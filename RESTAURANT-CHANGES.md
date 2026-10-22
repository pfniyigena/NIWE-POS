# Restaurant module — v1.8.0 changes

## RS-1 Tests, paging, exports
- Closed receipts, wastage and reservations lists: search, status/type/method/server/date filters, paging with X-Total-Count.
- Excel/PDF/CSV exports: receipts, wastage, reservations, menu items, tables (`/api/restaurant/.../export`).
- Order lines/payments now ordered by id (stable receipts).
- Tests: t77 (146 checks), walk60 (19 checks).

## RS-2 Discounts, tips, service charge  (migration V32)
- New `restaurant_settings` (service charge % per order type + VAT on it, manager discount limit, tips on/off + presets, cash rounding unit). Screen: Admin > Restaurant > Settings.
- Per-line % discount and whole-order discount (percent or amount, pro-rata over lines), reason required; discounts above the limit need a manager (ROLE_ADMIN, OPERATIONS_MANAGE or PERM_OVERRIDE_DISCOUNT_CAP).
- Service charge snapshotted on the order; can be waived by an approver. Rounding adjustment. Tip at checkout (presets), change returned out of cash only.
- Accounting: credits Service charge revenue (4020 / 4213 Chad), Tips payable (2890 / 7061), Rounding difference (5860 / 6588).
- Receipt shows discount, service charge, rounding, tip, change.
- Tests: t78 (62 checks), walk61 (26 checks).
- Known limits: tips are recorded as a liability only (no payout screen); service charge is % of pre-tax net; room-service tip goes through the room charge.

## RS-3 Choices, kitchen notes, courses, allergens  (migration V33)
- Menu items can have option groups (Size, Cooking, Extras): required or optional, up to N choices, each option adds (or takes off) a price. Edited under Admin > Restaurant > Menu ("Choices & allergens" button on each item). Ordered lines keep their own copy of what was picked; later menu edits never change a bill.
- Item panel on the order screen: quantity, choices, course, kitchen note with tap-to-add quick notes (list kept under Restaurant > Settings). Items without choices still go straight onto the order; the pencil button on any line edits its note/course/choices (choices and course only until it is sent).
- Courses 1-5: later courses wait ("on hold") when the order is sent; "Fire course N" releases them. Each menu item has a usual course.
- Allergens (11 standard tags) on menu items; the waiter records guest allergies on the order; lines that clash are flagged on the order screen and on the kitchen ticket.
- Prices: unit price + options per unit feeds discounts, service charge, tax, rounding, receipts and accounting automatically.
- Tests: t79 (74 checks), walk62 (37 checks).

## RS-4 Split bills, voids and comps, moving items  (migration V34)
- Split a table's bill into separate checks (by item, part of a quantity, or by seat): "Split / move items…" on the order screen. Each check is paid on its own and posts its own sale; the table is freed when the last check closes. Discounts, service charge, courses and kitchen progress travel with the items. A table's checks show in a bar at the top of the order screen.
- Move items onto another open bill (an emptied bill closes itself), and move a whole bill to another free table.
- Seat number per item (optional) in the item panel.
- Voids: an item not yet sent comes off freely; one already sent needs a reason and a manager (administrator, Operations manage, or the override-discount permission). Comps: a manager gives an item free with a reason (and can take it back). Cancelling an order that went to the kitchen needs a reason and a manager.
- Admin > Restaurant > Voids and comps: log of voids, comps, cancelled orders and receipt copies (who, approved by, why, value), with filters, paging and Excel/PDF/CSV export.
- Receipts: every print is counted; from the second print the receipt is marked COPY and logged.
- Tests: t80 (106 checks), walk63 (34 checks).
- Known limit: a voided item that was already cooked is not turned into a wastage entry automatically; record wastage separately.

