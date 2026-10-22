-- Law Firm: escalation e-mail to the lead lawyer when a limitation date is close (off until the admin switches it on), and a note of how far each deadline has been escalated.
alter table law_settings add column escalation_enabled boolean default false not null;
alter table law_settings add column escalation_days integer default 14 not null;
alter table law_matter_deadline add column escalation_stage integer default 0 not null;
alter table law_matter_deadline add column escalated_at timestamp(6) with time zone;
