package klite.jdbc.multitenant

import klite.HttpExchange

/**
 * User-provided strategy to extract tenant ID from HTTP request.
 *
 * Implementations might extract tenant from:
 * - Request header (e.g., X-Tenant-Id)
 * - Subdomain (e.g., tenant1.app.com)
 * - JWT claim from authentication token
 * - Path parameter (e.g., /api/tenants/:tenantId/...)
 * - Custom logic
 *
 * Example:
 * ```kotlin
 * class HeaderTenantResolver : TenantResolver {
 *     override fun resolve(exchange: HttpExchange) =
 *         exchange.header("X-Tenant-Id")?.let { TenantId(it) }
 * }
 * ```
 */
interface TenantResolver {
  /**
   * Resolve the tenant ID from the given HTTP exchange.
   * @return TenantId if a tenant can be determined, null if this is not a tenant-specific request
   */
  fun resolve(exchange: HttpExchange): TenantId?
}
