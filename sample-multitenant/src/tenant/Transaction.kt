package tenant

import klite.Decimal
import klite.jdbc.BaseEntity
import java.util.*

/**
 * Represents a transaction in a tenant database.
 * Each tenant has their own transactions table with isolated data.
 */
data class Transaction(
  val description: String,
  val amount: Decimal,
  override val id: UUID = UUID.randomUUID()
) : BaseEntity<UUID>
