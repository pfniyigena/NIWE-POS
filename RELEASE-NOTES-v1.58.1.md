# Release notes v1.58.1 - Online Store: staff re-issue a delivery code

## What is new
- On Online orders, an order that is **out for delivery** or **ready for pick-up** has a button **Send a new code to the customer**. Use it when the customer lost the code, never received the message, or the code was blocked after five wrong tries.
- The new code replaces the old one (the old one stops working), the wrong-code counter starts again, and the customer receives the new code the same way as the first one (e-mail and/or text), and sees it on the order page / My orders.
- Staff never see the code. The history shows "A new code was sent to the customer" with the staff user, and the audit log records it.
- Only possible while the order is out for delivery or ready for pick-up. No database change.

## Correction to v1.58.0
The v1.58.0 notes and docs said staff cannot re-issue the delivery code. That was a misreading: staff can now, as above. A failed delivery still keeps the same code; the code only changes when staff press the new button.

## Technical
`POST /api/online-store/orders/{id}/reissue-code` (MANAGE), `OnlineOrderService.reissueCode`, `OnlineOrder.reissueReceiptCode()` (always a different 4-digit code). Version 1.58.1.
