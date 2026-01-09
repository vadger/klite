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
  fun `connection provider uses TenantTransaction connection when available`() {
    TenantDataSource.installConnectionProvider()

    val txConnection = mockk<Connection>()
    every { mockDataSource.connection } returns txConnection

    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    val tx = TenantTransaction(tenantId, mockDataSource)
    tx.attachToThread()

    // Access connection to initialize it
    every { txConnection.autoCommit } returns true
    every { txConnection.autoCommit = any() } just Runs
    val conn = tx.connection

    // Now the connection provider should return the transaction's connection
    val provided = klite.jdbc.additionalConnectionProvider?.invoke(tenantDataSource)
    expect(provided).toBeTheInstance(conn)

    tx.detachFromThread()
    context.detachFromThread()
  }

  @Test
  fun `connection provider returns null for non-TenantDataSource`() {
    TenantDataSource.installConnectionProvider()

    val regularDataSource = mockk<DataSource>()

    val provided = klite.jdbc.additionalConnectionProvider?.invoke(regularDataSource)
    expect(provided).toEqual(null)
  }
}
