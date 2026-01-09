import klite.HttpExchange
import klite.jdbc.multitenant.TenantId
import klite.jdbc.multitenant.TenantResolver

/**
 * Resolves tenant from X-Tenant-Id header.
 * In a real application, you might resolve from JWT claims, subdomain, or path parameter.
 */
class HeaderTenantResolver : TenantResolver {
  companion object {
    const val HEADER_NAME = "Tenant-Id"
  }

  override fun resolve(exchange: HttpExchange): TenantId? {
    return exchange.header(HEADER_NAME)?.let { TenantId(it) }
  }
}
