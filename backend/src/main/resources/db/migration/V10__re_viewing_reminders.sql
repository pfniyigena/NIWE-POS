-- Real Estate: e-mail reminders before a viewing (off until the admin switches it on), and a note of who has been reminded.
alter table re_settings add column viewing_reminder_enabled boolean default false not null;
alter table re_settings add column viewing_reminder_hours integer default 24 not null;
alter table re_viewing add column client_reminded_at timestamp(6) with time zone;
alter table re_viewing add column agent_reminded_at timestamp(6) with time zone;
