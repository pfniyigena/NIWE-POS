# Release 1.27.1 - The public shop shows its products a page at a time

The public shop (`/shop`) used to download every product on the shop in one go and filter it in the browser. With a large catalogue that is slow on a phone. It now loads one page at a time.

## Changed

- **Pagination in the shop.** 16 products per page, with **Previous**, **Next**, "Page 2 of 9" and "Showing 17-32 of 210". Changing page scrolls back to the top of the products.
- **Search and categories are done by the server**, so they cover the whole catalogue, not only the page on screen. Typing waits a moment (0.3 s) before searching, and starting a search or choosing a category returns to page 1.
- **The cart is kept** while the customer moves between pages, searches and categories.
- Pictures are loaded for the products on the current page only.
- When there is no match the shop says "No products match."; when nothing is on the shop yet it keeps the earlier message.

## Technical

- New public endpoint `GET /api/public/store/products?page=0&size=16&q=&category=`. `page` starts at 0; `size` is at most 60 (default 16); `q` matches the product name or the shop title; `category` is a category name. It returns `{ items, page, size, total, totalPages }`.
- `GET /api/public/store/shop` no longer contains `products`; it now has `categories` (the categories that have products on the shop).
- The query is `OnlineListingRepository.findShopPage` (database paging, ordered by the shop order then id). Products without a category are included when no category is chosen.
- One new French text: "Showing {p0}-{p1} of {p2}". No migration, no setting to change.
- Anything outside this application that reads `/api/public/store/shop` for the product list must call the new endpoint instead.

## Tested

- The new queries were run through Hibernate and Spring Data against PostgreSQL 16 with the project schema and 100,000 listed products: totals for the whole shop, a category, a name search and a shop-title search matched direct SQL counts; unlisted and inactive products and products without a category behave as described; a page past the end returns no items. A page at 2,000 pages deep took about 0.13 s and a search about 0.06 s.
- The backend changes compile. The shop screen passed the Angular compiler with strict templates.

## Not tested

- The shop screen in a browser (paging, scrolling, cart across pages) and on a phone, the French wording, and a full build with Maven.
- Very deep pages on a very large catalogue (offset paging slows down on the last pages; keyset paging can follow if a shop ever needs it).
