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
