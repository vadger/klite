package klite.jdbc.multitenant

import klite.*
import klite.jdbc.ChangeSet
import klite.jdbc.ChangeSetFileReader

/**
 * Config key for tenant database migration file.
 * Use this to specify a separate migration file for tenant databases,
 * distinct from the main database's DB_MIGRATE.
 *
 * Example: DB_TENANT_MIGRATE=tenant-db.sql
 */
val Config.dbTenantMigrate: String? get() = optional("DB_TENANT_MIGRATE")

/**
 * Module that wires together all multitenancy components.
 *
 * Prerequisites:
 * - Register [TenantResolver] implementation
 * - Register [TenantDataSourceProvider] implementation
 *
 * This module will:
 * 1. Install the connection provider hook for tenant-aware connections
 * 2. Register [TenantDataSource] for DI injection into repositories
 * 3. Create [TenantMigrator] if changesets are provided (or DB_TENANT_MIGRATE config is set)
 * 4. Install [TenantRequestHandler] as a request decorator
 * 5. Register cleanup on server stop
 *
 * ## Migration Separation
 *
 * Tenant databases use SEPARATE changesets from the main database:
 * - Main DB: Configure via `DB_MIGRATE` config (default: db.sql), used by [klite.jdbc.DBMigrator]
 * - Tenant DBs: Configure via `DB_TENANT_MIGRATE` config, or pass explicit changesets
 *
 * This ensures main database schema (users, billing, tenant metadata) is separate
 * from tenant-specific schema (tenant data, orders, etc.).
 *
 * Usage:
 * ```kotlin
 * Server().apply {
 *     // Main DB migrations (for shared data: users, billing, tenant configs)
 *     use(DBMigrator()) // uses DB_MIGRATE config or db.sql
 *     use(DBModule(PooledDataSource()))
 *
 *     // Register tenant implementations
 *     register<TenantResolver>(MyTenantResolver::class)
 *     register<TenantDataSourceProvider>(MyTenantDataSourceProvider::class)
 *
 *     // Tenant DB - uses DB_TENANT_MIGRATE config if set
 *     use<TenantDBModule>()
 *
 *     // Or with explicit changeset file
 *     use(TenantDBModule("tenant-db.sql"))
 *
 *     // Or with custom changesets
 *     use(TenantDBModule(tenantChangeSets = ChangeSetFileReader("tenant-db.sql")))
 * }
 * ```
 */
class TenantDBModule(
  private val tenantChangeSets: Sequence<ChangeSet>? = Config.dbTenantMigrate?.let { ChangeSetFileReader(it) }
) : Extension {

  private val log = logger()

  override fun install(server: Server) {
    val registry = server.registry

    // Install the connection provider hook
    TenantDataSource.installConnectionProvider()
    log.info("Installed tenant connection provider")

    // Get required dependencies
    val resolver = registry.require<TenantResolver>()
    val dataSourceProvider = registry.require<TenantDataSourceProvider>()

    // Register TenantDataSource for DI
    registry.register(TenantDataSource())

    // Create migrator if changesets provided
    val migrator = tenantChangeSets?.let { TenantMigrator(dataSourceProvider, it) }
    migrator?.let { registry.register(it) }

    // Install request handler
    val handler = TenantRequestHandler(resolver, dataSourceProvider, migrator)
    server.use(handler)

    // Cleanup on server stop
    server.onStop {
      log.info("Closing tenant data source provider")
      dataSourceProvider.close()
    }
  }
}

/**
 * Convenience function to create TenantDBModule with a changeset file path.
 */
fun TenantDBModule(tenantChangeSetPath: String): TenantDBModule =
  TenantDBModule(tenantChangeSets = ChangeSetFileReader(tenantChangeSetPath))
