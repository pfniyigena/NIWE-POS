# NIWE ERP, version 1.0.0

Angular 18 frontend, Spring Boot 3.3.4 (Java 21) backend, PostgreSQL 16, schema owned by Flyway.
Version 1 is a feature freeze: no new modules. After this release only fixes and changes to the existing modules are made.

## What is in it

Core (always on): **Sales** (quotations, sale orders, invoices, credit notes, customers, customer payments), **Purchases** (requisitions, orders, bills, returns, suppliers, supplier payments), **Accounting** (chart of accounts for Rwanda and Chad, journal entries, cost centres, incomes, financial reports; other modules post here automatically).

Optional, switched on under Settings > Modules: POS, Hotel, Restaurant, Manufacturing, Subscriptions, Help Desk, Expenses, Assets, Installments, Time Tracking, Work Orders, Booking, Law Firm (matters, billing, client money, documents, client portal, reports), Real Estate, Property Management (owners, properties and units, tenants and leases, rent, maintenance, owner statements with ledger posting, VAT on the fee, e-mail, and the monthly run), and the HR modules (Employees, Organizational Structure, Attendance and Leaves, Contracts, Payroll).

Also: multiple branches and warehouses, batch/serial tracking, scheduled stock jobs and scheduled report e-mails, Settings > Backup, audit log, offline POS sales.

## The database

- The schema is **one file**: `backend/src/main/resources/db/migration/V1__baseline_schema.sql` (203 tables). It replaces the 23 migrations used during development. It was generated from the catalog of a database built by those 23 files and compared with it table by table (columns, constraints, indexes, foreign keys all identical).
- **Never edit V1 again.** Every later change is a new file, `V2__...sql`, in the same change as the entity.
- **Start on an empty database.** Create it (`createdb pos_app`), start the backend, and Flyway applies V1. Check that `flyway_schema_history` has one row.
- A database built by Hibernate before Flyway: start once with the profile `dbsync` (Flyway off, `ddl-auto=update`), stop, then start normally; Flyway marks it as version 1.
- A database that ran the development migrations (old V1 to V23) **must be dropped and recreated**: the new V1 has a different checksum and Flyway will refuse to start ("Migration checksum mismatch"). Only do this where there is no data worth keeping.

## Tested, and not tested

Tested in the build environment over the HTTP API against PostgreSQL 16 (scripted suites per module; for Property Management 177, 97, 104, 91, 64, 56, 52, 38 and 43 checks, all repeated on a database built only from the new V1), Hibernate schema validation against that database (30 entity classes checked in the last run; the full set earlier), and `ngc` on the frontend.

**Not tested** (do these on your machine, in this order):

1. **Flyway applying V1 to an empty database.** Maven Central is not reachable from the build environment, so Flyway itself never ran; V1 was applied with `psql`. This is the first thing to check.
2. A real SMTP server (a local test sink was used). Set it under Settings > Email, then send one statement to yourself.
3. The morning jobs firing on their own clock (for example the Property Management monthly statements at 06:40). Use the "run now" buttons first.
4. Real data, the Chad chart of accounts and phone/light-theme layouts.
5. The production Angular build (`ng build`) was seen to fail only on a bundle-size budget (the development build is clean); raise the budget in `frontend/angular.json` or lazy-load the large routes before building for deployment.

## Before you deploy

- `backend/src/main/resources/application-niwe.properties` holds a database password and a server IP in plain text and is tracked in git. Move them to environment variables, and change the password if the repository is shared. Replace the placeholder `app.jwt.secret` too.
- `backend/_verify-src.tgz` (tracked) and `backend/target/_verify-src.tgz` are leftover verification archives and can be deleted.
- Set `app.seed-demo-data=false` once real data is entered.

## Commit and tag (run these yourself)

```bash
cd "D:/2026/NIWE-GROUP/TECH LTD/ERP/codes/NIWE-POS"
git rm -f backend/_verify-src.tgz
git add -A
git status                      # check the list first
git commit -m "NIWE ERP 1.0.0: Law Firm, Real Estate, Property Management, single baseline migration"
git tag -a v1.0.0 -m "NIWE ERP version 1.0.0"
git push origin main --follow-tags
```
