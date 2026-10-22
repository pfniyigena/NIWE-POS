# NIWE ERP, version 1.7.0

Hotel improvements, in eight steps. No new business module; versions 1.0.0 to 1.6.0 stay the base (see their release notes). Everything below works with the Hotel module an installation already has licensed.

## What changed (eight steps)

- **Lists and exports (HO-1).** Bookings, guests, rooms and maintenance get a search bar, filters and pages of 25 done on the server, with Excel, PDF and CSV of the same filtered list; the front-desk lists, room types and the occupancy report export too. Tables turn into cards on a phone.
- **Seasons and rate plans (HO-2).** Seasonal price rules (fixed price, percent or amount, by room type, dates, weekdays, minimum nights, priority), rate plans such as Corporate or Weekly, per-night pricing stored on each booking, a live quote on the booking form and a price calendar. Maintenance "out of service" can have from and until dates so only those nights are blocked.
- **Folio and policies (HO-3).** Up to six numbered bills per stay, bill to a company (city ledger) with credit-limit check, company accounts with receipts, moving a charge to another in-house guest, and a fee policy (late cancellation, no-show, early departure, late check-out; all off until set). PDFs for bills, receipts and company invoices.
- **Group bookings (HO-4).** Groups with room blocks and release dates, a rooming list, who-pays options, a master account with deposit, payments and a statement, and cancelling a whole group.
- **Housekeeping (HO-5).** A daily board with departure and stay-over cleans, sharing the work between housekeepers, an inspection checklist, do-not-disturb, lost and found, minibar charged to the room, linen log and productivity.
- **Guest register (HO-6).** Guest profile with history and lifetime spend, VIP and blacklist (a blacklisted guest cannot book or check in), preferences, duplicate finder and merge, ID document uploads, registration card and confirmation letter PDFs, and the police / immigration guest register.
- **Reports and night audit (HO-7).** Day report, revenue by booking source, forecast with pick-up, and a night-audit report with six checks of the hotel's records against Accounting (cash, revenue, tax, deposits, city ledger, group ledger); all export.
- **Online booking, e-mails and calendars (HO-8).** A public page `/stay` where guests see free rooms with the full price and book, with an optional deposit and hold that releases unpaid rooms; look up and cancel with code and phone; confirmation (with the letter attached), pre-arrival and thank-you e-mails; and a private iCal feed per room type.

Details of each step, with the API and the limits, are in the README (entry "Hotel improvements, version 1.7.0") and in `tools/hotel-changes.md`.

## Database

Six migrations were added after `V25__hr_payroll_corrections_final_pay.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Step |
|---|---|
| `V26__hotel_rates_maintenance_windows.sql` | seasons, rate plans, maintenance windows |
| `V27__hotel_folio_policies_company_ledger.sql` | bills, company ledger, fee policy |
| `V28__hotel_groups.sql` | groups |
| `V29__hotel_housekeeping_v2.sql` | housekeeping |
| `V30__hotel_guest_register.sql` | guest register |
| `V31__hotel_online_booking.sql` | online booking |

Lists, exports and the reports change no table.

## Upgrading an installation that ran 1.6.0

Back up the database, install the new build and start it. Flyway applies V26 to V31. Nothing else is needed: the license is unchanged and existing rooms, bookings and guests keep working. Everything new is off or neutral until set: no fee policy, no price rules, online booking closed, every guest e-mail switched off. Existing bookings keep their agreed nightly rate. To open the booking page: Hotel > Online booking, tick "Guests can book online" (and, if you want a deposit, say how guests pay it). E-mails also need the mail server set up under Settings.

## Wiring the screens

New and changed frontend files are in the package. Then run, from the repository folder: `python3 tools/wire-ho2.py frontend/src/app`, then `wire-ho3.py`, `wire-ho4.py`, `wire-ho6.py` and `wire-ho8.py` the same way (each is safe to run twice). They add the routes, menu entries, the public `/stay` route and the home-page tile. Then `python3 tools/apply-v170.py` bumps the version to 1.7.0 and adds the README entry.

## Before the first customer build (NIWE), unchanged from 1.1.0

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties`.

## Tested, and not tested

Every step was tested against PostgreSQL 16 over the HTTP API and driven in a browser (desktop and phone width), and the whole set (all earlier modules included) was re-run after the last step: 108 result files, 0 failures. Hotel suites (checks passed, none failed): backend t69 to t76 (128, 133, 152, 167, 155, 107, 132 and 139 checks) and browser walks walk52 to walk59 (46, 27, 53, 52, 44, 43, 46 and 57 checks). The frontend type-checks (`ngc`) and builds. Two older test scripts were adjusted for reasons outside the product: the HR leave test t61 used a sample date that now crosses a year end, and the Hotel report walk now opens the Occupancy tab first.

Not tested: Flyway itself applying V1 to V31 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), a real channel manager or booking site reading the iCal feed, real mobile-money or bank payment of deposits (staff record them by hand), real data in volume, a license signed with your real key, and branch-limited users on the Hotel screens.

## Known limits of the new features

- Online deposits are paid outside the page and recorded by staff; there is no card or payment gateway. Any recorded deposit ends the hold, even a partial one.
- Online bookings choose a room type, not a room; rate plans are not offered online. A guest can cancel online only while nothing is paid and no cancellation fee applies.
- The iCal feed is outbound only: it shows booked nights ("Reserved", no guest details), not a count of free rooms for a type with several rooms, and nothing is imported from other sites.
- The company and group ledgers are the hotel's own and do not appear in the shop's customer statements; a company bill cannot be voided once issued.
- Night-audit checks for revenue, tax, deposits and ledgers compare all-time balances, so an old difference keeps showing until it is corrected in Accounting.
- The guest register lists one line per booking (the lead guest); companions are not recorded individually. ID documents are not virus-scanned.
- No automatic e-mail goes to a group organiser; guest e-mails need a guest e-mail address and the mail server set up.
