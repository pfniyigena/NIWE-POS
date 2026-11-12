# Adding an online payment provider

The Online Store takes payments through one interface, `com.posapp.onlinestore.payment.PaymentProvider`. A provider is a Spring component; adding one means writing that class and nothing else: no database change, no screen change. The shop settings list every provider and let the owner choose the one in use.

`SimulatedPaymentProvider` is the working example for a provider that signs its webhooks. Read it first. `MtnMomoPaymentProvider` is the real one (setup in `online-store-mtn-momo.md`), and shows what to do when the provider does **not** sign its callbacks: `verifySignature` asks the provider for the payment's status itself and accepts the callback only when the provider's own answer agrees.

## What a provider must do

| Method | What it does |
| --- | --- |
| `code()` | Upper case, at most 30 characters, e.g. `MTN_MOMO`. Stored in the shop settings and part of the webhook address. |
| `label()` | The name staff see. |
| `available()` | False until the keys are configured. Only an available provider can be chosen. |
| `methods()` | `MOBILE_MONEY` and/or `CARD`. |
| `initiate(request)` | Starts a payment for `request.reference()` (ours, unique, what the provider must send back), amount and currency. Returns the provider's reference and, for a card, the page to send the customer to (empty for a payment approved on the phone). Throw for a failure: the order is not placed and the customer sees an error. |
| `status(reference)` | Asks the provider what became of a payment. Called when the webhook did not arrive. Answer `PENDING` for an unknown reference; do not throw for that. |
| `refund(reference, amount, reason)` | Gives the money back. If the provider cannot, answer `ok = false` with a message and staff refund by hand and enter the reference. |
| `verifySignature(rawBody, headers)` | True only when the call really came from the provider. `rawBody` is exactly what was received; header names are lower case. Compare with a constant-time check (`MessageDigest.isEqual`). |
| `parseWebhook(rawBody)` | Turns the body into `reference`, `state` (`PAID`, `FAILED`, `PENDING`), `amount`, `currency`, the provider's reference and a failure reason. |

## Rules

1. **Never trust the amount or state without the signature.** The core already refuses a call whose signature fails (401) and a paid call whose amount or currency differs from what was asked (the payment goes to review), but only if `verifySignature` is right.
2. **Secrets come from properties or the environment**, read in the provider's constructor with `@Value`, e.g. `app.online-store.payments.mtn.api-key`. They are never stored in the database or shown on a screen. Put real keys in the environment of the server, not in a file that is committed.
3. `available()` must return false when a key is missing, so a half-configured provider cannot be switched on.
4. The core handles repeated webhooks, a webhook racing the status check, late payments, expiry, refunds and reconciliation. The provider only translates.
5. `initiate` and `refund` run inside a database transaction: set short timeouts on the provider's HTTP client.

## Steps

1. Get a sandbox account and keys from the provider and read how it signs webhooks.
2. Write `MyProvider implements PaymentProvider`, annotated `@Component`, next to `SimulatedPaymentProvider`.
3. Set its properties in the environment of a test server. Set the shop's currency (Business settings) to the currency you charge in (RWF for Rwanda).
4. In Online Store > Shop settings > Online payments choose the provider and switch online payments on. Give the provider the webhook address shown there: `<server>/api/public/store/payments/<CODE>/webhook`.
5. Run `tools/online-store-smoke.mjs` style checks against the sandbox: a paid payment, a declined one, the same webhook twice, a wrong signature, a refund.
6. Only then switch it on in production, with the live keys.

## Testing without a provider

Set `app.online-store.payments.simulated.enabled=true` and a `app.online-store.payments.simulated.secret` (the development profile already does). In the shop choose Online payments > Simulated provider. At checkout the customer lands on a test page with **Pay** and **Decline**; both go through the same signed webhook a real provider would call. `node tools/online-store-smoke.mjs <base> <user> <password> <secret>` runs the whole flow.
