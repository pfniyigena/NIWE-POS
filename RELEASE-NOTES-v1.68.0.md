# Release notes v1.68.0 - Online Store: cartons, warehouses, reorder

## Fixed
- **Cartons were invisible to the shop.** A product kept in cartons showed "sold out" online although cartons could be opened. The shop now counts the units that can be had by opening cartons (when breakdown is on in Inventory), in every figure it uses.
- **Accepting or delivering an order failed when only cartons were on the shelf.** Cartons are now opened automatically when the order is accepted, and again at delivery if stock moved.
- **Reorder ignored the shop.** Reorder now takes away the units of shop orders nobody has answered yet and counts customers waiting for a back-in-stock e-mail as demand.

## New
- Reorder: columns "Online orders" and "Waiting customers".
- Stock valuation report and its export: column "Online orders".
- Order history line: "n carton(s) broken down into single units for this order".

## Rules
Only sellable warehouse locations count. Breakdown switched off in Inventory: cartons do not count. When a carton and its units are both listed online, held single-unit orders do not reduce the carton figure (a clear refusal at delivery is possible, never negative stock).

## Technical
No migration. `PendingDemandProvider`, `OnlineDemand`, `OnlineShopStock.breakDownFor`, `ReorderSuggestionDto.onlineOrders/waitingCustomers`, `StockValuationRow.onlineOrders`. Version 1.68.0. See `docs/online-store-inventory-link.md`.
