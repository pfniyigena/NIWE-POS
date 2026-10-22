-- Real Estate: the morning follow-up e-mail to each agent (off until the admin switches it on) and a note of its last run.
alter table re_settings add column follow_up_email_enabled boolean default false not null;
alter table re_settings add column follow_up_last_run date;
alter table re_settings add column follow_up_last_summary character varying(500);
