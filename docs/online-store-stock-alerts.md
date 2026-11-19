# Online Store: back-in-stock alerts (v1.54.0)

## What the customer sees

On a product page, when the product (or the chosen option, for example Red / M) is sold out, a box "Tell me when it is back" asks for an e-mail address and, if the customer wishes, a phone number. After "Tell me" the customer sees a thank-you. The shop then writes once, as soon as the product can be ordered again: an e-mail with a link to the product, and a text message when the shop sends texts.

## What the shop team does

1. Nothing for the automatic part. It needs **e-mail** set up (Settings > Email) and, for phone-only customers, **text messages** set up (Online Store > Shop settings > Text messages). The check runs every five minutes, so the message goes out a few minutes after the stock is back (received goods, a count, a cancelled order...).
2. Open **Online Store > Waiting customers**:
   - The top shows the products customers wait for, with how many people. Use it to decide what to restock. Click a product to see only its customers.
   - **WhatsApp** opens a chat with the message and the product link already written (the shop's country code is used for numbers starting with 0).
   - **Mark as told** closes a request you handled yourself. **Remove** deletes it.
   - **Tell them now** runs the notice at once.
3. Tabs: Waiting; Told (with when and by whom, "automatically" for the shop's own message); Could not be told (the e-mail or text failed three times: use WhatsApp or the phone); Everything.

A customer is told once per request. If they want to be told again they ask again on the product page.

## Good to know

- The request is for the exact item: for a product with options, the option (size, colour). A product that is not on the shop any more, or is switched off, is not announced.
- "Can be sold again" is the same figure the shop uses for its stock words: stock on hand minus what is reserved for other orders minus the stock buffer of the shop settings. A product whose stock is not tracked counts as available.
- A customer who gives only a phone number while text messages are off stays Waiting: you can write to them on WhatsApp.
- Limits: 10 requests an hour per connection; the e-mail address is checked; a hidden field stops robots.

## For the technical team

- Table `online_stock_alert` (product id, product name copy, e-mail, phone, language, status WAITING / NOTIFIED / FAILED, attempts, notified at / by, source IP).
- `OnlineStockAlertService.request` (public), `notifyAvailable` (the pass, run by `OnlineStockAlertJob` every 5 minutes after a 150 s delay, and by the "Tell them now" button), staff list, demand, told, delete. Text messages go through `OnlineTextService.send("STOCK", ...)` and so count against the daily limit and appear in the text-message log.
- Links in e-mails and texts use `app.frontend-url` (`/shop?product=ID`).
