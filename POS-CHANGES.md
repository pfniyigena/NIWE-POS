# POS module improvements (v1.9.0)

## PO-1 Lists, paging and exports
- Receipts (`/receipts`), Returns (`/returns`) and Shifts (`/admin/shifts`) now have search, date range, filters, 25-per-page paging and Excel / PDF / CSV export of the filtered list.
- Receipts: search invoice, customer, cashier, register, product; filter by payment method, register, and kind (rung offline, sync problem, EFRIS failed, part on credit, charged to a room). With no filter the latest 200 sales show as before.
- Returns: search invoice, return number, customer, reason; filter by refund method and day.
- Shifts: search register or cashier; Open/Closed; register; day opened. Expandable cashier list, add cashier and close-all kept.
- API: `GET /api/orders/completed`, `/api/sessions`, `/api/returns` accept `q, status, method, registerId, from, to, page, size` and return `X-Total-Count`; exports at `/api/orders/completed/export`, `/api/sessions/export`, `/api/returns/export` (Sales view or manage).
- Tests: t85 (76), walk68 (19).

## PO-2 Cash control
- New **Cash drawer** page for every shift (`/cash-control/:id`, a 💵 Cash button on the till, and "Cash" links on Sessions and Shifts): where the drawer stands (float, cash sales, cash refunds, paid in, paid out, drops, expected cash) and a form to record **paid in**, **paid out**, **drop to safe**, or a **count** in the middle of the shift (no effect on the expected cash; the difference is kept).
- Anyone working on the shift can record; a manager (admin, Sales manage, or discount-override) can **void** a mistaken entry while the shift is open. A paid-out or drop cannot exceed the cash in the drawer.
- Closing a shift: count by **notes and coins** (or one typed total), shows over/short live, optional/required **note** for a difference, **allowed difference** (tolerance), and records expected cash, who closed, and the count. The Z-report now shows paid in/out/drops, the movement list, the note and who closed.
- **Accounting**: over/short is posted when a shift closes (new account *Cash Over / Short*); paid-outs post to *Till Paid-out Expenses*, supplier (Accounts Payable), staff advance (Staff Advances) or owner withdrawal; owner money paid in posts to Owner's Equity; float top-ups, drops and counts post nothing (one pooled cash account). A void posts the reverse. New accounts are added to existing charts automatically.
- **POS settings** (`/admin/pos-settings`): allowed difference, note required, notes-and-coins count required, paid-out above X needs a manager PIN (shared `ManagerApprovalService` for later use), safe-drop reminder amount.
- **Cash movements** list (`/admin/cash-movements`) with search, kind/voided filter, register, dates, pages, Excel/PDF/CSV.
- Database: V39 (`pos_settings`, `cash_movement`, four columns on `register_session`).
- Tests: t86 (83), walk69 (25).

## PO-3 X and Z reports
- **X report** (`/sessions/:id/x-report`, 📄 X report on the till, the drawer page and the Shifts list): the shift so far, any time, nothing closed or cleared. **Z report** (`/sessions/:id/z-report`): the closed shift, **numbered per register (Z no. 1, 2, 3...)** and **frozen when the shift is closed** — a late return or later change never alters it. Shifts closed before this release show their Z report worked out live, without a number.
- Contents: sales (count, items, shelf price, discounts and promotions, net, tax, total, average), paid by (per payment method with counts), tax by rate, refunds (count, total, per method), the cash drawer (float, cash sales, refunds, paid in/out, drops, expected, counted, difference, note), cash in/out list, watch points (sales with a discount, carts dropped with items, offline sales, credit, room charges, gift cards sold, voided cash entries) and a line per cashier.
- Printing a Z report is counted: the first printing is the original, later ones are marked **COPY n**. Looking at it does not count. Closing a shift now opens the Z report ready to print.
- Excel / PDF / CSV of any X or Z report (Sales view or manage). The Shifts list shows the Z number and links to the right report; its Excel/PDF/CSV gained Z no., expected cash and note columns.
- Database: V40 (`z_number`, `z_snapshot`, `z_prints` on `register_session`, unique per register).
- Tests: t87 (48), walk70 (23).

