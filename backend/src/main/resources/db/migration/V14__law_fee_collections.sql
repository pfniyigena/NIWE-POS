-- Law Firm: reminders to clients about overdue fees (the daily automatic run is off until the admin switches it on) and a note of who has been reminded.
alter table law_settings add column collections_enabled boolean default false not null;
alter table law_settings add column collections_first_days integer default 7 not null;
alter table law_settings add column collections_repeat_days integer default 14 not null;
alter table law_settings add column collections_max integer default 3 not null;
alter table law_settings add column default_term_days integer default 30 not null;
alter table law_bill add column reminder_count integer default 0 not null;
alter table law_bill add column last_reminded_at timestamp(6) with time zone;
