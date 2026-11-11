# Release 1.28.0 - Online Store e-mails

## New

- **The shop is told when an order is placed.** A new field **Send new-order e-mails to** in Online Store > Shop settings. When it is empty, the e-mail goes to the business e-mail from Settings. The e-mail lists the customer (name, phone, e-mail), delivery or pick-up with the area and address, the items, the delivery fee, the total and the customer's note, and tells you to open Online Store > Online orders. If neither address is set, no e-mail is sent.
- **The customer is told at every step** (only when they gave an e-mail address on the order form, which is optional): order received, accepted, rejected (with the reason), packed, on its way, ready for pick-up, completed (with the invoice number), cancelled by the shop, and cancelled by the customer. *Packed* and *cancelled by the customer* sent nothing before.
- French texts for all of these e-mails (the earlier ones had no French).

## Things to know

- Nothing is sent until e-mail sending (SMTP) is set up and switched on in Settings. A failed or missing e-mail never stops an order or a step: it is only written to the log.
- The texts follow the business language set in Settings, not the language the customer chose in the shop.
- A customer who leaves the e-mail field empty gets no e-mails; the order page with the code still shows every step.

## Technical

- Migration `V65__online_store_notify_email.sql` adds `online_store_settings.notify_email`. The settings API gains `notifyEmail`, checked as an e-mail address.
- `OnlineMessages.notifyShop` builds the shop e-mail. New server French texts in `resources/i18n/fr.json`; one new screen text.

## Tested

- The e-mail code was run with a stand-in mail service: the shop e-mail goes to the set address, falls back to the business e-mail when it is blank, is skipped when there is no address or mail is not set up, and the customer is skipped when there is no address. Customer names and notes are escaped in the HTML.
- V65 ran on PostgreSQL 16 on top of the project schema. The backend changes compile and the two screens pass the Angular compiler with strict templates.

## Not tested

- A real e-mail through your SMTP server, how the e-mail looks in a mail program, the French wording, and a full Maven build.
