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
