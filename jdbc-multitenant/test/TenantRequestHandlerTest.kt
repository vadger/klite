package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.*
import klite.HttpExchange
import klite.StatusCode.Companion.Found
import klite.StatusCodeException
import klite.jdbc.NoTransaction
import klite.jdbc.Transaction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

class TenantRequestHandlerTest {
  private val tenantId = TenantId("test-tenant")
  private val mockDataSource = mockk<DataSource>()
  private val mockConnection = mockk<Connection>(relaxed = true)
  private val mockResolver = mockk<TenantResolver>()
  private val mockDataSourceProvider = mockk<TenantDataSourceProvider>()

  private val handler = TenantRequestHandler(mockResolver, mockDataSourceProvider, null)

  @BeforeEach
  fun setup() {
    every { mockDataSource.connection } returns mockConnection
    every { mockConnection.autoCommit } returns true
    every { mockConnection.autoCommit = any() } just Runs
    every { mockDataSourceProvider.getDataSource(tenantId) } returns mockDataSource
  }

  @AfterEach
  fun cleanup() {
    TenantContext.current()?.detachFromThread()
    Transaction.current()?.detachFromThread()
    clearAllMocks()
  }

  private fun exchangeWithTenant(hasNoTransaction: Boolean = false): HttpExchange = mockk(relaxed = true) {
    every { route.annotations } returns if (hasNoTransaction) listOf(NoTransaction()) else emptyList()
  }

  private fun exchangeWithoutTenant(): HttpExchange = mockk(relaxed = true)

  @Test
  fun `passes through when no tenant resolved`() {
    val exchange = exchangeWithoutTenant()
    every { mockResolver.resolve(exchange) } returns null

    var handlerCalled = false
    runBlocking {
      val result = handler.decorate(exchange) {
        handlerCalled = true
        "result"
      }
      expect(result).toEqual("result")
    }

    expect(handlerCalled).toEqual(true)
    expect(TenantContext.current()).toEqual(null)
    expect(Transaction.current()).toEqual(null)
  }

  @Test
  fun `sets up TenantContext and Transaction for normal request`() {
    val exchange = exchangeWithTenant(hasNoTransaction = false)
    every { mockResolver.resolve(exchange) } returns tenantId

    var contextInHandler: TenantContext? = null
    var transactionInHandler: Transaction? = null

    runBlocking {
      handler.decorate(exchange) {
        contextInHandler = TenantContext.current()
        transactionInHandler = Transaction.current()
        "result"
      }
    }

    expect(contextInHandler).notToEqualNull()
    expect(contextInHandler!!.tenantId).toEqual(tenantId)
    expect(transactionInHandler).notToEqualNull()

    // After request, both should be cleaned up
    expect(TenantContext.current()).toEqual(null)
    expect(Transaction.current()).toEqual(null)
  }

  @Test
  fun `commits transaction on success`() {
    val exchange = exchangeWithTenant(hasNoTransaction = false)
    every { mockResolver.resolve(exchange) } returns tenantId
    every { mockConnection.autoCommit } returns false

    runBlocking {
      handler.decorate(exchange) {
        // Access connection to open it
        Transaction.current()!!.connection(mockDataSource)
        "success"
      }
    }

    verify { mockConnection.commit() }
    verify { mockConnection.autoCommit = true }
    verify { mockConnection.close() }
  }

  @Test
  fun `commits transaction on StatusCodeException`() {
    val exchange = exchangeWithTenant(hasNoTransaction = false)
    every { mockResolver.resolve(exchange) } returns tenantId
    every { mockConnection.autoCommit } returns false

    expect {
      runBlocking {
        handler.decorate(exchange) {
          Transaction.current()!!.connection(mockDataSource)
          throw StatusCodeException(Found, "redirect")
        }
      }
    }.toThrow<StatusCodeException>()

    verify { mockConnection.commit() }
    verify { mockConnection.close() }
  }

  @Test
  fun `rollbacks transaction on error`() {
    val exchange = exchangeWithTenant(hasNoTransaction = false)
    every { mockResolver.resolve(exchange) } returns tenantId
    every { mockConnection.autoCommit } returns false

    expect {
      runBlocking {
        handler.decorate(exchange) {
          Transaction.current()!!.connection(mockDataSource)
          error("Kaboom")
        }
      }
    }.toThrow<IllegalStateException>()

    verify { mockConnection.rollback() }
    verify { mockConnection.close() }
  }

  // ========== NoTransaction tests ==========

  @Test
  fun `NoTransaction sets up TenantContext but NOT Transaction`() {
    val exchange = exchangeWithTenant(hasNoTransaction = true)
    every { mockResolver.resolve(exchange) } returns tenantId

    var contextInHandler: TenantContext? = null
    var transactionInHandler: Transaction? = null

    runBlocking {
      handler.decorate(exchange) {
        contextInHandler = TenantContext.current()
        transactionInHandler = Transaction.current()
        "result"
      }
    }

    // TenantContext should be set
    expect(contextInHandler).notToEqualNull()
    expect(contextInHandler!!.tenantId).toEqual(tenantId)
    expect(contextInHandler!!.dataSource).toBeTheInstance(mockDataSource)

    // Transaction should NOT be set
    expect(transactionInHandler).toEqual(null)

    // After request, context should be cleaned up
    expect(TenantContext.current()).toEqual(null)
  }

  @Test
  fun `NoTransaction does not open connection or manage transaction`() {
    val exchange = exchangeWithTenant(hasNoTransaction = true)
    every { mockResolver.resolve(exchange) } returns tenantId

    runBlocking {
      handler.decorate(exchange) {
        // Just access context, don't use connection
        expect(TenantContext.current()).notToEqualNull()
        "result"
      }
    }

    // No connection should be opened for transaction management
    verify(exactly = 0) { mockDataSource.connection }
    verify(exactly = 0) { mockConnection.autoCommit = false }
    verify(exactly = 0) { mockConnection.commit() }
    verify(exactly = 0) { mockConnection.rollback() }
  }

  @Test
  fun `NoTransaction handler failure does not trigger rollback`() {
    val exchange = exchangeWithTenant(hasNoTransaction = true)
    every { mockResolver.resolve(exchange) } returns tenantId

    expect {
      runBlocking {
        handler.decorate(exchange) {
          expect(TenantContext.current()).notToEqualNull()
          expect(Transaction.current()).toEqual(null)
          error("Kaboom")
        }
      }
    }.toThrow<IllegalStateException>()

    // No transaction management should happen
    verify(exactly = 0) { mockConnection.rollback() }
    verify(exactly = 0) { mockConnection.commit() }

    // Context should be cleaned up even on error
    expect(TenantContext.current()).toEqual(null)
  }

  @Test
  fun `NoTransaction cleans up context on success`() {
    val exchange = exchangeWithTenant(hasNoTransaction = true)
    every { mockResolver.resolve(exchange) } returns tenantId

    runBlocking {
      handler.decorate(exchange) { "result" }
    }

    expect(TenantContext.current()).toEqual(null)
    expect(Transaction.current()).toEqual(null)
  }

  @Test
  fun `NoTransaction cleans up context on failure`() {
    val exchange = exchangeWithTenant(hasNoTransaction = true)
    every { mockResolver.resolve(exchange) } returns tenantId

    expect {
      runBlocking {
        handler.decorate(exchange) { error("Fail") }
      }
    }.toThrow<IllegalStateException>()

    expect(TenantContext.current()).toEqual(null)
    expect(Transaction.current()).toEqual(null)
  }
}
