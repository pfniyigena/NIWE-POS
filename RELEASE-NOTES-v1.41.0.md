# Release 1.41.0 - Online Store: Airtel Money payment provider

A second real payment provider next to MTN MoMo: **Airtel Money** (Rwanda, code `AIRTEL_MONEY`). The customer enters an Airtel Money number at checkout, approves the prompt on the phone, and the order is marked paid. Choose it in Online Store > Shop settings > Online payments. It is not available until it is switched on and keyed (`app.online-store.payments.airtel.*`, see `docs/online-store-airtel-money.md`).

- **Callbacks are never trusted**: the shop asks Airtel for the payment's status by our reference and accepts a callback only when Airtel's own final answer agrees; a forged one is answered 401 and changes nothing. A lost callback is covered by the status check (about once a minute).
- **Refunds are done by hand** (Airtel's refund gives back a whole payment and cannot do a part): the screen tells staff to refund from the Airtel Money account and record it with "I refunded it myself" and the Airtel reference.
- Numbers are sent without the country code (0788..., +250 788... and 250788... all become 788...). Airtel's status answer has no amount, so the amount is not compared.
- The checkout's "Pay from my phone number above" box works for both providers; French texts for the new messages.
- Settings are properties or environment variables only (never the database, never logged). Profile groups: `dev` brings `airtel-sandbox`, `store` brings `airtel-production` (git-ignored files in `backend/config/`).
- No migration.

Tested on PostgreSQL against a local imitation of Airtel (token, push, status, callbacks): a paid order by callback and by the status job, a failed one, a pending one with a forged success callback (401, stays pending), an invalid number and a too-short number (clear messages), odd references (401), a cancelled paid order (refund-by-hand message with the Airtel reference). **Written from public integrations of Airtel's Open API because the developer portal could not be read: not yet tried against the real Airtel UAT or live service.** Items to confirm in the Airtel portal: base addresses, the push and status paths, that Collection and Rwanda are enabled, the UAT test numbers, and whether the callback needs authentication. Not run in a browser; the manuals are not updated yet.
