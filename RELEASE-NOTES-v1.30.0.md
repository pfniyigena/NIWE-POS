# Release 1.30.0 - Online Store phase 3 (1 of 4): customer accounts

Customers of the public shop can now **sign in** to see their orders, save their details and **order again** in one tap. **Buying without an account still works exactly as before**: an account is optional and only adds convenience.

Switched off by default (Shop settings > Customer accounts). Needs e-mail to be set up, because the sign-in code is sent by e-mail.

## How it works for the customer

1. On the shop, **Sign in** (top right). Type an e-mail address and press **Send me a code**. A 6-digit code arrives by e-mail (in the customer's language) and works for 10 minutes.
2. Type the code. The account is made on the first sign-in; there is no password and no sign-up form.
3. **My account**: saved name, phone and delivery address (they pre-fill the next order), the list of past orders (each links to its tracking page), **Order again**, **Sign out**, **Delete my account**.
4. **Order again** puts the products of an old order in the cart at **today's price**. Products that are gone or sold out are not added; the shop says which. The customer lands on the order form.
5. Orders placed while signed in join the account. **Guest orders placed earlier with the same e-mail address join it too** when the address is proved with a code.
6. A phone number can be used to sign in once it is saved on the account (the code still goes to the account's e-mail).

## What keeps it safe

- No passwords. Only a **hash** of each code and of each session token is stored, never the value.
- A code lives 10 minutes, is single-use, and **dies after 5 wrong tries** (the failed tries are counted even though the answer is an error). Asking for a new code cancels the older one.
- Limits: 5 codes per address per hour, 12 code requests and 40 code checks per connection per hour.
- Asking for a code gives the **same answer whether or not an account exists**, so the form cannot be used to find out who has one. A phone number shared by two accounts sends nothing.
- A session lasts 30 days; sign-out and account deletion end it at once. Expired sessions and old codes are cleaned every hour.
- Every account call is scoped to the signed-in customer: another customer's order code gives 404.
- A missing, stale or expired token **never stops an order**: it simply becomes a guest order.
- Deleting the account removes the details and sessions. **The orders stay with the shop** (its books need them) but are no longer linked to the person.
- The staff app's login handling ignores these calls, so a customer's 401 can never log a staff user out.

## For staff

- **Shop settings > Customer accounts**: one switch. Nothing else to configure. Account creation and deletion are written to the audit log (no personal details in the text).

## Technical

- Migration `V68__online_store_accounts.sql`: `online_store_settings.accounts_enabled`; tables `shop_customer`, `shop_login_code`, `shop_session`; `online_order.shop_customer_id` (set null when the account is deleted) with an index on the customer and one on lower-cased `customer_email`.
- New: `ShopAccountService`, `ShopAccountController` (`/api/public/store/account`: `POST code`, `POST verify`, `GET/PUT me`, `GET orders`, `GET orders/{code}/reorder`, `POST logout`, `DELETE`), `ShopAccountJob`, `ShopCodeChannel` (interface) with `EmailShopCodeChannel`. **SMS or WhatsApp codes are one more class implementing `ShopCodeChannel`** (planned with the notifications feature).
- The session token travels in the `X-Shop-Token` header and is kept in the customer's browser storage.
- Development only: `app.online-store.accounts.dev-code=123456` (in `application-dev.properties`) makes every code that value so the shop can be tried without a mail server. The application logs a warning at start when it is set, and a stronger one outside the `dev` profile. **Never set it on a live shop: anyone could sign in to any account.**
- `tools/online-store-smoke.mjs` now also runs the account flow when given the dev code (`DEV_CODE` or a 6th argument).
- 10 new French texts on the server, 31 on the screens.

## Tested

- Against PostgreSQL 16 with V1 to V68 and Hibernate's schema validation of every entity, 52 checks, all passed: switched off gives 404 and ignores tokens; bad e-mail and phone refused; code stored only as a hash; unknown phone and unknown address give the same answer; no mail server gives a clear error; wrong code counted and dead after 5 (even the right code then fails); a new code kills the old one; the 5-per-hour limit; expired code; account made on first sign-in; guest orders with the same address in any letter case join, a stranger's and another account's do not; only the token hash is stored; second sign-in reuses the account; good, bad and missing tokens; profile saving and phone cleaning; sign-in by saved phone; a phone on two accounts sends nothing; history shows only my orders; reorder of someone else's order gives 404; reorder uses today's price, caps at 20, and names sold-out, unlisted and gone products; sign-out, expiry, purge; account deletion unlinks its orders and touches nobody else's; connection limits on codes and on guesses; the development code works with no mail server.
- The screens pass the Angular compiler with strict templates; the backend compiles.

## Not tested

- **The new screens in a browser, and the smoke script's account part, have not been run against the real application.** Please run `node tools/online-store-smoke.mjs http://localhost:9090 admin <password> dev-simulated-secret 123456` against a development database (switch Customer accounts on first) and send me the output.
- Real e-mail through your SMTP server (the code mail), the French wording, a full Maven build.
- **Placing an order while signed in** (the token read by the public controller and passed to `OnlineStoreService.place`) compiles but was not run in the cloud tests: the order-placing code needs the full product catalogue. The smoke script's account part exercises it.

## Known limits

- Anyone who can read the customer's mailbox can sign in as them: that is the nature of e-mail codes.
- A phone number alone cannot create an account; the first sign-in is always by e-mail.
- The throttle counters live in memory: they reset when the server restarts and are per server instance.
- The customer's saved language is stored but the shop screen language is still chosen on the page.
- No staff screen lists shop customers yet.
