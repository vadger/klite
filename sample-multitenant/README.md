# klite Multitenancy Sample

This sample demonstrates how to use the `klite-jdbc-multitenant` module to build a multi-tenant application with separate databases per tenant.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Application                              │
├─────────────────────────────────────────────────────────────────┤
│  Main DB (Pooled)              │  Tenant DBs (On-demand)        │
│  - tenant_users table          │  - transactions table          │
│  - Billing, configs            │  - Tenant-specific data        │
│  - Shared across tenants       │  - Isolated per tenant         │
└────────────────────────────────┴────────────────────────────────┘
```

## Database Structure

### Main Database (`multitenant`)
- `tenant_users` - Stores user info with tenant database mapping
- Uses pooled connections via `PooledDataSource`
- Migrations: `main-db.sql`

### Tenant Databases (`tenant1`, `tenant2`, ...)
- `transactions` - Tenant-specific transaction data
- Non-pooled connections (managed by `CachingTenantDataSourceProvider`)
- Migrations: `tenant-db.sql` (run on-demand per tenant)

## Running

### 1. Start PostgreSQL

```bash
cd sample-multitenant
docker-compose up -d
```

This creates:
- Main database: `multitenant`
- Test database: `multitenant_test`
- Tenant databases: `tenant1`, `tenant1_test`, `tenant2`, `tenant2_test`

### 2. Run the application

```bash
./gradlew :sample-multitenant:run
```

### 3. Test the API

```bash
# Health check
curl http://localhost:8080/health

# List users (from main DB)
curl http://localhost:8080/api/users

# Create transaction for tenant1
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant1" \
  -d '{"description": "Payment", "amount": "100.50"}'

# List transactions for tenant1
curl http://localhost:8080/api/transactions \
  -H "X-Tenant-Id: tenant1"

# List transactions for tenant2 (empty - isolated)
curl http://localhost:8080/api/transactions \
  -H "X-Tenant-Id: tenant2"

# Test rollback - this will fail and rollback
curl -X POST http://localhost:8080/api/transactions/fail \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant1" \
  -d '{"description": "Should be rolled back", "amount": "999.99"}'
```

## Running Tests

```bash
./gradlew :sample-multitenant:test
```

## Configuration

Environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | Main database JDBC URL | `jdbc:postgresql://localhost:5433/multitenant` |
| `DB_USER` | Database username | `multitenant` |
| `DB_PASS` | Database password | `multitenant` |
| `DB_MIGRATE` | Main DB migration file | `main-db.sql` |
| `DB_TENANT_MIGRATE` | Tenant DB migration file | `tenant-db.sql` |

## Key Components

- **HeaderTenantResolver** - Extracts tenant ID from `X-Tenant-Id` header
- **CachingTenantDataSourceProvider** - Creates and caches DataSources per tenant
- **TenantUserRepository** - Main DB repository for tenant user data
- **TransactionRepository** - Tenant DB repository (uses `TenantDataSource`)
