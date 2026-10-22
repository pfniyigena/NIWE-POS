### Version 1.8.0 - Restaurant improvements, version 1.8.0

Eight steps in the Restaurant module (see `RELEASE-NOTES-v1.8.0.md` for the summary, database and upgrade notes; `RESTAURANT-CHANGES.md` has the full detail of each step).

- **Lists and exports.** `page`, `size`, `q` and filters on the closed-receipts, wastage and reservations lists (total in `X-Total-Count`); Excel, PDF and CSV of receipts, wastage, reservations, menu items and tables.
- **Discounts, tips, service charge.** `/api/restaurant/settings` (service charge per order type, manager discount limit, tips, rounding); line and order discounts with a reason; tip at checkout; accounting for service charge, tips payable and rounding; migration V32.
- **Choices, notes, courses, allergens.** `/api/restaurant/menu/items/{id}/options`, courses 1-5 with `fire-course`, allergen tags and guest allergies on the order; migration V33.
- **Split bills, voids, comps.** `POST /orders/{id}/split|transfer|move-table`, line `comp`, void rules with manager approval, `/api/restaurant/voids` log (+ export), receipt copy marking; migration V34.
- **Reservations v2.** Time-window bookings, `/reservations/slots`, deposits (`/deposit`), no-show handling, reminders, waiting list `/api/restaurant/waitlist`, public `/api/public/reservations/slots|info`; migration V35.
- **Kitchen display v2.** Per-dish clocks and prep targets, `/kitchen/summary`, `/kitchen/expo`, `/kitchen/orders/{id}/bump|serve-ready`, `/lines/{lineId}/refire`, `POST /menu/items/{id}/sold-out`; migration V36.
- **Reports and end of day.** `/api/restaurant/reports/analysis|end-of-day|end-of-day/history` (+ exports), `POST /end-of-day/close` and `/{date}/reopen`, `POST|DELETE /api/restaurant/cash-events`; screens Sales analysis, End of day and a printable Z-report; migration V37.
- **QR menu and online ordering.** Public, no login: `/api/public/restaurant/menu?table=`, `POST /orders`, `GET /orders/{code}`, `POST /orders/{code}/cancel`; staff: `/api/restaurant/online-orders` (+ `/pending-count`, `/{id}/accept|reject`, `/expire-stale`), `/delivery-zones`, `/qr`; pages `/menu/:token`, `/order`, `/order/track/:code`; migration V38.
