# Release 1.30.1 - Currency on the public shop, paging of the shop's products

## Changes

- **Public shop: every price and amount now shows the currency** (for example `1,500 RWF`): product cards, the cart bar, the order form (items, delivery, total, delivery-area fees, minimum order), the order tracking page (lines, totals, paid and refunded amounts) and the account's order list. The currency is the business currency set in the business settings (RWF if none is set), the same one used for online payments. The shop answer (`GET /api/public/store/shop`) gains `currency`.
- **Online Store > Shop settings > Products on the shop is paged**: 10, 25, 50 or 100 per page, Previous / Next, "Showing 1-10 of 83", and a filter box (product name, shop title, SKU or category). What you type in a row stays there while you move to another page; Save still saves one row.

## Technical

- New `shop-money.pipe.ts` (`shopMoney`), used by the shop, the tracking page and the account page. No database change.
- The products table is paged on the screen: the server still sends the whole list of the shop's products in one call.
- 3 new French texts on the screens.

## Tested

- Backend compiles; the 52 account checks against PostgreSQL still pass; the screens pass the Angular compiler with strict templates.

## Not tested

- **Not seen in a browser.** Please check the shop (prices with the currency, in English and French), the tracking page, and the paging of the products table with more than 10 products.
