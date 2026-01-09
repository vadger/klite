package main

import klite.Email
import klite.jdbc.BaseEntity
import java.util.*

/**
 * Represents a user in the main database who has access to a specific tenant.
 * The tenantDbName field stores the database name for that tenant.
 */
data class TenantUser(
  val name: String,
  val email: Email,
  val tenantDbName: String,
  override val id: UUID = UUID.randomUUID()
) : BaseEntity<UUID>
