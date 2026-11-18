# Release 1.44.0 - Online Store: product page

Every product now has its own page, with more information and the customers' ratings.

- **Own address**: opening a product goes to `/shop?product=ID`. It can be shared or bookmarked, the back button works, and the cart is kept.
- **The page** shows: a big picture with a gallery, the price (and the normal price struck through for an offer), the stock word, add to cart, the full description, a specifications table, the star rating with a breakdown and the approved reviews, related products (same category first), and the "above the product list" ad.
- **Staff**: Shop settings > Products on the shop > *Product page: pictures, description, specifications* on each product: write the description, add up to 5 more pictures (5 MB each, reorder, remove) and write up to 20 specification rows (name and value). The product's own picture is always the first one.
- **Ratings** work as before: only a customer who bought the product can rate it, from the order page after the order is completed, and staff approve it before it shows. The product page lists the approved reviews and the cards of the shop show the stars.
- Cards in the shop (including the home page rows) open the page from the picture or the name.
- The advertising place "Above the product list" now also shows on product pages (the label says so).
- Migration V79 (table online_listing_image, column online_listing.specs). French texts.

Tested on PostgreSQL through the API (pictures: type check, limit of 5, order, delete, scaling; specifications: trimming, empty rows dropped, half-filled rows refused; the public page: ratings and breakdown, pending reviews hidden, related products, 404 for products that are not on the shop) and in a headless browser (open from a card, gallery, add to cart, related product, back button, shared link, search leaves the page, missing product message, phone width without sideways scrolling, the staff panel end to end). Not yet checked: French screens, the real Maven build, `ng build --configuration store`. The manuals are not updated yet.
