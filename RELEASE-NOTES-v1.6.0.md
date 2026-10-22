# NIWE ERP, version 1.6.0

HR and Payroll improvements, in eight steps. No new business module; versions 1.0.0 to 1.5.0 stay the base (see their release notes). Everything below works with the modules an installation already has licensed (Employees, Attendance & Leaves, Contracts and Payroll).

## What changed (eight steps)

- **Lists and exports (HR-1).** Employees, leave requests, attendance, contracts and payrolls get a search bar, filters (status, department, contract type, year, dates, branch) and pages of 25, done on the server, with Excel, PDF and CSV of the same filtered list. Tables turn into cards on a phone.
- **Payslip PDFs and certificates (HR-2).** A real A4 payslip (a draft payroll's payslips carry a DRAFT mark), all payslips of a month in one PDF, the payroll register (Excel, PDF, CSV), a yearly earnings and tax certificate per employee, and a yearly payroll summary. E-mailed payslips now carry the PDF.
- **Adjustments, advances and loans (HR-3).** One-off bonuses, arrears, allowances and deductions for one employee and one month; salary advances and staff loans with a number, a balance, monthly installments taken by payroll (never more than the pay allows), cash repayment, write-off and cancel. The cash given is posted to Accounting (new account "Staff Advances and Loans").
- **Income-tax and social-security payments (HR-4).** For every approved or paid month: what is owed, what was paid to the authority, what is still owed (due on the 15th of the next month); record and cancel payments (posted to Accounting); the monthly income-tax and social-security returns as Excel, PDF or CSV.
- **Leave (HR-5).** Half days, leave earned month by month, carry-over of unused days (with a limit per leave type), and a team leave calendar (month grid with weekends, public holidays, approved and waiting leave).
- **My HR, employee self-service (HR-6).** Every signed-in user whose login is linked to an employee sees their own payslips (approved months only), earnings certificate, leave balances and requests (ask for leave, withdraw a waiting request), attendance with clock in and out, and can update their contact details.
- **Documents, checklists and import (HR-7).** Employee documents (contract copies, ID, qualifications, permits) with expiry dates, warnings for expired or soon-to-expire ones and optional sharing with the employee; onboarding and offboarding checklists from editable templates with an overview of open tasks; employee import from Excel or CSV with a check first.
- **Payroll corrections and final pay (HR-8).** Cancel the payment of a paid payroll, or reopen an approved payroll to a draft (accounting entries reversed with equal and opposite entries, loan repayments given back, reason required and kept, refused while tax payments are recorded for the month). A Final pay card for employees who left: unused leave paid out, notice paid or recovered, loans taken back from the last pay.

Details of each step, with the API and the limits, are in the README (entry "HR and Payroll improvements, version 1.6.0").

## Database

Five migrations were added after `V20__core_gift_cards_loyalty.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Step |
|---|---|
| `V21__hr_adjustments_loans.sql` | adjustments, advances and loans |
| `V22__hr_remittances.sql` | tax and social-security payments |
| `V23__hr_leave_improvements.sql` | leave (half days, monthly earning, carry-over) |
| `V24__hr_documents_checklists.sql` | documents and checklists |
| `V25__hr_payroll_corrections_final_pay.sql` | payroll corrections and final pay |

Lists, exports, PDFs and My HR change no table. One account is added to the chart of accounts by the program itself, on existing installations too, with no migration: Staff Advances and Loans (Rwanda 1250, Chad 421).

## Upgrading an installation that ran 1.5.0

Back up the database, install the new build and start it. Flyway applies V21 to V25. Nothing else is needed: the license is unchanged and existing employees, contracts, payrolls and leave keep working. Annual leave is switched to "paid out when leaving" by the migration (change it under Leave settings); the two ready-made checklist templates appear the first time the checklist list is opened and no template exists.

## Before the first customer build (NIWE), unchanged from 1.1.0

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties`.

## Tested, and not tested

Every step was tested against PostgreSQL 16 over the HTTP API and driven in a browser (desktop and phone width), and the whole set (all earlier modules included) was re-run after the last step with no failure: 92 result files, 0 failures. HR suites (checks passed, none failed): backend t61 to t68 (113, 68, 134, 71, 65, 94, 177 and 91 checks) and browser walks walk44 to walk51 (44, 17, 40, 23, 33, 32, 64 and 36 checks). The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V25 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), imports of very large files, real data in volume, a license signed with your real key, branch-limited users on the payslip, loan and tax-payment screens (refused by design, tested for documents, checklists and the final-pay and reopen actions only), employees with several contracts and loans, a login linked to an employee in a branch-limited setup, and the Employees module being unlicensed while My HR is open.

## Known limits of the new features

- Lists are built in full on the server and then paged; fine for thousands of rows, not for hundreds of thousands.
- The returns for the tax and social-security authorities are schedules to key in or upload by hand; no electronic filing format of RRA, RSSB, DGI or CNPS is built.
- Carry-over uses the leave type's current settings for past years (no history) and has no expiry date; with monthly earning "remaining" can be negative when leave was booked ahead.
- The unused leave suggested in the final pay is the balance of the leaving year as the balance screen shows it; change the days by hand when your policy differs. No certificate of service is produced.
- Checklist tasks can only be ticked by users with HR manage; import only adds new employees (it never updates existing ones) and does not link staff logins; document expiry is shown but no reminder is e-mailed; uploaded files are not virus-scanned.
- Reversal entries are dated the day they are made (an accounting period that is already locked refuses them); payslips already e-mailed are not recalled; final pay is not recalculated when the last day is changed afterwards (save it again).
