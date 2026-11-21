# Online Store and Inventory: cartons, several warehouses, reorder

## Cartons and single units (Packaging rules)
When Inventory > Packaging has a rule "1 carton = 12 units" and breakdown is switched on, the shop counts the units that can be had by opening cartons:

`sellable = units + (cartons that can be opened x units per carton) - expired - reserved - held - buffer - kept for the till`

- The card word, "Only n left", the In stock filter, checkout and back-in-stock alerts all use this one figure.
- **Accept** an order: if the units are not on the shelf, the needed cartons are opened automatically (note "Automatic: online order ...", and an event on the order: "n carton(s) broken down into single units for this order").
- **Deliver**: if stock moved since the acceptance, cartons are opened again at the delivery location before the stock check.
- Breakdown switched off in Inventory settings: cartons do not count.

## Several warehouses
Only the sellable locations count (as before). Cartons are opened at the location that has them, and the units land where the delivery takes them from (the shop floor, for example).

## Reorder and the stock report
- Reorder position = on hand - reserved - units of shop orders nobody has answered yet + open purchase orders - customers waiting for a back-in-stock e-mail.
- New columns on Reorder: "Online orders" and "Waiting customers".
- The stock valuation report (and its export) has a new "Online orders" column.

## Limit
If a carton and its single units are both sold online, the carton figure is reduced only by held carton orders, not by held single-unit orders. A race can then end in a clear refusal at delivery, never in negative stock.

## Technical
`PendingDemandProvider` (salesorder) is implemented by `OnlineDemand` (onlinestore); `StockReservationService.pendingOnlineByProduct/waitingByProduct`; `OnlineShopStock.breakable/breakableSql/breakDownFor`; `PackagingRuleRepository.findByChildProductIdAndActiveTrueOrderByIdAsc`; `ReorderSuggestionDto.onlineOrders/waitingCustomers`; `StockValuationRow.onlineOrders`. No migration.
