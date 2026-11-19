-- v1.57.0: driver cash hand-in. The cash recorded when a delivery order was completed (what the driver should bring back) and, once staff settle it,
-- what he handed in, the difference (posted to Cash over / short), who and when.
alter table online_order add column cash_collected numeric(38,2);
alter table online_order add column cash_settled_at timestamp(6) with time zone;
alter table online_order add column cash_settled_by varchar(120);
alter table online_order add column cash_handed_in numeric(38,2);
alter table online_order add column cash_difference numeric(38,2);
alter table online_order add column cash_note varchar(200);
create index online_order_cash_idx on online_order (cash_settled_at) where cash_collected is not null;
