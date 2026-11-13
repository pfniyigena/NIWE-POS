# Release 1.42.0 - Online Store: several payment providers, chosen at checkout

Until now the shop could use only one payment provider at a time. Now several can be on together and the customer picks.

- **Shop settings > Online payments**: the provider list is now check boxes (MTN MoMo, Airtel Money, and the simulated one for tests). Tick every provider you want to offer. Each ticked provider must be switched on and keyed on the server, otherwise saving is refused with a clear message. At least one must stay ticked while online payments are on.
- **Checkout and "pay again"**: when more than one provider can take the chosen way to pay, a "Pay with" row shows one button per provider, the first selected. With only one provider the row does not appear and nothing changes for the customer.
- **Logos**: each button shows the provider's logo if the file exists at `frontend/src/assets/payment/<code in lower case>.png` (`mtn_momo.png`, `airtel_money.png`); otherwise just the name. The MTN and Airtel logos are trademarks, so they are not included: put the official files there yourself (see the README in that folder) and rebuild.
- **Server**: the public shop now lists the usable providers (`payProviders`); placing an order and paying again accept an optional provider code. The server checks the code is one the shop offers and that it supports the chosen way to pay; a wrong or missing choice gets a clear message ("Choose which provider to pay with"). The status job, webhooks and refunds follow the provider each payment was started with, as before.
- Migration V77 widens the stored provider setting to hold a comma-separated list; an existing single value keeps working unchanged.
- French texts for the new messages.

Tested on PostgreSQL through the API with the MTN and Airtel imitations (settings rules, routing of each order to the chosen provider) and in a headless browser (two buttons at checkout with MTN selected by default, switching to Airtel reaches Airtel, the staff check boxes, a test logo shown next to a button). Not yet checked: the French screens, the phone layout, the production (store) build, and the look of the real logos. The manuals are not updated yet.
