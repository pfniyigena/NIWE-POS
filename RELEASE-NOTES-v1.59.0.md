# Release notes v1.59.0 - Online Store: delivery drivers without a login (private link + PIN), and hand-over by code only

## What is new
A delivery driver does not need a staff login any more to confirm deliveries on his phone.

- In **Online Store > Shop settings > Drivers**, each driver now has **Give a link**. It makes a private link (for example `https://your-shop/driver/...`) and a 4-digit **PIN**. The PIN is shown once, at that moment (it is kept scrambled, nobody can read it later). Buttons send the link, and the PIN separately, to the driver on WhatsApp.
- The driver opens the link on his phone, types his PIN, and sees **only his own orders that are out for delivery**: customer, phone (tap to call), address and map, what to collect, the goods.
- For each order he taps **Hand over** and types the **customer's 4-digit delivery code**. That completes the order exactly as the staff screen does: stock out, invoice, cash recorded as collected (it then waits in Driver cash), customer thanked.
- The order history and "received by" show **Driver: his name**.
- Staff can at any time: **Show link** again, make a **New PIN**, or **Switch off** the link. Making a new link or PIN, or switching off, logs the driver out at once.

## Hand-over by code only; the customer signs only on his own page
- Drivers, the **My deliveries** screen and the staff **Online orders** screen no longer offer a signature: the order is handed over by typing the customer's delivery code.
- The customer can still confirm and sign on his own page (the order page / My account, "I received my order"). A signature given there is kept and printed on the delivery slip as before.
- A request without the code is refused ("Type the customer's delivery code."). Orders already completed by a signature on a phone keep their signature and can still be viewed.

## What the driver cannot do (on purpose)
- Record a **failed visit** or a **partial delivery**. Those stay with staff (as decided in v1.58.0). The driver page tells him to call the shop; the shop's phone is on the page.
- See any other driver's orders, the delivery code, or any staff screen.

## Safety
- The link carries a long random secret; the PIN is checked on top of it. **5 wrong PINs lock the link for 30 minutes** (a new PIN from staff unlocks it). Wrong delivery codes are counted like before (5, then 30 minutes).
- After the PIN the phone keeps a session for up to 14 hours of use, then asks for the PIN again. One session per driver: opening the link on a second phone signs out the first.
- A driver switched off in the list (Active unticked) cannot use his link.

## Also fixed
Two help texts in Shop settings (the Drivers card, and Send a newsletter) showed their raw template code instead of the sentence because of an apostrophe; they read normally now, and the Drivers text now has a French translation.

## To do after the update
- Set `app.frontend-url` to the public address of the app, so the link shown to staff is complete (otherwise only the end of the link, `/driver/...`, is shown).
- Online Store must be on, and the site must be served over HTTPS (drivers use phones).
- robots.txt now also asks search engines to stay out of `/driver/`.

## Technical
Migration V92 (`online_driver`: access_token, access_created_at, access_last_used_at, pin_hash (BCrypt), pin_failures, pin_failed_at, session_hash (SHA-256), session_expires_at; unique index on access_token). `OnlineDriverAccessService`, `OnlineDriverLinkController`: staff `POST/GET/DELETE /api/online-store/drivers/{id}/access`, `POST .../access/pin` (MANAGE); public `GET /api/public/driver/{token}`, `POST .../session`, `GET .../orders`, `POST .../orders/{id}/receive` (header `X-Driver-Session`; an expired session answers 403, never 401). `OnlineOrderService.receiveByDriver` shares `receiveAs` with the staff `receive`. New screen `/driver/:token` (`driver-link.component.ts`). Version 1.59.0.
