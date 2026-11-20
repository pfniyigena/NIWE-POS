-- v1.59.0: a private link and a 4-digit PIN for a delivery driver, so he can confirm deliveries on his phone without a staff login.
-- access_token is the secret in the link (null = switched off); the PIN is kept hashed; a session is kept hashed too, one per driver.
alter table online_driver add column access_token varchar(64);
alter table online_driver add column access_created_at timestamp(6) with time zone;
alter table online_driver add column access_last_used_at timestamp(6) with time zone;
alter table online_driver add column pin_hash varchar(100);
alter table online_driver add column pin_failures integer not null default 0;
alter table online_driver add column pin_failed_at timestamp(6) with time zone;
alter table online_driver add column session_hash varchar(64);
alter table online_driver add column session_expires_at timestamp(6) with time zone;
create unique index online_driver_access_token_idx on online_driver (access_token) where access_token is not null;
