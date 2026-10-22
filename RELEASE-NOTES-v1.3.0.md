# NIWE ERP, version 1.3.0

Real Estate improvements. No new business module; versions 1.0.0, 1.1.0 and 1.2.0 stay the base (see their release notes). Every change is inside the Real Estate module, so an installation that does not have it licensed sees no difference.

## What changed (Real Estate, eight phases)

- **Phone and tablet layout, and paging.** Tables become cards on a phone. The property, lead, deal, project and unit-sale lists page on the server (25 per page); the viewings diary and the wide tables on the deal, project, unit and unit-sale pages also stack on a phone.
- **List exports.** Excel, PDF and CSV of properties, leads, deals, projects, units, unit sales and viewings, with the same filters as the screen and always the whole list.
- **Lead follow-ups.** A "My follow-ups" screen (late, due today, coming up, gone quiet), log a call or move a lead on, and an optional 06:30 morning e-mail to each agent with preview and "Send now".
- **Viewing reminders and a week calendar.** A reminder e-mail to the client and one list per agent before a viewing (15-minute job, hours ahead set in the settings), a Remind button per viewing, and a Monday-to-Sunday week view of the diary.
- **Printable documents.** Offer, reservation, sale agreement (with the payment schedule), payment list and payment receipt for deals and unit sales; a price list for a project; a flyer with photos for a property. Standard wording can be replaced in the settings.
- **Unit-sale collections.** A Collections screen of buyers who still owe (late, due soon, no schedule), calls, promises to pay and notes on each sold unit, a reminder e-mail to the buyer, a printable and e-mailable buyer statement, and an export.
- **Agent commission payouts and statements.** A Commissions screen grouping every commission share by person (staff or outsider) with its stage (not earned yet, earned, awaiting approval, approved to pay, paid, rejected), "Post all earned" to Expenses, a PDF statement per person, e-mail of the statement to staff, and an export. Payment itself stays in Expenses.
- **Imports from Excel or CSV.** An Import button on the Leads and Properties screens. Download a template, choose a file, read the check (nothing is saved), then import. Each skipped row is listed with its spreadsheet row number and the reason; duplicates are caught; properties can be listed as available on import if they have the price they need.

Details of each phase, with the API and the limits, are in the README (entry "Real Estate improvements, version 1.3.0").

## Database

Four migrations were added after `V8__pm_co_owners.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited. The two phases that follow V12 (commissions, imports) change no table.

| Migration | Phase |
|---|---|
| `V9__re_follow_ups.sql` | lead follow-ups |
| `V10__re_viewing_reminders.sql` | viewing reminders |
| `V11__re_print_terms.sql` | printable documents |
| `V12__re_sale_collection.sql` | unit-sale collections |

## Upgrading an installation that ran 1.2.0

Back up the database, install the new build and start it. Flyway applies V9 to V12. Nothing else is needed: the license is unchanged and existing properties, leads, deals, projects and unit sales keep working. The three e-mail features (follow-ups, viewing reminders, buyer reminders and statements) do nothing until e-mail is set up and, for the two morning jobs, switched on in the Real Estate settings.

## Before the first customer build (NIWE), unchanged from 1.1.0

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties`.

## Tested, and not tested

Every phase was tested against PostgreSQL 16 over the HTTP API and driven in a browser, and the whole set (all earlier modules included) was re-run after the last phase with no failure. Real Estate backend suites (checks passed, none failed): 27, 46, 58, 39, 75, 85, 81, 87. Real Estate browser walks: 80, 20, 37, 36, 37, 29, 26, 27. The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V12 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), the morning and 15-minute jobs firing by themselves (06:30 follow-ups, viewing reminders; they were run through their endpoints), a license signed with your real key, real data, and the business details on PDF headers from real store settings.

## Known limits of the new features

- The server builds a full list before paging it; fine for thousands of rows, not for hundreds of thousands.
- Unit sales have no commission shares, so the Commissions screen covers deals only.
- The commission screen follows Expenses; it does not approve or pay. Statements can be e-mailed to staff only (outsiders have no e-mail address on record).
- Imports read the first sheet only, up to 2000 rows, and accept `.xlsx` or CSV (not old `.xls`). The check does not simulate someone else saving the same lead or property at the same time.
- Collections does not duplicate the automatic buyer reminders and late fees of the Instalments module; those stay there and run only when that module is on.

## Commit and tag (run these yourself)

Nothing from 1.0.0 onwards was committed or tagged, so tag one version: **v1.3.0** (the older notes stay as history). First remove the delivery leftovers from the repository folder: they are not part of the code.

```bash
cd "D:/2026/NIWE-GROUP/TECH LTD/ERP/codes/NIWE-POS"
rm -f niwe-pm*.tgz niwe-re*.tgz apply-pm*.py apply-re*.py
git rm -f backend/_verify-src.tgz
git add -A
git status                      # check the list first: no keys, no licenses, no .tgz or apply-* files
git commit -m "NIWE ERP 1.3.0: Real Estate improvements (follow-ups, viewing reminders, print documents, collections, commissions, imports), Property Management improvements, module licensing, Law Firm"
git tag -a v1.3.0 -m "NIWE ERP version 1.3.0"
git push origin main --follow-tags
```

If `git rm` says the file is not in the index, skip that line. The private license key and any `.niwelic` file or `licenses.csv` are ignored by git, but look for them in `git status` anyway.
