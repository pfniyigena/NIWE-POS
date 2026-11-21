-- v1.66.0: hours an unanswered shop order may wait before it is released (0 = never), the share of each product kept for the till (percent),
-- and an optional safety buffer of one product (null = the shop-wide one).
alter table online_store_settings add column auto_release_hours integer not null default 24;
alter table online_store_settings add column till_share_percent integer not null default 0;
alter table online_listing add column stock_buffer integer;