## RS-5 Reservations v2 (V35)
- Bookings hold a table for a time window (booking's own minutes or the restaurant's turn time) instead of a whole day; the clash message names the range ("19:00 to 20:30"). SEATED bookings block only while their bill is open.
- Settings: opening/closing time, slot step, turn time, days ahead, deposit per guest, refund window, reminder hours, grace minutes, no-show block count, reminder text.
- Slots (`/reservations/slots`, `/api/public/reservations/slots|info`), tables free at a time (`available-tables?at=&partySize=`).
- Online booking checks hours, slot grid, horizon, table size, no-show block (phones compared by last 9 digits); guests cannot set duration or waive the deposit.
- Deposits: REQUIRED/HELD/APPLIED/REFUNDED/FORFEITED, accounts 2855/4170 (Chad 4196/7582). Taken at booking or later; applied to the bill on seating; the unused part is refunded at checkout; cancelling the bill refunds it. Cancel refunds by the window or by the manager's choice; no-show keeps it (manager only).
- No-show after grace; bulk "mark overdue" (manager); guest history (earlier no-shows/visits) by phone.
- Reminders: due list, text from template, e-mail (or mark as sent by hand), 15-minute job.
- Waiting list: `/api/restaurant/waitlist` (add, notify, seat, leave), estimated wait, free-table hint.
- Checkout: payments may be empty when a deposit covers the bill; order DTO has `depositApplied`; receipt shows it.
- Tests: t81 (168), walk64 (55); t77 updated for time windows.
- Limit: deposits are recorded as cash received (no online payment).

## RS-6 Kitchen display v2 (V36)
- Every dish carries its own clock: sent, started, ready and served times. Prep target per menu item (Menu screen, minutes) or the restaurant default (Settings, 15 min); warning at a set % of the target (default 75%), then LATE. Tickets turn amber/red and the timer ticks live.
- Kitchen screen: summary strip (waiting, cooking, ready, late, oldest), per-station counts and today's average prep time, station tabs, Start / Ready / Served per dish, "Bump" clears a station's dishes on an order in one tap, "Remake" sends a dish back with a fresh clock (counted and audited).
- 86 board: kitchen (any logged-in user) marks a dish sold out with a reason; sold-out tiles are disabled on the order screen and the server refuses them; the board is also on the Menu screen.
- Expo / pass screen (`/admin/restaurant/kitchen/expo`): open orders with what is ready and who it is waiting on, complete orders first, "Serve ready" and remake from the pass.
- Waiters can "Send back" a sent dish from the order screen; the line shows "remade ×N".
- Endpoints: `GET /kitchen/expo`, `GET /kitchen/summary`, `POST /kitchen/orders/{id}/bump|serve-ready`, `POST /kitchen/orders/{id}/lines/{lineId}/refire`, `POST /menu/items/{id}/sold-out`.
- Tests: t82 (70 checks), walk65 (40 checks).

## RS-7 Reports and end of day (V37)
- Admin > Restaurant > Sales analysis (`/admin/restaurant/analysis`): date range with presets; headline figures (net sales, billed, checks, covers, average check, per cover, margin, tips, minutes at the table); sales by hour (by the hour the bill was paid), weekday, day, order type and payment method; menu mix with category totals and menu engineering (Star, Plowhorse, Puzzle, Dog, from popularity and profit per plate); servers (checks, covers, sales, average check, service charge, tips, discounts, voids and comps); table turns (parties, covers, turns per day, minutes per party, sales per cover; a split table counts once); voids/comps/cancelled summary. Every table exports to Excel, PDF and CSV.
- Admin > Restaurant > End of day (`/admin/restaurant/end-of-day`): the day's takings, tax by rate and on the service charge, payments by method (cash, other, room charge, booking deposit applied), tips by server, deposits, controls, tables still open. Cash-up: opening float + cash sales + cash deposits taken - cash deposits refunded + paid in - paid out - tips paid out = what should be in the drawer; count the drawer, see over/short, leave a float, bank the rest. Any difference needs a note. Only a manager can close; open tables block closing unless the manager chooses to close anyway.
- Closing numbers the day Z-0001, Z-0002... and freezes the figures. A sale paid after closing is flagged ("changed since close"). A manager can reopen with a reason (kept in the list, audited); a reopened day keeps its opening float. Next day's float is suggested from what was left in the drawer.
- Paid in / paid out / tips paid out entries (any signed-in user adds, a manager removes while the day is open, none on a closed day). Booking deposit movements are recorded automatically (taken, refunded, kept; only cash deposits touch the drawer).
- Printable Z-report page without menus (`/admin/restaurant/end-of-day/print/:date`), Excel/PDF/CSV of the day and of the closed-days list.
- Permissions: reading and exporting need Operations view (or admin); closing/reopening need a manager (admin, Operations manage or override-discount).
- Endpoints: `GET /api/restaurant/reports/analysis`, `/end-of-day`, `/end-of-day/history`, `POST /end-of-day/close`, `/end-of-day/{date}/reopen`, exports under `/analysis/export?table=`, `/end-of-day/export`, `/end-of-day/history/export`; `POST|DELETE /api/restaurant/cash-events`.
- Tests: t83 (137 checks; net sales agree with the order records and the ledger), walk66 (60 checks).
- Limits: a sale belongs to the day it was paid (store time zone); the cash-up has one drawer for the whole restaurant; hourly sales use the time the bill was paid.

