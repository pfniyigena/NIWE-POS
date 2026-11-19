# Release notes v1.58.0 - Online Store: failed and partial deliveries

## What is new
- **Delivery failed** (Online Store > Online orders, on an order that is out for delivery): staff pick a reason (customer not at home, customer refused the goods, wrong or unreachable address, no answer on the phone, other) and may add details. The order goes back to **Packed** with the reason and the number of tries. The customer is told by e-mail/text. The stock stays reserved, the driver stays on the order; staff send it out again later (or reassign it) or cancel it. From the third failed try the screen suggests cancelling.
- **The customer takes only part of the goods**: tick the box on an order that is out for delivery (or ready for pick-up), type the quantity kept per line, then Complete (or confirm with the customer's code / signature). The invoice, the stock and the cash to collect cover only what the customer kept. What was not delivered is no longer reserved. The delivery fee stays on the invoice.
- **Paid online and delivered in part**: the part that was paid but not delivered shows as **Refund due for the goods not delivered** (tab Refunds due). Staff refund it through the provider or record a refund they made themselves (with its reference). The customer is told.
- Loyalty: points are earned on what was delivered; points spent give back in proportion to the discount that belonged to the goods not delivered.
- The order page and My orders show "delivered 2" on a line that was delivered in part, and the history keeps the reason or the quantities.

## Rules
- Only staff record a failed or partial delivery (screen Online orders). The driver's phone screen is unchanged.
- Staff cannot re-issue the delivery code. A failed visit does not change the code or reset the wrong-code counter; sending the order out again repeats the same code to the customer.
- Nothing delivered at all is refused: use "Delivery failed" (or cancel the order).

## Technical
- Migration **V91**: `online_order` (delivery_attempts, last_failure_reason, last_failure_at, delivered_total, refund_difference), `online_order_line` (delivered_quantity).
- New: `POST /api/online-store/orders/{id}/delivery-failed` {reason}, `POST /api/online-store/orders/{id}/refund-difference` {manual, reference}; `complete` and `receive` accept `kept` [{productId, quantity}]. `SalesOrderService.reduceQuantities` lowers the ordered quantities of a confirmed sale order.
- Version 1.58.0.
