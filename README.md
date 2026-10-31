# POS

A standalone point-of-sale application: **Angular** frontend, **Spring Boot** REST API backend,
**PostgreSQL** database. This is a clean break from the earlier Thymeleaf-based `smb-erp` project —
it doesn't depend on that codebase or database at all, and can be developed, deployed, and scaled
independently.

### Version 1.22.2 - First-start administrator, 404 and 405 answers

Found in a clean-install rehearsal: with demo data switched off (`app.seed-demo-data=false`) a new database had no users and nobody could sign in. Now, only when there are no users at all, the administrator `admin` is created, with the password in `app.initial-admin-password` or a random one written once to the log (line `FIRST START`). Mistyped addresses under `/api` now answer 404, and wrong methods 405, instead of 500. No migration, no screen changes. Details in `RELEASE-NOTES-v1.22.2.md`.

### Version 1.22.1 - Subscriptions: month-end billing fix

Subscriptions that start on the 29th, 30th or 31st no longer drift to the 28th after a short month: 31 January now bills 28 February, 31 March, 30 April and so on, in the same way for quarterly and yearly plans (29 February returns in leap years). Invoice periods join up, the plan-change quote uses the true period length, and subscriptions that had already drifted are put back by the migration. One migration (V62), no screen changes. Details in `RELEASE-NOTES-v1.22.1.md`.

### Version 1.22.0 - Assets: register search and files, cost and value changes, custody and verification, overview and reports

The asset register now has search, filters and quick views, several photos and files per asset, warranty and insurance dates with expiry warnings, printable QR tags and a history of everything done to an asset. Value changes after purchase are supported: improvements, write-downs, new depreciation terms, taking back the last month, and undoing a disposal. Assets can be handed to staff (with a handover sheet), checked in physical counting rounds, written off when missing, and disposals above a limit need approval. There is an overview page, a depreciation forecast and five new reports, and each category can have its own accounts. One migration (V61), a new permission (Approve large asset disposals) and four new pages under Assets (Overview, Verification, Settings, Print tags). Details in `RELEASE-NOTES-v1.22.0.md`.

### Version 1.21.0 - Expenses: VAT, payment accounts, bills with part-payments, budgets, approval rules, files, overview, reports

Expenses now keep recoverable VAT in its own account, can be paid from a bank or mobile money account, and unpaid bills have due dates, part-payments and an overdue view. Every expense gets a number and a supplier reference, the list has search, filters and quick views, several files can be attached, a pending or rejected expense can be deleted, and several can be approved or rejected at once. Monthly budgets per category or cost center, an approval limit, a "not your own expense" rule and a new Expenses overview and four new reports are included. One migration (V60), a new permission (Approve large expenses) and four new pages under Expenses (Overview, Budgets, Reports, Settings). Details in `RELEASE-NOTES-v1.21.0.md`.

### Version 1.20.0 - Help Desk: deadlines (SLA), replies and files, overview, reports, workflow and history

Help Desk tickets now carry first-reply and resolution deadlines by priority (with a "waiting for the customer" status that pauses the clock, warning and escalation e-mails and automatic closing of old resolved tickets), agents can reply to the customer by e-mail with saved replies and attach files, and customers can answer, add files and rate the help on the public ticket page. There is a new Overview, five new reports, ticket views and bulk actions, a full history, tags, links to an invoice or a job, and merging of duplicate tickets. One migration (V59) and two new pages under Help Desk (Overview, Saved replies). Details in `RELEASE-NOTES-v1.20.0.md`.

### Version 1.19.0 - Subscriptions: edit and change plan, trials and terms, pauses, overview, reports, overdue reminders

Subscriptions can now be edited and moved to another plan with proration, sold per seat with a setup fee, started with a free trial or a fixed term, paused with a reason and an automatic resume date, skipped for one cycle and cancelled at the end of the paid period. There is a new Overview (recurring revenue, churn, what bills next and what is unpaid), list filters, three reports and optional e-mail reminders for overdue subscription invoices. One migration (V58) and two new pages under Subscriptions (Overview, Reports). Details in `RELEASE-NOTES-v1.19.0.md`.

### Version 1.18.0 - Installments: collections, early payoff, reversals, default and write-off, statements

Installments now has a Collections page (who to chase, with a log of calls, visits and promises to pay), early payoff with an interest waiver, reversal of a wrongly recorded payment, defaulting and writing off a plan, and a printable or e-mailed statement and agreement. One migration (V57) and a new page under Installments > Collections. Details in `RELEASE-NOTES-v1.18.0.md`.

### Version 1.17.0 - Booking: reminders, guest rescheduling, working hours and leave

Booking now e-mails guests a reminder before the visit and a "moved" notice (with a calendar file attached), lets guests move their own booking on the public page, shows a guest's earlier visits and no-shows on the appointment, prints a day sheet, and respects each person's own working hours and leave when offering times. One migration (V56) and a new page under Booking > Working hours. Details in `RELEASE-NOTES-v1.17.0.md`.

### Version 1.16.0 - Time Tracking: timesheet discipline and project control

Time Tracking now helps keep timesheets complete and projects on budget: a timer left running is stopped automatically (and a warning shows while it runs), *Copy last week* starts a week from the one before, a lock date stops changes to past weeks, a *Not submitted* list shows who has not handed in their week and can e-mail a reminder (by hand or every week), projects can be limited to a team, and budget warnings (with an optional hard stop) show on the project list and when logging time. One migration (V55). Details in `RELEASE-NOTES-v1.16.0.md`.

### Version 1.15.0 - Work Orders: job card and customer PDFs, estimate approval, board, calendar, planned maintenance, photos

Work Orders now prints a job card for the technician, an estimate and a completion note for the customer, and can require the customer's approval of an estimate before work starts. Jobs can be seen on a status board and a week calendar (drag to start, hold or re-schedule), repeat automatically through planned-maintenance schedules (a check runs every morning), and carry notes, photos and files, a history and a timer. The customer can be e-mailed the estimate and the completion note. One migration (V54). Details in `RELEASE-NOTES-v1.15.0.md`.

### Version 1.14.0 - Server logs, sign-in log, business name on module requests, currency by chart

The server now writes rotating log files (`niwe-erp.log`, `niwe-erp-error.log`, `niwe-access.log`) with a request id and user on every line; administrators read and download them under *Settings > Server logs* and see every sign-in and failed attempt under *Settings > Sign-in log*. More actions are recorded in the audit log (customers, categories and brands, imports and exports, shifts, time tracking, own profile, help desk settings and more). A module request now carries the business name in its text and e-mail subject. Choosing the Rwanda chart sets the currency to RWF and the Chad chart to XAF while the currency is still the untouched default. One migration (V53). Details in `RELEASE-NOTES-v1.14.0.md`.

### Version 1.13.0 - A till tied to one known device

A register (till) can be tied to one device. An administrator at the till PC presses **Bind to this device** on *POS > Registers & Terminal* (name for the PC, and whether its address must also match); any other device is then refused by the server: the terminal, sales, opening a shift, offline sync and cash movements, with a clear message. **Unbind** (administrator, from any device) frees it. Reports, receipts, history and closing a shift stay open from the back office. A device is the till of one register only. Audit log entries for bind and unbind. Tills that are not bound behave as before. One migration (V52). Details in `RELEASE-NOTES-v1.13.0.md`.

### Version 1.12.0 - Thermal (Epson ESC/POS) printing and the COM customer display

POS and Restaurant now print for thermal roll printers, and the till can drive a two-line customer display on a COM (serial) port. Full set-up guide in `THERMAL-PRINTING.md`, summary in `RELEASE-NOTES-v1.12.0.md`.

- **Devices page** (`/devices`, POS menu): per PC, in the browser. Receipt, kitchen and bar printers: Windows driver, USB (WebUSB) or serial / COM (Web Serial), 80 mm or 58 mm, characters per line, code page, cut, copies; test page; cash drawer button and pin.
- **Direct ESC/POS** (Chrome / Edge): sale receipt (with logos, EFRIS block and native QR), return receipt, restaurant bill and receipt, X / Z shift reports, kitchen / bar tickets per station, cash drawer pulse on a cash sale. A printer that does not answer falls back to the Windows driver with a message.
- **Windows driver mode** now prints exactly the roll width, black and bold, without margins or blank paper after the receipt (`core/hardware/thermal-css.ts`).
- **Customer display on a COM port**: item and price as rung up, then the running total, PAID / CHANGE and THANK YOU, welcome text when idle; ESC/POS (Epson DM-D and compatible), CD5220 or plain text; baud rate, data bits, parity and stop bits are settings.
- **Restaurant**: "Send to kitchen" can print one ticket per station on the printer that takes the station; buttons Bill (pre-bill) and Tickets (reprint) on the order screen.
- No server change and no migration.

### Version 1.11.0 - Receipt tax ID, Dashboard paging, sales feature switches

Three changes (see `RELEASE-NOTES-v1.11.0.md` for the summary, database and upgrade notes).

- **Customer tax ID on the POS receipt.** The printed receipt and the emailed receipt PDF show "Customer tax ID" under the customer when the customer has one (`customerTaxId` on the order).
- **Low-stock paging on the Dashboard.** The low-stock products and the per-warehouse low-stock lists show 10 rows per page with a pager.
- **Promotions, Gift cards and Loyalty points switched on in Sales > Settings.** `GET/PUT /api/sales/settings` (administrator) carries `promotionsEnabled`, `giftCardsEnabled` and `loyaltyEnabled`; `GET /api/sales/features` (any signed-in user) tells the screens which are on. Switched off = hidden in the menu, till and checkout and refused by the server for new use (existing data and refunds stay). Migration V51: after upgrade Promotions are on if any promotion exists, Gift cards on if any card exists, Loyalty keeps its setting; fresh installs start with all three off.

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

### Version 1.9.0 - POS improvements, version 1.9.0

Eight steps in the POS module (see `RELEASE-NOTES-v1.9.0.md` for the summary, database and upgrade notes; `POS-CHANGES.md` has the full detail of each step).

- **Lists and exports.** `page`, `size`, `q` and filters on receipts, returns, shifts and cash movements (total in `X-Total-Count`); Excel, PDF and CSV of the same filtered lists.
- **Cash control.** `/api/sessions/{id}/cash-movements` (paid in, paid out, safe drops with reasons and manager approval above a limit), cash counts, closing with a denominations count and variance note, accounting entries for the movements; `/api/pos/settings`; migration V39.
- **X and Z reports.** Printable X (mid-shift) and Z (closing, numbered) reports per shift and per cashier; migration V40.
- **Void and return controls.** `POST /api/returns/void-sale`, reasons and manager approval rules, refunds charged to the drawer that pays them (`sales_return.register_session_id`), `/api/pos/exceptions` log; migration V41.
- **Discounts and price changes at the till.** Cashier allowance, manager approval by PIN, reasons, whole-sale discount (`POST|DELETE /api/orders/{id}/discount`), price change on a line; migration V42.
- **Receipts.** 80 mm / 58 mm paper, header and footer text, logo, QR code of the invoice number, original vs COPY marking (`POST /api/orders/{id}/receipt-print`); migration V43.
- **POS reports.** `/api/reports/pos-by-cashier | pos-by-hour | pos-by-payment | pos-exceptions | pos-shift-comparison` with Excel, PDF and CSV.
- **Register setup and permissions.** Per-register on/off, usual and highest opening float, list of allowed cashiers; three new approval permissions (`APPROVE_POS_VOID_RETURN`, `APPROVE_POS_PRICE`, `APPROVE_POS_CASH`); migration V44.

### Version 1.8.0 - Restaurant improvements, version 1.8.0

Eight steps in the Restaurant module (see `RELEASE-NOTES-v1.8.0.md` for the summary, database and upgrade notes; `RESTAURANT-CHANGES.md` has the full detail of each step).

- **Lists and exports.** `page`, `size`, `q` and filters on the closed-receipts, wastage and reservations lists (total in `X-Total-Count`); Excel, PDF and CSV of receipts, wastage, reservations, menu items and tables.
- **Discounts, tips, service charge.** `/api/restaurant/settings` (service charge per order type, manager discount limit, tips, rounding); line and order discounts with a reason; tip at checkout; accounting for service charge, tips payable and rounding; migration V32.
- **Choices, notes, courses, allergens.** `/api/restaurant/menu/items/{id}/options`, courses 1-5 with `fire-course`, allergen tags and guest allergies on the order; migration V33.
- **Split bills, voids, comps.** `POST /orders/{id}/split|transfer|move-table`, line `comp`, void rules with manager approval, `/api/restaurant/voids` log (+ export), receipt copy marking; migration V34.
- **Reservations v2.** Time-window bookings, `/reservations/slots`, deposits (`/deposit`), no-show handling, reminders, waiting list `/api/restaurant/waitlist`, public `/api/public/reservations/slots|info`; migration V35.
- **Kitchen display v2.** Per-dish clocks and prep targets, `/kitchen/summary`, `/kitchen/expo`, `/kitchen/orders/{id}/bump|serve-ready`, `/lines/{lineId}/refire`, `POST /menu/items/{id}/sold-out`; migration V36.
- **Reports and end of day.** `/api/restaurant/reports/analysis|end-of-day|end-of-day/history` (+ exports), `POST /end-of-day/close` and `/{date}/reopen`, `POST|DELETE /api/restaurant/cash-events`; screens Sales analysis, End of day and a printable Z-report; migration V37.
- **QR menu and online ordering.** Public, no login: `/api/public/restaurant/menu?table=`, `POST /orders`, `GET /orders/{code}`, `POST /orders/{code}/cancel`; staff: `/api/restaurant/online-orders` (+ `/pending-count`, `/{id}/accept|reject`, `/expire-stale`), `/delivery-zones`, `/qr`; pages `/menu/:token`, `/order`, `/order/track/:code`; migration V38.

### Version 1.7.0 - Hotel improvements, version 1.7.0

Eight steps in the Hotel module (see `RELEASE-NOTES-v1.7.0.md` for the summary, database and upgrade notes; `tools/hotel-changes.md` has the full detail of each step).

- **Lists and exports.** `page`, `size`, `q` and filters on `GET /api/hotel/bookings|guests|rooms|maintenance`; total in `X-Total-Count`; `.../export?format=xlsx|pdf|csv` for each list, the front-desk lists (`/front-desk/{arrivals|departures|in-house}/export`) and the occupancy report.
- **Seasons and rate plans.** `/api/hotel/rate-rules`, `/api/hotel/rate-plans`, `GET /quote`, `GET /rate-calendar`, `GET /bookings/{id}/nights`; maintenance requests take `blockFrom`/`blockUntil`; migration V26.
- **Folio and policies.** Numbered bills (`/bookings/{id}/bills`, `billNo` on charges and payments), bill to a company (`/api/hotel/company-bills`, `/receipts`), moving a charge to another guest, fee policy fields on `/api/hotel/settings`, PDFs for bills and receipts; migration V27.
- **Groups.** `/api/hotel/groups` (blocks, rooming list, `/deposits`, `/payments`, `/refunds`, `/complete`, `/cancel`, master statement PDF); migration V28.
- **Housekeeping.** `/api/hotel/housekeeping/**` (staff, checklist, daily board, generate, assign, inspect, do-not-disturb, lost and found, minibar, linen, productivity); migration V29.
- **Guest register.** `GET /guests/{id}/profile`, `/guests/duplicates`, `POST /guests/{id}/merge`, ID documents (`/guests/{id}/documents`), `GET /bookings/{id}/registration-card/pdf` and `/confirmation/pdf`, `GET /guest-register` (+ `/export`); VIP and blacklist on guests; migration V30.
- **Reports and night audit.** `GET /api/hotel/reports/daily|sources|forecast|night-audit` (each with `/export`): day report, revenue by booking source, forecast and pick-up, and a night-audit report that checks the hotel's cash, revenue, tax, deposits, city ledger and group ledger against Accounting. No migration.
- **Online booking.** Public, no login: `/api/public/hotel/info|availability`, `POST /bookings`, `/bookings/{code}/lookup|cancel`, `GET /ical/{token}.ics`; staff: `/api/hotel/online/settings|room-types|send-due|release-unpaid`, `POST /bookings/{id}/send-confirmation`; page `/stay`; migration V31.

### Version 1.6.0 - HR and Payroll improvements, version 1.6.0

Eight steps in Employees, Attendance & Leaves, Contracts and Payroll (see `RELEASE-NOTES-v1.6.0.md` for the summary, database and upgrade notes).

- **Lists and exports.** `page`, `size`, `q` and filters on `GET /api/hr/employees|leaves|attendance|contracts|payroll/runs`; total in the `X-Total-Count` header; without them the whole list comes back as before. `GET /api/hr/{employees|leaves|attendance|contracts|payroll/runs}/export?format=xlsx|pdf|csv` with the same filters.
- **Payslips and certificates.** `GET /api/hr/payroll/runs/{id}/lines/{lineId}/payslip/pdf`, `.../runs/{id}/payslips/pdf`, `.../runs/{id}/register?format=`, `GET /api/hr/payroll/certificate?employeeId=&year=`, `GET /api/hr/payroll/annual-summary?year=&format=`. Company-wide only.
- **Adjustments, advances and loans.** `/api/hr/payroll/adjustments` and `/api/hr/payroll/loans` (repay, write-off, cancel); migration V21.
- **Tax and social-security payments.** `/api/hr/payroll/remittances` (+ `/summary`, `/{id}/void`) and `GET /api/hr/payroll/runs/{id}/returns/{paye|social-security}?format=`; migration V22.
- **Leave.** Half days (`startHalf`, `endHalf` on a leave request), `accrualMode` and `carryOverMaxDays` on leave types, `GET /api/hr/leaves/calendar?year=&month=`; migration V23.
- **My HR.** `/api/hr/me/**` (overview, contact, payslips and PDFs, certificate, leave, attendance, clock in/out, shared documents): always the signed-in user's own record; under the Employees license.
- **Documents, checklists, import.** `/api/hr/employees/documents` (+ `/{id}/documents`, file download, expiry summary), `/api/hr/employees/checklist-templates`, `/api/hr/employees/{id}/checklists`, `/api/hr/employees/checklists/open`, `POST /api/hr/employees/import?dryRun=` (+ `/template`); migration V24.
- **Payroll corrections and final pay.** `POST /api/hr/payroll/runs/{id}/reopen` and `/cancel-payment` (body `{"note": "reason"}`), `GET /api/hr/payroll/runs/{id}/history`; `GET|POST|DELETE /api/hr/payroll/final-pay/{employeeId}`; migration V25.

### Version 1.5.0 - Core improvements, version 1.5.0

Eight steps across Sales, Purchases, Customers, Suppliers, Inventory papers, Accounting and the till (see `RELEASE-NOTES-v1.5.0.md` for the summary, database and upgrade notes).

- **Paging and phone layout.** `page`, `size`, `q`, `status`, `branchId`, `from`, `to`, `method`, `customerId`/`supplierId` on the invoices, credit notes, quotations, sales orders, purchase orders, bills, refunds, customer payments, supplier payments, customers and suppliers lists; total in the `X-Total-Count` header; without them the whole list comes back as before.
- **Exports.** `GET /api/export/{invoices|credit-notes|quotations|sales-orders|customer-payments|customers|purchase-orders|bills|refunds|supplier-payments|suppliers|collections|collection-customers}?format=xlsx|pdf|csv`, same filters and access as the screens, whole filtered list.
- **Customer collections.** `/api/collections` (list with ageing, statement PDF and e-mail per customer, reminders, settings); daily job about 07:15 (off by default); migration V15.
- **Delivery and stock papers.** `GET /api/sales-orders/{id}/delivery-note`, `GET /api/purchase-orders/{id}/goods-received-note`, `GET /api/stock-counts/{id}/sheet[?showSystem=true]`, `GET /api/stock-transfers/{id}/note`.
- **Imports with a check first.** Every importer takes `?dryRun=true` (processed for real, then rolled back). New: `POST /api/data/import/{suppliers|categories|brands|customer-balances|supplier-balances|ledger-balances}` with a template for each; opening balances are real `OB-` invoices and bills; migration V16.
- **Accounting controls.** `/api/accounting/controls/...`: close the books up to a date, bank reconciliation, ledger opening balances; migration V17.
- **Dashboard that acts.** `GET /api/dashboard/overview?branchId=`.
- **Price lists and customer groups.** Sales > Price lists and Customer groups; one price rule (`PricingService.resolve`) for the till, synced offline sales and, when no price is typed, invoices, quotations and sales orders; migration V18.
- **Promotions and coupons.** Sales > Promotions; one rule (`PromotionService.apply`) run on every change to the till cart and again at payment; migration V19.
- **Gift cards and loyalty points.** `/api/giftcards` (sell, top up, block, expiry, release expired, lookup) and `/api/loyalty` (settings, customers, adjust, balance); gift-card money is a liability until spent; migration V20.

Limits: full lists are built before paging; reconciliation shows at most 2000 ledger lines; promotions, gift cards and loyalty points are till features and are not used on offline sales, invoices, quotations or sales orders; a typed price is never replaced by a price list; imports are untested on very large files; loyalty points do not expire and are not accrued as a liability.

## Version 1.4.0

Seven phases, all inside the Law Firm module (see `RELEASE-NOTES-v1.4.0.md` for the summary, database and upgrade notes).

- **Paging and phone layout.** `page` and `size` on the matters, clients, contacts, deadlines and tasks lists; total in the `X-Total-Count` header; without them the whole list comes back as before.
- **Client money papers.** `GET /api/law/trust/{id}/receipt`, `GET /api/law/matters/{id}/trust/statement`, `POST /api/law/matters/{id}/trust/statement/email`, `GET /api/law/trust/reconciliation/{id}/print`.
- **Exports.** `GET /api/law/export/{matters|clients|contacts|deadlines|tasks|billing|trust-balances|trust-ledger|collections}?format=xlsx|pdf|csv`, same filters and access as the screens.
- **Calendar and limitation warnings.** `GET /api/law/calendar`, `GET /api/law/calendar/ics`, `GET /api/law/escalations`, `POST /api/law/escalations/send`; daily job about 06:25 (off by default); migration V13.
- **Matter papers.** `GET /api/law/matters/{id}/papers/checks`, `POST /api/law/matters/{id}/papers/{cover|engagement|closing}[/save|/email]`.
- **Fee collections.** `GET /api/law/collections`, `GET /api/law/collections/clients/{id}/statement`, `POST .../statement/email`, `POST /api/law/collections/remind`, `GET /api/law/collections/reminders`, `POST /api/law/collections/reminders/run`; daily job about 06:35 (off by default); migration V14.
- **Imports.** `GET /api/law/import/{clients|matters}/template`, `POST /api/law/import/{clients|matters}?dryRun&createClients&clearConflicts` (managers and admins; dry run by default).

Limits: full lists are built before paging; the calendar file is a download, not a subscription; only LIMITATION deadlines are escalated; imports read the first sheet only, up to 2000 rows, `.xlsx` or CSV.

## Version 1.3.0

**1.3.0 improves Real Estate** (eight phases: phone layout and paging, list exports, lead follow-ups, viewing reminders and a week calendar, printable documents, unit-sale collections, agent commission payouts and statements, and imports from Excel or CSV). No new module. See the entry "Real Estate improvements, version 1.3.0" under *What's included* and [`RELEASE-NOTES-v1.3.0.md`](RELEASE-NOTES-v1.3.0.md). Migrations V9 to V12 are new.

### Version 1.2.0

**1.2.0 improves Property Management** (nine phases: phone layout and paging, tenant statements and receipts, rent reminders and late fees, more reports and exports, documents, deposits and inspections, a tax invoice to the owner for the management fee, utilities and service charges on the rent invoice, and co-owners). No new module. See the entry "Property Management improvements, version 1.2.0" under *What's included* and [`RELEASE-NOTES-v1.2.0.md`](RELEASE-NOTES-v1.2.0.md). Migrations V3 to V8 are new.

### Version 1.1.0

**1.1.0 adds module licensing** (signed license files, Settings > Modules, module requests; see "Module licensing" under *Running it* and [`RELEASE-NOTES-v1.1.0.md`](RELEASE-NOTES-v1.1.0.md)). Version 1.0.0 below is the base.

This is **version 1 of the NIWE ERP**. The module list is frozen for this release: Sales, Purchases and Accounting (core), then the optional modules POS, Hotel, Restaurant, Manufacturing, Subscriptions, Help Desk, Expenses, Assets, Installments, Time Tracking, Work Orders, Booking, Law Firm, Real Estate, Property Management and the HR modules. From here on only fixes and changes to existing modules are made; each schema change is a new migration (see "Database migrations (Flyway)"). What is in the release, how to start it and what has and has not been tested is in [`RELEASE-NOTES-v1.0.0.md`](RELEASE-NOTES-v1.0.0.md).

## Architecture

```
pos-app/
├── backend/     Spring Boot 3 REST API (Java 21, JWT auth, JPA/Hibernate, PostgreSQL)
└── frontend/    Angular 18 SPA (standalone components, signals, no NgModules)
```

The two talk over plain JSON REST + a bearer JWT — there's no server-side rendering, no shared
templates, no coupling beyond the API contract. That means you can point a different frontend
(a native mobile app, a kiosk app, another web client) at the same backend later without touching
it, and you can redeploy either side independently.

### Backend package layout

Package-per-module, same "modular monolith" discipline as the earlier ERP project:

```
com.posapp
├── common/     BaseEntity (id + audit timestamps), ApiException, global @RestControllerAdvice
├── security/   JWT issuing/parsing, the auth filter, the Spring Security UserDetails adapter
├── config/     SecurityConfig (JWT + CORS wiring), DataSeeder (first-run demo data)
├── user/       AppUser, Role (ADMIN/CASHIER), CustomRole + access areas (Manager/Accountant...), admin-only staff account management
├── auth/       POST /api/auth/login
├── catalog/    Category, Product — search/CRUD, admin-only write + full listing/low-stock
├── customer/   Customer — list (any user)/create/update (admin)
├── register/   Register, RegisterSession (a shift/till) — open/close/report, till reconciliation;
│               CashierSession — lets more than one cashier work the same shift at once, each
│               identified by their own PIN (see "Multiple cashiers per shift" below)
├── order/      SaleOrder (a cart, and after checkout, a completed sale), OrderLine, Payment —
│               the core of the terminal: add/remove lines, discounts, barcode lookup, checkout
├── paymentmethod/  PaymentMethodEntity — admin-manageable payment/refund methods (code, name,
│               active, cash-equivalent flag); Payment.method / SalesReturn.refundMethod store its
│               `code` as a plain string, not a foreign key, so renaming/deactivating a method
│               never touches historical rows
├── returns/    SalesReturn, SalesReturnLine — look up a sale, restock, reversing refund math
├── reports/    ReportService/ReportController/ReportExportService — the seven POS Reports plus
│               Customer Statement, all built from OrderRepository directly (see the note below)
├── credit/     CustomerCreditService — the store-credit/AR subsystem (enable/limit credit,
│               checkout-time enforcement, CustomerPayment + CustomerPaymentAllocation,
│               the four POS Credit reports' underlying balance math)
├── inventory/  Suppliers, purchase orders/receiving, stock adjustments, stock counts, supplier price lists, reorder
├── warehouse/  Warehouses, locations (RECEIVING/SALES/STORAGE/DAMAGED), per-location stock, stock transfers (incl. in-transit)
├── hotel/      Rooms, room types, guests, bookings, folio (guest bill), front desk, housekeeping, maintenance, night audit,
│               occupancy reports; "Charge to room" from POS / Restaurant and its Accounting postings
├── assets/     Assets Management module: fixed-asset register, categories, monthly depreciation, disposals
├── installment/  Installments module: customer instalment plans on invoices (deposit, interest, late fees, reminders)
├── timetracking/ Time Tracking module: projects, tasks, rates, timer, timesheets, approval and billing time to invoices
├── booking/      Booking module: appointments with staff, rooms/equipment, no double-booking, public booking page (/book), paid bookings matched to invoices
├── law/          Law Firm module (phases 1-2): clients/matters, conflict check, deadlines, tasks, case diary, daily reminder e-mail; fee terms, hours, costs, fixed-fee stages, retainers, contingency fees and invoicing
├── realestate/   Real Estate module (phase 1): properties with photos, leads with a diary, viewings, overview and reports
├── workorder/    Work Orders module: customer jobs, internal maintenance/repair, checklists, parts (stock + cost posting), service packages, billing to invoices
├── hr/         HR modules: employees, org structure (departments, positions), attendance & leaves, contracts, payroll (rules, tax bands, runs, payslips)
├── modules/    Settings > Modules: permanent activation of the optional modules (POS, Hotel, Restaurant, Manufacturing, Subscriptions, Help Desk, Expenses, Assets Management, Employees, Organizational Structure, Attendance & Leaves, Contracts, Payroll); Sales/Purchases/Accounting are core
├── reportschedule/  Saved report schedules: emailed Excel/PDF reports (daily/weekly/monthly)
├── tracking/   Batch/expiry and serial-number tracking (StockBatch, StockSerial, TrackingService)
└── dashboard/  Admin-only summary: today's sales, open sessions, active products, customers,
                low-stock list — one aggregating GET /api/dashboard/summary
```

Cross-module calls go through a service's public methods, never another module's repository —
`RegisterService` asks `OrderService` for a session's sales totals and `ReturnsService` for its
refunds total when closing a till or building a Z-report; `ReturnsService` asks `OrderService` for
the completed order it's refunding against and `CatalogService` to restock. No module reaches
past another's public API. **`reports/` and `credit/` are the deliberate exception**:
`ReportService` reads `OrderRepository`/`CustomerRepository` directly rather than going through
`OrderService`, and `CustomerCreditService` does the same, specifically so that `OrderService`
can depend on `CustomerCreditService` (for the checkout-time credit-limit check) without creating
a circular Spring bean dependency — a service is free to depend on another module's repository
read-only when that module's own service already needs to depend back on it.

### Why a cart is a `SaleOrder`, not two different things

There's one `SaleOrder` entity for both "the cart the cashier is currently building" (status
`OPEN`) and "a finished sale" (status `COMPLETED`) — checkout doesn't create a new record, it just
flips the status, stamps an invoice number, and records payments on the same row. Every open
`SaleOrder` for a session is a tab in the terminal's tab bar; there's no separate "parked" status —
parking just sets a free-text `note` for context, because unlike the old page-reload Thymeleaf
version, every mutation here is already persisted immediately over REST, so there's nothing extra
to "save" when you switch tabs or step away from one.

### Multiple cashiers per shift

A shift (`RegisterSession` — one till open/close, one cash float, one reconciliation) can now have
more than one cashier ringing sales on it at the same time, via `CashierSession`: a lightweight
"who's actually at the terminal right now" record, separate from the shift itself. The terminal
silently resolves "my own" cashier session from whoever's logged in (no PIN — `GET
/api/sessions/{id}/cashier-sessions/mine`, find-or-create); a **🔁 Switch cashier** button lets a
*different* staff member step up to the same physical terminal and identify themselves with just
their PIN (`POST /api/sessions/{id}/cashier-sessions`, no username needed — PINs are enforced
unique among active staff), which scopes the tab bar to just their own open carts and tags any sale
they ring to them (`OrderDto.cashierName` now prefers the order's own `cashierSession.cashier` over
the shift's opener). This is deliberately **attribution only** — it never touches or widens the JWT
that actually authorizes API calls; the till's cash float and reconciliation stay one shared thing
at the `RegisterSession` level regardless of how many `CashierSession`s work it. See
`CashierSession`'s and `CashierSessionService`'s javadoc for the full reasoning, and the admin
**Shifts** screen's "▸ Cashiers" expander for a per-shift breakdown of who joined.

Ending a `CashierSession` is separate from ending the *shift*: a cashier can end their own working
session at any time (**🚪 End my session** in the terminal — `POST
/api/sessions/{id}/cashier-sessions/{cashierSessionId}/close`), which discards any cart they never
checked out (same VOIDED-status mechanism as **Discard**, so nothing is left orphaned once their
tab bar disappears) and sends them back to the register list. An `ADMIN` can additionally end
*every* open cashier session on a shift at once without closing the shift itself (**Close all
sessions** on the admin **Shifts** screen's "▸ Cashiers" expander — `POST
/api/sessions/{id}/cashier-sessions/close-all`) — handy for kicking everyone off a till at end of
day before the shift itself is closed and reconciled. `CashierSessionService.close` allows a
cashier to close only their *own* session (`ApiException.forbidden` otherwise); `closeAllForShift`
is `ADMIN`-only and is also what runs automatically when the shift itself is closed.

**Sessions are never shared, and this is enforced server-side, not just by what the frontend
happens to ask for.** Every order endpoint that's scoped to a `CashierSession` — listing/starting
tabs (`GET`/`POST /api/cashier-sessions/{id}/orders`) and every mutation of an open cart
(add/remove/update line, set customer, hold, discard, checkout) — now requires the authenticated
caller to actually be that cashier session's own cashier, or an `ADMIN`
(`OrderService.requireOwnCashierSessionOrAdmin`, `403` otherwise). Before this check existed, the
frontend always *asked* for its own session (via `mine`/`join`), but nothing stopped a different
authenticated cashier from calling the same endpoints with someone else's `cashierSessionId` or
`orderId` and reading or posting transactions under it — i.e. "sessions" were attributable in the
UI but not actually isolated at the API. A pre-existing order with no `cashierSession` (from
before this feature existed) can only be acted on by an `ADMIN`, since ownership can't be
determined for it. The same ownership-or-admin check now also backs a cashier's own read access to
their session data (see "Sessions" below and `OrderService.listAllForCashierSession`) — being
scoped out of *other* cashiers' sessions is one half of "sessions are never shared"; being able to
actually *see your own* is the other half, which the `ADMIN`-only "Sessions" screen from the
previous pass had accidentally removed for non-admins.

### Auth

Stateless JWT — `POST /api/auth/login` returns a token, every other request carries it as
`Authorization: Bearer <token>`, `JwtAuthFilter` validates it per-request, no server-side sessions.
Two base roles exist (`ADMIN`, `CASHIER`) — plus optional admin-built **custom roles** (Manager, Accountant... see *Custom roles* below) that add access areas to a Cashier account — and **`ADMIN` is now enforced** via Spring Security method
security (`@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")`): every write to categories,
products, customers, and every staff-account and dashboard endpoint requires `ADMIN` — and, as of
an earlier pass, so does every write on registers and shifts: creating/editing a register
(`RegisterController.createRegister`/`updateRegister`), opening or force-closing a shift
(`openSession`/`closeSession`), listing every cashier session on a shift and bulk-closing them
(`CashierSessionController.list`/`closeAll`). Reads that the terminal needs at checkout time
(category/product search, customer list, register listing) stay open to any logged-in user,
including `CASHIER`, and a cashier can always join, resolve, and close *their own* cashier session
(`CashierSessionController.join`/`mine`/`close`) — that "own resource" check can't be expressed
as a role alone, so it lives in `CashierSessionService.close` (and, for the order endpoints it
scopes, `OrderService.requireOwnCashierSessionOrAdmin`) as an owner-or-`ADMIN` check instead of a
`@PreAuthorize` annotation. The plain "Sessions" screen's listing (`GET /api/cashier-sessions`,
`CashierSessionQueryController`) follows the same pattern rather than an `ADMIN`-only
`@PreAuthorize`: it's open to any logged-in user, and branches internally —
`CashierSessionService.listAll()` for an `ADMIN` (every cashier session, every shift),
`listMine(caller)` for anyone else (just their own, via
`CashierSessionRepository.findAllByCashierIdOrderByStartedAtDesc`). An earlier pass had made this
endpoint (then `CashierSessionAdminController`) `ADMIN`-only outright, which over-corrected: it
stopped a cashier from seeing *any* of their own session history, not just other cashiers'. The
Angular app mirrors all of this with a route-level `adminGuard` on the truly admin-only routes and
by hiding the still-restricted buttons and nav links (New/Edit register, Open session, Close,
Close all sessions) from non-admins — but the backend check is what actually matters; the frontend
guard is just UX.

**One route-level gate is frontend-only and doesn't mirror a backend restriction**: `/registers`
itself gained `adminGuard` this pass (see "Registers" below), even though every individual action
on it was already `ADMIN`-only server-side. The problem wasn't a missing backend check on a
mutation — it was a *read* (the register list, and its unguarded "Go to terminal" link) that let a
cashier reach a terminal for a shift nobody had assigned them, where the terminal's own `mine()`
call would then auto-create a cashier session for them. There's no single backend endpoint to gate
against that; gating the route was the actual fix. `adminGuard`'s own fallback-on-rejection
changed from `/registers` to `/sessions` in the same pass, since falling back to a route the guard
itself now blocks would loop.

## Design system: Dark Pro Terminal, app-wide

Earlier, the Thymeleaf ERP's POS module got a dark "Pro Terminal" theme scoped to just that module,
because it lived inside a lighter admin app. This project **is** the POS product, so the same dark
theme is now the app's only look, applied globally in `frontend/src/styles.scss`: a small set of CSS
variables (`--accent`, `--bg`, `--surface`, `--text`, `--muted`, …) that every component's classes
build on. Change the look by editing those variables in one file — nothing else needs to change.

The one deliberate exception is the **receipt** screen (`checkout/receipt.component.ts`): it stays
light and printable on purpose — a dark background is a bad idea both on paper and in a print
preview — using its own component-scoped styles that don't touch the global dark theme.

## Running it

### Backend

```bash
cd backend
createdb pos_app   # or: psql -c "CREATE DATABASE pos_app;"
mvn spring-boot:run
```

`src/main/resources/application.properties` defaults to `jdbc:postgresql://localhost:5432/pos_app`
with user/password `postgres`/`postgres` — edit those to match your local setup.

Demo data (an admin + cashier login, 4 categories, 8 barcoded products, a walk-in customer, one
register) is seeded automatically on first run. Set `app.seed-demo-data=false` once you have real
data. The API listens on `http://localhost:8080`.

### Database migrations (Flyway)

The database schema is owned by **Flyway**. Hibernate no longer creates or changes tables (`spring.jpa.hibernate.ddl-auto=validate`): on start-up it only checks that the entities match the database and refuses to start, naming the missing table or column, when a migration is missing.

- Migrations are plain SQL files in `backend/src/main/resources/db/migration`, named `V<number>__<short_description>.sql` (two underscores): `V3__invoice_add_po_number.sql`, `V4__...`. Flyway runs, in order, every file the database has not seen yet, each in its own transaction, and records it in the `flyway_schema_history` table.
- **`V1__baseline_schema.sql` is version 1.0.0 of the schema; `V2__module_licensing.sql` (1.1.0) is the only migration after it.** It was generated from the live catalog of a database built by the 23 migrations used during development (203 tables, every column, constraint, index and foreign key; the same signature, compared table by table) and also seeds the two settings rows (`re_settings`, `pm_settings`). A **new empty database** runs V1 and gets everything. A database that **already has tables but no Flyway history** (built by Hibernate before Flyway) is marked as being at version 1 and keeps its data; V1 is not run against it (`baseline-on-migrate` with `baseline-version=1`). **V1 is never edited after the release** — every later change is `V3`, `V4`, ...
- **Rule 1: any change to an entity that touches the database needs a new migration file in the same change** (new table, new or changed column, new index or constraint, renamed or dropped column). Example: a new column on `invoice` is `V3__invoice_add_po_number.sql` containing `alter table invoice add column po_number varchar(255);`. Write it by hand; keep the column type the same as the entity would produce, otherwise validation fails.
- **Rule 2: never edit or delete a migration that has been applied anywhere.** Flyway stores a checksum and refuses to start if a file changed. To fix a mistake, add a newer migration.
- **Rule 3: when an enum gains a value, the migration must also replace its check constraint.** Hibernate created `check (status in (...))` constraints listing the enum values; they do not change by themselves. Pattern: `alter table stock_transfer drop constraint <name>; alter table stock_transfer add constraint <name> check (status in (...));` (find the name with `\d stock_transfer`).
- Data fixes are migrations too (`update ...`); keep them idempotent where you can.
- **A database that ran the development migrations (the old V1 to V23) must be dropped and recreated**, because the single V1 has a different checksum from the old V1 and Flyway refuses to start on it ("Migration checksum mismatch"). That is safe only for a database with no real data; none had real data when version 1 was cut. Back up first if in doubt.
- The history entries further down this file that mention `V2` to `V23` describe the development steps; since 1.0.0 all of them are inside V1.

**Moving an existing database to Flyway (once per database).** The new build starts with `validate`, so the database must already have every table and column the entities need. If it has not been started with the latest code (for example it is missing the Installments or Time Tracking tables), start the backend **once** with the one-time profile first, stop it, then start normally:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dbsync        # with your usual profile: niwe,dbsync
# wait for "Started PosApplication", stop it (Ctrl+C), then:
mvn spring-boot:run                                          # Flyway baselines the database at V1 and carries on
```

`dbsync` (`application-dbsync.properties`) switches Flyway off and puts Hibernate back on `update` for that one run. Never use it on a database Flyway already manages. Take a backup first (Settings > Backup).

If a normal start fails with `Schema-validation: missing column/table [...]`, the database is behind the entities: run the `dbsync` profile once as above, then add the missing change as a proper migration. If it fails with a wrong *type* for a column in an old database, send the message and fix it with a migration that alters the column (or, as a temporary way out only, start with `--spring.jpa.hibernate.ddl-auto=none`).

**Backups.** Backup files made by Settings > Backup include `flyway_schema_history`, so a restore brings the database and its migration history back together. Restoring a backup from a *newer* build into an *older* build will make Flyway report a migration it does not know; deploy the newer build instead.

### Module licensing (version 1.1.0)

Which modules an installation may use is decided by a **license file** that NIWE signs for that installation. A module is usable when it is **in the license and an admin has activated it** (Settings > Modules, activation is permanent as before). Sales, Purchases and Accounting are core and are in every installation. With no license installed only the core modules work.

- **The license file** (`.niwelic`) is a small JSON envelope: the license text (license id, a rising number, customer, installation id, issue date, optional expiry date, module list) and an **Ed25519 signature** over it. The application checks the signature against NIWE's public key, which is inside the application (`backend/src/main/resources/license/public-key.b64`), never in a setting. A file that was edited, was signed with another key, was issued for another installation, is older than the installed one, is expired, lists a module without the modules it needs (Hotel needs POS and Restaurant; Work Orders and Law Firm need Time Tracking; Organizational Structure, Attendance & Leaves and Contracts need Employees; Payroll needs Employees, Contracts and Attendance & Leaves), or names a module this version does not know, is refused with a message.
- **Installation id.** A random id made at the first start and kept in the database (table `app_installation`, so it travels with backups). It is shown on Settings > Modules with a Copy button; a license is issued for exactly this id.
- **Installing a new customer.** (1) Empty database, start the backend, Flyway applies V1 and V2. (2) Sign in as the first admin (change the default password and `app.jwt.secret`). (3) Settings > Modules: copy the installation id and send it to NIWE with the list of modules bought. (4) NIWE sends a license file. (5) Settings > Modules > **Upload license file**. (6) Activate the modules the customer wants to start with, then set up e-mail, tax, branches and each module's settings. A restored backup carries the installation id and the license with it; a brand-new database is a new installation and needs a new license.
- **What a locked module does.** It shows greyed out on Settings > Modules and its menus are hidden. The server refuses calls to its own API (`/api/propertymgmt`, `/api/law`, `/api/hr/payroll`, ... HTTP 403, "X is not part of your license"), its public pages answer "not available", its morning jobs are skipped, and it cannot be activated. Its data is never deleted; when a license that contains the module is installed again it is usable again, without being re-activated. A license that drops a module, or runs out, locks it the same way.
- **Expiry.** Optional. An admin sees a warning on Settings > Modules for the last 30 days. After the date the non-core modules lock, core keeps working, and nothing is deleted.
- **Requests ("activated by request").** On a locked module an admin presses **Request** (optional note). The request is saved and, when e-mail is set up (Settings > Email) and `app.license.vendor-email` has an address, e-mailed to NIWE with the installation id; otherwise the screen shows the text to send by hand. One pending request per module; it can be withdrawn. **A request unlocks nothing.** NIWE answers by sending a new license; installing it closes the requests it covers.
- **Developer switch.** `app.license.dev-all-modules=true` together with the `dev` profile (`application-dev.properties` sets the property) makes every module count as licensed, so developers do not need a license. It is ignored unless the `dev` profile is active. Never start a deployment with the `dev` profile.
- **For NIWE only: issuing licenses.** `tools/license-tool/LicenseTool.java` (Java 17+, nothing to build; see `tools/license-tool/README.md`): `keygen` makes the key pair once, `issue` signs a license (adds the modules each module needs, numbers it from `licenses.csv`, the register), `verify` checks a file, `modules` lists the module keys. The module list inside the tool must be kept in step with `ModuleKey.java` by hand. The private key must never be in git or on a customer's machine and is backed up in two places: losing it means a new application build with a new public key and new licenses for every customer. **Before the first build for customers, run `keygen` and paste the printed line into `public-key.b64`** (it ships empty, so no license can be installed in a build that lacks it).
- **API.** `GET /api/license` (status), `POST /api/license` `{license: "<file text>"}`, `GET|POST /api/license/requests`, `DELETE /api/license/requests/{id}`; all admin only. `GET /api/modules` now also returns `licensed`, `state` (CORE, ACTIVE, AVAILABLE, LOCKED, REQUESTED, EXPIRED) and `requestedAt`, and `enabled` means licensed and activated.
- **Limits.** The server-side refusal covers the endpoints that belong to exactly one module. Shared endpoints stay open (reports, the dashboard, products, customers, invoices, stock), so a locked module's reports are hidden from menus but a hand-made call to `/api/reports/...` is not blocked; scheduled e-mail reports are not stopped when their module is locked. The license is checked offline: a license cannot be recalled before its expiry date, and someone with the source and the server can remove the check. The signature stops editing a license file; beyond that it is a contract matter.

### Frontend

```bash
cd frontend
npm install
npm start          # ng serve — http://localhost:4200, proxies nothing; the API base URL
                    # is set directly in src/app/core/constants.ts
```

Log in with `admin` / `admin123` (full access) or `cashier` / `cashier123` (day-to-day use). Each
demo account also has a PIN for the terminal's **🔁 Switch cashier** flow: `1234` for admin, `5678`
for cashier — try opening a shift as one, then switching to the other from the same browser
session without logging out, to see two cashiers' carts kept separate on one shift.

If your backend runs somewhere other than `localhost:8080`, change `API_BASE` in
`frontend/src/app/core/constants.ts`, and `app.cors.allowed-origins` in
`backend/src/main/resources/application.properties` if the frontend runs somewhere other than
`localhost:4200`.

## What's included

- **Terminal** (`/pos/:sessionId`): opening it now always **requires a PIN** (Task: "To open
  terminal for selling should ask PIN and open related session based on the PIN entered. If PIN
  is not valid the terminal shouldn't open") — there's no more silent "my own session" auto-open
  by whoever's logged in; the terminal shows a PIN-entry screen first, and only resolves (or
  creates) the matching cashier's `CashierSession` — and shows the terminal proper — once a valid
  PIN is entered. An invalid PIN leaves the PIN screen up with an error, same as any other PIN
  check in this app (`CashierSessionService.joinShift`'s "PIN not recognized"). Past that gate:
  barcode scan or product-tile entry, colored category filter tiles, live search, order tabs
  (every open cart for the *current cashier's own* working session on this shift, with a **+** to
  start a new one — see "Multiple cashiers per shift" above), per-line quantity/discount, a
  customer picker with its own **search box** (filters by name/phone as you type — Task: "Add
  Search Input Customer from Terminal screen") and a **👤+ Add** button that opens an inline
  name/phone/email/**Tax ID** form (Task: "Add Tax ID on Customer input inside Terminal screen" —
  the same optional field Admin > Customers' own form has) and, once saved, **selects the
  newly-created customer on the current order** (Task: "Add Customer button from Terminal screen
  And the added customer should be selected") — no need to leave the terminal (and lose the cart
  in progress) to reach Admin > Customers just to add someone new, **Hold** (parks the tab with a
  note and opens a new one),
  **Discard** (cancels an open cart outright — never touches stock), Payment, a **🔁 Switch
  cashier** button so another staff member can ring sales on the same shift under their own PIN
  without anyone logging out, a **🚪 End my session** button that closes the current cashier's own
  `CashierSession` (discarding any cart they never checked out) and returns to the register list,
  and an **↩️ Exit** link (an icon added alongside the existing text — Task: "Add icon on Exit
  Terminal button") back to the register/session list. A **📺 Customer display** button (Task:
  "Add customer-facing double screen") opens a second, read-only screen (`/customer-display/
  :cashierSessionId`, a new browser window) showing whichever tab is currently selected — its
  live cart and running total while ringing up, then a "Thank you!" screen with the completed
  payment breakdown for a few seconds once checkout finishes, then back to an idle branded
  welcome screen. It's meant for a second monitor on the same till (drag the new window over), or
  for entirely separate hardware (a tablet, a small counter screen) pointed at the exact same URL
  — either way it works the same, since it never talks to the terminal directly: the terminal
  just tells the backend which order is "on screen" every time a tab is switched
  (`CashierSessionApiService.setActiveOrder`), and the display polls that plus the order itself
  every couple of seconds. **It still needs a login** — there's no new unauthenticated "public
  kiosk" mode — so a customer display on separate hardware needs to be signed in as either the
  same cashier working that till or an admin (see "Known limitations" below for why, and what a
  display shows if that's not the case). Once a PIN has opened the terminal, it
  stays "remembered" for the rest of this browser tab's session (`TerminalSessionService`, a
  plain in-memory `providedIn: 'root'` singleton keyed by register-session id) — navigating to
  Checkout or the receipt screen and back **no longer re-shows the PIN screen** (Tasks: "The back
  button on checkout should not request PIN" / "The back button on print receipt should take back
  to terminal and not request PIN"), since that's just an in-app route change, not a new terminal
  session. An actual browser reload still requires the PIN again, because that clears the
  singleton — the PIN-on-open gate itself is unchanged and intentional (see the pass below).
- **Offline mode** (this pass — "there's no offline mode... a real deployment usually needs local
  queuing of sales that sync once connectivity returns"): once a cashier session is already open,
  a lost connection no longer means the terminal can't ring anything. `ConnectivityService` tracks
  reachability (a periodic ping to the new public `GET /api/health`, plus the browser's own
  online/offline events), and `OfflineSyncService`/`OfflineDbService` keep a local IndexedDB
  snapshot of the active product catalog, categories, active payment methods, and customers,
  refreshed automatically every few minutes while online (and force-refreshed once a cashier
  session opens). The moment connectivity is reported lost, the terminal's right-hand cart panel
  swaps to a local-only **offline cart**: the same product grid/search/barcode entry, tapping a
  cached product or scanning a barcode adds a line, quantities/discounts are editable the same
  way, and a **Payment** step (the same split-tender button-per-method UI as Checkout, minus a
  live credit-limit check — see below) queues the finished sale in IndexedDB with a
  client-generated id instead of calling the server. A **📥 Offline queue** button (shown once
  anything is queued) lists every queued sale and its status. The moment connectivity returns,
  every queued sale is replayed automatically, oldest first, against a new
  `POST /api/orders/offline-sync` endpoint — one call per sale that does the equivalent of the
  normal create-order/add-line/checkout round trips at once (since none of those are reachable
  offline), computing the *real* price/tax/total server-side exactly like an online sale would
  (same `OrderService.completeCheckout` code path, credit-limit enforcement included). It's
  idempotent by that client-generated id, so a retried sync (the terminal lost connectivity again
  mid-request) can never double-sell. **What happens to a queued sale at sync time**: the terminal
  priced it offline from a cached snapshot that can be stale by the time it actually syncs — stock
  was never checked at checkout at all, online or offline, so there's nothing to reconcile there,
  but price/tax can drift. The chosen policy (see the two clarifying questions this feature was
  built from) is to sync the sale anyway and flag it: if the terminal's offline estimate and the
  server's final total differ by more than a cent, the resulting order gets a plain-text
  `syncNote` ("a price or tax rate likely changed...") shown on the Receipts list, the in-terminal
  shift receipts, and the receipt reprint screen (a 📴/⚠ badge with the details in its tooltip) —
  reviewable, never silently accepted or silently rejected. The one thing that genuinely still
  fails at sync time is a real business-rule violation the terminal couldn't check offline (most
  notably a credit sale that turns out to exceed the customer's limit by sync time) — that one
  queued sale is marked **failed** with the server's own rejection message and stays in the queue
  for a cashier/admin to review and **Discard**, rather than being silently accepted or retried
  forever.
- **Checkout** (`/checkout/:orderId`): payment entry is a **row of buttons, one per active
  payment method** (Task: "On checkout change select into buttons for payment modes so If I click
  on the button then input text is enabled for entering amount") — tapping a method toggles it
  selected and reveals/enables its own amount input; any combination of selected methods, entered
  amounts summing to at least the total due, completes the sale. Replaces the previous fixed three
  rows of a method dropdown + always-enabled amount field. **An amount input can no longer be
  pushed past what's still owed** (Task: "if the total amount to pay is already input do not
  allow input extra amount") — each input is capped to the order total minus whatever's already
  entered on the *other* selected methods, so once one or more methods already cover the sale in
  full, every other method's input is capped at zero; toggling on a second method after the first
  already covers the total now defaults its amount to that remaining zero rather than pre-filling
  the full total again. **Cash is selected by default** (Task: "by default cash is selected") —
  whichever payment method is flagged cash-equivalent (`PaymentMethodOption.cash`, same flag
  `listCashCodes()` already uses elsewhere) is pre-selected with the full total on page load,
  falling back to the first active method if none is flagged cash; every other method's amount
  input stays hidden until its own button is tapped (Task: "Input for payment mode should be
  hidden and displayed once I press on payment mode"). **Each method's button and its amount input
  now share one row** (this pass — Task: "Replace Payment method label before input with the
  payment method button (each payment method should be on the same line with its input)") — the
  separate row of method-toggle buttons above a separate stack of "label + input" rows is gone;
  each payment method is one `*ngFor` row with its own toggle button immediately followed by its
  own (still hidden-until-selected) amount input, so the button itself is what replaces the old
  plain-text label rather than sitting apart from it. **Layout: payment on the left, totals on
  the right** (Task: "payment mode to be on left side and totals on right side") — the payment
  method buttons/inputs and the subtotal/tax/total-due table now sit side by side in a two-column
  layout (stacking on narrow viewports) instead of totals-then-payment stacked vertically; purely a
  template/CSS change, no behavior difference. "Back to cart" always returns straight to the
  terminal without a PIN prompt (see **Terminal** above). Then a printable **receipt**, which now
  opens with a **business header** (this pass — Task: "Add Business details + logo on the header
  of receipt printout"): logo (if one's been uploaded), business name, address, phone/email, and
  tax ID, pulled from the same `GET /api/settings` **Business details** feeds — the whole block
  is wrapped in `*ngIf="settings as s"` with every field inside its own `*ngIf`, so a store that
  hasn't filled in Business details yet just sees the receipt exactly as before (plain "Receipt"
  heading, no gap where the header would be). Also prints the register
  name and cashier who rang up the sale (`o.registerName` / `o.cashierName` on `OrderDto`, resolved
  from the order's `RegisterSession`), a **(tax incl.)** note under any line whose tax was folded
  into its price rather than added on top (see **Taxes** below), and a single **"Back to
  terminal"** link (previously a completed sale's receipt linked to `/receipts`
  instead, and a still-open one linked to the terminal but with a PIN prompt; Task: "The back
  button on print receipt should take back to terminal and not request PIN"). **"Complete sale"
  now auto-opens the print dialog and returns to the terminal once it's dismissed** (Task: "when I
  press Complete sale button, it should open automatically kiosk print page where I have to select
  printer and once I leave that page it have to take me back to the terminal screen to make
  another order") — checkout navigates to the receipt with `?autoPrint=1`, which triggers
  `window.print()` on load (the browser's own print dialog is where a printer actually gets
  picked) and listens for the browser's `afterprint` event — fired once that dialog is dismissed,
  whether the cashier printed or cancelled — to navigate straight back to `/pos/:sessionId`. A
  reprint opened from the **Receipts** list has no `autoPrint` flag, so it still behaves exactly as
  before: a manual **Print** button and a manual **Back to terminal** link, no forced navigation.
- **Receipts** (`/receipts`, any logged-in user): the most recent 200 completed sales app-wide,
  newest first, with invoice number, customer, total, and payment method — each row links to its
  receipt for **reprint** (`GET /api/orders/completed`). The terminal (`/pos/:sessionId`) now has
  its own **🧾 Receipts** toggle in the tab bar that opens the same list scoped to just that till
  session (`GET /api/orders/completed?sessionId=...`) without leaving the terminal's full-screen
  layout — a "← Back to terminal" button returns to the cart view.
- **My Profile** (`/profile`, new this pass — Task: "Add my profile, change my password"; any
  logged-in user, so it's a link in the topbar next to "Log out" rather than a sidebar item under
  the admin-only groups): view your own username/role (read-only) and edit your own display name,
  plus a separate change-password form that requires your *current* password (new backend
  `MeController` at `/api/me`, open to any authenticated user — every endpoint resolves "you" from
  the JWT, there's no id in the URL to act on someone else's account). Deliberately narrower than
  what Admin > Users' Staff screen can do to *other* accounts: you can't change your own
  username, role, or active flag here — self-promoting to `ADMIN` or self-deactivating would
  defeat the point of those being admin-controlled. Saving your name here updates the topbar
  immediately (`AuthService.updateFullName`), without needing to log out and back in. **Change PIN**
  (new this pass — Task: "Add change PIN inside my profile"): a third card, new PIN + confirm PIN
  (4-6 digits, same pattern as the admin-side `SetPinRequest`), a **Set/Not set** badge like Admin >
  Staff's list already shows, and `POST /api/me/pin` on the backend — which just calls the existing
  `UserService.setPin` aimed at the caller's own id instead of one an admin picked, so it gets the
  same active-user PIN-uniqueness check `CashierSessionService` relies on for free. Deliberately
  doesn't ask for your *current* PIN first, unlike the password form above: the PIN isn't the
  credential that got you into My Profile (your password already did that), so requiring the old
  one would strand anyone who forgot it with no self-service way to reset it.
- **Registers** (`/registers`, **`ADMIN`-only route** as of this pass — was open to any logged-in
  user): create/edit a register (name, location), open a till session with a starting cash float,
  close it with a counted-cash reconciliation (expected vs. counted, variance), and a Z-report per
  closed session — all already `ADMIN`-only server-side. The route itself is now gated too (Task:
  "Remove Registers & Terminal menu from Cashier side so he can not create a session himself"):
  its "Go to terminal" link had no per-row ownership check, so a cashier browsing this list could
  open the terminal for a shift nobody had assigned them, silently self-creating a cashier session
  on it via the terminal's `mine()` call — the exact thing the ninth pass's "Assign to" picker was
  supposed to prevent. A cashier's own assigned shift is still one click away, just from
  **Sessions** now (their own row's "Terminal" link) instead of from this list. `adminGuard`'s
  fallback-on-rejection destination changed from `/registers` to `/sessions` accordingly (falling
  back to a now-gated route would have looped), and login/`**`/`''` still resolve to `/registers`
  first but a non-admin bounces through to `/sessions` immediately. **Opening a session requires
  picking which staff member it's assigned to** (`SessionOpenComponent`'s "Assign to" dropdown,
  populated from active staff via `GET /api/users`) — the admin opening the till usually isn't the
  one who'll ring sales on it, so that staff member, not the admin, becomes the shift's
  `RegisterSession.cashier` and gets its first `CashierSession` (`OpenSessionRequest.cashierId`,
  validated server-side against `UserRepository` — 404 if no such user, 400 if inactive). Opening
  doesn't drop the admin straight into the terminal (that would auto-create *the admin's own*
  cashier session on the shift, which was never who the till was actually being opened for); it
  returns to the register list instead, and the assigned staff member reaches the terminal
  themselves — either by clicking "Go to terminal" on their own row on **Sessions**, or via
  **🔁 Switch cashier** with their PIN if the terminal is already logged in as someone else.
  One gap this left (reported as "when I open a new session for Admin there is no way to open
  another session for Cashier"): since **Registers & Terminal** is now `ADMIN`-only, an admin who
  clicks "Go to terminal" themselves lands the same `mine()` call the ninth pass built for
  cashiers — it silently auto-creates *the admin's own* `CashierSession` on that shift, from their
  JWT, regardless of who the shift was assigned to. If the admin doesn't happen to know the
  intended cashier's PIN for **🔁 Switch cashier**, there was no other way to get that cashier a
  session at all. Fixed with an admin-only **"+ Add cashier"** action (see **Shifts** below) that
  creates a specific staff member's `CashierSession` directly — `POST
  /api/sessions/{id}/cashier-sessions/assign` (`CashierSessionService.assignCashier`), the same
  find-or-create as `mine()`/"Assign to", just for an admin-chosen cashier instead of the caller's
  own JWT identity, and with no PIN involved.
- **Sessions** (`/sessions`, any logged-in user): every individual cashier session — one row per
  cashier actually working a shift, not one row per shift (`GET /api/cashier-sessions`, backed by
  `CashierSessionQueryController`/`CashierSessionService.listAll`/`listMine`). What comes back is
  scoped by role, not by an `ADMIN`-only route guard: an `ADMIN` sees and can **Close** any cashier
  session across every shift; a `CASHIER` sees and can close only their own (Change: "Cashier does
  not have access to his session data" — an earlier pass had made the whole screen `ADMIN`-only,
  which was an over-correction). Each row also has an **Orders** link to that cashier session's
  full order history (`/cashier-sessions/:id/orders`, `GET
  /api/cashier-sessions/{id}/orders/all`) — every order on that session regardless of status (open
  tab, completed sale, voided cart), not just what's currently open at the terminal
  (`OrderService.listAllForCashierSession`, same ownership-or-`ADMIN` check as everything else
  scoped to a cashier session). Shift-level actions (force-closing a whole shift, its Z-report)
  stay on the **Shifts** screen below — this screen is deliberately just about individual cashier
  sessions and their own orders, so both screens have one clear job instead of overlapping. Each
  row's other two links are keyed off **that row's own status** (`cs.status`, not the shift's) —
  Task: "When Session is closed show orders and report. When session is open show orders and
  Terminal." A **Terminal** link shows only while that specific cashier session is still `OPEN`
  (previously it showed whenever the *shift* was open, so a cashier whose own session had already
  ended could still see "Terminal" and silently start a brand-new session on a shift they were
  done with); a **Report** link (`/sessions/:id/report`) shows once that row's own session is
  `CLOSED` (previously gated on the whole shift being closed, so a session someone ended early on
  a still-open shift had no Report link at all).
- **Returns** (`/returns`): look up a completed sale by invoice number, pick quantities to return
  per line (validated server-side against what's already been returned, across multiple return
  visits), restocks inventory, and computes a tax-inclusive refund. A **Return all items** button
  (this pass — "add any option of returning all items on the order at once") fills every line's
  return quantity up to what was sold in one click, instead of typing each line by hand; a
  **Clear** button next to it resets every line back to zero. On an order that already had a
  prior partial return, filling in the full originally-sold quantity this way and submitting it
  gets the same precise "only N remaining" rejection a manual over-return always has — the button
  doesn't try to guess what's still returnable, only the backend actually knows that. Processing a
  return (this pass — "print out the return") now navigates straight to a printable **Return
  Receipt** (`/returns/:id/receipt`) and auto-prints it, the same flow checkout already uses for
  sale receipts; the Returns list also has a **Print** link per row to reprint one later. The
  return receipt shows each returned line's description, quantity, and refund amount (snapshotted
  onto `SalesReturnLine.refundAmount` at the moment it's returned — null for any return processed
  before this pass, which the receipt shows as "—"), plus the refund total/method and reason.
  Separately (this pass — "on the receipt which was returned, I should mark it as returned"), the
  original **sale** receipt (`/receipt/:orderId`) now marks any line with a prior return against
  it as `[RETURNED N of M]` in plain bold text (no color, since this receipt has to stay readable
  on a monochrome printout) — fetched from `GET /api/returns/order/{orderId}`, a
  `Map<orderLineId, totalQuantityReturned>` summed across every return visit against that order.
- **Admin management** (`/admin/*`, sidebar shown only when logged in as `ADMIN`):
  - **Dashboard** (`/admin/dashboard`): today's sale count/revenue, open session count, active
    product count, customer count, and a low-stock table (threshold set by
    `app.pos.low-stock-threshold`, default 10).
  - **Products** (`/admin/products`): full list including deactivated items, create/edit
    (name, SKU, barcode, **code**, **icon**, price, **cost price**, stock, category, tax rate,
    active flag), and deactivate (soft-delete — a product referenced by past sales is never
    hard-deleted). The tax rate picker offers only currently-active rates plus "— None
    (tax-exempt) —"; the list view shows each product's assigned rate (or "— exempt —") and its
    cost alongside its price. Cost price (`Product.costPrice`) is never shown to customers — it
    exists purely so the **Reports** below can compute profit; it defaults to 0 (100% margin)
    until an admin fills it in, and is snapshotted onto each `OrderLine` the moment it's added to
    a cart (`OrderLine.unitCost`), the same reasoning as `unitPrice`/the tax snapshot, so a later
    cost edit never rewrites a past sale's reported profit.
    - **Code** (`Product.code`, this pass — "add product icon, code. code can be null but
      unique"): a second, optional product identifier, distinct from SKU (never
      uniqueness-checked) and barcode (unique only within the barcode-lookup query, never
      enforced at write time). Leave it blank for products that don't need one; if set, the
      backend rejects a duplicate with a 409 (`CatalogService.applyRequest`), and the column
      itself carries a Postgres unique index (`@Column(unique = true)`) that — like every other
      nullable-but-unique column in this app — allows any number of NULLs since Postgres never
      treats two NULLs as equal.
    - **Icon** (`Product.iconDataUrl`, this pass): a small picture per product, uploaded the same
      way as the business logo (Settings > Business Details) — a `data:` URL read client-side via
      `FileReader`, capped at 2MB, no server-side resizing. Shown as a thumbnail in this list, in
      the edit form, and — new this pass — as a small icon on the product's tile on the cashier
      terminal's product grid.
  - **Loading 100,000 products** (this pass — "check the loading of 100000 products"): this
    screen used to fetch the *entire* product table in one request and render every row in one
    unbounded table. `GET /api/products/all` is now a real server-side pager
    (`page`/`size`/`q` params, capped at 200 rows per page regardless of what's requested,
    returned as a plain `{content, page, size, totalElements, totalPages}` envelope —
    `com.posapp.common.PageResponse` — rather than Spring's own `Page<T>` JSON shape) with a
    search box (name/SKU/barcode/code) and page-size selector, debounced so typing doesn't fire a
    request per keystroke. The edit form no longer loads the whole list to find one product by
    id (broken once that list was paged anyway) — it fetches `GET /api/products/{id}` directly.
    The terminal's own product search (`GET /api/products`, used by "All products" and by typing
    in the search box) is capped server-side at 200 results
    (`CatalogService.TERMINAL_SEARCH_CAP`) for the same reason — selecting "All products" against
    a 100,000-row catalog can't hand the terminal tens of thousands of tiles to render; a small
    hint appears under the grid when that cap is the reason the list stopped. See
    `backend/scripts/seed_100k_products.sql` for a fast, set-based script that generates 100,000
    sample products directly in Postgres to test this against (run it after starting the backend
    at least once, so `ddl-auto=update` has added the `code`/`icon_data_url` columns first).
  - **Categories** (`/admin/categories`): create/edit/delete (blocked server-side if any product
    still uses it), with a color picker matching the terminal's category tiles.
  - **Customers**, now its own collapsible sidebar group (Task: "Rename Customers menu to List
    and put it under Customers Menu") rather than a single flat link:
    - **List** (`/admin/customers` — same route as always, only the nav label/grouping changed):
      create/edit, now including a **Tax ID** field (Task: "Add Tax ID on customer entity") — a
      VAT/registration number a business customer can quote on their receipt, purely
      informational (nothing in checkout/tax math reads it), nullable since most walk-in
      customers won't have one.
    - **Statement** (`/admin/customers/statement`, new this pass — Task: "Add Customers Report
      (Customer Statement)"): pick a customer and a date range, see every completed sale they
      made in it (invoice, date, register, cashier, items, subtotal, tax, grand total, payment
      method) with a totals row, and export the same list as Excel or PDF — same backend
      machinery (`ReportController`/`ReportService`/`ReportExportService`) the seven POS Reports
      use, just with its own hand-built screen (a customer picker isn't one of the generic
      report-viewer's filter types) and its own home under **Customers** instead of the Reports
      picker.
  - **Users**, also now its own collapsible sidebar group (Task: "Move Staff menu to new Menu
    called Users") holding **Staff** (`/admin/staff` — route/label unchanged): create/edit staff
    accounts (username, full name, role, active flag), reset a staff member's password, and
    set/change their PIN (4–6 digits, used at the terminal — see "Multiple cashiers per shift"
    above; the list shows Set/Not set per staff member). An admin cannot deactivate their own
    account (enforced server-side in `UserService.updateUser`) — that's still only an admin
    resetting *someone else's* account; see **My Profile** below for a user managing their own.
  - **Shifts** (`/admin/shifts`, nav link moved under the **POS** sidebar group as of this pass —
    see "Sidebar navigation" below; the route itself is unchanged): an oversight view over every
    register session (open and closed, across all registers/cashiers) with a status filter, each
    row's sale count and gross sales for the session, a **▸ Cashiers** expander showing every
    `CashierSession` that joined that shift (loaded on demand) plus, while the shift is still
    `OPEN`, a staff picker and **+ Add cashier** button that gives the chosen active staff member
    their own `CashierSession` on that shift right away — no PIN needed (see the "Registers" bullet
    above for why this was added) — and a **Close all sessions** button that ends every still-open
    one on that shift without closing the shift itself, and
    links to the existing force-close (`/sessions/:id/close`) and Z-report
    (`/sessions/:id/report`) screens. This reuses the existing `RegisterSession` data — "shift"
    and "till session" are the same thing — it's the admin-facing list with totals that was
    missing; the `/sessions` list itself is role-scoped, not admin-only (see "Sessions" above).
  - **Settings** (new collapsible sidebar group as of this pass, grouping the "how the store is
    configured" screens):
    - **Payment methods** (`/admin/payment-methods` — route unchanged, only the nav link moved):
      what used to be a hardcoded CASH/CARD/MOBILE_MONEY/BANK_TRANSFER enum is an admin-manageable
      list (`PaymentMethodEntity`/`PaymentMethodController`, `/api/payment-methods`). Create a
      method with a `code` (e.g. `CASH`), a display `name`, and a `cash` flag (whether it counts
      as cash for till reconciliation — `RegisterService`'s expected-cash math and
      `ReturnsService`'s cash-refund total both look this up instead of comparing against a fixed
      `CASH` constant). A method's `code` is immutable once created (the edit form disables that
      field) because it's stored as a plain string on every historical `Payment`/`SalesReturn`
      row, not a foreign key — renaming or deactivating a method never rewrites past sales.
      Deactivating a method (there's no delete) just hides it from the checkout/refund dropdowns
      going forward. **Display order is now admin-configurable** (this pass — Task: "Make payment
      method order dynamic so I can set the first, second on checkout screen"): a plain integer
      `sortOrder` field (lower shows first; ties break alphabetically by name) on both the create
      and edit forms, plus an **Order** column on the list screen. The backend sorts by it
      everywhere a method list is returned (`findByActiveTrueOrderBySortOrderAscNameAsc` /
      `findAllByOrderBySortOrderAscNameAsc`), so Checkout's payment-method row — which simply maps
      `listActive()`'s array in order into its `*ngFor` — reorders itself automatically with no
      separate checkout-side sorting logic. The four seeded methods default to CASH, CARD, MOBILE
      MONEY, BANK TRANSFER, CREDIT in that order; every method created before this pass defaults to
      `0` (same as a never-configured new one), so nothing looks unordered until an admin actually
      sets distinct values. The four original methods are seeded on first run so existing behavior is
      unchanged out of the box.
    - **Taxes** (`/admin/settings/taxes`): what started as a single fixed `app.pos.tax-rate`
      application property, then (an earlier pass) a single admin-manageable "one active rate for
      the whole store" row, is now **per-product** tax (this pass — "Manage multiple tax where
      each product will have tax Id"): an admin-manageable list of named rates
      (`TaxRate`/`TaxRateService`/`TaxRateController`, `/api/tax-rates`, all methods `ADMIN`-only —
      a cashier never picks a tax rate at checkout, so unlike payment methods there's no "active
      list for anyone logged in" endpoint here), and **any number of rates can be `active` at
      once** — there's no more "activating one deactivates the others" exclusivity. Each product
      (`/admin/products`) picks which rate applies to it — or none, for a deliberately tax-exempt
      product — via `Product.taxRate` (nullable `@ManyToOne`). Like `PaymentMethodEntity`, a
      `TaxRate` has a `code` (e.g. `STD`) that's set at creation and never editable afterward
      (mirrors `PaymentMethodEntity.code`'s reasoning below) alongside an editable `name` and
      `ratePercent`, plus (this pass — Task: "On Tax rate add inclusive attribute where if it is
      true the in the tax in price is inclusive") an editable **"Tax-inclusive price"** checkbox,
      `TaxRate.inclusive` (default `false`).
      - **Inclusive vs. exclusive tax**: `inclusive = false` (the default, unchanged behavior) adds
        tax on top of a product's `unitPrice` at checkout — the classic "+ tax" total. `inclusive =
        true` means `unitPrice` already has that rate's tax folded into it (the shelf price the
        customer sees *is* the final price); `OrderCalculator` then extracts the tax back out
        (`amount * rate / (1 + rate)`, the standard tax-inclusive-price formula) instead of adding
        it on top. Either way, `lineNetTotal(line) + lineTax(line)` still equals what the customer
        pays for that line, and `subtotal(order) + taxTotal(order)` still equals `total(order)` —
        only what counts as "subtotal" changes: for an inclusive line it's the price *minus* the tax
        baked into it, not the raw displayed price. The per-line displayed price (`lineTotal`, and
        every screen that shows it — cart, receipt) is untouched either way, since showing the
        sticker price as-is is correct for both cases; only the tax/subtotal *split* changes.
      - **How tax is computed per sale**: when a line is added to a cart, `OrderService` reads the
        product's *currently assigned* `TaxRate` and snapshots its `code`/`ratePercent`/`inclusive`
        onto that `OrderLine` right then (`OrderLine.taxCode`/`taxRatePercent`/`taxInclusive`) — the
        same snapshot-not-live-reference pattern `OrderLine.description`/`unitPrice` already used
        for price/name. `OrderCalculator.lineTax()` computes each line's tax from its own snapshotted
        percent and inclusive/exclusive flag (zero if the percent is null, i.e. tax-exempt), and
        `OrderCalculator.lineNetTotal()`/`subtotal()`/`taxTotal()`/`total()` sum across lines —
        there's no more single external "the rate" (or "is it inclusive") parameter threaded through
        order math at all. A later edit to a product's tax assignment, a rename of a rate, a change
        to its percent or its inclusive flag, or deactivating it entirely **never** rewrites an
        already-rung line, because nothing about checkout looks the rate up live from the `TaxRate`
        table again. The Reports (below) and product list/edit screens show the same
        `taxRatePercent`/`taxRateInclusive` pairing, e.g. "Standard (15%, incl.)".
      - **Returns** work the same way: `ReturnsService.processReturn` computes the tax portion of
        each returned line's refund from that specific `OrderLine`'s own `taxRatePercent` and
        `taxInclusive` flag (via the same `OrderCalculator.taxOnAmount` helper `lineTax` uses), not
        a single store-wide rate or a hardcoded exclusive assumption — a return on a sale with mixed
        tax rates (or a mix of inclusive/exclusive lines) across its lines refunds the correct tax
        per line, and the refunded "subtotal" share is likewise net of any embedded tax.
      - **Seeding/upgrades**: `DataSeeder` seeds one `"Standard"` rate (code `STD`, from
        `app.pos.tax-rate` — still the *only* thing that property does, and only on a brand-new
        database) and assigns it to every demo product it creates. On every startup (not just a
        fresh database) it also backfills that same Standard rate onto any product that has no tax
        rate assigned — covering products that existed before this migration — so upgrading an
        existing deployment doesn't silently make previously-taxed products tax-exempt.
      - **Known trade-off**: pre-existing `OrderLine` rows from *before* this migration (created
        under the old single-flat-rate model) have no snapshot (`taxCode`/`taxRatePercent` are
        both `null`) — `OrderCalculator.lineTax()` treats that as zero, so historical completed
        sales rung before this pass will show **$0 tax** on their receipts going forward, even
        though they collected tax at the time. This is a direct consequence of the old design never
        having stored a per-order tax figure at all (`OrderDto` always recomputed tax live from
        whichever rate was *currently* active — so a rate edit could already silently change a
        past sale's displayed tax before this pass); there's no clean way to reconstruct what rate
        applied to a specific historical line after the fact. If preserving historical tax figures
        matters for your deployment, backfill `OrderLine.tax_code`/`tax_rate_percent` from your own
        records before/after upgrading, or accept that old receipts now read $0 tax.
    - **Business details** (`/admin/settings/business`, new this pass — Task: "Under Settings menu
      add Business Details menu" / "Add Business Details like name, address, contact, TAX ID,
      logo, etc"): business name (required), address, phone, email, tax ID, and a logo image —
      picked as a file, read client-side with `FileReader.readAsDataURL` (capped at 2MB before
      upload — there's no server-side resizing), and stored as-is as a `data:` URL string. Backed
      by a new singleton `StoreSettings` row (new `settings` backend package: `StoreSettings`,
      `StoreSettingsService`, `StoreSettingsController` at `/api/settings`, `ADMIN`-only end to
      end) — there's exactly one row in practice, created on first run by `DataSeeder` (mostly
      empty; an admin fills it in) and fetched-or-created on demand otherwise, the same
      singleton-row pattern as any other "one row of store-wide config" table would use, unlike
      **Payment methods**/**Taxes** above which are proper multi-row lists. **The business name and
      logo are now shown app-wide, not just on this settings screen** (this pass — Task: "Add
      Business name and logo in the header of system template (where label POS)" / "Add Business
      details + logo on the header of receipt printout"): the topbar's plain "POS" brand label is
      replaced with the business name (+ logo, if uploaded) once Business details has been filled
      in, and the same fields appear as a header block on every printed receipt (see **Checkout**
      above). Reading `GET /api/settings` for this reason is why the twenty-fourth pass changed the
      controller's access from class-level `ADMIN`-only to a method-level `@PreAuthorize` on just
      the two `PUT` endpoints — the topbar and receipt are seen by cashiers just as often as admins,
      so the **read** needed to open up (the same "open read, gated write" shape **Payment
      methods**/**Customer credit** already use), while writes stayed `ADMIN`-only.
    - **System settings** (`/admin/settings/system`, new this pass — Task: "Add System setting
      like currency, time zone, date format, etc"): currency (a curated dropdown of ~10 common
      codes — USD, EUR, GBP, UGX, KES, TZS, RWF, NGN, GHS, ZAR, INR — each pre-filling its usual
      symbol into a separately editable **currency symbol** field, so an admin can show a code like
      "UGX" instead of a symbol if they prefer), time zone (populated from the browser's own
      `Intl.supportedValuesOf('timeZone')` — the full, always-current IANA database, with a short
      static fallback list for the rare browser without that API — rather than hand-maintaining a
      timezone list), and date format (a fixed choice of four common patterns). Saved to the same
      `StoreSettings` row as **Business details** above, via its own `PUT /api/settings/system`
      that only ever touches these four fields. **Still not wired into how amounts or dates actually
      render anywhere in the app** (unlike Business details' name/logo, see above) — receipts,
      reports, and every other screen still format plain numbers/dates with no symbol or per-store
      pattern applied; doing that is a cross-cutting change touching every screen that shows a
      price or a date, out of scope for adding the settings screen itself (see "Known limitations"
      below).
    - **Reports** (`/admin/reports`, brand new this pass — Task: "Add POS Report where it will
      show the list of the following reports and by click on each report it will open report
      with different filters and export as EXCEL,PDF"): a picker grouped exactly as asked — **POS
      Sales-Transactions** (Total POS Categories Sales, Total POS Products Sales, POS Shifts
      Sales, Detailed POS Shifts Transactions) and **POS Profit** (POS Shifts Profit, Total POS
      Categories Profit, Total POS Products Profit) — each linking to its own report page
      (`/admin/reports/:key`). New `reports` backend package: `ReportController`
      (`/api/reports/*`, `ADMIN`-only, same as `DashboardController`) and `ReportService`, which
      builds every report from `OrderRepository.findCompletedForReport` (every completed sale in
      a date range, optionally narrowed to one register) grouped in Java by category/product/shift
      — the same "group in Java, not SQL" convention `OrderService.aggregate` already uses for the
      Z-report. Filters, on every report: a date range (defaults to the last 30 days) and a
      register; the two per-product reports also filter by category. Every report supports
      `?format=xlsx` or `?format=pdf` (`ReportExportService`, using Apache POI and OpenPDF) for
      download, alongside the plain JSON the on-screen table uses — one frontend
      `ReportViewerComponent` renders all seven from a shared config (`core/report-defs.ts`)
      rather than seven near-identical components.
      - **"Profit" throughout means revenue (post-discount, pre-tax — `OrderCalculator.lineNetTotal`,
        not `lineTotal`: for an inclusive-tax line `lineTotal` still has its tax folded in) minus
        cost of goods** — sales tax collected is never counted as revenue, since it's money held
        for remittance, not income. Cost comes from the new `Product.costPrice`/`OrderLine.unitCost`
        (see **Products** above); a product with no cost recorded reports 100% margin until an
        admin fills it in. The Sales reports (Total POS Categories/Products Sales) use the same
        `lineNetTotal` + `lineTax` split for their "Sales"/"Tax"/"Grand total" columns, for the same
        reason — summing the raw sticker price for an inclusive-tax line would double-count its
        embedded tax once the Tax column is added back on.
      - **Shift** in these reports means `RegisterSession` (the till/shift), same meaning as
        everywhere else in this app — "POS Shifts Sales"/"POS Shifts Profit" group by shift,
        "Detailed POS Shifts Transactions" is one row per completed sale (not per shift), and the
        two "Total POS Categories/Products" reports group across every matching shift at once.
- **Sidebar navigation** (`shared/shell.component.ts`, reworked this pass — Tasks 2–6): every link
  and toggle now has a leading icon (Task 3 — plain emoji, e.g. 🛒 POS, 🖥️ Registers & Terminal,
  📋 Sessions, ⚙️ Settings), and every collapsible group's expand/collapse indicator moved to the
  right edge of its row and switched from the previous right/down-pointing (▸/▾) pair to an
  up/down (▲/▼) pair — `▼` collapsed, `▲` expanded (Task 2). **Dashboard** is still the very first
  link (admins only), above every group; the everyday screens are grouped under **POS**
  (Registers & Terminal — `ADMIN`-only nav link now, see "Registers" above; Sessions; Returns;
  Receipts; and, moved in from the flat Admin list this pass, **Shifts**, Task 4); inside the admin
  section, **Products**/**Categories** stay grouped under **Inventory**, a new **Settings**
  group (Task 5/6) holds **Payment methods** (moved from a flat top-level admin link),
  **Taxes**, and (this pass) **Business details** and **System settings**, and (a later pass) the former flat **Customers** and **Staff** links became their
  own **Customers** (List/Statement) and **Users** (Staff) groups the same way — see
  **Customers**/**Users** above. The non-clickable "🛠️ Admin" heading that used to sit above the
  admin-only nav items has been removed — those items sit directly under the role check with no
  divider label above them.
  - **Sidebar groups are now an accordion, not five independent toggles** (Task: "Once one menu
    is open other should be closed"): `ShellComponent` replaced its per-group `posOpen`/
    `inventoryOpen`/`settingsOpen` booleans (each toggled independently, so any number could be
    open at once) with a single `openGroup: string | null` field plus a `toggleGroup(name)`
    method — clicking a group's header opens it and, as a side effect of there being only one
    `openGroup` value, closes whatever other group was open. Defaults to `'pos'` open (the
    everyday group) rather than every group collapsed, so the accordion doesn't hide everything
    on first load. State is still just a component field, not persisted across reloads.

- **Store credit / accounts receivable** (this pass — the 18-item credit request: "Enable credit
  for customer" through "Optional due date/payment terms"). A full add-on, not a bolt-on: every
  write action is `ADMIN`-only end to end (the user's explicit "who settles" answer), while the
  one read every cashier needs — a customer's current balance — stays open to any logged-in user.
  - **Enable credit / credit limit / default payment term** (Tasks 1, 2, 18): three new fields on
    `Customer` — `creditEnabled` (off by default), `creditLimit` (defaults to zero, never null,
    same "default missing numeric to zero" convention as `Product.costPrice`), and an optional
    `defaultCreditTermDays`. All three are `ADMIN`-only, set from Admin > Customers' existing
    form (a new "Store credit" section: an enable checkbox, and — shown only once enabled — the
    limit and default term inputs). The Customers list shows an "Enabled · limit X" / "Off" badge
    per row, plus a **Record payment** link for credit-enabled customers.
  - **Credit as a payment-method flag, not a separate checkout flow** (Tasks 3, 4 — "Cash + credit
    partial payment" / "100% credit sale"): `PaymentMethodEntity` gained a `credit` boolean,
    mirroring its existing `cash` flag exactly, and a seeded "CREDIT"/"Store Credit" method has it
    set. This means checkout's existing split-tender UI (tap a method, enter an amount, any
    combination summing to the total) already *is* "cash + credit" or "100% credit" — no new
    screen, just one more button. `SaleOrder` gained `creditAmount` (the sum of that sale's
    credit-flagged payments, snapshotted once at checkout — never live-derived from the payment
    method's flag later, same snapshot convention as tax/cost on `OrderLine`) and `dueDate`
    (explicit checkout value, else the customer's `defaultCreditTermDays`, else null/never-overdue).
  - **Credit limit enforcement** (Task 12): `CustomerCreditService.assertWithinLimit`, called from
    `OrderService.checkout` only when a sale has a credit portion — requires a customer on the
    order, requires that customer's `creditEnabled`, and rejects the sale if the customer's
    current outstanding balance plus this sale's credit portion would exceed their `creditLimit`.
    Checkout's payment-method row for the credit method is capped to the customer's currently
    available credit the same way every other method's row is already capped to the amount still
    due, and shows "Available credit: X" beneath it once a credit-enabled customer is on the sale.
  - **No floating/unapplied balance — allocations must sum to exactly the payment amount**: the
    deliberate design choice underlying items 5–8 and 13. A `CustomerPayment` (one customer
    settling their tab, in whole or in part) always carries one or more
    `CustomerPaymentAllocation` rows, each applied to a specific outstanding `SaleOrder` up to that
    order's own remaining balance, and the allocations on one payment must add up to exactly that
    payment's amount — there's no separate running "customer credit balance" sub-ledger to keep in
    sync. `CustomerCreditService.outstandingForOrder` (an order's `creditAmount` minus whatever's
    been allocated against it, floored at zero) is the one calculation every other credit feature
    reuses — checkout's limit check, the allocation screen, and all four new reports.
  - **Record Payment screen** (`/admin/customers/:id/record-payment`, `ADMIN`-only — Tasks 5, 6,
    7, 8, 13, 15: "Customer payment later" / "Partial settlement" / "Multiple payments against one
    order" / "Payment allocation to orders" / "Payment receipt" / "Payment history"). Allocation is
    **manual, oldest-first suggested** (the user's explicit answer to "how should a payment apply
    to outstanding orders?"): every outstanding order gets its own editable amount field; a
    "Suggest oldest-first" button (and typing a payment amount) greedily fills them oldest-first,
    but any row can be hand-edited before submitting — the backend only requires that the final
    amounts sum to exactly the entered payment. One payment can span several orders (item 8) and
    one order can receive several payments over time as it's partially settled (items 6, 7) — both
    fall out of the same allocation table with no separate code path. Recording a payment shows an
    inline printable receipt (item 13, a receipt number, amount, per-order allocations, and the
    customer's remaining balance) and a payment-history table below (item 15) that persists across
    visits (`GET /api/customers/{id}/payments`); `GET /api/customer-payments/{id}` reprints any
    past payment's receipt by its own id.
  - **Customer balance on POS screen** (Task 14): the terminal's customer picker shows "Owes X ·
    available credit Y" (or "Not enabled for credit") right under the customer dropdown, refreshed
    whenever the selected customer changes — backed by the same `GET
    /api/customers/{id}/credit-summary` endpoint checkout uses, the one credit endpoint open to any
    logged-in user rather than `ADMIN`-only.
  - **Four new reports under a new "POS Credit" section** on the Reports picker (Tasks 9, 10, 11,
    16, 17), reusing the same `ReportController`/`ReportExportService` (Excel/PDF export) as the
    original seven. Credit Sale Report (16) and Daily Credit Collection Report (17) take a date
    range but no register filter (`ReportDef.filterMode: 'dateOnly'` — a new option the generic
    `ReportViewerComponent` respects by hiding the register picker); Outstanding Credit Report (10)
    and Overdue Credit Report (11) are **snapshots** with no date range at all
    (`filterMode: 'none'` — the viewer hides the whole date block and runs automatically on open,
    since there's nothing to fill in first). The **Customer Statement** (item 9's closest existing
    relative) kept its existing shape — one row per invoice — and just gained three additive
    columns (credit amount, due date, outstanding) rather than being restructured into a mixed
    ledger of invoices and payments interleaved chronologically; that "everything in one timeline"
    interpretation is what the separate Payment History table on the Record Payment screen serves
    instead, so Customer Statement's existing contract (and every screen already built on it) never
    breaks.
  - **One-directional dependency to avoid a circular Spring bean**: `CustomerCreditService`
    (package `credit`) depends on `OrderRepository`/`CustomerRepository` directly, never
    `OrderService` — the same pattern `ReportService` already uses to read orders without depending
    on `OrderService`. This matters here specifically because `OrderService.checkout` needs
    `CustomerCreditService` for the limit check, so the dependency could only ever point one way.
- **Apps/plugins framework, starting with EFRIS Uganda e-Invoicing** (Task: "If this is headed
  toward Uganda-market use — proper e-invoicing/EFRIS integration with the Uganda Revenue
  Authority... I want to enable EFRIS... This will work as a plugin (or apps) to be installed and
  configured... I will share the required field to be added on receipt and new tables to be added
  once this app is installed"). A new `Admin > Settings > Apps` screen backed by a new `apps`
  package on the backend:
  - **A fixed, code-defined catalog, not a real plugin-jar/marketplace system** — `AppCatalog` is a
    small `List<Definition>` in code (currently one entry, `EFRIS_UG` — "EFRIS Uganda
    e-Invoicing"), the same "fixed list defined in code" pattern the frontend's report picker
    already uses for its report definitions. Adding a second app later means adding a second
    `AppCatalog` entry (and its own settings/behavior), not building a real installable-package
    system — there was no request for arbitrary third-party plugins, just a small fixed set of
    built-in integrations that can be turned on per store.
  - **Two-step lifecycle: Install, then separately Enable.** A new `InstalledApp` row (one per
    store, unique per `AppKey`) tracks `installed` and `enabled` as two independent booleans.
    Installing an app always leaves it disabled — so an app with no settings filled in yet never
    starts acting on real sales the moment someone clicks "Install." Enabling is a distinct,
    explicit second action, always available from both the Apps list and an app's own Configure
    screen. Uninstalling deletes the `InstalledApp` row (cascading to its settings) and requires an
    on-screen confirmation, since it discards whatever configuration was entered.
  - **Deliberately generic key/value settings, not an EFRIS-shaped form.** `InstalledAppSetting` is
    just `(appId, settingKey, settingValue)` with no fixed schema — because EFRIS's actual required
    fields (and any new tables needed to store what EFRIS returns per sale) haven't been specified
    yet; the user explicitly said those would come in a follow-up message. The Configure screen
    (`/admin/apps/:key`) is a plain add/remove key-value editor with an on-screen note that nothing
    here is validated or wired into any live integration yet. This was a deliberate choice to avoid
    inventing plausible-sounding EFRIS field names now and having to throw them away — the generic
    editor lets someone start typing in what they already know (e.g. an EFRIS API base URL or
    device/TIN identifier) without losing that work once the real structured form replaces this
    screen.
  - **New endpoints, all `ADMIN`-only** (`AppsController`, same tier as every other Settings
    screen): `GET /api/apps` (catalog merged with this store's install/enable state), `POST
    /api/apps/{key}/install|uninstall|enable|disable`, `GET`/`PUT /api/apps/{key}/settings`.
  - **`AppsService.isEnabled(AppKey)` exists but nothing calls it yet** — see Known limitations
    below; this pass intentionally stopped short of wiring any real checkout-time or receipt-time
    behavior for EFRIS.
- **EFRIS fiscal-receipt columns and per-terminal URL setting** (follow-up to the Apps framework
  above — Fourier's own spec: "When installing EFRIS the system will add the following column to
  SaleOrder... Add also Table EFRIS_setting column EFRIS_url... EFRIS_setting for one terminal"):
  - **`SaleOrder` gained eight nullable `TEXT` columns**: `sdcId`, `sdcDate`, `sdcReceiptNumber`,
    `internalData`, `signatureData`, `cisNumber`, `cisDate`, `mrcData` — exactly the fields
    specified, all mirrored onto `OrderDto` and the frontend `Order` model. All eight are null for
    every sale today, since nothing yet calls a real EFRIS/URA endpoint to populate them (see
    below) — this pass adds the storage and the receipt display, not the submission.
  - **The sale receipt (`checkout/receipt.component.ts`) shows a fiscal-receipt block once these
    fields are populated** — SDC ID, receipt number, fiscalization date, CIS number/date,
    signature, internal data, MRC — printed (not `no-print`) since these are meant for the
    customer's own paper copy, same as a real EFRIS receipt would show them. The block simply
    doesn't render at all while every field is null, which is every sale as of this pass.
  - **A new `efris_setting` table, one row per `Register` (terminal), not store-wide** — Fourier's
    own wording, "EFRIS_setting for one terminal," names a real difference from every other
    admin-configurable setting in this app (business details, system settings, tax rates, payment
    methods, and the generic `InstalledAppSetting` store the rest of the Apps framework uses):
    those are all one value for the whole store, but a real EFRIS deployment fiscalizes through a
    local device on each till's own network (a "Sales Data Controller"), so each terminal needs
    its own URL. New `EfrisSetting` entity (`register` FK unique, `efrisUrl` TEXT), its own
    `EfrisSettingService`/`EfrisSettingController` at a fixed `/api/apps/efris/terminals` path
    (`ADMIN`-only) rather than routed through `AppsController`'s generic `{key}` endpoints, since
    this is EFRIS-specific structured data, not something every catalog app needs.
  - **The Configure screen (`/admin/apps/EFRIS_UG`) gained a "Terminal EFRIS URLs" table** above
    its existing generic key/value editor — one row per register with an editable URL and its own
    Save button. The generic editor is unchanged and still there for anything else EFRIS (or a
    future app) needs that has no structured shape yet.
  - **Superseded by the next pass below** — this pass was schema plus display only; the actual
    submission is wired in immediately after.
- **Real EFRIS submission at checkout** (Fourier's own request: "Send to order data to EFRIS as
  json," with a working JSON-RPC request/response sample — Odoo's generic `/jsonrpc`
  "object.execute" calling convention, model `api.invoice`, method `create_api`):
  - **Two schema additions the sample payload needed but nothing captured yet**, both added after
    asking Fourier rather than guessing: `TaxRate.taxLabel` (the EFRIS/URA-RRA letter tax category,
    e.g. "A"/"B"/"C"/"D" — an admin maps each rate to one on the Tax Rates screen) and
    `Product.unspCode` (the UNSPSC-style commodity/tariff code EFRIS calls `unsp_code` — set per
    product). Both nullable, both snapshotted onto `OrderLine` at add-line time (`taxLabel`,
    `unspCode`), same "freeze it at the moment of sale" convention every other per-line field
    already uses (`taxCode`, `unitCost`, ...) — a later edit to a tax rate or product never rewrites
    what was actually sent for a past sale.
  - **`EfrisSetting` gained three more columns** (`efrisDb`, `efrisUid`, `efrisPassword`) alongside
    the existing `efrisUrl` — the JSON-RPC `db`/`uid`/`password` arguments EFRIS's own middleware
    expects (`args[0..2]` in the sample), still one row per terminal. The Configure screen's
    "Terminal EFRIS URLs" table gained DB/UID/Password columns to match; `efrisPassword` is stored
    (and resent on every call) in plaintext by necessity — EFRIS's protocol requires the raw value
    each time, the same way any third-party API key would.
  - **`EfrisSubmissionService`** (new, package `com.posapp.apps.efris`) builds the exact JSON-RPC
    tree from the sample using Jackson's `ObjectMapper`/`ObjectNode` and POSTs it with the JDK's
    built-in `java.net.http.HttpClient` — no new HTTP client dependency added. Deliberately holds
    zero reference to `SaleOrder`/`Register`/`Customer`: it takes and returns plain records
    (`EfrisConnection`, `EfrisInvoicePayload`, `EfrisInvoiceLine`, `EfrisSubmissionResult`) that
    `OrderService` builds from its own entities and applies the result back onto its own
    `SaleOrder`, so `apps.efris` never depends back on `order` — avoiding a circular Spring bean
    dependency, since `order` is the one that needs to call into `apps.efris`.
  - **How checkout triggers it**: `OrderController.checkout`/`syncOfflineSale` call the existing
    checkout logic (which commits its own transaction), then make a second, separate call to
    `OrderService.submitToEfrisIfApplicable(orderId)` — its own `@Transactional` method. This is a
    deliberate simplification: the outbound HTTP call to EFRIS happens synchronously, inline with
    the checkout HTTP response (briefly blocking it if EFRIS is slow to respond), rather than being
    queued for a background worker — there's no async/post-commit-hook infrastructure in this
    codebase yet, and adding one felt like scope creep beyond what was asked. `submitToEfrisIfApplicable`
    is a no-op (returns the order unchanged) whenever the order isn't `COMPLETED`, the order already
    has an `sdcId` (already fiscalized — protects a retried offline-sync request from double
    submitting), the `EFRIS_UG` app isn't enabled, or the order's terminal has no `EfrisSetting` URL
    configured — none of those are errors, just nothing to do.
  - **Failure policy Fourier chose when asked: "sale still completes, flagged for review."** A
    network error, an unreachable endpoint, or EFRIS itself rejecting the invoice never blocks or
    rolls back the sale — `submitToEfrisIfApplicable` catches everything and records
    `SaleOrder.efrisSubmittedAt` (when it was attempted) and `SaleOrder.efrisError` (plain text,
    null on success) instead, mirroring the offline-sync pass's existing `syncNote` pattern exactly.
    The receipt component shows an `efrisError` warning banner (`no-print`, staff-facing only) the
    same way it already shows `syncNote`.
  - **Parsing EFRIS's response is a best-effort read of the one sample given, not documented EFRIS
    behavior.** `sdc_data`/`cis_data` come back as opaque comma-separated strings with no field
    names; `EfrisSubmissionResult.success(sdcData, cisData)` splits each positionally
    (`sdc_data` → `[sdcId, sdcReceiptNumber, (dup), (unused "NS"), sdcDate, internalData,
    signatureData]`; `cis_data` → `[cisDate, cisNumber, mrcData, (unused amounts/count/TIN)]`) —
    documented in that method's own javadoc as the one place to fix the split/index logic if a real
    submission's fields land in the wrong `SaleOrder` columns.
  - **Not attempted this pass**: no retry queue for a failed submission (staff would need to notice
    the `efrisError` badge and handle it manually — there's no "retry EFRIS" button anywhere yet);
    no validation that a tax rate has a `taxLabel` or a product has an `unspCode` before checkout
    (a line simply submits `null` for either if unset, which will likely fail EFRIS's own validation
    — this pass didn't add a client-side warning for that); and `transaction_type`/`invoice_type`
    are hardcoded to the sample's own `"S"`/`"N"` since every sale this app submits is a normal
    completed sale.
  - **Fixed same day, after Fourier noticed it**: the "EFRIS commodity code" input on the Product
    form and the "EFRIS tax label" input on the Tax Rate form originally showed unconditionally —
    every store's admin saw two EFRIS-specific fields on its everyday product/tax-rate forms even
    if EFRIS was never installed at all. Both forms now call `AppsApiService.list()` and only
    render that one field when `EFRIS_UG` shows `installed: true`, mirroring how the Configure
    screen's own "Terminal EFRIS URLs" table is only ever reached by navigating into that specific
    app. The underlying `unspCode`/`taxLabel` values themselves are unaffected either way — hiding
    the field only changes whether it's *shown*; a value set while EFRIS was installed still loads,
    submits, and prints correctly if EFRIS is later uninstalled and the field disappears again.
  - **Request/response logging, added same day**: `EfrisSubmissionService` now logs every
    submission's outbound request and inbound response at `INFO`, each line tagged with the
    invoice number (`erp_code`) so a real submission can be traced end to end in the server's own
    log output — there's no admin-UI log viewer for this, just the application log. The logged
    request is never the literal one sent over the wire: a `redactedRequestJson` helper replaces
    the connection password (`args[2]`) with a fixed placeholder before serializing it for
    logging, falling back to a placeholder string (never the raw tree) if redaction itself ever
    fails, so a bug in that one method can't result in a terminal's EFRIS password landing in
    plain text in a log file. A network failure, a non-2xx response, and a response the parser
    couldn't understand are all still logged at `WARN` (as before), now also tagged with the
    invoice number for the same traceability.
  - **`payment_mode` wired up, same day**: Fourier confirmed four field mappings for the JSON sent
    to EFRIS. Three (`client_tin` ← the customer's `taxId`, `client_name` ← the customer's `name`,
    `client_phone` ← the customer's `phone`) were already exactly right in `OrderService
    .buildEfrisPayload` from the thirty-second pass — no change needed there. The fourth,
    `payment_mode` ← "the payment method," had been sent as a hardcoded `null` (EFRIS's own sample
    payload didn't populate it either, so there was nothing to copy at the time). `EfrisInvoicePayload`
    gained a `paymentMode` field; a new `OrderService.resolvePaymentMode(SaleOrder)` collects the
    sale's distinct `Payment.method` codes (the same plain code — `"CASH"`, `"CARD"`, etc. — this
    app already stores on every payment) in tender order and joins them with a comma for a
    split-tender sale, since EFRIS's sample sends this as one string field, not an array.
    `EfrisSubmissionService` now sends that value (or a JSON `null` if a sale genuinely has no
    payments, which shouldn't happen for a completed sale) instead of always sending `null`.
    **Not an EFRIS-defined payment-mode code** — no such enum was specified, so this sends the
    store's own payment-method vocabulary as-is, the same "carry whatever this app already has,
    don't invent a translation" choice `tax_label`/`unsp_code` already made.
  - **`tax_label` corrected to send the tax code, next day**: Fourier corrected the mapping — the
    JSON's `tax_label` field should carry this app's own tax-rate `code` (e.g. `"STD"`, the stable
    identifier already snapshotted onto `OrderLine.taxCode`), not the separate admin-entered
    EFRIS-category-letter field (`TaxRate.taxLabel`/`OrderLine.taxLabel`, e.g. `"A"`/`"B"`/`"C"`/
    `"D"`) the thirty-second pass had wired up instead, based on a misreading of Fourier's original
    sample. `OrderService.buildEfrisPayload` now passes `line.getTaxCode()` into
    `EfrisInvoiceLine.taxLabel` (kept under that name — like `erpCode`/`erpCodeRef` elsewhere on
    this record, it's named after the JSON key it becomes, not its source field). Since nothing
    reads `TaxRate.taxLabel`/`OrderLine.taxLabel` anymore, the "EFRIS tax label" input on the Tax
    Rate admin form was removed too — leaving it in place would have kept inviting an admin to set
    a value that silently did nothing, the same class of stale-UI gap the "EFRIS fields showing
    for stores without EFRIS" bug above was about. The underlying `taxLabel` columns/fields
    themselves were left alone (nullable, harmless, no migration needed) rather than deleted
    outright, in case a genuine future use for an EFRIS category letter shows up.
  - **Also asked for, same follow-up**: a "Retry EFRIS submission" button for a sale whose EFRIS
    submission failed. New `POST /api/orders/{id}/efris/retry` on `OrderController` — it just calls
    `OrderService.submitToEfrisIfApplicable` again, the exact same method the original checkout
    called; that method's own guards (order not `COMPLETED`, already has an `sdcId`, EFRIS not
    enabled, no connection configured for this terminal) already make repeat calls safe, so the
    endpoint adds nothing beyond exposing it. The receipt screen (`receipt.component.ts`) — which
    already showed the `⚠ EFRIS submission failed: ...` warning — now renders a button right next
    to it whenever `order.efrisError` is set; clicking it calls the new endpoint and replaces the
    on-screen order with whatever comes back, so a successful retry immediately swaps the warning
    for the fiscal-data block (`sdcId` etc.) with no reload needed. The button disables itself
    while a retry is in flight (so a slow EFRIS endpoint can't be double-clicked into two
    concurrent submissions) and shows a separate small error only if the retry call itself
    couldn't be reached — an EFRIS rejection on the retry just re-populates `efrisError` and the
    same warning shows again, no separate handling needed for that case.
  - **Investigated, not changed: a report that `client_tin`/`client_name`/`client_phone` were
    still null.** Re-read `OrderService.buildEfrisPayload`, `Customer`, `CustomerController`,
    `SaleOrder`'s `customer` association, the `/orders/{id}/customer` endpoint, and the terminal's
    customer picker end to end — every step is wired correctly: selecting a customer in the
    terminal persists it via `setCustomer` immediately (not deferred to checkout), and
    `buildEfrisPayload` reads `customer.getTaxId()/getName()/getPhone()` straight off whatever
    `order.getCustomer()` returns, with no other construction site anywhere in the codebase. No
    bug could be found in the code as it stands in this delivery. The most likely explanations for
    what Fourier saw: the specific sale in question was rung without a customer attached (walk-in
    default — check the receipt's own "Customer: ..." line for that sale), or it was tested against
    a build predating this delivery. The redacted request now logged at `INFO` for every submission
    (tagged with the invoice number) is the fastest way to confirm which: if `client_tin` is null
    there for a sale whose receipt clearly shows a real customer name, that's the concrete repro
    this needs to track the bug down further.
  - **`tax_amount`/`discount_amount`/`gross_amount` now fixed-2-decimal, next follow-up**: Fourier
    asked that "the amount value in json should be type of double with 2 decimals." These three
    fields (see the retry-button note above and `EfrisInvoiceLine`'s own javadoc for why they're
    always zero — the sample lets EFRIS compute them server-side) were being sent as the plain
    JSON integer `0` rather than a decimal. A JSON number has no real "double" type distinct from
    "integer," so what actually produces a fixed two-decimal number on the wire is a
    `java.math.BigDecimal` scaled to exactly 2 places — a Java `double` literal can't guarantee
    that (Jackson would still print a bare `0.0` for `0.0d`, not `0.00`). New
    `EfrisSubmissionService.ZERO_AMOUNT` constant (`BigDecimal.ZERO.setScale(2,
    RoundingMode.HALF_UP)`) replaces the three `0` literals, so the JSON now sends `0.00` for each
    — the same money-shaped representation `unit_price`/`tax_rate`/`discount_rate` already used.
  - **Tax code default "B", and the old EFRIS-category `taxLabel` columns dropped entirely**:
    Fourier asked for a default tax code of `"B"` (URA/RRA's standard-rate category letter), and
    for `TaxRate.taxLabel`/`OrderLine.taxLabel` to be removed as columns, not just left unused.
    `OrderService.DEFAULT_EFRIS_TAX_CODE` is the new `"B"` constant — `buildEfrisPayload` now
    falls back to it whenever a line's `taxCode` is null or blank (a tax-exempt line, or one
    predating the tax-rate feature), since EFRIS requires `tax_label` on every line and a bare
    `null` isn't a valid category. The `taxLabel` field is gone from `TaxRate` and `OrderLine`
    (entities), `TaxRateDto`/`TaxRateRequest`/`UpdateTaxRateRequest` (backend DTOs),
    `TaxRateService` (no longer sets it), and the frontend's `TaxRateOption` model,
    `tax-rate-api.service.ts` request payloads, and `tax-rate-form.component.ts` (which was
    already round-tripping a value nothing read since the previous pass — now gone for real). The
    two DB columns themselves are left in place (this app's schema is `ddl-auto=update`, which
    only adds columns, never drops them) — orphaned, harmless, and outside this pass's scope.
  - **`sdc_data`/`cis_data` parsed to Fourier's exact spec, and printed on the receipt**: the
    previous split/index logic was an explicit best-effort guess from a single worked example
    (see `EfrisSubmissionResult`'s old javadoc); Fourier has now supplied the real rule.
    `sdc_data` is 7 comma-separated values: `[0]` `sdcId`; `[1..3]` joined as `"{1}/{2} {3}"` into
    `sdcReceiptNumber` (e.g. `"301271/328875 NS"` — previously only `[1]` was kept and `[2]`/`[3]`
    were discarded as "unused"); `[4]` `sdcDate`; `[5]` `signatureData`; `[6]` `internalData` —
    note `[5]`/`[6]` were swapped from the old guess. `cis_data` is 3 comma-separated values:
    `[0]` `cisDate`, `[1]` `cisNumber`, `[2]` `mrcData` (the old code already had this part right).
    New private `EfrisSubmissionResult.joinReceiptNumber` builds the combined receipt number;
    `splitLenient`'s expected-length for `cis_data` dropped from 7 to 3 to match the real shape.
    No `OrderService`/`SaleOrder` changes were needed — the eight fiscal columns and their
    `submitToEfrisIfApplicable` wiring were already correct, only what fed them was wrong.

    The receipt (`receipt.component.ts`) now prints these as their own "SDC INFORMATION" section
    right after Payment, matching Fourier's own layout: SDC DATE and TIME side by side on one
    line (both come out of the single `sdcDate` value — `sdcDate`/`cisDate` are themselves a
    combined "date time" string, e.g. `"16/09/2026 07:38:15"`, per the parsing above), then SDC
    RECEIPT NUMBER and SDC ID as label/value rows, then Internal Data and Receipt Signature
    centered on their own lines (long opaque strings don't fit a label/value row), a divider, and
    the CIS RECEIPT NUMBER / CIS DATE+TIME / MRC rows. New component method
    `dateTimeParts(raw)` splits a combined date-time string on its first space (falling back to
    putting everything in `date` and leaving `time` blank if there's no space, so a
    differently-shaped response still prints something instead of breaking the layout) — used for
    both `sdcDate` and `cisDate`. The whole section still only renders when `sdcId` is set, same
    gate as before.
  - **Fixup, same day: a leftover `line.setTaxLabel(taxRate.getTaxLabel())` in `buildLine`**:
    Fourier caught a real gap in the previous pass's `taxLabel`-removal sweep — `OrderService
    .buildLine` (the snapshot-at-add-line-time method) still had this call, which the earlier
    grep for `taxLabel` across the codebase should have caught but didn't (it was checked by
    method name — `buildEfrisPayload`, the DTOs, the admin form — rather than a plain full-file
    search of `OrderService.java` itself). With `TaxRate.taxLabel`/`OrderLine.taxLabel` already
    deleted as fields, this line no longer compiles at all — the static brace/package check this
    project relies on (no Maven access in this sandbox — see "Known limitations" below) can't
    catch a missing method the way a real `mvn compile` would have. Removed; the surrounding
    `taxCode`/`taxRatePercent`/`taxInclusive` snapshot lines are untouched.
  - **Walk-in Customer now pre-selected on a new cart, next follow-up**: Fourier: "since we have
    a default customer 'Walk-in Customer' it is better to set this customer as default selected
    in the terminal (Selling cart) instead of having null." `DataSeeder` already seeded a real
    Customer row named "Walk-in Customer" on a fresh install, but nothing ever selected it — a new
    cart's `customerId` just stayed null until a cashier picked someone, and the terminal's
    customer dropdown's null option was itself mislabeled "Walk-in Customer" (implying it *was*
    that customer, when it was really "no customer at all"). That gap is also why an earlier
    investigation into null `client_tin`/`client_name`/`client_phone` in the EFRIS JSON came up
    with nothing to fix in the code — a walk-in sale genuinely had no customer attached, so those
    fields were correctly null; this pass is what actually closes that gap.

    New `Customer.walkIn` boolean (default false) marks the one customer the terminal should
    pre-select. `DataSeeder` sets it true on the "Walk-in Customer" row it seeds fresh, and a new
    `backfillWalkInCustomer` step (runs every startup, unconditionally, same pattern as
    `backfillProductTaxRates`) flags an *existing* store's own identically-named customer on
    upgrade, so nobody has to do this by hand. Not admin-editable from the Customer form —
    changing which customer this is means editing the seed or the database directly, deliberately
    out of scope for this pass.

    `CustomerDto` now carries `walkIn`; the terminal (`terminal.component.ts`) tracks
    `walkInCustomerId` (found from `customerApi.list()`) and applies it via
    `applyDefaultCustomer()` — called only right after a *brand-new* cart is created
    (`loadTabs`'s empty-tabs branch and `newOrder()`), deliberately not on every ordinary tab
    switch: a cart a cashier explicitly set to "No customer" must stay that way when they switch
    back to it, not get silently re-defaulted. It persists the pick through the existing
    `setCustomer()` call, exactly as if the cashier had chosen it from the dropdown — not just a
    frontend display value, so it's genuinely on the order server-side for EFRIS to read.

    The dropdown's null option is no longer labeled "Walk-in Customer" (that name now belongs to
    the real customer entry below it) — it reads "No customer" instead, for a cashier who
    deliberately wants a cart with nobody attached. The offline cart (`offlineCustomerId`,
    populated from the IndexedDB-cached customer list) got the identical treatment: default
    applied when the cache loads or after a queued sale resets the cart, same "No customer"
    relabel. If a store has no customer flagged `walkIn` at all (no fresh-install seed and no
    existing customer literally named "Walk-in Customer" for the backfill to find), the terminal
    simply falls back to its old null-by-default behavior — no regression, just no improvement
    until such a customer exists.

- **EFRIS success/failure decision corrected, raw response now recorded (2026-09-16)**: Fourier:
  "if error is not null and not empty that is when we record error ... also record Efris response."
  `EfrisSubmissionService.parseResponse` previously required EFRIS's `result.error` to equal the
  literal string `"SUCCESS"` for a submission to count as a success — anything else, including a
  blank/absent `error` field, was treated as a rejection. That was backwards from how EFRIS
  actually signals outcome: the corrected rule is `status != null && !status.isBlank()` means
  failure, so a null or empty `error` (EFRIS's own way of saying "no problem") is now correctly
  read as success, matching Fourier's own stated rule.

  Separately, the raw EFRIS response body — whatever text/JSON EFRIS actually sent back — is now
  kept end to end instead of only ever reaching the application log. `EfrisSubmissionResult`
  gained an 11th field, `rawResponse`, threaded through every call site in
  `EfrisSubmissionService` that has an actual HTTP response available (the non-2xx-status
  failure, all three failure returns inside `parseResponse`, the parse-exception catch, and the
  success path) — the handful of earlier failures that never get as far as receiving a response
  (no EFRIS URL configured, the request couldn't be built or sent) keep the original
  single-argument `failure(String message)` and leave `rawResponse` null, since there's genuinely
  nothing to record yet. `SaleOrder` gained a matching nullable `efrisResponse` TEXT column,
  written in `OrderService.submitToEfrisIfApplicable` right alongside `efrisSubmittedAt` so it's
  captured on both success and failure — reviewable next to the existing `efrisError` field, same
  "sale still completes, flagged for review" policy as before. `OrderDto` now exposes
  `efrisResponse` alongside the other fiscal fields for API consistency, though no UI currently
  displays it (not requested this pass). Backend-only change; no frontend files were touched.

- **Customer dropdown's "No customer" placeholder removed, replaced by the real Walk-in Customer
  DB row (2026-09-16)**: Fourier: "Instead of No customer (in select box) replaced by Walk-in
  Customer Customer from DB." The terminal's customer `<select>` (both the online cart and the
  offline cart) previously had a synthetic `<option [ngValue]="null">No customer</option>` above
  the real customer list, left over from before Walk-in Customer was pre-selected by default — a
  cashier could use it to deliberately clear a cart to no customer at all. That option is now
  removed from both dropdowns entirely; the real seeded "Walk-in Customer" row already renders via
  the existing `*ngFor` over `filteredCustomers`/`offlineCustomers`, and `applyDefaultCustomer`/
  `loadOfflineCatalog`'s existing default-selection logic (unchanged) is what makes it show there
  pre-selected on a new cart, so nothing else had to change to make it "the" default option instead
  of a fake null one. A cashier can still change the cart to any other real customer from the same
  dropdown, same as before — the only thing that's gone is the specific "no customer attached at
  all" choice.

  Frontend-only change: one file edited, `terminal.component.ts` (two `<option>` removals plus a
  doc-comment update on `applyDefaultCustomer` to stop describing a "No customer" selection that
  the UI no longer offers). Both dev and production `ng build` pass cleanly; no backend files
  touched, so the structural check stayed at 167 files.

- **QR code added to the EFRIS printout (2026-09-16)**: Fourier: "add QR CODE on the printout of
  receipt: input_data=sdc_date#sdc_id#sdc_receipt_number#+internal_data#+signature_data." The
  receipt's SDC INFORMATION block (printed only for a sale actually submitted to EFRIS, gated on
  `o.sdcId`) now ends with a centered QR code encoding exactly that payload — `sdcDate`, `sdcId`,
  and `sdcReceiptNumber` joined with `#`, followed by `internalData` and `signatureData` each
  additionally prefixed with a literal `+` before their own `#` separator. Missing sub-fields
  degrade to empty segments (never the literal string "null"), consistent with how every other
  field in this printout already handles a short/garbled EFRIS response.

  Generated entirely client-side with the `qrcode` npm package (new dependency, plus
  `@types/qrcode` as a dev dependency) — `ReceiptComponent.generateQrCode()` builds the payload
  string and calls `QRCode.toDataURL(...)`, storing the resulting PNG data: URL in a new
  `qrCodeDataUrl` field that the template renders as a plain `<img>`; a rejected/failed generation
  just leaves it null, so a receipt is never blocked from printing over a QR code failure. Called
  once after the order first loads and again after a successful EFRIS retry (`retryEfris`), so a
  receipt that failed EFRIS submission and is then retried successfully picks up its QR code
  without a page reload. `angular.json`'s `build.options` gained `"allowedCommonJsDependencies":
  ["qrcode"]` to silence the CLI's CommonJS-optimization-bailout warning for that package (it isn't
  published as ESM) — a cosmetic build-log fix, not a behavior change.

  Frontend-only change: `package.json`/`package-lock.json` (new `qrcode`/`@types/qrcode`
  dependencies), `angular.json` (the CommonJS allowlist entry), and `receipt.component.ts`
  (`qrCodeDataUrl` field, `generateQrCode()`, the two call sites, and the `<img>` in the template).
  Both dev and production `ng build` pass cleanly with no new warnings. No backend files touched —
  the structural check stayed at 167 files.

- **Tax code printed after each line's total, and two EFRIS letterhead logos added (2026-09-16)**:
  Fourier's two-part request. Task 1: "Add tax code after total order_line" — each order line's
  right-hand total cell on the receipt now prints that line's own `taxCode` (the EFRIS tax code
  already snapshotted onto `OrderLine` at add-line time, e.g. "B") in parentheses right after the
  amount, e.g. "12.00 (B)"; a line with no tax code (tax-exempt, or rung before this feature
  existed) just shows the plain total, same as before. Frontend-only, one line changed in
  `receipt.component.ts`'s line-item template.

  Task 2: "On EFRIS setting add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the
  printout." `EfrisSetting` (the per-terminal EFRIS row this app already had for URL/db/uid/
  password) gained two new nullable TEXT columns, `uraLogoDataUrl`/`efrisLogoDataUrl` — data: URLs,
  same convention as `StoreSettings.logoDataUrl`. Admin > Settings > Apps > EFRIS's per-terminal
  table gained two matching columns with a file-upload input each (same FileReader.readAsDataURL
  pattern as the existing Business Details logo upload, 2MB cap), saved through the existing
  per-terminal PUT endpoint (`UpdateEfrisSettingRequest` gained the same two fields).

  The receipt needed a way to read just these two images without also exposing that terminal's
  EFRIS URL/db/uid/password to whoever's printing a receipt — any logged-in cashier can print a
  sale, but the rest of that settings table is admin-only. Rather than loosen that, a new endpoint,
  `GET /api/apps/efris/terminals/{registerId}/logos`, was added with a method-level
  `@PreAuthorize("isAuthenticated()")` override on the otherwise `hasRole('ADMIN')`-gated
  `EfrisSettingController`, backed by a new narrow `EfrisLogosDto` (just the two logo fields) and
  `EfrisSettingService.getLogosForRegister`. `OrderDto`/the frontend `Order` model both gained a
  `registerId` field (the order's own terminal's Register id, previously only `registerName` was
  exposed) so the receipt knows which terminal's logos to ask for.

  On the printout, the two logos render as a letterhead row — URA on the left, EFRIS on the right,
  fixed-width slots so a single uploaded logo still lands on the correct side — right above "SDC
  INFORMATION," gated the same way the rest of that block already is (only for a sale actually
  submitted to EFRIS, and only when at least one of the two logos has actually been uploaded for
  that terminal). `ReceiptComponent` fetches them once via the new endpoint when the order first
  loads (`loadEfrisLogos`); any fetch error just leaves them unset, same "never blocks printing"
  convention as every other best-effort fetch already in that component.

  Backend: six files edited (`EfrisSetting.java`, `EfrisTerminalDto.java`,
  `UpdateEfrisSettingRequest.java`, `EfrisSettingService.java`, `EfrisSettingController.java`,
  `OrderDto.java`) plus one new file, `EfrisLogosDto.java` (168 files total, static review clean).
  Frontend: `models.ts` (`registerId` on `Order`, two logo fields on `EfrisTerminal`, new
  `EfrisLogos` interface), `efris-api.service.ts` (new `getLogosForRegister`, `saveTerminalSettings`
  widened), `app-settings.component.ts` (two logo upload columns), and `receipt.component.ts` (tax
  code + the letterhead row/`loadEfrisLogos`). Both dev and production `ng build` pass cleanly.

- **EFRIS logos moved to the top of the receipt, flanking a centered "Receipt" title
  (2026-09-16)**: Fourier's follow-up on the previous pass: "those two logos ... right at the top
  of receipt. Better put Receipt Label in center." The URA/EFRIS letterhead row (added the same
  day, just above SDC INFORMATION) moves to the very top of the printout — the first thing inside
  `.receipt` — with the "Receipt" heading itself centered between the two logos instead of sitting
  on its own line below the business details block. The plain centered "Receipt" heading
  (unchanged) still renders on its own when the terminal has no logos configured, via an
  `<ng-template #plainReceiptTitle>` fallback on the row's `*ngIf`.

  Deliberately no longer gated on `o.sdcId`: it was fetched/gated that way only because it first
  landed inside the SDC INFORMATION block, which only renders for a sale actually submitted to
  EFRIS. Now that it's the page header, gating it the same way would mean the very top of the
  receipt visibly shifts layout depending on EFRIS submission status — inconsistent with how the
  business logo/name above it already prints unconditionally. A terminal with these logos
  configured now prints them on every receipt, submitted to EFRIS or not; `loadEfrisLogos` itself
  is unchanged (still fetched once when the order loads, still never blocks printing on error).

  Frontend-only change: one file edited, `receipt.component.ts` (template reorganization only — no
  new fields, no new API calls). Both dev and production `ng build` pass cleanly. No backend files
  touched — the structural check stayed at 168 files.

- **Five access-control/discount requirements (2026-09-16)**: Fourier's numbered list. Investigated
  each before touching anything — two (Tasks 3 and 5) turned out to already be fully implemented
  by earlier work, so only Tasks 1, 2, and 4 involved code changes.

  **Task 1, "In the system setting configure the max discount"**: `StoreSettings` gained a new
  `maxDiscountPercent` column (`BigDecimal`, default 100 — effectively unrestricted until an admin
  narrows it, so an existing deployment that never visits the screen keeps today's behavior).
  Threaded through `SystemSettingsRequest` (`@DecimalMin`/`@DecimalMax` 0-100), `StoreSettingsDto`,
  `StoreSettingsService.updateSystemSettings`, and a new "Max discount (%)" field on Admin >
  Settings > System settings, same pattern as the existing currency/timezone/date-format fields.

  **Task 2, "Only Admin will be allowed to gives discount"**: `OrderService` gained a shared
  `validateDiscount(discountPercent, caller)` helper, called from both `updateLine` (the normal
  online add-then-discount flow) and `syncOfflineSale`'s own per-line loop (which previously had
  its own separate copy of just the 0-100 range check — replaced with the same shared call so the
  offline path can't bypass the new rules). The rule: a negative value is always rejected; raising
  a line's discount above zero is rejected for anyone who isn't `Role.ADMIN` (dropping it back to
  zero is always allowed for any cashier, since that only removes a discount, never grants one);
  and even an admin's discount is capped at the store's own configured
  `maxDiscountPercent` from Task 1 — the cap and the role check are independent rules, both
  enforced together. On the terminal, both discount inputs (online cart and offline cart) are now
  `[disabled]` for a non-admin cashier (visible, so an admin-applied discount is still shown, just
  not editable) and clamped client-side to the fetched max — real enforcement stays server-side in
  `validateDiscount`, so disabling the input is a UX courtesy, not the actual security boundary.

  **Task 3, "Only Admin can enable Customer Credit"** — already fully in place from earlier work:
  `Customer.creditEnabled` is only ever set via `CustomerController.create`/`update`, both
  `@PreAuthorize("hasRole('ADMIN')")`, and the Customer form itself lives behind `[authGuard,
  adminGuard]` in `app.routes.ts` — a cashier can't even reach the checkbox. No code changed for
  this task.

  **Task 4, "If the customer does not signed Credit then the credit is desabled on checkout"** —
  the *effect* already existed (checkout's `maxAmountFor` capped a non-credit-enabled customer's
  credit row to a 0 max amount), but the credit payment button itself was still clickable, which
  read as a dead end rather than a disabled option. `CheckoutComponent` gained a `creditDisabled(row)`
  method (true when there's no customer on the sale yet, or the selected customer's own
  `creditEnabled` is off) — the credit button now has `[disabled]="creditDisabled(row)"` plus an
  explanatory `title`, and `togglePaymentMethod` itself also short-circuits on a disabled row as a
  second guard. The three existing explanatory `<p>` messages under the payment rows are unchanged.

  **Task 5, "Only Admin is allowed to change the product price"** — already fully in place: the
  product create/edit form lives behind `[authGuard, adminGuard]`, and every `CatalogController`
  write (`POST`/`PUT`/`DELETE /api/products`) is `@PreAuthorize("hasRole('ADMIN')")`. There is no
  separate in-terminal price-override feature anywhere in this codebase to gate — the only way a
  sale line's effective price ever changes today is via the line discount (Tasks 1/2 above), and
  `unitPrice` is always a read-only snapshot of the catalog price at add-line time. No code changed
  for this task.

  Backend: five files edited — `StoreSettings.java`, `SystemSettingsRequest.java`,
  `StoreSettingsDto.java`, `StoreSettingsService.java`, `OrderService.java` (168 files total,
  static review clean, unchanged count — no new files). Frontend: `models.ts`,
  `store-settings-api.service.ts`, `system-settings.component.ts`, `terminal.component.ts`,
  `checkout.component.ts`. Both dev and production `ng build` pass cleanly.

- **"Switch cashier" hard to find, and a cashier can be stuck with no way to open the terminal
  (2026-09-16)**: Fourier's bug report — "Missing 'Switch cashier' in the terminal" and "The
  Cashier is not able to sale in terminal (reason no selling cart available for him)". Both traced
  to `terminal.component.ts`; no backend change needed, so the 168-file count/static check is
  unaffected.

  Investigated first rather than guessing: read through the whole cashier-session model
  (`CashierSessionService`, `CashierSessionController`, `OrderService`'s ownership checks,
  `session-list.component.ts`, `app.routes.ts`) and confirmed the backend join/assign/mine
  endpoints, the `/sessions` "Terminal" link, and `loadTabs`'s auto-create-a-tab-if-none-exist
  logic are all already correct — a cashier who successfully resolves a `cashierSession` always
  gets a cart. The actual gap was reaching that point at all:

  1. The "Switch cashier" button (lets someone else step up to the same physical terminal and
     re-identify by their own PIN) has always worked, but its visible label was only
     `🔁 {{ cashierSession.cashierName }}` — the *current* cashier's own name. The words "Switch
     cashier" existed only in the button's `title` tooltip, so the feature read as just a name
     badge, not an action — which is exactly Fourier's report. Now reads
     `🔁 Switch cashier (name)`, keeping the current cashier's name visible alongside the now-explicit action.
  2. The terminal's PIN-entry gate (`*ngIf="!cashierSession"`) is deliberately mandatory on every
     fresh open (Round 1 decision, see the class doc comment) — but a cashier with no PIN set yet
     (Admin > Staff shows this as a "Not set" badge) had no way forward from that screen at all: no
     cashier session, so no cart, so nothing to sell with, and no hint that they even *could* fix
     it themselves. Self-service PIN setup already exists (My Profile > Change PIN, from an
     earlier task), it just wasn't linked from the one screen where a cashier without a PIN
     actually gets stuck. Added a line under the PIN form pointing to Admin > Staff (ask an admin)
     or My Profile (set your own PIN) — no change to the PIN-required behavior itself, which stays
     intentional.

  Frontend only: `terminal.component.ts`. Both dev and production `ng build` pass cleanly; backend
  untouched (168 files, static review clean).

- **Three more from the same day (2026-09-16): "Switch cashier" left the switched-to cashier
  unable to sell; cashiers can now add/modify customers; cashiers now have a Customers menu.**
  Fourier's messages, verbatim: "Task 2: When Switch cashier, the user is not able to sale in
  terminal(reason no selling cart available for him)." / "Task 1: Cashier can add or modify
  customer" / "Task 3: Give Cashier Customer menu". Task 1 and Task 3 are really one change (open
  the same screens the same way), so they're written up together below.

  **Task 2 — the real bug behind the *previous* entry's Issue 2, found while chasing this one.**
  "Switch cashier" re-identifies who a sale belongs to by PIN alone, deliberately *without*
  logging the browser out of its own JWT (see `terminal.component.ts`'s class doc comment) — so
  the instant a cashier switches to a teammate's PIN, every order call afterward carries
  `cashierSessionId` = the teammate's session but the authenticated caller = whoever the browser
  was originally logged in as. `OrderService.requireOwnCashierSessionOrAdmin` (and the identical
  `CashierSessionService.requireOwnSessionOrAdmin`) only ever allowed that session's own cashier
  or an ADMIN — so unless the browser happened to be logged in as an admin, `listOpenOrders`,
  `startNewOrder`, and everything else scoped to that cashier session came back 403, the terminal
  never got a single tab, and the switched-to cashier saw no cart, no way to sell, and no
  indication why. Fixed both checks the same way: also allow a caller who has their *own* open
  cashier session on the *same shift* as the one being acted on — i.e. anyone already legitimately
  signed on to this shift can act on a teammate's session on that shift once switched to it, which
  is exactly what "Switch cashier" is for, while a cashier with no session anywhere on this shift
  is still refused (the original "guessing another session's id" protection is untouched). Two
  files: `OrderService.java`, `CashierSessionService.java` (168 files total, static review clean,
  no new files).

  **Task 1 / Task 3 — cashiers can now add and edit customers, and see the menu for it.**
  `CustomerController.create`/`update` were `@PreAuthorize("hasRole('ADMIN')")` — which meant the
  terminal's own inline "Add customer" panel (an earlier task) had been silently unusable for a
  cashier the whole time, always 403ing. Opened both endpoints to any logged-in user, but split
  `applyRequest` into `applyBasicFields` (name/phone/email/taxId — always applied) and
  `applyCreditFields` (creditEnabled/creditLimit/defaultCreditTermDays — only ever applied when the
  caller is ADMIN), so "Only Admin can enable Customer Credit" stays true: a non-admin creating a
  customer gets one with credit off by default, and a non-admin editing an existing customer can't
  touch its credit fields no matter what the request contains. On the frontend, `/admin/customers`,
  `/admin/customers/new`, and `/admin/customers/:id/edit` dropped their `adminGuard` (list/new/edit
  are now `authGuard` only); `/admin/customers/statement` and the per-customer `record-payment`
  route stayed ADMIN-only, since those are financial reporting, not "add or modify a customer."
  `CustomerFormComponent`'s "Store credit" section is now `[disabled]="!isAdmin"` on all three
  inputs rather than gone — same visible-but-disabled pattern as the terminal's discount inputs
  from an earlier pass, so a cashier editing a customer can still see their existing credit status,
  just can't change it (the real boundary is server-side either way). `CustomerListComponent`'s
  "Record payment" link is now also `*ngIf="isAdmin"`. In the sidebar, the "Customers" group moved
  out of the ADMIN-only `ng-container` it shared with Inventory/Users/Settings and into its own
  block open to any logged-in user, with only its "Statement" sublink still gated inline to ADMIN.
  Backend: `CustomerController.java` (168 files, static review clean, no new files). Frontend:
  `app.routes.ts`, `shell.component.ts`, `customer-list.component.ts`, `customer-form.component.ts`.

  Both `ng build --configuration development` and the production `ng build` pass cleanly for all
  three tasks together.

- **Returns now integrate with EFRIS too.** Fourier's own spec: "The return will also integrate
  with EFRIS, the only change are: 1. instead of sending 'transaction_type':'S' send
  'transaction_type':'R' 2. instead of sending 'erp_code_ref':'0' send 'erp_code_ref': value of
  erp_code of the original receipt." Until now `EfrisSubmissionService` hardcoded
  `transaction_type` to the literal `"S"` and nothing on the `returns` side called EFRIS at all —
  a processed return just stayed a plain refund record with no fiscal submission, no matter how
  EFRIS was configured.

  `EfrisInvoicePayload` gained a `transactionType` field (was previously absent entirely, since
  every submission used to be a normal sale); `EfrisSubmissionService` now sends
  `payload.transactionType()` instead of the old hardcoded `"S"`, and `OrderService.buildEfrisPayload`
  passes `"S"` explicitly to keep a sale's own behavior identical. `SalesReturn` gained the same
  eight nullable EFRIS fiscal columns `SaleOrder` already had (`sdcId`/`sdcDate`/
  `sdcReceiptNumber`/`internalData`/`signatureData`/`cisNumber`/`cisDate`/`mrcData`) plus
  `efrisSubmittedAt`/`efrisError`/`efrisResponse`, and a new `returnNumber` column — a return needs
  its own `erp_code` distinct from the sale it's reversing (assigned the same `"POS-" +
  currentTimeMillis()` shape as `SaleOrder.invoiceNumber`, right at `processReturn` time, since a
  return has no draft/open state to defer it to). `ReturnsService` gained
  `submitToEfrisIfApplicable`/`buildEfrisPayload`, an exact mirror of `OrderService`'s own methods
  of the same name (same guards — already fiscalized, EFRIS not enabled, no `EfrisSetting` for this
  terminal — same "return still completes, flagged for review" policy on failure) with Fourier's
  two specified deltas: `transactionType` is `"R"`, and `erpCodeRef` is the *original* sale's own
  `erpCode` (its `invoiceNumber`) rather than `"0"`. The terminal used for a return's own EFRIS
  connection is the *original* sale's terminal (`salesReturn.getOrder().getSession().getRegister()`)
  since a return carries no register/session of its own. `OrderService.resolvePaymentMode`/
  `resolveProductCode` were changed from `private` to `public` so `ReturnsService` could reuse them
  rather than duplicating that logic a second time — a return reports the *original* sale's payment
  mode, since nothing new was tendered at return time. `ReturnsController.processReturn` now calls
  `submitToEfrisIfApplicable` right after creating the return, exactly like `OrderController.checkout`
  does for a sale, and a new `POST /api/returns/{id}/efris/retry` mirrors the sale's own
  `/orders/{id}/efris/retry` "Retry EFRIS submission" endpoint.

  `SalesReturnDto` gained the same fields (plus `registerId`, so the return receipt can fetch that
  terminal's EFRIS logos) and `return-receipt.component.ts` picked up the exact same EFRIS
  treatment `checkout/receipt.component.ts` already had: the URA/EFRIS logo letterhead flanking the
  "Return Receipt" title, the `efrisError` failure banner with its own "Retry EFRIS submission"
  button, the SDC/CIS INFORMATION block, and the same `sdc_date#sdc_id#sdc_receipt_number#+internal_data#+signature_data`
  QR code — all reading off `SalesReturn`'s new fields instead of `Order`'s, otherwise byte-for-byte
  the same layout and logic.

  Backend: `EfrisInvoicePayload.java`, `EfrisSubmissionService.java`, `OrderService.java`,
  `SalesReturn.java`, `ReturnsService.java`, `ReturnsController.java`, `SalesReturnDto.java` (168
  files total, static review clean, no new files — every change landed in an existing file).
  Frontend: `core/models.ts`, `core/api/returns-api.service.ts`, `return-receipt.component.ts`.
  Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **The Z-report is now printable, the same way a receipt is.** Fourier's own request: "Add print
  session report (Z-report) same we print receipt. Not PDF actually." `SessionReportComponent`
  (`/sessions/:id/report`) used to be an ordinary screen inside `ShellComponent` — the app's normal
  dark-themed sidebar chrome — with no print treatment at all: no print button, no print-specific
  CSS, nothing hidden on print. Rebuilt as a direct copy of `checkout/receipt.component.ts`'s own
  printable-page shape: no shell, the same light `.receipt`-style monospace layout, the same
  business letterhead (logo/name/address/phone/email/tax ID, from `StoreSettingsApiService`, same
  graceful "just don't show it" degradation if nothing's configured), a `no-print` **Print** button
  that calls `window.print()`, and `@media print { .no-print { display: none !important; } }` to
  hide the Print/Back buttons on the actual printout — same CSS block, copied as-is. Deliberately
  *not* a PDF, per Fourier's own words: no export step, no `jsPDF`/similar dependency, just the
  browser's own print dialog, exactly like every receipt in this app already works.

  `SessionCloseComponent.submit()` now navigates to the report with `?autoPrint=1` after closing a
  shift, the same auto-print-then-the-browser's-own-dialog convention checkout and return
  processing already use — the print dialog opens automatically the moment the Z-report loads,
  and dismissing it (printed or cancelled) sends the cashier back to `/sessions` via the identical
  `afterprint` listener pattern `receipt.component.ts`/`return-receipt.component.ts` use. A Z-report
  reopened later from the "Report" link on a closed session's `/sessions` row has no `autoPrint`
  flag and behaves exactly as before this pass: the report just loads, Print is a manual click.

  Frontend only, two files: `session-report.component.ts` (effectively rewritten),
  `session-close.component.ts` (one line — the added query param). No backend change; 168 files,
  static review clean. Both `ng build --configuration development` and the production `ng build`
  pass cleanly.

- **2026-09-16 — Negative sign on return receipt amounts.** Task: "add also negative sign (-) on
  the amount on return receipt printoud." The return receipt (`return-receipt.component.ts`)
  printed refund amounts as plain positive-formatted numbers (e.g. `15,000.00`), with nothing on
  the printout itself distinguishing a refund from a sale amount. Added a leading minus sign to
  both places a refund amount is shown: the per-line `refundAmount` in the line-items table (now
  `−15,000.00` per line, still `—` when the line has no recorded refund amount) and the
  `refundTotal` in the totals table. Same minus-sign glyph (U+2212, not a hyphen) `session-close
  .component.ts` and `session-report.component.ts` already use for their own "Cash refunds" lines,
  so a refund reads as a negative amount consistently everywhere this app prints one.

  Frontend only, one file: `return-receipt.component.ts` (two template expressions changed). No
  backend change; 168 files, static review unaffected. Both `ng build --configuration development`
  and the production `ng build` pass cleanly.

- **2026-09-16 — Z-report shows a per-cashier breakdown, with start/end times.** Two requests taken
  together: "1. The session report should show start and end time. 2. The report for
  register_session (Shift) should show all cashier_session (session report)." The Z-report
  (`session-report.component.ts`) already showed the *shift*'s own opened/closed timestamps, but
  had nothing about the individual cashiers who actually worked it — relevant because a shift can
  have more than one cashier session inside it (the "Switch cashier" workflow, or an admin's
  "assign", lets several people ring sales on the same till during the same shift; see
  `CashierSession`'s own class javadoc). Added a "By cashier" section to the report listing every
  `CashierSession` that belongs to the shift, oldest first, each with its own start time, end time
  (or `—` while still open), status, and its own slice of the shift's sales (sale count, gross
  sales) — this is what answers both asks at once: per-cashier start/end times, and the full list
  of cashier sessions on the shift.

  Backend: `OrderService.computeCashierSessionTotals(cashierSessionId)` (new — same `aggregate()`
  helper `computeSessionTotals` already uses, just scoped to one cashier session's own orders via
  the existing `findByCashierSessionIdAndStatusOrderByCreatedAtAsc` repository method instead of
  the whole shift's). `CashierSessionService.listEntitiesForShift(registerSessionId)` (new — same
  query `listForShift` already runs, returning entities instead of DTOs since
  `RegisterService.sessionReport` needs each session's id to also pull its totals). New DTO
  `CashierSessionReportDto` (cashierSessionId, cashierId, cashierName, startedAt, endedAt, status,
  saleCount, grossSales, taxCollected, cashIn, nonCashIn). `SessionReportDto` gained a
  `cashierSessions: List<CashierSessionReportDto>` field, populated by `RegisterService
  .sessionReport`. No new endpoint — this rides the existing `GET /api/sessions/{id}/report` any
  logged-in user can already call for their own shift's Z-report.

  Frontend: `core/models.ts` gained a `CashierSessionReport` interface and `SessionReport
  .cashierSessions`; `session-report.component.ts` renders the new section (only when the list is
  non-empty) between the sales-data table and the cash-reconciliation table, one compact block per
  cashier — name and status on one line, start → end time on the next, sale count and gross sales
  on the last — styled consistently with the rest of the receipt (same font, same muted-gray
  secondary text this component already uses elsewhere).

  Five files (four backend, one frontend), all existing plus one new DTO; 169 files, static review
  clean. Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Split the CashierSession report out from the RegisterSession (shift) Z-report.**
  Task: "We need to differentiate CashierSession Z Report with RegisterSession Z Report." The plain
  "Sessions" screen (`session-list.component.ts`) lists individual `CashierSession`s — one row per
  cashier's own stint on a till — but its "Report" link pointed at `session-report.component.ts`,
  the whole *shift*'s (`RegisterSession`'s) Z-report: every cashier who worked that till, the
  shift's cash-drawer reconciliation, the lot. A cashier clicking "Report" on their own row saw the
  entire shift's numbers, not just their own. Fixed by giving `CashierSession` its own, smaller
  report, separate from the shift-level one.

  Backend: new `GET /api/cashier-sessions/{id}/report`, `CashierSessionService.report(id, caller)`
  (same own-session-or-admin-or-shares-this-shift gate `getById`/`close` already use), and a new DTO
  `CashierSessionZReportDto` (cashierSessionId, registerSessionId, registerId, registerName,
  shiftStatus, cashierId, cashierName, startedAt, endedAt, status, saleCount, grossSales,
  taxCollected, cashIn, nonCashIn) built from `OrderService.computeCashierSessionTotals` — the same
  helper the shift report's own "By cashier" breakdown (added just above) already uses. Deliberately
  excludes cash-drawer reconciliation and refunds, same reasoning as that breakdown's rows: both are
  shift-wide, physical-till concepts, and `SalesReturn` has no cashier-session reference to
  attribute a refund to one cashier's stint even if it wanted to.

  Frontend: new `cashier-session-report.component.ts` at `/cashier-sessions/:id/report` — same
  printable `.receipt` shape (light/monospace, business letterhead, `no-print` Print button) every
  other receipt-like screen in this app uses, titled **"Cashier Session Report"** (not "Z-Report")
  so the two are never mistaken for each other on the printed page itself. Shows register name,
  shift status, this cashier's own start/end time and status, then sale count/gross sales/tax
  collected/cash in/non-cash in — no cash reconciliation, no refunds. `session-list.component.ts`'s
  "Report" link now points here instead of the shift report. `session-report.component.ts` (the
  shift-level Z-report — reached from the admin "Shifts" screen, and auto-printed on closing a
  shift) is otherwise unchanged; its own "By cashier" section still gives a quick per-cashier
  overview inline, but a cashier's own definitive numbers now live on this separate report.
  `core/models.ts` gained `CashierSessionZReport`; `cashier-session-api.service.ts` gained
  `getReport()`; new route registered in `app.routes.ts`.

  Nine files touched in all — three backend (`CashierSessionZReportDto.java` new,
  `CashierSessionService.java`, `CashierSessionQueryController.java`) and six frontend
  (`cashier-session-report.component.ts` new, `core/models.ts`, `cashier-session-api.service.ts`,
  `app.routes.ts`, `session-list.component.ts`, plus a doc-comment-only touch on
  `session-report.component.ts`). Backend: 170 files, static review clean (up from 169 — the one
  new file is `CashierSessionZReportDto.java`, expected). Both `ng build --configuration
  development` and the production `ng build` pass cleanly.

- **2026-09-16 — Z-report's back link now says "Back to Shifts".** Task: "On Z-Report Change Back
  to Session link int Back to Shifts." A leftover from before the CashierSession/RegisterSession
  report split just above: the shift-level Z-report's (`session-report.component.ts`) footer link
  still read "Back to sessions" and pointed at `/sessions` — the plain "Sessions" screen (one row
  per `CashierSession`). That's no longer where this report is reached from; it's the admin
  "Shifts" screen (`shift-list.component.ts`, `/admin/shifts`, one row per `RegisterSession`) and
  the auto-print-after-closing-a-shift flow, both admin-only. Changed the link's label to "Back to
  Shifts" and its target to `/admin/shifts`, and updated the same auto-print `afterPrintHandler`
  (fires when the print dialog is dismissed after `?autoPrint=1`) to navigate there too, so both the
  manual click and the auto-print flow land in the same place now.

  Frontend only, one file: `session-report.component.ts` (link label/target, `afterPrintHandler`).
  No backend change; 170 files, static review unaffected. Both `ng build --configuration
  development` and the production `ng build` pass cleanly.

- **2026-09-16 — Removed the unused generic app key/value settings table, frontend and backend.**
  Fourier asked what the Admin > Apps > Configure screen's "there are no other fixed configuration
  fields yet — add whatever other key/value settings you already know below" warning was for; once
  it was clear nothing reads those rows back (EFRIS, the only app in the catalog, got its own
  structured per-terminal settings instead, and no other app exists to use the generic table), he
  asked to remove it — "Frontend + backend, fully."

  Backend: deleted `InstalledAppSetting.java` (the entity), `InstalledAppSettingRepository.java`,
  `AppSettingDto.java`, and `UpdateAppSettingsRequest.java` outright. `InstalledApp.java` lost its
  `@OneToMany List<InstalledAppSetting> settings` field (and the now-unused `CascadeType`/
  `OneToMany`/`ArrayList` imports). `AppsService.java` lost the `InstalledAppSettingRepository`
  dependency and its `getSettings`/`saveSettings` methods; `uninstall`'s own doc comment was
  trimmed to match (it no longer has settings to cascade-delete). `AppsController.java` lost the
  `GET`/`PUT /api/apps/{key}/settings` endpoints. Doc-comment-only touches on `AppCatalog.java`,
  `EfrisSetting.java`, and `EfrisSettingService.java` to drop their now-dangling references to the
  deleted class. No migration needed — this app has no SQL migration files at all
  (`spring.jpa.hibernate.ddl-auto=update` manages the schema from the entities directly), so the
  `installed_app_setting` table simply stops being touched by a fresh schema going forward.

  Frontend: removed the generic key/value `<table>`, its "+ Add field"/"Save" buttons, and the
  warning `<div class="alert alert-warning">` above it from `app-settings.component.ts`, along with
  the `rows`/`saving` fields, the `SettingRow` interface, `addRow`/`removeRow`/`save()`, and the
  `getSettings` call in `ngOnInit` — the EFRIS per-terminal table (and its own independent per-row
  Save) is untouched, it's a separate block on the same screen. `AppsApiService` lost
  `getSettings`/`saveSettings`; `core/models.ts` lost the `AppSetting` interface. Confirmed nothing
  else in the frontend referenced any of these before removing them.

  Thirteen files touched: ten backend (four deleted — `InstalledAppSetting.java`,
  `InstalledAppSettingRepository.java`, `AppSettingDto.java`, `UpdateAppSettingsRequest.java`;
  three substantively edited — `InstalledApp.java`, `AppsService.java`, `AppsController.java`;
  three doc-comment-only — `AppCatalog.java`, `EfrisSetting.java`, `EfrisSettingService.java`) and
  three frontend (`app-settings.component.ts`, `apps-api.service.ts`, `core/models.ts`). 166 Java
  files, static review clean (down from 170 — the four deletions, as expected). Both
  `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Apps catalog entry renamed to EFRIS Rwanda e-Invoicing.** Task 1: "In Apps Change
  EFRIS Uganda e-Invoicing Uganda Revenue Authority (URA) to EFRIS Rwanda e-Invoicing Rwanda
  Revenue Authority (RRA)." Scoped to exactly what "In Apps" names — the display name and
  description shown on the Admin > Settings > Apps catalog screen (`AppCatalog.java`'s one
  `Definition`): `"EFRIS Uganda e-Invoicing"` → `"EFRIS Rwanda e-Invoicing"`, and its description's
  `"Uganda Revenue Authority (URA)"` / `"URA's exact requirements"` → `"Rwanda Revenue Authority
  (RRA)"` / `"RRA's exact requirements"`. Also fixed one now-stale doc comment that quoted the old
  display name as an example (`app-list.component.ts`).

  Deliberately left everything else Uganda/URA-named alone for this pass: the `AppKey.EFRIS_UG`
  enum constant (an internal identifier, not shown in the Apps UI — renaming it would ripple into
  persisted `installed_app` rows, `AppsController.parseKey`, and every `key === 'EFRIS_UG'` check
  in the frontend), the per-terminal "URA Logo" column/`uraLogoDataUrl` field on the EFRIS settings
  table and receipts (`EfrisSetting.java`, `app-settings.component.ts`, `receipt.component.ts`/
  `return-receipt.component.ts`'s letterhead), and every historical dated README entry that
  accurately described this integration as Uganda-focused at the time it was written — those stay
  as the historical record. If those also need to become Rwanda-facing, that reads as its own
  follow-up task.

  Two files: `AppCatalog.java` (backend) and a doc-comment-only touch on `app-list.component.ts`
  (frontend). 166 Java files, static review unaffected (no files added/removed). Both
  `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Finished the Uganda → Rwanda rename everywhere: the `EFRIS_UG` enum, the "URA
  Logo" field, and every remaining prose mention.** Follow-up to Task 1 above — "yes do it for
  all." This is the deeper pass the previous entry deliberately deferred.

  **`AppKey.EFRIS_UG` → `AppKey.EFRIS_RW`** (`AppKey.java`), updated everywhere it's referenced:
  `AppCatalog.java`'s catalog entry, `OrderService`/`ReturnsService`'s `appsService.isEnabled(...)`
  checks, and every frontend `key === 'EFRIS_UG'` comparison (`app-settings.component.ts` x2,
  `product-form.component.ts`'s EFRIS-installed check). ⚠️ **This constant is persisted** —
  `InstalledApp.appKey` is `@Enumerated(EnumType.STRING)`, so any database that already has an
  `EFRIS_UG` row (i.e. EFRIS was installed/enabled before this update) will show the app as **not
  installed** after upgrading, since `AppKey.valueOf("EFRIS_UG")` no longer matches anything. Fix
  with one statement before or after deploying the new build: `UPDATE installed_app SET app_key =
  'EFRIS_RW' WHERE app_key = 'EFRIS_UG';` (verify the actual table/column names for your database
  first — Hibernate's default naming strategy gives `installed_app`/`app_key`, but confirm with
  `\d installed_app` in psql if unsure).

  **`uraLogoDataUrl`/`ura_logo_data_url` → `rraLogoDataUrl`/`rra_logo_data_url`** — the per-terminal
  "URA Logo" field, renamed "RRA Logo" everywhere it's shown. Backend: `EfrisSetting.java` (entity
  field + column), `UpdateEfrisSettingRequest.java`, `EfrisTerminalDto.java`, `EfrisLogosDto.java`,
  `EfrisSettingService.java`'s mapping code. Frontend: `core/models.ts` (`EfrisTerminal`/
  `EfrisLogos`), `efris-api.service.ts`, `app-settings.component.ts` (column header, image/file
  input, `onLogoSelected`'s field-name type, `saveTerminalSettings`'s payload), and the letterhead
  in both `checkout/receipt.component.ts` and `returns/return-receipt.component.ts` (`alt="URA
  logo"` → `alt="RRA logo"`, plus the layout comment "URA on the left and EFRIS on the right" →
  "RRA on the left"). ⚠️ **This column is also persisted** — `ddl-auto=update` adds the new
  `rra_logo_data_url` column alongside the old `ura_logo_data_url` one rather than renaming it in
  place, so any logo already uploaded under the old name needs a one-time carry-over: `UPDATE
  efris_setting SET rra_logo_data_url = ura_logo_data_url WHERE ura_logo_data_url IS NOT NULL;`
  (the old column is harmless left in place, or drop it afterward with `ALTER TABLE efris_setting
  DROP COLUMN ura_logo_data_url;` if you'd rather clean it up).

  **Remaining prose-only mentions** of "Uganda"/"URA" updated to "Rwanda"/"RRA" for consistency,
  now that nothing in the live app is Uganda-specific anymore: the `"B"` default tax code comments
  in `OrderService`/`ReturnsService`/`EfrisInvoiceLine.java` ("URA/RRA's standard-rate category
  letter" → "RRA's..."), the "EFRIS (Uganda URA e-invoicing) fiscal receipt data/fields" doc
  comments on `SaleOrder.java`, `SalesReturn.java`, `OrderDto.java`, and their frontend mirrors in
  `core/models.ts`, and `app.routes.ts`'s catalog comment.

  **Deliberately left alone**, same reasoning as the previous entry: every direct quote of
  Fourier's own original task wording ("add two logo (URA LOGO, EFRIS LOGO)...", still in quotation
  marks in several doc comments — rewriting a historical quote would misrepresent what was actually
  asked at the time), every already-shipped dated README entry describing past Uganda-era work, and
  the `UGX`/"Ugandan Shilling" entry in `system-settings.component.ts`'s currency picker — that's
  one option in a general East African currency list (which already also offers `RWF`/"Rwandan
  Franc"), not something tied to EFRIS or this rename.

  Twenty files touched: thirteen backend (`AppKey.java`, `AppCatalog.java`, `OrderService.java`,
  `ReturnsService.java`, `EfrisInvoiceLine.java`, `SaleOrder.java`, `SalesReturn.java`,
  `OrderDto.java`, `EfrisSetting.java`, `UpdateEfrisSettingRequest.java`, `EfrisTerminalDto.java`,
  `EfrisLogosDto.java`, `EfrisSettingService.java`) and seven frontend (`core/models.ts`,
  `efris-api.service.ts`, `return-receipt.component.ts`, `checkout/receipt.component.ts`,
  `app-settings.component.ts`, `product-form.component.ts`, `app.routes.ts`). 166 Java files,
  static review clean (no files added/removed — every change was an edit to an existing file).
  Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Added a new app to the catalog: EFRIS Chad e-Invoicing (`AppKey.EFRIS_TD`).**
  Task: "Now I want to add new app (EFRIS Chad) with will need the following settings: url,
  basic_auth_username, basic_password, sdc_number, branch_code, mrc, integration_key. The
  integration with EFRIS Chad is via HTTP(S) with basic authentication," plus sample request/
  response JSON for a future submission call. Fourier's own words: "I will share the resulting
  parsing later" — so, deliberately, this pass is install/enable/settings-storage only, the same
  scope EFRIS Rwanda itself started with before its own submission logic was ever discussed. **No
  HTTP request is sent to EFRIS Chad anywhere in this codebase yet** — nothing reads the new
  settings back except the admin screen that saves them.

  **New package `com.posapp.apps.efrischad`**, six new files, deliberately a wholly separate
  entity/table from `EfrisSetting` (Rwanda's) rather than an extension of it — the two
  integrations' auth models don't overlap (Rwanda: JSON-RPC `db`/`uid`/`password`; Chad: HTTP
  Basic Auth plus `sdc_number`/`branch_code`/`mrc`/`integration_key`), so sharing one shape would
  mean a pile of always-null fields per country. `EfrisChadSetting.java` (entity, table
  `efris_chad_setting`, one row per `Register` same as `EfrisSetting`), `EfrisChadSettingRepository
  .java`, `EfrisChadTerminalDto.java`, `UpdateEfrisChadSettingRequest.java`,
  `EfrisChadSettingService.java`, `EfrisChadSettingController.java` (`/api/apps/efris-chad/
  terminals`, ADMIN-only, mirrors `EfrisSettingController` but with no `/logos` endpoint — Chad's
  field list didn't include any letterhead logos this time).

  **`AppKey.java`**: added `EFRIS_TD`. **`AppCatalog.java`**: added its catalog entry — the
  description sticks to the technical/protocol details Fourier actually gave (HTTP(S) + basic
  auth + SDC number/branch code/MRC/integration key) and deliberately does **not** invent an
  official Chad tax-authority name/acronym the way "Rwanda Revenue Authority (RRA)" was named for
  EFRIS Rwanda, since Fourier didn't provide one for Chad.

  **Frontend**: `core/models.ts` — new `EfrisChadTerminal` interface (`registerId`,
  `registerName`, `location`, `url`, `basicAuthUsername`, `basicPassword`, `sdcNumber`,
  `branchCode`, `mrc`, `integrationKey`). New `efris-chad-api.service.ts` (`listTerminals`/
  `saveTerminalSettings`, mirrors `efris-api.service.ts`). `app-settings.component.ts` — new
  `*ngIf="key === 'EFRIS_TD'"` per-terminal settings table (same shape as the EFRIS Rwanda table
  above it: Terminal, Location, URL, Basic Auth Username, Basic Password, SDC Number, Branch Code,
  MRC, Integration Key, per-row Save), plus the matching component-class fields/methods
  (`efrisChadTerminals`, `chadTerminalsError`, `savingChadTerminalId`, `loadChadTerminals()`,
  `saveChadTerminalSettings()`), wired into `ngOnInit` the same way as the EFRIS Rwanda block.

  Seven files added (six backend + `efris-chad-api.service.ts`), four edited (`AppKey.java`,
  `AppCatalog.java`, `core/models.ts`, `app-settings.component.ts`). 172 Java files, static review
  clean. Both `ng build --configuration development` and the production `ng build` pass cleanly.

  **Deliberately not built yet, per Fourier's own "I will share the resulting parsing later"**:
  the actual HTTP(S) submission call to EFRIS Chad, request payload construction from an order/
  return (matching the sample `details_request` shape), and response parsing (`response_code ==
  "P"` for success; parsing `response_data.sdc_data`/`total_amounts`/`tax_amounts`/`rate_amounts`,
  which arrive as comma-separated strings, not structured objects, per the sample response). This
  is a deliberate follow-up once Fourier shares that mapping — same pattern as EFRIS Rwanda's own
  `EfrisSubmissionService`, added well after its settings screen first shipped.

- **2026-09-16 — Three related rules around EFRIS install/mapping/receipts.** Task: "1. The app
  installed and used can not be unstalled. 2. One Terminal can have only one EFRIS mapped with.
  2. [sic] The printout of receipt will always use the used app settings. If it used RWANDA or
  CHAD."

  **1. An app with terminal settings saved can't be uninstalled.** `AppsService.uninstall` now
  refuses (400) when at least one terminal has settings saved for that app — a direct
  `efrisSettingRepository.count() > 0` / `efrisChadSettingRepository.count() > 0` check, not a
  plugin-style usage-checker interface (this codebase has exactly two apps; see the javadoc on
  `AppsService.uninstall` for why a generic abstraction for two call sites would be the kind of
  speculative structure this session has otherwise avoided). "Used" here means *has terminal
  settings saved*, not *currently enabled* — a disabled app with settings still on file is still
  blocked, by design (Fourier's own choice when asked). The admin has to clear every terminal's
  settings for that app first (see point 2's new Clear action) before uninstalling becomes
  possible.

  **2. A terminal can only be mapped to one EFRIS integration at a time.** `EfrisSettingService
  .saveTerminalSettings` (Rwanda) now refuses to newly map a register that already has an
  `EfrisChadSetting` row, and `EfrisChadSettingService.saveTerminalSettings` (Chad) refuses the
  same the other way — each service takes a direct dependency on the other's repository (a
  two-app rule, not a general framework concern; see each service's own javadoc). Re-saving a
  terminal's *own* already-mapped app is always fine; only *newly* mapping the *other* app while a
  row already exists is blocked. New `DELETE /api/apps/efris/terminals/{id}` and `DELETE
  /api/apps/efris-chad/terminals/{id}` endpoints (`clearTerminalSettings`, idempotent) are how an
  admin unmaps a terminal — to switch it to the other integration, or just to stop using EFRIS on
  that till (also what unblocks point 1's uninstall check once every terminal is cleared).
  Frontend: `app-settings.component.ts` gets a "Clear" button next to "Save" in both terminal
  tables (with a confirm prompt, since it's destructive), and `efris-api.service.ts`/
  `efris-chad-api.service.ts` each get a matching `clearTerminalSettings` call.

  **3. Receipts already use "the used app's" settings — verified, no code changes needed.** Once
  point 2 guarantees at most one EFRIS mapping per terminal, the existing receipt code already
  does the right thing without any changes: `EfrisSettingService.getLogosForRegister` (backend)
  already returns `EfrisLogosDto.EMPTY` — never an error — for a terminal with no `EfrisSetting`
  row, so a terminal mapped to Chad instead of Rwanda now correctly shows no RRA/EFRIS logos on
  its receipts (`checkout/receipt.component.ts` and `returns/return-receipt.component.ts` both
  already degrade gracefully on a null/empty `efrisLogos`). Likewise, the "SDC INFORMATION" fiscal
  block on both receipts is already gated on `o.sdcId`/`r.sdcId` — a field only Rwanda's (not yet
  built) submission logic would ever populate — so it already never renders for a Chad-processed
  sale. EFRIS Chad has no logo fields at all (per its own settings screen), so there's currently
  nothing Chad-specific for a receipt to show either way; this becomes relevant again once Chad's
  own submission/response parsing is built in a future pass.

  Backend: five files edited (`AppsService.java`, `EfrisSettingService.java`,
  `EfrisSettingController.java`, `EfrisChadSettingService.java`,
  `EfrisChadSettingController.java`), no files added. Frontend: three files edited
  (`app-settings.component.ts`, `efris-api.service.ts`, `efris-chad-api.service.ts`). 172 Java
  files, static review clean. Both `ng build --configuration development` and the production
  `ng build` pass cleanly.

- **2026-09-16 — EFRIS Chad's actual submission call, "as we did for EFRIS Rwanda."** Task: "As we
  did for EFRIS Rwanda, if EFRIS Chad is set then call configured with the shared sample request."
  This is the follow-up to "I will share the resulting parsing later" — a completed sale is now
  actually submitted to EFRIS Chad, over HTTP(S) with Basic Authentication, using Fourier's own
  sample request/response shape from that earlier message. Sale-only for this pass (no return
  submission yet — see below).

  Three genuine ambiguities were cleared up with Fourier before building this, since guessing wrong
  on a live fiscal-compliance call is expensive to get wrong:
  - **payment_mode**: Chad's sample shows `"PAYMENT_METHOD_CASH"`, but this app's payment methods
    are freely admin-configurable strings with no known Chad equivalents beyond that one example.
    Fourier's choice: derive it as `"PAYMENT_METHOD_" + code.toUpperCase()` for each distinct
    `Payment.method`, comma-joined for a split-tender sale (same joining convention Rwanda's own
    `payment_mode` already uses).
  - **item_nature (goods vs. service)**: nothing in this app distinguished goods from services
    before Chad needed one for its `item_nature` field. Fourier's choice: add a real per-product
    field rather than a blanket default — see below.
  - **The sample's numeric `"invoice_number": 38`**: Fourier's instruction was to drop this field
    from the request entirely rather than build a per-terminal sequence-number counter for it, so
    it's not sent at all. (Distinct from `external_reference`, which *is* sent — see below.)

  **New `Product.service` (boolean, default `false` = goods)**, snapshotted onto
  `OrderLine.service` at add-line time, same convention as every other per-line snapshot
  (`taxCode`, `unspCode`, `unitCost`). Feeds Chad's `item_nature`
  (`ITEM_NATURE_GOODS`/`ITEM_NATURE_SERVICE`); `item_type` itself is always the sample's own
  `"ITEM_TYPE_FINISHED_PRODUCT"` literal, since the one sample line doesn't show that varying by
  goods vs. service. Product form: a new "This is a service, not a physical good" checkbox, shown
  once EFRIS Chad is installed (mirrors the existing "EFRIS commodity code" field's install-gated
  visibility, which now also mentions it doubles as Chad's `classification_code`). Files: backend
  `Product.java`, `ProductDto.java`, `ProductRequest.java`, `CatalogService.java`, `OrderLine.java`,
  `OrderService.java` (`buildLine`); frontend `models.ts`, `catalog-api.service.ts`,
  `product-form.component.ts`.

  **New package pieces mirroring EFRIS Rwanda's own submission plumbing:**
  `com.posapp.apps.efrischad.dto.EfrisChadConnection` (url/basicAuthUsername/basicPassword/
  sdcNumber/branchCode/mrc/integrationKey — mirrors `EfrisConnection`), `EfrisChadInvoiceLine` and
  `EfrisChadInvoicePayload` (mirror `EfrisInvoiceLine`/`EfrisInvoicePayload`, Chad's own field set),
  `EfrisChadSubmissionResult` (mirrors `EfrisSubmissionResult`, but see below for why it doesn't
  parse fiscal fields yet), and `EfrisChadSubmissionService` — builds the exact JSON shape from
  Fourier's sample (`registered_name`/`tin_number` from `StoreSettings`, `sdc_number`/
  `branch_code`/`mrc`/`integration_key` from that terminal's `EfrisChadSetting`,
  `classification_code` reusing `OrderLine.unspCode` — the same UNSPSC-style code Rwanda's own
  `unsp_code` already uses, `tax_code` reusing `OrderLine.taxCode` with the sample's own `"TN"`
  fallback (mirrors `OrderService.DEFAULT_EFRIS_TAX_CODE`'s `"B"` fallback for Rwanda),
  `external_reference` reusing `SaleOrder.invoiceNumber` unchanged since it's already generated as
  `"POS-" + System.currentTimeMillis()` — the exact shape the sample's own
  `"POS-1789562530534"` is), sends it as a plain HTTP(S) POST with an `Authorization: Basic ...`
  header (credentials never logged, matching Rwanda's own password-redaction discipline for its
  JSON-RPC body), and treats `response_code == "P"` as success, exactly Fourier's own rule ("When
  response_code is 'P' means SUCCESS").

  **`OrderService.submitToEfrisIfApplicable` now dispatches**: tries EFRIS Rwanda first (if enabled
  and this order's terminal has a Rwanda connection configured), then EFRIS Chad (same check, Chad
  connection). "One Terminal can have only one EFRIS mapped with" (enforced earlier) guarantees at
  most one branch ever actually has a connection to try, so this is a dispatch, not a real race —
  whichever integration a terminal is mapped to is the one that runs a given sale through. The
  existing `/api/orders/{id}/efris/retry` endpoint and receipt "Retry EFRIS submission" button work
  unchanged for Chad too, since both just re-call this same method.

  **Deliberately doesn't parse EFRIS Chad's fiscal response fields yet.** Rwanda's own
  `EfrisSubmissionResult` only got its exact `sdc_data`/`cis_data` positional split ("[0] sdcId,
  [1..3] sdcReceiptNumber...") after its submission call was already live, once Fourier gave that
  spec explicitly. No equivalent spec has been given for Chad's `response_data.sdc_data`/
  `total_amounts`/`tax_amounts`/`rate_amounts` comma-separated strings — guessing at what each
  position means would risk mislabeling real fiscal data. So this pass parses only what was
  actually specified (`response_code == "P"` for success) and stores the raw response body; a
  Chad-processed order's `efrisSubmittedAt`/`efrisResponse`/`efrisError` are set the same as
  Rwanda's, but `SaleOrder`'s Rwanda-specific fiscal columns (`sdcId`, `sdcDate`, ...) stay
  untouched for a Chad sale. Receipts already degrade correctly as a result (see the 2026-09-16
  "receipts already use the used app's settings" entry above) — no "Chad SDC INFORMATION" block
  exists yet, since there's no field-by-field spec to build one from. That parsing, and any Chad
  return submission (no return `transaction_type` has been given yet — the sample's own
  `"transaction_type": "V"` is the only value sent, always, for now), are the natural next
  follow-ups.

  Five new backend files (`EfrisChadConnection.java`, `EfrisChadInvoiceLine.java`,
  `EfrisChadInvoicePayload.java`, `EfrisChadSubmissionResult.java`,
  `EfrisChadSubmissionService.java`), seven backend files edited (`Product.java`,
  `ProductDto.java`, `ProductRequest.java`, `CatalogService.java`, `OrderLine.java`,
  `OrderService.java`, `EfrisChadSettingService.java`). Three frontend files edited (`models.ts`,
  `catalog-api.service.ts`, `product-form.component.ts`). 177 Java files, static review clean.
  Both `ng build --configuration development` and the production `ng build` pass cleanly.

- **2026-09-16 — Updated `seed_100k_products.sql` for the new `unsp_code`/`service` columns.**
  Task: "update also seed_100k_products.sql." The script's `INSERT` list hadn't been touched since
  `unsp_code` was added for EFRIS Rwanda, so every seeded row silently got `unsp_code = NULL` and,
  after this session's EFRIS Chad pass, would have gotten `service = false` (the column default)
  for every one of the 100,000 rows — fine for the listing/paging/search load-test this script
  exists for, but useless for exercising an EFRIS submission's per-line lookups or the product
  form's new "This is a service" checkbox against bulk data. Now: `unsp_code` is set on 1 in 10
  rows (an 8-digit placeholder commodity code), `service` is `true` on 1 in 10 rows — both
  documented in the script's own header comment alongside the existing barcode/code/icon
  distribution notes, with matching sanity-check queries appended. One file edited
  (`backend/scripts/seed_100k_products.sql`), no application code touched.

- **2026-09-17 — Fixed EFRIS Chad's `cis_date`: the sale's own completion time, not submission
  time.** Fourier's explicit field-by-field mapping confirmed every other field already matched
  the previous pass, but named `cis_date` as "Order date in this format '2026-01-04 10:03:41'" —
  the earlier implementation instead sent wall-clock time at the moment the HTTP request was
  built (`LocalDateTime.now()`), which is the same as the order's completion time on a normal
  checkout but silently wrong for a *retried* submission (a fixed URL, a transient network issue,
  etc. — see the receipt's "Retry EFRIS submission" button): a retry minutes or hours later would
  have reported the retry's own time as if that's when the sale happened.

  `EfrisChadInvoicePayload` gained a new `orderDate` field (an `Instant` — `SaleOrder.completedAt`,
  the same timestamp stamped once at checkout and never touched again), and
  `OrderService.buildEfrisChadPayload` now passes `order.getCompletedAt()` through instead of
  nothing. `EfrisChadSubmissionService` no longer calls `LocalDateTime.now()` at all — a new
  `formatOrderDate` helper converts that `Instant` to the exact `"yyyy-MM-dd HH:mm:ss"` string the
  sample shows (falling back to "now" only if a payload somehow arrives with no order date at all,
  which shouldn't happen for a completed sale). Three files edited
  (`EfrisChadInvoicePayload.java`, `OrderService.java`, `EfrisChadSubmissionService.java`), no
  files added. 177 Java files, static review clean. `ng build --configuration development` passes
  (no frontend files touched by this fix).

- **2026-09-17 — EFRIS Chad: parse `sdc_data` on a successful submission into the same fiscal
  columns Rwanda's own submission uses.** Task: "If the response is SUCCESS when
  `response_code`=`P` then parse the result of sdc_data (split by ,) as follow: Second value is
  sdc_id / Third value is MRC / Fourth is sdc_receipt_number / Next is sdc_date / Next is
  signature_data / Next is internal_data." This is the split spec `EfrisChadSubmissionResult`'s own
  javadoc said was missing (see the 2026-09-16 EFRIS Chad submission entry above) — Fourier gave it
  the same way he originally gave Rwanda's own `sdc_data`/`cis_data` split, after the submission
  call itself was already live.

  `EfrisChadSubmissionResult` now carries `sdcId`, `mrc`, `sdcReceiptNumber`, `sdcDate`,
  `signatureData`, `internalData` alongside `success`/`errorMessage`/`rawResponse`, and its
  `success(String sdcData, String rawResponse)` factory splits `sdcData` on commas (padding with
  nulls so a short/garbled response never throws — same `splitLenient` convention Rwanda's
  `EfrisSubmissionResult` already uses) into those fields by Fourier's exact 1-indexed positions
  (0-indexed: `[1]`→sdcId, `[2]`→mrc, `[3]`→sdcReceiptNumber, `[4]`→sdcDate, `[5]`→signatureData,
  `[6]`→internalData — `[0]`, the tin_number, isn't named by his spec and is unused).
  `EfrisChadSubmissionService.parseResponse` now reads `response_data.sdc_data` out of the JSON
  body and passes it into that factory instead of the previous no-op `success(body)`.
  `OrderService.submitToEfrisChad` now sets those six fields onto `SaleOrder` on success — reusing
  the *same* columns Rwanda's own `submitToEfrisRwanda` populates (`sdcId`, `mrcData`,
  `sdcReceiptNumber`, `sdcDate`, `signatureData`, `internalData`), since these are the same
  conceptual SDC fiscal fields, not Rwanda-specific ones (mirroring the same reuse decision already
  made for `unsp_code`↔`classification_code` and `taxCode`↔`tax_code`). `cisNumber`/`cisDate` stay
  untouched — Chad's response has no equivalent.

  **No frontend changes were needed.** The receipt's "SDC INFORMATION" block (`receipt.component
  .ts`) was already gated generically on `o.sdcId`/`o.sdcDate`/`o.mrcData`, not on which
  integration produced them, and its `dateTimeParts` helper just splits on the first space — which
  Chad's `"yyyy-MM-dd HH:mm:ss"` `sdc_date` format satisfies exactly like Rwanda's. So a successful
  Chad submission now makes that whole block (including the MRC line and the QR code) render
  correctly on its own, while the Rwanda-only "CIS RECEIPT NUMBER"/"CIS DATE" lines correctly stay
  hidden (still gated on `cisNumber`/`cisDate`, which remain null for Chad).

  Three files edited (`EfrisChadSubmissionResult.java`, `EfrisChadSubmissionService.java`,
  `OrderService.java`), no files added. 177 Java files, static review clean. No frontend files
  touched.

- **2026-09-17 — EFRIS Chad: `cis_date`/`cis_number` corrections, plus Chad's own two letterhead
  logos.** Three follow-up tasks in one pass:

  **Task 1: "The cis_data for sale_order should be the one send to json cis_date."** The sale's
  fiscal `cisDate` is now set to exactly the `cis_date` string the request itself sent (computed
  once in `EfrisChadSubmissionService#submit` and threaded through to both `buildRequestTree` and,
  on success, `EfrisChadSubmissionResult`) rather than left null. **Task 2: "The cis_number is
  picked from tag invoice_number of json response."** `cisNumber` is now read from the response's
  `response_data.invoice_number` (works whether EFRIS Chad sends it as a JSON number, as the
  sample shows, or a string). `OrderService.submitToEfrisChad` sets both onto `SaleOrder` on
  success, alongside the `sdc_data`-derived fields from the previous pass — reusing Rwanda's own
  `cisNumber`/`cisDate` columns, same "same conceptual fiscal field, not Rwanda-specific" reasoning
  as every other SDC/CIS field reuse in this integration. The receipt's CIS lines ("CIS RECEIPT
  NUMBER"/"CIS DATE"), previously always hidden for a Chad sale, now render correctly too — no
  frontend change needed, same generic `*ngIf="o.cisNumber"`/`*ngIf="o.cisDate"` gating as before.

  **Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting."** Mirrors
  EFRIS Rwanda's own two-logo feature field-for-field: `EfrisChadSetting` gained `craLogoDataUrl`
  (Chad's own revenue authority — deliberately its own field rather than reusing Rwanda's
  `rraLogoDataUrl`, since "which country's revenue authority" is part of the field's identity, not
  an interchangeable label) and `efrisLogoDataUrl`; `UpdateEfrisChadSettingRequest` and
  `EfrisChadTerminalDto` carry them through the existing save/list flow; a new non-admin-gated
  `GET /api/apps/efris-chad/terminals/{registerId}/logos` (returning a new `EfrisChadLogosDto`,
  mirroring `EfrisLogosDto`) lets any logged-in cashier fetch them for printing, same permission
  split as Rwanda's own `/logos` endpoint. Admin > Settings > Apps > EFRIS Chad's terminal table
  gained "CRA Logo"/"EFRIS Logo" columns with the same file-upload-to-data:-URL pattern as the
  Rwanda table's own two logo columns.

  On the receipt side (`checkout/receipt.component.ts` and `returns/return-receipt.component.ts`),
  rather than looking up which integration a terminal is mapped to before deciding which `/logos`
  endpoint to call, both `EfrisApiService.getLogosForRegister` (Rwanda) and the new
  `EfrisChadApiService.getLogosForRegister` (Chad) are called in parallel, and two new getters
  (`authorityLogoDataUrl`, `printEfrisLogoDataUrl`) merge whichever one actually returned a logo —
  safe because "One Terminal can have only one EFRIS mapped with" (an earlier task) already
  guarantees at most one of the two ever has a row for a given terminal. The letterhead's left
  image alt text changed from the Rwanda-specific "RRA logo" to the neutral "Revenue authority
  logo" since it can now be either country's.

  Backend: one file added (`EfrisChadLogosDto.java`), five edited (`EfrisChadSetting.java`,
  `UpdateEfrisChadSettingRequest.java`, `EfrisChadTerminalDto.java`, `EfrisChadSettingService.java`,
  `EfrisChadSettingController.java`) for Task 3, plus three edited for Tasks 1–2
  (`EfrisChadSubmissionResult.java`, `EfrisChadSubmissionService.java`, `OrderService.java`). 178
  Java files, static review clean. Frontend: six files edited (`models.ts`,
  `efris-chad-api.service.ts`, `app-settings.component.ts`, `checkout/receipt.component.ts`,
  `returns/return-receipt.component.ts`). Both `ng build --configuration development` and the
  production `ng build` pass cleanly.

- **2026-09-17 — Audit log: who did what and when.** Task: "Implement an audit log of who did
  what and when." Scoped, when asked, to a curated set of "key business actions" rather than
  every mutating request (the alternative on offer): settings changes (business details, system
  settings, tax rates, payment methods, EFRIS Rwanda/Chad terminal settings), user management
  (create/update/reset password/set PIN/self password change), product create/update/deactivate,
  register create/update, app install/uninstall/enable/disable, voiding a sale, and processing a
  return. A cashier ringing up items or editing a cart is deliberately not logged — that would
  make the audit trail noise instead of a short, readable list of who changed what.

  New `com.posapp.audit` package: `AuditLog` (id/who/what/when — extends `BaseEntity`, whose
  `createdAt` *is* this entity's "when," since a row is written once and never updated),
  `AuditLogRepository` (a filtered/paged `search` query mirroring `ProductRepository
  .searchAllForAdmin`'s null-safe style, plus a distinct-actions lookup for the viewer's filter
  dropdown), `AuditLogDto`, `AuditLogService` (`record(action, description, entityType?,
  entityId?)` — pulls the acting user off `SecurityContextHolder` itself, the same context
  `JwtAuthFilter` populates on every request, so none of the eleven services below needed a new
  parameter threaded through their existing method signatures), and `AuditLogController`
  (`GET /api/audit-log` search + `GET /api/audit-log/actions`, both ADMIN-only).

  Eleven existing services now call `auditLogService.record(...)` right after their own save,
  each composing a plain-English sentence rather than leaving the viewer to reconstruct one from
  raw before/after fields: `StoreSettingsService` (business details, system settings),
  `TaxRateService`/`PaymentMethodService`/`UserService`/`RegisterService` (create/update, calling
  out a rate/role/active-flag/price change specifically when one happened), `AppsService`
  (install/uninstall/enable/disable), `EfrisSettingService`/`EfrisChadSettingService`
  (save/clear terminal settings — clear only logs when there was actually a row to remove, so
  re-clearing an already-unmapped terminal isn't a false event), `CatalogService`
  (create/update/deactivate product, with the update entry calling out a price change), and
  `OrderService`/`ReturnsService` (voiding a sale, processing a return). A password reset/change
  or PIN set is logged as having happened, never with the actual secret in the description.

  Admin > Settings > Audit Log (`admin/audit-log`, ADMIN-only, same tier as Reports/Apps): a
  server-side-paged, filterable table (search text, action-type dropdown, from/to date range) —
  same paging shape as the Products table, since this log only grows over time. New
  `AuditLogEntry` model, `AuditLogApiService`, `AuditLogListComponent`; one route and one sidenav
  link added.

  Backend: five files added (`AuditLog.java`, `AuditLogRepository.java`, `AuditLogService.java`,
  `AuditLogDto.java`, `AuditLogController.java`), eleven services edited to call it
  (`StoreSettingsService`, `TaxRateService`, `PaymentMethodService`, `UserService`,
  `RegisterService`, `AppsService`, `EfrisSettingService`, `EfrisChadSettingService`,
  `CatalogService`, `OrderService`, `ReturnsService`). 183 Java files, static review clean.
  Frontend: two files added (`audit-log-api.service.ts`, `audit-log-list.component.ts`), three
  edited (`models.ts`, `app.routes.ts`, `shell.component.ts`). Both `ng build --configuration
  development` and the production `ng build` pass cleanly.

- **2026-09-17 — Fix: Audit Log date-range filter crashed with a Postgres JDBC error.** Filtering
  the new Audit Log screen by from/to date threw `[ERROR: could not determine data type of
  parameter $7]` at runtime. Root cause: `AuditLogRepository.search`'s JPQL only ever used the
  `from`/`to` `Instant` parameters inside a bare `:from is null`/`:to is null` check — with no
  other typed context for that bind position, PostgreSQL's JDBC driver can't infer the
  parameter's type and fails to plan the whole query. This is the same bug class already solved
  once in this codebase for `ProductRepository.search`'s `q` String parameter (there, by always
  passing `""` instead of `null`); this fix applies the equivalent fix for `Instant`s. `from`/`to`
  are now normalized in `AuditLogService.search` to non-null sentinel bounds (`Instant.EPOCH` for
  an unset "from", `9999-12-31T23:59:59Z` for an unset "to") instead of being passed through as
  `null`, and `AuditLogRepository.search`'s query drops both `is null` branches for a plain `a
  .createdAt >= :from and a.createdAt <= :to` range check. Filtering with only one of the two
  dates set, or neither, or both, all now work. Two files touched (`AuditLogRepository.java`,
  `AuditLogService.java`); no frontend or API contract change. 183 Java files, static review
  clean.

- **2026-09-18 — Inventory depth: suppliers, purchase orders, receiving, and stock
  adjustments.** Task: "Inventory depth. Today stock is a single quantity field you edit
  directly. A complete POS usually adds purchase orders and supplier records, a stock-receiving
  flow that increments quantity with a paper trail, stock adjustments/write-offs with a reason
  code, and low-stock reordering (the dashboard already flags low stock, but nothing acts on
  it)." Scoped (when asked) to suppliers + purchase orders + receiving, stock
  adjustments/write-offs, and acting on the dashboard's low-stock flag — all against the existing
  single-store data model (no multi-location transfers this pass, since that only makes sense
  once there's more than one location to transfer between).

  New `com.posapp.inventory` package. `Supplier` (name/contact/phone/email/address/notes/active)
  — `SupplierService` hard-deletes an unreferenced supplier, same as `CatalogService
  .deleteCategory` does for an unused category, but falls back to deactivating one a purchase
  order already references rather than leaving that PO's "ordered from" dangling.
  `PurchaseOrderStatus` (DRAFT -> SENT -> (PARTIALLY_RECEIVED ->)* RECEIVED, or DRAFT/SENT ->
  CANCELLED) plus `PurchaseOrder`/`PurchaseOrderLine` — only a DRAFT can still be edited;
  `PurchaseOrderService.receiveLines` is the actual "stock-receiving flow that increments
  quantity with a paper trail": each call bumps a line's `quantityReceived` and, in the same
  transaction, increments the matching product's live stock via the existing
  `CatalogService.adjustStock` — the PO plus this call's own AuditLog entry together are the
  paper trail. Accepts a partial delivery and can be called more than once against the same PO.
  `StockAdjustmentReason` (DAMAGE/THEFT/EXPIRY/RECOUNT/OTHER) plus `StockAdjustment` — every
  direct stock correction that isn't a sale, return, or PO receipt now goes through
  `StockAdjustmentService.create` instead of an admin editing `Product.stockQuantity` directly:
  it snapshots stockBefore/stockAfter, requires a reason code, rejects a delta that would take
  stock negative, and (same as everywhere else) records who/when via `AuditLogService`.
  `CatalogService` gained `getProductOrThrow` — a plain, non-active-filtered lookup the inventory
  package needs (restocking/correcting a product regardless of its current active flag) without
  reusing `getProductForOfflineSync`, whose javadoc already claims a narrower single caller.
  Deliberately *not* a `Product.preferredSupplier` FK: `com.posapp.inventory` already depends on
  `com.posapp.catalog` (purchase order lines and stock adjustments reference `Product`), so a
  reference the other way — `Product` pointing at `Supplier` — would create the same kind of
  circular package dependency this codebase has always avoided (see `ReturnsService`'s own
  javadoc on `order`/`returns` for the earlier instance of this rule); the purchase-order form's
  supplier picker is a plain dropdown instead.

  Backend: 19 files added across `com.posapp.inventory` (`Supplier`, `SupplierRepository`,
  `SupplierService`, `SupplierController`, `PurchaseOrderStatus`, `PurchaseOrder`,
  `PurchaseOrderLine`, `PurchaseOrderRepository`, `PurchaseOrderService`,
  `PurchaseOrderController`, `StockAdjustmentReason`, `StockAdjustment`,
  `StockAdjustmentRepository`, `StockAdjustmentService`, `StockAdjustmentController`, and five
  `dto/` records/classes), plus `CatalogService.getProductOrThrow` and an `AuditLogService`
  class-javadoc update listing the new curated actions (`SUPPLIER_CREATED/UPDATED/DELETED`,
  `PURCHASE_ORDER_CREATED/UPDATED/SENT/CANCELLED/RECEIVED`, `STOCK_ADJUSTED`). 206 Java files,
  static review clean.

  Frontend: new `Supplier`, `PurchaseOrder`(+`PurchaseOrderLine`), `StockAdjustment` models;
  `supplier-api.service.ts`, `purchase-order-api.service.ts`, `stock-adjustment-api.service.ts`.
  Admin > Inventory gained three new screens: Suppliers (list/form), Purchase orders
  (list/form/detail — the detail screen is where a draft is sent or cancelled, and where a
  sent/partially-received order is actually received against, with per-line "receiving now"
  inputs), and Stock adjustments (list/form, with a product search picker and a reason-code
  dropdown). The dashboard's low-stock table (Task: "nothing acts on it") gained a per-row
  "Reorder" link and a bulk "Create purchase order for all" button, both landing on the
  purchase-order form pre-filled with a default quantity of 10 per line — a single PO belongs to
  one supplier, so a low-stock list spanning more than one usual supplier still produces one
  draft the admin assigns a supplier to and adjusts before saving, rather than being auto-split.
  Ten frontend files added, four edited (`models.ts`, `app.routes.ts`, `shell.component.ts`,
  `dashboard.component.ts`). Added a `.badge-warning` style (PARTIALLY_RECEIVED) to
  `styles.scss` — the existing badge set only had success/secondary/danger. Both `ng build
  --configuration development` and the production `ng build` pass cleanly.

- **2026-09-18 — Fix: sidebar accordion forgot which group was open on every navigation.** Bug
  report: "When I click on Inventory => Products, after loading product, POS Menu open its
  submenu instead of keeping Inventory submenu opened." Root cause: `ShellComponent` isn't a
  persistent layout wrapping a `<router-outlet>` — `AppComponent` is just `<router-outlet>`, and
  every routed page embeds its *own* `<app-shell>...</app-shell>` in its own template. So
  navigating to a different route destroys the old page (and the `ShellComponent` instance
  embedded in it) and constructs a brand new one — and `openGroup` was a plain field on that
  component, initialized to `'pos'` every time. The accordion wasn't "closing Inventory and
  reopening POS" on click; it was being torn down and rebuilt from scratch on *every single*
  navigation, in every group, and just happened to get noticed on Inventory → Products.

  Fixed by moving `openGroup` out of `ShellComponent` entirely into a new
  `SidenavStateService` (`providedIn: 'root'`, a true singleton for the app's lifetime — not
  reconstructed when the page underneath it is), with an Angular `signal` the same way
  `AuthService.currentUser` already does. `ShellComponent.isOpen`/`toggleGroup` now just
  delegate to it. Also added a one-time "open the right group already" default for the *first*
  page a session lands on (e.g. a bookmark straight into `/admin/suppliers`, or a hard refresh)
  by mapping `window.location.pathname` to a group at service-construction time — before this,
  landing directly on an Inventory page via URL/refresh always opened POS instead. One file
  added (`core/sidenav-state.service.ts`), one edited (`shell.component.ts`). Both `ng build`
  configurations pass cleanly.

- **2026-09-18 — Add a supplier inline from the New purchase order form.** Task: "You can add
  new Supplier from New purchase order form." Placing an order with a supplier that isn't set up
  yet used to mean losing the draft's lines-so-far to go fill out the full Admin > Suppliers
  form and come back. Now a "+ New supplier" button next to the picker opens an inline panel
  (name, contact person, phone, email — deliberately a smaller field set than the full Admin >
  Suppliers form, which still has address/notes too; a quick add to unblock this PO, not a
  replacement for it) right on the purchase-order form. Saving calls the same
  `SupplierApiService.create` the full form uses, adds the new supplier into the picker's list,
  and selects it on the draft right away — same "just did it, so use it" convenience `addLine`
  already gives a freshly-searched product. One file edited
  (`purchase-order-form.component.ts`); no backend change (reuses `POST /api/suppliers`). Both
  `ng build` configurations pass cleanly.

- **2026-09-18 — Task 1: Inventory Report; Task 2: Inventory Setting (allow negative stock).**
  Two independently-scoped tasks landed together.

  *Task 1 — Inventory Report.* Asked which of "stock on hand & valuation" vs. "stock movement
  history" it should show; answer was **Both**. Rather than bespoke components, extended the
  existing config-driven POS Reports framework (`REPORT_DEFS` / one generic
  `ReportViewerComponent` / `ReportController`) with a new `Inventory` section and two reports,
  since both are flat per-row tables that fit that framework exactly:
  - **Stock On Hand & Valuation** (`GET /api/reports/stock-valuation`) — a snapshot (no date
    range, like Outstanding/Overdue Credit), one row per active product: SKU, product, category,
    stock qty, unit cost, stock value (`qty × costPrice`, i.e. valued at cost, not at the
    customer-facing price), and a Low stock/OK status using the same
    `app.pos.low-stock-threshold` config the dashboard's low-stock list already uses.
  - **Stock Movement History** (`GET /api/reports/stock-movement`) — a date-ranged ledger, one
    row per dated stock-changing event. Discovered while building this that checkout/returns
    never touch `Product.stockQuantity` at all in this app (confirmed by `OrderService`'s own
    existing javadoc — stock is a purely manually/PO-managed number, never sale-driven), so the
    only two structured event sources are `StockAdjustment` (manual corrections/write-offs,
    already existed) and purchase-order receiving — which *didn't* already have a clean
    per-product-per-event record, only a cumulative `PurchaseOrderLine.quantityReceived` and a
    prose `AuditLog` line that can bundle several products together. Added a new entity,
    `StockReceipt` (mirrors `StockAdjustment`: product, qty, order number, unit cost, all
    snapshotted, timestamped via `BaseEntity.createdAt`), written once per product inside
    `PurchaseOrderService.receiveLines` alongside its existing `AuditLog` entry — this is what
    lets the ledger show accurate per-product, per-delivery rows instead of collapsing a
    multi-product receipt into one line. Both reports support the existing category filter;
    neither uses the register filter (stock isn't tied to a register).

  Backend: `StockReceipt` + `StockReceiptRepository` (new), `StockAdjustmentRepository` gained
  `findByCreatedAtBetweenOrderByCreatedAtDesc`, `PurchaseOrderService.receiveLines` now also
  writes a `StockReceipt` row per product received, `ReportService` gained `stockValuation`/
  `stockMovementHistory`, `ReportController` gained the two endpoints (JSON + Excel/PDF export,
  same as every other report), two new row DTOs (`StockValuationRow`, `StockMovementRow`).
  Frontend: two new `REPORT_DEFS` entries under a new `Inventory` section; `ReportListComponent`'s
  section list updated so it actually renders. No new components — the existing report picker and
  viewer handle both automatically.

  *Task 2 — Inventory Setting (allow negative stock).* Asked whether this should be per-product
  or store-wide; answer was **store-wide**. Added `StoreSettings.allowNegativeStock` (defaults to
  `true` — before this setting existed, stock was never checked anywhere in the app, so this
  preserves today's behavior for every existing deployment until an admin turns it off), its own
  independent request DTO/endpoint (`InventorySettingsRequest`, `PUT /api/settings/inventory`) —
  same "each admin screen only ever saves the fields it owns" split `StoreSettingsService`
  already uses for Business Details vs. System settings — and a new Admin > Inventory >
  Settings screen (`InventorySettingsComponent`) rather than folding the checkbox into System
  settings. Enforced in two places: `OrderService.checkout` now calls a new
  `validateStockAvailability`, grouping a cart's lines by product id (not line-by-line — a
  discounted and non-discounted line for the same product don't merge, so the check has to sum
  them) and rejecting a sale that would oversell any product when the setting is off; it's
  deliberately called only from the online `checkout()` path, never from the shared
  `completeCheckout()` that `syncOfflineSale` also uses — an offline sale already happened for
  real by the time it syncs, so this setting doesn't change that path's existing "never checked"
  policy. `StockAdjustmentService.create` got the same guard for manual corrections. Audit log
  entries record on/off changes the same as every other settings screen.

  Verified with `verify_java.py` and both `ng build` configurations (dev + production); both
  pass cleanly.

- **2026-09-18 — Five smaller tasks: stock qty on the terminal, Inventory Report moved into the
  Inventory menu, purchase order printing, a Purchase Orders report, and a Supplier Statement.**

  1. **Show stock quantity on Product terminal.** Every product tile in the cashier terminal's
     grid now shows its current stock quantity under the price ("Stock: 12"), or "Out of stock"
     in red at zero or below. A heads-up, not a block — this store may have "Allow negative
     stock" on, so a cashier can still legitimately sell past zero; the tile stays clickable
     either way. `ProductDto`/the frontend `Product` model already carried `stockQuantity`
     end-to-end, so this was template-only (`terminal.component.ts` — works in offline mode too,
     since it reads the same cached `Product` objects). Noticed while doing this that `.text-danger`
     was already referenced in a couple of places (this component's own offline-queue row, the
     receipts list) but never actually defined in `styles.scss`, so those silently rendered in the
     default text color — added the class for real (using the same `--danger-text` token
     `alert-danger` already uses), which fixes those pre-existing spots too.

  2. **Move Inventory Report as submenu to Inventory Menu.** The two Inventory reports (Task 1
     above) were only reachable via the general Admin > Reports picker, mixed in with unrelated
     sales/profit/credit reports. Added a second, smaller picker — `InventoryReportListComponent`
     at `/admin/inventory/reports`, same `REPORT_DEFS` data filtered to `section === 'Inventory'`,
     same target route (`/admin/reports/:key`, still the one shared `ReportViewerComponent` — no
     new viewer needed) — reached from a new "Reports" link under the Inventory sidenav group
     instead. `ReportListComponent`'s own section list dropped `'Inventory'` so those two reports
     show up on one picker, not both. Also fixed a latent instance of the sidebar-accordion bug
     from two passes ago: `SidenavStateService`'s URL-to-group map had no entry at all for
     `/admin/inventory/*`, so landing directly on the (already-shipped) Inventory Settings screen
     via bookmark/refresh opened the wrong accordion group — added a `/admin/inventory` prefix
     that covers both that screen and the new Reports link.

  3. **Print purchase order.** A printable purchase order (`/admin/purchase-orders/:id/print`,
     `PurchaseOrderPrintComponent`) — reached via a "Print" link on both the purchase-order list
     and its detail page. Same light/printable treatment as the sale and return receipts (a dark
     background is a bad idea on paper), but no EFRIS fiscal data — a PO is this store buying from
     a supplier, not a customer-facing fiscal sale. Shows the business letterhead (from
     `StoreSettings`, same as the sale receipt), the supplier's name/contact/address (looked up
     client-side from `SupplierApiService.listAll()` by id — `PurchaseOrderDto` only ever carried
     `supplierName`, and there's no reason to prefer a stale snapshotted address over the
     supplier's current one when printing an order to send them), the line items with quantity
     ordered/unit cost/line total, and a grand total (computed client-side — no `total` field on
     `PurchaseOrderDto`). No backend change.

  4. **Add Purchase order reports.** A new "Purchase Orders" report under Admin > Inventory >
     Reports — every PO placed in a chosen date range (any status/supplier), with order #,
     supplier, status, order/expected dates, line count, qty ordered, qty received, and total
     cost (`Σ quantityOrdered × unitCost` per line — what the order commits the store to paying,
     shown alongside qty received so a reader can see at a glance how much is still outstanding
     without opening it). Built as a normal `REPORT_DEFS` entry (`purchase-orders` endpoint, date
     range only — no register, since a PO isn't tied to one; no category, since one PO can span
     several) — fits the existing generic table exactly, same as Task 1's two reports.
     `PurchaseOrderRepository` gained `findByOrderDateBetweenOrderByOrderDateDesc`; `ReportService`
     gained `purchaseOrders` plus a shared `purchaseOrderRow` builder (reused by Task 5 below);
     `ReportController` gained the endpoint; new `PurchaseOrderReportRow` DTO.

  5. **Add Supplier Statement.** A per-supplier ledger — every PO ever placed with one supplier
     in a date range, oldest first, plus running totals — reached from a new "Supplier statement"
     link under Admin > Inventory > Suppliers, at `/admin/suppliers/statement`. Hand-built
     (`SupplierStatementComponent`) rather than plugged into the generic report framework, same
     "needs a picker + no register/category filter" reasoning as the existing Customer Statement,
     which it mirrors closely (down to reusing the same `ReportApiService`/`ReportFilter`, now
     with an added `supplierId` field alongside the existing `customerId`). Backend:
     `PurchaseOrderRepository` gained `findBySupplierIdAndOrderDateBetweenOrderByOrderDateAsc`;
     `ReportService.supplierStatement` 404s on an unknown supplier id (same convention as
     `customerStatement`) and reuses the same row-builder Task 4 added; `ReportController` gained
     `GET /api/reports/supplier-statement` (`supplierId` required, unlike every other report's
     optional filters — same shape as `/customer-statement`); new `SupplierStatementRow` DTO.

  Verified with `verify_java.py` (213 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Supplier payments, Sale Orders + Customer payments, and a "Sales" sidenav menu.** Fourier's
  request, verbatim: "Add Supplier payment / Add Sale order and Customer payment (So that we can
  have POS & Sales) / Then let have Sale as Menu and Move Customers into Sales (It becomes
  Submenu)." Four design decisions were confirmed with Fourier up front (all four "(Recommended)"
  options): a Sales Order is a **new, separate order type** mirroring Purchase Orders — no
  register/cashier session involved at all, stock only moves on fulfillment; a Sales Order's
  payment **reuses the existing customer credit/payment system** end to end, so it shows up
  alongside register sales in the same Outstanding Credit report and the same Customer Statement;
  a Supplier payment is **allocated against POs with an outstanding balance**, mirroring the
  customer side exactly; and the new "Sales" sidenav group holds Customers, Customer statement,
  Sale Orders, and Customer payments, replacing the old standalone Customers group.

  **Supplier payment** (`com.posapp.inventory`) — `SupplierPayment`/`SupplierPaymentAllocation`
  are the accounts-payable mirror of `CustomerPayment`/`CustomerPaymentAllocation`: every payment's
  `amount` must be allocated across one or more `PurchaseOrder`s, each allocation capped at what's
  still owed on that PO (`SupplierPaymentService.amountOwedForOrder` — zero for a DRAFT/CANCELLED
  PO, since nothing's been committed to the supplier yet). `SupplierPaymentService.totalCost`
  mirrors `ReportService.purchaseOrderRow`'s own `Σ quantityOrdered × unitCost` calculation, so
  "what's owed" always agrees with what Purchase Orders/Supplier Statement already show.
  `SupplierPaymentController` (`/api/suppliers/{id}/outstanding-balance|outstanding-orders|payments`)
  and a second `SupplierPaymentListController` (`/api/supplier-payments`, store-wide) back a new
  `RecordSupplierPaymentComponent` (`/admin/suppliers/:id/record-payment` — same "manual,
  oldest-first suggested" allocation table and inline printable receipt as `RecordPaymentComponent`)
  and `SupplierPaymentListComponent` (`/admin/supplier-payments`), both reached from Suppliers.
  Purchase Orders and Supplier Statement gained **Paid**/**Outstanding** columns
  (`SupplierPaymentService.amountPaidForOrder`/`amountOwedForOrder`), and a new **Outstanding
  Payables Report** (`/api/reports/outstanding-payables`, `section: 'Inventory'` in
  `report-defs.ts`) mirrors Outstanding Credit Report on the purchasing side.

  **Sale Order** (new `com.posapp.salesorder` package) — `SalesOrder`/`SalesOrderLine` mirror
  `PurchaseOrder`/`PurchaseOrderLine`'s DRAFT → CONFIRMED → (PARTIALLY_FULFILLED →)* FULFILLED /
  CANCELLED lifecycle (`SalesOrderStatus`), created for a customer with no register/cashier session
  anywhere in the flow. No tax modeling (mirrors `PurchaseOrderLine`, deliberately distinct from
  the POS `SaleOrder`/`OrderCalculator`'s inclusive-tax machinery). Confirming an order
  (`SalesOrderService.confirm`) is the moment its whole amount (`Σ quantityOrdered × unitPrice` —
  `SalesOrderService.totalAmount`, a static helper so `CustomerCreditService` can call it without a
  circular Spring bean dependency) becomes something the customer owes: it runs the same
  `CustomerCreditService.assertWithinLimit` credit-limit gate a POS credit sale goes through at
  checkout, and stamps `dueDate` from the customer's `defaultCreditTermDays`, same as checkout
  does. Fulfilling (`SalesOrderService.fulfillLines`) is the only step that moves stock
  (`CatalogService.adjustStock`, gated by the same store-wide "Allow negative stock" Inventory
  setting checkout already enforces) — accepts partial fulfillment across more than one call, same
  as `PurchaseOrderService.receiveLines`. `SalesOrderController` (`/api/sales-orders`, admin-only)
  backs `SalesOrderListComponent`/`SalesOrderFormComponent`/`SalesOrderDetailComponent`
  (`/admin/sales-orders`), mirroring the Purchase Order screens' own list/form/detail split.

  **Widening the customer credit/payment system for Sale Orders** — rather than a new payment
  mechanism, `CustomerPaymentAllocation` gained a second nullable FK, `salesOrder` (alongside the
  existing `order: SaleOrder`), an "exclusive arc": exactly one of the two is ever set on a given
  row, enforced in `CustomerCreditService.recordPayment`, not the entity. A new `OrderType` enum
  (`POS_SALE` / `SALES_ORDER`) discriminates which one a row/allocation/outstanding-order-DTO
  targets. `CustomerCreditService.outstandingForSalesOrder` mirrors `outstandingForOrder` (zero for
  a DRAFT/CANCELLED Sales Order — nothing owed yet, or never will be); `outstandingBalance`,
  `listOutstandingOrders`, and `recordPayment` all now merge POS credit sales and Sales Orders into
  one result rather than reading only `SaleOrder`s. `ReportService.customerStatement`/
  `outstandingCredit`/`overdueCredit` were widened the same way, so a Sales Order shows up
  alongside register sales in the Customer Statement and both credit reports, exactly as promised.
  A new **Sales Orders report** (`/api/reports/sales-orders`, `section: 'Sales'`) lists every Sales
  Order in a date range with its own outstanding amount.

  `RecordPaymentComponent`'s allocation table now shows a **Type** column (POS sale vs. Sales
  order) and threads `orderType` through every allocation it submits — no separate screen needed,
  since the backend already returns one merged, oldest-first list. Two new store-wide payment list
  screens: `CustomerPaymentListComponent` (`/admin/customer-payments` — `CustomerPaymentController`
  gained `GET /api/customer-payments`, `CustomerCreditService.paymentHistory()` no-arg overload)
  and the Supplier one described above.

  **Sidenav reorg** — the old standalone "Customers" group is gone; a new **"Sales"** group (💵)
  holds Customers, Customer statement, Sale orders, Customer payments, and a Sales-scoped Reports
  picker (`SalesReportListComponent`, mirrors `InventoryReportListComponent`'s own pattern —
  `/admin/sales/reports`). `SidenavStateService.GROUP_ROUTE_PREFIXES`'s `'customers'` entry became
  `'sales'` with the widened prefix list. Inventory gained a "Supplier payments" link.

  Backend package layout: `com.posapp.inventory` (Supplier payment — same package as
  `PurchaseOrder`/`Supplier`, since it's purchasing-side accounting) and a new top-level
  `com.posapp.salesorder` (Sale Order — deliberately not `com.posapp.order`, to keep it visibly
  separate from the POS `SaleOrder`/checkout code it never touches). `com.posapp.credit` (existing)
  absorbed the widening. No circular Spring bean dependency anywhere in this chain:
  `SalesOrderService` depends on `CustomerCreditService`, which depends on `SalesOrderRepository`
  (a repository, never `SalesOrderService` itself) plus a static `SalesOrderService.totalAmount`
  helper call — repository/static-method dependencies only ever point one way.

  Verified with `verify_java.py` (236 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Four small follow-ups: new-customer-from-Sale-Order, Supplier Tax ID, report
  "back" links, Stock Movement History before/after quantities.** Task, verbatim: "1. can add new
  Customer from New sale order form 2. add TAX ID on supplier 3. all link to back report shoudl go
  to the parent not to the POS reports 4. On Stock Movement History, no pos and sale transactions,
  also add the previous quantity and current quantity after transaction."

  **1. New customer from the Sale Order form** — `SalesOrderFormComponent` gained an inline
  "+ New customer" panel next to its Customer picker (the picker disables while it's open), the
  same pattern as `PurchaseOrderFormComponent`'s own "+ New supplier" panel: name/phone/email only
  (no credit fields — those stay a deliberate decision made on the full Admin > Customers form),
  and saving selects the new customer on the draft right away.

  **2. Tax ID on Supplier** — `Supplier` gained a `taxId` field, the same nullable/purely-
  informational treatment as `Customer.taxId` (nothing in purchasing math reads it). Threaded
  through `SupplierDto`/`SupplierRequest`/`SupplierService.applyRequest` on the backend and
  `models.ts`/`SupplierPayload`/`SupplierFormComponent` (new "Tax ID" input) on the frontend, plus a
  "Tax ID" column on the supplier list for parity with the Customer list.

  **3. Report "back" links go to their actual parent, not always POS Reports** —
  `ReportViewerComponent` is one shared screen for every report regardless of which picker linked
  to it (`ReportListComponent` at `/admin/reports`, `InventoryReportListComponent` at
  `/admin/inventory/reports`, `SalesReportListComponent` at `/admin/sales/reports`), but its "back"
  link was hardcoded to `/admin/reports` no matter which picker actually owned the report. Added
  `reportListRouteForSection()` in `report-defs.ts`, mapping a `ReportDef`'s own `section` to its
  owning picker's route, and `ReportViewerComponent` now derives its back link from that instead of
  assuming POS.

  **4. Stock Movement History: no POS/Sale rows (already true); added Before/After quantity
  columns** — re-verified `ReportService.stockMovementHistory` only ever merges
  `StockAdjustmentRepository`/`StockReceiptRepository`; sales and Sale Order fulfillment were
  already never a source (see `StockMovementRow`'s own javadoc on why) and stayed that way. Added
  `stockBefore`/`stockAfter` snapshot columns to `StockMovementRow`: `StockAdjustment` already had
  these fields, but `StockReceipt` didn't, so it gained them and `PurchaseOrderService.receiveLines`
  now snapshots the product's stock immediately before/after each `CatalogService.adjustStock` call,
  same convention `StockAdjustmentService` already used for adjustments. Flowed through
  `ReportController`'s export headers and `report-defs.ts`'s `stock-movement` column list ("Before"
  / "After", both `int`-formatted).

  Verified with `verify_java.py` (236 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Bug fix: can't confirm a Sale Order for "Walk-in Customer".** Bug: "not
  confirming sale order => Customer 'Walk-in Customer' is not enabled for credit purchases."
  Root cause is by design, not a defect in the credit check itself: confirming a Sale Order always
  puts its whole amount on the customer's credit tab (`SalesOrderService.confirm` calls
  `CustomerCreditService.assertWithinLimit` with the order's full total, unconditionally — there's
  no "pay now" path for a Sale Order the way there is at POS checkout), so the seeded, always-
  non-credit "Walk-in Customer" (`Customer.walkIn`) can never be confirmed. It was still offered in
  `SalesOrderFormComponent`'s customer picker, so a user could build out a whole draft against it
  and only discover this dead end at Confirm.

  Fix (frontend only, `SalesOrderFormComponent`): Walk-in Customer is now excluded from the New/Edit
  Sale Order form's customer dropdown entirely. An existing draft that already has it set (saved
  before this fix) instead shows an inline warning identifying the problem and what to do about it
  ("pick a different customer, or enable credit for one from Admin > Customers first") rather than
  silently failing later. The draft can still be saved either way — only *confirming* requires a
  credit-enabled customer — so this doesn't block editing an already-broken draft to fix it.

  Verified with `verify_java.py` (236 files — no backend change this pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Follow-up: Sale Order confirmation no longer requires credit-enabled at all
  (supersedes the Walk-in Customer fix above).** Fourier's correction after the previous entry:
  "the POS Module stores data into sale_order table while Sale Module store data into sales_order
  table. The rules of enabling credit on customer should only be required in POS Module not in
  Sale Module." The previous fix treated Walk-in Customer's block as the whole problem and worked
  around it in the frontend; this pass removes the actual cause. `SalesOrderService.confirm` no
  longer calls `CustomerCreditService.assertWithinLimit` at all — confirming a Sale Order now just
  stamps `dueDate` and moves DRAFT → CONFIRMED, for any customer, regardless of
  `Customer.creditEnabled` or `creditLimit`. Only `OrderService.checkout` (POS, the `sale_order`
  table) still gates on those two fields; the two modules' credit rules are now fully independent,
  per the user's own framing of the two tables.

  `SalesOrderFormComponent`'s customer picker reverted back to listing every customer, including
  Walk-in Customer — the exclusion/warning added in the previous pass is no longer needed since
  there's nothing left for it to warn about. `CustomerCreditService` still reads Sales Orders via
  `SalesOrderRepository`/`SalesOrderService.totalAmount` (unchanged) purely to compute outstanding/
  paid amounts for reporting and payment allocation — that part of the "reuse the existing credit/
  payment system" design from the original Sale Order feature is untouched, so a confirmed Sale
  Order still shows up in Outstanding/Overdue Credit reports, the Customer Statement, and Record
  Payment exactly as before; only the confirmation-time gate was removed.

  Verified with `verify_java.py` (236 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Six-item batch: why Customer Payment doesn't show POS payments, payment mode on
  Customer Payment, Record Payment on Purchase Order, Sale Order Return, Purchase Return, and a
  unified Stock Movement History.**

  1. **Why doesn't Customer Payment show payments made in the POS Module?** Not a bug — the two
     are deliberately different concepts. `CustomerPayment` is only ever created by
     `CustomerCreditService.recordPayment` (the Record Payment screen), i.e. a *later* settlement
     against something already owed — a Sale Order or a POS credit sale being paid down after the
     fact. A POS sale paid in full **at the register, at time of sale** never creates a
     `CustomerPayment` at all: it's captured directly as one or more `Payment` rows on the
     `SaleOrder` itself (`OrderService.completeCheckout`), the simpler "tendered at checkout"
     concept that never needed later allocation against an outstanding balance because nothing was
     ever left outstanding. Both are visible, just in different places: a `SaleOrder`'s own
     `Payment`s show on its receipt and the Z-report/shift reports; `CustomerPayment`s show on the
     Customer Payments list and a customer's own statement. No code changed for this item — it's
     the existing, intended design, documented here since it wasn't obvious from the UI alone.

  2. **Add payment mode to Customer Payment.** `CustomerPayment` gained a `method` field (`CASH`,
     `CHEQUE`, etc.) — reusing the same admin-configurable `PaymentMethodEntity`/code system
     checkout and POS refunds already validate against
     (`PaymentMethodService.validateActiveCodeOrThrow`), rather than inventing a second payment-
     method list. `RecordPaymentRequest.method` is now required; `CustomerCreditService
     .recordPayment` validates it the same way `SupplierPaymentService`/`ReturnsService` already
     validate theirs. The Record Payment screen (`record-payment.component.ts`) gained a Payment
     method dropdown (defaults to the first active method), a Method column on its own history
     table and the store-wide Customer Payments list, and a line on the printable receipt.

  3. **Add Record Payment shortcut to the Purchase Order view/list.** Every `PurchaseOrderDto` now
     carries its own `amountPaid`/`amountOutstanding`, computed by `PurchaseOrderService.toDto`
     via `SupplierPaymentService` — the same "every order DTO knows its own paid/outstanding
     figures" shape `SalesOrderDto` already had for Sale Orders. A "Record payment" button/link
     shows on the Purchase Order detail page and each outstanding row of the Purchase Orders list
     (`*ngIf="amountOutstanding > 0"`), jumping straight to that supplier's own Record Supplier
     Payment screen — which already lists every one of that supplier's outstanding POs to allocate
     against, this one included, so no new allocation UI was needed.

  4. **Sale Order Return** (Task: "Sale Order Return (Cancellation after Confirmation and Payment)
     Sale Module"). Mirrors the existing POS `ReturnsService`/`SalesReturn` pattern
     (`com.posapp.returns`) but scoped to a CONFIRMED/PARTIALLY_FULFILLED/FULFILLED `SalesOrder`:
     new `SalesOrderReturn`/`SalesOrderReturnLine` entities, `SalesOrderReturnService`, controller,
     and DTOs — partial, per-line, with an optional reason. Only the *fulfilled* portion of a line
     can be returned (nothing was ever physically handed over beyond what was fulfilled); each
     return reverses stock for the returned quantity (`CatalogService.adjustStock(+qty)`) and never
     touches the Sale Order's own status — a return is a side-ledger, exactly like a POS return
     leaves the original sale's status alone. Fourier's own choice, asked directly: the money side
     is **informational only** — a return that leaves the customer having overpaid is tracked as a
     "refund due" figure (`CustomerCreditService.refundDueForSalesOrder`), not an instruction for
     the system to actually pay anything out, so unlike a POS return there's no `refundMethod`
     field at all. `CustomerCreditService.outstandingForSalesOrder` was widened to subtract
     returned amounts before subtracting what's paid, and `SalesOrderDto` grew `amountPaid`/
     `amountOutstanding`/`amountReturned`/`refundDue` fields (computed by `SalesOrderService.toDto`
     via `CustomerCreditService` — safe to depend on it there since `CustomerCreditService` never
     injects a `SalesOrderService` bean back, only reads `SalesOrderRepository`/the static
     `totalAmount` helper, so there's no circular Spring dependency). Frontend: a "Process a
     return" panel and return history section on `SalesOrderDetailComponent` (only shown once
     there's fulfilled-but-unreturned quantity), Paid/Outstanding/Returned/Refund-due stat tiles,
     an Outstanding column on the Sale Orders list, and a store-wide Sale Order Returns list
     screen (`/admin/sales-order-returns`) mirroring the POS Returns list.

  5. **Purchase Return** (Task: "Supplier Purchase Return (Cancellation after Confirmation and
     Payment) Inventory Module"). The purchasing-side mirror of the above: new
     `PurchaseReturn`/`PurchaseReturnLine` entities (`com.posapp.inventory`),
     `PurchaseReturnService`, controller, and DTOs — partial, per-line, with a reason. Only the
     *received* portion of a line can be returned; reverses stock for the returned quantity in the
     opposite direction from a Sale Order return (`adjustStock(-qty)`, since it's leaving the store
     back to the supplier) and never touches the Purchase Order's own status. Same informational-
     only money model, confirmed separately for this direction too: `SupplierPaymentService
     .amountOwedForOrder` now subtracts returned amounts before subtracting what's paid, and a new
     `refundDueFromSupplierForOrder` tracks what the *supplier* now owes the store back once a
     return brings the net owed below what's already been paid to them. `PurchaseOrderDto` grew
     matching `amountReturned`/`refundDue` fields alongside the `amountPaid`/`amountOutstanding`
     item 3 added. Frontend: a "Process a return" panel and return history section on
     `PurchaseOrderDetailComponent`, Returned/Refund-due-from-supplier stat tiles, and a store-wide
     Purchase Returns list screen (`/admin/purchase-returns`).

  6. **Unify Stock Movement History across every stock-affecting source** (Task: "Any transaction
     affecting stock should be shown to Stock Movement History (Pos sales, Sale orders, Purchases
     orders)"). `ReportService.stockMovementHistory` went from two structured sources
     (`StockAdjustment`, `StockReceipt`) to seven: those two, plus POS sale (`OrderLine`, which
     gained `stockBefore`/`stockAfter` snapshot fields populated in
     `OrderService.completeCheckout`), POS return (`SalesReturnLine`, same two fields, populated in
     `ReturnsService.processReturn`), Sale Order fulfillment (a new `SalesOrderFulfillment` entity
     — one row per product per `SalesOrderService.fulfillLines` call, the fulfillment-side
     counterpart to `StockReceipt`, needed because `SalesOrderLine.quantityFulfilled` is only a
     cumulative running total with no per-event timestamp), and this batch's own two new return
     types, Sale Order return and Purchase return (their `SalesOrderReturnLine`/`PurchaseReturnLine`
     entities each got the same `stockBefore`/`stockAfter` pair). Also widened the scope beyond the
     task's three literal examples to include POS/Sale Order/Purchase returns too — the general
     principle ("any transaction affecting stock") would have been inconsistently applied
     otherwise, especially with two of those return features being built in this very batch.
     `StockMovementRow`'s javadoc, which incorrectly claimed sales/returns never touch stock (they
     always have — `OrderService.completeCheckout` and `ReturnsService.processReturn` both call
     `CatalogService.adjustStock`; the claim was simply wrong when it was written and this pass
     both fixes the report and corrects the comment), is now accurate: seven `type` values
     ("Adjustment", "Purchase receipt", "POS sale", "POS return", "Sale order fulfillment", "Sale
     order return", "Purchase return"), one row per dated event, still floored by the "no product
     snapshot, no row" rule for any line whose product was null or predates its snapshot fields.
     The frontend needed no changes at all — `report-defs.ts`'s `stock-movement` column set is
     already generic (`type`/`reference`/`note` as plain text columns), so the three new source
     types just render.

  Verified with `verify_java.py` (256 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-18 — Three-item follow-up: merge POS payments into Customer Payment history, a
  track-stock attribute on Product, and require a customer on every POS sale.**

  1. **Customer Payment history now includes POS-tendered payments too.** Fourier's own follow-up
     to item 1 of the previous batch: "no POS payment for cash sale. But when I sale with Credit
     and record payment after it appear" — confirming that behavior was correct but asking the two
     kinds of payment to live in one list. Rather than forcing a checkout `Payment` row into the
     `CustomerPayment` entity/allocation model, `CustomerPaymentDto` gained a `source: PaymentSource`
     discriminator (`SETTLEMENT` vs `POS_SALE`, a new enum) and a second static factory,
     `fromPosPayment(Payment, SaleOrder, BigDecimal)`, that synthesizes a DTO row directly from a
     checkout `Payment` and its parent `SaleOrder` — `id`/`recordedByName` null (nothing was
     "recorded", it was tendered at checkout), a single synthetic allocation pointing back at the
     sale itself so the "Applied to" column still reads sensibly. `OrderRepository` gained
     `findAllCompletedWithCustomer`/`findCompletedForCustomer` (completed, customer-attached sales,
     newest first); `CustomerCreditService.paymentHistory()`/`paymentHistory(Long)` now merge actual
     `CustomerPayment` rows with these synthesized POS-sale rows, sorted by date descending, with a
     small balance cache so a customer with many rows doesn't recompute `outstandingBalance` once
     per row. Frontend: `CustomerPayment.id` widened to `number | null`, new `source` field; both the
     store-wide Customer Payments list and the per-customer history table on Record Payment gained a
     Type column (badge: "POS sale" vs "Settlement").

  2. **Track stock attribute on Product.** Fourier: "Add track stock attribute on Product." Asked
     what turning it off should actually do; chosen answer: skip stock deduction/checks entirely — a
     non-tracked product behaves like a service or made-to-order item, always sellable regardless of
     `stockQuantity`, and selling it never changes that number. New `Product.trackStock` (boolean,
     default `true`, so every existing product is unaffected). Checked in exactly the three places
     the confirmed answer named: `OrderService.validateStockAvailability`/`completeCheckout` (POS
     checkout — skips the stock-availability check and the decrement for a non-tracked line),
     `SalesOrderService.fulfillLines` (Sale Order fulfillment — same skip, and no
     `SalesOrderFulfillment` stock-movement row is created for it), and
     `CatalogService.listLowStock`/`ProductRepository` (a new
     `findByActiveTrueAndTrackStockTrueAndStockQuantityLessThanOrderByStockQuantityAsc` query
     excludes non-tracked products from low-stock alerts entirely). To keep a return the mirror image
     of the sale it reverses, `ReturnsService.processReturn` and `SalesOrderReturnService` also skip
     restocking a non-tracked line, since nothing was ever deducted for it. Purchase-order receiving
     and manual stock adjustments are deliberately left alone — an admin's own explicit inventory
     action, not an automatic side effect of a sale. Frontend: a "Track stock for this product"
     checkbox on the product form (hides the stock-quantity field when off), the admin product list
     shows "Not tracked" instead of a number, and the POS terminal's product tiles simply omit the
     stock caption for a non-tracked product instead of ever showing "Out of stock".

  3. **Every POS sale now requires a customer.** Fourier, mid-session: "Never allow any sale
     transaction even into POS without Customer information." The terminal already defaults every
     new cart to the seeded "Walk-in Customer" (an earlier task replaced the old null/"No customer"
     dropdown option with this), so in normal use a cart already always carries *some* customer —
     this closes the remaining gap for anything that could reach checkout without going through that
     flow. `OrderService.checkout` now throws a 400 ("A customer is required for every sale...") if
     `order.getCustomer()` is still null at checkout time, checked only on the online path — same
     exemption reasoning as `validateStockAvailability`'s: `syncOfflineSale` replays a sale that
     already genuinely happened while offline, so rejecting the sync over a missing customer would
     only strand it, not undo anything. Frontend: `CheckoutComponent` mirrors the guard client-side
     (a clear message and a disabled "Complete sale" button when `order.customerId` is null) so a
     cashier gets an immediate, obvious reason rather than a raw error from the backend.

  Verified with `verify_java.py` (257 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **2026-09-19 — Manufacturing Module (new).** Fourier: "We are now good with POS and Sale Module,
  Let move on with adding more Module for ERP. Add Manufacturing Module." Scoped via his own
  numbered list — "1.Bill of Materials 2.Production plans 3.Manufacturing orders 4.Indirects Costs
  5.Workstations 5.Settings (Production routings etc)" — and, when asked to pick between a simpler
  or fuller design for Production Plans and Indirect Costs, "Am new on Manufacturing but I need
  full features," read as "the fuller option, everywhere." A brand-new `com.posapp.manufacturing`
  backend package plus a full Angular admin section, six sub-areas:

  1. **Bill of Materials (`Bom`/`BomLine`)** — the recipe for one finished-good `Product`: a list
     of component `Product`s and, per component, `quantityPerUnit` — deliberately "how many of
     this component to make ONE unit of the finished good," not a batch-yield ratio, so every
     quantity in this app stays a plain whole-number `int` with zero rounding (see `BomLine`'s
     javadoc). Deliberately single-level — a component is always a plain `Product`, never another
     product's own Bom — to keep cost roll-up and stock consumption a one-pass calculation with no
     cycle detection; `Bom`'s javadoc documents this as the extension point if sub-assemblies are
     ever wanted. A product can have more than one Bom (e.g. an alternate recipe); `active` marks
     the default. `BomDto.estimatedUnitMaterialCost` sums `component.costPrice * quantityPerUnit`
     at today's prices — an estimate, refreshed live, distinct from the cost a real production run
     actually locks in (below). Frontend: a searchable finished-good picker plus a searchable
     component-line editor, both mirroring the Purchase Order form's product-search convention.

  2. **Workstations (`Workstation`)** and **Settings/Routings (`Routing`/`RoutingOperation`)** —
     Fourier's own naming, "Settings (Production routings etc)." A flat admin-managed lookup
     (`Workstation`: code/name/`hourlyRate`, same shape as `PaymentMethodEntity`/`TaxRate`) and an
     ordered list of named operations (`Routing`: "Cut," "Assemble," "Paint," "Pack," each
     optionally at a workstation, with `estimatedMinutesPerUnit`). Both are entirely optional — a
     Bom without a routing still works end to end for materials-in/finished-goods-out; a routing
     only adds a labor/time estimate on top. A `ManufacturingOrder` snapshots its Bom's routing at
     confirm time, the same "lock in what was referenced" convention as every other order type in
     this app. Frontend: simple CRUD forms for Workstations; a nested add/remove-row operation
     editor for Routings.

  3. **Indirect Costs (`OverheadCost`/`OverheadAllocationMethod`)** — Fourier's own term for
     overhead, confirmed (via "full features") as named cost pools rather than one flat store-wide
     percentage: an admin defines any number of pools (e.g. "Electricity," "Factory rent share"),
     each with its own `allocationMethod` — `FLAT` (a fixed total for the whole order, spread
     across its runs proportional to how much of the planned quantity each run produced, so a
     fully-produced order's runs always sum to exactly the flat rate), `PER_UNIT` (rate × quantity
     produced this run), or `PERCENT_OF_MATERIAL_COST` (rate% × that run's own material cost).
     Picked pools are snapshotted onto `ManufacturingOrderOverhead` (name/method/rate) at order
     creation, so a later edit to the admin record never rewrites a past order's own cost — the
     same convention `Payment.method`/`OrderLine.taxRatePercent` already use.

  4. **Manufacturing Orders (`ManufacturingOrder`/`ManufacturingOrderRun`/
     `ManufacturingOrderConsumption`)** — the same two-stage, run-based lifecycle as
     `PurchaseOrderStatus`/`SalesOrderStatus`: DRAFT (freely editable) → `confirm` (locks it,
     snapshots the routing) → one or more `produce` calls (PARTIALLY_PRODUCED) → fully produced
     (PRODUCED), or `cancel` from DRAFT/CONFIRMED. Each `produce` call is a real production run: it
     computes required component quantities via `BomService.requiredComponentQuantities`, checks
     stock (unless "Allow negative stock" is on, same setting Purchase/Sales Orders already
     respect), decrements every stock-tracked component (`Product.trackStock` — an untracked
     component is never checked or adjusted, same convention as POS/Sale Order checkout),
     increments the finished good's stock, and computes that run's own material/overhead/total
     cost, snapshotting a `stockBefore`/`stockAfter` pair for every stock-tracked movement so this
     run reads correctly forever after. The Inventory Report's Stock Movement History now includes
     two more source types, "Manufacturing output" and "Manufacturing consumption" (nine sources
     total — see `StockMovementRow`'s updated javadoc). Frontend: a create form (pick a Bom,
     planned quantity, optional overhead pools) and a detail screen with confirm/produce/cancel
     actions, a live cost breakdown (material/overhead/total/unit cost), and a run history table
     with per-run component consumption.

  5. **Production Plans (`ProductionPlan`/`ProductionPlanLine`)** — full MRP-style, per "I need
     full features": `ProductionPlanService.generate` aggregates demand from every unfulfilled
     line on a CONFIRMED/PARTIALLY_FULFILLED back-office Sale Order (not POS sales — those settle
     instantly with no "unfulfilled" concept) plus a caller-supplied low-stock threshold (mirroring
     `CatalogService.listLowStock`'s own threshold-per-call shape rather than a stored per-product
     reorder point), for every product with an active Bom. `suggestedQuantity` = shortfall against
     sales demand + whatever's still needed to top current stock up to the threshold after that
     demand is covered. Deliberately does **not** explode multi-level BOMs and does **not**
     auto-generate Purchase Orders for shortfalls — both documented as scope boundaries in
     `ProductionPlan`'s class javadoc, left for a future pass. A generated plan is a stored, dated
     snapshot (not a live filter); each line shows live component shortfalls (recomputed against
     today's stock every time the plan is viewed, not frozen at generation time — see
     `ProductionPlanDto`'s javadoc for why) and a "Generate order" action that converts one
     reviewed line straight into a real DRAFT Manufacturing Order. Frontend: a list with an inline
     "Generate a new plan" panel, and a detail screen with the demand/shortfall table and per-line
     order generation.

  All six areas are ADMIN-only, reachable from a new "Manufacturing" sidenav group (same
  accordion/admin-gated treatment as Inventory/Sales/Users/Settings) with sublinks for Bill of
  Materials, Production Plans, Manufacturing Orders, Workstations, Routings, and Indirect Costs.

  Verified with `verify_java.py` (305 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Accounting Module** (Fourier's own request: "We need to add Accounting module. Then all POS,
  Sale, Inventory and Manufacturing module will be integrated when accounting is enabled") — a
  full double-entry bookkeeping engine (`com.posapp.accounting`), off by default and gated behind
  one new store-wide switch (`StoreSettings.accountingEnabled`, Admin > Accounting > Settings) so
  a store that never turns it on gets no journal entries and pays no cost, exactly today's
  behavior unchanged.

  1. **Chart of Accounts (`Account`/`AccountType`)** — the five standard types (Asset, Liability,
     Equity, Revenue, Expense). Turning Accounting on for the first time seeds a standard starter
     chart (`AccountService.seedDefaultChartIfNeeded`, idempotent — a no-op once any account
     exists): Cash, Accounts Receivable, Inventory, Accounts Payable, Tax Payable, Accrued
     Manufacturing Overhead, Owner's Equity, Sales Revenue, Cost of Goods Sold, Inventory
     Shrinkage Expense, Operating Expenses, and a Suspense account (see point 4 below). The dozen
     accounts every other module posts to automatically are flagged `systemAccount` and can't be
     deactivated; everything else (including the two starter accounts nothing posts to
     automatically — Owner's Equity, Operating Expenses) is freely editable, and an admin can add
     more at any time. Frontend: a grouped-by-type list, a create/edit form, and a per-account
     dated running-balance ledger view.

  2. **Journal Entries (`JournalEntry`/`JournalEntryLine`)** — every posting, automatic or manual,
     is one balanced entry (its lines' debits and credits always total exactly the same amount).
     Admin > Accounting > Journal Entries lists every entry (tagged by source — "POS sale,"
     "Purchase receipt," "Manual," etc.) with a detail view of its lines, plus a "New manual
     entry" screen (`JournalEntryService.createManual`) for one-off adjustments no module posts by
     itself — recording an owner contribution, paying rent, or settling an actual utility bill
     against the Accrued Manufacturing Overhead accrual point 6 below creates (both validated
     client-side and, as the real source of truth, server-side: at least two lines, exactly one of
     debit/credit set per line, and the totals must match).

  3. **Automatic posting, one call per existing module** — `AccountingService` is the one class
     every other module calls into, right after its own real side effect has already happened and
     saved, never before: `OrderService.completeCheckout` (POS sale — cash/credit split, revenue,
     tax, COGS/inventory relief for stock-tracked lines only), `ReturnsService.processReturn` (POS
     return — full reversal), `SalesOrderService.fulfillLines` (Sales Order fulfillment — revenue/
     COGS for exactly the quantity fulfilled that call, COGS priced at the product's live
     `costPrice` since `SalesOrderLine` carries no cost snapshot of its own, unlike a POS
     `OrderLine`), `SalesOrderReturnService.processReturn` (Sales Order return), `PurchaseOrder
     Service.receiveLines` (purchase receipt — Inventory debit / Accounts Payable credit),
     `PurchaseReturnService.processReturn` (purchase return), `SupplierPaymentService.record
     Payment` and `CustomerCreditService.recordPayment` (both settlements — every tender method,
     cash or otherwise, lands in the one Cash account, since this app has no per-method bank/
     clearing sub-accounts), `StockAdjustmentService.create` (a positive delta is a recount gain,
     a negative one a real shrinkage loss, both priced at the product's `costPrice`), and
     `ManufacturingOrderService.produce` (see point 6 below for why only overhead absorption
     posts). Every posting call is a no-op when Accounting is disabled, and every one defends
     itself with the Suspense-account fallback in point 4 rather than ever throwing — a posting
     happens strictly after its real-world event, so it must never roll one back.

  4. **A posting can never block or undo the real transaction it's recording.** If a would-be
     entry's lines don't balance to the exact penny — which shouldn't happen from how any of the
     `postXxx` methods above are built, but could in principle drift from an edge case like an
     offline-synced sale's client-estimated price/tax not exactly matching the server's final
     recomputed figures (see `SaleOrder.syncNote`) — `AccountingService.post` plugs the
     difference into a dedicated Suspense account and logs a warning rather than throwing. The
     Balance Sheet report flags this directly (point 5 below) so it's never silently missed.

  5. **General Ledger, Trial Balance, Profit & Loss, and Balance Sheet (`AccountingReportService`/
     `AccountingReportController`)** — the four standard reports, all built from `JournalEntryLine`
     rows and kept deliberately separate from the pre-existing POS/Inventory/Sales report engine
     (`com.posapp.reports`), which they share no data with. The Ledger and Trial Balance take a
     date range (mirroring `ReportController`'s own `from`/`to` → `Instant` conversion, defaulting
     to the last 30 days); the Balance Sheet is a snapshot as of one date (default: today) and
     carries a live `currentEarnings` figure (all-time Revenue minus Expenses not yet closed to
     Owner's Equity) plus a `balanced` sanity flag comparing Assets against Liabilities + Equity +
     current earnings. Frontend: a per-account ledger screen (reached from the Chart of Accounts
     list) and three report screens under a new "Accounting" sidenav group.

  6. **Manufacturing's own posting is deliberately overhead-only.** This app has one pooled
     `Product.stockQuantity`/`costPrice` pair, not separate raw-material/WIP/finished-goods GL
     accounts, so a production run's material consumption and finished-goods output are an exact
     wash within the one Inventory account — nothing to post. Only the overhead actually absorbed
     into a run creates a real movement: Inventory debit / a new `ACCRUED_MANUFACTURING_OVERHEAD`
     liability account credit (a run with zero overhead posts nothing at all). That liability sits
     there until an admin settles it with a manual journal entry once the real bill is actually
     paid (point 2 above) — chosen over inventing a permanently-unreconciled "Applied Overhead"
     contra-expense account, since this app has no separate utility-bill-payment feature yet.

  Reachable from a new "Accounting" sidenav group (same accordion/admin-gated treatment as every
  other module here) with sublinks for Chart of Accounts, Journal Entries, Trial Balance,
  Profit & Loss, Balance Sheet, and Settings.

  Verified with `verify_java.py` (328 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Accounting Reports expansion** (Fourier's own follow-up request, a six-group list: Accounting
  Reports, Journals Reports, Expenses Segmented/Periodic Reports, Incomes Segmented/Periodic
  Reports — 24 report menu items in total, plus two new entry modules he scoped via four
  `AskUserQuestion` choices, all "Recommended": add dedicated Expense/Income entry screens rather
  than deriving them from existing data; add Cost Centers now; a simple Assets report rather than
  a full fixed-asset register; and a general Tax Report/Declaration summary rather than a
  jurisdiction-specific filing format).

  1. **Cost Centers (`CostCenter`)** — a new, deliberately flat lookup (code + name + active, no
     hierarchy — same shape as `Account` itself), optional on every `JournalEntryLine` (nullable
     `costCenter` field), a manual journal entry's own lines (`JournalEntryFormComponent`'s new
     per-line dropdown), and the new Expense/Income entries below (tagged on the expense/revenue
     side of the posting only, never the Cash/Payable/Receivable offset — see
     `AccountingService.postExpense`/`postIncome`'s own javadoc). Admin > Accounting > Cost
     Centers is its own CRUD screen; Admin > Journals Reports > Cost Centers is a separate
     *activity* report (`AccountingReportService.costCenterActivity`) showing each one's own
     debit/credit/net totals for a date range.

  2. **Expenses / Incomes (`Expense`/`Income`)** — the "Add new Expense & Income entry screens"
     choice: a category (an Expense-type/Revenue-type `Account` — no separate category taxonomy),
     a payee (`PayeeType` — Vendor/Staff/Client/Other, backed by a `Supplier`/`AppUser`/`Customer`
     lookup or free text, always snapshotted into `payeeName` so a later rename or deletion never
     changes the record), an amount, a date, and a `paid`/`received` flag that picks the cash side
     of the posting: `true` (the default) hits `CASH` immediately, `false` posts a bill/receivable
     against `ACCOUNTS_PAYABLE`/`ACCOUNTS_RECEIVABLE` instead. Each save posts its own balanced
     journal entry (`AccountingService.postExpense`/`postIncome`, sourceType `EXPENSE`/`INCOME`)
     the same way every other module's own integration does — see `AccountingService`'s class
     javadoc for the full caller list, now including these two. Admin > Accounting > Expenses/
     Incomes are read-only lists (each row already posted) plus a "New" entry form.

  3. **Accounting Reports** (Tax Report, Tax Declaration, Income Statement, Balance Sheet, Profit
     & Loss, Financial Transactions, Cash Flow Report, Assets) — Income Statement is an alias
     route to the existing Profit & Loss screen (the standard alternate name for the same report);
     Balance Sheet/Profit & Loss are the existing point-5 reports, just re-listed under this new
     sidenav group. Financial Transactions (`AccountingReportService.financialTransactions`) is
     every journal entry line across every account in a date range, flattened into one table — a
     multi-account counterpart to the existing single-account Ledger. Cash Flow Report
     (`AccountingReportService.cashFlow`) uses the **direct method** (chosen over indirect, which
     would need non-cash adjustments like depreciation this app doesn't model): every movement of
     the `CASH` account is Operating by default, or Financing when it's the cash side of a manual
     entry whose other line hits `OWNERS_EQUITY` (an owner contribution/draw); Investing is always
     empty (not modeled). Tax Report and Tax Declaration both open the same screen
     (`TaxSummaryDto`) — a general summary, not tied to any tax authority's filing format: tax
     collected, broken down by `OrderLine.taxCode`/`taxRatePercent` (the same per-line tax the POS
     Reports package already tracks, just grouped by rate here), against the `TAX_PAYABLE`
     account's own net ledger movement for the period (this app models no input/purchase tax, so
     there's no separate "tax paid" figure to net against it). Assets is a simple listing of
     Asset-type accounts and their as-of-date balances, reusing the Balance Sheet's own
     computation (`AccountingReportService.assets`) rather than a full fixed-asset register with
     depreciation.

  4. **Journals Reports** (Trial Balance & Nets, Trial Balance, Trial Nets, Ledger, Cost Centers,
     Journals Report, Chart of Accounts Directory) — the first three all read the *exact same*
     `/api/accounting/reports/trial-balance` data (`TrialBalanceComponent` now takes a route-level
     `mode`: `'totals'` hides the Balance column, `'nets'` hides Debit/Credit, `'full'` shows all
     three) rather than three near-identical backend computations. Ledger and Chart of Accounts
     Directory both link straight to the existing Chart of Accounts screen (pick an account there
     for its own Ledger; the account list itself already *is* the directory) — no new backend or
     component needed for either. Journals Report (`AccountingReportService.journals`) is the
     existing Journal Entries list, date-scoped. Cost Centers here is the activity report from
     point 1 above.

  5. **Expenses/Incomes Segmented + Periodic Reports** (by Category/Vendor/Staff/Client, and
     Daily/Weekly/Monthly/Yearly — 8 report menu items per direction, 16 total) — collapsed into
     **two** backend methods (`AccountingReportService.expensesGrouped`/`incomesGrouped`, one
     `groupBy` parameter: `category`/`vendor`/`staff`/`client`/`daily`/`weekly`/`monthly`/`yearly`)
     and **one** frontend component (`ExpenseIncomeReportComponent`, driven by route data) instead
     of sixteen near-duplicate reports — the same "config-driven, one component renders every
     variant" architecture the original POS Reports package established with `REPORT_DEFS`/
     `ReportViewerComponent`, just sized to how simple these particular reports are (one label
     column, one amount column — no category/register filters or Excel/PDF export needed).

  Reachable from a single "Reports" sublink inside the existing "Accounting" sidenav group,
  opening one picker screen (`AccountingReportMenuComponent` — a row of buttons per section, click
  one to open its own report) rather than putting all 24 report links directly in the sidebar.
  This went through three revisions after this same pass's first attempt, which put four *extra*
  sidenav accordion groups next to "Accounting" (one per report section): Fourier's follow-up
  ("Move those report to the Report Menu and accounting Report... same as we did for POS Report")
  asked for exactly the POS Reports picker pattern instead — see that pattern's own
  `ReportListComponent`/`InventoryReportListComponent` — which first landed as *two* sidenav
  sublinks/picker screens ("Accounting Report" for the eight Accounting Reports, "Report Menu" for
  the other 23); Fourier then asked to fold the two together ("Move also Accounting Report to the
  report Menu"), so the standalone `AccountingReportListComponent` screen, its route, and its
  sidenav link were retired and its eight entries (Tax Report, Tax Declaration, Income Statement,
  Balance Sheet, Profit & Loss, Financial Transactions, Cash Flow Report, Assets) became a fourth
  "Accounting Reports" section inside `AccountingReportMenuComponent`, alongside Journals Reports,
  Expenses Reports, and Incomes Reports — all 24 reports under their own section headers on one
  screen, the same "grouped by section" layout `ReportListComponent` itself uses. Fourier then
  asked to rename the sublink/screen from "Report Menu" to just "Reports," which is its current
  name — the route path (`admin/accounting/reports/menu`) and component name
  (`AccountingReportMenuComponent`) were left unchanged, only the user-facing label and the
  screen's own `<h3>` heading changed. The original
  "Accounting" group also gained Cost Centers, Expenses, and Incomes sublinks alongside its
  existing Chart of Accounts/Journal Entries/Settings.

  Verified with `verify_java.py` (355 files, brace/package checks pass) and both `ng build`
  configurations (dev + production) — both pass cleanly.

- **Report drill-down details + Excel/PDF export** (Fourier's own follow-up: "The report show the
  summary, Add all details and Export to Excel and Pdf") — every one of the 24 accounting reports
  (everything under "Reports" except the two that just link to the existing Chart of Accounts
  screen, Ledger and Chart of Accounts Directory) now does two more things beyond its original
  summary:

  1. **Drill-down detail.** A report row that summarizes more than one underlying transaction
     (an account's Trial Balance/Profit & Loss/Balance Sheet/Assets total, a cost center's
     activity, a tax rate's collected amount, the Tax Payable net-movement figure, or an
     Expenses/Incomes grouped-report row) can be clicked to expand a nested table of every
     journal-entry line, sale-order line, or Expense/Income entry that rolled into it, in date
     order. The backend computes this once per report as a `details` list riding along on the
     existing row DTO (a new shared `ReportDetailLineDto` — see its own javadoc for the
     debit/credit convention each report family uses) rather than a second endpoint the frontend
     would have to call per row. Financial Transactions, the Cash Flow Report, and (on-screen)
     the Journals Report needed no new backend data for this at all — their rows already are
     (or already carry, for Journals Report's own `lines`) the individual transactions; they just
     needed the frontend to actually show that instead of only a totals row per entry.
  2. **Export Excel/PDF.** Every report screen gained the exact same "Export Excel"/"Export PDF"
     buttons the original POS Reports package already has, reusing that same generic
     `ReportExportService` (title + column headers + string rows in, an `.xlsx` or `.pdf` out) —
     no second exporter was written. `AccountingReportController` now accepts a `format` query
     param (`json` by default, or `xlsx`/`pdf`) on every report endpoint; a row's own `details`
     are flattened into indented sub-rows right beneath it in the exported file, via a shared
     `withDetails` helper, so the download shows the identical drill-down the on-screen table
     does.

  Ledger and Chart of Accounts Directory were left alone — both are still plain links to the
  existing Chart of Accounts CRUD screen, not a report with rows to expand or export.

  Verified with `verify_java.py` (356 files, brace/package checks pass — one new DTO,
  `ReportDetailLineDto`) and both `ng build` configurations (dev + production) — both pass
  cleanly.

- **2026-10-09 — Inventory completeness pass: warehouses, costing, stock counts, reorder, reservation, in-transit transfers, variants, batch/expiry/serial tracking.**
  Everything below lives under Admin > Inventory unless stated. The single-store limits described in the older Inventory entries and in "Known limitations" are superseded by this section.

  *Multi-warehouse and branches.* Stock is now held per warehouse **location** (`warehouse/`: `Warehouse`, `WarehouseLocation` with types RECEIVING / SALES / STORAGE / DAMAGED, `ProductStock`). `Product.stockQuantity` mirrors the primary warehouse's SALES location, so the POS keeps working unchanged. Purchase orders, stock adjustments, sales-order fulfilment, returns, manufacturing, packaging and restaurant all take a warehouse/location. Stock Transfers move stock between locations (PENDING → CONFIRMED) and, between different branches, between branches (see in-transit below). Stock Movement History has a warehouse filter and now also lists stock transfers (`Stock transfer out/in`, `... (dispatched)`, `... (received)`, `... returned to sender`; reference `Transfer #id`; transfers made before balances were recorded show 0 → 0). A startup guard (`WarehouseService.columnExists`) skips the old `is_default` / `product_stock.warehouse_id` migrations on databases that never had those columns.

  *Cost follows purchases.* Receiving a purchase order re-prices the product by weighted average: (old cost × on hand + received net value) ÷ (on hand + received qty), audited as `PRODUCT_COST_UPDATED`. Inventory Settings gained a **Costing method**: Weighted average (default), Last purchase cost, or Manual (receiving never changes cost). **Landed cost** (freight, duty) can be added on the receive panel; it is spread over the lines by net value, added to cost, and posted Dr Inventory / Cr Cash (if already paid) or Accounts Payable. Record freight there rather than as an expense or it is counted twice; the AP credit has no supplier-ledger entry, so it does not appear in a supplier statement.

  *Stock counts* (`/api/stock-counts`). Start a count for a warehouse (optionally one category) to snapshot every tracked, non-zero product per location; enter counted quantities, submit, then approve (ADMIN or `PERM_APPROVE_STOCK_ADJUSTMENT`). Approval posts one RECOUNT stock adjustment per counted line of (counted − system at start), so stock, audit and accounting behave like any adjustment and sales/receipts made during the count are preserved. Uncounted lines are ignored; approval is all-or-nothing.

  *Reorder levels and requisitions.* `Product.reorderLevel` / `maxStockLevel`. Admin > Reorder lists products whose position (sellable − reserved + open POs + in transit) is at or below the level, with a suggested quantity, and creates a DRAFT purchase requisition.

  *Supplier price lists.* `SupplierPrice` (supplier, product, unit cost, optional validity dates). A new PO form fills line costs from the chosen supplier's current prices.

  *Stock aging and dead stock.* Two reports (age buckets by days since last sale; no sales in 90 days) with category / warehouse / branch filters, built from stock movement history.

  *Sales-order reservation.* Computed, not stored: reserved = Σ(ordered − fulfilled) on confirmed / partially fulfilled orders; available = sellable − reserved. Confirming an order that stock cannot cover returns 409 with the shortage and the screen offers "confirm anyway" (audited `SALES_ORDER_RESERVATION_OVERRIDE`). The POS blocks a sale into reserved stock only when "Allow negative stock" is off. Reservation is global per product, not per location.

  *In-transit transfers.* For transfers between branches "Confirm" dispatches (stock leaves the source, status IN_TRANSIT); the receiving branch records the received quantity (≤ sent). A shortfall is booked as shrinkage in the sending branch; rejecting an in-transit transfer returns it to the sender. Same-branch transfers still move instantly. `EnumConstraintMigration` drops the stale `stock_transfer.status` check constraint that `ddl-auto=update` leaves behind.

  *Product variants.* Variants are ordinary products linked to a template (`variantParentId`, label, attributes). Product detail > Variants generates option combinations (up to 3 options, 200 combinations), copying price, cost, category, tax, reorder and tracking settings; the template is deactivated. Reports are per variant.

  *Batch, expiry and serial tracking* (`tracking/`: `StockBatch`, `StockSerial`, `TrackingService`, `TrackingContext`, `TrackingController`). Per-product mode None / Batch / Serial on the product form. Batches/serials are captured on the PO receive panel and on stock adjustments, and consumed inside `CatalogService.adjustStock` — the single stock chokepoint — so every module is covered. Batch products sell earliest-expiry-first (expired last); serial products use the numbers typed on the POS cart line or sales-order fulfil line, otherwise the oldest in stock; offline-synced sales always auto-pick. Transfers carry batches/serials with the stock (`adjustStockUntracked` + `moveNow` / `dispatch` / `arrive` / `returnToSender`). Reports: Batch Stock, Expiring Stock (expired or within 60 days), Serial Number Register; product detail shows Batches / Serial numbers panels. Stock that was never given a batch/serial (opening balances, count surpluses, customer returns) is "untracked" and never blocked.

  *Smaller changes.* The packaging rule form's quick-add child product now has a Tax rate dropdown.

  Limits: customer returns restock quantity only (a returned serial stays SOLD; re-add it via an adjustment); purchase returns and count shortages auto-pick batch/serial; selling an expired batch is allowed; serial numbers are unique per product, not global; changing a product's tracking mode does not convert existing records.

  Verification: frontend `tsc --noEmit` and `ngc --noEmit` clean. The backend was **not** compiled with Maven (Maven Central is unreachable from the build environment); it was checked with `javac -proc:none` over all sources, which found and fixed one real error. Run `mvn -q -DskipTests compile`, then start the app once so Hibernate creates `stock_count`, `stock_count_line`, `supplier_price`, `stock_batch`, `stock_serial` and the new columns on `product`, `purchase_order`, `store_settings`, `stock_transfer`, `stock_adjustment` and `order_line`.

- **2026-10-12 — Scheduled stock jobs and scheduled report delivery.** Task: the app had no recurring low-stock digest, no emailed reports and no automatic reorder requisitions, even though low-stock alerts and purchase requisitions already existed. (Scheduled jobs that already existed: daily backup, quotation-expiry sweep, recurring expense and recurring invoice templates, subscription billing.)

  *Daily stock jobs* (`inventory/StockAutomationService`, switched on in Admin > Inventory > Settings, which gained two checkboxes and a "run from" time; new `store_settings` columns `low_stock_digest_enabled`, `auto_requisition_enabled`, `stock_job_time`, `stock_job_last_run`). A check runs every five minutes and starts the jobs once per store-local day from the configured time, so a server that was down at that minute catches up later the same day. **Auto reorder** turns `ReorderService.suggestions()` into one DRAFT purchase requisition (note "Created automatically by the daily reorder job", requested by "system"); it never submits or approves, and skips products already on a DRAFT, PENDING_APPROVAL or APPROVED requisition so an unreviewed draft is not repeated next day. **Low-stock digest**: one HTML email to the notifications address with two tables — products below the low-stock threshold and products at/below their reorder level (with suggested quantity) — plus the requisition just drafted; nothing is sent on a day when nothing is low, or when no notifications email / SMTP is configured. A "Run now" button (`POST /api/inventory/stock-automation/run`) runs whichever jobs are on, immediately, using the saved settings. The existing per-event low-stock alert is unchanged.

  *Scheduled reports* (`reportschedule/`, Admin > Settings > Scheduled reports, `/api/report-schedules`, table `report_schedule`). A schedule = report + Excel/PDF + daily / weekly (day of week) / monthly (day 1-28) + "from" time + recipients (blank = notifications email) + optional category / warehouse / branch / days filters. 15 reports can be scheduled (reorder list, stock valuation, stock movement, stock aging, dead stock, expiring stock, batch stock, purchase orders, outstanding payables, sales orders, product sales, category sales, credit sales, outstanding and overdue credit); adding one is a single entry in `ScheduledReportCatalog`. Each entry calls the same `ReportController` method the screen's Excel/PDF buttons call, under a short-lived system ADMIN identity, so the file always matches the screen. Date-range reports cover the period that just ended: yesterday (daily), the previous 7 days (weekly), the previous calendar month (monthly). Each schedule shows its last result (OK / FAILED / SKIPPED, with the reason), can be paused, edited, deleted or sent immediately ("Send now"), and every run is audited (`REPORT_SCHEDULE_RUN`). Limits: a schedule missed because the server was off for the whole due day is not sent later; only reports that already have an Excel/PDF export can be scheduled; the sender is the SMTP account in Email settings.

  Verification: frontend `tsc --noEmit` and `ngc --noEmit` clean. Backend not compiled with Maven; `javac -proc:none` over all sources shows only missing-dependency / Lombok noise and no arity or type errors in the new code. Run `mvn -q -DskipTests compile` and start the app once to create `report_schedule` and the four `store_settings` columns.

- **2026-10-12 — Subscription daily job: settings, reminders, unpaid handling, digest.** Task: "Add also Job Scheduled for subscription module". Subscriptions already had a fixed 06:30 billing sweep; it is now one configurable daily job (`subscription/SubscriptionAutomationService`, settings at Admin > Subscriptions > Job settings, `GET/PUT /api/subscriptions/automation`, `POST /api/subscriptions/automation/run`). A check runs every five minutes and starts the job once per store-local day from the chosen time (default 06:30, **now in the store time zone, UTC unless changed in System settings**, instead of server time); a server that was off at that minute catches up the same day. Steps, in order:

  1. **Unpaid handling** (off by default): an ACTIVE subscription holding an ISSUED invoice that is still unpaid more than N days after its due date (subscription invoices have none, so the invoice date) is paused or cancelled; audited `SUBSCRIPTION_AUTO_PAUSED` / `SUBSCRIPTION_AUTO_CANCELLED`. Done first so a subscription just paused is not billed again today. Pause can be undone with Resume; cancel cannot.
  2. **Billing** (on by default, so existing behaviour is unchanged): every due ACTIVE subscription is billed in its own transaction (`SubscriptionService.billDue`), so one failure no longer risks the others; failures are audited `SUBSCRIPTION_BILLING_FAILED`.
  3. **Reminders** (off by default): N days before a billing date, an email goes to the customer (when they have an email) and/or the notifications address; each billing date is announced once (`subscription.reminder_sent_for`).
  4. **Digest** (off by default): one email to the notifications address listing billed, failed ("needs attention"), paused/cancelled and reminder count; not sent on a day when nothing happened.

  "Run now" runs the job with the saved settings and counts as today's run (the scheduled run is then skipped). The last run's one-line result is shown on the job settings screen and on the Subscriptions list. New `store_settings` columns: `subscription_billing_enabled`, `subscription_job_time`, `subscription_job_last_run`, `subscription_job_last_summary`, `subscription_reminder_enabled`, `subscription_reminder_days`, `subscription_reminder_customer`, `subscription_reminder_store`, `subscription_digest_enabled`, `subscription_overdue_action`, `subscription_overdue_days`; new `subscription.reminder_sent_for`. The old `@Scheduled generateDueInvoices` method was removed. Reminders need Email settings (SMTP) to be configured.

  Verification: frontend `tsc --noEmit` and `ngc --noEmit` clean; backend not compiled with Maven (`javac -proc:none` shows only missing-dependency / Lombok noise). Run `mvn -q -DskipTests compile` and start the app once so the new columns are created.

- **2026-10-12 — Custom roles (Manager, Accountant and any role you build).** Task: roles were binary (ADMIN/CASHIER) plus four narrow permission escapes, so there was no middle tier such as a Manager, or an Accountant who can see the financial reports without full admin rights over users and settings. Choices: roles are built by the admin (Staff > Roles); Manager and Accountant ship as ready-made, editable roles; existing ADMIN and CASHIER accounts are unchanged.

  **Model.** `ADMIN` / `CASHIER` stay the base role. A staff account can additionally hold one **custom role** (`user/CustomRole`, table `custom_role`, grants in `custom_role_grants`; `app_user.custom_role_id`). A role is a name plus, per **access area**, *No access / View / Manage* (Manage includes View): **Sales and customers** (customers, quotations, sales orders and returns, registers, shifts), **Billing and payments** (invoices, bills, customer and supplier payments, credit notes, refunds, recurring invoices and expenses), **Inventory and purchasing** (products, suppliers, purchasing, stock, warehouses, packaging), **Operations** (manufacturing, restaurant, subscriptions, help desk), **Accounting** (chart of accounts, journals, expenses, incomes, cost centres) and **Reports and dashboard** (view only; includes the financial reports). Grants are stored as plain text (`INVENTORY_MANAGE`), not an enum column, so adding an area later never trips a stale database check constraint. The role only adds access: it is ignored for an Admin, and an inactive role grants nothing.

  **Ready-made roles** (created once on first start, flag `store_settings.custom_roles_seeded`; deleting them never brings them back): **Manager** = Sales manage + Inventory manage + Operations manage + Reports view; **Accountant** = Accounting manage + Billing manage + Sales view + Inventory view + Reports view. Neither can open Staff, Settings, Backup, Audit log, Data import/export, Apps or Scheduled reports (still Admin-only).

  **Enforcement.** `AppUserPrincipal` turns the grants into authorities `AREA_<AREA>_VIEW`, `AREA_<AREA>_MANAGE` and `AREA_ANY` (any valid grant: lets role holders read shared lookups such as suppliers, warehouses, branches and tax rates). Controllers that were `hasRole('ADMIN')` now read `hasRole('ADMIN') or hasAuthority('AREA_X_VIEW')` at class level, and each write method carries `... AREA_X_MANAGE`; method-level approval rules (`PERM_APPROVE_EXPENSE`, `PERM_APPROVE_STOCK_ADJUSTMENT`, `PERM_APPROVE_PURCHASE_REQUISITION`, `PERM_APPROVE_STOCK_TRANSFER`, `PERM_CONFIRM_RESTAURANT_WASTAGE`, `PERM_OVERRIDE_DISCOUNT_CAP`) are untouched, so approving stays a separate per-person grant (maker-checker). Accounting reports accept Reports-view **or** Accounting-view; help-desk reports accept Reports-view or Operations-view. "Own data only" checks that used to test `role == ADMIN` (customer credit fields, cashier-session lookups) now also pass for Sales manage. Branch restrictions on the staff account still apply to role holders.

  **API.** `/api/roles` (list, create, update, delete — delete refused while staff hold the role), `/api/roles/areas`; staff create/update take `customRoleId` (update also `customRoleProvided: true` so null clears it); login and the staff list return `areas` and `customRoleName`.

  **Frontend.** `core/access.ts` is the single path-prefix → area table; `adminGuard` lets a non-admin through when their role covers the screen (`/new`, `/edit`, `record-payment` need Manage) and falls back to `/sessions` otherwise; the sidebar shows only the groups and links the user can open (`ShellComponent.canSee` / `showGroup`); after login a role holder lands on their first available area. Staff > Roles (list + form with a None / View / Manage picker per area); the staff form has a *Custom role* select for Cashier accounts; the staff list shows it.

  Limits: write buttons are still visible to a View-only user (the server answers 403 and the screen shows the error); a user holds one custom role; areas are coarse by design, not per-screen; the Admin-only screens listed above cannot be delegated.

  Verification: frontend `tsc --noEmit` and `ngc --noEmit` clean; backend not compiled with Maven (`javac -proc:none` shows only missing-dependency / Lombok noise). Run `mvn -q -DskipTests compile` and restart the backend: it creates tables `custom_role` and `custom_role_grants` and columns `app_user.custom_role_id` and `store_settings.custom_roles_seeded`.

- **2026-10-12 — Colour themes (per user).** Task: "put a theme switcher in Settings so each user can choose between a few of these". Eleven ready-made themes plus a **Custom** one: dark — Navy (the original default), Charcoal, True black, Slate, Forest, Plum, Warm brown; light — Clean white, Warm paper, Soft grey, Sky blue. **Custom** (follow-up request: "Do both (blue sky)"): on the same screen a user picks a page background and an accent with colour pickers or hex codes; `customVariables()` in `core/theme.service.ts` derives the cards, borders, text and tab colours, and chooses a light or dark layout from the background's brightness (a light one reuses the light status colours); a warning shows when the accent has less than 3:1 contrast on the background. The two colours are stored per user (`pos.theme.custom.<id>`) and applied as inline CSS variables on `<html>`. Every user picks their own on **Settings > Appearance** (`/settings/appearance`, open to all logged-in users, so cashiers reach it too) or from the **🎨 Theme** button in the top bar. `core/theme.service.ts` applies it as `<html data-theme="...">`; the variables for each theme are `:root[data-theme='...']` blocks in `styles.scss` (dark themes only change the background tones; the light ones also flip text, borders, status colours and the accent). The choice is remembered per user id in the browser's local storage (key `pos.theme.user.<id>`), so two people sharing a till each get their own look on login; the login screen shows the last theme used on that browser. Adding a theme = one entry in `THEMES` plus one CSS block. A few previously hard-coded colours (POS tabs, outline-danger button, secondary badge) now use variables so they follow the theme. Receipts, purchase orders and printed reports stay white on purpose, and the customer-facing display keeps its own dark look. Limit: stored per browser, not on the server, so a user signing in on another device picks the theme again there. Verification: frontend `ngc --noEmit` clean; no backend change.

- **2026-10-13 — Hotel management (reservations, front desk, folio, housekeeping, reports), integrated with POS, Restaurant and Accounting.** Task: "add hotel management which will also be integrated with POS, Restaurant, Accounting". Choices: scope = reservations and availability, front desk and guest folio, housekeeping and maintenance, hotel reports; "Charge to room" from Restaurant, POS and room service, with the folio settled at check-out; Accounting posting is **configurable** (three modes); one nightly rate per room type (extra-guest charge and a per-room rate override are optional).

  **Model** (package `hotel/`, all tables new): `room_type` (nightly rate, guests included, max guests, extra-guest rate), `room` (number, type, optional own rate, housekeeping status CLEAN/DIRTY/CLEANING/INSPECTED, `out_of_order` flag), `guest`, `hotel_booking` (one record from reservation to departure: BOOKED → CHECKED_IN → CHECKED_OUT, or CANCELLED / NO_SHOW), `folio_charge` (ROOM per night, RESTAURANT, POS, ROOM_SERVICE, EXTRA; net + tax = amount; voidable) and `folio_payment` (DEPOSIT / PAYMENT / REFUND), `maintenance_request` (a request that "takes the room out of service" sets the room out of order until it is done), and one `hotel_settings` row. A night's price is the rate + (guests above the included number) × the extra-guest rate; tax is the percentage in Hotel > Settings, added on top.

  **Availability.** Per room type and night: sellable rooms (in use, not out of order) minus overlapping BOOKED / CHECKED_IN stays; a guest staying past the planned departure still holds the room through today. A booking can name a room or leave it for check-in; a room can never overlap another booking.

  **Front desk.** Check-in needs arrival date ≤ today, a vacant in-service room and (unless ticked "check in anyway") a clean/inspected room; the first night is charged at check-in. Check-out adds the remaining nights, posts the room revenue, records the guest's payments (split payments allowed), applies held deposits, refunds any overpayment, marks the room DIRTY, and refuses (rolling everything back) if the bill is not covered. Also: walk-ins (book + check in in one step), move room, change departure date, extra charges (negative = discount), void a line, deposits, cancel / no-show with the deposit refunded or kept. The **night audit** (`NightAuditService`, a five-minute tick like the stock jobs; once a day after the time set in Hotel > Settings, with a "Run now" button) charges in-house guests for nights that have ended and sets occupied rooms back to dirty; a night is never charged twice (even a voided night).

  **Accounting modes** (Hotel > Settings; changeable only when no booking is open). One rule serves all three: room revenue posted so far is compared with the non-voided ROOM lines and only the difference is posted. *Every night* — each night: DR Accounts Receivable ("guest ledger") / CR Room revenue + Tax payable. *At check-out* — the same entry for the whole stay when the guest leaves. *At booking and check-out* — at booking DR Accounts Receivable / CR Unearned room revenue (kept equal to the planned stay, so it follows date, rate and guest changes and is reversed on cancel / no-show); at check-out the unearned amount is released into revenue. Always, when money moves: deposit DR Cash / CR Guest deposits; payment DR Cash / CR Accounts Receivable; deposit applied at check-out DR Guest deposits / CR Accounts Receivable; refund DR Guest deposits (and/or Accounts Receivable) / CR Cash; a kept deposit DR Guest deposits / CR Room revenue; manual extra charges post as revenue when added (and are reversed on void). New chart accounts (added to existing stores by the usual backfill): Rwanda 2850 Guest deposits, 2860 Unearned room revenue, 4010 Room revenue; Chad 4191, 477, 706.

  **Integration.** *Charge to room* is a payment method (`payment_method_entity.room_charge`, default code `ROOM`, "Charge to room"); it is created and switched on/off together with the module (Hotel > Settings) and is flagged on the Payment methods screen. At the POS checkout and the restaurant table-order checkout, picking it shows a list of checked-in guests (`GET /api/hotel/in-house`, open to any signed-in user); the amount tendered on it becomes a folio line (`HotelFolioService.chargeToRoom`) and is posted to Accounts Receivable instead of Cash (`sale_order.room_charge_amount` / `room_booking_id`; the restaurant order keeps `room_booking_id`). **Room service**: Restaurant > Takeaway/Delivery > Room service opens an order for an in-house guest (`restaurant_order.room_booking_id`, `room_label`), shown as "Room service — 101 — Jane" on the kitchen display; at checkout it is normally charged to the room. Lines that came from the POS / Restaurant / room service cannot be voided from the folio (reverse them in that module). Offline POS sales cannot use Charge to room.

  **Access.** New access area **Hotel** (`AREA_HOTEL_VIEW` / `AREA_HOTEL_MANAGE`, assignable on Staff > Roles). Hotel > Settings is admin-only; reading hotel data needs View, changes need Manage; housekeeping status can be updated with View.

  **API** (`/api/hotel/...`): `settings`, `night-audit/run`, `in-house`, `dashboard`, `reports/occupancy`, `room-types`, `rooms` (+ `/{id}/housekeeping`), `guests`, `bookings` (+ `/{id}/check-in`, `check-out`, `move`, `extend`, `cancel`, `no-show`, `folio`, `charges`, `charges/{id}/void`, `payments`), `walk-in`, `availability`, `free-rooms`, `grid`, `front-desk/arrivals|departures|in-house`, `maintenance`.

  **Frontend.** Sidebar group *Hotel* (shown once the module is on; an admin always sees Settings): Front desk (today's numbers, arrivals / in-house / departures), Bookings (+ new booking, walk-in, booking detail with the whole folio and all actions, printable guest invoice), Reservation chart (rooms × 3 weeks), Housekeeping board, Maintenance, Guests, Rooms, Room types, Reports (occupancy %, ADR, RevPAR, revenue by room type), Settings. Files: `app/hotel/*`, `core/api/hotel-api.service.ts`.

  **Reports.** Occupancy / ADR / RevPAR are built from the ROOM lines on bills (before tax), so they agree with the invoices. Hotel figures also reach Accounting reports through the journal entries above.

  Limits: one rate per room type (no seasons / rate plans); no online booking page, channel manager or card-terminal integration; availability counts rooms, not future maintenance dates; Charge to room needs a checked-in guest; the accounting mode cannot change while bookings are open; tax is one percentage for rooms and extras (restaurant / POS lines carry the tax of their own module).

  Verification: backend compiled with `javac` against the application's dependencies (Lombok getters/setters expanded by a script, since Lombok is not available in the check environment) — no errors; frontend `ngc --noEmit` clean. Please also run `mvn -q -DskipTests compile` and restart: new tables `room_type`, `room`, `guest`, `hotel_booking`, `folio_charge`, `folio_payment`, `maintenance_request`, `hotel_settings`; new columns `payment_method_entity.room_charge`, `sale_order.room_charge_amount`, `sale_order.room_booking_id`, `restaurant_order.room_booking_id`, `restaurant_order.room_label`. Then open **Hotel > Settings**, switch the module on, choose the accounting mode and tax, and add room types and rooms.

- **2026-10-13 — Settings > Modules (turn optional modules on or off).** Task: "the module management under settings where the modules can be activated / turned on or off". Admin-only screen *Settings > Modules* lists **POS**, **Hotel**, **Restaurant**, **Manufacturing**, **Subscriptions**, **Help Desk** and **Expenses** (expenses + recurring expenses) — all **off by default** (menu hidden), so an admin switches on only what the business uses. On an existing installation these menus disappear after the update until switched on here; the data is untouched. Switching a module off hides its menu group and blocks its screens (a typed URL sends an admin back to Settings > Modules, anyone else to Sessions); **no data is deleted** and everything returns when it is switched on again. POS, sales, purchasing, inventory, accounting and staff are core and always on.

  **Hotel** keeps its flag in `hotel_settings.enabled`, so the switch here and the one in Hotel > Settings are the same thing; switching it also turns the "Charge to room" payment method on or off (POS checkout, Restaurant checkout, room service). After switching Hotel on, the screen shows the set-up order: room types first, then rooms, then hotel settings. Hotel cannot be switched off while there are booked or in-house guests. The Hotel menu is now hidden completely while the module is off (admins switch it on in Settings > Modules).

  **Model/API.** New table `module_setting` (one row per module an admin has switched; no row = the module's default). `GET /api/modules` (any signed-in user — the sidebar needs it), `PUT /api/modules/{key}` with `{"enabled": true|false}` (admin; written to the audit log). Backend package `modules/`; frontend `admin/modules.component.ts`, `core/api/module-api.service.ts`, `moduleGuard` in `core/auth.guard.ts` (applied by path prefix at the bottom of `app.routes.ts`), sidebar in `shared/shell.component.ts`. If the state cannot be loaded, every module counts as off.

  **Integrated modules switch together.** A module can name the modules it works with (`ModuleKey.requires()`): switching it on switches those on too, and they cannot be switched off while it is on. Today: **Hotel needs Restaurant** (room service and "Charge to room" at the restaurant till), so turning Hotel on turns Restaurant on, and Restaurant stays locked on ("Needed by Hotel") until Hotel is switched off. This also applies to the switch in Hotel > Settings. POS, inventory and accounting are core, so they are always on. `PUT /api/modules/{key}` now returns the whole module list.

  **Public home page.** The home page's "Reserve a table" and "Help Desk" cards only show while Restaurant / Help Desk are on (`GET /api/public/modules`, no login, returns only those two on/off flags). The public pages `/reserve` and `/helpdesk` redirect to the home page and their `/api/public/reservations` and `/api/public/helpdesk` endpoints answer "not found" while the module is off. With both off, the home page shows only the Staff Login.

  **Expenses off.** Besides the menu and screens, the daily job that generates recurring expenses does nothing while Expenses is off (due templates catch up when it is switched on), and the *Expenses Reports* section is removed from Accounting > Reports and its report screens (`/admin/accounting/reports/expenses-*`) are blocked. Existing expense journal entries still appear in the Accounting reports (income statement, ledger, etc.).

  **POS is now a module; Sales, Purchases and Accounting are core.** The Modules screen lists *Sales*, *Purchases* and *Accounting* as "Core / Always on" (they cannot be switched off; `PUT /api/modules/{key}` answers 409 for them) and **POS** as a normal module, **off by default**. POS covers the POS menu: Registers & Terminal, Sessions, the terminal and checkout, Returns, Receipts, Shifts and POS Reports. While POS is off those screens are blocked (typed URLs go to Settings > Modules for an admin, to Settings > Appearance for anyone else), and the login landing page adapts: an admin lands on the Dashboard instead of Registers, a sales user on Sale orders. **Hotel now needs POS as well as Restaurant** (Charge to room at the POS), so switching Hotel on switches both on. Products/stock, customers, suppliers, staff and settings are shared foundations and are not modules. Note: there was never an "activate accounting" switch — accounting has always been on; the only accounting set-up is the one-time chart-of-accounts country wizard, which stays. A stale hint on the Journal entries screen that mentioned "once Accounting is enabled (Accounting > Settings)" was removed.

  Limits: a module that is not activated is hidden/blocked only — scheduled jobs of the other modules (e.g. daily subscription billing) keep running; Restaurant buttons elsewhere (e.g. POS) are not hidden. (Switching a module off is no longer possible — see "Modules are permanent once activated" below.)

- **2026-10-13 — Purchases reports.** Task: "In the purchases module add reports related to purchases". New *Purchases > Reports* (`/admin/purchases/reports`, same picker pattern as Sales > Reports; Reports area; every report has date range + branch filters and Excel/PDF export). Reports: **Purchase Orders** and **Outstanding Payables** (moved here from the Inventory reports picker), and new **Purchases by Supplier**, **Purchases by Product**, **Purchases by Category** (orders, quantities ordered/received, net cost, tax, total, paid, outstanding), **Bills**, **Purchase Returns**, **Supplier Payments**, and **Payables Aging** (what each supplier is owed today in 0-30 / 31-60 / 61-90 / over 90 day buckets, aged from the bill date once a bill exists, otherwise the order date; snapshot, no date range). "Purchases" means confirmed purchase orders (sent, partly or fully received) — drafts and cancelled orders are excluded; amounts are the same tax-inclusive figures the Purchase Order screens show. All seven new reports can also be emailed on a schedule (Settings > Scheduled reports, section Purchasing).

  Code: backend `reports/PurchaseReportService.java`, `reports/dto/PurchaseReportRows.java`, endpoints `GET /api/reports/purchases-by-supplier|purchases-by-product|purchases-by-category|purchase-bills|purchase-returns|supplier-payments|payables-aging` in `ReportController`, entries in `reportschedule/ScheduledReportCatalog`; frontend `core/report-defs.ts` (new section `Purchases`), `admin/reports/purchase-report-list.component.ts`, a *Reports* link in the Purchases menu. The shared report viewer (`/admin/reports/:key`) is no longer blocked when the POS module is off (only the POS reports themselves are).

- **2026-10-13 — More Sales reports.** Task: "For Sales Reports add more reports as we have on purchases". *Sales > Reports* now has, besides **Sales Orders**: **Sales by Customer**, **Sales by Product**, **Sales by Category** (orders, quantities ordered/fulfilled, net, tax, total, paid, outstanding), **Invoices**, **Credit Notes** (voided invoices, by void date, with the reason and how the payment was handled), **Sale Order Returns**, **Customer Payments**, and **Receivables Aging** (what each customer owes today from invoices and sale orders, in 0-30 / 31-60 / 61-90 / over 90 day buckets, aged from the invoice date or, without an invoice, the order date; snapshot). Same filters (date range + branch), Excel/PDF export and e-mail scheduling as the Purchases reports. "Sales" means confirmed sale orders (confirmed, partly or fully fulfilled). The POS sales reports are unchanged and stay under POS; POS credit sales are not part of Receivables Aging (the POS credit reports cover them).

  Code: backend `reports/SalesReportService.java`, `reports/dto/SalesReportRows.java`, endpoints `GET /api/reports/sales-by-customer|sales-by-product|sales-by-category|sales-invoices|sales-credit-notes|sales-order-returns|customer-payments|receivables-aging`, entries in `ScheduledReportCatalog`; frontend: `core/report-defs.ts` (section `Sales`); the existing Sales reports picker lists them automatically.



- **2026-10-13 — Modules are permanent once activated.** Task: "the activated module can not be deactivate". This **replaces the on/off switch** described in the *Settings > Modules* entry above: Settings > Modules now shows **Activate** for a module that is not activated yet (with a confirmation that it cannot be undone and which integrated modules come along), and **"Active — can't be turned off"** afterwards; the three core modules show "Core / Always on". Backend: `ModuleService.setEnabled(key, false)` answers 409 for an active module (a no-op for one that was never activated); the Hotel open-bookings check and the "needed by another module" check are gone because nothing can be switched off any more. Hotel > Settings: the Hotel checkbox reads "Activate the Hotel module" and is locked once Hotel is active. Activating a module still activates the modules it needs (Hotel brings POS and Restaurant); audit entries are `MODULE_ENABLED` only. The rest of that entry (hidden menus, blocked screens, public pages, POS-aware landing) still applies to modules that are not activated yet. To undo an activation by mistake an administrator would have to delete the `module_setting` row (or set `hotel_settings.enabled` to false for Hotel) in the database.

- **2026-10-13 — Assets Management module (fixed assets).** Task: "Assets Management Module (of course keep our design to be activated)". A new optional module **Assets Management**, off by default and activated (permanently) in Settings > Modules. Once active, the sidebar gets an **Assets** group (Asset register, New asset, Categories, Reports), placed just before Accounting; the screens live under `/admin/assets` and use the *Accounting* access area (reports: *Reports* or *Accounting*), so custom roles control them like other accounting screens.

  **What it does.** *Categories* (name, depreciation method, useful life in months, salvage % of cost) give new assets their defaults; each asset keeps its own copy. An *asset* has tag (auto `FA-0001`…), name, category, purchase date, cost, salvage value, useful life, method, start date, location, assigned to, serial number, supplier and notes. **Depreciation** is monthly: *straight line* = (cost − salvage) ÷ life each month; *declining balance* = book value × 2 ÷ life each month, never below salvage; a full month is charged for the start month, and the last month takes whatever is left so the asset ends exactly at its salvage value (status becomes *Fully depreciated*). It is charged by the **daily job** (05:30, only for months that have ended, only while the module is active) or by **Run now** on the register (up to a chosen date, not in the future); months already charged are never charged twice, and an asset registered with an old purchase date is caught up month by month. All charges of one month are posted as one journal entry. **Disposal** (sale or write-off) first charges the full months before the disposal month, then posts cash received, clears the asset's cost and accumulated depreciation, and books the gain or loss; the asset becomes *Disposed* and read-only. An asset can be deleted only while it has no depreciation, purchase posting or disposal. Cost, purchase date and depreciation terms lock once depreciation or the purchase posting exists.

  **Accounting.** New well-known accounts, added to every store's chart by the normal start-up top-up (Rwanda / Chad): *Fixed Assets* 1500 / 24, *Accumulated Depreciation* 1590 / 284, *Depreciation Expense* 5950 / 681, *Gain / Loss on Asset Disposal* 5960 / 81 (gain = credit, loss = debit). Registering an asset can post the purchase (*Fixed Assets / Cash* or *Fixed Assets / Accounts Payable*) — default "don't post", because the purchase is often already in the books. An asset that is already part-depreciated can be entered with an "opening depreciation" amount (not posted — use a journal entry if it is needed in the ledger). Journal sources: `ASSET_ACQUISITION`, `ASSET_DEPRECIATION` (source id = year×100+month), `ASSET_DISPOSAL`.

  **Reports** (*Assets > Reports*, Excel/PDF like the others; also schedulable by email): **Asset Register**, **Assets by Category**, **Depreciation Schedule** (date range), **Asset Disposals** (date range).

  **Code.** Backend package `assets/` (`AssetCategory`, `FixedAsset`, `DepreciationEntry`, `FixedAssetService`, `AssetDepreciationService`, `AssetController` at `/api/assets`), `AccountService` / `AccountingService` (accounts and `postAssetAcquisition|postAssetDepreciation|postAssetDisposal`), `reports/AssetReportService.java`, `reports/dto/AssetReportRows.java`, endpoints `GET /api/reports/asset-register|assets-by-category|depreciation-schedule|asset-disposals`, entries in `ScheduledReportCatalog`. Frontend `admin/assets/*` (list, form, detail, categories), `admin/reports/asset-report-list.component.ts`, `core/api/asset-api.service.ts`, the *Assets* section of `core/report-defs.ts`, `ModuleKey` `ASSETS`, route prefix `admin/assets` in `MODULE_PREFIXES`. New tables (`asset_category`, `fixed_asset`, `depreciation_entry`) are created on start-up (`ddl-auto=update`), so restart the backend.

  **Register from a purchase (follow-up).** *Assets > From purchases* (`/admin/assets/from-purchase`, button on the register too) lists purchase-order lines that have received goods. Pick a line, a category and a quantity: one asset is created per unit, each costing the line's **net** unit cost (after discount, without tax; input VAT stays in Input VAT Recoverable). The goods receipt already debited Inventory, so the registration only **moves the cost from Inventory to Fixed Assets** (journal source `ASSET_FROM_PURCHASE`) — nothing is counted twice, and the asset keeps the purchase-order number, supplier and date (its purchase counts as posted, so cost and date lock). The product on the line must have **Track stock switched off** (otherwise the units would also stay in the stock count and valuation); the screen shows such lines as "Tracks stock" and the API answers 400. You cannot register more units than were received, in total across registrations of the same line. Landed cost on the purchase order is not included in the asset's cost. A later purchase return on the same line is not reflected in the asset (dispose of it instead).

  **Prorate the first month (follow-up).** Category and asset option *Prorate the first month by days* (default off, copied from the category when an asset is registered): the first charge is the normal monthly amount × (days from the start date to month end ÷ days in that month); the final month absorbs the difference, so the asset takes one extra month to reach salvage value. Example: cost 1,200, 12 months, start 11 January → 67.74 for January, 100.00 for the next 11 months, 32.26 in the 13th month.

  **Branches and transfers (follow-up).** Every asset belongs to a branch (the *Branch* picker on the New asset form appears when there is more than one active branch; an asset with no branch, or registered before branches existed, belongs to head office; assets from a purchase take the purchase order's branch). Users restricted to certain branches see and change only those branches' assets (`BranchAccessService`). The register has a branch filter and column, and its totals follow the filter. **Transfer** (button on the asset page, `POST /api/assets/{id}/transfer`) moves an asset to another branch and/or a new location, with date, new "assigned to" and reason; the history is listed on the asset page. Depreciation is first charged up to the month before the transfer month, so those months stay with the old branch and later ones go to the new one: each monthly depreciation entry is posted per branch (`journal_entry.branch_id`), and each depreciation row remembers the branch it was charged to. **Accounting is not re-posted on a transfer** — earlier entries keep the old branch and no inter-branch entry is made (the same as stock transfers), so a branch's Fixed Assets / Accumulated Depreciation balances do not move with the asset. Reports: all Assets reports have a branch filter and a Branch column; new **Asset Transfers** report (moves into or out of the chosen branch), also schedulable. New columns/table: `fixed_asset.branch_id`, `depreciation_entry.branch_id`, `asset_transfer` — restart the backend.

  Limits: straight-line and declining-balance only; no inter-branch journal entry on transfer; no revaluation or impairment.


- **2026-10-13 — HR modules: Employees, Organizational Structure, Attendance & Leaves, Contracts, Payroll.** Task: "I want also to have the following module: 1.Employees Management 2.Organizational Structure 3.Attendance & Leaves 4.Contracts 5.Payroll". Five optional modules, off by default and activated (permanently) in Settings > Modules. **Employees is the base:** the other four need it (activating one switches Employees on too), and **Payroll also needs Contracts and Attendance & Leaves** (all three come along). The sidebar gets one group per active module, placed just before Assets; the screens live under `/admin/hr/...`. New tables are created on start-up, so restart the backend.

  **Employees** (*Employees > Employees*). Employee number (auto `EMP-0001`…), personal details, national ID, emergency contact, hire date, employment type, job title, department, position, "reports to", branch (picker appears with more than one branch), bank and tax/social-security numbers, an optional **staff login** link (lets that person clock in themselves), and **Track attendance for pay** (see Payroll). Statuses: Active, Inactive, Left the company. *Employee left* records the last day and ends their active contract the same day; *Re-hire* brings them back; delete only works for an employee with no contracts, leave or attendance. Staff limited to some branches only see their branches' employees.

  **Organizational Structure.** *Departments and positions* (departments nest, have a manager; positions have a planned headcount and show how many are filled) and an **Organization chart** (department tree and the "reports to" tree).

  **Attendance & Leaves.** *Attendance*: a **My day** card to clock in / clock out (needs the login to be linked to an employee), manual add/correct of a day, daily records and a per-employee summary (expected days, present, late, absent, leave days, hours, overtime). Status late/present and hours/overtime are worked out from *Settings* (work start 08:00, 15-minute grace, 8-hour day, Mon–Fri, overtime x1.5 — all editable). *Leave*: requests with approve / reject / cancel, working days counted without weekends and **public holidays** (entered under Settings), a check for overlap and for the yearly balance, and a **Balances** tab. Ready-made leave types (Annual 18 days, Sick 30, Maternity 98, Paternity 4, Unpaid) can be edited, added or switched off; **unpaid leave is deducted by payroll**. A request cannot span two years; entitlements are not prorated for people hired during the year.

  **Contracts** (*Contracts*). Type (permanent, fixed term, probation, casual, internship), dates, monthly basic salary, and monthly **allowances** (taxable or not) and **fixed deductions** (e.g. loan repayment). A contract starts as a **draft**; *Activate* makes it live (an employee cannot have two active contracts covering the same dates), *Renew* makes a new draft with the same terms, *Terminate* ends it early. Contracts that reach their end date are marked **Expired** by a daily job, and the list warns about contracts ending within 60 days.

  **Payroll** (*Payroll > Payroll runs*, *Rules & tax bands*). *Generate* works out a month for everyone with a contract: basic salary (prorated by calendar days when the contract or employment starts/ends mid-month), allowances, **unpaid leave** (daily rate = salary / working days of the month), and — only for employees flagged *Track attendance for pay* — **absent days** and **overtime**; then the statutory **contributions**, **income tax (PAYE)** on the taxable pay (progressive bands; contributions marked "reduces taxable pay" come off first), fixed deductions after tax, and rules on the net pay (e.g. CBHI) last. Employer contributions are added as employer cost. A draft can be recalculated; **Approve and post** locks it and posts to Accounting; **Mark salaries as paid** settles the salaries owed. Each employee has a printable **payslip**. Payroll covers all branches, so users limited to some branches cannot open it.

  **Ready-made rules (Rwanda and Chad) — confirm with an accountant.** The first time payroll is opened, rules for the store's chart country are loaded; every rate, cap, tax band and switch is editable under *Rules & tax bands*, and *Add missing ready-made rules* restores any you deleted. **Rwanda:** PAYE per month 0–60,000 at 0%, to 100,000 at 10%, to 200,000 at 20%, above at 30%; employee RSSB pension 3% and maternity 0.3% (reduce taxable pay); CBHI 0.5% of net pay; employer pension 5.3%, maternity 0.3%, occupational hazards 2%; RAMA 7.5% / 7.5% added but off. **Chad:** CNPS employee 3.5% (reduces taxable pay) and employer 16.5%, both capped at 500,000 a month; employer payroll tax 7.5% and FONAP 1.2%; **the IRPP income-tax bands are not pre-filled** — enter them under *Rules & tax bands* or PAYE stays 0 (the page warns). Sources disagreed on several rates, so treat all of these as a starting point, not legal advice. Tax bands can be set as monthly or yearly amounts.

  **Accounting.** New well-known accounts added to the chart by the normal start-up top-up: *Salaries Payable* 2350 / 422 and *Employer Contributions Expense* 5410 / 664 are new; the existing PAYE 2300 / 4472, RSSB-CNPS 2500 / 431, health & other payroll payables 2600 / 4473 and Salaries & Wages Expense 5400 / 661 accounts are now used automatically. **Approve** posts, per branch of the employee: Dr Salaries expense (gross) + Dr Employer contributions expense; Cr PAYE payable, Cr Social security payable, Cr Health / other payroll payable, Cr Salaries payable (net). **Paid** posts Dr Salaries payable / Cr Cash.

  **Access and reports.** Two new access areas for custom roles: **Human resources** (employees, org, attendance, leave, HR reports — no salaries) and **Payroll and contracts** (contracts, payroll, salary reports); admins have both. *Employees > HR reports* offers Employee Directory, Headcount by Department, Leave Balances, Leave Requests, Attendance Summary, Contracts Expiring, Payroll Register and Payroll Summary (Excel/PDF; also schedulable by email); the last three need the Payroll area.

  **Payslips by email and the bank transfer file (follow-up).** On an **approved or paid** payroll the run page has a *Pay the salaries* box: **Bank transfer file (CSV / Excel)** lists every employee with net pay above zero in the usual payroll-sheet layout — **No, SURNAME, NAME, NET SALARY, ACCOUNT NO, BANK NAME** — in alphabetical order of surname, with whole-unit amounts (rounded) and account numbers kept as text so leading zeros and dashes survive (Excel is the better choice; a CSV opened in Excel can drop leading zeros). The bank column is whatever is typed on the employee record, so enter it the way your bank sheet writes it (e.g. `BK`). Employees without a bank account are flagged on the page. **Email all payslips** sends each employee an email with their payslip in the body and as a PDF attachment, to the email address on their employee record (SMTP must be set up under Settings > Email); the result says how many went out, who has no email address and who failed. A single payslip can be emailed from its own page (*Email to employee*). Both need the Payroll area with manage access and are written to the audit log; neither is available for a draft.

  Limits: no loans/advances module, no half-day leave, no leave carry-over or accrual, no shift scheduling, no 13th-month/severance/end-of-service calculation, payroll is one run per month for the whole company, and attendance is by the server's clock (no biometric or geo checks).


- **2026-10-13 — Installments module.** Task: "Add Installments module". An optional module (off by default; activate it in Settings > Modules — it needs nothing else) that lets a customer pay an **issued invoice in dated instalments**. Sidebar group *Installments* (before Assets): Agreements, New plan, Reports, Settings. Screens live under `/admin/installments/...`. New tables (`installment_plan`, `installment`, `installment_payment`, `installment_settings`) are created on start-up, so restart the backend.

  **How it works.** The invoice stays the legal debt: its payments, receipts and accounting are unchanged. A plan only says *when* the unpaid part falls due and adds optional interest and late fees on top. From an issued invoice with a customer and an unpaid balance, click **Pay in instalments** (or *Installments > New plan*): optional **deposit** (taken at once as an ordinary payment on the invoice), **number of instalments** (2–60), **every** week / 2 weeks / month / 3 months, **first due date**, **interest** (none, *flat* = yearly rate on the whole financed amount for the plan's length split evenly, or *reducing balance* = equal total instalments like a loan), **grace days** and a **late fee** (a fixed amount and/or a % of what is unpaid on that instalment), and whether to email reminders. *Preview schedule* shows every instalment (principal, interest, total) before anything is saved; amounts have 2 decimals and the last instalment takes the rounding difference. One active plan per invoice.

  **Paying.** On the plan page, *Record a payment* goes to the oldest unpaid instalment first; within an instalment interest and late fees are settled before principal. The **principal part** is recorded as a normal invoice payment (so the customer's receipt, the invoice's paid amount and the Accounting entry Dr Cash / Cr Receivable are the usual ones); the **interest and fee part** is income of its own (Dr Cash / Cr *Instalment Interest and Late-Fee Income*, new account 4150 in the Rwanda chart and 771 in the Chad chart, source `INSTALLMENT_CHARGES`). What is paid per instalment is *worked out*, never stored: principal paid = what the invoice received after the plan was made (so a payment recorded straight on the invoice screen also moves the schedule), charges paid = the plan's own payments. A plan becomes **Completed** when everything is settled; **Cancel plan** leaves the unpaid part on the invoice as an ordinary debt and drops unpaid interest and late fees. Interest and fees are income **when collected** (cash basis), not accrued.

  **Late fees and reminders.** A nightly job (05:30) adds the late fee, once, to every instalment still unpaid after its grace days; *Waive late fee* removes one. A morning job (08:05) emails each customer one message per plan listing instalments due within *N days* (once) and overdue ones (again every *M* days) — set the timing and the starting values for new plans in *Installments > Settings* (admin only). Email needs SMTP (Settings > Email) and an email address on the customer; *Send reminder* on the plan page sends one now. The plan page also has a **Schedule (PDF)**.

  **Dashboard and reports.** The Installments page and the main dashboard (when the module is on) show four cards: **Active agreements**, **Outstanding balance**, **Overdue amount** and **Due this month** (plus collected this month). Four reports (*Installments > Reports*, exportable, schedulable): **Instalment Agreements**, **Instalments Due** (next 30 days), **Overdue Instalments**, **Instalment Collections** (date range).

  **Access.** Follows the Billing area: view to look, manage to create plans, record payments, cancel, waive fees and remind; reports need the Reports area (or Billing); settings are admin-only. Staff limited to some branches only see their branches' plans (a plan takes its invoice's branch). Everything is written to the audit log.

  **Follow-up: invoice changes, whole amounts, editable schedule.** (1) *The invoice changing under a plan.* If the invoice is **voided** (or cancelled and credited) the plan is **cancelled automatically** and no more reminders go out; if the invoice is **reduced** by a return or credit note, the unpaid principal is **trimmed to match, taking it off the last instalments first**, and a note on the plan says so (checked whenever the plan, the list, the dashboard cards or a report is opened, when a payment or reminder is made, and nightly). (2) *Whole amounts.* A plan can use **whole amounts (no cents)**; the last instalment takes the rounding left over. The default for new plans is set in *Installments > Settings* and starts **on** when the store's currency has no cents (RWF, XAF, XOF, UGX, JPY and similar). (3) *Edit schedule.* **Edit schedule** on the plan page changes every instalment that isn't fully paid: new dates and amounts, more or fewer instalments, *Split principal evenly*. Rules: the principal across the edited instalments must add up to what was still scheduled (nothing owed is created or forgotten), dates stay in order, interest and fees can't be lowered below what has been paid, instalments already paid are untouched, and late fees stay with the instalment they were charged on. A note records who edited it.

  Limits: one active plan per invoice, no accrual of interest before it is paid, plans only apply to invoices (not POS credit sales or supplier bills), and when an invoice is reduced the cut always comes off the last instalments (edit the schedule afterwards to spread it differently).

- **2026-10-13 — Time Tracking module.** Task: "Add Time Tracking module" (first of Time Tracking, Work Orders, Booking, which the Law Firm module will build on). An optional module (off by default; activate it in Settings > Modules, nothing else needed). Sidebar group *Time Tracking*: My time, Projects, Approvals, Bill time, Reports, Settings. New tables (`time_project`, `time_project_rate`, `time_task`, `time_entry`, `time_timer`, `time_settings`) are created on start-up, so restart the backend.

  **Projects.** A project is the billable container: name, customer (who is billed), hourly rate, budget in hours, dates, billable or not, optional tasks and a per-person rate. Each project also carries a `sourceType`/`sourceId` (MANUAL by default) so Work Orders and Law Firm matters can later create or link a project through `TimeTrackingService.ensureProjectForSource(...)` instead of having a time system of their own.

  **Logging time.** Everyone signed in (no special area needed) opens *My time*: a stopwatch (start on a project and task, stop to save; starting another timer saves the running one first), a quick entry form that accepts `1:30`, `1.5`, `90m` or `2h`, a week view with previous/next, and *Submit for approval*. Time is stored in minutes. Checks: a project must be active, no future dates, no more than the settings' hours per day per person. Draft and sent-back entries can be edited or deleted; once submitted they are locked. The hourly rate is worked out as person-on-project, then project rate, then the default rate in the settings, and fixed on the entry when it is saved and again when it is submitted.

  **Approval.** *Approvals* lists time waiting for a decision (or everyone's time over a period, filtered by person, project and status). A manager approves or sends back with a reason (the person sees the reason, corrects and resubmits), and can correct minutes, description or billable on any entry not yet invoiced. If the settings switch approval off, submitted time is approved at once.

  **Billing.** *Bill time* lists projects with approved billable time not yet invoiced. *Prepare invoice* previews one line per person and rate ("Project — Alice: 12.5 h @ 40000 (3 Oct 2026 – 12 Oct 2026)") for the chosen period, and *Create draft invoice* makes a normal DRAFT invoice for the project's customer (choose one if the project has none) through the existing invoice service, then marks that time as invoiced. Lines use a service product (Settings > Time Tracking; "Professional services (time)" is created automatically the first time), quantity 1 and the amount as price, because invoice quantities are whole numbers; the product decides tax and accounting. If the draft is deleted or the invoice voided, *Undo a time invoice* releases the time to be billed again (refused while the invoice is issued).

  **Reports** (Time Tracking section of the report viewer, Excel/PDF, schedulable): *Timesheet Summary*, *Project Hours vs Budget*, *Unbilled Time*, *Utilisation* (hours logged against Monday-Friday working hours) and *Time Entries*.

  **Access.** *My time* needs only a login. Seeing all projects, time, approvals and reports follows the Operations area (view), changing projects, rates and approving needs Operations (manage), turning time into an invoice needs Billing (manage), settings are admin-only. The Operations area description now mentions time tracking, so existing custom roles that manage Operations can approve time.

  Limits: any active project can be logged on by any staff member (no project membership list yet), rates are in the single store currency, entries cannot be split across days, approved time cannot be edited by the person who logged it (a manager corrects it or sends it back), and invoice lines cannot show fractional quantities (the hours are in the description).

- **2026-10-14 — Flyway database migrations.** Task: "introduce Flyway ... so that for any database changes the migration can happen without changing the database [by hand]". Added `flyway-core` and `flyway-database-postgresql` to `pom.xml`; `application.properties` and `application-niwe.properties` now set `spring.jpa.hibernate.ddl-auto=validate` and enable Flyway (`classpath:db/migration`, `baseline-on-migrate=true`, `baseline-version=1`). `db/migration/V1__baseline_schema.sql` (137 tables, generated from the 135 entity classes with Hibernate's own PostgreSQL DDL generator, so it is exactly what `ddl-auto=update` would have created) is the baseline: a new database runs it, an existing database is baselined at 1 and untouched. `application-dbsync.properties` is a one-time profile (Flyway off, `ddl-auto=update`) to bring an existing database fully up to date before its first Flyway start. See "Database migrations (Flyway)" under *Running it* for the rules (one migration per entity change, never edit an applied file, enum check constraints, backups). `EnumConstraintMigration` and the `WarehouseService` legacy column fixes stay: they are guarded and do nothing on a new schema. Verified: V1 applies without error to an empty PostgreSQL 16 and Hibernate's schema validation accepts the result (and rejects it when a column is removed). Flyway itself could not be run in the build sandbox (Maven Central is not reachable), so the first start on your machine is the first real Flyway run.


- **2026-10-14 — Work Orders module.** Task: second of Time Tracking, Work Orders, Booking (the Law Firm module builds on them). Scope chosen: customer service jobs, internal maintenance/repair linked to Assets and Expenses, standard service packages, and a link to Manufacturing. An optional module (off by default; activate it in Settings > Modules — it brings Time Tracking along). Sidebar group *Work Orders*: My jobs, Work orders, Service packages, Reports. First schema change under Flyway: `V2__work_orders.sql` creates `work_order`, `work_order_line`, `work_order_checklist_item`, `work_order_cost`, `service_package`, `service_package_item`, `service_package_part` — restart the backend and Flyway applies it.

  **Jobs.** A job is a *customer job* (billed) or an *internal* one (maintenance/repair of your own assets or premises, cost only). It has a number (WO-00001), title, priority, optional asset, "what is being worked on", an assignee, scheduled/due dates, a checklist, parts, outside costs and a hourly rate or fixed price. Statuses: Open, In progress, On hold, Completed, Cancelled. The person it is assigned to (or an Operations manager) starts, holds and completes it, ticks the checklist and records parts; managers create, edit, assign, add costs, reopen, cancel and delete. *My jobs* (`/work-orders/mine`) and one's own job are open to any login; the list, packages and reports follow the Operations area; invoicing follows Billing.

  **Hours.** Each job has its own Time Tracking project, so people log hours in *My time*. Customer jobs without a fixed price bill those hours; they are billed only from the work order (the Bill time page skips these projects).

  **Parts and stock.** A standalone invoice does not move stock, so completing a job takes the listed parts out of stock (refused when there is not enough, unless negative stock is allowed) and posts their cost: customer job → Cost of Goods Sold, internal job → the new account *Repairs & Maintenance Expense* (5210 Rwanda, 624 Chad). Reopening a job (not allowed once invoiced) returns the stock and reverses the posting.

  **Billing.** A completed customer job → *Preview invoice* → *Create draft invoice*: fixed price (or one line per person and rate for the hours), then parts (those "in the fixed price" are left off), then outside costs you charge on. *Release* lets the job be billed again (refused while the invoice is issued).

  **Outside costs, packages, Manufacturing.** An outside cost can be recorded as a pending expense in Expenses. A *service package* (checklist, usual parts, fixed price, hours) is copied onto a new job. A job can create a linked manufacturing order and cannot complete until it is produced. The asset page has a *Maintenance history* button.

  **Reports** (Work Orders section): *Work Orders*, *Work Order Profitability*, *Maintenance Cost by Asset*, *Overdue Work Orders* (Excel/PDF, schedulable).

  Verification: backend javac clean; V1+V2 applied to an empty PostgreSQL 16 and Hibernate validation passes; frontend `ngc --noEmit` clean. Not run against a live database, Flyway or a browser. Limits: one assignee per job; hours are logged from My time (no timer button on the job); part prices are the product price when added.

- **2026-10-14 — Booking module.** Task: third of Time Tracking, Work Orders, Booking (the Law Firm module builds on them). Scope chosen: appointments with staff, rooms/equipment that cannot be double-booked, a public booking page, and paid bookings (deposit or full payment) matched to an invoice. An optional module (off by default; activate it in Settings > Modules). Sidebar group *Booking*: My appointments, Calendar, Appointments, Services, Rooms & equipment, Reports, Settings. Schema change: `V3__booking.sql` creates `appointment`, `bookable_resource`, `bookable_service`, `bookable_service_staff`, `booking_block`, `booking_settings` — restart the backend and Flyway applies it. (Classes are named `Appointment` etc. on purpose; the hotel module already owns `Booking`.)

  **Services and availability.** A *service* has a length, a buffer after it, a price, a payment rule (none, deposit %, full price), an optional kind of room it needs, the people who can do it, and whether it shows on the public page. Opening hours, working days, the step between start times, minimum notice and how far ahead guests can book are in *Settings* (global; per-person working hours are not modelled — use *Blocked time* on Rooms & equipment for leave, holidays and repairs). A start time is offered only if a suitable person **and** a suitable room are both free for the length plus buffer.

  **No double-booking.** Booking creation takes a row lock on the booking settings, so two people cannot take the same slot at the same moment. There is no database exclusion constraint behind it. Occupying statuses: Confirmed, Completed, and Waiting for payment while the hold is still running.

  **Paid bookings.** A deposit/full-price service creates the appointment as *Waiting for payment*, holds the time for the hold period, and raises an issued invoice for the full price (service product "Appointments and bookings"). When payments recorded on that invoice reach the amount needed, the booking becomes Confirmed; voiding the invoice cancels it. A job running every 5 minutes cancels expired holds (and voids the unpaid invoice). Staff can also confirm without payment. Revenue is recognised when the invoice is issued. There is no online payment gateway: the guest pays as the instructions say and staff record the payment on the invoice.

  **Public page** (`/book`, no login, only when Booking is on and online booking is enabled). Service → optional person → day → a free time → name and phone. Safeguards: a hidden honeypot field, a per-phone daily limit, and lookup/cancel only with the booking code **and** the phone number; online cancel is allowed until a set number of hours before the start. A confirmation email is sent when the guest gives an address and e-mail is configured.

  **Reports** (Booking section): *Appointments*, *Booking utilisation* (booked vs available hours per person/room), *Bookings by service* (incl. no-show rate). The calendar page shows a one-day grid by person or by room with blocked time shaded.

  Verification: backend javac clean (899 files); V1+V2+V3 applied in order to an empty PostgreSQL 16 and Hibernate validation passes (148 entity classes); frontend `ngc --noEmit` clean. Not run against a live database, Flyway or a browser — the slot logic and the invoice/payment sync are untested beyond compiling.

- **2026-10-14 — Fix: Booking (and other newer modules) could not be activated.** Activating a module failed with `module_setting_module_key_check` violated. Hibernate had written a CHECK on `module_setting.module_key` listing only the modules that existed when the table was created, and it never grows with the enum. `V4__module_key_check_drop.sql` drops it (the application validates the key itself; re-listing it would need a migration for every future module). Restart the backend and Flyway applies it. The same would have happened for Installments, Time Tracking, Work Orders and the HR modules. Found while checking: an earlier schema comparison had been run with an older build of the classes ahead of the current ones on the class path; redone with a clean class path, this was the only difference between the migrations and the entities. Rule going forward: when an existing enum gains a value, check whether its column has a CHECK list and handle it in the same migration.

- **2026-10-14 — Law Firm module, phase 1 (matters, conflict check, deadlines, tasks).** Task: the Law Firm module on top of Time Tracking, Work Orders and Booking, built in phases; this is phase 1 of 6 (next: time and billing, client money/trust account, documents, client portal, reports). An optional module (off by default; activate it in Settings > Modules — it brings Time Tracking along). Sidebar group *Law Firm*: My work, Matters, Clients, Deadlines & tasks, Conflict check, Contacts, Reports, Settings. Schema change: `V5__law_firm.sql` creates `law_settings`, `law_matter_counter`, `law_contact`, `law_matter`, `law_matter_team`, `law_matter_party`, `law_matter_deadline`, `law_matter_task`, `law_matter_note`, `law_conflict_check` — restart the backend and Flyway applies it.

  **Clients and matters.** A client is an ordinary customer (so invoices, statements and credit work as usual); *Clients* lists customers that have a matter. A *matter* has a number such as `RW-2026-0012` (branch prefix, year, running number that restarts each year), a title, client, practice area, jurisdiction, court and court case number, how it is billed (hourly, fixed fee, retainer, contingency, pro bono — recorded now, used by the billing phase), a fee-agreement note, a lead lawyer and a team (lead, associate, paralegal, support). Status: Open, On hold, Closed (closing asks for the outcome and warns about open deadlines/tasks).

  **Who can see what.** Admins see everything. A matter's team can work it; the lead can also edit it, change the team and clear conflicts. Operations viewers/managers cover every matter that is *not restricted*. A **restricted** matter (set per matter, or by default in Settings) is invisible to everyone outside its team except admins, and reports show it without title or client. My work, a single matter, the conflict check and the contacts are open to any login; the server decides what they show.

  **Conflict check.** Search a name, phone, ID number or e-mail against all clients and all outside contacts (opposing parties, opposing counsel, co-clients, witnesses, experts, officials), open or closed matters. Names match on the whole name or all words of the shorter name (not part-words); phones on the last digits; IDs and e-mails exactly. A hit is flagged *Opposite sides* when one side is our client/co-client and the other an opposing party/counsel, otherwise *Review*. Matters the person cannot open are reported as "a restricted matter". On a matter, *Run conflict check* checks the client and every recorded party; *Clear conflicts* records who cleared it and when, and needs a written reason when overlaps exist. Adding a party or changing the client un-clears the matter. Every search is logged (Conflict check page).

  **Deadlines, tasks, diary.** Deadlines (court date, hearing, filing, limitation date, meeting, other) with date/time and assignee; tasks with due date, priority and assignee (assignees must be on the team); a diary of notes, calls, meetings, e-mails and court events. A daily e-mail at 06:15 (server time) lists each lawyer's overdue and due-soon deadlines and tasks (window and on/off in Settings; needs e-mail configured and an address on the user).

  **Reports** (Law Firm section): *Law matters*, *Law deadlines*, *Law workload* (Excel/PDF, schedulable).

  Verification: backend javac clean; V1–V5 applied in order to an empty PostgreSQL 16 and Hibernate validation passes (158 entity classes); frontend `ngc --noEmit` clean. Not run against a live database, Flyway or a browser — numbering, the access rules, the conflict matching and the digest e-mail are untested beyond compiling. Limits: one lead per matter; matter numbers use the branch prefix (`document_prefix`, else branch code) and the opened date's year; no documents, billing, client money or portal yet; the daily e-mail uses the server's time zone.

- **2026-10-14 — Law Firm module, phase 2 (time and billing).** Task: phase 2 of 6 (next: client money/trust account, documents, client portal). Choices made with the user: bill hourly, fixed-fee, retainer and contingency matters; costs paid for the client are billable; hours are logged through Time Tracking on a project of their own per matter. Schema change: `V6__law_billing.sql` adds five fee columns and the matter's time project to `law_matter` and creates `law_fee_stage`, `law_matter_expense` and `law_bill` — restart the backend and Flyway applies it (it needs V5). Sidebar: *Law Firm > Billing* (overview); each matter has a new **Billing** tab. Two new reports in the Law Firm section: *Law billing and work in progress* and *Law fees billed*.

  **Hours.** Every matter gets its own Time Tracking project (named `HO-2026-0001 — title (client)`, created when the matter is opened; matters that already exist get theirs when the backend starts). Lawyers log hours in *My time* and pick the matter; the lead lawyer (or an Operations manager on the team's behalf) approves or sends back from the matter's Billing tab, and Time Tracking's own Approvals page works too. Hours are billable on **hourly** and **retainer** matters; on fixed, contingency and pro bono matters they are tracked but not billed. The rate is the person's rate on the matter, else the matter's hourly rate, else the Time Tracking default (set on the Billing tab, *Fee terms* and *Rates per person*). A closed matter's project stops taking new time; reopening the matter reopens it. A matter with hours, costs or bills can no longer be deleted.

  **Restricted matters.** The matter's time project shows only as `HO-2026-0004 — (restricted)` with no client. People outside the team (admins excepted) do not get it in the time pickers, cannot log, approve or see its entries (Time Tracking now asks the law module before showing or accepting time on a matter: `TimeProjectGate`), and the billing tab answers "not found". The Time Tracking project list and its reports still show the restricted matter's number, hours and amounts under that masked name.

  **Costs.** Court and filing fees, travel and the like are recorded on the matter (what it cost, what to charge the client, optionally also as an expense in Expenses). Charged costs that are not yet billed are offered on the next invoice. Only the person who recorded a cost or the lead can delete it, and not once it is billed.

  **Fixed fee and stages.** Set the fixed fee in the fee terms, then split it into stages (for example on instruction, on filing, on completion); stages cannot add up to more than the fee. Each stage is billed when ready.

  **Retainer.** *Ask for a retainer* creates a normal draft invoice for the amount (a bill of kind Retainer). When fees are billed later, the retainer left (invoiced minus already used) is taken off the fee lines — invoice lines cannot be negative, so the fee lines are reduced and marked "less retainer applied" — and the invoice note says what was used and what is left. If the retainer covers everything, no invoice is made: the hours are marked as billed against the retainer. Costs are never paid from the retainer. A retainer counts once its invoice exists and is not void; it does not wait for payment. This is an advance fee billed by invoice, **not** client money held in trust — that is phase 3.

  **Contingency.** Enter the percentage in the fee terms; when something is recovered, type the amount on the Billing tab and the invoice carries "Contingency fee — 25% of 4,000,000 recovered". Several recoveries can be billed one after another.

  **Billing and taking back.** *Bill this matter* previews, then creates one **draft** invoice for the client (through the normal invoice service, using the Time Tracking service product, tax comes from that product) holding the chosen approved time (up to a date), stages, costs and contingency fee. Everything on it is marked billed. *Take back* (a bill) deletes its draft invoice and frees the hours, costs and stages; an issued invoice must be voided first, after which *Free the work* does the same. Billing needs Billing (manage); the lead lawyer can set the terms, rates and stages; any team member can add costs. The *Bill time* page of Time Tracking no longer offers matter projects.

  **Reports.** *Law billing and work in progress*: per matter hours, time/costs/stages waiting to be billed, billed, paid, owed and retainer left. *Law fees billed*: every bill in a period with its invoice and payment. Restricted matters show without title or client.

  Verification: javac clean; V1–V6 applied in order to an empty PostgreSQL 16 and Hibernate validation passes (161 entity classes); frontend `ngc --noEmit` clean. The application was also started in the sandbox against that database and driven through its HTTP API (opening matters, logging and approving time, billing, taking bills back, retainer netting and full cover, stages, contingency fee, restricted matters with an outsider and an Operations manager, deleting rules, closing and reopening, conflict check, the reports) — the figures and refusals were as designed. This was the first time phase 1 was also run for real; it behaved. Not run: Flyway itself, a browser (the Billing tab and Billing overview screens have only been type-checked), mail (the daily digest), issuing the invoices to a real chart of accounts. Limits: bill amounts and the retainer are tracked before tax; invoice lines for costs use the same service product as fees; one lead per matter; the retainer is not client money; the Time Tracking project list and reports show the masked restricted matter. Seen on the way: a user without the needed area who calls a protected API gets HTTP 500 "Access Denied" instead of 403 (existing behaviour of the whole application, not changed).

- **2026-10-14 — Law Firm module, phase 3 (client money / trust account).** Task: phase 3 of 6 (next: documents, client portal, reports). Choices made with the user: money held for clients is posted to the accounts (a trust bank asset and an equal "Client money held" liability, the firm's own money is never mixed in); payouts need two people (one asks, a different person approves); fees are taken from trust by paying that matter's issued invoice. Schema change: `V7__law_trust.sql` adds three columns to `law_settings` and creates `law_trust_transaction` and `law_trust_reconciliation` — restart the backend and Flyway applies it (it needs V6). The first start also adds two accounts to the chart of accounts: **Client Trust Bank Account** (Rwanda 1010, Chad 5211) and **Client Money Held** (Rwanda 2870, Chad 4192). Sidebar: *Law Firm > Client money*; each matter has a new **Client money** tab; *Law firm settings* has a switch for it. Two new reports in the Law Firm section: *Client money held* and *Client money ledger*.
  **Switching it on.** Admin: Law Firm > Settings > tick "The firm holds money for clients in a trust account", enter the bank and account number. It is off by default. It cannot be switched off while client money is held or a payout is waiting.
  **Receiving money.** On the matter's Client money tab, *Receive money* (amount, date, how paid, reference, who paid in). It is posted at once (Dr Client trust bank / Cr Client money held). Refused when the matter is closed or its conflict check has not been cleared. A client's balance is kept per matter; a client's total is the sum of its matters.
  **Paying out.** *Pay out…* (amount, who is paid, how, what for) creates a request. It cannot be more than the balance after other waiting requests, so a client's balance can never go below zero. The matter's **lead lawyer or an admin, who must be a different person from the one who asked,** approves (money leaves: Dr Client money held / Cr Client trust bank) or rejects it with a reason; the person who asked can cancel it.
  **Paying fees from trust.** *Pay an invoice from trust…* lists the matter's issued invoices that are addressed to the client and still owe something. A request cannot be more than the invoice still owes (counting other waiting requests) or more than the client has. On approval the invoice receives a normal payment with method `TRUST` (so it shows paid, receipts and the customer account stay right; Dr Cash / Cr Accounts Receivable) and the trust side is posted (Dr Client money held / Cr Client trust bank).
  **Mistakes.** Posted entries are never edited or deleted. A deposit entered wrongly or a cheque that bounced is taken back with *Take back* (lead lawyer, manager or admin, with a reason); it is refused if part of that money has already gone.
  **Closing and deleting.** A matter cannot be closed while it holds client money or has a waiting request, and cannot be deleted once it has any client-money entry.
  **Reconciliation** (*Client money > Reconcile*, Billing manage or admin): enter the bank statement balance, deposits not yet on the statement and payments not yet cleared; the screen compares the adjusted bank balance with the sum of the client ledgers and with the two accounts in the general ledger, and saves the result (balanced or the difference).
  **Access.** Everything follows the matter's own rules: restricted matters are invisible (404) to everyone outside the team and admins, including in the overview and reports (reports show restricted matters by number only). Recording money and asking for payouts: the matter's team, a manager, or Billing (manage) staff who may view the matter. The overview needs Operations (view). The reconciliation needs Billing (manage).
  **Reports.** *Client money held*: per matter held and waiting, last activity. *Client money ledger*: every posted movement in a period with the matter's running balance.
  Verification: javac clean (965 files); V1–V7 applied in order to an empty PostgreSQL 16 and Hibernate validation passes (163 entity classes); frontend `ngc --noEmit` clean. The application was started in the sandbox (Flyway disabled there, migrations applied by psql) and exercised over HTTP as admin, a lead lawyer, a junior team member and an outsider: deposits, payouts, own-request approval refused, outsider refused, overdraw refused, reject/cancel, deposit take-back (and refused twice), fee transfers paying an issued invoice in two steps with the limits above, draft invoice refused, closing/deleting/switching off refused, overview, three reconciliations, both reports (JSON, xlsx, pdf), restricted matter hidden. The general ledger showed the trust bank account and Client money held equal to the client ledgers, every journal entry balanced, nothing in Suspense; the two chart accounts were re-added after being removed and the app restarted. Not run: Flyway itself, a browser, the Chad chart.
  Limits: the rules for client money differ by bar and country (Rwanda and Chad) — the firm and its accountant must confirm them (for example whether interest, minimum balances or separate accounts per client are required); one trust bank account only; the matter is the unit of the ledger (money cannot be moved between matters or clients yet); a payout is recorded when it is approved, not when the bank clears it; an invoice paid from trust that is later cancelled is not returned to the trust account by this module (handle it with a payout/deposit); an existing account that already uses code 1010 or 2870 (Chad: 5211 or 4192) would be taken over as the trust account on first start; a firm with a single user cannot approve its own payouts; protected endpoints answer HTTP 500 "Access Denied" instead of 403 when permission is missing (existing app-wide behaviour).

- **2026-10-14 — Law Firm module, phase 4 (documents).** Task: phase 4 of 6 (next: client portal, reports). Choices made with the user: files are stored **in the database** (PostgreSQL `bytea`, so a database backup is a backup of the documents; no server folder to look after); upload/download with **version history**, **templates** that fill in matter details, **check-out lock** while editing, and a **"shared with the client"** mark (used later by the client portal); access is **the same as the matter plus a confidential mark**. Schema change: `V8__law_documents.sql` creates `law_document`, `law_document_version`, `law_document_blob` and `law_doc_template` (restart applies it; nothing existing is changed). No new menu permission: it follows Operations. New menu entry *Law Firm > Templates*.
  **Adding a document.** Matter > Documents tab > *Add a document* (file up to 25 MB, optional name, kind, description, confidential). The file name is cleaned (no folders), an empty file is refused, and programs/scripts/web pages (`exe, bat, cmd, js, html, svg, sh, jar, …`) are refused — put them in a zip or save as PDF. A SHA-256 fingerprint of each version is stored. Documents are always sent to the browser as downloads (never opened inside the site).
  **Versions.** Every saved file is a version and is never changed or removed; *Versions* lists them with who, when, size and a note, and any version can be downloaded. *Add a new version* is refused while the document is archived or checked out by someone else.
  **Check out / check in.** *Check out* tells everyone else you are editing: they can still download it but cannot add a version until you check it in (saving a new version checks it in unless you tick "keep it checked out"). The person who holds it, the lead lawyer, a manager or an admin can release it.
  **Confidential and sharing.** A confidential document is invisible (404) to everyone except the matter's team and admins — including Operations managers and viewers, on the list, version list and download. Changing the mark, sharing with the client and archiving are for the lead lawyer, a manager or an admin; a confidential document cannot be shared, and marking one confidential removes the sharing. The "shared with client" mark does nothing yet by itself; the client portal (phase 5) will use it.
  **Archive.** Archived documents are hidden unless *Show archived* is ticked; nothing is ever deleted. A matter that has any document cannot be deleted (close it instead).
  **Templates.** *Law Firm > Templates* (admin or Operations manager to change; Operations viewers can read): either typed text or a Word `.docx` file containing placeholders such as `{{client.name}}`, `{{matter.number}}`, `{{matter.title}}`, `{{matter.practiceArea}}`, `{{matter.jurisdiction}}`, `{{matter.court}}`, `{{matter.courtCaseNumber}}`, `{{matter.status}}`, `{{matter.opened}}`, `{{lead.name}}`, `{{team}}`, `{{opposing.parties}}`, `{{opposing.counsel}}`, `{{client.phone}}`, `{{client.email}}`, `{{today}}`, `{{user.name}}`, `{{firm.name}}` (Settings > Business details), `{{firm.address}}`, `{{firm.phone}}`, `{{firm.email}}`. In a Word file they work in the body, tables, headers and footers and keep the formatting when typed in one go. On a matter, *Make one from a template…* creates a new Word document (version 1) with the details filled in; unknown placeholders stay as written and are listed. A template used by documents cannot be deleted — switch it off instead.
  **Access.** Everything follows the matter's own rules: restricted matters are invisible outside the team and admins; adding, versioning, locking and making from a template need the matter's team, a manager, or an admin; Operations viewers can see and download non-confidential documents. Uploads, new versions, lock changes, flag changes, archiving, template changes and downloads of confidential documents are written to the audit log.
  Verification: javac clean (979 files); V1–V8 applied in order to an empty PostgreSQL 16 and Hibernate validation passes (167 entity classes); frontend `ngc --noEmit` clean. The application was started in the sandbox (Flyway disabled, migrations applied by psql) and 77 HTTP checks passed as admin, lead, junior team member, Operations manager, Operations viewer and an outsider: upload/download byte-for-byte, size/empty/type/path-traversal refusals, version history and old versions intact, lock behaviour, confidential hiding for non-team managers/viewers/outsiders, sharing rules, archive, matter delete refusal, text and Word template generation (split placeholders, tables, headers, bold kept), unknown placeholders, template permissions, restricted matter. Not run: Flyway itself, a browser (the screens were only compile-checked), the Chad data.
  Limits: no virus scanning (a file is stored as received, only the type list above is blocked); no in-browser preview or editing (download, edit, upload as a new version); the 25 MB cap is checked by the application, and a reverse proxy in front (nginx, IIS) may have a smaller upload limit that must be raised; very large numbers of big files make the database and its backups big; text search inside documents is not provided; locks do not expire by themselves (the lead or an admin releases a forgotten one); the "shared with client" mark is not used until phase 5.

- **2026-10-14 — Law Firm module, phase 5 (client portal).** Task: phase 5 of 6 (next: reports). Choices made with the user: each client contact gets **their own account** (a lawyer invites them by e-mail, they choose a password and sign in at `/portal`, separate from staff login); they can follow the matter's status and the dates staff mark as visible, **download documents marked "shared with the client"**, **send documents to the lawyer** (staff accept or reject each one before it joins the document list), read issued invoices and the posted trust balance (read-only, **no online payment**), and exchange messages; the portal is **off by default for each matter** and a lead lawyer, manager or admin switches it on and picks what the client sees. Schema change: `V9__law_portal.sql` adds `law_settings.portal_enabled`, `law_matter_deadline.visible_to_client` and four review columns on `law_document`, and creates `law_matter_portal`, `law_portal_account` and `law_portal_message` — restart the backend and Flyway applies it (it needs V8). A new firm-wide switch *Client portal* in *Law firm settings* must be on (admin) before any client can sign in. Security: portal accounts are not staff users; a portal token only works on `/api/portal/**` and a staff token is refused there; every call checks that the matter belongs to the signed-in client and is open to the portal (otherwise 404); 5 wrong passwords lock the account for 15 minutes; passwords need 10+ characters with a letter and a digit; invite links are random, stored only as a hash, valid 7 days and work once; changing a password, a reset or switching a person off ends their earlier sessions. Staff: each matter has a new **Client portal** tab (settings, progress note, people with access, visible deadlines, messages); documents sent by a client appear under *Sent by the client, waiting for review* on the Documents tab. Notes: there is no self-service "forgot password" (staff use *New link / reset password*); if e-mail is not configured the invite link is shown on screen to staff so they can send it themselves; the e-mail link uses `app.frontend-url`; a reverse proxy in front of the API needs an upload limit of at least 25 MB.
  **Using it.** Admin: *Law firm settings > Client portal* on. Lead lawyer: matter > *Client portal* tab > tick *Open this matter to the client portal*, choose what is shown, add a person (name and e-mail), copy the one-time link to them if no e-mail went out. The client opens it, sets a password and lands on their matters; later they sign in at `/portal`. A document is shown to the client only when it is marked *Share with client*, is not confidential, not archived; a document the client sent can be shared only after you accept it.
  Verification: javac clean (995 files); V1–V9 applied in order to an empty PostgreSQL; Hibernate `validate` OK (170 entities); 106 scripted checks against the running API passed (isolation between two clients, staff and portal tokens refused on each other's endpoints, invite single use and expiry, lockout, password change, reset and switch-off ending old tokens, upload then accept and reject, messages, trust and invoice visibility, firm switch, matter delete refused while portal records exist); `ngc` clean. Not verified: the screens in a browser, e-mail delivery, an issued invoice shown to a client (only the empty case was exercised), a Flyway run against the existing Chad data.
  Limits: no online payment, no self-service forgot-password, no virus scanning of client uploads, no two-factor sign-in; a restricted matter can still be opened to the portal by its lead.

- **2026-10-14 — Law Firm module, phase 6 (reports and overview).** Task: phase 6 of 6, the last one. Choices made with the user: six new reports, and a Law Firm overview page for partners. Follow-up the same day: a cost per hour for each lawyer, so profitability shows real profit, and a tick box to include closed matters (see the end of this entry). Schema change: `V10__law_cost_rate.sql` creates `law_cost_rate` (restart applies it; it needs V9). **New reports** (in *Law Firm > Reports* and *Reports*, each with Excel and PDF download; admin or the Reports area): *Law lawyer productivity* (hours, billable and non-billable, billable %, billable value, billed and not yet billed per lawyer for a period; rejected time left out), *Law matter profitability* (per matter: days open, hours, value of time, fees billed, billed, paid, owed, collected %, cost of time, costs paid out and recovered, profit and profit %, hours without a cost rate, work not yet billed), *Law unpaid invoices by age* (each unpaid invoice in 0-30 / 31-60 / 61-90 / over 90 days since its invoice date), *Law documents and client portal* (per matter: documents, storage over all versions, confidential, shared, client uploads waiting, portal open or closed, people with access, last client sign-in, unanswered client messages), *Law conflict check log* (who searched for what, hits, result) and *Law caseload by practice area and court* (opened and closed in the period, open now, on hold, average days to close). **Overview**: *Law Firm > Overview* (`GET /api/law/overview`) shows open matters, deadlines this week and overdue, hours this month, billed this month, work not yet billed, owed by clients (and how much is older than 60 days), client money held, client portal items waiting, a "needs attention" list, and four bar charts (fees billed over six months, owed by age, open matters by practice area, hours by lawyer). The overview only counts matters the signed-in person may open, so a restricted matter stays out for people not on its team; the reports show a restricted matter's number only. Code: `reports/LawInsightService`, `LawInsightController`, `LawOverviewService`, `LawOverviewController`, DTOs `LawInsightRows`, `LawOverviewDto`; frontend `admin/law/law-overview.component.ts`, six entries in `core/report-defs.ts`, route `admin/law/overview`, sidebar link.
  Verification: javac clean; 81 scripted checks against the running API passed (every report's figures against independently computed values, access with a lawyer, an Operations viewer and manager, an outsider and a Reports-only role, Excel and PDF downloads, restricted matter masking, backdated invoices into the right age bands); `ngc` clean. Not verified: the screens in a browser, Flyway, the Chad data.
  **Profit and closed matters (follow-up).** *Law firm settings > Cost of lawyers' time* (admin only; `GET|PUT /api/law/cost-rates`, audit-logged) holds what one hour of each person costs the firm. *Law matter profitability* then shows **Profit = fees billed (live invoice bills, retainers not counted twice) - cost of time (all logged hours that are not rejected, billable or not, x the person's cost per hour) + costs recovered from clients - costs paid out**. A person with no cost rate counts as free; the column *Hours without a cost rate* says how many hours that is, and a matter with no rated hours shows a dash for profit, not 0. Cost rates are today's rates applied to all past hours (no rate history). *Law billing and work in progress*, *Law matter profitability* and *Law documents and client portal* have a tick box *Include closed matters* (open matters only by default). Verified with 40 more scripted checks (profit arithmetic, the missing-rate cases, admin-only access, validation, audit entry, open/closed lists, Excel and PDF); Hibernate validate OK (171 entities); `ngc` clean.
  Limits: payments are not tied to a lawyer, so productivity shows billed and not yet billed, not collected, per lawyer. Hours on a restricted matter still count in the lawyer totals (no title or client is shown).

- **2026-10-14 — Real Estate module, phase 1 (properties, leads, viewings).** Task: "Add two modules, Real Estate and Property Management". Choices made with the user: two fully separate modules (each has its own property list, nothing shared); Real Estate covers listings and viewings, sales deals and agent commission, developer projects (plots and units) and documents and valuation; Property Management covers owners/tenants/leases, rent invoicing and arrears, maintenance requests and owner statements with the management fee; no tenant or owner portal in the first version. Built in phases (RE-1 properties/leads/viewings, RE-2 sales deals and commission, RE-3 developer projects, RE-4 documents and valuations, then PM-1 to PM-4); this entry is RE-1. An optional module (off by default; activate it in Settings > Modules). Sidebar group *Real Estate*: Overview, Properties, Leads, Viewings, Reports, Settings. Schema change: `V11__real_estate.sql` creates `re_settings` (with its one row), `re_counter`, `re_property`, `re_property_photo`, `re_photo_blob`, `re_lead`, `re_lead_note`, `re_viewing` — restart the backend and Flyway applies it (it needs V10).
  **Properties.** House, apartment, villa, land, commercial, office, warehouse. Offered for sale, rent or both; asking price and monthly rent; area, address, title/plot reference, optional map position, bedrooms, bathrooms, parking, built and plot size, year built, features, description and the owner's contact (staff only). Codes PR-0001, PR-0002… A new property is a *Draft*; *Available* needs the price(s) and sets the listed date; the other states are Reserved, Sold, Let (rent only) and Withdrawn. A rent-only property cannot be marked sold and a sale-only one cannot be marked let. Only a draft or withdrawn property without leads or viewings can be deleted. **Photos**: up to 12 per property (JPEG, PNG or WebP, 5 MB each, checked by content not only by name), one is the cover; stored in the database (`bytea`) like Law Firm documents, so a backup of the database is a backup of the photos. Each property has an agent (the person who added it unless chosen).
  **Leads.** A lead is someone who asked about buying or renting: contact, budget range, preferred area/type/bedrooms, the property asked about, where they came from, who looks after them and the next follow-up date. Stages New, Contacted, Viewing, Negotiating, Won, Lost (Lost needs a reason; Won makes the customer record first). The *diary* records notes, calls, meetings, e-mails and WhatsApp messages; a call or meeting moves a New lead to Contacted and can set the next follow-up. The same phone number written 0772…, +256 772… or with spaces is recognised as one number (last nine digits): adding a second open lead with it is refused unless the person chooses to save anyway. *Make a customer* reuses the customer with the same number or creates one. The lead page lists up to ten available properties that suit the lead (purpose, budget with 10% slack, area, type, bedrooms) with the reasons. A lead is "quiet" after the number of days set in Settings with no contact.
  **Viewings.** Book a viewing of an available property for an open lead; it lasts the default set in Settings (30 minutes) unless an end is given and takes the property's agent unless another is chosen. An agent cannot have two scheduled viewings overlapping: the server answers 409 and the screen offers *Keep both*. Mark a viewing Done (not before it starts), No-show or Cancelled with feedback; a closed viewing is locked. Booking a viewing moves a New or Contacted lead to Viewing.
  **Permissions.** Operations viewers read; Operations managers and admins add and change; Settings (areas, viewing length, quiet-lead days) are admin-only. Reports follow the Reports area. **Reports** (Real Estate section, Excel and PDF): *Real Estate listings*, *Real Estate leads* (tick box to include won and lost), *Real Estate viewings* (period; defaults from the start of this month to 30 days ahead). **Overview**: stock by status, value for sale, rent on offer, open leads and follow-ups overdue, viewings today and in the next 7 days, leads that need a call and three bar charts. Editing: a save that carries the title (property) or name (lead) replaces every field, so an emptied box clears the value; a request without it changes only the fields it names.
  **Public listings website (added the same day).** Task: "a public website for listing". Schema: `V12__real_estate_website.sql` adds `re_property.public_listing`, six website columns on `re_settings` and creates `re_enquiry` (restart applies it; it needs V11). Off by default: an admin opens *Real Estate > Settings > Public website* (heading, introduction, phone and e-mail shown to visitors, daily enquiry limit) and ticks **Open the public listings website**; each property then needs **Show on the public website** ticked, and shows only while it is *Available*. Visitors, with no login, use `/listings` (filters: buy or rent, type, area, bedrooms, price range, search; 12 per page; featured first) and `/listings/{id}` (photo gallery, details, features, agent's name, enquiry form); the home page gets a *Find a property* tile. What a visitor never gets: owner name, phone or e-mail, street address, title reference, map position, internal notes, staff other than the agent's name; an unlisted, draft or withdrawn property answers "not found", as do its photos, and the whole site answers "not found" while the module or the switch is off. **Enquiries** (`POST /api/public/realestate/properties/{id}/enquiries`) become a lead with source *Website*, assigned to the property's agent, follow-up today, and the message as a diary note; if an open lead with the same phone number (written any way) exists, the message is added to that lead instead. Abuse limits: a hidden "website" field that bots fill in (the enquiry is thanked and thrown away), at most the configured number of enquiries per phone number or e-mail per day (default 3, then HTTP 429), and at most 10 per connection per hour (in memory, per server; behind a proxy that hides visitors' addresses all visitors share one allowance, so make the proxy pass the real address, or raise the limit in `RePublicService`). Code: backend `RePublicService`, `RePublicController` (`/api/public/realestate/info|properties|properties/{id}|properties/{id}/photos/{photoId}|properties/{id}/enquiries`), `ReEnquiry`, `dto/RealEstatePublicDtos`, `PublicModuleController` (now reports REAL_ESTATE as open only when the website switch is on); frontend `realestate/public-listings.component.ts`, `public-listing-detail.component.ts`, `core/api/public-realestate-api.service.ts`, routes `listings`, `listings/:id` (`publicModuleGuard('REAL_ESTATE')`).
  **Real Estate phase 2: sales deals and agent commission.** Schema: `V13__real_estate_deals.sql` (needs V11/V12; restart applies it) adds `re_deal`, `re_deal_milestone`, `re_deal_commission` and three settings columns (`default_commission_percent`, `commission_account_id`, `sale_product_id`). **A deal** (`DL-0001`...) is the sale of one property to one buyer and moves *Offer, Reserved, Agreement signed, Completed* (or *Cancelled* from any open stage). Several offers can compete for a property (even while it is reserved); *Accept and reserve* takes the property off the market (status Reserved, one reserving deal per property), turns the buyer into a customer (from the lead, or by phone number, or a new one) and puts a deposit and a balance in the payment schedule; the agreement needs a schedule that adds up to the agreed price; *Complete* needs every payment invoiced and paid (an administrator can force it), marks the property **Sold**, the lead **Won**, and cancels the other open offers. Cancelling drafts and voids unpaid invoices (a paid invoice stops it: refund in Invoices first) and puts the property back on the market. While a deal holds a property its status cannot be changed by hand, and a property or lead with deals cannot be deleted. **Buyer's payments:** each payment is a row (deposit, instalment, balance, other) that is invoiced on its own through the normal invoices under a "Property sale" service product (made on first use); payments are recorded on those invoices (Record payment) and the deal page follows them; "Make a plan" spreads what is left over a deposit % and N instalments. **Commission:** the agency's commission is the settings default % (editable per deal) of the price, or a fixed amount; it is split into shares (selling agent, listing agent, referrer, outside agency; each a % of the commission or a fixed amount; the shares can never add up to more than the commission). Once the sale is complete a share is *posted to accounts*: it logs an expense (account from the settings or chosen, payee a staff member or a vendor, unpaid) that waits for approval in Expenses and is settled there when paid out (the deal page shows posted, approved, paid; *Take back* after a rejection). The agency's own commission can be invoiced to the seller (the property's owner becomes a customer) with one click. Overview gets deals in progress, sold this month and commission still to post; reports **Real Estate deals** and **Real Estate commission**; sidebar link *Deals*; property pages show their deals and get *Record an offer*, leads too. Code: backend `ReDeal`, `ReDealMilestone`, `ReDealCommission`, `ReDealService`, `dto/RealEstateDealDtos`, `dto/RealEstateDealRequests`, endpoints `/api/realestate/deals/...`, `ReReportController` (`re-deals`, `re-commission`); frontend `realestate/deal-list|deal-form|deal-detail.component.ts`. **Accounting fix made on the way:** approving or voiding an expense (and posting an income) looked the account up by its display code as if it were an internal key, which fails with a country chart (Rwanda, Chad) with HTTP 500; `AccountingService` now posts to the very account chosen (`addAccountLine`).
  **Real Estate phase 3: developer projects, units and the price list.** Schema: `V14__real_estate_projects.sql` (needs V11–V13; restart applies it) adds `re_project`, `re_unit`, `re_unit_price_change` and `re_unit_sale`. **A project** (`PJ-0001`...) is a development (an estate of plots, an apartment block) with an area, developer, launch and handover dates and a status: *Planning* (set units and prices up; nothing can be reserved), *Selling*, *Completed*, *Archived* (read-only). **Units** (plots, apartments, townhouses, villas, shops, offices) have a number that is unique in the project, a block or phase, floor, bedrooms, bathrooms, size and a list price (price per m² is worked out); *Add many* makes a run such as A-01 to A-40 in one go (up to 500). A unit is *Available*, *Reserved*, *Sold* or *Withheld* (not for sale, set by hand); reserving and selling happen only through a sale. **The price list** is the units with their prices; *Change prices* moves the prices of the units still on sale by a percentage (rounded to a step, filtered by block or type, with a preview first) and every change, single or bulk, is kept in the unit's price history with a reason; a reserved or sold unit keeps the price of its sale. **A unit sale** (`US-0001`...) is *Reserved* (unit held, optional expiry date, agreed price defaults to the list price, the difference is shown as discount), then *Agreement signed* (one invoice for the agreed price is made and issued to the buyer under the "Property sale" service product; the unit becomes Sold and the lead Won), then *Paid in full*. After the agreement the buyer either pays the invoice in one go or gets a **payment plan**: this is the existing Installments module (an optional deposit paid now, N instalments, frequency, interest, grace days and late fees, reminders), made on the sale's invoice from the sale page with a preview first; instalments are recorded on the plan page, and payments recorded directly on the invoice count too. Cancelling a sale before anything is paid voids the invoice, cancels the plan and puts the unit back on sale; once the buyer has paid, refund in Invoices first. *Paid in full* needs the invoice paid (an administrator can force it). Overview gets *Project units for sale* and *Units sold this month*; reports **Real Estate projects**, **Real Estate price list** and **Real Estate unit sales**; sidebar links *Projects* and *Unit sales*. Code: backend `ReProject`, `ReUnit`, `ReUnitPriceChange`, `ReUnitSale`, `ReProjectService`, `ReUnitSaleService`, `ReProjectController`, `dto/RealEstateProjectDtos`, `dto/RealEstateProjectRequests`, endpoints `/api/realestate/projects`, `/units`, `/unit-sales`; frontend `realestate/project-list|project-form|project-detail|unit-detail|unit-sale-list|unit-sale-detail.component.ts`.
  **Real Estate phase 4: documents and valuations.** Schema: `V15__real_estate_documents.sql` (needs V11–V14; restart applies it) adds `re_document`, `re_document_blob` and `re_valuation`. **Documents** can be attached to a property, a project, a unit, a deal, a unit sale, a lead or a valuation (a *Documents* panel at the bottom of each of those pages): title deeds, sale agreements, surveys and plans, permits, ID documents, valuation reports, floor plans. PDF, JPEG/PNG/WebP, Word (.docx), Excel (.xlsx) and text files up to 20 MB, at most 100 per record; the file type is checked by content, not only by name, and files are stored in the database (the bytes in their own table) and sent back only as downloads. A document can carry an *expiry date* (a permit, a licence, an ID); it shows *Expired* or *N days left* (30-day warning). Name, kind, note and expiry can be changed without re-uploading; deleting a record deletes its documents. **Valuations** (a panel on the property page): date, valuer and firm, purpose (sale, mortgage, insurance, review), method, market value, forced-sale value, monthly rent and a *valid until* date; the newest valuation that is still valid is the *current* one and is compared with the asking price (+/- %). Each valuation can hold its report as a document. On an edit an empty forced-sale value or rent clears it. Overview gets *Documents expiring* and *Valuations expired*; reports **Real Estate valuations** (current only, or all) and **Real Estate documents** (those with an expiry date, soonest first, or all). No document versions, no confidential flag and no sharing outside the staff yet. Code: backend `ReDocument`, `ReDocumentBlob`, `ReValuation`, `ReDocumentService`, `ReValuationService`, `ReDocumentController`, `dto/RealEstateDocDtos`, `dto/RealEstateDocRequests`, endpoints `/api/realestate/documents` and `/valuations`; frontend `realestate/re-documents.component.ts` and `re-valuations.component.ts`.
  **Property Management phase 1: owners, managed properties, tenants and leases (a module of its own, separate from Real Estate).** Schema: `V16__property_management.sql` (needs V11–V15; restart applies it) adds `pm_settings`, `pm_counter`, `pm_owner`, `pm_property`, `pm_unit`, `pm_tenant` and `pm_lease`. Switch it on in Settings > Modules. **Owners** (`OW-0001`...) are the landlords you manage for, with bank details and an optional management fee %. **Properties** (`MP-0001`...) belong to an owner and hold **units** (flat, shop, office, room, house...), added one at a time or many in one go (A-01 to A-40); a unit has floor, bedrooms, size, asking rent and can be set off-market. **Tenants** (`TN-0001`...) are people or companies. **Leases** (`LS-0001`...) start as a draft; *Sign the lease* makes it active and turns the tenant into a customer (matched by phone, or created) so rent can be invoiced later. A lease moves through *Upcoming, Current, Expiring* (inside the warning window, with no signed renewal), *Renewed*, *Expired*, *Ended* and *Terminated*. *Renew* makes a draft for the next term at the rent plus the agreed yearly increase; the old lease keeps running until its end date and then shows as Renewed. *Terminate early* records the day the tenant left and why; *Mark as ended* closes a lease that has run out; an administrator can *End now*. A unit is Occupied, Upcoming (a signed lease that has not started), Vacant (with the date it fell vacant) or Off-market, and two leases cannot overlap on one unit. The management fee is the property's, else the owner's, else the settings default. Overview shows occupancy, rent roll, vacant units with the rent they could bring in, and leases ending soon; reports **Property Management rent roll**, **vacancy**, **lease expiry** and **owners**; sidebar group *Property Management*. Not yet: rent invoicing and arrears, deposits and receipts, maintenance, owner statements, documents on leases and tenants, any tenant or owner portal. Code: backend `propertymgmt/` (`PmOwner`, `PmProperty`, `PmUnit`, `PmTenant`, `PmLease`, `PmViews`, `Pm*Service`, `PropertyManagementController`, `PmReportController`, `dto/PropertyMgmt*`), endpoints `/api/propertymgmt/...`; frontend `admin/propertymgmt/*.component.ts` and `core/api/propertymgmt-api.service.ts`.
  **Property Management phase 2: rent invoicing and arrears.** Schema: `V17__property_management_rent.sql` (needs V16; restart applies it) adds `pm_rent_charge` and three settings columns (`invoice_ahead_days`, `auto_invoice`, `rent_product_id`). **Signing a lease lays out its rent**: one charge per payment period (monthly, every 3 or 6 months, yearly), each with a due date (the lease's due day in the month the period starts, or the first day of the period if that day has passed). A last period shorter than a full one is charged by the day. *Bill rent from* on the sign panel leaves out periods that ended before a date, for a lease signed late whose earlier rent was settled outside. **Invoicing**: *Invoice the rent that is ready* (Rent page, or per lease) makes an invoice for every charge due within the lead time (default 7 days, Settings), issued to the tenant as a customer under a "Rent" service product made on first use; with *Invoice rent automatically every morning* on in Settings the same thing runs daily at 06:20. Each charge is its own transaction, so one failure does not stop the rest. Payments are recorded on the invoice as everywhere (Record payment links are on the rent lines). **Status of a period**: Not invoiced, Invoiced, Part paid, Paid, Overdue (unpaid past the due date, with days late) or Waived (a reason is kept; it can be restored; rent already invoiced cannot be waived, void the invoice instead). A voided invoice puts the period back to Not invoiced so it can be invoiced again. Ending a lease early drops the rent for later periods and cuts the period the tenant left in to the day, charged by the day; invoices already made stay and the lease page warns about any that fall after the tenant left. Changing the due day on a lease in force moves the dates of rent not yet invoiced. **Arrears** page: overdue rent by tenant with ageing (1 to 30, 31 to 60, 61 to 90, over 90 days) and the invoices behind it, also on the tenant page. Overview gets *Rent to invoice* and *Rent in arrears*; reports **Property Management arrears** and **rent collection** (by month: invoiced, collected, still owed, not yet invoiced); sidebar links *Rent* and *Arrears*. Not yet: deposits and receipts, owner statements and the management fee (PM-4), maintenance (PM-3), late fees, rent in advance or credit carried forward. Code: backend `PmRentCharge`, `PmRentChargeRepository`, `PmRentService`, `PmRentSweep`, `dto/PropertyMgmtRentDtos`, endpoints `/api/propertymgmt/rent`, `/rent/invoice`, `/rent/{id}/invoice|waive|restore`, `/leases/{id}/rent`, `/arrears`; frontend `rent-schedule`, `rent-list`, `arrears` components.
  **Property Management phase 3: maintenance (repair requests).** Schema: `V18__property_management_maintenance.sql` (needs V17; restart applies it) adds `pm_maintenance` and `pm_settings.maintenance_product_id`. A **request** (`MR-0001`...) is logged against a unit: what is wrong, kind of job (plumbing, electrical, structure, painting, appliance, security, cleaning, garden, pest, other), priority (low, normal, high, urgent), reported date and by whom, planned and due dates, contractor, estimate. The tenant and lease in the unit on that day are filled in automatically. Steps: *Start the job*, *Put on hold*, *Resume*, *Mark as done* (finish date, actual cost, who pays, what was done; the date cannot be in the future or before it was reported), *Cancel* (a reason is needed), *Reopen*. A request that is done or cancelled cannot be edited until it is reopened; only a request that is open or cancelled, with no work order or invoice, can be deleted. **Who pays**: the owner, the tenant or the agency. When the tenant pays and the job is done with a cost, *Invoice the tenant* makes an invoice to the tenant's customer record under a "Maintenance recharge" service product (due in 14 days); it cannot be done twice, a request with a live invoice cannot be reopened (void the invoice first), and a voided invoice lets you invoice again. With the Work Orders module on, *Raise a work order* makes an internal Work Order titled with the request code, property and unit (priority, dates and contractor carried over), shown with its status on the request; *Unlink* detaches it. A unit or tenant that has requests cannot be deleted. Overview gets *Open repairs* (with urgent and overdue counts) and a *Maintenance needing attention* list; the unit, property and tenant pages list their requests; reports **Property Management maintenance requests** and **maintenance cost** (per property: owner, tenant and agency share of finished jobs); sidebar link *Maintenance*. Not yet: photos or documents on requests, cost pulled from the Work Order (it is typed in), recurring or preventive jobs, owner statements that deduct the owner's repair cost (PM-4), any tenant portal for logging requests. Code: backend `PmMaintenance`, `PmMaintenanceRepository`, `PmMaintenanceService`, `dto/PropertyMgmtMaintenanceDtos`, endpoints `/api/propertymgmt/maintenance` (`/{id}/start|hold|complete|cancel|reopen|work-order|invoice-tenant`); frontend `maintenance-list`, `maintenance-form`, `maintenance-detail`, `maintenance-panel`, `maintenance-attention` components and `core/api/pm-maintenance-api.service.ts`.
  **Property Management phase 4: owner statements and the management fee.** Schema: `V19__property_management_statements.sql` (needs V18; restart applies it) adds `pm_owner_statement` and `pm_statement_line`. A **statement** (`ST-0001`...) is made for one owner and a period (it can end today at the latest, up to a year long, and two statements of one owner cannot overlap), or for every owner at once. It adds up the **rent collected**: payments dated in the period on rent invoices of the owner's properties (counted on the day the payment was recorded, not the due date; an invoice that was voided drops out). It takes off the **management fee**, worked out property by property: the property's own percentage, else the owner's, else the default in Settings; a warning shows when none is set. It takes off the **repairs the owner pays for**: maintenance requests marked done in the period with the cost bearer set to the owner. You can add **manual lines** (a positive amount adds to what the owner gets, a negative one takes from it). The result is the **net to the owner**. A statement starts as a **draft** (*Rebuild* reads the payments and repairs again and keeps your manual lines; a draft can be deleted), then *Agree the statement* locks it (you can go back to draft), then *Pay the owner* records the payout date, method and reference (only from the end of the period, never in the future); *Undo payment* steps back. A net of zero or less can be settled without a payment method. A negative net shows a warning and is not carried forward by itself: add it to the next statement as a line. Each statement downloads as a PDF or Excel copy for the owner. An owner with statements cannot be deleted. Overview gets *Owed to owners* (agreed statements not yet paid); the owner page lists its statements; report **Property Management owner statements**; sidebar link *Owner statements*. Not yet: VAT on the fee, an owner portal. Code: backend `PmOwnerStatement`, `PmStatementLine`, `PmStatementService`, `PmStatementController`, `PmPaymentQueryRepository`, `dto/PropertyMgmtStatementDtos`, endpoints `/api/propertymgmt/statements` (`/generate-all`, `/{id}/refresh|finalize|reopen|pay|unpay|lines|notes|export`); frontend `statement-list`, `statement-detail`, `statements-panel` components and `core/api/pm-statement-api.service.ts`.
  **Property Management phase 5: owner statements are booked to the accounts.** Schema: `V20__property_management_statement_ledger.sql` (needs V19; restart applies it) adds `agreement_posted` and `payout_posted` to `pm_owner_statement`. Rent invoices are booked as sales revenue when they are issued, but that rent is the owner's money. So **agreeing a statement** posts one journal entry: debit Sales revenue for the rent collected; credit *Management Fee Income* for the fee (plus any manual deductions you added, less any manual additions); credit *Repairs and maintenance expense* for the repairs recharged to the owner; credit *Owners' Funds Payable* for the net owed to the owner. **Paying the owner** posts debit Owners' Funds Payable, credit Cash for the net (nothing moves when the net is zero or less). **Reopening** a statement and **undoing a payment** post the opposite entries, so the books always follow the status. The two new accounts (*Owners' Funds Payable*, a liability, code from 2880; *Management Fee Income*, revenue, code from 4300) are created on first use if the chart does not have them, and the default chart is seeded first when the chart is empty. A statement agreed before this phase has no entry; paying it posts the missing agreement entry first, and reopening it posts no reversal. The statement page says whether it has been booked, and shows a hint while it is a draft. Limits: rent that is invoiced but not collected stays in revenue; the repair recharge assumes the contractor's bill was entered as an expense (Accounting > Expenses, Repairs and maintenance), otherwise the books show a credit with no matching cost; no VAT on the fee; entries are not tagged to a branch; a negative net leaves a debit balance in Owners' Funds Payable until a later statement recovers it. Code: backend `PmLedgerService`, changes to `PmStatementService`/`PmOwnerStatement`, `StatementDto.postedToAccounts`; frontend `statement-detail`.
  **Property Management phase 6: e-mail owner statements.** Schema: `V21__property_management_statement_email.sql` (needs V20; restart applies it) adds `emailed_at` and `emailed_to` to `pm_owner_statement`. An agreed or paid statement has an **E-mail to owner** button: it sends the PDF (the same file as the download) with the totals in the message, to the e-mail address on the owner or to one you type in, with an optional note above the statement. A paid statement's message also says how and when it was paid. A draft cannot be sent, because it can still change. It uses the e-mail (SMTP) settings under Settings > Email and shows a clear message when that is not set up, when the owner has no address, or when the mail server refuses. The statement page shows who it was last sent to and when; sending again is allowed. Sending the statement back to draft clears that record (the audit log, action `PM_STATEMENT_EMAILED`, keeps the history). Only people who can manage Operations may send. Not yet: sending every agreed statement in one go, a copy to a staff address, e-mail reminders, an owner portal. Code: backend `PmStatementMailService`, `PmStatementFiles` (the PDF and Excel copy, now shared by the download and the e-mail), `POST /api/propertymgmt/statements/{id}/email`, `StatementDto.ownerEmail/emailedAt/emailedTo`; frontend `statement-detail`.
  **Property Management phase 7: VAT on the management fee.** Schema: `V22__property_management_fee_vat.sql` (needs V21; restart applies it) adds `pm_settings.fee_vat_percent`, `vat_amount` and `vat_percent` on `pm_owner_statement`, and a `VAT` line kind. In Settings, **VAT on the management fee (%)** is empty by default, which means no VAT. With a rate set, every statement gets a **VAT line under each property's fee** (the fee times the rate, rounded to the cent) and the VAT is taken from the owner: net to the owner = rent − fee − VAT − repairs ± adjustments. The statement page, the PDF and Excel copy, the e-mail and the **owner statements report** all show it. A statement keeps the rate it was built with: a draft shows a warning when the rate in Settings has changed, and *Rebuild* applies the new rate; an agreed statement is never changed. In the accounts, agreeing a statement now also credits **Tax Payable** with the VAT (the fee income account still gets only the fee), and reopening reverses it; paying the owner pays the net after VAT. Statements agreed before this phase have no VAT. Limits: only the fee carries VAT (not rent, repairs or manual lines); the VAT is not shown as a separate tax invoice to the owner, so issue your own tax invoice for the fee if the tax authority requires one; one rate for all properties. Code: backend `PmStatementService`, `PmLedgerService`, `PmSettings`, `StatementRow.vat`; frontend `statement-detail`, `pm-settings`.
  **Property Management phase 8: e-mail all agreed statements.** No schema change. On the Owner statements page, **E-mail agreed statements** sends the PDF of every agreed statement that has not been e-mailed yet to its owner's address, in one press. Options: also the paid ones, send again those already e-mailed, and only the statements shown in the list (after an owner filter, say). Owners with no address are skipped and listed afterwards; a bad address or a refusing mail server is counted and explained, and never stops the others. Drafts are never sent. At most 100 are sent per press (the result says how many are left; press again). It uses the same settings, PDF, wording and record as the single e-mail (phase 6), writes one audit line per message (`PM_STATEMENT_EMAILED`) and one summary line (`PM_STATEMENTS_EMAILED`), and only people who manage Operations can use it. Code: backend `PmStatementMailService.emailAll`, `POST /api/propertymgmt/statements/email-all`, `EmailAllStatementsRequest`, `EmailAllResult`; frontend `statement-list` and `pm-statement-api.service.ts`.
  **Property Management phase 9: the monthly run (last feature before version 1).** Schema (before the squash, now inside V1): four columns on `pm_settings`: `auto_statements` (default off), `auto_statements_day` (default 5), `auto_send_statements` and `auto_statements_through`. In *Property Management > Settings*, section **Owner statements every month**: switch it on, choose the day of the month (1 to 28), and optionally **agree and e-mail them**. Every morning at 06:40 (server time) `PmStatementAuto` checks whether the switch is on, today is on or after the chosen day and last month has not been done yet; if so it makes the draft statements for the previous calendar month for every owner (same rules as *Make statements for a month*). With *agree and e-mail* on, it agrees and e-mails only the statements that have no warnings and where the owner is owed money; everything else stays a draft with a note saying why ("owner would get nothing", "owner would owe money", or the first warning), so a person decides. The run is done as "The monthly run", is audited (`PM_STATEMENTS_AUTO`) and remembers the month it finished (`auto_statements_through`), so a restart or a second check the same day does nothing. The button **Make the statements for last month now** (`POST /api/propertymgmt/statements/auto-run?force=true`, admin only) runs it at once, ignoring the day. Settings are admin only. Limits: a run for a month missed because the server was off all the morning is made on the next day the server is up; if e-mail is not set up the statements are still agreed but not sent, and the result says so.

  Verification: 43 scripted checks against the running API (not-due day, switch off, run once, no second run, drafts left with the right notes, agree and e-mail to a test mail server, permission refusals) and a browser walk of the settings screen; the full regression of the nine Property Management suites (177, 97, 104, 91, 64, 56, 52, 38, 43 checks) was then repeated on a database built only from the single V1.
  Code: backend `realestate/` (entities `ReProperty`, `RePropertyPhoto`, `RePhotoBlob`, `ReLead`, `ReLeadNote`, `ReViewing`, `ReSettings`, `ReCounter`; services `RePropertyService`, `ReLeadService`, `ReViewingService`, `ReOverviewService`, `ReSettingsService`; `RealEstateController`, `ReReportController`, `RealEstateAccess`), `ModuleKey.REAL_ESTATE`; frontend `admin/realestate/*`, `core/api/realestate-api.service.ts`, three entries in `core/report-defs.ts`, routes `admin/realestate/*`, sidebar group.

- **2026-10-16 — Property Management improvements, version 1.2.0 (phases PM-10 to PM-18).** Task: improve the modules one by one before installing at customers, Property Management first; the user chose the audit items one phase at a time. Schema: `V3` to `V8` (never edit a delivered migration; the next is V9). All endpoints are under `/api/propertymgmt` unless noted; Operations viewers read, Operations managers and admins change; reports need admin or the Reports area.
  **PM-10 phone layout and paging.** The global `table.stack` style turns a table with `data-label` cells into cards up to 768 px. Owners, properties, units, tenants and leases page on the server (`page` from 0, `size` 1..500, header `X-Total-Count`); rent, maintenance and statements page on the screen. Shared `app-pager` and `core/api/paged.ts`.
  **PM-11 tenant account and receipt.** `GET tenants/{id}/account` (+ `.pdf`, `POST account/email`), `GET payments`, `GET payments/{id}/receipt` (+ `.pdf`, `POST receipt/email`). Rent counts when invoiced; payments are the money recorded on those invoices; running balance and balance brought forward.
  **PM-12 reminders and late fees** (`V3__pm_reminders_late_fees.sql`). Morning job 06:50 (`PmReminderJob`): reminder e-mail before the due date and when late (first after X days, then every Y days, at most Z; never twice on a day). Late fee (percentage with cap, or fixed) after a grace period, once per rent, invoiced to the tenant under the product "Late rent fee"; agency income, not part of the owner statement. `reminders/settings` (PUT admin only), `reminders/due`, `reminders/run`, `rent/{id}/remind`, `rent/{id}/late-fee`.
  **PM-13 reports and exports.** Reports `pm-deposits`, `pm-rent-increases`, `pm-trend`, `pm-contractors` (json, xlsx, pdf). `GET export/{owners|tenants|leases}?format=xlsx|pdf|csv` with the list filters, always the whole result.
  **PM-14 documents** (`V4__pm_documents.sql`). Files on a property, unit, owner, tenant, lease or repair request: PDF, JPEG, PNG, WebP, docx, xlsx, text, up to 20 MB and 100 per record; the first bytes must match the type; optional expiry (OK, SOON within 30 days, EXPIRED). `documents` (GET, POST multipart, `{id}/file`, PUT, DELETE). Report `pm-documents`, overview card for lapsing documents.
  **PM-15 deposits and inspections** (`V5__pm_deposits_inspections.sql`). The deposit of a lease is a list of movements that are never deleted (received, transfer in or out, deduction, refund); held = in minus out. Keeping for unpaid rent records a payment with method DEPOSIT on the rent invoice; other deductions are agency income. Accounts 2890 "Tenant Security Deposits" and 4310 "Tenant Charges Recovered" are made on first use. A mistaken entry can be voided (kept, reversed). One move-in and one move-out inspection per lease with a room list, conditions, repair costs, sign-off, photos, PDF and Excel. `deposits`, `deposits/totals`, `leases/{id}/deposit/{receive|deduct|refund|transfer}`, `deposits/entries/{id}/void`, `inspections`.
  **PM-16 tax invoice for the management fee** (`V6__pm_fee_invoices.sql`). Made when a statement is agreed, numbered FI-0001, one line per property; headed "Tax invoice" when VAT was charged. A document only: the statement already books the fee and the VAT. Reopening the statement voids it (number kept); agreeing again makes a new number. Owner and agency details are copied onto it. The e-mailed statement carries it. `fee-invoices`, `fee-invoices/totals`, `fee-invoices/{id}/pdf`, `statements/{id}/fee-invoice` (GET, POST). Report `pm-fee-invoices` for the VAT return.
  **PM-17 utilities and service charges** (`V7__pm_charges.sql`). Charge types (fixed monthly, or metered with a unit and a rate), charges on a lease (from and until dates, billed per rent period and cut by the day), meter readings and one-off charges (lease in force only) become lines on the tenant's rent invoice. The money is the agency's: tenant payments on such an invoice go to the rent first and only the rent part counts for the owner statement and the fee; a percentage late fee is worked out on the unpaid rent only. `charge-types`, `leases/{id}/charges`, `lease-charges/{id}`, `lease-charges/{id}/readings`, `leases/{id}/extras`, `extras/{id}`; report `pm-charges`.
  **PM-18 co-owners** (`V8__pm_co_owners.sql`). A property's owners are kept as splits: owners and percentages (two decimals, exactly 100, up to 12) from a date, one of them the main owner. The split in force on the day a payment was made shares it; the main owner takes the rounding cents. Each owner has their own statement (rent share, the property's fee percentage on their part, repairs by share) and their own fee invoice. A split an agreed statement covers is locked; draft statements to rebuild are listed after a change. `pm_property.owner_id` keeps the main owner of the newest split, so every screen that shows "the owner" is unchanged, and every existing property starts at 100%. `properties/{id}/owners` (GET; PUT adds or replaces the split of a date; DELETE `/{start|yyyy-MM-dd}`). Owner filters (properties, units, leases, rent, arrears, maintenance, deposits) include co-owners.
  Verification: backend suites on PostgreSQL 16 (177, 97, 104, 91, 64, 56, 52, 38, 43 for the earlier phases; 26, 64, 105, 69, 91, 171, 83, 128, 99 for PM-10 to PM-18) and browser walks (19, 20, 47, 54, 39, 23, 33, 26) all passed in one final run after PM-18; the frontend type-checks and builds.
  Limits: charges are not taxed; readings and one-off charges only while a lease is in force; no meter reset; a statement edited after it was agreed must be reopened (which renumbers its fee invoice); no credit notes other than void and reissue; ownership is per property, not per unit; one fee percentage per property; maintenance and tenant mail go to the main owner only; damage kept from a deposit is not on the owner statement; no deposit interest; the server builds the whole filtered list before it slices a page.
  Code: backend `propertymgmt/` (`PmReminderService`, `PmDocumentService`, `PmDepositService`, `PmInspectionService`, `PmFeeInvoiceService`, `PmChargeService`, `PmOwnershipService`, `PmOwnership`, ...), frontend `admin/propertymgmt/*` and `core/api/pm-*-api.service.ts`.

- **2026-10-16 — Real Estate improvements, version 1.3.0 (phases RE-1 to RE-8).** Task: improve the modules one by one before installing at customers, Real Estate second; the user chose the phases one at a time. Schema: `V9` to `V12` (never edit a delivered migration; the next is V13). All endpoints are under `/api/realestate`; Operations viewers read, Operations managers and admins change; the module must be on.
  **RE-1 phone layout and paging.** `RePaging` (optional `page` and `size`, `X-Total-Count`) on properties, leads, deals, viewings, projects, units and unit sales; without them the whole list comes back as before. Lists page 25 at a time and turn into cards on a phone.
  **RE-2 list exports.** `export/{properties|leads|deals|projects|units|unit-sales|viewings}` as Excel (default), PDF or CSV with the screen's filters, always the whole list; CSV has a byte-order mark and neutralises values starting with `=`, `+`, `-` or `@`.
  **RE-3 lead follow-ups** (`V9__re_follow_ups.sql`). `ReFollowUpService` board (late, due today, coming up, gone quiet), `PUT leads/{id}/follow-up`, morning e-mail job 06:30 (`ReFollowUpJob`, only when switched on) with preview and `POST follow-ups/send` (admin).
  **RE-4 viewing reminders and week calendar** (`V10__re_viewing_reminders.sql`). `ReViewingReminderService` and a 15-minute job: a reminder to the lead and one e-mail per agent; `POST viewings/{id}/remind`, `POST viewings/reminders/send?preview=` (admin); settings `viewingReminderEnabled` and `viewingReminderHours` (1 to 72, default 24). A List/Week toggle on the viewings screen.
  **RE-5 printable documents** (`V11__re_print_terms.sql`). `RePrintFiles` and `/print`: deal offer, reservation, agreement, payments and receipt; unit-sale reservation, agreement, payments and receipt; project price list; property flyer. Wording can be replaced in the settings (up to 3000 characters each). A paper that does not belong to the record's stage is refused (409).
  **RE-6 unit-sale collections** (`V12__re_sale_collection.sql`). `ReCollectionService`: `GET collections` (late, due soon, no schedule), collection notes (call, promise, note), `POST unit-sales/{id}/remind`, buyer statement PDF and `statement/email`, `export/collections`. The automatic reminders and late fees stay in the Instalments module.
  **RE-7 commission payouts and statements** (no schema change). `ReCommissionService`: `GET commissions/payouts` groups every commission share by person (staff by user, outsiders by name) with its payout state (planned, to post, awaiting approval, to pay, paid, rejected), `POST post-due`, statement PDF, `POST statement/email` (staff with an e-mail address), `export/commissions`. Payment stays in Expenses.
  **RE-8 imports** (no schema change). `ReImportService`, `/import/{leads|properties}`: `GET template?format=xlsx|csv`; `POST ?dryRun=true` (default, saves nothing) or `dryRun=false`, with `listThem` for properties. Reads .xlsx (first sheet) or CSV; headings matched by name with aliases; per-row errors with spreadsheet row numbers; duplicates by phone digits or e-mail (leads) and by title with address or area, or reference (properties); maximum 2000 rows; an `RE_IMPORT` audit record.
  Verification: backend suites on PostgreSQL 16 (27, 46, 58, 39, 75, 85, 81, 87 for the eight phases) and browser walks (80, 20, 37, 36, 37, 29, 26, 27); the whole earlier set re-run after the last phase with no failure; the frontend type-checks and builds.
  Limits: the server builds the full list before paging; unit sales have no commission shares; imports read the first sheet only, up to 2000 rows, no `.xls`; a check does not simulate a race with other users.
  Code: backend `realestate/` (`RePaging`, `ReListExportController`, `ReFollowUpService`, `ReViewingReminderService`, `RePrintFiles`, `RePrintService`, `ReCollectionService`, `ReCommissionService`, `ReImportService`), frontend `admin/realestate/` (`re-export`, `re-print`, `re-collections`, `re-commissions`, `re-import` components).

## Known limitations / where to go next

- **Compiling and testing, as of version 1.0.0.** The backend compiles and was exercised over its HTTP API against PostgreSQL 16 during development (the scripted suites are described in the entries above), and the frontend type-checks with `ngc`. What was never run in the build environment: Flyway itself (Maven Central is not reachable there, so the migration was applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), the morning jobs firing on their own clock, real customer data, and the screens on a phone or in the light theme. **First check on your machine: start the backend on an empty database and watch Flyway apply V1.**
- **Real Estate, as of 1.3.0.** Tested over the HTTP API and in a browser against PostgreSQL 16 (see the 1.3.0 entry for the counts). Not tested: Flyway applying V1 to V12 on your machine, a real SMTP server, the 06:30 job and the 15-minute viewing reminders firing by themselves (they were run through their endpoints), real data, and PDF headers from real store settings.
- **Property Management, as of 1.2.0.** Tested over the HTTP API and in a browser against PostgreSQL 16 (see the 1.2.0 entry for the counts). Not tested: Flyway applying V1 to V8 on your machine, a real SMTP server, the 06:40 and 06:50 jobs firing by themselves (they were run through their endpoints), real data, and PDF headers from real store settings.
- **Licensing, as of 1.1.0.** Tested over the HTTP API and in a browser against PostgreSQL 16 with a throw-away key; not tested with your real key, with a real mail server for module requests, or on Flyway running V2 on your machine. The server-side refusal covers only module-specific endpoints (see "Module licensing", Limits).
- Returns endpoints aren't role-gated yet — registers/shifts are now `ADMIN`-only for writes (see
  "Auth" above), and so are catalog, customers, staff accounts, and the dashboard, but any
  logged-in cashier can still process a return. Add `@PreAuthorize("hasRole('ADMIN')")` to
  `ReturnsController` if you want that restricted too.
- Stock is held per warehouse location, with purchase orders, stock counts, in-transit transfers and
  optional batch/serial tracking (see the 2026-10-09 inventory entry under "What's included");
  `Product.stockQuantity` is only a mirror of the primary warehouse's SALES location. The older
  "single-store" notes elsewhere in this file are historical.
- Tax is per-product now (see **Taxes** above), but still no tax *jurisdictions* (e.g. different
  rates by region/customer) — one rate per product, store-wide. See the **Known trade-off** note
  under **Taxes** above for what happens to tax figures on sales rung before this pass's
  migration (they now read $0 tax, since those `OrderLine` rows have no snapshotted rate to read).
- No integration tests yet. `OrderService`/`RegisterService`/`ReturnsService` are where the
  money math lives — that's the highest-value place to add them first.
- The JWT secret in `application.properties` (`app.jwt.secret`) is a placeholder — replace it
  before any real deployment, and consider moving it to an environment variable.
- Every service method that maps a JPA entity with a `LAZY` association to a DTO needs
  `@Transactional` wrapping the *whole* method (not just the repository call) — a Spring Data
  repository call is transactional on its own, but that transaction closes as soon as the call
  returns, before any DTO mapping that follows it runs. `CatalogService` was missing this
  entirely and threw `LazyInitializationException: could not initialize proxy ... - no Session`
  on `Product.category` the first time it ran against real Postgres; it's now fixed there and in
  `UserService`, but keep the pattern in mind for any new service.
- The Receipts list (both the standalone `/receipts` page and the in-terminal panel) is capped at
  the 200 most recent completed sales — no pagination or date-range filter yet.
- Payment methods have no delete, only deactivate — intentional (a deleted method would orphan
  the string stored on historical `Payment`/`SalesReturn` rows), but there's currently no admin UI
  affordance explaining *why* delete is missing beyond the form's help text.
- Shifts (`/admin/shifts`) is a read view over existing session data — it doesn't add scheduling,
  shift assignment, or labor-cost reporting; "shift" here means "till session," not a rostering
  concept.
- Closing a cashier session (alone or via "Close all sessions") discards any cart that cashier
  never checked out — same as the terminal's **Discard** button — rather than transferring it to
  another cashier or leaving it "parked" for later. If that's too aggressive for your workflow
  (e.g. a cashier steps away mid-sale and someone else should be able to pick the cart back up),
  the discard call in `CashierSessionService.close`/`closeAllForShift` is the place to change —
  e.g. reassign the order's `cashierSession` to the shift's still-open opener session instead of
  voiding it.
- Both "opening the terminal" (this pass's PIN gate) and "Switch cashier" resolve who's active by
  PIN alone with a linear scan over active, PIN-enabled staff
  (`CashierSessionService.findByPinOrThrow`) — fine for a normal-sized staff roster, but not the
  approach to keep if this ever needed to scale to hundreds of active PINs.
- **A staff member with no PIN set can no longer use the terminal at all** — this pass's PIN gate
  (Task: "should ask PIN and open related session based on the PIN entered") removed the old
  silent "my own session" fallback that used to open the terminal for whoever was logged in with
  no PIN needed. Set every cashier's PIN from Admin > Staff before they need to ring a sale.
  A real browser **reload** of the terminal still re-shows the PIN screen every time, rather than
  resuming a session automatically — arguably a fix, not just a trade-off, since it also removes
  the earlier limitation where a page reload silently fell back to the JWT identity regardless of
  who had actually switched in. **Navigating back to the terminal in-app** (from Checkout or the
  receipt screen) no longer re-prompts, though (`TerminalSessionService` — see **Terminal**
  above); the in-memory cache that skips the PIN screen is scoped to the current browser tab's
  session, so opening the same register session in a *second* tab (or after a reload) still
  prompts for a PIN there, same as before.
- The new Reports export (`ReportExportService`, Apache POI + OpenPDF) is, like the rest of this
  backend, not compiler-verified in this sandbox (see the note above) — if either dependency's
  Maven coordinates/version need adjusting once you actually build, that file plus `pom.xml` is
  the whole surface area to check.
- The terminal's customer search (`filterCustomers`) filters an already-loaded, in-memory list
  client-side on every keystroke — fine at normal customer-list sizes, but it fetches the *whole*
  customer list once on terminal load rather than searching server-side, so a store with a very
  large customer base should switch this to a debounced `GET /api/customers?q=...`-style backend
  search instead.
- The terminal's inline **👤+ Add** customer panel has no duplicate check — creating "Jane Doe"
  twice from the terminal (once by two different cashiers, say) makes two separate customer rows;
  the same is already true of the Admin > Customers form, this pass just added a second place that
  can produce it.
- Checkout's per-method amount cap (Task: "if the total amount to pay is already input do not
  allow input extra amount") only prevents *overpaying* — it does not require the entered amounts
  to add up to the full total before "Complete sale" is enabled; checkout has always allowed
  submitting less than the total due (an underpayment), and this pass didn't change that.
- **My Profile** (Task: "Add my profile, change my password") only lets you edit your own display
  name — not your username, role, or active flag; those stay admin-only, on the Staff screen.
  There's no "forgot password" flow (there's no email/SMS integration in this app at all) — if a
  user is locked out, an admin has to reset it for them from Admin > Users, same as before this
  pass existed.
- Customer `taxId` (Task: "Add Tax ID on customer entity") is purely informational — nothing in
  checkout, tax calculation, or the reports validates its format or uses it for anything; it's
  just captured for display/printing on things like the new Customer Statement.
- The Customer Statement (Task: "Add Customers Report (Customer Statement)") has no pagination
  and, like the seven POS Reports, aggregates entirely from `findCompletedForCustomerStatement`'s
  result in Java — fine for one customer's normal purchase history, potentially slow for a
  long-standing customer with thousands of sales over a very wide date range.
- The sidebar's accordion behavior (Task: "Once one menu is open other should be closed") is
  purely a display-state change — every group's set of links and every route/access guard behind
  them is unchanged; only how many groups can be visually expanded at once changed (from "any
  number" to "at most one").
- **Store credit / AR** (this pass) has a few deliberate scope boundaries, not oversights:
  - There's no "unapply"/void for a recorded `CustomerPayment` — once allocations are saved,
    correcting a mistaken payment means recording a manual adjustment some other way (there's no
    UI for it yet); the ledger is append-only.
  - `defaultCreditTermDays` and `dueDate` are calendar days from checkout time, with no per-store
    business-day or holiday calendar — "overdue" in the two new credit reports is a plain
    `LocalDate` comparison against server "today" (UTC).
  - The Outstanding/Overdue Credit reports and `CustomerCreditService.outstandingBalance` iterate
    every one of a customer's credit orders in Java (same "aggregate in Java, not SQL" convention
    as the original seven reports) — fine at normal order-history sizes, potentially slow for a
    single customer with thousands of historical credit sales.
  - The terminal's inline "add a customer" panel still can't set `creditEnabled`/`creditLimit` —
    a customer added mid-sale from the terminal has credit off by default; an admin has to visit
    Admin > Customers afterward to enable it. (This is also where a pre-existing latent bug lives,
    unrelated to this pass: that panel calls the same `ADMIN`-only `POST /api/customers` endpoint
    every other customer-creation form uses, so a non-admin cashier using it would get a 403 —
    not something this pass introduced or was asked to fix, but worth knowing about.)
  - Every credit number (`creditAmount`, `creditLimit`, allocations) is plain `BigDecimal` with no
    currency field — same single-currency assumption as the rest of this app's money handling.
- **Auto-print-then-return-to-terminal after checkout** relies on the browser's `afterprint`
  event, which every modern desktop browser (Chrome/Edge/Firefox/Safari) fires once the native
  print dialog is dismissed — but it's not part of a formal spec, so a very old or unusual browser
  could fail to fire it, which would leave the cashier on the receipt screen with no automatic
  redirect (the manual **Back to terminal** link is still right there as a fallback either way).
  There's also no way from the web app to force a specific printer, skip the OS print dialog
  entirely, or print silently — "select a printer" still means the browser's own print dialog;
  a true silent/kiosk print (no dialog at all) needs either a kiosk-mode browser launch flag
  (e.g. Chrome's `--kiosk-printing`) configured on the till's machine, or a dedicated print/agent
  service, neither of which this app controls.
- **Business details / System settings** are stored and readable via `/api/settings`. As of the
  twenty-fourth pass, the **business name and logo** are wired into both the receipt header and
  the topbar (replacing "POS") — but **System settings' currency/time zone/date format still
  aren't wired into anything**: every screen that shows a price or a date still formats it plainly,
  with no currency symbol or per-store date pattern applied anywhere in the app. Wiring that in is
  a cross-cutting change touching every price/date display in the app — out of scope for adding
  the settings screen itself, and a natural next step.
- The receipt header and topbar branding (twenty-fourth pass) only ever read `GET /api/settings`
  and degrade to their pre-pass appearance (plain "Receipt" heading, plain "POS" brand label) on
  any failure or when Business details hasn't been filled in — neither screen has its own error
  message for a failed settings fetch, by design, since a broken settings call should never block
  printing a receipt or using the app.
- The **logo upload** (Business details) is capped at 2MB client-side and stored as a raw
  `data:` URL string in the database (`StoreSettings.logoDataUrl`, a `TEXT` column) — there's no
  server-side resizing/compression, so a large-but-under-2MB image is stored (and sent back on
  every `GET /api/settings`) at its original size. Fine for a small store logo; a very
  image-heavy deployment might want actual file storage (S3-style) plus a thumbnail instead.
- The **System settings** currency picker is a curated list of about ten common codes (with an
  editable symbol field for anything not listed), not the full ISO 4217 table — good enough for
  this app's expected userbase without maintaining an exhaustive currency list.
- **Change PIN** (My Profile, this pass) reuses the same active-user PIN-uniqueness check Admin >
  Staff's "Set/Change PIN" already enforces — a cashier trying to reuse a PIN another active staff
  member currently has gets the same 409 conflict an admin would. There's still no way to *view*
  your current PIN (PINs are hashed, same as passwords) — only to overwrite it with a new one.
- **Customer display** (the **📺 Customer display** button) still requires a login, on whichever
  device shows it — there's no unauthenticated "public kiosk" access mode, because `GET
  /api/orders/{id}` stays ownership-or-admin gated while an order is OPEN (the seventh pass's
  rule, deliberately left untouched). On separate hardware that means signing that device in as
  either the same cashier working the till or an admin; anyone else gets a plain "Display
  unavailable" screen with a 403, not someone else's cart. It has no "change due" for the same
  reason the app has never had one anywhere: checkout has capped payment amounts at the order
  total since the sixteenth pass, so there's no overpayment to compute change from — the display
  shows the completed payment breakdown per method instead, once checkout finishes. It's poll-based
  (every ~2.5s), not push-based — this app has no websocket/SSE infrastructure anywhere, so a
  couple of seconds of staleness after an item is scanned is expected, not a bug. The "on screen"
  pointer (`CashierSession.activeOrderId`) is a display concern only, exactly like
  `PaymentMethodEntity.sortOrder` — nothing in checkout, reporting, or till reconciliation reads
  it. And the thank-you screen's fallback-to-idle timer (8 seconds) is just that — a fallback in
  case the terminal doesn't move on to a new tab quickly; normally the *next* tab switch's
  `activeOrderId` change preempts it well before the timer fires.
- **Product paging caps** (this pass — "check the loading of 100000 products") are fixed
  ceilings, not configurable settings: the admin product list clamps `size` to 200 server-side no
  matter what's requested (`CatalogService.listAllForAdmin`), and the terminal's own search is
  hard-capped at 200 results (`CatalogService.TERMINAL_SEARCH_CAP`) with no UI to raise it —
  intentional (a terminal cashier scrolling through hundreds of tiles isn't the point; searching
  is), but worth knowing if a future store genuinely needs to browse more than 200 unfiltered
  matches at once. Neither cap was tested against a live 100,000-row database in this
  environment — there's no Postgres instance available here to run
  `backend/scripts/seed_100k_products.sql` against — so this is a design response to the
  performance problem an unbounded list would clearly have at that scale, not a benchmarked one;
  run the seed script locally and try both screens to confirm.
- **Return all items** (this pass) has no idea which lines already have a prior partial return —
  it fills every row with what was originally sold, not what's still outstanding, since the form
  never fetches an "already returned" figure per line (only the backend, at submit time, actually
  knows that — see `ReturnsService.processReturn`). On a fully-fresh order (no earlier return),
  this is correct on the first click; on an order that already had one, clicking it and submitting
  as-is gets rejected with the existing precise error naming exactly which line and how much is
  left, and the cashier adjusts that one row down and resubmits. A future pass could show a live
  "already returned"/"remaining" figure per line so this — and manual entry — can't produce an
  invalid amount in the first place.
- **Return receipt / "marked returned" on the sale receipt** (this pass) deliberately don't share
  code with each other despite looking related: the sale receipt's "already returned" lookup
  (`GET /api/returns/order/{orderId}`) lives in the `returns` package and is called *by* the sale
  receipt component, rather than `OrderDto`/`OrderLine` growing a `returnedQuantity` field, because
  `returns` already depends on `order` (`ReturnsService` calls `OrderService`) — the reverse
  dependency would be circular, the same constraint already documented for
  `CashierSession.activeOrderId` (register vs. order packages). One consequence: a return
  processed before this pass has `SalesReturnLine.refundAmount == null` (the receipt prints "—"
  for it), since that snapshot field didn't exist yet when it was written.
- **Offline mode's scope boundaries** (this pass) are deliberate, not oversights:
  - A **fresh PIN-gated terminal open still requires connectivity** — PIN validation is a server
    call this never tries to replicate offline (caching PIN hashes client-side would be a real
    security regression, not a convenience worth adding). Offline mode covers a terminal that's
    already open and mid-shift when the connection drops, not opening one from scratch with no
    network at all.
  - **A server-backed tab that was already open when connectivity dropped is frozen, not
    migrated** — there's no attempt to transplant an in-progress `currentOrder`'s lines into the
    local offline cart. It simply waits, untouched, until the terminal is back online; a cashier
    starts a *new* sale in the offline cart in the meantime. This was a deliberate scope line, not
    a technical wall — merging the two would mean duplicating a lot more of `OrderService`'s logic
    client-side for one edge case (mid-cart, not "already offline when the sale started").
  - **The offline product/category/payment-method/customer cache is a fixed ceiling, not the
    whole catalog for any store size** — `CatalogService.listForOfflineCache` caps at 5,000 active
    products, the same "fixed ceiling with no UI to raise it" shape as the terminal's own live
    search cap and the admin product list's page-size cap (see above). A store at the
    twenty-sixth pass's 100,000-product stress-test scale would need a smarter (partial/LRU) cache
    strategy to go fully offline — out of scope here, and flagged the same way that pass's own
    caps were.
  - **No live credit-limit check while offline** — Checkout's `maxAmountFor` caps a credit row at
    the customer's `CreditSummary.availableCredit`, fetched live from the server; offline mode has
    no such fetch, so a credit sale can be queued for more than the customer can actually still
    borrow. It's caught for real at sync time (the exact same `completeCheckout` code path
    `customerCreditService.assertWithinLimit` runs for online sales), and lands in the queue
    marked "failed" for a cashier/admin to review rather than being silently accepted.
  - **The IndexedDB queue lives on one device, not shared across terminals** — two terminals that
    both go offline queue their own sales independently and each syncs on its own; there's no
    cross-device view of "everything currently queued anywhere" short of opening the queue panel
    on each device once it's back online.
  - **A discarded 'failed' queued sale is genuinely gone** — `discardQueuedSale` removes it from
    IndexedDB outright with no undo and no server-side record of it ever having existed (it was
    never created there in the first place, precisely because it failed to sync). This is the
    intended escape hatch for a queued sale nobody can fix (e.g. a customer who's no longer
    enabled for credit at all), not something to click without being sure.
- **EFRIS now actually submits at checkout, but the integration is still narrower than a real
  deployment needs.** `OrderService.submitToEfrisIfApplicable` calls a real HTTP endpoint (see the
  "Real EFRIS submission at checkout" changelog entry above) once `EFRIS_UG` is enabled and a
  terminal has connection settings saved, but: there's no retry queue for a failed submission (the
  `efrisError` badge is the only signal — a cashier/admin has to notice it and handle the sale
  manually, e.g. by resubmitting through some other means); the `sdc_data`/`cis_data` response
  parsing is inferred from one worked example, not documented EFRIS behavior, so a real device's
  response could come back split differently than expected (see `EfrisSubmissionResult`'s javadoc
  for the one place to fix that); nothing warns an admin at Tax Rate/Product edit time that a
  `taxLabel`/`unspCode` is missing before it's needed at checkout; and the submission happens
  synchronously inline with the checkout HTTP response (a slow or hanging EFRIS endpoint delays
  that response, bounded only by `EfrisSubmissionService`'s own 20-second request timeout) rather
  than being queued for a background worker, since this codebase has no async/post-commit-hook
  infrastructure yet. Until those are addressed, `InstalledAppSetting`'s free-form key/value rows
  (the generic settings table, still present alongside the per-terminal connection table) aren't
  validated or read by anything.
- **[Superseded by the 2026-10-09 inventory entry: multi-warehouse, transfers and per-location stock now exist.]** **Inventory depth (this pass) covers a single store; multi-location transfers are deliberately
  out of scope.** Fourier's own choice when asked to scope this: the data model has no
  location/warehouse concept at all — one shared `Product.stockQuantity` pool, same as before —
  so "move stock between locations" isn't representable yet. Adding it later means giving
  `Register` (or a new `Location` entity) its own stock pool per product and reworking every place
  that reads/writes `stockQuantity` today (`CatalogService.adjustStock`, the new
  `PurchaseOrderService.receiveLines`/`StockAdjustmentService.create`, `OrderService`/
  `ReturnsService`'s own stock adjustments) to take a location — a real rework, not an additive
  change, so it's better done as its own pass once there's an actual second location to design
  against. Relatedly: a purchase order belongs to exactly one supplier, so the dashboard's bulk
  "Create purchase order for all" low-stock action always drafts a single PO even when the
  low-stock list spans products usually bought from different suppliers — the admin picks one
  supplier for the whole draft (or removes/relocates lines to a second manually-created PO) rather
  than this auto-splitting by preferred supplier, since `Product` has no supplier-preference field
  (see the "Inventory depth" changelog entry above for why one wasn't added: it would have created
  a circular dependency between the `catalog` and `inventory` packages).
- **Manufacturing is deliberately single-level and doesn't close the loop with Purchasing.** A Bom
  component is always a plain `Product`, never another product's own Bom (see `Bom`'s javadoc) —
  no multi-level sub-assemblies/explosion yet, so a "finished good" that's itself built from other
  manufactured sub-assemblies has to be modeled as one flat parts list today. Production Plans
  surface component shortfalls but never auto-generate a Purchase Order for a short raw material —
  an admin still has to notice a shortfall and place that PO by hand (`ProductionPlan`'s javadoc
  documents both as scope boundaries, not oversights). Production Plan demand is also
  single-warehouse, same ceiling as the rest of this app's stock model (see the multi-location
  note just above). And Workstation/Routing time estimates (`estimatedMinutesPerUnit` ×
  `Workstation.hourlyRate`) are informational only — nothing currently rolls them into a
  Manufacturing Order's own cost breakdown, which today is material + indirect costs alone; adding
  labor cost from routing time is the natural next increment if Fourier wants it.
- **The Accounting module models this app's economics as they exist today, not a full general
  ledger for every scenario a real bookkeeper might want.** Every non-store-credit tender (cash,
  card, mobile money, whatever an admin has set up as a `PaymentMethodEntity`) posts to the one
  Cash account — there are no per-method bank/clearing sub-accounts, so a card payment is treated
  identically to cash for GL purposes rather than modeling settlement delay or processor fees.
  Inventory is likewise one pooled asset account, matching `Product`'s own single
  `stockQuantity`/`costPrice` pair — there's no separate raw-material/WIP/finished-goods split (see
  the "Manufacturing's own posting is deliberately overhead-only" changelog entry above). There's
  no period-close/retained-earnings-rollover step — the Balance Sheet's `currentEarnings` is
  always computed live, all-time, rather than ever being closed into Owner's Equity by a year-end
  entry, so it keeps growing rather than resetting each period the way a real close would. There's
  no accrual-basis expense recognition beyond what's described above (e.g. no accounts-payable
  aging or 1099-style vendor reporting), and no multi-currency support — everything posts in the
  store's single configured currency. And turning Accounting on only affects transactions from
  that point forward — nothing retroactively back-posts journal entries for sales, purchases, or
  payments that happened before it was enabled.

- **The Accounting Reports expansion's own scope boundaries (this pass) are deliberate, per
  Fourier's own `AskUserQuestion` choices, not oversights:** the Assets report is a simple
  as-of-date listing of Asset-type accounts, not a fixed-asset register — there's no per-asset
  acquisition date/cost/depreciation schedule anywhere in this app. The Tax Report/Declaration is
  a general summary (tax collected by rate, from POS sale lines' own snapshotted tax rate; tax
  payable's own net ledger movement) rather than any specific tax authority's official filing
  form, and there's no "tax paid" figure at all — this app has never modeled input/purchase tax
  (no `PurchaseOrderLine` field carries a tax amount). Expense/Income's `paid`/`received` flag is
  a simple boolean, not a real accounts-payable/receivable aging workflow — there's no "mark this
  unpaid expense as paid later" screen; recording that today means a manual journal entry against
  `ACCOUNTS_PAYABLE`/`ACCOUNTS_RECEIVABLE` once it's actually settled, same as any other accrual
  in this app. Cost Centers are a flat, unlimited-depth-free list (no parent/child rollups), and
  tagging one is entirely optional everywhere it's offered — nothing requires it. And the sixteen
  Expenses/Incomes segmented+periodic reports only ever read `Expense`/`Income` rows created
  through the new dedicated entry screens — a POS sale's revenue, a Purchase Order's cost, or any
  other module's own automatic posting doesn't count toward them (that's what Profit & Loss/
  Financial Transactions are for).
