-- Run this in the Supabase SQL Editor (or via psql) against your project's
-- Postgres database before starting the backend.
-- This script recreates the current schema from scratch, including seed data.

drop table if exists notifications cascade;
drop table if exists order_items cascade;
drop table if exists orders cascade;
drop table if exists inventory cascade;
drop table if exists supplier_orders cascade;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

create table orders (
    order_id    bigint generated always as identity primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason      text,
    source_reference text unique,
    created_at  timestamptz not null default now()
);

create table order_items (
    item_id     bigint generated always as identity primary key,
    order_id    bigint not null references orders(order_id) on delete cascade,
    product_id  text not null,
    quantity    integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigint generated always as identity primary key,
    message         text not null,
    created_at      timestamptz not null default now()
);

-- Lab 3: Supplier Orders table for Anti-Corruption Layer
create table supplier_orders (
    id          bigint generated always as identity primary key,
    product_id  text not null,
    buyer_ref   text not null unique,
    request_id  text not null unique,
    po_number   text,
    cases       integer not null check (cases > 0),
    units       integer not null check (units > 0),
    status      text not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- Lab 4: persistent Tiangge feed cursor and marketplace-to-shop order mapping.
create table tiangge_checkpoint (
    id integer primary key check (id = 1),
    cursor bigint not null default 0
);
insert into tiangge_checkpoint (id, cursor) values (1, 0) on conflict (id) do nothing;

create table tiangge_orders (
    marketplace_order_id text primary key,
    shop_order_id bigint references orders(order_id),
    status text not null,
    lines_json text not null
);

-- Seed data
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
    set name = excluded.name,
        stock = excluded.stock;
