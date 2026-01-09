package klite.jdbc.multitenant

import klite.*
import kotlinx.coroutines.withContext

/**
 * Request decorator that sets up tenant context for each request.
 *
 * For requests where a tenant is resolved:
 * 1. Optionally runs migrations for the tenant (if [migrator] is provided)
 * 2. Sets up [TenantContext] with the tenant's DataSource
 * 3. Wraps the request in a [TenantTransaction] for proper commit/rollback
 *
 * For requests where no tenant is resolved, the handler is called without tenant context.
 *
 * Usage:
 * ```kotlin
 * Server().apply {
 *     register<TenantResolver>(MyTenantResolver::class)
 *     register<TenantDataSourceProvider>(MyTenantDataSourceProvider::class)
 *     use<TenantRequestHandler>()
 *     // or with migrations:
 *     use(TenantRequestHandler(
 *         resolver = require(),
 *         dataSourceProvider = require(),
 *         migrator = TenantMigrator(require(), ChangeSetFileReader("tenant-db.sql"))
 *     ))
 * }
 * ```
 */
class TenantRequestHandler(
  private val resolver: TenantResolver,
  private val dataSourceProvider: TenantDataSourceProvider,
  private val migrator: TenantMigrator? = null
) : Extension {

  override fun install(config: RouterConfig) {
    config.decorator { exchange, handler ->
      decorate(exchange, handler)
    }
  }

  private suspend fun decorate(exchange: HttpExchange, handler: Handler): Any? {
    val tenantId = resolver.resolve(exchange) ?: return handler(exchange)

    // Run migrations if migrator is configured
    migrator?.ensureMigrated(tenantId)

    // Get DataSource for this tenant
    val dataSource = dataSourceProvider.getDataSource(tenantId)

    // Set up tenant context and transaction
    val context = TenantContext(tenantId, dataSource)
    val tx = TenantTransaction(tenantId, dataSource)

    return withContext(context + TenantTransactionContext(tx)) {
      tx.attachToThread()
      try {
        handler(exchange).also {
          tx.close(commit = true)
        }
      } catch (e: Throwable) {
        tx.close(commit = e is StatusCodeException)
        throw e
      } finally {
        context.detachFromThread()
      }
    }
  }
}
