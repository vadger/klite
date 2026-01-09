package klite.jdbc.multitenant

import kotlinx.coroutines.ThreadContextElement
import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Thread-local context holding the current tenant information for the request.
 * Similar to [klite.jdbc.TransactionContext], provides tenant awareness throughout the request lifecycle.
 */
class TenantContext(
  val tenantId: TenantId,
  val dataSource: DataSource
) : ThreadContextElement<TenantContext?>, AbstractCoroutineContextElement(Key) {

  companion object Key : CoroutineContext.Key<TenantContext> {
    private val threadLocal = ThreadLocal<TenantContext>()

    /** Returns the current tenant context, or null if not in a tenant context */
    fun current(): TenantContext? = threadLocal.get()

    /** Returns the current tenant context, throws if not in a tenant context */
    fun requireCurrent(): TenantContext = current()
      ?: throw IllegalStateException("No tenant context available. Ensure TenantRequestHandler is configured and tenant was resolved.")
  }

  fun attachToThread(): TenantContext = this.also { threadLocal.set(it) }
  fun detachFromThread() = threadLocal.remove()

  // ThreadContextElement implementation for coroutine support
  override fun updateThreadContext(context: CoroutineContext): TenantContext? =
    current().also { attachToThread() }

  override fun restoreThreadContext(context: CoroutineContext, oldState: TenantContext?) {
    oldState?.attachToThread() ?: detachFromThread()
  }
}
