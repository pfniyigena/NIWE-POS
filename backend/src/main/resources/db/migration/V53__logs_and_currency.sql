-- v1.14.0: the sign-in log, and the currency that follows the chart of accounts

create table login_event (
    id bigserial primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    username varchar(100),
    user_id bigint,
    success boolean not null,
    reason varchar(80),
    ip varchar(64),
    device_id varchar(64),
    user_agent varchar(255)
);
create index idx_login_event_created on login_event (created_at desc);
create index idx_login_event_username on login_event (lower(username));

-- A store that chose the Rwanda or Chad chart but never changed the currency (still the untouched USD / $) gets the franc of its country.
-- A currency somebody typed in is never touched.
update store_settings set currency_code = 'RWF', currency_symbol = 'RWF'
 where chart_country = 'RWANDA' and currency_code = 'USD' and currency_symbol = '$';
update store_settings set currency_code = 'XAF', currency_symbol = 'XAF'
 where chart_country = 'CHAD' and currency_code = 'USD' and currency_symbol = '$';
