package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import javax.sql.DataSource

class TenantContextTest {
  private val tenantId = TenantId("test-tenant")
  private val mockDataSource = mockk<DataSource>()

  @AfterEach
  fun cleanup() {
    TenantContext.current()?.detachFromThread()
  }

  @Test
  fun `current returns null when no context is set`() {
    expect(TenantContext.current()).toEqual(null)
  }

  @Test
  fun `requireCurrent throws when no context is set`() {
    expect { TenantContext.requireCurrent() }
      .toThrow<IllegalStateException>()
      .messageToContain("No tenant context")
  }

  @Test
  fun `attachToThread makes context available via current`() {
    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    val current = TenantContext.current()
    expect(current).notToEqualNull()
    expect(current).toBeTheInstance(context)
    expect(current!!.tenantId).toEqual(tenantId)
    expect(current.dataSource).toBeTheInstance(mockDataSource)
  }

  @Test
  fun `detachFromThread removes context`() {
    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    expect(TenantContext.current()).notToEqualNull()

    context.detachFromThread()

    expect(TenantContext.current()).toEqual(null)
  }

  @Test
  fun `nested contexts work correctly`() {
    val tenant1 = TenantId("tenant1")
    val tenant2 = TenantId("tenant2")
    val ds1 = mockk<DataSource>()
    val ds2 = mockk<DataSource>()

    val context1 = TenantContext(tenant1, ds1)
    context1.attachToThread()

    expect(TenantContext.current()?.tenantId).toEqual(tenant1)

    // Nested context
    val context2 = TenantContext(tenant2, ds2)
    context2.attachToThread()

    expect(TenantContext.current()?.tenantId).toEqual(tenant2)

    // Restore outer context
    context1.attachToThread()
    expect(TenantContext.current()?.tenantId).toEqual(tenant1)
  }
}
