-- PO-6: receipt text, paper width, logo, QR, and a count of how many times each receipt was printed.
alter table pos_settings add column receipt_header character varying(300);
alter table pos_settings add column receipt_footer character varying(300) not null default 'Thank you!';
alter table pos_settings add column receipt_paper character varying(4) not null default '80';
alter table pos_settings add column receipt_show_logo boolean not null default true;
alter table pos_settings add column receipt_show_qr boolean not null default false;

alter table sale_order add column print_count integer not null default 0;

create table receipt_print (
    id bigserial primary key,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone,
    order_id bigint not null,
    copy_number integer not null,
    printed_by character varying(80)
);
create index receipt_print_order_idx on receipt_print (order_id);
create index receipt_print_copy_idx on receipt_print (copy_number, created_at);
