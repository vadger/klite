package klite.jdbc.multitenant

import kotlinx.coroutines.withContext
import javax.sql.DataSource

/**
 * User-provided strategy for DataSource management per tenant.
 *
 * Implementations control:
 * - How to create DataSource for a tenant (connection URL, credentials, etc.)
 * - Whether/how to cache DataSources (none, LRU, simple map, etc.)
 * - Connection lifecycle management
 *
 * Example with simple caching:
 * ```kotlin
 * class CachingTenantDataSourceProvider : TenantDataSourceProvider {
 *     private val cache = ConcurrentHashMap<TenantId, DataSource>()
 *
 *     override fun getDataSource(tenantId: TenantId) = cache.computeIfAbsent(tenantId) {
 *         ConfigDataSource(url = "jdbc:postgresql://host/${tenantId.value}")
 *     }
 *
 *     override fun close() = cache.values.forEach { (it as? AutoCloseable)?.close() }
 * }
 * ```
 */
interface TenantDataSourceProvider : AutoCloseable {
  /**
   * Get or create a DataSource for the specified tenant.
   * Implementation may cache DataSources or create fresh ones.
   */
  fun getDataSource(tenantId: TenantId): DataSource

  /**
   * Cleanup resources when the server stops.
   * Should close any cached DataSources/connections.
   */
  override fun close()
}

/**
 * Execute a block within a tenant context WITHOUT automatic transaction.
 * Sets up [TenantContext] so tenant-aware code knows which tenant is active.
 * Each DB operation will use auto-commit mode.
 *
 * Useful for jobs that need tenant context but manage transactions manually,
 * or when doing read-only operations.
 *
 * Example:
 * ```kotlin
 * @NoTransaction
 * class SyncJob(private val tenantProvider: TenantDataSourceProvider) : Job {
 *   override suspend fun run() {
 *     val data = externalApi.fetch()  // No DB transaction held open
 *
 *     for (tenantId in getTenantIds()) {
 *       tenantProvider.withTenant(tenantId) {
 *         repository.save(data)  // Auto-commits
 *       }
 *     }
 *   }
 * }
 * ```
 */
suspend fun <T> TenantDataSourceProvider.withTenant(tenantId: TenantId, block: suspend () -> T): T {
  val dataSource = getDataSource(tenantId)
  val context = TenantContext(tenantId, dataSource)

  return withContext(context) {
    context.attachToThread()
    try {
      block()
    } finally {
      context.detachFromThread()
    }
  }
}

/**
 * Execute a block within a tenant transaction.
 * Sets up both [TenantContext] and [TenantTransaction] with proper commit/rollback.
 *
 * - On success: transaction is committed
 * - On exception: transaction is rolled back
 *
 * Useful for jobs that need to perform atomic tenant DB operations.
 *
 * Example:
 * ```kotlin
 * @NoTransaction  // Skip main DB transaction
 * class BatchUpdateJob(private val tenantProvider: TenantDataSourceProvider) : Job {
 *   override suspend fun run() {
 *     for (tenantId in getTenantIds()) {
 *       tenantProvider.withTenantTransaction(tenantId) {
 *         // All operations in this block are in a single transaction
 *         repo.updateStatus("processing")
 *         repo.processItems()
 *         repo.updateStatus("done")
 *       }
 *     }
 *   }
 * }
 * ```
 */
suspend fun <T> TenantDataSourceProvider.withTenantTransaction(tenantId: TenantId, block: suspend () -> T): T {
  val dataSource = getDataSource(tenantId)
  val context = TenantContext(tenantId, dataSource)
  val tx = TenantTransaction(tenantId, dataSource)

  return withContext(context + TenantTransactionContext(tx)) {
    context.attachToThread()
    tx.attachToThread()
    try {
      block().also {
        tx.close(commit = true)
      }
    } catch (e: Throwable) {
      tx.close(commit = false)
      throw e
    } finally {
      context.detachFromThread()
    }
  }
}
