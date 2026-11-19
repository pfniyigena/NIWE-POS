# Shop experience: cards, search, filters, product page extras

## For customers
- **Product cards** show the picture, a two-line name, the price (with the old price struck through and a percentage badge when staff set an online price below the normal one), stars, and stock. **Quick view** opens a window with the essentials and Add to cart.
- **Search** (top bar): type any words of the name, SKU, brand or category; every word must match. Suggestions appear after two letters. Pick a product to open it, or a category or brand to filter.
- **Filters** above the list: sort, price from/to, brand, rating, in stock only. **Clear filters** resets them.
- **Recently viewed** products are remembered in the visitor's browser only and shown on the front page and the product pages.
- **Product page**: hover to zoom, click for the full-screen picture, choose a quantity, see delivery information and the estimated delivery dates, share the page on WhatsApp, and add the products often bought together.
- **Shop by category** tiles on the front page open that category.

## For staff
Shop settings > Front page has two new settings:
- **Shop colour (buttons and links)**: a colour like `#0f766e`; "Standard" goes back to the normal colour.
- **Estimated delivery, in days**: for example 2 to 4. Leave empty to show no estimate. The dates shown are counted from today.
Everything else is automatic: category tile pictures come from the products' own pictures, "most popular" and "bought together" come from the online orders.

## Notes
- "In stock only" uses the same rule as the stock word on the card: sellable stock minus what confirmed orders promised minus the shop's buffer.
- Price filters and sorting use the price the customer sees (tax added when it is on top).
- Not built yet: wishlist, product variants (size, colour), bank card payments.

## For developers
- `GET /api/public/store/products` takes `q`, `category`, `brand`, `minPrice`, `maxPrice`, `inStock`, `minRating`, `sort` (`price_asc`, `price_desc`, `newest`, `rating`, `popular`), `page`, `size`; built in `OnlineShopSearch` (one JPQL query with a count query).
- `GET /api/public/store/suggest?q=`, `GET /api/public/store/recent?ids=1,2,3`; the product detail has `bought` (up to 3); the shop JSON has `brands` and `categoryTiles`, and `page` has `brandColor`, `deliveryDaysMin`, `deliveryDaysMax`.
- Migration V80: `online_store_settings.brand_color`, `delivery_days_min`, `delivery_days_max`.
