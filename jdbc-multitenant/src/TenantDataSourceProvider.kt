package klite.jdbc.multitenant

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
