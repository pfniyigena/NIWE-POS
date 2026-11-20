# Release notes v1.59.2 - Online Store: delivery and pick-up codes by SMS and e-mail

## What is new
- The message that carries the **delivery code** (order on its way), the **pick-up code** (ready for pick-up) and a **new code** (re-issued by staff) is now sent **both by e-mail and by SMS** to the phone number of the order.
- **Customers with no e-mail** get their code by SMS. The SMS does **not** depend on the box ticked at checkout: the code is part of the delivery, not news. (Other order updates still need the tick.)
- **SMS is a placeholder for now.** Until an SMS company is connected (or a provider is chosen in the shop settings), a built-in sender keeps every code SMS in **Online Store > Text messages** with the result **Waiting for an SMS company**. Nothing leaves the server yet. When a real provider is chosen in the shop settings, the codes go through it and show **Sent** or **Failed**.
- The **order's history** gets a line after each code message: "Code message: e-mail sent / none (no address) / failed, SMS sent / waiting for an SMS company / failed". Staff still never see the code itself.
- The code is **never written in the text log** (it shows ****), like the sign-in codes.
- Missing **French** texts added for the code messages (driver, delivery code, pick-up code, new code, partial delivery, failed visit, refund).

## Notes
- A phone number that is not valid is listed as "Not sent" with the reason.
- The daily SMS limit of the shop does not stop a code SMS when only the built-in sender is used.
- Staff can still re-issue a code; the customer can always read the code on his order page.

## Technical
Migration V94 (status QUEUED allowed in `online_text_message`). New `text/BuiltInSmsSender` (`deliver(to, body)` returns false until the real company is plugged in). `OnlineTextService.code(order, text, code)` (purpose CODE, falls back to the built-in sender when no provider is chosen). `OnlineMessages.sendCode` sends the e-mail and the SMS and returns the summary for the order history. Version 1.59.2.
