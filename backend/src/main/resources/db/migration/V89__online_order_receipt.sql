-- v1.56.0: order reception. The delivery (or pick-up) code the customer holds, how many wrong codes were typed, and who confirmed that the goods
-- arrived (the customer, the code typed by the driver or counter staff, or the customer's signature on the driver's phone) with the signature itself.
alter table online_order add column receipt_code varchar(8);
alter table online_order add column receipt_failures integer not null default 0;
alter table online_order add column receipt_failed_at timestamp(6) with time zone;
alter table online_order add column received_at timestamp(6) with time zone;
alter table online_order add column received_via varchar(12);
alter table online_order add column received_by varchar(120);
alter table online_order add column signed_name varchar(120);
alter table online_order add column signature text;
