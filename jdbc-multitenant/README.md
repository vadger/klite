# klite-jdbc-multitenant

Multitenancy support for klite-jdbc, enabling multiple tenant databases alongside a meta database.

## Features

- **TenantContext**: Thread-local tenant awareness for request-scoped tenant resolution
- **TenantDataSource**: Dynamic DataSource wrapper for DI-compatible tenant repositories
- **TenantTransaction**: Tenant-aware transaction handling with coroutine context support
- **TenantMigrator**: On-demand database migrations per tenant
- **TenantRequestHandler**: Request decorator that sets up tenant context automatically

## Usage

### 1. Implement TenantResolver

Provide your own logic to extract tenant ID from requests:

```kotlin
class MyTenantResolver : TenantResolver {
    override fun resolve(exchange: HttpExchange) = 
        exchange.header("X-Tenant-Id")?.let { TenantId(it) }
}
```

### 2. Implement TenantDataSourceProvider

Provide your own DataSource management strategy (caching, connection creation, etc.):

```kotlin
class MyTenantDataSourceProvider : TenantDataSourceProvider {
    private val cache = ConcurrentHashMap<TenantId, DataSource>()
    
    override fun getDataSource(tenantId: TenantId) = cache.computeIfAbsent(tenantId) {
        ConfigDataSource(url = "jdbc:postgresql://host/${tenantId.value}")
    }
    
    override fun close() = cache.values.forEach { (it as? AutoCloseable)?.close() }
}
```

### 3. Configure Server

```kotlin
Server().apply {
    // Meta DB (pooled) - for shared data
    use(DBModule(PooledDataSource()))
    use<RequestTransactionHandler>()
    
    // Tenant DB (non-pooled, user-cached)
    register<TenantResolver>(MyTenantResolver::class)
    register<TenantDataSourceProvider>(MyTenantDataSourceProvider::class)
    use<TenantDBModule>()
    
    context("/api") {
        annotated<MyRoutes>()
    }
}.start()
```

### 4. Create Repositories

```kotlin
// Meta DB repository - uses regular DataSource
class MetaUserRepository(db: DataSource) : BaseCrudRepository<MetaUser, UUID>(db, "users")

// Tenant DB repository - uses TenantDataSource (automatically resolves to current tenant's DB)
class TenantOrderRepository(db: TenantDataSource) : BaseCrudRepository<Order, UUID>(db, "orders")
```

## Mixed Operations

Both meta and tenant databases can be accessed in the same request:

```kotlin
class MyRoutes(
    private val metaRepo: MetaUserRepository,    // Uses pooled meta DB
    private val tenantRepo: TenantOrderRepository // Uses tenant-specific DB
) {
    @GET("/orders")
    fun getOrders(): List<Order> {
        val user = metaRepo.get(currentUserId) // From meta DB
        return tenantRepo.list(Order::userId to user.id) // From tenant DB
    }
}
```

## Migrations

### Separation of Main DB and Tenant DB Migrations

**Important**: Main database and tenant databases use **separate** migration files:

| Database | Config Key | Default | Purpose |
|----------|-----------|---------|---------|
| Main DB | `DB_MIGRATE` | `db.sql` | Users, billing, tenant metadata |
| Tenant DBs | `DB_TENANT_MIGRATE` | (none) | Tenant-specific data, orders, etc. |

This separation ensures that main database schema changes don't accidentally get applied to tenant databases and vice versa.

### Configuration

**Option 1: Environment/Config variables**

```properties
# Main database migrations
DB_MIGRATE=main-db.sql

# Tenant database migrations (separate file!)
DB_TENANT_MIGRATE=tenant-db.sql
```

```kotlin
Server().apply {
    // Main DB - uses DB_MIGRATE config
    use(DBMigrator())
    use(DBModule(PooledDataSource()))
    
    // Tenant DB - automatically uses DB_TENANT_MIGRATE config
    register<TenantResolver>(MyTenantResolver::class)
    register<TenantDataSourceProvider>(MyTenantDataSourceProvider::class)
    use<TenantDBModule>()
}
```

**Option 2: Explicit changeset files**

```kotlin
Server().apply {
    // Main DB - explicit file
    use(DBMigrator(PooledDataSource(), ChangeSetFileReader("main-db.sql")))
    use(DBModule(PooledDataSource()))
    
    // Tenant DB - explicit file
    use(TenantDBModule("tenant-db.sql"))
}
```

**Option 3: No tenant migrations** (if tenant DBs are pre-migrated externally)

```kotlin
// Without DB_TENANT_MIGRATE set and no explicit changesets,
// TenantDBModule won't run any migrations
use<TenantDBModule>()
```

### On-Demand Tenant Migrations

Tenant migrations run **on-demand** when a tenant is first accessed during a request. This is tracked in-memory per application instance, so:

- First request for a tenant → migrations run
- Subsequent requests → migrations skipped (already tracked)
- Application restart → migrations re-checked (but DB changelog table prevents re-execution)
