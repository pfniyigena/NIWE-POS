-- PO-8: register setup and permissions
alter table register add column active boolean not null default true;
alter table register add column default_float numeric(38,2);
alter table register add column max_float numeric(38,2);

create table register_cashier (
    register_id bigint not null,
    app_user_id bigint not null,
    constraint register_cashier_pkey primary key (register_id, app_user_id),
    constraint fk_register_cashier_register foreign key (register_id) references register(id),
    constraint fk_register_cashier_user foreign key (app_user_id) references app_user(id)
);

alter table user_permissions drop constraint user_permissions_permissions_check;
alter table user_permissions add constraint user_permissions_permissions_check check (permissions in (
    'CONFIRM_RESTAURANT_WASTAGE', 'APPROVE_STOCK_ADJUSTMENT', 'APPROVE_PURCHASE_REQUISITION', 'OVERRIDE_DISCOUNT_CAP',
    'APPROVE_EXPENSE', 'APPROVE_STOCK_TRANSFER', 'APPROVE_POS_VOID_RETURN', 'APPROVE_POS_PRICE', 'APPROVE_POS_CASH'));
