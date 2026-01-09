import klite.Decimal
import klite.StatusCode
import klite.annotations.*
import klite.jdbc.delete
import klite.jdbc.multitenant.TenantContext
import klite.jdbc.multitenant.TenantDataSource
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
