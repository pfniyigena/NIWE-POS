-- PO-4: void a completed sale, return window / reasons / approval, and refunds counted against the drawer that paid them.
alter table pos_settings add column void_requires_approval boolean not null default true;
alter table pos_settings add column void_same_shift_only boolean not null default true;
alter table pos_settings add column return_window_days integer not null default 0;
alter table pos_settings add column return_approval_over numeric(38,2) not null default 0;
alter table pos_settings add column return_reason_required boolean not null default false;
alter table pos_settings add column return_reasons character varying(400) not null default 'Wrong size,Damaged or faulty,Changed mind,Wrong item,Other';

alter table sale_order add column voided_at timestamp(6) with time zone;
alter table sale_order add column void_reason character varying(200);
alter table sale_order add column voided_by character varying(80);
alter table sale_order add column void_approved_by character varying(80);

alter table sales_return add column register_session_id bigint;
alter table sales_return add column processed_by character varying(80);
alter table sales_return add column approved_by character varying(80);
alter table sales_return add column void_return boolean not null default false;
create index sales_return_session_idx on sales_return (register_session_id);
