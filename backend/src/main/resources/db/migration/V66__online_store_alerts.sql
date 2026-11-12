-- v1.28.2: tell the shop when an online order has waited too long for an answer.
alter table online_store_settings add column unanswered_alert_minutes integer not null default 30;
alter table online_order add column alerted_at timestamp(6) with time zone;
create index online_order_unanswered_idx on online_order (status, created_at) where alerted_at is null;
