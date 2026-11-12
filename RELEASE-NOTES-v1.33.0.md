# Release 1.33.0 - Online Store phase 3 (4 of 4): returns and refunds after completion

A customer can now ask to **return goods from a completed online order**. Staff approve or refuse; when the goods are back, one click puts them back in stock, reduces the sale in the books and gives the money back **the way the customer paid**.

This is the last feature of Phase 3. Still to do, separately: a real payment provider and a real text (SMS / WhatsApp) provider.

## For customers

- On the order page (`/shop/track/CODE`), a completed order shows **Return goods** while the return period lasts (default **14 days after completion**).
- The customer **picks the items and quantities** (partial returns), writes a reason and gives the **phone number of the order** (not needed when signed in to the account that holds the order). The form shows what the goods are worth: what they really paid for them, **after any coupon discount**. **The delivery fee is not refunded.**
- The same goods cannot be asked back twice: what is asked, approved or already refunded is held against the order line.
- The page shows each return with its status: waiting, approved (bring the goods back), refused (with the reason), refunded (with the amount and reference).
- The customer gets an **e-mail and, if they ticked the text box at checkout, a text** at every step, in their language: request received, approved, refused, refunded.

## For staff

- **Online Store > Returns** (new): tabs Waiting / To refund / Done / Refused / Everything, with counters; search by code, name or phone. The same counter shows on the Online orders screen.
- **Approve** (with an optional note, for example where to bring the goods) or **Refuse** (a reason is required; the customer sees it).
- **Goods received: put back and refund** on an approved return:
  - the goods go back in stock through the existing **sale order return** (so stock, revenue, credit figures and reports follow, like any return); you can choose the location, the default is where they were taken from;
  - **paid online**: refunded **through the payment provider**, or **by hand** with the reference of the refund you made. Several partial refunds add up; when everything paid is back the payment becomes Refunded;
  - **paid in cash** (cash on delivery): the screen says to hand the cash back and records it.
- All or nothing: if the stock side or the refund fails (provider says no, no reference given), nothing is recorded and the return stays approved.
- **Shop settings > Returns**: the return period in days (0 = no returns through the shop).
- The shop owner is told by e-mail and text when a return is asked (to the same address and phone as new orders).
- The online orders screen shows "Given back so far for returns: X of Y" for part-refunded orders; the Online payments reconciliation counts the part refunded under Refunded and the rest under In the books.

## Safety

- Asking needs the order code **and** the phone number of the order (or the signed-in account that holds it), so knowing a code is not enough. 20 requests per connection per hour.
- Quantities are checked under a lock on the order, so two requests at the same moment cannot both take the last unit. Staff decisions and processing are done under a lock on the return, so two staff cannot process it twice.
- The refund can never exceed what was paid online, minus what was already given back.

## Not included (on purpose)

- The delivery fee is not refunded. A coupon discount follows the goods (a returned item is worth what was paid for it).
- A return asked by staff for the customer (customers ask, staff decide).
- Exchanges, store credit, return shipping labels, restocking fees.
- The customer cannot withdraw a request (staff can refuse it).
- Refunds are in the shop's currency exactly as paid; partial refunds through a real provider depend on that provider supporting them (the adapter's `refund(reference, amount, reason)` receives the amount).

## Technical

- Migration `V71__online_store_returns.sql`: `online_store_settings.return_window_days` (default 14); `online_order.refunded_total` (filled for orders already refunded); tables `online_return` and `online_return_line`.
- New: `OnlineReturn`, `OnlineReturnLine`, `OnlineReturnRepository`, `OnlineReturnService`. `OnlinePaymentService.refundPart` (and a shared `giveBack`); `OnlineMessages.notifyShopNote`.
- New endpoints. Public: `GET /api/public/store/orders/{code}/returns` (options), `POST /api/public/store/orders/{code}/returns` (ask; header `X-Shop-Token` optional). Staff: `GET /api/online-store/returns`, `GET /returns/{id}`, `POST /returns/{id}/approve|refuse|process`; `GET /orders/pending-count` gains `returnsRequested` and `returnsToRefund`. The order answer gains `refundedTotal`; the settings gain `returnWindowDays`.
- Return codes are the order code with `-R1`, `-R2` ... Processing maps each returned line to the sale order lines of the same product, oldest first (a coupon can split a product over two sale order lines).
- `tools/online-store-smoke.mjs` has a returns part (wrong phone refused, ask, approve, process in cash, second request refused).
- 33 new French texts on the server, 47 on the screens.
