# Release notes v1.59.1 - Online Store: a tax for each delivery area's fee

## What is new
In **Online Store > Shop settings > Delivery areas**, each area now has a **Tax** column. Choose one of your existing taxes (the same taxes you pick on a product), or leave "The delivery product's tax".

- The **fee you type is the amount the customer pays**. The tax you choose is the part of that amount that is tax, **included in the fee**. Example: fee 2,000 with VAT 18% means 1,694.92 of fee plus 305.08 of tax, and the customer still pays 2,000.
- The sale order, the invoice and the cash collected therefore **always equal the total the customer saw**. Before, a delivery-fee product whose tax is added on top made the invoice and the cash higher than the order total.
- The tax is copied onto the order when it is placed, so changing an area later does not change an order already placed.
- With no tax chosen, nothing changes: the tax of the delivery product is used, as before.

## Notes
- Only active taxes can be chosen. An area that already has a tax keeps it even if that tax is later switched off. If a chosen tax is deleted, the delivery product's own tax is used.
- Orders placed before this version keep using the delivery product's tax.
- The list of taxes in this screen is available to everyone who can open the shop settings (the full tax list stays for administrators).
- You can now leave the delivery product itself without tax, or with any tax: the area's tax wins when one is chosen.

## Technical
Migration V93 (`online_delivery_zone.tax_rate_id`, `online_order.delivery_tax_rate_id`). `GET /api/online-store/tax-options`. `SalesOrderRequest.LineEntry` gets optional `taxRateId` and `taxIncluded`; `SalesOrderService.applyRequest` uses them instead of the product's tax when set (a missing tax falls back to the product's). Used by `OnlineOrderService.accept` for the fee line. Version 1.59.1.
