package klite.jdbc.multitenant

import klite.info
import klite.jdbc.ChangeSet
import klite.jdbc.DBMigrator
import klite.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Handles on-demand database migrations for tenant databases.
 *
 * Migrations are run when a tenant is first accessed, and the migration status
 * is tracked to avoid re-running migrations on subsequent requests.
 *
 * Thread-safe: uses per-tenant locks to prevent concurrent migrations for the same tenant.
 */
class TenantMigrator(
  private val dataSourceProvider: TenantDataSourceProvider,
  private val changeSets: Sequence<ChangeSet>
) {
  private val log = logger()

  /** Tracks which tenants have been migrated */
  private val migrated = ConcurrentHashMap<TenantId, Boolean>()

  /** Per-tenant locks to prevent concurrent migrations */
  private val locks = ConcurrentHashMap<TenantId, ReentrantLock>()

  /**
   * Ensure migrations have been applied for the given tenant.
   * If migrations haven't run yet, runs them synchronously.
   * Thread-safe: concurrent calls for the same tenant will wait.
   */
  fun ensureMigrated(tenantId: TenantId) {
    // Fast path: already migrated
    if (migrated[tenantId] == true) return

    // Get or create lock for this tenant
    val lock = locks.computeIfAbsent(tenantId) { ReentrantLock() }

    lock.withLock {
      // Double-check after acquiring lock
      if (migrated[tenantId] == true) return

      log.info("Running migrations for tenant $tenantId")
      val dataSource = dataSourceProvider.getDataSource(tenantId)

      DBMigrator(dataSource, changeSets).migrate()

      migrated[tenantId] = true
      log.info("Migrations completed for tenant $tenantId")
    }
  }

  /**
   * Check if a tenant has been migrated in this session.
   * Note: This only tracks migrations run by this instance.
   */
  fun isMigrated(tenantId: TenantId): Boolean = migrated[tenantId] == true

  /**
   * Clear migration status for a tenant, allowing re-migration.
   * Useful for testing or when tenant DB is recreated.
   */
  fun clearMigrationStatus(tenantId: TenantId) {
    migrated.remove(tenantId)
  }

  /**
   * Clear all migration status tracking.
   */
  fun clearAllMigrationStatus() {
    migrated.clear()
  }
}
