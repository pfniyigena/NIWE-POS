-- PO-3: numbered Z reports, frozen when the shift is closed, and a count of how often each was printed.
alter table register_session add column z_number integer;
alter table register_session add column z_snapshot text;
alter table register_session add column z_prints integer not null default 0;
create unique index register_session_z_unique on register_session (register_id, z_number) where z_number is not null;
