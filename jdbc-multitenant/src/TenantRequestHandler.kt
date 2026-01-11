package klite.jdbc.multitenant

import klite.*
import klite.jdbc.NoTransaction
import kotlinx.coroutines.withContext
import kotlin.reflect.full.hasAnnotation

/**
 * Request decorator that sets up tenant context for each request.
 *
 * For requests where a tenant is resolved:
 * 1. Optionally runs migrations for the tenant (if [migrator] is provided)
 * 2. Sets up [TenantContext] with the tenant's DataSource
 * 3. Wraps the request in a [TenantTransaction] for proper commit/rollback (unless [NoTransaction] is used)
 *
 * For requests where no tenant is resolved, the handler is called without tenant context.
 *
 * When [NoTransaction] annotation is present on the route:
 * - [TenantContext] is still set up (tenant ID and DataSource available)
 * - [TenantTransaction] is NOT created (connections use auto-commit mode)
 * - This is useful for routes with external API calls to avoid long-running transactions
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

  suspend fun decorate(exchange: HttpExchange, handler: Handler): Any? {
    val tenantId = resolver.resolve(exchange) ?: return handler(exchange)

    // Run migrations if migrator is configured
    migrator?.ensureMigrated(tenantId)

    // Get DataSource for this tenant
    val dataSource = dataSourceProvider.getDataSource(tenantId)

    // Set up tenant context
    val context = TenantContext(tenantId, dataSource)

    // Check for NoTransaction annotation - skip transaction setup if present
    if (exchange.route.hasAnnotation<NoTransaction>()) {
      return withContext(context) {
        context.attachToThread()
        try {
          handler(exchange)
        } finally {
          context.detachFromThread()
        }
      }
    }

    // Normal case: set up transaction
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
