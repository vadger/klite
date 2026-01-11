package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

class TenantDataSourceTest {
  private val tenantId = TenantId("test-tenant")
  private val mockDataSource = mockk<DataSource>()
  private val mockConnection = mockk<Connection>()
  private val tenantDataSource = TenantDataSource()

  @BeforeEach
  fun setup() {
    every { mockDataSource.connection } returns mockConnection
  }

  @AfterEach
  fun cleanup() {
    TenantContext.current()?.detachFromThread()
    TenantTransaction.current()?.detachFromThread()
    clearAllMocks()
  }

  @Test
  fun `getConnection throws when no tenant context is set`() {
    expect { tenantDataSource.connection }
      .toThrow<IllegalStateException>()
  }

  @Test
  fun `getConnection returns connection from current tenant DataSource`() {
    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    val connection = tenantDataSource.connection

    expect(connection).toBeTheInstance(mockConnection)
    verify { mockDataSource.connection }
  }

  @Test
  fun `getTransactionConnection returns null when no tenant context`() {
    expect(tenantDataSource.getTransactionConnection()).toEqual(null)
  }

  @Test
  fun `getTransactionConnection returns null when no tenant transaction`() {
    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    expect(tenantDataSource.getTransactionConnection()).toEqual(null)
  }

  @Test
  fun `getTransactionConnection returns null when transaction db differs from context dataSource`() {
    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    // Create transaction with a different DataSource
    val differentDataSource = mockk<DataSource>()
    val tx = TenantTransaction(tenantId, differentDataSource)
    tx.attachToThread()

    expect(tenantDataSource.getTransactionConnection()).toEqual(null)
  }

  @Test
  fun `getTransactionConnection returns transaction connection when context and transaction match`() {
    val txConnection = mockk<Connection>()
    every { mockDataSource.connection } returns txConnection
    every { txConnection.autoCommit } returns true
    every { txConnection.autoCommit = any() } just Runs

    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    val tx = TenantTransaction(tenantId, mockDataSource)
    tx.attachToThread()

    // Access connection to initialize it
    val conn = tx.connection

    // Now getTransactionConnection should return the transaction's connection
    val provided = tenantDataSource.getTransactionConnection()
    expect(provided).toBeTheInstance(conn)
  }
}
