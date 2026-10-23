-- MF-6: recipe versions (numbered per product, one active at a time is kept by the Activate action).
alter table bom add column version integer not null default 1;
update bom b set version = r.rn from (select id, row_number() over (partition by product_id order by id) as rn from bom) r where r.id = b.id;
