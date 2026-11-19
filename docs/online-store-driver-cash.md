# Online Store: driver cash (v1.57.0)

## What it follows
A delivery order completed with the payment taken **in cash** keeps the amount recorded as `cash to be handed in`. It appears in **Online Store > Driver cash** under the driver of the order (or "Not assigned") until staff settle it. Orders paid online, paid by mobile money, picked up at the counter, or completed with "payment not collected" never appear. Only orders completed after v1.57.0 appear.

## Settling
1. Open Driver cash, choose the driver (or All drivers).
2. For every order, the **Handed in** box starts at the cash collected. Change it if the driver brought a different amount, and write the **reason** (required when it differs).
3. Tick the orders (or **Select all**) and press **Settle the selected**. All selected orders are settled together; if one is refused (missing reason, already settled), nothing is saved.

A short or surplus amount is posted to the **Cash over / short** account (Dr Cash over / short and Cr Cash for a shortage; Dr Cash and Cr Cash over / short for a surplus). The order keeps the amount, the difference, the reason, who and when; its history and the audit log say so. A settlement cannot be undone from the screen: correct it with a journal entry.

## Permissions
Seeing the screen: Operations view. Settling: Operations manage (as for completing orders). Posting needs the accounting period to be open.

## Tax on the delivery fee
The cash recorded is the invoice total. If the delivery-fee product has a tax added on top, the invoice is higher than the total the customer paid and each settlement would show a difference. Set that product's tax to inclusive (or none).

## For the developer
`OnlineDriverCashService` (list, settle), `OnlineOrder.cashCollected` set in `OnlineOrderService.finish` (delivery order, method CASH), `AccountingService.postDriverCashVariance` (source type `DRIVER_CASH_VARIANCE`), screen `store-driver-cash.component.ts` (`/admin/online-store/driver-cash`).
