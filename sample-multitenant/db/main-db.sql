--substitute id=id uuid default gen_random_uuid() primary key
--substitute createdAt=createdAt timestamptz default current_timestamp

--changeset tenant_users
-- Stores tenant connection information in the main database
create table tenant_users(
  ${id},
  name varchar not null,
  email varchar not null,
  tenantDbName varchar not null,
  ${createdAt}
);

--changeset tenant_users_email_idx
create unique index tenant_users_email_idx on tenant_users (email);

--changeset tenant_users_tenantDbName_idx
create unique index tenant_users_tenantDbName_idx on tenant_users (tenantDbName);

--changeset seed_tenants
insert into tenant_users (id, name, email, tenantDbName) values
  ('11111111-1111-1111-1111-111111111111', 'Tenant One Admin', 'admin@tenant1.com', 'tenant1'),
  ('22222222-2222-2222-2222-222222222222', 'Tenant Two Admin', 'admin@tenant2.com', 'tenant2');
