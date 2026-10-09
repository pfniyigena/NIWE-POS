-- seed_100k_products.sql
--
-- Generates 100,000 sample products for load-testing the admin product list and the terminal's
-- product search/grid (Task: "I want to check the loading of 100000 products. Feed the sample of
-- products."). This is what motivated pagination on GET /api/products/all and the 200-row cap on
-- GET /api/products (see CatalogService.TERMINAL_SEARCH_CAP, CatalogRepository.searchAllForAdmin)
-- in this same pass, alongside the new `code`/`iconDataUrl` columns.
--
-- RUN THIS AFTER STARTING THE BACKEND AT LEAST ONCE against this database — spring.jpa.hibernate
-- .ddl-auto=update only adds a new column (originally `code`/`icon_data_url`; now also
-- `unsp_code`/`service`, added for EFRIS Rwanda/Chad) the first time the app boots with the
-- updated Product entity. Running this script against an older schema fails with
-- "column ... does not exist".
--
-- Usage (adjust host/user/db to match backend/src/main/resources/application.properties):
--   psql -h localhost -U postgres -d pos_app -f backend/scripts/seed_100k_products.sql
--
-- One set-based INSERT ... SELECT over generate_series rather than 100,000 individual
-- statements — this finishes in a couple of seconds instead of minutes, and is exactly the kind
-- of bulk load that would be painfully slow to do 100,000 times through the REST API instead.
--
-- Data shape, deliberately kept simple:
--   * category_id / tax_rate_id are left NULL (uncategorized, tax-exempt) so this script has no
--     foreign keys to satisfy against whatever categories/tax rates already exist in your
--     database. The point of this data is to exercise the LISTING/paging/search code paths, not
--     checkout — if you want these filed under a real category, fill in a literal category_id
--     below and re-run (safe to run more than once; it only ever adds rows).
--   * barcode is set on 1 in 3 rows, `code` on 1 in 5 (both unique per row by construction, since
--     each embeds the row's own generated-series number) — most real catalogs don't assign a
--     second code to every single item, so this leaves plenty of NULLs to confirm "code can be
--     null but unique" actually holds under bulk data, not just in the form.
--   * iconDataUrl is set on only 1 row in 500, using a tiny 1x1-pixel placeholder GIF, purely so
--     the admin list's thumbnail column and the terminal tile's icon both have at least a few
--     real rows to render — 100,000 real product photos as base64 would bloat this script (and
--     your database) for no testing benefit.
--   * unsp_code (EFRIS's UNSPSC-style commodity/tariff code, "unsp_code" for EFRIS Rwanda,
--     "classification_code" for EFRIS Chad) is set on 1 in 10 rows, an 8-digit placeholder code —
--     enough NULL/non-NULL mix to exercise a real EFRIS submission's per-line lookup without
--     pretending every product has been through real UNSPSC classification.
--   * service (EFRIS Chad's item_nature: goods vs. service) is true on 1 in 10 rows — most of a
--     typical POS catalog is physical goods, so this keeps that the overwhelming majority while
--     still giving the admin product list/form a realistic few services to show.

BEGIN;

INSERT INTO product (
    created_at, updated_at, sku, barcode, code, icon_data_url, name,
    unit_price, cost_price, stock_quantity, active, category_id, tax_rate_id, unsp_code, service
)
SELECT
    now(),
    now(),
    'SKU-' || lpad(i::text, 6, '0'),
    CASE WHEN i % 3 = 0 THEN '2' || lpad(i::text, 11, '0') ELSE NULL END,
    CASE WHEN i % 5 = 0 THEN 'CODE-' || lpad(i::text, 6, '0') ELSE NULL END,
    CASE WHEN i % 500 = 0
        THEN 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBTAA7'
        ELSE NULL
    END,
    'Sample product ' || i,
    round((random() * 100 + 1)::numeric, 2),
    round((random() * 50)::numeric, 2),
    (random() * 200)::int,
    true,
    NULL,
    NULL,
    CASE WHEN i % 10 = 0 THEN '43' || lpad((i % 1000000)::text, 6, '0') ELSE NULL END,
    (i % 10 = 0)
FROM generate_series(1, 100000) AS i;

COMMIT;

-- Sanity checks to run afterward:
--   select count(*) from product;                        -- should include +100000 over whatever you had
--   select count(*) from product where code is not null;  -- ~20000, all distinct
--   select count(*) from product where icon_data_url is not null; -- ~200
--   select count(*) from product where unsp_code is not null; -- ~10000
--   select count(*) from product where service = true;    -- ~10000
