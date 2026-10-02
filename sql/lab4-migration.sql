-- Apply to an existing Lab 3 database without dropping its data.
alter table orders add column if not exists source_reference text unique;

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
