-- HR-5: leave types can accrue monthly and carry unused days over; leave requests can start or end at midday (half days).
alter table leave_type add column accrual_mode character varying(10) not null default 'UPFRONT';
alter table leave_type add column carry_over_max_days integer not null default 0;
alter table leave_type add constraint leave_type_accrual_check check (accrual_mode in ('UPFRONT', 'MONTHLY'));
alter table leave_request add column start_half boolean not null default false;
alter table leave_request add column end_half boolean not null default false;
