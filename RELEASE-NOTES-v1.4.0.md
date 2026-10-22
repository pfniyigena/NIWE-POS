# NIWE ERP, version 1.4.0

Law Firm improvements. No new business module; versions 1.0.0 to 1.3.0 stay the base (see their release notes). Every change is inside the Law Firm module (plus a small fix to the Real Estate import, below), so an installation that does not have Law Firm licensed sees no difference.

## What changed (Law Firm, seven phases)

- **Phone and tablet layout, and paging.** Law tables become cards on a phone. The matters, clients, contacts, deadlines and tasks lists page on the server (25 per page).
- **Client money (trust) papers.** Receipt for money received, payment voucher, fee transfer and deposit-taken-back vouchers, a client trust statement per matter for a chosen period (e-mailable to the client), and a printable sheet for any past reconciliation.
- **List exports.** Excel, PDF and CSV of matters, clients, contacts, deadlines, tasks, the billing overview, client money balances and the client money ledger, with the same filters as the screen. Restricted matters stay hidden from people not on the team.
- **Deadlines calendar and limitation warnings.** A Calendar screen (month and week, mine or everyone, tasks and finished items on request), a calendar file (.ics) for Outlook, Google Calendar or a phone, and optional e-mail warnings to the lead lawyer and the person assigned when a limitation date is close (first warning N days before, then 3 days before, then when overdue; daily run at about 06:25, with preview and "Send now").
- **Matter and client papers.** A Papers tab on each matter: file cover sheet, engagement and fee letter, and closing letter. Print, keep in the matter's documents, or e-mail the two letters to the client.
- **Client fee collections.** A Fee collections screen of unpaid issued invoices with days late and a "how late" table, reminder e-mails (one per client, with the fee statement attached), a PDF statement of fees per client that can be e-mailed, an export, and optional automatic reminders (daily run at about 06:35: first after N days late, repeated every N days, at most N times).
- **Imports from Excel or CSV.** "Import clients" and "Import matters" on the Matters screen and "Import" on the Clients screen. Same check-then-import panel as Real Estate: template, check (nothing is saved), import. Matters come in as open with their opened date and lead lawyer; clients that are missing can be added from the matters file; the conflict check can be marked done for matters the firm already handles.

Also: the Real Estate import now refuses an impossible date such as 31/02/2025 instead of moving it to the last day of the month.

Details of each phase, with the API and the limits, are in the README (entry "Law Firm improvements, version 1.4.0").

## Database

Two migrations were added after `V12__re_sale_collection.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited. Phases 1, 2, 3, 5 and 7 change no table.

| Migration | Phase |
|---|---|
| `V13__law_escalation.sql` | limitation warnings |
| `V14__law_fee_collections.sql` | fee collections |

## Upgrading an installation that ran 1.3.0

Back up the database, install the new build and start it. Flyway applies V13 and V14. Nothing else is needed: the license is unchanged and existing matters, invoices and client money keep working. The three e-mail features (limitation warnings, fee reminders, and the letters and statements sent from a screen) do nothing until e-mail is set up and, for the two daily jobs, switched on in the Law firm settings (both are off by default).

## Before the first customer build (NIWE), unchanged from 1.1.0

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties`.

## Tested, and not tested

Every phase was tested against PostgreSQL 16 over the HTTP API and driven in a browser, and the whole set (all earlier modules included) was re-run after each phase and after the last one with no failure. Law Firm backend suites (checks passed, none failed): 41, 49, 42, 55, 71, 74, 58. Law Firm browser walks: 23, 17, 21, 38, 21, 30, 27. The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V14 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), the daily jobs firing by themselves (06:25 limitation warnings, 06:35 fee reminders; they were run through their endpoints), a license signed with your real key, real data, and the business details on PDF headers from real store settings.

## Known limits of the new features

- The server builds a full list before paging it; fine for thousands of rows, not for hundreds of thousands.
- The calendar file is a download, not a live subscription.
- Only deadlines of the type LIMITATION are escalated by e-mail.
- A matter that still holds client money cannot be closed, so the closing letter never asks for payment instructions.
- Restricted matters are hidden from the fee collections list for people who are not on the team (admins see them).
- Fee reminders go to the client's e-mail on record; a client without one is skipped and shown in the result.
- Imports read the first sheet only, up to 2000 rows, and accept `.xlsx` or CSV (not old `.xls`). Time, deadlines, documents, invoices, fee amounts and stages are not imported; set the fees on the matter afterwards.

## Commit and tag (run these yourself)

Nothing from 1.0.0 onwards was committed or tagged by me. Tag the new version: **v1.4.0**. Remove the delivery leftovers from the repository folder first: they are not part of the code.

```bash
cd "D:/2026/NIWE-GROUP/TECH LTD/ERP/codes/NIWE-POS"
rm -f niwe-*.tgz apply-*.py _law-src.tgz
git add -A
git status                      # check the list first: no keys, no licenses, no .tgz or apply-* files
git commit -m "NIWE ERP 1.4.0: Law Firm improvements (paging, trust papers, exports, calendar and limitation warnings, matter papers, fee collections, imports)"
git tag -a v1.4.0 -m "NIWE ERP version 1.4.0"
git push origin main --follow-tags
```

If an earlier commit for 1.3.0 has not been made yet, the same commit simply carries both; tag v1.3.0 first on the earlier commit if you want both tags. The private license key and any `.niwelic` file or `licenses.csv` are ignored by git, but look for them in `git status` anyway.
