package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.*
import klite.jdbc.ChangeSet
import klite.jdbc.DBMigrator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class TenantMigratorTest {
  private val tenant1 = TenantId("tenant1")
  private val tenant2 = TenantId("tenant2")
  private val mockDataSourceProvider = mockk<TenantDataSourceProvider>()
  private val mockDataSource1 = mockk<DataSource>()
  private val mockDataSource2 = mockk<DataSource>()

  private val emptyChangeSets = emptySequence<ChangeSet>()

  @BeforeEach
  fun setup() {
    every { mockDataSourceProvider.getDataSource(tenant1) } returns mockDataSource1
    every { mockDataSourceProvider.getDataSource(tenant2) } returns mockDataSource2

    // Mock DBMigrator to avoid actual DB operations
    mockkConstructor(DBMigrator::class)
    every { anyConstructed<DBMigrator>().migrate() } just Runs
  }

  @AfterEach
  fun cleanup() {
    unmockkConstructor(DBMigrator::class)
    clearAllMocks()
  }

  @Test
  fun `ensureMigrated runs migrations for tenant`() {
    val migrator = TenantMigrator(mockDataSourceProvider, emptyChangeSets)

    expect(migrator.isMigrated(tenant1)).toEqual(false)

    migrator.ensureMigrated(tenant1)

    expect(migrator.isMigrated(tenant1)).toEqual(true)
    verify { mockDataSourceProvider.getDataSource(tenant1) }
    verify { anyConstructed<DBMigrator>().migrate() }
  }

  @Test
  fun `ensureMigrated is idempotent`() {
    val migrator = TenantMigrator(mockDataSourceProvider, emptyChangeSets)

    migrator.ensureMigrated(tenant1)
    migrator.ensureMigrated(tenant1)
    migrator.ensureMigrated(tenant1)

    // DBMigrator should only be constructed once
    verify(exactly = 1) { mockDataSourceProvider.getDataSource(tenant1) }
    verify(exactly = 1) { anyConstructed<DBMigrator>().migrate() }
  }

  @Test
  fun `different tenants are migrated independently`() {
    val migrator = TenantMigrator(mockDataSourceProvider, emptyChangeSets)

    migrator.ensureMigrated(tenant1)

    expect(migrator.isMigrated(tenant1)).toEqual(true)
    expect(migrator.isMigrated(tenant2)).toEqual(false)

    migrator.ensureMigrated(tenant2)

    expect(migrator.isMigrated(tenant1)).toEqual(true)
    expect(migrator.isMigrated(tenant2)).toEqual(true)
  }

  @Test
  fun `clearMigrationStatus allows re-migration`() {
    val migrator = TenantMigrator(mockDataSourceProvider, emptyChangeSets)

    migrator.ensureMigrated(tenant1)
    expect(migrator.isMigrated(tenant1)).toEqual(true)

    migrator.clearMigrationStatus(tenant1)
    expect(migrator.isMigrated(tenant1)).toEqual(false)

    migrator.ensureMigrated(tenant1)

    // Should have called getDataSource and migrate twice
    verify(exactly = 2) { mockDataSourceProvider.getDataSource(tenant1) }
    verify(exactly = 2) { anyConstructed<DBMigrator>().migrate() }
  }

  @Test
  fun `clearAllMigrationStatus clears all tenants`() {
    val migrator = TenantMigrator(mockDataSourceProvider, emptyChangeSets)

    migrator.ensureMigrated(tenant1)
    migrator.ensureMigrated(tenant2)

    expect(migrator.isMigrated(tenant1)).toEqual(true)
    expect(migrator.isMigrated(tenant2)).toEqual(true)

    migrator.clearAllMigrationStatus()

    expect(migrator.isMigrated(tenant1)).toEqual(false)
    expect(migrator.isMigrated(tenant2)).toEqual(false)
  }
}
