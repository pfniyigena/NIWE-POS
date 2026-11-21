-- v1.72.0: cash a driver collects at the door is booked to "Cash with Drivers" until he hands it in. driver_cash_booked is the amount booked there for the order
-- (orders delivered before this version have none: their cash went to Cash and Bank at delivery, so only a difference is posted when they are settled).
alter table online_order add column driver_cash_booked numeric(14,2) not null default 0;
