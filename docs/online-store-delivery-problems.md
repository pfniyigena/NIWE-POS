# Online Store: failed and partial deliveries (v1.58.0)

## Failed delivery
Online Store > Online orders > an order **Out for delivery** > **Delivery failed**. Choose the reason, add details if useful, then **Back to packed, try again later**.

- The order returns to **Packed**; the history shows "Delivery failed (attempt n): reason". The card shows a warning with the number of tries and the last reason; from the third one it suggests cancelling.
- The customer receives a message with the reason and is told you will arrange a new delivery.
- The stock stays reserved and the driver stays assigned. Press **Out for delivery** again when you are ready (you can change the driver first), or **Cancel order** (the stock is released and, if the customer paid online, a refund becomes due).
- Nothing is invoiced and no cash is recorded for a failed visit.

## Partial delivery
On an order that is out for delivery (or ready for pick-up), tick **The customer takes only part of the goods** and type the quantity kept on each line (0 to the quantity ordered). Then press Complete, or confirm with the customer's code or signature.

- Invoice, stock out and cash collected cover only the goods kept. The sale order is lowered to the same quantities, so the rest is released.
- The delivery fee stays on the invoice (the trip was made).
- If nothing at all is kept, the order cannot be completed this way: use Delivery failed or cancel it.
- The order line shows "delivered n", the card shows "Invoiced for the goods delivered", the customer's order page shows the same.
- A delivery paid in cash puts the invoice total in Driver cash, as usual.

### Paid online
The customer paid the whole order online, so the difference between what was paid and the invoice is to be given back. It appears on the order as **Refund due for the goods not delivered** and in the tab **Refunds due**. Use **Refund through the provider**, or refund by hand and type the reference, then **I refunded it myself**. The customer is told.

Note: when the delivery-fee product has tax added on top, the invoice can be higher than what the customer saw, and the difference to refund can be smaller than expected or nil. Set that product's tax to inclusive or none.

### Loyalty points
Points are earned on the goods delivered. Points the customer spent come back in proportion to the discount that belonged to the goods not delivered.

## The delivery code
Staff cannot re-issue or reset the code. A failed visit keeps the same code and the same wrong-code counter; the next "Out for delivery" message repeats that same code to the customer.

## Who can do it
Anyone who can manage the Online Store (staff). The driver's phone screen (My deliveries) does not record failed or partial deliveries.
