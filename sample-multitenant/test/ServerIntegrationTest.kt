package multitenant

import TransactionRequest
import TransactionResponse
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import klite.Decimal
import klite.json.JsonHttpClient
import kotlinx.coroutines.runBlocking
import main.TenantUser
import multitenantServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.IOException
import java.net.http.HttpRequest.BodyPublishers

@TestInstance(PER_CLASS)
class ServerIntegrationTest : DBTest() {
  private lateinit var server: klite.Server
  private lateinit var http: JsonHttpClient

  @BeforeAll
  fun startServer() {
    server = multitenantServer(0).apply { start(gracefulStopDelaySec = -1) }
    http = JsonHttpClient("http://localhost:${server.address.port}", registry = server.registry)
  }

  @AfterAll
  fun stopServer() {
    server.stop()
  }

  @Test
  fun `health check returns OK`() {
    runBlocking {
      expect(http.get<String>("/health")).toEqual("\"OK\"")
    }
  }

  @Test
  fun `list users from main DB`() {
    runBlocking {
      val users = http.get<List<TenantUser>>("/api/users")
      expect(users).toHaveSize(2)
      expect(users.map { it.tenantDbName }).toContainExactlyElementsOf(listOf("tenant1", "tenant2"))
    }
  }

  @Test
  fun `tenant operations require Tenant-Id header`() {
    expect {
      runBlocking { http.get<List<TransactionResponse>>("/api/transactions") }
    }.toThrow<IOException>().messageToContain("Tenant-Id header is required")
  }

