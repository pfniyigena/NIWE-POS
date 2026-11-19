# Online Store: ratings and reviews (v1.46.0)

Two different things, kept apart:

- **Rating** = the stars (1 to 5). Required. It counts in the averages **as soon as the customer gives it**.
- **Review** = the words (up to 1,000 characters). Optional. It is shown on the shop **only after staff approve it**.

## Who can rate
Only a customer who bought the product on a **completed** order, from the order page (order code plus the order's phone number, or signed in). One rating per product per order. Nothing changed here.

## What the customer sees
- Order page, "Rate your products": tap the stars, add words if wanted, **Send my rating**. Stars only: "Your rating is counted". Stars and words: the rating is counted, the words wait to be checked.
- Product cards, the product page and the front page show the **average, the number of ratings** and, on the product page and the front page, how many ratings gave 5, 4, 3, 2 and 1 star.
- The product page lists the approved **Reviews** (the words) under the rating summary. The front page shows the shop's overall rating with the count per star, then the newest approved reviews.

## What staff do (Online Store > Reviews)
- Only the **words** are moderated. Tabs: Waiting (words to check), Shown, Rating only (stars without words), Hidden, Everything. The number on "Waiting" and the e-mail/note "New review waiting" are only for entries that have words.
- **Approve** shows the words, **Hide** takes them off the shop (the stars keep counting).
- **Remove rating** takes the stars out of every average (for a rating that is not honest); **Count rating again** puts it back. The entry is kept and marked "Rating not counted".
- **Delete** removes the whole entry for good.

## Upgrade (migration V81)
- New column `online_review.rating_counted` (true by default).
- Entries that had no words and were still waiting become approved (nothing to check).
- Entries staff had hidden keep their words hidden and their stars **stay out of the averages**, as before; use "Count rating again" to bring any back.
- Entries still waiting with words stay waiting, but their stars now already count.

## Endpoints
Staff: `POST /api/online-store/reviews/{id}/rating-off` and `/rating-on` (new); `approve`, `hide`, delete as before. Public: the review calls are unchanged; the front page answer has `ratingStars` (five counts, 1 star first).
