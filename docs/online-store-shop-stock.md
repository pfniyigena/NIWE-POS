# Online Store: the stock the shop sells (v1.65.0)

## The problem this solves
With **multi-warehouse** on (Inventory settings), a sale can only leave a **Sales** location of a warehouse **other than the Main one**. The shop did not know that:
- it counted the stock of every active location (Main warehouse, Receiving, Storage, every branch), so it could sell goods that no sale can take;
- the driver's code hand-over and the customer's "I received my order" chose no location, so the completion was refused (a driver got "Transaction silently rolled back");
- any refusal while completing an order (not enough stock at the location, say) reached the screen as that 500 error instead of the reason.

## What it does now
- **Shop settings > Products on the shop > Stock the shop sells from** (shown only with multi-warehouse on). Choose one Sales location, or leave **Automatic** = every Sales location outside the Main warehouse, together. The list offers only those locations. Saved when you change it.
- With multi-warehouse on, the shop's figures (card word In stock / Few left / Sold out, "Only n left", the *In stock only* filter, back-in-stock alerts, the checkout check) use only that stock, minus what confirmed sale orders still owe, minus the units kept back for the till. With it off, nothing changes (the total of every location, as before).
- A delivery or pick-up is taken from: the location staff chose on Online orders; otherwise the shop's location; with several, the one holding the most of what the order needs. This is what the driver's code and the customer's tap now use. No Sales location at all: the shop shows everything sold out and a completion says what to set up.
- The stock check is made **before** anything is changed (new `SalesOrderService.checkFulfilment`). Staff get the plain reason ("Not enough stock for X - 2 available, 5 requested"). A driver or customer gets "We could not record this delivery just now. Please call the shop." and the order's history gets "The hand-over could not be recorded: ..." for staff. A partial delivery is checked for the part kept and leaves the sale order untouched when refused.

## Technical
Migration V98 `online_store_settings.shop_location_id` (no foreign key; an unusable value falls back to Automatic). `OnlineShopStock` (eligible and chosen locations, `onHand`, `available`, `locationFor`, `searchCondition` for the search query); `GET/PUT /api/online-store/shop-stock` (VIEW / MANAGE). `OnlineStoreService.sellable/stockInfo` and `OnlineShopSearch` use it; `OnlineOrderService.finish` resolves the location, checks, then completes. Reservations are still the outstanding lines of confirmed sale orders (all locations).

## Not changed (see the gap list in the project)
Stock is still not held for an order until staff accept it; expiry and serial numbers are ignored; one global buffer.
