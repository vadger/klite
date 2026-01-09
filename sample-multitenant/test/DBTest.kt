package multitenant

import klite.Config
import klite.jdbc.ConfigDataSource
import klite.jdbc.DBMigrator
import klite.jdbc.ChangeSetFileReader
import klite.jdbc.startDevDB
import javax.sql.DataSource

/**
 * Base class for DB-dependent unit tests in the multitenant sample.
 * Sets up both main and tenant test databases with migrations.
 */
abstract class DBTest : klite.jdbc.DBTest() {
  companion object {
    init {
      // Set up config for multitenant sample
      Config["ENV"] = "test,test-data"
      Config["DB_URL"] = "jdbc:postgresql://localhost:5433/multitenant"
      Config["DB_USER"] = "multitenant"
      Config["DB_PASS"] = "multitenant"
      Config["DB_MIGRATE"] = "main-db.sql"

      // Start docker-compose if needed
      startDevDB()

      // Use test database for main DB
      Config["DB_URL"] = Config["DB_URL"] + "_test"

      // Migrate main DB
      DBMigrator(db, ChangeSetFileReader("main-db.sql"), dropAllOnFailure = true).migrate()

      // Migrate tenant test databases
      migrateTenantDb("tenant1_test")
      migrateTenantDb("tenant2_test")
    }

    private fun migrateTenantDb(dbName: String) {
      val baseUrl = Config["DB_URL"].substringBeforeLast("/") + "/"
      val tenantDs = ConfigDataSource(
        url = "$baseUrl$dbName",
        user = Config.optional("DB_USER"),
        pass = Config.optional("DB_PASS")
      )
      DBMigrator(tenantDs, ChangeSetFileReader("tenant-db.sql"), dropAllOnFailure = true).migrate()
    }

    fun tenantDataSource(tenantId: String): DataSource {
      val baseUrl = Config["DB_URL"].substringBeforeLast("/") + "/"
      val testDbName = "${tenantId}_test"
      return ConfigDataSource(
        url = "$baseUrl$testDbName",
        user = Config.optional("DB_USER"),
        pass = Config.optional("DB_PASS")
      )
    }
  }
}
