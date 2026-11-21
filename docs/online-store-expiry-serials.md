# Online Store: expiry dates and serial numbers (v1.67.0)

## Expiry
Products tracked **by batch** (Inventory) carry an expiry date per batch. The shop's sellable figure now is: units in the sellable locations - **expired or too close to expiry** - reserved - held by unanswered shop orders - buffer - share kept for the till. A batch is held back when its expiry date is before *today + "Do not sell goods with fewer days left than"* (Shop settings; 0 = only batches already expired). The stock stays in Inventory (the till can still sell or write it off); the shop simply does not offer it. Units that were never given a batch are never held back.

Delivering a shop order takes batches in this order: the ones the shop may sell, earliest expiry first; then those too close to their expiry; expired last.

## Serial numbers
Serial-numbered goods are taken oldest first when the order is delivered (the sale order records them as sold under its number). They then appear: on the customer's order page (Serial numbers), on staff's order, and on the invoice (under the line, "Serial numbers: A, B").

**Returns**: on the Returns screen, a serial-numbered line shows the serial numbers delivered. Tick the ones that came back (as many as the quantity returned); when every delivered unit comes back nothing needs ticking. Those units go back **in stock** at the return location with their own serial numbers; before this release the quantity increased but the serials stayed sold. A serial that was not delivered on that order is refused.

## Technical
Migration V100. `OnlineShopStock.expired`, `.expiryCut`; the `SELLABLE` search query subtracts the batches before `:expiryCut`. `TrackingContext.setMinExpiry` is set by `OnlineOrderService.finish` around the fulfilment. `TrackingService.soldSerials/soldSerialsByProduct/restoreSerials`; `SalesOrderReturnService` restores serials; `InvoiceService.buildFromSalesOrder` writes them into the line description.

## Not changed yet
Reorder and available figures in Inventory that ignore the shop, automatic carton breakdown (next release), ERP promotions and price lists, decimal quantities.
