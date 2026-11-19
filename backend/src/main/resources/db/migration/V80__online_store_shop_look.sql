-- v1.45.0: the shop's own colour and an estimated delivery time, both shown on the public shop.
alter table online_store_settings add column brand_color varchar(7);
alter table online_store_settings add column delivery_days_min integer;
alter table online_store_settings add column delivery_days_max integer;