## RS-8 QR menu and online ordering (V38)
- Table QR codes: every table has a private code (`/menu/<code>`). Guests scan, see the live menu (sold-out dishes greyed with the reason, allergens, option groups) and send an order. Admin > Restaurant > Online orders > "Delivery areas & QR codes" lists the addresses, makes a fresh code for a table (the old one stops working at once) and opens a print sheet (one card per table; a separate poster for the website page).
- Takeaway and delivery page (`/order`): name and phone, address and delivery area for delivery, e-mail optional, note. Follows the opening hours (tables are exempt), a minimum order (global and per area), a pause switch and a message shown on top of the menu.
- Orders are requests. They wait in the queue and touch no bill until staff accept (or, if chosen in Settings, table orders are accepted automatically). Accepting opens the bill (table: joins the table's open bill, else opens one; takeaway/delivery: opens a takeaway/delivery bill), adds the dishes with their options and notes, sends only the new dishes to the kitchen, tags the bill "Ordered from the table"/"Online order" with the code, and records the promised time (usual ready time + the area's extra minutes, or a time chosen by staff). If a dish has sold out since, staff see a warning and can accept without it. Rejecting needs a reason the guest sees; unanswered requests lapse (default 20 min, checked every minute); the guest can cancel while it waits.
- Delivery fee: added to the bill as a line without a product (never reaches the kitchen, taxed as food only, no stock effect).
- Tracker (`/order/track/<code>`, refreshes every 5 s): Waiting, Accepted, Preparing, Ready, On its way (delivery), Served/Complete, or Not accepted/Cancelled/No answer, with the promised time and a price estimate. The tracker shows only the first name and never the phone.
- Abuse limits: honeypot field, 1-40 lines, quantity 1-20, 60 items in total, waiting requests per phone (last 9 digits, default 3), 5 waiting per table.
- Settings (Settings > QR menu and online ordering): QR on/off, online on/off, takeaway, delivery, auto-accept table orders, pause, usual ready time, minimum order, waiting orders per phone, lapse minutes, message.
- Staff screen (`/admin/restaurant/online-orders`): Waiting / Accepted / Rejected / Everything, type filter, search, waiting-minutes warning, refreshes every 8 s; accept with ready time and waiter, reject with reason.
- E-mail to the guest on accept/reject when an address was given and e-mail is set up.
- Endpoints: public `GET /api/public/restaurant/menu?table=`, `POST /orders`, `GET /orders/{code}`, `POST /orders/{code}/cancel`; staff `GET /api/restaurant/online-orders` (paged), `/pending-count`, `/{id}`, `POST /{id}/accept|reject`, `POST /expire-stale` (manager); `GET|POST|PUT|DELETE /delivery-zones` (write = manager), `GET /qr`, `POST /qr/{tableId}/regenerate` (manager).
- Tests: t84 (153 checks), walk67 (57 checks).
- Limits: no online payment (pay at table / pick-up / delivery); a delivery fee area that was used is switched off rather than deleted; the table menu does not check that the guest is really at the table (a code that leaks can be renewed).
