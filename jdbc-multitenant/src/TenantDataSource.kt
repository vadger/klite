package klite.jdbc.multitenant

import klite.jdbc.KliteTransactionAwareDataSource
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import javax.sql.DataSource

/**
 * A dynamic DataSource wrapper that delegates to the current tenant's DataSource.
 *
 * This allows using existing [klite.jdbc.BaseCrudRepository] unchanged - just inject
 * TenantDataSource instead of regular DataSource:
 *
 * ```kotlin
 * class TenantOrderRepository(db: TenantDataSource) : BaseCrudRepository<Order, UUID>(db, "orders")
 * ```
 *
 * When repository operations execute, this wrapper automatically routes to the
 * current tenant's actual DataSource via [TenantContext].
 *
 * Implements [KliteTransactionAwareDataSource] to integrate with [klite.jdbc.withConnection],
 * allowing proper transaction management when accessing tenant databases.
 */
class TenantDataSource : DataSource, KliteTransactionAwareDataSource {

  override fun getTransactionConnection(): Connection? {
    val ctx = TenantContext.current() ?: return null
    val tenantTx = TenantTransaction.current() ?: return null
    return if (tenantTx.db == ctx.dataSource) tenantTx.connection else null
  }

  private val currentDataSource: DataSource
    get() = TenantContext.requireCurrent().dataSource

  override fun getConnection(): Connection = currentDataSource.connection

  override fun getConnection(username: String?, password: String?): Connection =
    throw SQLFeatureNotSupportedException("Use getConnection()")

  override fun getLogWriter(): PrintWriter? = null
  override fun setLogWriter(out: PrintWriter?) = throw SQLFeatureNotSupportedException()

  override fun getLoginTimeout(): Int = 0
  override fun setLoginTimeout(seconds: Int) = throw SQLFeatureNotSupportedException()

  override fun getParentLogger() = throw SQLFeatureNotSupportedException()

  override fun <T : Any?> unwrap(iface: Class<T>): T = currentDataSource.unwrap(iface)
  override fun isWrapperFor(iface: Class<*>): Boolean = currentDataSource.isWrapperFor(iface)
}
