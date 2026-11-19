-- v1.50.0: product variants on the shop. A product that the Products screen made into a template (Size x Colour ...) is put on the shop as one product
-- with option pickers; each variant keeps its own product, stock, price and listing row. variant_off = staff switched this one option off on the shop.
alter table online_listing add column variant_off boolean not null default false;
