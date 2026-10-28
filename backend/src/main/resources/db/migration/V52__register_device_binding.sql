-- v1.13.0: a till (register) can be tied to one known device.
-- device_id   : the random id the browser on that PC keeps (X-Device-Id header)
-- device_ip   : the address that PC had when it was bound; null = the address is not checked
-- no device_id = not bound = any device may work the till (how every existing till keeps behaving)
alter table register add column device_id varchar(64);
alter table register add column device_name varchar(120);
alter table register add column device_ip varchar(64);
alter table register add column device_bound_at timestamp(6) with time zone;
alter table register add column device_bound_by varchar(120);
create unique index uq_register_device_id on register (device_id) where device_id is not null;
