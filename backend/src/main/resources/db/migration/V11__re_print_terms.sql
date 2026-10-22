-- Real Estate: the agency's own wording for the printed offer letter, reservation letter and sale agreement (empty = the standard wording).
alter table re_settings add column offer_terms varchar(3000);
alter table re_settings add column reservation_terms varchar(3000);
alter table re_settings add column agreement_terms varchar(3000);
