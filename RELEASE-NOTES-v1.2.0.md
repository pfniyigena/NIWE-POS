# NIWE ERP, version 1.2.0

Property Management improvements. No new business module; versions 1.0.0 and 1.1.0 stay the base (see `RELEASE-NOTES-v1.0.0.md` and `RELEASE-NOTES-v1.1.0.md`). Every change is inside the Property Management module, so an installation that does not have it licensed sees no difference.

## What changed (Property Management, nine phases)

- **Phone and tablet layout, and paging.** Tables become cards on a phone. The owner, property, unit, tenant and lease lists page on the server (25 per page); rent, maintenance and statements page on the screen. The silent 1000/2000 row caps on the rent list are gone.
- **Tenant account statement and rent receipt.** A running account per tenant (PDF, e-mail) and a receipt for every payment received (PDF, e-mail).
- **Rent reminders and late fees.** A morning job (06:50) e-mails the tenant before the due date and when rent is late; an optional late fee (a percentage with a cap, or a fixed amount) is invoiced once per rent. Settings page, preview of what would go out today, Run it now.
- **More reports and list exports.** Deposits held, rent increases, 12-month trend, contractors, documents, fee invoices and charges reports; Excel, PDF and CSV exports of the owner, tenant and lease lists with the same filters as the screen.
- **Documents.** Attach leases, IDs, title deeds, photos and papers to a property, unit, owner, tenant, lease or repair request (PDF, images, Word, Excel, text; up to 20 MB), with optional expiry dates and an overview card for lapsing documents.
- **Security deposits and inspections.** The deposit of a lease as a ledger of movements (received, kept for rent, damage or other reasons, refunded, moved to a renewal, voids); two new accounts in the books; keeping for unpaid rent settles the rent invoice for real. Move-in and move-out inspections with a room list, conditions, repair costs, sign-off and a printable report.
- **Tax invoice to the owner for the management fee.** Made when a statement is agreed; a document only (the statement already books the fee and the VAT), numbered, voided when the statement is reopened, attached to the e-mailed statement.
- **Utilities and service charges on the rent invoice.** Charge types (fixed monthly or metered), charges on a lease, meter readings and one-off charges become lines on the tenant's rent invoice. The money is the agency's: the owner statement counts only the rent part of what the tenant pays, and late fees are worked out on the rent only.
- **Co-owners.** A property can have several owners with a percentage each, from a date. Each owner gets their own statement for their share of the rent, the management fee and repairs, and their own fee invoice. A split that an agreed statement covers is locked. See "Co-owners" below.

Details of each phase, with the API and the limits, are in the README (entry "Property Management improvements, version 1.2.0").

## Co-owners, in short

- On a property page, the **Owners** panel shows who owns it. **Change owners** sets the owners and their percentages (they must add up to 100%) from a date; leave the date empty to set them from the beginning, which is only possible while no agreed statement covers the property. One owner is the main owner (the one you deal with; leftover cents of a shared payment go to them).
- Rent is shared by the split in force on the day it was paid. The management fee is the property's percentage (the property's own, else the main owner's, else the module's) on each owner's part. Repairs that the owner pays for are shared by the same percentages.
- Every property already in the database starts with its owner at 100%, so statements of single-owner properties come out exactly as before.

## Database

Seven migrations were added after `V2__module_licensing.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Phase |
|---|---|
| `V3__pm_reminders_late_fees.sql` | reminders and late fees |
| `V4__pm_documents.sql` | documents |
| `V5__pm_deposits_inspections.sql` | deposits and inspections |
| `V6__pm_fee_invoices.sql` | fee invoices |
| `V7__pm_charges.sql` | utilities and service charges |
| `V8__pm_co_owners.sql` | co-owners (every property gets its owner at 100%) |

## Upgrading an installation that ran 1.1.0

Back up the database, install the new build and start it. Flyway applies V3 to V8. Nothing else is needed: the license is unchanged, and existing owners, properties, leases, invoices and statements keep working. Two new product records ("Late rent fee", and "Other charges" or one per charge type) and two accounts (2890 Tenant Security Deposits, 4310 Tenant Charges Recovered) are created the first time those features are used.

## Before the first customer build (NIWE), unchanged from 1.1.0

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties`.

## Tested, and not tested

Every phase was tested against PostgreSQL 16 over the HTTP API and driven in a browser, and the whole set was re-run after the last phase. Backend suites (checks passed, none failed): 177, 97, 104, 91, 64, 56, 52, 38, 43 (the earlier Property Management phases), then 26, 64, 105, 69, 91, 171, 83, 128, 99 (phases 10 to 18). Browser walks: 19, 20, 47, 54, 39, 23, 33, 26. The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V8 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), the morning jobs firing by themselves (06:40 statements, 06:50 reminders; they were run through their endpoints), a license signed with your real key, real data, and the business details on PDF headers from real store settings.

## Known limits of the new features

- Charges are not taxed. Readings and one-off charges can only be added while a lease is in force. No meter replacement or reset (start a new charge).
- A statement edited after it was agreed must be reopened, which voids and renumbers its fee invoice. No credit notes other than void and reissue.
- Ownership is per property, not per unit. Everyone on a property pays the same fee percentage. Maintenance and tenant mail go to the main owner only. A split cannot start in the future.
- Damage kept from a deposit is agency income, not part of the owner statement. No deposit interest. Escalation applies only at renewal.

## Commit and tag (run these yourself)

Versions 1.0.0 and 1.1.0 were never committed or tagged, and 1.2.0 builds on the same uncommitted work, so tag one version: **v1.2.0** (the older notes stay as history). First remove the delivery leftovers from the repository folder: they are not part of the code.

```bash
cd "D:/2026/NIWE-GROUP/TECH LTD/ERP/codes/NIWE-POS"
rm -f niwe-pm*.tgz apply-pm*.py
git rm -f backend/_verify-src.tgz
git add -A
git status                      # check the list first: no keys, no licenses, no .tgz or apply-pm files
git commit -m "NIWE ERP 1.2.0: Property Management improvements (reminders, documents, deposits, fee invoices, charges, co-owners), module licensing, Law Firm, Real Estate"
git tag -a v1.2.0 -m "NIWE ERP version 1.2.0"
git push origin main --follow-tags
```

If `git rm` says the file is not in the index, skip that line. The private license key and any `.niwelic` file or `licenses.csv` are ignored by git, but look for them in `git status` anyway.
