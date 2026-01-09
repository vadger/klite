import klite.Config
import klite.info
import klite.jdbc.ConfigDataSource
import klite.jdbc.multitenant.TenantDataSourceProvider
import klite.jdbc.multitenant.TenantId
import klite.logger
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * Provides DataSource for each tenant by constructing JDBC URL from tenant ID.
 * Caches DataSources for reuse across requests.
 *
 * In production, tenant connection info would typically come from the main database.
 */
class CachingTenantDataSourceProvider : TenantDataSourceProvider {
  private val log = logger()
  private val cache = ConcurrentHashMap<TenantId, DataSource>()

  // Base URL without database name, e.g., "jdbc:postgresql://localhost:5433/"
  private val baseUrl: String = run {
    val mainUrl = Config["DB_URL"]
    // Extract base URL by removing the database name
    val lastSlash = mainUrl.lastIndexOf('/')
    if (lastSlash > 0) mainUrl.substring(0, lastSlash + 1) else "$mainUrl/"
  }

  override fun getDataSource(tenantId: TenantId): DataSource = cache.computeIfAbsent(tenantId) {
    val tenantUrl = "$baseUrl${tenantId.value}"
    log.info("Creating DataSource for tenant $tenantId: $tenantUrl")
    ConfigDataSource(
      url = tenantUrl,
      user = Config.optional("DB_USER"),
      pass = Config.optional("DB_PASS")
    )
  }

  override fun close() {
    log.info("Closing ${cache.size} tenant DataSources")
    cache.values.forEach { (it as? AutoCloseable)?.close() }
    cache.clear()
  }
}
