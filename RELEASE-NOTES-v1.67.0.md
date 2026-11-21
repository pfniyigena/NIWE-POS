# Release notes v1.67.0 - Online Store: expiry dates and serial numbers

## Fixed
- **The shop sold expired goods.** Batch-tracked products showed all their stock as sellable, expired batches included. Expired batches (and, with the new setting, batches too close to their expiry date) are now left out of every figure the shop uses: card word, "Only n left", the In stock filter, checkout, back-in-stock alerts. Stock that was never given a batch is never held back.
- **A delivery could take a near-expiry batch the shop does not sell.** Shop deliveries now take the batches the shop may sell first (earliest expiry first), then the others, expired last.
- **Serial numbers were invisible and returns lost them.** The serial numbers delivered are now on the customer's order page, on staff's order and printed on the invoice. A return of serial-numbered goods puts the chosen serial numbers back in stock (before, the stock count went up but the serials stayed "sold").

## New
- **Shop settings > Do not sell goods with fewer days left than** (default 0 = only expired batches).
- **Returns screen**: for serial-numbered lines, tick the serial numbers that came back. If all the delivered units come back nothing needs choosing.
- The stock line of each product now also shows what is expired or too close to expiry.

## Rules
Serial numbers are taken oldest first when delivering (nothing to choose). A serial can only be returned if it was delivered on that order. Several lines of one product on one sale order repeat the same serial list on the invoice.

## Technical
Migration V100 (`online_store_settings.min_days_left`). `OnlineShopStock.expired/expiryCut`, `TrackingContext.minExpiry` used by `TrackingService.orderedBatches`, `TrackingService.soldSerials/soldSerialsByProduct/restoreSerials`, `ProcessSalesOrderReturnRequest.ReturnLineEntry.serialNumbers`, `GET /api/online-store/returns/{id}/serials`, `ReturnProcess.serials`, invoice line description. Version 1.67.0. See `docs/online-store-expiry-serials.md`.
