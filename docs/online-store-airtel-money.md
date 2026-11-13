# Airtel Money for the Online Store

Provider code: `AIRTEL_MONEY` (shown to staff as "Airtel Money"). The customer enters an Airtel Money number at checkout, gets a prompt on their phone ("USSD push"), approves with their PIN, and the order is marked paid. It works next to MTN MoMo: choose one provider in Online Store > Shop settings > Online payments.

**Status: written from public integrations of Airtel's Open API (Collection product) and tested only against a local imitation of Airtel, never against the real Airtel service.** Airtel's developer portal could not be read automatically while this was built, so the items marked **(check)** must be confirmed in your Airtel developer account. Every one of them is a setting, so a difference needs no code change. Do the test steps below before anything else.

## How it works

1. At checkout the shop calls Airtel's USSD push with our order reference (`transaction.id`), the amount, the currency and the customer's number (without the country code: 0788 123 456 becomes 788123456). The customer approves on the phone.
2. Airtel calls back once. **The shop never believes a callback.** It asks Airtel itself for the status of that reference and only acts when Airtel's own answer is final (successful or failed) and says the same. A forged callback changes nothing (answered 401).
3. If the callback is lost or never registered, the shop's own status check (about once a minute per pending payment) finds the result. The shop works with no callback at all, only a little slower.
4. Airtel's status answer carries no amount, so the amount is not compared (the shop asked for it and Airtel took exactly that). The status, the reference and Airtel's own id (`airtel_money_id`, shown as the payment's provider reference) are used.
5. **Refunds are done by hand.** Airtel's refund call gives back a whole payment and the shop cannot tell it that only part of an order is returned, so the shop does not send refunds to Airtel. When an order has to be refunded, the screen tells staff to refund the customer from the Airtel Money account and record it with "I refunded it myself" and the Airtel reference it shows.

## Get an account and keys

1. Create an account on the Airtel Africa developer portal (developers.airtel.africa) and create an application. **(check)** Make sure the **Collection** product and **Rwanda** are enabled for it.
2. The portal gives you a **client id** and a **client secret** (the portal's test environment is called UAT: base address `https://openapiuat.airtel.africa`; production is `https://openapi.airtel.africa`) **(check)**.
3. Register the **callback address** for the application in the portal (the shop does not send it with each request): `https://<your shop>/api/public/store/payments/AIRTEL_MONEY/webhook`. Optional: without it the status check still finds every payment. If the portal offers "callback authentication", you may switch it on, the shop does not depend on it.
4. Test numbers and what they do in UAT: **(check)** the portal's list; the shop has no built-in list.

## Configure the shop

Keys belong in the server's environment or in the git-ignored files in `backend/config/`, **never in a file that is committed**. An environment variable is the property with dots and dashes turned into underscores, in capitals, e.g. `APP_ONLINE_STORE_PAYMENTS_AIRTEL_CLIENT_SECRET`.

| Property (`app.online-store.payments.airtel.`) | Meaning | Default |
| --- | --- | --- |
| `enabled` | Switch the provider on | `false` |
| `client-id` | Client id of your Airtel application | none |
| `client-secret` | Client secret | none |
| `base-url` | Airtel's address, **https** only | `https://openapiuat.airtel.africa` **(check)** |
| `country` | Airtel country code sent in `X-Country` and the request | `RW` |
| `currency` | Currency sent; empty = the shop's currency (RWF). Set it only if Airtel's test system needs another one, e.g. UGX with country UG | empty |
| `country-code` | Removed from numbers written with it (250788... and +250 788... become 788...) | `250` |
| `push-path` | The USSD push call's path **(check)** | `/merchant/v1/payments/` |
| `status-path` | The status call's path (our reference is added) **(check)** | `/standard/v1/payments/` |
| `timeout-seconds` | Timeout of each call to Airtel (3 to 60) | `15` |

The provider is **not available** (it cannot be chosen) until it is switched on, both keys are set and the address is https. At start-up the log says so, and warns when the test (UAT) system is in use. The v2 push call of Airtel (encrypted payload) is not supported.

With the profile groups in `application.properties`, put the test values in `backend/config/application-airtel-sandbox.properties` (brought by `dev`) and the live ones in `application-airtel-production.properties` (brought by `store`), as for MTN.

## Test it

1. Order from the public shop, pay by mobile money with a number that succeeds in UAT: the order becomes paid (within about a minute, at once with a callback).
2. Pay with a number that fails: the customer sees the payment failed and can try again or pay another way.
3. Pay and leave it unapproved: the order stays waiting; after the shop's payment expiry it is cancelled.
4. Cancel a paid order: the screen says to refund by hand; record it with the reference.
5. Online Store > Online payments lists every payment, callback and outcome.

## Going live

1. Ask Airtel for production access (KYC documents are theirs to run).
2. Set the live base address and live keys, and register the live callback address.
3. Make one small real purchase before opening the shop. Keep test keys out of production and live keys out of every committed file.

## When something does not work

| What you see | Likely cause |
| --- | --- |
| Airtel Money is not in the list of providers, or cannot be chosen | Not switched on, a key is missing, or the base address is not https (the log says which) |
| Checkout says the payment could not be started | Wrong keys, Rwanda or Collection not enabled for the application, or a different push path. The log shows Airtel's answer code (never the keys or the full phone number) |
| "That number has no Airtel Money account" | Airtel answered DP00800001006 (invalid number) |
| Orders stay waiting after the customer paid | Airtel could not be reached by the status check; look at the log; the payment is checked again every minute until it expires |
| Callbacks answered 401 in the log | Normal for forged or unfinished ones; a real callback is accepted only when Airtel's own status says the same |

## Offering several providers (v1.42.0)

You can offer Airtel Money and another provider together: in Online Store > Shop settings > Online payments, tick every provider you want. The customer picks one at checkout. Each button shows a logo if you place the official PNG in `frontend/src/assets/payment/` (`airtel_money.png`), then rebuild the frontend; without it the button shows the name only.