  @Test
  fun `create and list transactions for tenant1`() {
    runBlocking {
      // Clean up first
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1_test") }

      // Create transaction
      val created = http.post<TransactionResponse>(
        "/api/transactions",
        TransactionRequest("Test payment", Decimal("100.50"))
      ) { header("Tenant-Id", "tenant1_test") }

      expect(created.description).toEqual("Test payment")
      expect(created.amount).toEqual(Decimal("100.50"))

      // List transactions
      val transactions = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1_test")
      }
      expect(transactions).toHaveSize(1)
      expect(transactions.first().description).toEqual("Test payment")
    }
  }

  @Test
  fun `tenant isolation - tenant2 cannot see tenant1 transactions`() {
    runBlocking {
      // Clean up both tenants
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1_test") }
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant2_test") }

      // Create transaction for tenant1
      http.post<TransactionResponse>(
        "/api/transactions",
        TransactionRequest("Tenant1 payment", Decimal("50.00"))
      ) { header("Tenant-Id", "tenant1_test") }

      // Create transaction for tenant2
      http.post<TransactionResponse>(
        "/api/transactions",
        TransactionRequest("Tenant2 payment", Decimal("75.00"))
      ) { header("Tenant-Id", "tenant2_test") }

      // Tenant1 should only see their transaction
      val tenant1Txs = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1_test")
      }
      expect(tenant1Txs).toHaveSize(1)
      expect(tenant1Txs.first().description).toEqual("Tenant1 payment")

      // Tenant2 should only see their transaction
      val tenant2Txs = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant2_test")
      }
      expect(tenant2Txs).toHaveSize(1)
      expect(tenant2Txs.first().description).toEqual("Tenant2 payment")
    }
  }

  @Test
  fun `failed transaction rolls back - transaction not saved`() {
    runBlocking {
      // Clean up first
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1_test") }

      // Try to create transaction that fails
      expect {
        runBlocking {
          http.post<TransactionResponse>(
            "/api/transactions/fail",
            TransactionRequest("Should be rolled back", Decimal("999.99"))
          ) { header("Tenant-Id", "tenant1_test") }
        }
      }.toThrow<IOException>().messageToContain("Simulated failure")

      // Verify transaction was rolled back - list should be empty
      val transactions = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1_test")
      }
      expect(transactions).toBeEmpty()
    }
  }

  @Test
  fun `mixed DB operations - read main DB and write tenant DB in same request`() {
    runBlocking {
      // Clean up tenant1's transactions
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1") }

      // Create transaction using the mixed endpoint that:
      // 1. Reads tenant user from MAIN DB (verifies user exists)
      // 2. Writes transaction to TENANT DB (with user email in description)
      val created = http.post<TransactionResponse>(
        "/api/transactions/with-user-check",
        TransactionRequest("Mixed DB test", Decimal("42.00"))
      ) { header("Tenant-Id", "tenant1") }

      // Verify the transaction was created with the user's email from main DB
      expect(created.description).toContain("admin@tenant1.com")
      expect(created.description).toContain("Mixed DB test")
      expect(created.amount).toEqual(Decimal("42.00"))

      // Verify transaction is persisted in tenant DB
      val transactions = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1")
      }
      expect(transactions).toHaveSize(1)
      expect(transactions.first().description).toContain("admin@tenant1.com")
    }
  }

  @Test
  fun `mixed DB operations - fails gracefully when tenant user not found in main DB`() {
    expect {
      runBlocking {
        // Use a tenant ID that exists in DB (tenant1) but doesn't match any user's tenantDbName
        // by using tenant1_test which exists as a database but has no matching user in main DB
        http.post<TransactionResponse>(
          "/api/transactions/with-user-check",
          TransactionRequest("Should fail", Decimal("1.00"))
        ) { header("Tenant-Id", "tenant1_test") }
      }
    }.toThrow<IOException>().messageToContain("Tenant user not found")
  }

  // ========== NoTransaction tests ==========

  @Test
  fun `NoTransaction route creates transaction successfully without automatic transaction`() {
    runBlocking {
      // Clean up first
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1_test") }

      // Create transaction using @NoTransaction endpoint
      val created = http.post<TransactionResponse>(
        "/api/transactions/no-tx",
        TransactionRequest("No-TX payment", Decimal("123.45"))
      ) { header("Tenant-Id", "tenant1_test") }

      expect(created.description).toEqual("No-TX payment")
      expect(created.amount).toEqual(Decimal("123.45"))

      // Verify transaction is persisted (auto-committed)
      val transactions = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1_test")
      }
      expect(transactions).toHaveSize(1)
      expect(transactions.first().description).toEqual("No-TX payment")
    }
  }

  @Test
  fun `NoTransaction route failure does NOT rollback - insert is persisted`() {
    runBlocking {
      // Clean up first
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1_test") }

      // Try to create transaction that fails AFTER insert
      // With @NoTransaction, the insert should be auto-committed and NOT rolled back
      expect {
        runBlocking {
          http.post<TransactionResponse>(
            "/api/transactions/no-tx-fail",
            TransactionRequest("Should persist despite failure", Decimal("777.77"))
          ) { header("Tenant-Id", "tenant1_test") }
        }
      }.toThrow<IOException>().messageToContain("should NOT be rolled back")

      // Verify transaction WAS persisted (because @NoTransaction = auto-commit mode)
      val transactions = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1_test")
      }
      expect(transactions).toHaveSize(1)
      expect(transactions.first().description).toEqual("Should persist despite failure")
      expect(transactions.first().amount).toEqual(Decimal("777.77"))
    }
  }

  @Test
  fun `NoTransaction vs normal transaction - compare rollback behavior`() {
    runBlocking {
      // Clean up both test tenants
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant1_test") }
      http.delete<Unit>("/api/transactions") { header("Tenant-Id", "tenant2_test") }

      // Test 1: Normal transaction endpoint with failure - should rollback
      expect {
        runBlocking {
          http.post<TransactionResponse>(
            "/api/transactions/fail",
            TransactionRequest("Normal TX - should rollback", Decimal("111.11"))
          ) { header("Tenant-Id", "tenant1_test") }
        }
      }.toThrow<IOException>()

      // Test 2: @NoTransaction endpoint with failure - should NOT rollback
      expect {
        runBlocking {
          http.post<TransactionResponse>(
            "/api/transactions/no-tx-fail",
            TransactionRequest("NoTX - should persist", Decimal("222.22"))
          ) { header("Tenant-Id", "tenant2_test") }
        }
      }.toThrow<IOException>()

      // Verify: Normal transaction was rolled back
      val tenant1Txs = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant1_test")
      }
      expect(tenant1Txs).toBeEmpty()

      // Verify: @NoTransaction insert was persisted
      val tenant2Txs = http.get<List<TransactionResponse>>("/api/transactions") {
        header("Tenant-Id", "tenant2_test")
      }
      expect(tenant2Txs).toHaveSize(1)
      expect(tenant2Txs.first().description).toEqual("NoTX - should persist")
    }
  }
}