## PO-4 Void and return controls
- **Void a sale** (new **Void** button on each row of Receipts): a wrong sale caught at the till is refunded **in full** and kept on record as **VOIDED** (who voided it, why, who approved, when). Stock goes back, Accounting is reversed, gift cards and points are given back and the EFRIS credit note is made, all through the normal return logic. The printed receipt shows a **VOIDED** banner. Receipts has a new **Voided sales** filter.
- Rules (POS settings): voiding needs a manager (their sign-in, or their PIN typed at the till); a sale can be voided only while its shift is open (afterwards it is a return); a reason is always needed. A sale partly returned, or partly sold on credit or charged to a room, cannot be voided (use a return).
- **Returns**: new rules in POS settings: a **return window** (older sales need a manager), a **refund limit** above which a manager approves, **reason required**, and an editable **list of reasons** shown as a drop-down on the return form. When approval is needed the form asks for the manager's PIN; the return records who processed and who approved it, shown on the Returns list.
- **Refunds hit the right drawer**: a cash refund is now charged to the drawer of the shift where the money actually went out (previously always the shift of the original sale), so each Z report and the expected cash match the cash in the drawer. Old returns keep the old attribution.
- **Voids & returns log** (`/admin/pos-log`, side menu): voided sales, returns (with approver) and carts dropped before payment, with search, type, register, dates, pages, Excel / PDF / CSV.
- API: `POST /api/returns/void-sale`, `POST /api/returns` accepts `approverPin`, `GET /api/pos/exceptions` (+ `/export`), `GET/PUT /api/pos/settings` has six new fields.
- Database: V41 (void columns on `sale_order`; register session, processed by, approved by and void flag on `sales_return`; six settings columns).
- Tests: t88 (57), walk71 (25).

## PO-5 Discounts and price changes at the till
- **Who may discount**: an admin or a holder of the discount-override right still gives a discount alone. A cashier may now give one up to the **cashier allowance** (POS settings, default 0 = none); above it a **manager approves** by signing in or typing their PIN in a small panel on the till. The store maximum discount still applies (unless the person giving or approving holds the override right). Taking a discount off is always allowed.
- **Reasons**: a discount can carry a reason chosen from an editable list (optional, or required by a setting). The reason and the approving manager are kept on the line and shown under it.
- **Discount on the whole sale** (new **% Discount** button in the cart): a percent or an amount, with reason and approval as above; shown as its own row in the cart and on the receipt ("Includes a discount of 10%"). Typed as an amount, it is worked out again as items are added or removed. Every line is cut by the same share, so returns, refunds, Accounting, EFRIS and the X / Z reports see the true prices.
- **Price change** (✎ on a cart line): a manager (signed in, or a PIN) sets a new price for that line with a reason; the normal price stays visible, ↺ puts it back. Quantity changes keep the price; adding the same item again makes a separate line at the normal price. A price below the item's cost is refused unless the approver holds the override right (setting). Both can be switched off in POS settings.
- **Log**: the Voids & returns log (`/admin/pos-log`) now also lists **Discounts** (value taken off, reason, approver) and **Price changes** (amount below the normal price, reason, approver), with the same filters and exports.
- API: `PATCH /api/orders/{id}/lines/{lineId}` accepts `discountReason, approverPin, unitPrice, priceReason, resetPrice`; `POST/DELETE /api/orders/{id}/discount`; order and line DTOs carry the new fields; `/api/pos/settings` has five more fields. Offline sales keep the old admin-only discount rule.
- Database: V42 (settings columns; whole-sale discount columns on `sale_order`; reason, approver and price-change columns on `order_line`).
- Tests: t89 (58), walk72 (17).

