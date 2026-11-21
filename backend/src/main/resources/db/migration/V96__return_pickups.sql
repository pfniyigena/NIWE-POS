-- v1.62.0: return pick-ups. An approved return of a delivered order is collected from the customer by a driver, who confirms it with a return code
-- (like the delivery code). New status COLLECTED between APPROVED and REFUNDED: the goods are with the driver and wait to be checked and refunded.
alter table online_return drop constraint online_return_status_ck;
alter table online_return add constraint online_return_status_ck check (status in ('REQUESTED', 'APPROVED', 'COLLECTED', 'REFUSED', 'REFUNDED'));

alter table online_return add column pickup_driver_id bigint;
alter table online_return add column pickup_driver_name varchar(80);
alter table online_return add column pickup_driver_phone varchar(40);
alter table online_return add column pickup_code varchar(4);
alter table online_return add column pickup_failures integer not null default 0;
alter table online_return add column pickup_failed_at timestamp(6) with time zone;
alter table online_return add column collected_at timestamp(6) with time zone;
alter table online_return add column collected_by varchar(80);
create index online_return_driver_idx on online_return (pickup_driver_id, status) where pickup_driver_id is not null;
