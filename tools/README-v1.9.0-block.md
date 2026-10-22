### Version 1.9.0 - POS improvements, version 1.9.0

Eight steps in the POS module (see `RELEASE-NOTES-v1.9.0.md` for the summary, database and upgrade notes; `POS-CHANGES.md` has the full detail of each step).

- **Lists and exports.** `page`, `size`, `q` and filters on receipts, returns, shifts and cash movements (total in `X-Total-Count`); Excel, PDF and CSV of the same filtered lists.
- **Cash control.** `/api/sessions/{id}/cash-movements` (paid in, paid out, safe drops with reasons and manager approval above a limit), cash counts, closing with a denominations count and variance note, accounting entries for the movements; `/api/pos/settings`; migration V39.
- **X and Z reports.** Printable X (mid-shift) and Z (closing, numbered) reports per shift and per cashier; migration V40.
- **Void and return controls.** `POST /api/returns/void-sale`, reasons and manager approval rules, refunds charged to the drawer that pays them (`sales_return.register_session_id`), `/api/pos/exceptions` log; migration V41.
- **Discounts and price changes at the till.** Cashier allowance, manager approval by PIN, reasons, whole-sale discount (`POST|DELETE /api/orders/{id}/discount`), price change on a line; migration V42.
- **Receipts.** 80 mm / 58 mm paper, header and footer text, logo, QR code of the invoice number, original vs COPY marking (`POST /api/orders/{id}/receipt-print`); migration V43.
- **POS reports.** `/api/reports/pos-by-cashier | pos-by-hour | pos-by-payment | pos-exceptions | pos-shift-comparison` with Excel, PDF and CSV.
- **Register setup and permissions.** Per-register on/off, usual and highest opening float, list of allowed cashiers; three new approval permissions (`APPROVE_POS_VOID_RETURN`, `APPROVE_POS_PRICE`, `APPROVE_POS_CASH`); migration V44.
