package klite.jdbc.multitenant

import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

class TenantDataSourceTest {
  private val tenantId = TenantId("test-tenant")
  private val mockDataSource = mockk<DataSource>()
  private val mockConnection = mockk<Connection>()
  private val tenantDataSource = TenantDataSource()

  @BeforeEach
  fun setup() {
    every { mockDataSource.connection } returns mockConnection
  }

  @AfterEach
  fun cleanup() {
    TenantContext.current()?.detachFromThread()
    TenantTransaction.current()?.detachFromThread()
    clearAllMocks()
  }

  @Test
  fun `getConnection throws when no tenant context is set`() {
    expect { tenantDataSource.connection }
      .toThrow<IllegalStateException>()
  }

  @Test
  fun `getConnection returns connection from current tenant DataSource`() {
    val context = TenantContext(tenantId, mockDataSource)
    context.attachToThread()

    val connection = tenantDataSource.connection

    expect(connection).toBeTheInstance(mockConnection)
    verify { mockDataSource.connection }
  }
}
