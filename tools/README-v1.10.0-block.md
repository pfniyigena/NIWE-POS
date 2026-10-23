### Version 1.10.0 - Manufacturing improvements and general changes, version 1.10.0

Six steps in the Manufacturing module plus nine general changes (see `RELEASE-NOTES-v1.10.0.md` for the summary, database and upgrade notes; `MANUFACTURING-CHANGES.md` has the full detail of each step).

- **Lists, exports and numbering.** `page`, `size`, `q` and filters on `/api/manufacturing/orders | boms | routings | workstations | overhead-costs | production-plans` (total in `X-Total-Count`); Excel, PDF and CSV at `.../export`; order numbers `MO-0001` and plan numbers `PLAN-0001` from database sequences; migration V45.
- **Labour and machine cost.** Labour rate per hour on each routing step; a confirmed order keeps a copy of its steps and a planned cost; each production run charges minutes x rate for labour and machines (optional actual minutes per step in `operationMinutes`), included in the run's cost and absorbed into Inventory in Accounting; migration V46.
- **Shortage check and order editing.** `GET /api/manufacturing/orders/{id}/availability`, `PUT /api/manufacturing/orders/{id}`, `POST /api/manufacturing/orders/{id}/copy`.
- **Scrap, yield and by-products.** `quantityScrapped`, `scrapReason` and `componentQuantities` on `POST .../orders/{id}/produce`; recipe `byProducts` with a cost share; migration V47.
- **Manufacturing reports.** `/api/reports/manufacturing-runs | manufacturing-cost-per-product | manufacturing-component-use | manufacturing-workstations | manufacturing-scrap | manufacturing-order-plan` with Excel, PDF and CSV; page Manufacturing > Reports.
- **Recipe cost, versions and job card.** Cost per unit and margin on every recipe; `POST /api/manufacturing/boms/{id}/copy` and `/activate`; `GET /api/manufacturing/orders/{id}/job-card` (A4 PDF); migration V48.

General changes in 1.10.0 (details in `GENERAL-CHANGES-v1.10.0.md`):

- **Decimal quantities everywhere.** Quantities are `BigDecimal`, stored as `numeric(19,4)` (migration V50): sales, invoices, orders, purchases, returns, stock, transfers, counts, adjustments, warehouses, manufacturing, restaurant, reports and exports. Screens accept values such as 2.5. Serial-numbered goods and registered assets stay in whole units.
- **Allow public website** in the settings of Help Desk (`/api/helpdesk/settings`, `publicEnabled`) and Reserve a Table (`/api/restaurant/settings`, `publicEnabled`), like Booking and Real Estate. On by default; off closes the public page and API and hides the tile on the home page (migration V49).
- **Copyright line** "(c) 2026 NIWE GROUP" on every screen and public page.
- **Appearance saved in the database** (`GET/PUT /api/me/theme`, table `user_theme`); an administrator can apply the choice to the public portal (`site_theme`, `GET /api/public/settings/theme`).
- **Print Service Card** on the employee record (`GET /api/hr/employees/{id}/service-card`, A4 PDF).
- **Employee logins.** `POST /api/hr/employees/{id}/login`, `POST /api/hr/employees/logins` (everyone without one), `POST /api/hr/employees/{id}/login/reset-password`. The login has the Employee role and opens My HR.
- **Rwanda chart of accounts** now creates tax codes A (Exempted, 0%) and B (VAT 18%).
