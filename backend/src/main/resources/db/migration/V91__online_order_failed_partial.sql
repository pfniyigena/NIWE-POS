-- v1.58.0: failed and partial deliveries. A failed visit puts the order back to PACKED with the reason and the number of tries; a partial delivery keeps
-- what the customer took (per line), the amount invoiced, and, for an order paid online, the part to give back.
alter table online_order add column delivery_attempts integer not null default 0;
alter table online_order add column last_failure_reason varchar(200);
alter table online_order add column last_failure_at timestamp(6) with time zone;
alter table online_order add column delivered_total numeric(38,2);
alter table online_order add column refund_difference numeric(38,2);
alter table online_order_line add column delivered_quantity numeric(38,4);
