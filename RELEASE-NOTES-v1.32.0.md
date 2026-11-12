# Release 1.32.0 - Online Store phase 3 (3 of 4): text messages (SMS / WhatsApp)

The shop can now **text** its customers and its owner. This version brings the whole mechanism and a **test sender** (nothing is really sent). A real SMS or WhatsApp provider is one more class: see `docs/online-store-text-providers.md`.

Texting is **off** until the owner chooses a provider in **Online Store > Shop settings > Text messages**. Until then nothing changes for anyone.

## What is texted

1. **Order updates** to the customer, the same messages that are e-mailed (received, accepted, ready, cancelled, refund, paid ...), in the language the customer chose, with the shop name in front. **Only to a customer who ticked "Send me updates about this order by text message (SMS)" at checkout. The box is off unless ticked.** It works even when the customer gave no e-mail address.
2. **Sign-in codes** for customer accounts. A customer can now type a **phone number** instead of an e-mail address: the code is texted to that number, and a number with no account opens one (an account with a phone number and no e-mail address). Guest orders placed with that number join the account.
3. **New-order alert** to the shop owner at the phone number in the settings, and the "orders are waiting" alert. Empty = nobody is texted.

## For staff

- **Shop settings > Text messages**: provider (or none), SMS or WhatsApp, country code (250), most texts per 24 hours (default 300), the owner's phone.
- **Online Store > Text messages** (new): every text sent, failed or skipped, newest first, with the reason; sign-in codes are shown without the code. A form sends a **test message** through the same limits and log.
- Switching texting on needs a provider that is available and carries the chosen channel.

## For customers

- The tick box appears on the order form only when the shop texts. Off by default; nothing is sent to anyone who did not tick it (except the sign-in codes they ask for).
- In **My account**, a phone number saved on an account can be **confirmed by text message**; once confirmed it signs in to that same account. A number that is typed but not confirmed does not open an e-mail account (see Safety).
- An account opened with a phone number cannot change that number there (it is how it signs in) and has no e-mail address.

## Safety

- **A phone number must be proved before it signs in.** Typing a number that someone saved, unconfirmed, on an e-mail account does not open that account: the code goes to the number and opens a separate phone account. So a wrong number typed by one customer can never let the owner of that number into the account.
- Only one account holds a confirmed number (database rule). Confirming a number that already belongs to another account is refused.
- Codes are texted only to the number asked for; same answer whether or not an account exists; 10 minutes, 5 tries, only hashes stored; 5 codes per number per hour, 12 requests per connection per hour; the text log never keeps the code.
- **Cost bound**: the daily limit counts texts that were actually sent in any 24 hours, for every purpose together; over it nothing is sent and the log says "Not sent".
- A provider that fails, times out or throws never stops an order, a status change or a sign-in screen: it is logged as Failed. The log row is kept even when the request that caused it is rolled back.
- Phone numbers are put in international form: 0788 123 456, +250 788 123 456 and 788123456 are one number; a bad number is skipped and logged.

## Technical

- Migration `V70__online_store_texts.sql`: `online_store_settings.text_provider/text_channel/text_country_code/text_daily_limit/notify_phone`; `online_order.text_opt_in`, `phone_key` (last 9 digits, filled for old orders); table `online_text_message`; `shop_customer.email` may be empty, `phone_key`, `phone_verified` (unique per confirmed number; an account needs an e-mail or a confirmed phone); `shop_login_code.email` renamed `identifier`.
- New: `text/TextProvider`, `text/SimulatedTextProvider` (property `app.online-store.text.simulated.enabled`, on in the dev profile), `OnlineTextService`, `OnlineTextMessage(+Repository)`, `SmsShopCodeChannel`; `ShopCodeChannel.sendConfirmation`. Order e-mails (`OnlineMessages`) now also text through the service. New endpoints: staff `GET /api/online-store/text-providers`, `GET /texts`, `POST /texts/test`; public `POST /api/public/store/account/phone/code` and `/phone/verify`. The shop answer gains `textChannel`; `PlaceRequest` gains `textUpdates`; the profile gains `phoneVerified`; the settings gain the five text fields.
- Old sign-in codes: with texting **off**, typing a phone number still sends the code to the e-mail of the one account that saved it (as in 1.30.0).
- `tools/online-store-smoke.mjs` has a text part (tick box, order updates, owner alert, test message, sign-in by phone). `docs/online-store-text-providers.md` explains how to add a provider.
- 15 new French texts on the server, 36 on the screens.

## Tested

- Against PostgreSQL 16 with V1 to V70 and Hibernate's schema validation, 63 new checks, all passed: phone numbers in international form (17 cases); provider off, unknown, not available, wrong channel; sending and the log (sent, refused, thrown, bad number, long text cut, sign-in code masked, log kept when the caller's transaction rolls back); the daily limit (4th refused, 24-hour window, failed texts not counted, 0 = unlimited); order updates only with the tick, in French, with no mail server; owner alerts; a failing provider never breaks the order messages; sign-in by phone (code texted, only a hash stored, wrong code counted, account opened with the number, guest orders joined, `0788...` `+250 788...` `788...` one account, 5 codes per hour, a failed text leaves no code but stays in the log); the e-mail account's saved number (unconfirmed number does not open it, confirmation code, wrong code, joins guest orders, afterwards signs in to it, a changed number needs a new proof, a number owned by another account is refused); a phone-only account cannot change or drop its number; texting off; the development code; clean-up of logs older than 90 days; the test message. The accounts (52) and coupons (58, +3 for the tick box) checks still pass.
- The screens pass the Angular compiler with strict templates; the backend compiles.

## Not tested

- **The screens in a browser, and the smoke script, have not been run against the real application.** Please run `node tools/online-store-smoke.mjs http://localhost:9090 admin <password> dev-simulated-secret 123456` after choosing "Test sender" in the shop settings, and send me the output.
- A real provider (none is included), delivery receipts, the French wording, a full Maven build.

## Known limits

- The test sender sends nothing. `SENT` means "handed to the provider".
- A text is sent while the customer's request waits: a slow provider slows that request (the provider must time out in a few seconds). Texts are not queued or retried.
- The daily limit is soft (two requests at once can both pass).
- No "STOP" handling: the tick belongs to one order.
- The order texts are the e-mail texts: some are longer than one SMS and are cut at 320 characters.
- WhatsApp needs approved templates; the interface passes the finished text (see the guide).
- An account opened with a phone number cannot add an e-mail address; guest orders join it by the last 9 digits of the confirmed number.
- Sign-in throttles are kept in memory (reset on restart).
