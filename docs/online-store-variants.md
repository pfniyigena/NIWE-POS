# Online Store: product variants (v1.50.0)

## How it works
The ERP already makes variants: Products > a product > Variants. A **template** (inactive, with `variantAttributes` such as "Colour: Red, Blue | Size: S, M, L") and one product per combination (`variantParentId`, `variantLabel` "Red / M"), each with its own SKU, price and stock. The shop reuses them.

- **Template listing (the master row)**: put the template on the shop like any product. Its row holds what is shared: title, description, specifications, gallery, front-page tags (Featured, Best seller, Offer, New), order and the Shown switch (the whole product).
- **Option listings**: created and kept by `OnlineVariants.sync` (when the shop list is opened in Shop settings, after a save, and every 5 minutes by `OnlineVariantSyncJob`). Each option row copies the master's title, description, tags and order; it adds its own **online price**, its own **pictures** (images attached to the option's listing row) and a **Sold online** switch (`variant_off`). An option is on the shop when the master is shown, the option is not switched off and the option's product is active.
- **Customers**: one card per template (id = the template's id). The product page (`GET /api/public/store/products/{id}`, template or option id) returns `options` [{name, values}], `variants` [{id, label, values, price, wasPrice, stock, sku, pictures}] and `selectedVariantId` (the requested option, else the first in stock). Cart lines, orders, invoices, stock reservations and returns use the **option's own product id**, so nothing else changed.

## Rules
- Price on a card: the lowest price among the options on sale ("Starting at" when they differ), tax added on top when the tax rate is not included, as for any product.
- Stock word on a card: the best of the options. On the page: per option. A sold-out option is shown but cannot be added; the server also refuses it ("... is sold out").
- Ratings and reviews are counted per product as a whole (all options); a review keeps the option's name ("Classic Polo Shirt - Red / M").
- Search matches any option (name, SKU, barcode, colour...) and returns the product once; sorting by price uses the cheapest option; the rating filter and the sales ranking use the whole product.
- Wishlist and recently viewed keep the template's id.
- Removing an option from the shop list switches it off (it is kept, so it stays off when the product is switched on again); removing the template removes the options' rows too.
- Staff screens: Shop settings > Products on the shop: a product with options is one row; "Options: price, stock, pictures" shows one line per option.

## Limits
Three options and 200 combinations per product (set in Products > Variants). An option's own picture shows on the product page, not on the card. No back-in-stock alert yet.
