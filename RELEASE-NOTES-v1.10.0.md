# NIWE ERP, version 1.10.0

Manufacturing improvements (six steps) and nine general changes. No new module; versions 1.0.0 to 1.9.0 stay the base (see their release notes).

## What changed

**Manufacturing (MF-1 to MF-6)**

- **Lists, exports and numbering (MF-1).** Search, filters, paging and Excel / PDF / CSV on orders, recipes, routings, workstations, indirect costs and production plans; numbers MO-0001 and PLAN-0001 from a database sequence.
- **Labour and machine cost (MF-2).** Labour rate per routing step; a confirmed order keeps a copy of its steps and a planned cost; every production run charges minutes x rate and posts it to Accounting.
- **Shortage check and order editing (MF-3).** Component availability before you start, edit a planned order, copy an order.
- **Scrap, yield and by-products (MF-4).** Scrap quantity and reason, actual component use, by-products with a cost share.
- **Manufacturing reports (MF-5).** Six reports with Excel / PDF / CSV.
- **Recipe cost, versions and job card (MF-6).** Cost per unit and margin on each recipe, recipe versions (copy and make active), printable job card.

**General changes**

1. **Quantities can be decimal everywhere** (2.5 kg, 0.75 m), up to four decimals. Serial-numbered goods and registered assets stay in whole units.
2. **Help Desk: Allow public website** in its settings, as Real Estate has.
3. **Reserve a Table: Allow public website** in its settings.
4. **Book an Appointment: Allow public website** in its settings.
5. **(c) 2026 NIWE GROUP** on every screen and public page.
6. **Appearance and theme are saved in the database** per user, and an administrator can apply the choice to the public portal.
7. **Print Service Card** on the employee record.
8. **Every employee can get a login** and sees his or her own HR (My HR).
9. **Rwanda chart:** tax codes A (Exempted, 0%) and B (VAT 18%).

The details of each item are in `MANUFACTURING-CHANGES.md` and `GENERAL-CHANGES-v1.10.0.md`, and in the README entry "Version 1.10.0".

## Database

Six migrations were added after `V44__pos_register_setup.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Content |
|---|---|
| `V45__manufacturing_numbering.sql` | order and plan number sequences |
| `V46__manufacturing_labour_machine_cost.sql` | labour rates, copied steps, planned cost |
| `V47__manufacturing_scrap_byproducts.sql` | scrap, actual use, by-products |
| `V48__manufacturing_recipe_versions.sql` | recipe versions |
| `V49__public_switches_theme.sql` | public-website switches for Help Desk and Reserve a Table; saved themes |
| `V50__decimal_quantities.sql` | quantity columns become `numeric(19,4)` |

## Upgrading an installation that ran 1.9.0

Back up the database, install the new build and start it. Things to know:

- V50 changes the type of many quantity columns in one go; on a large database it takes a moment and locks those tables while it runs. Whole numbers already stored are kept as they are.
- The new public-website switches for Help Desk and Reserve a Table are **on** after the upgrade, so nothing closes by itself. Switch them off in the module's settings if you do not want the public page.
- Existing installations on the Rwanda chart: run the chart setup again, or add tax codes A (0%) and B (18%) by hand.
- Employee logins are created only when you ask for them (employee page, or "Give everyone a login" on the list).

## Wiring the screens

After unpacking, run from the repository folder: `python3 tools/wire-mf.py frontend/src/app` and `python3 tools/wire-v110.py frontend/src/app` (both safe to run twice), then `python3 tools/apply-v1100.py`.

## Tested, and not tested

The whole set (all earlier modules included, 146 suites and about 9,000 checks over the HTTP API and in a browser) was re-run on a frozen copy of the code with no failure. The first full run found four problems, fixed before this release: buy-X-get-Y counted part of a group as a group, a manufacturing order lowered to exactly what was produced did not close, several messages showed quantities as "5.0000", and the cash-drawer reason list had no fixed order.

Not tested: Flyway itself applying V1 to V50 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a very large database for V50, real printers for the service card and job card.

## Known limits

- The Service Card is a staff ID-style A4 sheet without a photo.
- An employee login has the Employee role and opens My HR, but, like any signed-in cashier-level user, can still read the basic POS lists (products, customers, categories, registers). Say so if you want that locked down.
- Buy X get Y counts whole groups only: with buy 2 get 1, 2.5 kg earns no free unit, 3 earns one.
- Goods tracked by serial number can only be handled in whole units; assets are registered in whole units.
