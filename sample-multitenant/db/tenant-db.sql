--substitute id=id uuid default gen_random_uuid() primary key
--substitute createdAt=createdAt timestamptz default current_timestamp

--changeset transactions
-- Tenant-specific transactions table
create table transactions(
  ${id},
  description varchar not null,
  amount decimal(19,4) not null,
  ${createdAt}
);

--changeset transactions_createdAt_idx
create index transactions_createdAt_idx on transactions (createdAt desc);
