-- v1.63.0: an own delivery estimate (days after the order is accepted) per delivery area; both empty = the shop's estimate.
alter table online_delivery_zone add column delivery_days_min integer;
alter table online_delivery_zone add column delivery_days_max integer;
-- The estimate the customer was shown, kept on the order so later changes to the area do not change a placed order.
alter table online_order add column eta_days_min integer;
alter table online_order add column eta_days_max integer;
