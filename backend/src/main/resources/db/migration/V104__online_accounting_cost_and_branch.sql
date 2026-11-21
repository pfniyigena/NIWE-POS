-- v1.74.0: what the goods cost when they were sold (so a return takes them back at that cost), and the branch the online shop sells for.
alter table sales_order_line add column cost_total numeric(18,4) not null default 0;
alter table sales_order_line add column cost_quantity numeric(18,4) not null default 0;
alter table online_store_settings add column branch_id bigint;
