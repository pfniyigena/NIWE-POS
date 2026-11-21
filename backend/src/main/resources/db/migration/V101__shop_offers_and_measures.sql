-- v1.69.0: the shop applies the price list and the promotions of the ERP when a product has no online price (the result is kept on the listing, so searching and
-- sorting by price agree with what the card shows), and a product can be sold by weight or length (decimal quantities).
alter table online_store_settings add column price_list_id bigint;
alter table online_listing add column offer_price numeric(19,2);
alter table online_listing add column offer_note varchar(160);
alter table online_listing add column measure_unit varchar(12);
alter table online_cart_line alter column quantity type numeric(12,3);
