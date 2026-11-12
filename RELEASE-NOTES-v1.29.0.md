# Release 1.29.0 - Online Store phase 2: pay online

Customers can now pay at checkout with **mobile money or card**, and the order reaches the shop only once the payment is confirmed. The payment side is built once, for any provider, and comes with a **test provider** so the whole path can be tried without a real account. A real provider (MTN MoMo, Airtel Money, a card gateway) is added later by writing one class; see `docs/online-store-payment-providers.md`.

Switched off by default. Nothing changes for a shop that does not turn it on.

## How it works for the customer

1. At checkout the customer picks **cash** (as before), **mobile money** (and the number to pay from) or **card**.
2. The order is created as **Awaiting payment**. It holds no stock and does not appear in the shop's New list. Mobile money asks the customer to approve on the phone; card (and the test provider) send the customer to the provider's page.
3. The order page refreshes every 4 seconds while it waits. When the payment is confirmed the order becomes **New** (the shop gets its "new order" e-mail, marked "already paid online", and the customer gets "we received your payment").
4. A declined or expired payment leaves the order waiting: the customer sees **Try again** (same or other way, up to 5 tries). An order not paid after 24 hours is cancelled by the system.
5. The customer can cancel while the order is New or Awaiting payment. If it was already paid, a refund becomes due.

## For staff

- **Online orders**: new tabs **Awaiting payment** and **Refunds due** (with counts), a payment badge on each order, and for an order paid online "Already paid online: nothing to collect" at completion. Completing records the payment on the invoice with the way the customer paid (Mobile money or Card), whatever the cash box says.
- **Refunds**: rejecting or cancelling a paid order makes the refund **due**. The order shows "Refund through the provider" or "I refunded it myself" (with the reference you got). The customer gets a refund e-mail. A payment that arrives after the order was already cancelled becomes a refund due as well, so money is never lost.
- **Online payments** (new screen, Online Store menu): every try to pay, with a summary: **Paid**, **In the books** (orders completed, payment recorded on the invoice), **Held for open orders**, **Refunds due**, **Refunded**, payments **to review**, failed. What needs a person is flagged: a payment under review, a refund due, a payment stuck pending. A payment under review (wrong amount or currency, or a second payment on an order that was already paid) is closed with a note once you have dealt with it, usually by refunding it by hand.
- **Shop settings > Online payments**: switch on, choose the provider, minutes the customer has to pay (default 30). The webhook address to give the provider is shown.

## What keeps the money safe

- The provider's result arrives on a **signed webhook** (`POST /api/public/store/payments/{provider}/webhook`). A call with a wrong or missing signature is answered 401 and changes nothing. A valid one is logged and always answered `ok`.
- A payment is settled **once**, under a row lock: a repeated webhook, or a webhook and the status check at the same moment, cannot pay an order twice.
- The **amount and currency** the provider reports must equal what we asked for; otherwise the payment goes to review and the order is not released.
- If the webhook is lost, a check every 30 seconds asks the provider about each pending payment (after 45 seconds, then at most once a minute) and closes it when its time is up. A payment that still arrives after it was closed is accepted, not lost.
- Secrets (keys, the webhook secret) are read from properties or the environment by the provider class, never stored in the database.

## Technical

- Migration `V67__online_store_payments.sql`: `online_store_settings.online_payments_enabled`, `payment_provider`, `payment_expiry_minutes`; `online_order.payment_status`, `payment_channel`, `payment_reference`, `paid_amount`, `paid_at`, `refunded_at`, `refund_reference`; tables `online_payment` (one row per try) and `online_payment_webhook` (every valid call, kept for disputes).
- New order status `AWAITING_PAYMENT`. Order `payment_method` is `COD` or `ONLINE`; `payment_status` is `NONE`, `AWAITING`, `FAILED`, `PAID`, `REFUND_DUE` or `REFUNDED`.
- `PaymentProvider` (interface), `SimulatedPaymentProvider`, `OnlinePaymentService`, `OnlinePaymentJob`, entities `OnlinePayment`, `OnlinePaymentWebhook`.
- New endpoints: public `POST /api/public/store/payments/{provider}/webhook`, `POST /api/public/store/orders/{code}/pay`, and for the test provider `GET` and `POST /api/public/store/payments/simulated/{reference}[/approve|decline]` (404 unless the test provider is on); staff `POST /api/online-store/orders/{id}/refund`, `GET /api/online-store/payments`, `POST /api/online-store/payments/{id}/resolve`, `GET /api/online-store/payment-providers`. `pending-count` also returns `awaitingPayment`, `refundsDue`, `paymentsToReview`.
- Properties (development profile): `app.online-store.payments.simulated.enabled=true` and `app.online-store.payments.simulated.secret=...`. The test provider logs a warning if it is enabled outside the `dev` profile. **Never enable it on a live shop: no money moves.**
- `tools/online-store-smoke.mjs` now also runs the payment flow when given the webhook secret (`SIM_SECRET` or a 5th argument).
- 46 French texts on the server (e-mails and messages) and 68 on the screens.

## Tested

- Against PostgreSQL 16 with V1 to V67 and Hibernate's schema validation of every entity: signed payment settles the order; the same webhook again changes nothing; wrong, missing and tampered signatures are refused and not logged; wrong amount and wrong currency go to review; a decline then a retry with the other way; the status check finds a payment whose webhook was lost, is not asked too early or too often, and closes an expired payment; money that arrives after expiry or after cancellation is accepted (released, or refund due); provider refund and manual refund (reference needed), no second refund; a second payment on a paid order goes to review and cannot overwrite the first; unpaid orders are cancelled after a day but not while a payment is still pending; limits (5 tries, phone number, switched off); the reconciliation totals add up and move when an order is completed; placing orders on the shop (cash, mobile money, card, missing number, unknown way, per-phone limit counting unpaid orders, cash or online switched off, customer cancelling a paid or unpaid order, trying again); and French customers get the payment and refund e-mails in French. 88 checks, all passed.
- The screens pass the Angular compiler with strict templates, and the backend compiles.

## Not tested

- The **smoke script's payment part and the whole shop in a browser have not been run against the real application.** Please run `node tools/online-store-smoke.mjs http://localhost:9090 admin <password> <simulated secret>` against a development database and send me the output.
- No real provider yet: this release has the test provider only. Real mobile money or card needs the provider's sandbox account and keys, then one adapter class.
- A full Maven build, real e-mail through your SMTP server, the French wording.

## Known limits

- The money is recorded on the invoice when the order is **completed**, so in the books it only appears then (the Online payments screen shows it as "held" until then). A refund is possible until the order is completed; after that, use a sales return.
- A second payment on an already paid order, or a payment of the wrong amount, is **not refunded automatically**: it is held for review and a person refunds it.
- The provider is called inside the database transaction that places the order. If the provider is slow, the customer waits; if it fails, the order is not placed and the customer sees an error.
- The test provider keeps its state in memory: a pending test payment is lost when the server restarts.
- Orders and payments are not split by branch; one provider is used for the whole shop.
