# Adding a text message provider (SMS or WhatsApp)

The Online Store sends text messages through one interface, `com.posapp.onlinestore.text.TextProvider`. A provider is a Spring component; adding one means writing that class and nothing else: no database change, no screen change. Online Store > Shop settings > Text messages lists every provider and lets the owner choose the one in use.

`SimulatedTextProvider` is the working example (it sends nothing: it writes the text to the application log and the shop's text log). Read it first.

Everything that is the same for every provider is already done in `OnlineTextService`: phone numbers are put in international form, the daily limit, the log (**Online Store > Text messages**), a failure never breaking an order or a sign-in, and consent (see below). The provider only delivers.

## What a provider must do

| Method | What it does |
| --- | --- |
| `code()` | Upper case, at most 30 characters, e.g. `AFRICASTALKING`. Stored in the shop settings. |
| `label()` | The name staff see. |
| `available()` | False until the keys are configured. Only an available provider can be chosen. |
| `supports(channel)` | True for `SMS` and/or `WHATSAPP`. The owner chooses the channel in the settings; a provider that does not carry it cannot be chosen. |
| `send(channel, to, body)` | Delivers one message. `to` is in international form with a leading `+` (`+250788123456`). Answer `Result.sent(providerReference)` or `Result.failed("short reason in English")`. Do not throw for a message that was refused. |

## Rules

1. **Secrets come from properties or the environment**, read in the provider's constructor with `@Value`, e.g. `app.online-store.text.africastalking.api-key`. They are never stored in the database or shown on a screen. Put real keys in the environment of the server, not in a file that is committed.
2. `available()` must return false when a key is missing, so a half-configured provider cannot be switched on.
3. **`send` runs while a customer waits** (it is called from the request that places the order or asks for a sign-in code, inside its database transaction). Set short timeouts (a few seconds) on the provider's HTTP client, and answer `failed` on a timeout.
4. `SENT` in the log means "handed to the provider", not "delivered to the phone". Delivery receipts are not tracked in this version.
5. The text is already complete (shop name in front, in the customer's language, cut to 320 characters). Do not add to it, and do not log it: the core logs it, and the log shows a sign-in code as `******`.

## WhatsApp

WhatsApp Business does not let a business start a conversation with free text: it needs **message templates approved by WhatsApp**, and the customer must have agreed to be messaged. A WhatsApp provider therefore has to map each kind of text to an approved template. The core passes the finished text, so the simplest adapter sends it as the template's one variable; a stricter one can be added by extending `TextProvider` with the purpose (`ORDER`, `OWNER`, `SIGNIN`, `TEST`). Start with SMS, and add WhatsApp once the templates are approved.

## Consent and cost

- **Order updates** are texted only to a customer who ticked **"Send me updates about this order by text message"** at checkout (off unless ticked). The tick belongs to that order. There is no "STOP" reply handling in this version: a customer who no longer wants texts simply does not tick the box next time.
- **Sign-in codes** are texted to a number only when someone asks for a code for it. A number is limited to 5 codes an hour, a connection to 12 requests an hour.
- **The shop owner** is texted at the number in the settings (empty = nobody) about new orders and orders that wait too long.
- **The daily limit** (default 300 texts in any 24 hours, 0 = no limit) keeps the cost, and any misuse, in check. Over the limit a text is not sent and is logged as "Not sent". The limit is soft: two requests at the same moment can both pass.

## Steps

1. Get an account and keys from the provider; check how it wants the number written and what a sender name costs.
2. Write `MyProvider implements TextProvider`, annotated `@Component`, in package `com.posapp.onlinestore.text` next to `SimulatedTextProvider`.
3. Set its properties in the environment of a test server.
4. In Online Store > Shop settings > Text messages choose the provider, the channel, the country code (250 for Rwanda), the daily limit and the owner's phone. Save.
5. In Online Store > Text messages send a test message to your own phone.
6. Run `node tools/online-store-smoke.mjs <base> <user> <password> <secret> <dev-code>`: the text part ticks the box, places orders, checks the log and tries sign-in by phone.
7. Only then use it in production, with the live keys.

## Testing without a provider

Set `app.online-store.text.simulated.enabled=true` (the development profile already does). In the shop settings choose **Test sender**. Nothing leaves the server: every text appears in **Online Store > Text messages** and in the application log. A number ending in `0000` is refused, to try the failure path. With `app.online-store.accounts.dev-code=123456` set, every sign-in code is 123456, so sign-in by phone can be tried without reading the log.
