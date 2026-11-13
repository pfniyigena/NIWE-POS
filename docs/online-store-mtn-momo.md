# MTN Mobile Money (MoMo) for the Online Store

Provider code: `MTN_MOMO`. The customer enters their MTN number at checkout, gets a prompt on their phone, approves with their PIN, and the order is marked paid. Refunds go back through MTN.

**Status: written from MTN's published documentation and tested against a local imitation of MTN, never against the real MTN service**, because no MTN account existed when it was built. Do the sandbox steps below before anything else. The items marked **(check)** are the ones to confirm in your MTN portal; every one of them is a setting, so a difference needs no code change.

## How it works (so you can trust it)

1. At checkout the shop calls MTN "request to pay" with our order reference, the amount, the currency and the customer's number. The customer approves on the phone.
2. MTN calls our webhook once when the payment is done. **MTN does not sign its callbacks**, so the shop never believes a callback. It asks MTN itself for the payment status, and only acts when MTN's own answer agrees with the callback (state, amount, currency, reference). A forged callback changes nothing.
3. A callback is sent once and never retried. If it is lost, the shop's own status check (about once a minute per pending payment) finds the result. So the shop works even with no callback address at all, only a little slower.
4. A payment whose amount or currency differs from the order goes to **review** instead of being accepted, as with every provider.
5. Refunds: the shop asks MTN to refund the payment and waits a few seconds for MTN's answer. Only a refund MTN reports as successful is recorded as done. If MTN says no, or has not answered yet, staff are told, and refund by hand (**I refunded it myself**, with the reference). A second click on the same refund never gives the money twice (MTN is asked for the same refund again and says it already exists).

## Get a sandbox account and keys (about 20 minutes)

1. Create an account on the MTN MoMo developer portal for your country **(check: the Rwanda portal address)**. Sign in.
2. Subscribe to the **Collection** product. The portal gives you a **primary key** and a **secondary key**: either one is the *subscription key*.
3. Create a sandbox **API user** and its **API key**. The portal does not do this for you: it is two calls to the sandbox. Replace the three values in capitals; keep the generated id, you need it.

   ```
   # 1. Make up an id (any new UUID), then create the user. "callbackhost" is the host name of your shop, e.g. shop.example.rw (no https://)
   curl -X POST "https://SANDBOX-HOST/v1_0/apiuser" \
     -H "X-Reference-Id: NEW-UUID" \
     -H "Ocp-Apim-Subscription-Key: SUBSCRIPTION-KEY" \
     -H "Content-Type: application/json" \
     -d '{"providerCallbackHost": "callbackhost"}'

   # 2. Make its API key. The answer is {"apiKey": "..."}: copy it now, it is shown once.
   curl -X POST "https://SANDBOX-HOST/v1_0/apiuser/NEW-UUID/apikey" \
     -H "Ocp-Apim-Subscription-Key: SUBSCRIPTION-KEY"
   ```

   Then:
   - **subscription key** = the key from step 2 of this list
   - **API user** = NEW-UUID
   - **API key** = the `apiKey` from the answer

   In the sandbox MTN only calls back to the host you gave as `providerCallbackHost`; to test callbacks the shop must be reachable from the internet at that host (a tunnel such as ngrok works). Without callbacks the shop still finds the result by its status check.
4. **The sandbox only accepts the currency EUR.** You do not need to change anything: while `target-environment` is `sandbox`, the shop asks MTN for the payment in EUR whatever the business currency is (the amount stays the same number, and the currency MTN reports back is not compared). The order and the payment keep the shop's own currency. For the live service use the live target environment and the shop asks in the shop's currency (RWF).
5. Sandbox test numbers (they never move real money; **check** the portal's list for the current ones): `46733123450` always fails, `46733123451` is rejected, `46733123452` times out, `46733123453` stays pending, `46733123454` is slow; any other number succeeds. Type them at checkout in the international form (without +).

## Configure the shop

Set these properties on the server. Keys belong in the server's environment, **never in a file that is committed**. Each property can be given as an environment variable: replace dots and dashes with underscores and use capitals, e.g. `app.online-store.payments.mtn.api-key` is `APP_ONLINE_STORE_PAYMENTS_MTN_API_KEY`.

| Property | Meaning | Default |
| --- | --- | --- |
| `app.online-store.payments.mtn.enabled` | Switch the provider on | `false` |
| `app.online-store.payments.mtn.subscription-key` | Subscription key of the Collection product | none |
| `app.online-store.payments.mtn.api-user` | The API user id (UUID) | none |
| `app.online-store.payments.mtn.api-key` | The API key | none |
| `app.online-store.payments.mtn.base-url` | MTN's address, **https** only | `https://sandbox.momodeveloper.mtn.co.rw` **(check)** |
| `app.online-store.payments.mtn.target-environment` | `sandbox` for tests; the live value MTN gives you for your country for production **(check: for Rwanda it is expected to be `mtnrwanda`)** | `sandbox` |
| `app.online-store.payments.mtn.callback-url` | Where MTN calls back: `https://<your shop>/api/public/store/payments/MTN_MOMO/webhook`. Optional | none |
| `app.online-store.payments.mtn.country-code` | Added to numbers written without it (0788... becomes 250788...) | `250` |
| `app.online-store.payments.mtn.refund-path` | The refund call's path **(check)** | `/collection/v2_0/refund` |
| `app.online-store.payments.mtn.refund-wait-seconds` | How long to wait for MTN to confirm a refund (0 to 20) | `6` |
| `app.online-store.payments.mtn.timeout-seconds` | Timeout of each call to MTN (3 to 60) | `15` |

The provider is **not available** (it cannot be chosen) until it is switched on, the three keys are set and the address is https. At start-up the log says so, and warns when the sandbox is in use.

Then, in Online Store > Shop settings > Online payments, choose **MTN Mobile Money (MoMo)** and switch online payments on.

## Test it in the sandbox

1. Order from the public shop, pay by mobile money with a success number: the order becomes paid (within about a minute, at once with a callback).
2. Pay with the failing number: the customer sees the payment failed and can try again or pay another way.
3. Cancel a paid order or process a return: the refund goes through MTN; check the refund in the MTN portal.
4. Try a number that does not exist at MTN: the customer sees "That number has no MTN mobile money account".
5. Look at Online Store > Online payments: every payment, callback and its outcome is listed there.

## Going live

1. Ask MTN for production access for the Collection product; MTN's checks and your agreement are theirs to run.
2. Set the live base address, target environment and live keys; set the business currency to RWF; set `callback-url` to your real https address.
3. Make one small real purchase and one refund before opening the shop.
4. Keep the sandbox keys out of production and the production keys out of every file.

## When something does not work

| What you see | Likely cause |
| --- | --- |
| MTN MoMo is not in the list of providers, or cannot be chosen | Not switched on, a key is missing, or the base address is not https (the log says which) |
| Checkout says the payment could not be started | The keys are wrong, the subscription is not on the Collection product, or the target environment does not match the keys. The log shows MTN's answer (never the keys or the full phone number) |
| Orders stay waiting for payment after the customer paid | MTN could not be reached by the status check; look at the log; the payment is checked again every minute until it expires |
| A payment is "under review" | The amount MTN reports differs from the order (in the live service the currency too) |
| Refund says MTN has not confirmed | Look at the refund in the MTN portal; if it went through, record it by hand with the MTN reference shown, do not refund twice |
| Callbacks never arrive | Normal in the sandbox unless the callback host matches and the shop is reachable from the internet; the status check covers it |
