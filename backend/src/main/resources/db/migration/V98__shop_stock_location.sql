-- v1.65.0: the sales location the online shop sells from (null = every eligible sales location). Only used when multi-warehouse is on.
alter table online_store_settings add column shop_location_id bigint;
