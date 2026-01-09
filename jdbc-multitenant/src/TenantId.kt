package klite.jdbc.multitenant

/**
 * Represents a tenant identifier. Can wrap any string value.
 * Users may create their own TenantId inline class if stronger typing is needed.
 */
@JvmInline
value class TenantId(val value: String) {
  override fun toString() = value
}
