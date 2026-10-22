-- RS-6 Kitchen display v2: prep-time targets, ticket timestamps, refire, and sold-out (86'd) items.
alter table menu_item add column prep_minutes integer;
alter table menu_item add column sold_out boolean not null default false;
alter table menu_item add column sold_out_reason character varying(120);
alter table menu_item add column sold_out_at timestamp(6) with time zone;
alter table menu_item add column sold_out_by character varying(80);

alter table restaurant_order_line add column prep_minutes integer;
alter table restaurant_order_line add column sent_at timestamp(6) with time zone;
alter table restaurant_order_line add column preparing_at timestamp(6) with time zone;
alter table restaurant_order_line add column ready_at timestamp(6) with time zone;
alter table restaurant_order_line add column served_at timestamp(6) with time zone;
alter table restaurant_order_line add column remake_count integer not null default 0;

alter table restaurant_settings add column default_prep_minutes integer not null default 15;
alter table restaurant_settings add column kitchen_warn_percent integer not null default 75;

-- tickets already in the kitchen when this is applied: count their wait from the order's opening time
update restaurant_order_line l set sent_at = o.opened_at from restaurant_order o
 where l.order_id = o.id and l.kitchen_status <> 'PENDING' and l.sent_at is null;
