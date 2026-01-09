package klite.jdbc.multitenant

import klite.error
import klite.logger
import kotlinx.coroutines.ThreadContextElement
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Transaction for tenant database operations.
 * Similar to [klite.jdbc.Transaction] but specifically for tenant-scoped operations.
 *
 * Manages a single connection for the tenant DB within a request,
 * with proper commit/rollback semantics.
 */
class TenantTransaction(
  val tenantId: TenantId,
  val db: DataSource
) : AutoCloseable {

  companion object {
    private val log = logger()
    private val threadLocal = ThreadLocal<TenantTransaction>()

    /** Returns the current tenant transaction, or null if not in a tenant transaction */
    fun current(): TenantTransaction? = threadLocal.get()
  }

  private var conn: Connection? = null

  val connection: Connection
    get() = conn ?: openConnection()

  private fun openConnection(): Connection = db.connection.apply {
    autoCommit = false
    conn = this
  }

  override fun close() = close(commit = true)

  fun close(commit: Boolean = true) {
    try {
      conn?.apply {
        if (!autoCommit) {
          if (commit) commit() else rollback()
          autoCommit = true
        }
      }
    } catch (e: SQLException) {
      log.error("Failed to ${if (commit) "commit" else "rollback"} tenant transaction", e)
    } finally {
      try {
        conn?.close()
      } catch (e: Exception) {
        log.error("Failed to close tenant connection $conn: $e")
      }
      conn = null
      detachFromThread()
    }
  }

  fun commit() = conn?.commit()
  fun rollback() = conn?.rollback()

  fun attachToThread(): TenantTransaction = this.also { threadLocal.set(it) }
  fun detachFromThread() = threadLocal.remove()
}

/**
 * Coroutine context element for propagating [TenantTransaction] across coroutine dispatches.
 */
class TenantTransactionContext(
  val tx: TenantTransaction? = TenantTransaction.current()
) : ThreadContextElement<TenantTransaction?>, AbstractCoroutineContextElement(Key) {

  companion object Key : CoroutineContext.Key<TenantTransactionContext>

  override fun updateThreadContext(context: CoroutineContext): TenantTransaction? =
    TenantTransaction.current().also { tx?.attachToThread() }

  override fun restoreThreadContext(context: CoroutineContext, oldState: TenantTransaction?) {
    oldState?.attachToThread() ?: tx?.detachFromThread()
  }
}
