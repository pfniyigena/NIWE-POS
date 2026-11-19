-- v1.48.0: product rows on the shop slide by hand by default; staff can switch automatic sliding on.
alter table online_store_settings add column auto_slide_rows boolean not null default false;
