# Release notes v1.65.0 - Online Store: the stock the shop sells

## Fixed
- **Multi-warehouse on: the driver's code and the customer's "I received my order" completed nothing** (error). The delivery is now taken from the shop's Sales location automatically.
- **The shop offered stock no sale can use** (Main warehouse, Receiving, Storage). It now counts only Sales locations outside the Main warehouse.
- **Completion errors** ("not enough stock", ...) showed as "Transaction silently rolled back". Staff now get the real reason; drivers and customers a plain "please call the shop" (the reason is in the order history).

## New
- **Shop settings > Stock the shop sells from** (multi-warehouse on): one Sales location, or Automatic (all of them).

## Rules
With multi-warehouse off nothing changes. A partial delivery is checked for the goods kept; a refused one changes nothing.

## Technical
Migration V98. `OnlineShopStock`, `SalesOrderService.checkFulfilment`. Version 1.65.0. See `docs/online-store-shop-stock.md`.
