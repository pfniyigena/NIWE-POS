# General changes (v1.10.0)

## 1. Decimal quantities everywhere
- Every quantity (sale and invoice lines, sales orders, purchase orders and receipts, returns, stock on hand, transfers, counts, adjustments, reservations, warehouses, reorder, manufacturing, restaurant orders and wastage, work orders, imports, reports and exports) is a decimal number with up to four decimals, for example 2.5 kg or 0.75 m. Money is unchanged.
- Database: V50 changes the quantity columns to `numeric(19,4)`. Whole numbers already stored are kept as they are.
- Screens: quantity boxes accept decimals (`step="any"`), values are rounded to four decimals, and totals, messages and exports print without trailing zeros (`2.5`, not `2.5000`).
- Exceptions on purpose: goods tracked by serial number can only be sold, received, moved and counted in whole units; assets are registered in whole units.
- Request bodies reject zero or negative quantities where a positive one is needed.
- Fixed on the way: a repository method removed in the Manufacturing work and still used by a report was put back.

## 2-4. Allow public website: Help Desk, Reserve a Table, Book an Appointment
- Help Desk settings and Restaurant settings get the "Allow public website" box that Booking and Real Estate already had. Default: on (so nothing closes by itself after the upgrade).
- Off: the public page (`/helpdesk`, `/reserve`, `/book`) and its public API answer "not found", and the tile disappears from the home page. Staff keep using the module.
- `GET /api/public/modules` reports a module as public only when the module is on **and** its own switch is on.

## 5. Copyright
- "(c) 2026 NIWE GROUP" is shown at the bottom of every signed-in screen, the sign-in page and every public page (home, hotel, restaurant ordering, reservations, booking, help desk, listings). It is hidden when printing.

## 6. Appearance saved in the database
- A user's theme (including the custom colours) is stored per user, so it follows them to any browser or device.
- An administrator can tick "Also use my choice for the public website"; visitors who are not signed in then get that theme. Without the tick, only the administrator's own look changes. Unknown theme names and unsafe colour values are refused.

## 7. Print Service Card (employee record)
- A button on the employee page opens a staff ID-style A4 card (name, number, job title, department, hire date, contact). Not offered for terminated employees. No photo in this version.

## 8. Every employee can sign in and see his or her HR
- "Give login" on one employee, or "Give everyone a login" on the list, creates a user named after the employee number (for example `emp-0002`) with a first password shown once; "New password" resets it.
- The login has the CASHIER base role and the custom role "Employee". After signing in the employee opens **My HR** (own record, leave, attendance, payslips) and cannot open another person's record.
- Note: like any signed-in cashier-level user, an employee can still read the basic POS lists (products, customers, categories, registers). Tell us if you want these locked down for Employee logins.

## 9. Rwanda chart of accounts
- Setting up the Rwanda chart now creates tax code **A** (Exempted, 0%) and **B** (VAT 18%).
- Existing installations: run the Rwanda chart setup again (it adds only what is missing) or create the two tax rates by hand.

## Tests
- t99 (decimal quantities, 57 checks), t100 (switches, theme, service card, employee logins, tax codes, 53 checks), walk82 (browser, 30 checks).
