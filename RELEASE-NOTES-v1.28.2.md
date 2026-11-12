# Release 1.28.2 - Online Store: phase 1 gaps closed

Four gaps left from phase 1 (see "Known limits" in `RELEASE-NOTES-v1.27.0.md`).

## New

- **Alert for unanswered orders.** A check runs every two minutes. An order that has been New (waiting for accept or reject) for longer than the **alert time** is reported to the shop by e-mail (to "Send new-order e-mails to", else the business e-mail). Each order is reported once, and orders that are late at the same time go in one e-mail. The alert time is a new field in Online Store > Shop settings: 30 minutes by default, **0 switches the alert off**. Nothing is sent while the module or the shop is off.
- **The Online orders screen shows the waiting time.** Each new order shows "Waiting 42 min"; orders past the alert time turn red and get a red edge, and the **New** tab shows how many are late. The screen already refreshes by itself every 10 seconds.
- **Customer e-mails in the customer's language.** The language the customer chose in the shop (EN or FR) is stored on the order, and every e-mail to that customer now uses it, whatever the business language is. The e-mails to the shop stay in the business language.
- **Shop photos are shrunk.** The product cards ask for a 420-pixel-wide copy. The server makes it once (JPEG, or PNG when the picture has transparency), keeps the last 400 copies in memory and lets browsers keep them for an hour. A picture that is already small, or that cannot be read, is sent as it is. The full picture is still available without `?w=`.
- **`tools/online-store-smoke.mjs`: a whole order from the shop to the invoice, against a running backend.** Run `node tools/online-store-smoke.mjs http://localhost:9090 admin <password>` against a development database. It checks the catalogue (pages, search, category, photo), refusals (no name, no products, the bot field), a pick-up order through accept, pack, ready, complete and the invoice, a delivery order, a customer cancelling, and a staff rejection with its reason. Set `SMOKE_EMAIL` to also receive the customer's e-mails. It creates real orders, invoices and payments.

## Technical

- Migration `V66__online_store_alerts.sql`: `online_store_settings.unanswered_alert_minutes` (default 30) and `online_order.alerted_at`.
- `OnlineOrderAlertJob` (scheduled), `OnlineMessages.alertShop`, `I18n.tIn(language, text, values)`, `OnlineStoreService.image(id, width)`.
- `GET /api/online-store/orders/pending-count` now returns `pending`, `overdue` and `alertMinutes`. `GET /api/public/store/products/{id}/image` takes an optional `w`.
- Three French texts on the screens and three on the server.

## Tested

- Against PostgreSQL 16 with V1 to V66: the alert finds only the new order older than the alert time, reports it once in one e-mail, reports nothing the second time, and is off with 0 or when the shop is off. French and English customers get French and English subjects and texts. A 3000 x 2000 picture of 740 KB became a 420 x 280 picture of 21 KB; small, unreadable and transparent pictures behave as described.
- The smoke script's own logic was run against a stand-in server written from the controllers. It has **not** yet been run against the real application.
- The screens pass the Angular compiler with strict templates, and the backend compiles.

## Not tested

- Running `online-store-smoke.mjs` against the real application is the first real end-to-end check of phase 1. Please run it and send me the output.
- Real e-mail through your SMTP server, the French wording, the screens in a browser, and a full Maven build.
