-- Initialize an empty or partially initialized database without deleting data.
create table if not exists inventory (
    product_id text primary key,
    name text not null,
    stock integer not null check (stock >= 0)
);

create table if not exists orders (
    order_id bigint generated always as identity primary key,
    status text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason text,
    created_at timestamptz not null default now()
);
alter table orders add column if not exists source_reference text;
create unique index if not exists orders_source_reference_uq on orders(source_reference);

create table if not exists order_items (
    item_id bigint generated always as identity primary key,
    order_id bigint not null references orders(order_id) on delete cascade,
    product_id text not null,
    quantity integer not null check (quantity > 0)
);

create table if not exists notifications (
    notification_id bigint generated always as identity primary key,
    message text not null,
    created_at timestamptz not null default now()
);

create table if not exists supplier_orders (
    id bigint generated always as identity primary key,
    product_id text not null,
    buyer_ref text not null unique,
    request_id text not null unique,
    po_number text,
    cases integer not null check (cases > 0),
    units integer not null check (units > 0),
    status text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists tiangge_checkpoint (
    id integer primary key check (id = 1),
    cursor bigint not null default 0
);
insert into tiangge_checkpoint (id, cursor) values (1, 0) on conflict (id) do nothing;

create table if not exists tiangge_orders (
    marketplace_order_id text primary key,
    shop_order_id bigint references orders(order_id),
    status text not null,
    lines_json text not null
);

-- Seed only absent products, preserving stock and names already present.
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do nothing;