## PO-6 Receipts
- **POS settings > Receipts**: paper (**80 mm** or **58 mm** thermal roll — the print dialog is told the roll width and the layout narrows), extra header lines under the business details (opening hours, phone...), the **footer text** (was a fixed "Thank you!"), print the business logo or not, and a **QR code of the invoice number** (so scanning it at the returns desk finds the sale; sales sent to EFRIS keep their own fiscal QR code). Nothing changes until a setting is changed: the defaults print what the receipt printed before.
- **Original and COPY**: pressing Print counts the printing. The first is the original; every later printing is marked **COPY 1, COPY 2...** ("not the original") at the top, and who printed it is kept. Only pressing Print counts, looking on screen does not; if counting fails the receipt still prints. Receipts shows "printed ×n" and **Voids & returns** lists every reprint (with who and which copy; filter *Receipt reprints*).
- API: `POST /api/orders/{id}/receipt-print` returns `{copyNumber, printCount}`; the order carries `printCount`; `/api/pos/settings` has five more fields; `/api/pos/exceptions?type=REPRINT`.
- Database: V43 (receipt columns on `pos_settings`, `print_count` on `sale_order`, table `receipt_print`).
- Tests: t90 (25), walk73 (16).

## PO-7 POS reports
- Five new reports in a new **POS Operations** group on the POS Reports page, each with the date range, register and branch filters and Excel / PDF downloads (CSV also from the API):
  - **POS Sales by Cashier** — shifts worked, sales, items, sales total, discounts given, returns and refunded amount (charged to the cashier who rang the original sale), voided sales, net sales, average sale.
  - **POS Sales by Hour** — sales, items, total, average and share for each hour of the day (busiest hours show where staff are needed).
  - **POS Sales by Payment Method** — payments received, refunds and net per method, with the share of takings.
  - **POS Exceptions** — voided sales, returns, discounts, price changes, receipt reprints and dropped carts in one list with who did it and who approved it.
  - **POS Shift Comparison** — one row per shift: Z no., register, cashier, sales, average sale, items per sale, discounts, refunds, voids, expected and counted cash, cash difference, and how far the shift's sales were above or below the average shift.
- A sale that was voided stays in *sales* and shows again as a refund, so *net* figures are what was kept.
- API: `GET /api/reports/pos-by-cashier | pos-by-hour | pos-by-payment | pos-exceptions | pos-shift-comparison` (`from, to, registerId, branchId, format=json|xlsx|pdf|csv`; Reports view).
- Tests: t91 (47), walk74 (12).

## PO-8 Register setup and permissions
- **Registers > Edit > Till rules**: **In use** (switch a till off to stop new shifts being opened on it; its history and reports stay; a till with an open shift must be closed first), **usual opening float** (filled in when a shift is opened), **highest opening float allowed** (a shift cannot be opened above it), and **who can work this till** (tick the people; none ticked = anyone who can sell). Managers can always work any till. The list shows float, limit, cashiers and a *Switched off* badge, and the Open session page only offers the allowed people. Every change is kept in the audit log.
- **Enforced where it matters**: opening a shift, joining a shift with a PIN, switching cashier and the admin's *Add cashier* all check the till's cashier list; a cashier who is not on it gets "not allowed to work at <till>".
- **Three new approval permissions** (Staff > edit, each given on its own, none gives any other manager right): **Approve voids and returns at the till**, **Approve price changes at the till** and **Approve big paid-outs and cash corrections**. Someone with one of these can type their PIN to approve exactly that, nothing else; an admin, a Sales manager or a holder of the discount-override right still approve everything as before. Discount approval is unchanged.
- API: `/api/registers` carries `active, defaultFloat, maxFloat, allowedCashierIds, allowedCashierNames` (leaving `allowedCashierIds` out keeps the list; an empty list means anyone); `POST /api/registers/{id}/sessions` refuses a switched-off till, a float over the limit and a cashier not on the list.
- Database: V44 (`register.active, default_float, max_float`, table `register_cashier`, new values allowed in `user_permissions`).
- Tests: t92 (56), walk75 (18).
