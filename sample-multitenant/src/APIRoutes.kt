import klite.Decimal
import klite.NotFoundException
import klite.StatusCode
import klite.annotations.*
import klite.jdbc.NoTransaction
import klite.jdbc.delete
import klite.jdbc.multitenant.TenantContext
import klite.jdbc.multitenant.TenantDataSource
import klite.jdbc.multitenant.TenantTransaction
import main.TenantUser
import main.TenantUserRepository
import tenant.Transaction
import tenant.TransactionRepository

data class TransactionRequest(val description: String, val amount: Decimal)
data class TransactionResponse(val id: String, val description: String, val amount: Decimal)

@Path("")
class APIRoutes(
  private val tenantUserRepository: TenantUserRepository,
  private val transactionRepository: TransactionRepository,
  private val tenantDataSource: TenantDataSource
) {

  // ========== Main DB endpoints (tenant user management) ==========

  @GET("/users")
  fun listUsers(): List<TenantUser> = tenantUserRepository.list()

  @GET("/users/:id")
  fun getUser(@PathParam id: String): TenantUser =
    tenantUserRepository.get(java.util.UUID.fromString(id))

  // ========== Tenant DB endpoints (require X-Tenant-Id header) ==========

  @GET("/transactions")
  fun listTransactions(): List<TransactionResponse> {
    requireTenantContext()
    return transactionRepository.list().map { it.toResponse() }
  }

  @POST("/transactions")
  fun createTransaction(body: TransactionRequest): TransactionResponse {
    requireTenantContext()
    val transaction = Transaction(
      description = body.description,
      amount = body.amount
    )
    transactionRepository.save(transaction)
    return transaction.toResponse()
  }

  @POST("/transactions/fail")
  fun createTransactionAndFail(body: TransactionRequest): TransactionResponse {
    requireTenantContext()
    val transaction = Transaction(
      description = body.description,
      amount = body.amount
    )
    transactionRepository.save(transaction)

    // Simulate a failure after insert - this should rollback the transaction
    error("Simulated failure after insert - transaction should be rolled back")
  }

  /**
   * Mixed DB operation example: reads from main DB and writes to tenant DB in the same request.
   * This demonstrates that both main transaction (RequestTransactionHandler) and tenant transaction
   * (TenantRequestHandler) work correctly together.
   */
  @POST("/transactions/with-user-check")
  fun createTransactionWithUserCheck(body: TransactionRequest): TransactionResponse {
    requireTenantContext()

    // Read from MAIN DB - verify tenant user exists
    val tenantId = TenantContext.requireCurrent().tenantId
    val tenantUser = tenantUserRepository.byTenantDbName(tenantId.value)
      ?: throw NotFoundException("Tenant user not found for: ${tenantId.value}")

    // Write to TENANT DB - create transaction with user email in description
    val transaction = Transaction(
      description = "${tenantUser.email}: ${body.description}",
      amount = body.amount
    )
    transactionRepository.save(transaction)
    return transaction.toResponse()
  }

  // ========== NoTransaction test endpoints ==========

  /**
   * Creates a transaction WITHOUT automatic transaction wrapping.
   * Each DB operation runs in auto-commit mode.
   * Verifies that TenantContext is still available even without TenantTransaction.
   */
  @NoTransaction
  @POST("/transactions/no-tx")
  fun createWithoutTransaction(body: TransactionRequest): TransactionResponse {
    requireTenantContext()
    // Verify no TenantTransaction is active
    check(TenantTransaction.current() == null) { "Expected no TenantTransaction when @NoTransaction is used" }

    val transaction = Transaction(
      description = body.description,
      amount = body.amount
    )
    transactionRepository.save(transaction)
    return transaction.toResponse()
  }

  /**
   * Creates a transaction without automatic transaction, then fails.
   * This verifies that the insert is NOT rolled back (since each operation auto-commits).
   */
  @NoTransaction
  @POST("/transactions/no-tx-fail")
  fun createWithoutTransactionAndFail(body: TransactionRequest): TransactionResponse {
    requireTenantContext()
    check(TenantTransaction.current() == null) { "Expected no TenantTransaction when @NoTransaction is used" }

    val transaction = Transaction(
      description = body.description,
      amount = body.amount
    )
    transactionRepository.save(transaction)

    // Simulate a failure after insert - with @NoTransaction, the insert should NOT be rolled back
    error("Simulated failure after insert - transaction should NOT be rolled back because of @NoTransaction")
  }

  @DELETE("/transactions/:id")
  fun deleteTransaction(@PathParam id: String): StatusCode {
    requireTenantContext()
    val id = java.util.UUID.fromString(id)
    tenantDataSource.delete("transactions", "id" to id)
    return StatusCode.NoContent
  }

  @DELETE("/transactions")
  fun deleteAllTransactions(): StatusCode {
    requireTenantContext()
    transactionRepository.list().forEach { transaction ->
      tenantDataSource.delete("transactions", "id" to transaction.id)
    }
    return StatusCode.NoContent
  }

  // ========== Helper methods ==========

  private fun requireTenantContext() {
    TenantContext.current() ?: throw IllegalStateException("X-Tenant-Id header is required for this endpoint")
  }

  private fun Transaction.toResponse() = TransactionResponse(
    id = id.toString(),
    description = description,
    amount = amount
  )
}
