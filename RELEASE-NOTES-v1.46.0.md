# Release 1.46.0 - Online Store: ratings and reviews are separate

- **Rating (stars) counts at once.** A customer who bought a product gives 1 to 5 stars from the order page; the average and the count on cards, the product page and the front page change immediately. No approval needed for stars.
- **Review (words) is optional and moderated.** Words are shown on the shop only after staff approve them. A rating without words has nothing to check, so it does not wait and does not notify staff.
- **Names:** "Rating" is the stars and "Review" is the words, on the shop, the order page and the staff screen. Cards, the product page and the front page say "ratings".
- **Front page** shows the shop's overall rating (average, number of ratings, count per star 5 to 1) as soon as there is one rating, with the written reviews under it.
- **Staff (Online Store > Reviews):** tabs Waiting, Shown, Rating only, Hidden, Everything; Approve and Hide act on the words only; new **Remove rating** and **Count rating again** to keep an unfair rating out of the averages; Delete as before. "Waiting" and the new-review note count only entries with words.
- Same rule as before: only customers who bought the product on a completed order can rate it, once per product per order.
- Migration V81: `online_review.rating_counted`; wordless waiting entries become approved; entries staff had hidden stay out of the averages (use "Count rating again" to bring one back). French texts. See `docs/online-store-ratings-reviews.md`.

Known limits: a customer cannot add words later to a rating already sent; "1 ratings" (English singular) is not special-cased.

Tested on PostgreSQL through the API (stars counted at once, words hidden until approved, approve, hide, remove and restore a rating, per-star counts on the home answer, waiting count only for words) and in a headless browser (front page summary, product page, order page rating with no words, staff tabs and buttons, no page errors). Not yet checked: French screens, the real Maven build, `ng build --configuration store`.
