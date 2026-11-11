-- seed_100k_products.sql
--
-- Adds sample products for load-testing the admin product list, the POS product search and grid, and the
-- stock screens (Task: "I want to check the loading of 100000 products. Feed the sample of products.").
-- Updated for NIWE ERP 1.27: the schema is now built by Flyway (not ddl-auto), products must say whether they track
-- stock, and stock is held per warehouse location in product_stock.
--
-- BEFORE YOU RUN IT: start the backend at least once against this database, so Flyway has built the tables and the
-- default "Main" warehouse (with its SALES location) exists. The script stops with a message if it does not.
--
-- Usage: plain SQL, so it runs the same in psql, pgAdmin, DBeaver or any other SQL tool (it uses no psql commands).
--   psql -h localhost -U postgres -d pos_app -f backend/scripts/seed_100k_products.sql
--   or open it in the query tool of your database, connect to the application's database, and run the whole script.
-- A different number of products: change 100000 in the generate_series(1, 100000) line below (the only place it appears).
--
-- Safe to run again: every sample product has a fixed number (SKU-000001 ... SKU-100000). A product that is already
-- there is left alone, and only the missing ones are added, so a second run with the same count adds nothing.
-- Your own products are never touched.
--
-- What it creates, per product i:
--   * product: sku SKU-<i>, name "Sample product <i>", a selling price of 1 to 101, a cost price of 30 to 80 percent of
--     that price, active, with a barcode on 1 in 3 and a code on 1 in 5 (both unique, built from i), a tiny placeholder
--     picture on 1 in 500, and a UNSPSC-style code on 1 in 10. 1 in 10 are services (no stock tracked, like a real
--     catalogue). The rest track stock and are marked tracking mode NONE.
--   * product_stock: for each product that tracks stock, a row of 0 to 200 units at the primary warehouse's active
--     SALES location, and the same quantity on the product itself (the two must agree, as they do when stock is
--     received through the application). So the stock screens, the shop's availability and the POS all see stock.
--   * category: if you already have categories, products are spread across them (round robin) so the category filters
--     have something to show; with none, products are uncategorised. Tax rate is left empty (tax exempt), so these
--     products can be sold in tests without a tax setup.
--
-- It is one set-based INSERT ... SELECT per table, not 100,000 separate statements: well under a minute for 100,000 products.
--
-- To remove the sample products again (only works for ones never sold or used on a document; the others are kept):
--   delete from product_stock where product_id in (select id from product where sku ~ '^SKU-[0-9]{6}$' and name like 'Sample product %');
--   delete from product where sku ~ '^SKU-[0-9]{6}$' and name like 'Sample product %'
--     and not exists (select 1 from sales_order_line l where l.product_id = product.id);
-- (add the same "not exists" test for any other table that refers to the product if the delete reports a foreign key error).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM warehouse_location l JOIN warehouse w ON w.id = l.warehouse_id
                    WHERE w.is_primary AND w.active AND l.active AND l.location_type = 'SALES') THEN
        RAISE EXCEPTION 'No primary warehouse with an active SALES location was found. Start the backend once against this database (it creates the Main warehouse), then run this script again.';
    END IF;
END $$;

BEGIN;

INSERT INTO product (
    created_at, updated_at, sku, barcode, code, icon_data_url, name,
    unit_price, cost_price, stock_quantity, active, category_id, tax_rate_id, unsp_code,
    service, track_stock, tracking_mode
)
SELECT
    now(),
    now(),
    'SKU-' || lpad(s.i::text, 6, '0'),
    CASE WHEN s.i % 3 = 0 THEN '2' || lpad(s.i::text, 11, '0') ELSE NULL END,
    CASE WHEN s.i % 5 = 0 THEN 'CODE-' || lpad(s.i::text, 6, '0') ELSE NULL END,
    CASE WHEN s.i % 500 = 0
        THEN 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBTAA7'
        ELSE NULL
    END,
    'Sample product ' || s.i,
    s.price,
    round((s.price * (0.3 + random() * 0.5))::numeric, 2),
    CASE WHEN s.i % 10 = 0 THEN 0 ELSE (random() * 200)::int END,
    true,
    CASE WHEN c.ids IS NULL THEN NULL ELSE c.ids[1 + (s.i % cardinality(c.ids))] END,
    NULL,
    CASE WHEN s.i % 10 = 0 THEN '43' || lpad((s.i % 1000000)::text, 6, '0') ELSE NULL END,
    (s.i % 10 = 0),
    (s.i % 10 <> 0),
    CASE WHEN s.i % 10 = 0 THEN NULL ELSE 'NONE' END
FROM (SELECT i, round((random() * 100 + 1)::numeric, 2) AS price FROM generate_series(1, 100000) AS i) AS s
CROSS JOIN (SELECT array_agg(id ORDER BY id) AS ids FROM category) AS c
WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.sku = 'SKU-' || lpad(s.i::text, 6, '0'))
ON CONFLICT DO NOTHING;

-- Stock by location for the sample products that track stock and have none there yet; it mirrors the product's own quantity.
INSERT INTO product_stock (created_at, updated_at, product_id, location_id, quantity)
SELECT now(), now(), p.id, loc.id, p.stock_quantity
  FROM product p
 CROSS JOIN (SELECT min(l.id) AS id
               FROM warehouse_location l JOIN warehouse w ON w.id = l.warehouse_id
              WHERE w.is_primary AND w.active AND l.active AND l.location_type = 'SALES') AS loc
 WHERE p.sku ~ '^SKU-[0-9]{6}$'
   AND p.name LIKE 'Sample product %'
   AND p.track_stock
   AND NOT EXISTS (SELECT 1 FROM product_stock ps WHERE ps.product_id = p.id AND ps.location_id = loc.id)
ON CONFLICT DO NOTHING;

COMMIT;

ANALYZE product;
ANALYZE product_stock;

-- What is there now (one row):
SELECT (SELECT count(*) FROM product WHERE sku ~ '^SKU-[0-9]{6}$' AND name LIKE 'Sample product %')                          AS sample_products,
       (SELECT count(*) FROM product WHERE sku ~ '^SKU-[0-9]{6}$' AND name LIKE 'Sample product %' AND code IS NOT NULL)      AS with_code,
       (SELECT count(*) FROM product WHERE sku ~ '^SKU-[0-9]{6}$' AND name LIKE 'Sample product %' AND service)               AS services,
       (SELECT count(*) FROM product WHERE sku ~ '^SKU-[0-9]{6}$' AND name LIKE 'Sample product %' AND category_id IS NOT NULL) AS categorised,
       (SELECT count(*) FROM product_stock ps JOIN product p ON p.id = ps.product_id
         WHERE p.sku ~ '^SKU-[0-9]{6}$' AND p.name LIKE 'Sample product %')                                                   AS stock_rows,
       (SELECT count(*) FROM product_stock ps JOIN product p ON p.id = ps.product_id
         WHERE p.sku ~ '^SKU-[0-9]{6}$' AND p.name LIKE 'Sample product %' AND ps.quantity <> p.stock_quantity)               AS stock_rows_that_disagree;
