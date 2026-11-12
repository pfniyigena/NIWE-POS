# Release 1.34.0 - Online Store: MTN Mobile Money (MoMo) payment provider

The Online Store can now take **real mobile money payments through MTN MoMo**: the customer enters their MTN number at checkout and approves the payment on their phone; refunds go back through MTN. It is a new payment provider (`MTN_MOMO`) on the payment core of 1.29.0; no screen and no database change.

**Not yet tried against the real MTN service.** It was written from MTN's published documentation and tested against a local imitation of MTN, because no MTN account existed yet. Follow `docs/online-store-mtn-momo.md`: it lists how to get a sandbox account and keys, the settings, and the few values to confirm in your MTN portal (all settings, none needs code).

## What it does

- **Pay**: "request to pay" to the customer's phone; numbers are accepted as `0788 123 456`, `+250 788 123 456` or `788123456`. A number with no MTN account gives the customer a clear message and nothing is placed.
- **Result**: by MTN's callback, and by the shop's own status check about once a minute, so a lost callback costs time, not money.
- **Refund**: through MTN, for whole and partial (returns) refunds. Only a refund MTN confirms is recorded as done. If MTN refuses or is slow, staff are told and refund by hand with the reference as before. Asking for the same refund twice (second click, retry) never gives the money twice.
- **Settings only in properties or the environment** (`app.online-store.payments.mtn.*`): subscription key, API user, API key, address, environment, callback address. Never in the database, never on a screen, never in the log. Not available until switched on and complete; https only (plain http only for a server on the same machine).

## Safety

- **MTN does not sign callbacks.** So no callback is believed on its own: the shop asks MTN for the payment's status and acts only when MTN's answer agrees with the callback on reference, state, amount and currency. A forged callback changes nothing.
- At most 60 callbacks a minute are checked (each costs a call to MTN); real payments are still found by the status check.
- The customer's phone number is shortened in the log; keys and tokens are never logged.
- An amount or currency that differs from the order still sends the payment to review, as for every provider.

## Other changes

- The webhook address accepts **PUT** as well as POST (MTN calls back with PUT).
- New texts in English and French for the messages above.
- `docs/online-store-mtn-momo.md` (new): sandbox account and keys step by step, the property list, testing, going live, troubleshooting.

## Tests

- New: 72 checks of the adapter against a local imitation of MTN: availability rules, number and amount formats, the MTN id, request to pay (headers, body, duplicate, no account, MTN error), token reuse and renewal, status for pending / paid / failed / unknown, callbacks (genuine, forged, wrong amount or currency, unknown payment, bad shape, flood), refunds (confirmed, refused, slow, repeated, not paid, MTN down), bad keys, MTN unreachable.
- Not tried: the real MTN sandbox or live service, a browser run, the Maven build. Do the sandbox steps first.

## Not included

- Card payments, other mobile money brands (Airtel Money), recurring or saved payments.
- Payouts and MTN's other products (disbursement, remittance).
