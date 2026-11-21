# Online Store: stock held from checkout, buffers and the till share (v1.66.0)

## The problem this solves
The shop checked stock when an order was placed, but a sale order (which reserves stock) was only created when staff **accepted** the order. Between the two, any number of customers could be sold the same last units, and an order left unanswered changed nothing. The safety buffer was also one number for the whole shop.

## What it does now
- **Held stock.** An order in *Awaiting payment* or *Placed* holds its quantities. The figure the shop shows is: units in the sellable locations - reserved by confirmed sale orders - held by unanswered shop orders - buffer - share kept for the till. The card word, "Only n left", the *In stock only* filter, the checkout check and the back-in-stock alerts all use it.
- **Accepting** an order creates the sale order, which then reserves the units; the hold ends at that moment, so nothing is counted twice. Rejecting, cancelling and the automatic release free the units.
- **Two customers at once.** While an order is checked and saved the products in it are locked (always in the same order), so the second customer sees what the first one took and gets "Only n left".
- **Automatic release.** Shop settings > *Release unanswered orders after (hours)*, default 24, 0 = never. Every 10 minutes the shop cancels: an order still *Awaiting payment*, and a *cash* order still *Placed*. The order's history says "Cancelled automatically: ..." and the customer is told. An order **paid online** and still waiting for staff is never cancelled by it.
- **Buffer per product.** In the list of products (and for each option of a product with options) *Buffer for this product*; empty = the shop-wide buffer.
- **Share kept for the till.** Shop settings > *Share kept for the till (%)*: this percentage of the available units of each product (rounded up) is kept back in addition to the buffer. Example: 10 available, buffer 1, 50 %: 1 + 5 = 6 kept, 4 sellable.

## Technical
Migration V99. `OnlineShopStock.held/available/bufferOf/tillPercent/tillShare/lockProducts`; the `SELLABLE` query of `OnlineShopSearch` subtracts the held lines and uses `coalesce(l.stockBuffer, :buffer)` and `ceiling(available * :till / 100)`; `OnlineOrderService.autoRelease` and `OnlineOrderReleaseJob`. `StockInfo` gains `held` and `tillShare`; `ListingRequest.stockBuffer` (null = unchanged, negative = back to the shop-wide buffer).

## Not changed yet (see the gap list in the project)
Expiry and serial numbers (next release), reorder and available figures in Inventory that ignore the shop, automatic carton breakdown, ERP promotions and price lists, decimal quantities.
