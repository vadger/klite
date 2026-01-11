package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

class TenantDataSourceProviderExtensionsTest {
  private val tenantId = TenantId("test-tenant")
  private val mockDataSource = mockk<DataSource>()
  private val mockConnection = mockk<Connection>(relaxed = true)
  private val mockProvider = mockk<TenantDataSourceProvider>()

  @BeforeEach
  fun setup() {
    every { mockProvider.getDataSource(tenantId) } returns mockDataSource
    every { mockDataSource.connection } returns mockConnection
    every { mockConnection.autoCommit } returns true
    every { mockConnection.autoCommit = any() } just Runs
  }

  @AfterEach
  fun cleanup() {
    TenantContext.current()?.detachFromThread()
    TenantTransaction.current()?.detachFromThread()
    clearAllMocks()
  }

  // ========== withTenant tests (context only, no transaction) ==========

  @Test
  fun `withTenant sets up TenantContext`() {
    var contextInBlock: TenantContext? = null

    runBlocking {
      mockProvider.withTenant(tenantId) {
        contextInBlock = TenantContext.current()
      }
    }

    expect(contextInBlock).notToEqualNull()
    expect(contextInBlock!!.tenantId).toEqual(tenantId)
    expect(contextInBlock!!.dataSource).toBeTheInstance(mockDataSource)
  }

  @Test
  fun `withTenant does NOT set up TenantTransaction`() {
    var transactionInBlock: TenantTransaction? = null

    runBlocking {
      mockProvider.withTenant(tenantId) {
        transactionInBlock = TenantTransaction.current()
      }
    }

    expect(transactionInBlock).toEqual(null)
  }

  @Test
  fun `withTenant returns block result`() {
    val result = runBlocking {
      mockProvider.withTenant(tenantId) {
        "hello from tenant"
      }
    }

    expect(result).toEqual("hello from tenant")
  }

  @Test
  fun `withTenant cleans up context after success`() {
    runBlocking {
      mockProvider.withTenant(tenantId) { "done" }
    }

    expect(TenantContext.current()).toEqual(null)
  }

  @Test
  fun `withTenant cleans up context after failure`() {
    expect {
      runBlocking {
        mockProvider.withTenant(tenantId) {
          error("Boom!")
        }
      }
    }.toThrow<IllegalStateException>()

    expect(TenantContext.current()).toEqual(null)
  }

  // ========== withTenantTransaction tests ==========

  @Test
  fun `withTenantTransaction sets up TenantContext and TenantTransaction`() {
    var contextInBlock: TenantContext? = null
    var transactionInBlock: TenantTransaction? = null

    runBlocking {
      mockProvider.withTenantTransaction(tenantId) {
        contextInBlock = TenantContext.current()
        transactionInBlock = TenantTransaction.current()
      }
    }

    expect(contextInBlock).notToEqualNull()
    expect(contextInBlock!!.tenantId).toEqual(tenantId)
    expect(transactionInBlock).notToEqualNull()
    expect(transactionInBlock!!.tenantId).toEqual(tenantId)
  }

  @Test
  fun `withTenantTransaction commits on success`() {
    every { mockConnection.autoCommit } returns false

    runBlocking {
      mockProvider.withTenantTransaction(tenantId) {
        // Access connection to open it
        TenantTransaction.current()!!.connection
      }
    }

    verify { mockConnection.commit() }
    verify { mockConnection.autoCommit = true }
    verify { mockConnection.close() }
  }

  @Test
  fun `withTenantTransaction rolls back on error`() {
    every { mockConnection.autoCommit } returns false

    expect {
      runBlocking {
        mockProvider.withTenantTransaction(tenantId) {
          TenantTransaction.current()!!.connection
          error("Boom!")
        }
      }
    }.toThrow<IllegalStateException>()

    verify { mockConnection.rollback() }
    verify { mockConnection.close() }
  }

  @Test
  fun `withTenantTransaction returns block result`() {
    val result = runBlocking {
      mockProvider.withTenantTransaction(tenantId) {
        42
      }
    }

    expect(result).toEqual(42)
  }

  @Test
  fun `withTenantTransaction cleans up after success`() {
    runBlocking {
      mockProvider.withTenantTransaction(tenantId) { "done" }
    }

    expect(TenantContext.current()).toEqual(null)
    expect(TenantTransaction.current()).toEqual(null)
  }

  @Test
  fun `withTenantTransaction cleans up after failure`() {
    expect {
      runBlocking {
        mockProvider.withTenantTransaction(tenantId) {
          error("Boom!")
        }
      }
    }.toThrow<IllegalStateException>()

    expect(TenantContext.current()).toEqual(null)
    expect(TenantTransaction.current()).toEqual(null)
  }

  // ========== Nested usage tests ==========

  @Test
  fun `can nest withTenant for different tenants`() {
    val tenant1 = TenantId("tenant1")
    val tenant2 = TenantId("tenant2")
    val ds1 = mockk<DataSource>()
    val ds2 = mockk<DataSource>()

    every { mockProvider.getDataSource(tenant1) } returns ds1
    every { mockProvider.getDataSource(tenant2) } returns ds2

    var innerTenantId: TenantId? = null

    runBlocking {
      mockProvider.withTenant(tenant1) {
        expect(TenantContext.current()?.tenantId).toEqual(tenant1)

        mockProvider.withTenant(tenant2) {
          innerTenantId = TenantContext.current()?.tenantId
        }

        // After inner block, outer context should be restored
        expect(TenantContext.current()?.tenantId).toEqual(tenant1)
      }
    }

    expect(innerTenantId).toEqual(tenant2)
  }
}
