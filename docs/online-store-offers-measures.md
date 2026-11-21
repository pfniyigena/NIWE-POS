# Online Store: prices from Sales, and products sold by weight or length

## Prices from the price list and the promotions (Sales)
A product that staff gave **no online price** is sold at:

1. the normal selling price, moved to the price of the **price list** chosen in Shop settings ("Price list used by the shop"): the entry of the product for 1 unit, or else the list's own percent adjustment;
2. then the **best promotion for everybody**: a promotion on the product or on its category that is switched on, inside its dates, and has no coupon code, no customer group, no limit on uses, no minimum quantity and is "percent off" or "amount off".

The result is kept on the listing, so the product card, the cart, the order, the search by price and the sort by price all use the same figure. The normal price is shown struck through when the offer is lower.

- A product with an **online price** keeps it; offers do not apply to it.
- Promotions must be switched on in Sales > Settings.
- Not applied online: buy-X-get-Y, "from N units", coupon codes, customer-group promotions, promotions with a limit, whole-cart promotions, price list steps for more than one unit.
- The prices are worked out again every 2 minutes, and at once when staff save a listing or the settings, or press **Apply now**. A change made in Sales shows on the shop within two minutes.
- The order line keeps the price at the time of the order; the sale order and the invoice use it.
- Shop settings > product row: "Offer from Sales: price (name)".

## Sold by weight or length
In Shop settings, on a product row, fill **Sold by** with the unit ("kg", "m"). The customer may then order quantities from 0.1 to 100 with up to 3 decimals (cart: tap + or − for half a unit, or type the amount). The card and product page show the price "/ kg".

- The stock check, the held stock, the sale order, the delivery and the stock movement all use the decimal quantity.
- Such a product counts as one item in the cart badge and in the "too many items" limit.
- Empty the field to sell by the piece again. Not for products with options (sizes, colours).
- Returns already accept decimals; abandoned-cart reminders keep them.

## Technical
Migration V101: `online_store_settings.price_list_id`, `online_listing.offer_price/offer_note/measure_unit`, `online_cart_line.quantity` numeric(12,3). `OnlineOffers` (refresh every 2 minutes, `refreshOne`), `POST /api/online-store/offers/refresh`, `GET /api/online-store/price-lists`, `PublicProduct.measureUnit`, `ListingDto.offerPrice/offerNote/measureUnit`, `SettingsDto.priceListId/priceListName` (0 = none).
