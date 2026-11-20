-- v1.59.1: the tax a delivery area's fee carries (one of the ERP's taxes; null = the delivery product's own tax), and the tax copied onto the order
-- when it is placed, so a later change of the area does not change an order already placed.
alter table online_delivery_zone add column tax_rate_id bigint;
alter table online_order add column delivery_tax_rate_id bigint;
