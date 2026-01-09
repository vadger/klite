import klite.*
import klite.annotations.annotated
import klite.http.httpClient
import klite.jdbc.*
import klite.jdbc.multitenant.*
import klite.json.JsonBody
import java.net.InetSocketAddress

fun main() {
  multitenantServer().start()
}

fun multitenantServer(port: Int = Config.port): Server {
  Config.useEnvFile()

  // Set main DB migration file
  Config["DB_MIGRATE"] = "main-db.sql"

  return Server(listen = InetSocketAddress(port)).apply {
    // Start docker-compose db automatically in dev mode
    if (Config.isDev) DockerCompose.startDB()

    // ========== Main Database Setup ==========
    // Migrate main DB (users, tenant configs, billing, etc.)
    use(DBMigrator(dropAllOnFailure = Config.isDev))
    // Configure pooled DataSource for main DB
    use(DBModule(PooledDataSource()))
    // Wrap requests in transactions for main DB
    use<RequestTransactionHandler>()

    // ========== Tenant Database Setup ==========
    // Register tenant resolution and data source provider
    register<TenantResolver>(HeaderTenantResolver::class)
    register<TenantDataSourceProvider>(CachingTenantDataSourceProvider::class)
    // Configure tenant DB module with on-demand migrations
    use(TenantDBModule("tenant-db.sql"))

    // ========== Server Configuration ==========
    use<JsonBody>()
    register(httpClient())

    context("/api") {
      useOnly<JsonBody>()
      before(CorsHandler())
      annotated<APIRoutes>()
    }

    // Health check
    context("/health") {
      get { "OK" }
    }
  }
}
