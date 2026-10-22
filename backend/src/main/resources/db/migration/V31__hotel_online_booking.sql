-- HO-8: online booking page, deposit hold, guest e-mails and the per-room-type iCal feed.
alter table hotel_settings add column online_enabled boolean not null default false;
alter table hotel_settings add column online_intro text;
alter table hotel_settings add column online_deposit_percent numeric(5,2) not null default 0;
alter table hotel_settings add column online_hold_hours integer not null default 24;
alter table hotel_settings add column online_max_nights integer not null default 14;
alter table hotel_settings add column online_max_days_ahead integer not null default 365;
alter table hotel_settings add column online_max_per_phone_per_day integer not null default 3;
alter table hotel_settings add column online_payment_instructions text;
alter table hotel_settings add column mail_confirmation boolean not null default false;
alter table hotel_settings add column mail_prearrival boolean not null default false;
alter table hotel_settings add column mail_prearrival_days integer not null default 2;
alter table hotel_settings add column mail_thanks boolean not null default false;
alter table hotel_settings add column thanks_message text;
alter table hotel_settings add column review_url character varying(300);

alter table room_type add column online_visible boolean not null default true;
alter table room_type add column ical_token character varying(48);
create unique index uq_room_type_ical_token on room_type (ical_token) where ical_token is not null;

alter table hotel_booking add column confirmation_code character varying(16);
alter table hotel_booking add column online boolean not null default false;
alter table hotel_booking add column deposit_due numeric(38,2);
alter table hotel_booking add column hold_expires_at timestamp with time zone;
alter table hotel_booking add column confirmation_mailed_at timestamp with time zone;
alter table hotel_booking add column prearrival_mailed_at timestamp with time zone;
alter table hotel_booking add column thanks_mailed_at timestamp with time zone;
create unique index uq_hotel_booking_confirmation_code on hotel_booking (confirmation_code) where confirmation_code is not null;
create index idx_hotel_booking_online_hold on hotel_booking (hold_expires_at) where hold_expires_at is not null;
