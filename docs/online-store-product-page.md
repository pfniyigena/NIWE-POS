# The product page of the Online Store

Customers open a product from its picture or name in the shop. The page has its own address, `/shop?product=ID`, so it can be shared, bookmarked, and the browser's back button returns to the shop. The cart is not lost.

## What the customer sees
- A big picture and, when there are several, small pictures to choose from (the product's own picture first).
- The price, the normal price struck through when staff set a lower online price, and the stock word (In stock, Few left, Sold out). No stock number is ever shown.
- **Add to cart**, the full description, and a **Specifications** table when staff wrote one.
- The **star rating** (average and a count per star) with the approved reviews. When nobody reviewed the product yet it says so.
- **You may also like**: other products of the same category, then others.
- Ads set for the place "Above the product list and on product pages".

## What staff do
Open **Shop settings > Products on the shop**, and on a product press **Product page: pictures, description, specifications**.
- **Description on the shop**: your own words (up to 2000 characters). Empty means the product's own description is shown. Press the row's **Save** to keep it.
- **More pictures**: up to 5 (JPEG, PNG, WebP or GIF, 5 MB each). A big JPEG or PNG is shrunk to 1600 pixels wide. Use the arrows to change the order and the cross to remove one. The product's own picture (set in the product itself) is always shown first.
- **Specifications**: up to 20 rows, each a name (up to 60 characters) and a value (up to 200), for example *Size* and *5 kg*. Press **Save the specifications**. A row with only a name or only a value is refused; empty rows are ignored.
The product must be on the shop first (a row in the list).

## Ratings
Unchanged: a customer can rate a product only after buying it, from the order page once the order is completed, and staff approve the review (Online Store > Reviews) before it shows anywhere. The shop's cards show the stars, and the product page lists the approved reviews with a count per star.

## For developers
- Public: `GET /api/public/store/products/{id}` (404 when the product is not listed or not active) and `GET /api/public/store/product-images/{id}?w=` (gallery pictures).
- Staff: `GET /api/online-store/listings/{productId}/details`, `PUT .../specs`, `POST .../images` (multipart `file`), `DELETE /api/online-store/listing-images/{id}`, `POST /api/online-store/listing-images/{id}/move?direction=up|down`.
- Migration V79: table `online_listing_image` (pictures, cascade-deleted with the listing) and column `online_listing.specs` (JSON list of `{label, value}`).
