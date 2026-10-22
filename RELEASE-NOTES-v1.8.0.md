# NIWE ERP, version 1.8.0

Restaurant improvements, in eight steps. No new business module; versions 1.0.0 to 1.7.0 stay the base (see their release notes). Everything below works with the Restaurant module an installation already has licensed.

## What changed (eight steps)

- **Lists and exports (RS-1).** Closed receipts, wastage and reservations get a search bar, filters and pages done on the server, with Excel, PDF and CSV of the same filtered list; menu items and tables export too.
- **Discounts, tips and service charge (RS-2).** A Restaurant settings screen (service charge per order type, manager discount limit, tips and presets, cash rounding). Line and whole-bill discounts with a reason, a manager for large ones, tips at checkout, change out of cash, and the receipt and Accounting follow (service charge revenue, tips payable, rounding).
- **Choices, kitchen notes, courses and allergens (RS-3).** Size / cooking / extras choices with prices, quick notes, courses 1-5 that wait until fired, allergen tags and guest allergies with a warning on the order and the kitchen ticket.
- **Split bills, voids and comps (RS-4).** Split a table into separate checks (by item, part of a quantity or seat), move items or a whole bill, void rules with manager approval, comps with a reason, a voids and comps log, receipt copies marked COPY.
- **Reservations v2 (RS-5).** Bookings hold a table for a time window, not a whole day; opening hours and slots; deposits taken, applied to the bill, refunded or kept; no-show handling and a block after repeat no-shows; reminders by e-mail; a waiting list for walk-ins.
- **Kitchen display v2 (RS-6).** A clock on every dish, amber and red against a prep target, a summary strip and station counts, "Bump" and "Remake", an 86 board for sold-out dishes that the order screen respects, and an expo screen for the pass.
- **Reports and end of day (RS-7).** Sales analysis (hours, weekdays, days, order types, payments, menu engineering, categories, servers, tables, controls), an end-of-day cash-up with paid in / paid out, counted cash and variance, a numbered Z-report that freezes the day, reopen with a reason, and Excel / PDF / CSV.
- **QR menu and online ordering (RS-8).** A QR code per table opens the menu on the guest's phone; a public page `/order` takes takeaway and delivery orders with delivery areas and fees. Orders wait for staff to accept (or are accepted automatically for tables), then go to the kitchen on a real bill; the guest follows the order by a code. No online payment: guests pay at the table, at pick-up or on delivery.

Details of each step, with the API and the limits, are in the README (entry "Restaurant improvements, version 1.8.0") and in `RESTAURANT-CHANGES.md`.

## Database

Seven migrations were added after `V31__hotel_online_booking.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Step |
|---|---|
| `V32__restaurant_pricing.sql` | settings, discounts, service charge, tips |
| `V33__restaurant_options_courses.sql` | choices, courses, allergens |
| `V34__restaurant_checks_voids.sql` | split checks, voids, comps |
| `V35__restaurant_reservations_v2.sql` | reservations, deposits, waiting list |
| `V36__restaurant_kitchen_v2.sql` | kitchen clocks, 86 board |
| `V37__restaurant_reports.sql` | cash events, day closes |
| `V38__restaurant_online.sql` | table QR codes, delivery areas, online orders |

Lists and exports change no table.

## Upgrading an installation that ran 1.7.0

Back up the database, install the new build and start it. Flyway applies V32 to V38. Nothing else is needed: the license is unchanged and existing tables, menu items, orders and reservations keep working. Everything new is off or neutral until set: no service charge, no tips, no discount limit changes, no options, deposits 0, online ordering and table QR switched off. Every existing table gets a QR code automatically (it does nothing until "Guests can order from the QR code" is ticked under Restaurant > Settings). To open online ordering: Restaurant > Settings > QR menu and online ordering; delivery areas and the printable QR codes are on Restaurant > Online orders.

## Wiring the screens

New and changed frontend files are in the package. Then run, from the repository folder: `python3 tools/wire-rs.py frontend/src/app` (safe to run twice). It adds the routes (including the public `/menu/:token`, `/order` and `/order/track/:code`) and the menu entries. Then `python3 tools/apply-v180.py` bumps the version to 1.8.0 and adds the README entry.

## Tested, and not tested

Every step was tested against PostgreSQL 16 over the HTTP API and driven in a browser, and the whole set (all earlier modules included) was re-run after the last step: 124 result files, 0 failures (the run was interrupted once by a restart of the test machine and resumed from the Restaurant suites; the earlier files had already passed on the same frozen tree). Restaurant suites (checks passed, none failed): backend t77 to t84 (146, 62, 74, 106, 168, 70, 137 and 153 checks) and browser walks walk60 to walk67 (19, 26, 37, 34, 55, 40, 60 and 57 checks). The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V38 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), real printers or QR scanning with a phone camera (the QR sheet was checked to render images), real payment of deposits online, real data in volume, a license signed with your real key, and branch-limited users on the Restaurant screens.

## Known limits of the new features

- No online payment anywhere: deposits and online orders are paid at the restaurant and recorded by staff.
- A voided item that was already cooked does not become a wastage entry by itself; record wastage separately.
- The cash-up has one drawer for the whole restaurant; a sale belongs to the day it was paid (store time zone); hourly sales use the time paid.
- The table QR menu does not prove the guest is at the table; a code that leaks can be renewed (the old one stops at once).
- A delivery area that orders have used is switched off rather than deleted. Delivery has no map or distance check; the guest chooses an area and types the address.
- Tracker stages for a table order come from the kitchen screen; if nobody uses the kitchen display, the guest sees "Accepted" until the bill is paid.
