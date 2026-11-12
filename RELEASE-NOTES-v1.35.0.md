# Release 1.35.0 - Online Store: a full front page for the shop

The shop's first page now has the eleven usual sections of an online shop, from top to bottom:

1. **Announcement bar** - a short message above everything (optional link).
2. **Header** with the logo (shop name), the search box, the cart and the account/Staff Login links.
3. **Hero banner** with a title, a text, a picture and a clear button (default: "Shop now", down to the products).
4. **Popular categories** - the categories with the most products, each with a product picture.
5. **Featured products**, 6. **Best-selling products**, 7. **Special offers and discounts**, 8. **New arrivals** - four rows. **Staff choose** the products of each row with a tick box per product in Online Store > Shop settings (nothing is chosen automatically). A special offer shows the old price crossed out when the online price is below the normal price.
9. **Customer reviews** - real reviews only (see below), with the average rating.
10. **Newsletter** sign-up with e-mail confirmation.
11. **Footer** with quick links (shop, track your order, your own links), contact, opening hours, about text and social links.

A section with nothing to show (no featured product, no review...) is not shown at all.

## Wording and links - Shop settings > Front page
All texts are edited by staff in **Online Store > Shop settings > Front page**: announcement (+ link), banner title/text/picture/button, about text, contact e-mail, opening hours, footer links and social links (one per line, `Label|address`, at most 8 and 6). An address must be `/...` (a page of this site), `https://`, `http://`, `mailto:` or `tel:`; anything else (javascript:, data:, `//`) is refused. The banner picture must be an `https://` or `/` address.

## Reviews
- A customer with a **completed order** reviews the products of that order from the order page (rating 1-5 + optional words), proving it with the order's phone number or their account. One review per product per order; 20 tries per hour per address.
- A new review is **waiting**; staff **approve** or **hide** it (or delete it) in the new **Online Store > Reviews** screen. Only approved reviews appear on the shop, and only they count in the product and shop ratings. The shop owner is notified of a new review, and the waiting count shows in the menu pending counter.

## Newsletter
- Visitors subscribe at the bottom of the page and receive a **confirmation e-mail**; an address counts only after the link is opened (double opt-in). The person can unsubscribe from the page that opens when they confirm; the program sends no newsletters itself, so a mailing tool used with the exported list must add its own unsubscribe link (staff can also remove an address). Nobody can learn from the form whether an address is already registered.
- New screen **Online Store > Newsletter**: list with Subscribed / Not confirmed / Unsubscribed, remove an address, and **download the confirmed addresses as CSV**. Sending the newsletter itself is not part of this release.
- Protection: hidden trap field against robots, 6 sign-ups per hour per address, one confirmation e-mail every 10 minutes per address; confirmation tokens are stored hashed.
- The links in the e-mail use `app.frontend-url`: **set it to the public address of the site** (for example `https://shop.example.com`), or the links will point to the wrong place.

## Technical
- Migration **V72**: front-page columns on the shop settings, four tag columns on the online listings, tables `online_review` and `newsletter_subscriber`.
- Public API: `GET /api/public/store/home`, `POST /newsletter`, `/newsletter/confirm`, `/newsletter/unsubscribe`, `GET|POST /orders/{code}/reviews`. Staff API (VIEW/MANAGE as for the rest of the module): `/api/online-store/page-content`, `/reviews`, `/reviews/{id}/approve|hide`, `/newsletter`, `/newsletter/counts`, `/newsletter/export`.
- New frontend screens: `store-reviews`, `store-newsletter`, `shop-newsletter` (confirm/unsubscribe page, route `/shop/newsletter/:action/:token`). French texts added for screens and server messages.

## Checked
- Backend: 79 checks on a real PostgreSQL 16 database with all migrations through V72 (tags, home rows, categories, ratings, review rules and throttle, newsletter flow, links and CSV safety). Frontend: strict type-check passes.
- **Not done**: Maven build, a run in a browser. Please run `mvn test` and look at the shop page once before releasing.
