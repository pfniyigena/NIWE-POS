-- PO-5: discounts at the till (allowance, reasons, manager approval), whole-order discount and price override.
alter table pos_settings add column cashier_discount_max numeric(7,2) not null default 0;
alter table pos_settings add column discount_reason_required boolean not null default false;
alter table pos_settings add column discount_reasons character varying(400) not null default 'Loyal customer,Damaged item,Staff,Manager special,Price match,Other';
alter table pos_settings add column price_override_allowed boolean not null default true;
alter table pos_settings add column price_below_cost_blocked boolean not null default true;

alter table sale_order add column order_discount_percent numeric(9,4) not null default 0;
alter table sale_order add column order_discount_amount numeric(38,2);
alter table sale_order add column order_discount_reason character varying(80);
alter table sale_order add column order_discount_by character varying(80);
alter table sale_order add column order_discount_approved_by character varying(80);

alter table order_line add column discount_reason character varying(80);
alter table order_line add column discount_approved_by character varying(80);
alter table order_line add column price_overridden boolean not null default false;
alter table order_line add column original_unit_price numeric(38,2);
alter table order_line add column price_override_reason character varying(80);
alter table order_line add column price_override_by character varying(80);
alter table order_line add column price_override_approved_by character varying(80);
