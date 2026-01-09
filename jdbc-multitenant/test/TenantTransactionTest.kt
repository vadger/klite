package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

class TenantTransactionTest {
  private val tenantId = TenantId("test-tenant")
  private val mockDataSource = mockk<DataSource>()
  private val mockConnection = mockk<Connection>(relaxed = true)

  @BeforeEach
  fun setup() {
    every { mockDataSource.connection } returns mockConnection
    every { mockConnection.autoCommit } returns true
    every { mockConnection.autoCommit = any() } just Runs
  }

  @AfterEach
  fun cleanup() {
    TenantTransaction.current()?.detachFromThread()
    clearAllMocks()
  }

  @Test
  fun `current returns null when no transaction is active`() {
    expect(TenantTransaction.current()).toEqual(null)
  }

  @Test
  fun `attachToThread makes transaction available via current`() {
    val tx = TenantTransaction(tenantId, mockDataSource)
    tx.attachToThread()

    val current = TenantTransaction.current()
    expect(current).toBeTheInstance(tx)
    expect(current?.tenantId).toEqual(tenantId)
  }

  @Test
  fun `connection is lazily opened`() {
    val tx = TenantTransaction(tenantId, mockDataSource)

    verify(exactly = 0) { mockDataSource.connection }

    // Access connection
    tx.connection

    verify(exactly = 1) { mockDataSource.connection }
    verify { mockConnection.autoCommit = false }
  }

  @Test
  fun `close commits and returns connection`() {
    val tx = TenantTransaction(tenantId, mockDataSource)
    tx.attachToThread()

    // Open connection
    every { mockConnection.autoCommit } returns false
    tx.connection

    // Close with commit
    tx.close(commit = true)

    verify { mockConnection.commit() }
    verify { mockConnection.autoCommit = true }
    verify { mockConnection.close() }
    expect(TenantTransaction.current()).toEqual(null)
  }

  @Test
  fun `close with rollback rolls back transaction`() {
    val tx = TenantTransaction(tenantId, mockDataSource)
    tx.attachToThread()

    // Open connection
    every { mockConnection.autoCommit } returns false
    tx.connection

    // Close with rollback
    tx.close(commit = false)

    verify { mockConnection.rollback() }
    verify { mockConnection.autoCommit = true }
    verify { mockConnection.close() }
  }

  @Test
  fun `close without opened connection does not fail`() {
    val tx = TenantTransaction(tenantId, mockDataSource)
    tx.attachToThread()

    // Close without ever accessing connection
    tx.close()

    verify(exactly = 0) { mockConnection.commit() }
    verify(exactly = 0) { mockConnection.close() }
    expect(TenantTransaction.current()).toEqual(null)
  }
}
