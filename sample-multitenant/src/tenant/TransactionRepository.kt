package tenant

import klite.jdbc.BaseCrudRepository
import klite.jdbc.multitenant.TenantDataSource
import java.util.*

/**
 * Repository for tenant transactions.
 * Uses TenantDataSource which automatically routes to the current tenant's database.
 */
class TransactionRepository(db: TenantDataSource) : BaseCrudRepository<Transaction, UUID>(db, "transactions") {
  override val defaultOrder = "order by createdAt desc"
}
