# Release notes v1.69.0 - Online Store: prices from Sales, products sold by weight or length

## Fixed
- **The shop ignored the ERP's promotions and price lists.** A product with no online price was always sold at its normal price. It now follows the shop's price list (Shop settings) and the best promotion for everybody on the product or its category; the normal price is struck through.
- **No decimals.** Rice, cloth or cheese could only be sold by whole pieces. A product can now be sold by weight or length.

## New
- Shop settings: **Price list used by the shop**, and **Apply now** (prices are otherwise worked out every 2 minutes).
- Product row: **Sold by (kg, m ...)**, and "Offer from Sales: price (name)".
- Cart: decimal quantities for those products (+/- half a unit, or type the amount); "/ kg" next to the price.

## Rules
Offers are only for products with no online price. Promotions with a coupon, a customer group, a limit, a minimum, "buy X get Y" or for the whole cart are not applied online. Quantities by weight or length: 0.1 to 100, 3 decimals.

## Technical
Migration V101. `OnlineOffers`, `OnlineListing.offerPrice/offerNote/measureUnit`, `OnlineStoreSettings.priceListId`, `PublicProduct.measureUnit`, `online_cart_line.quantity` numeric(12,3). Version 1.69.0. See `docs/online-store-offers-measures.md`.
