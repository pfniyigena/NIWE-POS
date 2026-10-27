-- v1.11.0: Promotions and Gift cards are switched on or off in Sales > Settings (Loyalty already has its own switch, set from the same screen).
alter table sales_settings add column promotions_enabled boolean not null default false;
alter table sales_settings add column gift_cards_enabled boolean not null default false;

-- the settings row is created here so that reading the switches never has to insert it
insert into sales_settings (created_at)
select now() where not exists (select 1 from sales_settings);

-- installations that already use them keep them on
update sales_settings set promotions_enabled = true where exists (select 1 from promotion);
update sales_settings set gift_cards_enabled = true where exists (select 1 from gift_card);
