# NIWE ERP, version 1.9.0

POS improvements, in eight steps. No new business module; versions 1.0.0 to 1.8.0 stay the base (see their release notes). Everything below works with the POS module an installation already has licensed.

## What changed (eight steps)

- **Lists and exports (PO-1).** Receipts, returns, shifts and cash movements get a search bar, filters and pages done on the server, with Excel, PDF and CSV of the same filtered list.
- **Cash control (PO-2).** A cash drawer page for every shift: paid in, paid out, drops to the safe and mid-shift counts, each with a reason, a manager's PIN for big paid-outs, and a void for mistakes. Closing a shift counts notes and coins, shows over / short, asks for a note when the difference is too large, and posts the difference to Accounting. A POS settings screen holds the limits.
- **X and Z reports (PO-3).** A printable X report (the shift so far, any time) and a numbered Z report (frozen when the shift closes) with sales, payments, tax, refunds, the cash drawer and watch points; later printings are marked COPY.
- **Void and return controls (PO-4).** A Void button on receipts (full refund, kept as VOIDED, with who and why), return window, refund limit, required reasons and manager approval by PIN; cash refunds are now charged to the drawer that paid them; a Voids & returns log.
- **Discounts and price changes at the till (PO-5).** A cashier allowance for discounts with manager approval above it, discount reasons, a discount on the whole sale, and a manager-approved price change on a line (with reason and a floor at cost).
- **Receipts (PO-6).** 80 mm or 58 mm paper, header and footer text, logo, a QR code of the invoice number, and original / COPY marking with a count of every printing.
- **POS reports (PO-7).** Five reports: sales by cashier, by hour, by payment method, exceptions (voids, returns, discounts, price changes, reprints, dropped carts) and a shift comparison; Excel / PDF / CSV.
- **Register setup and permissions (PO-8).** Each register can be switched off, given a usual and a highest opening float and a list of cashiers allowed to work it; three new approval permissions let a supervisor approve only voids and returns, only price changes, or only big paid-outs, without any other manager right.

Details of each step, with the API and the limits, are in the README (entry "POS improvements, version 1.9.0") and in `POS-CHANGES.md`.

## Database

Six migrations were added after `V38__restaurant_online.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Step |
|---|---|
| `V39__pos_cash_control.sql` | POS settings, cash movements, shift counts |
| `V40__pos_shift_reports.sql` | Z numbers and frozen Z reports |
| `V41__pos_void_return_controls.sql` | voids, return rules, refund drawer |
| `V42__pos_discount_controls.sql` | discount and price-change controls |
| `V43__pos_receipts.sql` | receipt options and print counts |
| `V44__pos_register_setup.sql` | register rules, allowed cashiers, new permissions |

Lists, exports and the reports change no table.

## Upgrading an installation that ran 1.8.0

Back up the database, install the new build and start it. Flyway applies V39 to V44. Nothing else is needed: the license is unchanged and existing registers, shifts, sales and returns keep working. Everything new is off or neutral until set: no discount allowance for cashiers (a cashier still cannot discount alone), no paid-out limit, no return window, receipts print as before, every register is in use with no float limit and open to every cashier. Z numbers are given to shifts as they close; shifts closed earlier show their figures without a number. New accounts (Cash Over / Short, Till Paid-out Expenses, Staff Advances) are added to existing charts of accounts the first time they are needed. Where to look: POS settings (`/admin/pos-settings`), Cash movements, Voids & returns (`/admin/pos-log`), POS Reports, and Registers > Edit > Till rules.

One behaviour changes on purpose: a cash refund now reduces the drawer of the shift where the money went out (it used to be the shift of the original sale), so the Z report and the expected cash match the cash in the drawer. Refunds made before the upgrade keep their old attribution.

## Wiring the screens

New and changed frontend files are in the package. Then run, from the repository folder: `python3 tools/wire-rs.py frontend/src/app` and `python3 tools/wire-po.py frontend/src/app` (both safe to run twice). They add the new routes, the module gating and the side-menu entries. Then `python3 tools/apply-v190.py` bumps the version to 1.9.0 and adds the README entry.

## Tested, and not tested

Every step was tested against PostgreSQL 16 over the HTTP API and driven in a browser, and the whole set (all earlier modules included) was re-run after the last step on a frozen copy of the code: 140 result files, 0 failures. POS suites (checks passed, none failed): backend t85 to t92 (76, 83, 48, 57, 58, 25, 47 and 56 checks) and browser walks walk68 to walk75 (19, 25, 23, 25, 17, 16, 12 and 18 checks). The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V44 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), real receipt printers or paper widths (the layout was checked in the browser; a thermal printer may need its own margin setting), scanning the receipt QR code with a camera or a barcode scanner, a real cash drawer, real data in volume, a license signed with your real key, and branch-limited users on the new POS screens.

## Known limits of the new features

- Offline sales keep the old rule: only an admin can give a discount, and approvals by PIN are not possible without the server.
- A void is allowed only while the sale's shift is open; afterwards it is a return. A sale partly returned, or partly sold on credit or charged to a room, cannot be voided.
- One drawer per shift: if several cashiers share a shift, the cash is counted for the shift, not for each cashier. The sales-by-cashier report shows each cashier's sales, not their cash.
- The "allowed cashiers" list of a register is checked when a cashier opens, joins or is added to a shift. A cashier already working an open shift is not removed when the list changes; close the shift or their session.
- A register with an open shift cannot be switched off; close the shift first.
- The receipt QR code holds the invoice number only; sales sent to EFRIS keep their own fiscal QR code.
- Hourly sales use the time the sale was completed (store time zone).
