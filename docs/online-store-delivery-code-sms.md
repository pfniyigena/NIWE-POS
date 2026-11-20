# Delivery and pick-up codes by SMS and e-mail (v1.59.2)

## What is sent
When an order goes **out for delivery**, becomes **ready for pick-up**, or staff **re-issue the code**, the customer gets the message with the code:

- by **e-mail**, when the order has an e-mail address and the shop mail is set up;
- by **SMS**, to the phone number of the order, always (also when there is no e-mail, and whatever was ticked at checkout).

The order's history shows one line: `Code message: e-mail ..., SMS ...`.

## SMS for now
No SMS company is connected yet. The built-in sender (`BuiltInSmsSender.deliver`) therefore keeps the SMS in Online Store > Text messages as **Waiting for an SMS company**. To go live:

1. Preferred: add a `TextProvider` class for the company (see docs/online-store-text-providers.md) and choose it in the shop settings. Codes then go through it and show Sent or Failed.
2. Or send the text inside `BuiltInSmsSender.deliver` and return true when the company accepted it.

## Good to know
- The code is replaced by **** in the text log. Staff never see it.
- Customers with no phone number valid for texts: the log shows "Not sent - Not a phone number".
- The customer can always read the code on his own order page.
