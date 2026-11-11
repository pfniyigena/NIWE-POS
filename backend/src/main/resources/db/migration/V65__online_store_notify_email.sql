-- v1.28.0: the e-mail address that is told when a customer places an online order.
alter table online_store_settings add column notify_email character varying(160);
