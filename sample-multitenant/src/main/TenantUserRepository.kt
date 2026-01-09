package main

import klite.Email
import klite.jdbc.BaseCrudRepository
import java.util.*
import javax.sql.DataSource

class TenantUserRepository(db: DataSource) : BaseCrudRepository<TenantUser, UUID>(db, "tenant_users") {
  fun byEmail(email: Email): TenantUser? = by(TenantUser::email to email)
  fun byTenantDbName(tenantDbName: String): TenantUser? = by(TenantUser::tenantDbName to tenantDbName)
}
