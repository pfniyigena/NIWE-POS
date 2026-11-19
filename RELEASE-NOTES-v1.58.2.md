# Release notes v1.58.2 - Online Store: order number in accounting descriptions

## What is new
Every accounting entry that comes from an online order now ends its description with the order number, in brackets:

- Delivery of the goods: `Fulfilled sales order SO-... (online order 9FMMY2MC)`
- Payment (cash on delivery, or the money paid online): `Payment received from Ada (online order 9FMMY2MC)`
- Goods returned by the customer: `Sales order return ... (online order 9FMMY2MC)`
- A paid invoice cancelled: `Cancelled paid invoice INV-... (online order 9FMMY2MC)`
- Driver cash settled with a difference: `Driver cash, order 9FMMY2MC (driver): collected ..., handed in ...` (already carried the order number)

Anything that does not come from an online order keeps its usual description.

## Notes
- New postings only. Entries posted before this version are not changed.
- No database change, no new screen, nothing to set up.
- The number is the code the customer sees (for example 9FMMY2MC), so searching the journal for it finds all the entries of that order.

## Technical
`OnlineOrderRefs` (accounting package) looks up `online_order.code` by the sale order (or invoice) of the posting with plain SQL; a failed lookup never stops a posting. Used by `AccountingService` for SALES_ORDER_FULFILLMENT, CUSTOMER_PAYMENT, SALES_ORDER_RETURN and INVOICE_CANCELLED_PAID. Version 1.58.2.
