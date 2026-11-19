# Online Store: product rows

All rows of product cards in the shop use one component, `frontend/src/app/online-store/shop-rail.component.ts` (`<app-shop-rail>`): Featured, Best sellers, New arrivals, Special offers (front page), Recently viewed (front page and product page) and You may also like (product page).

## Behaviour
- One line, no wrapping. Each child of the row gets the same width (210 px on the front page, 180 px on the product page, set with the CSS variable `--rail-item`), so cards keep a consistent size; the cards of one row have the same height.
- Arrow buttons show only when the products do not all fit; the left arrow is dimmed at the start and the right arrow at the end.
- Native sideways scrolling: horizontal wheel, trackpad swipe, touch swipe. `scroll-behavior: smooth`, light snapping, hidden scrollbar.
- Automatic sliding: input `auto`, fed by the shop setting `autoSlideRows` (Shop settings > Front page, default off). Every 4.5 seconds, one screen; stops while the visitor hovers, touches or focuses the row and while the tab is hidden; wraps to the first card after the last; disabled for `prefers-reduced-motion`.

## Setting
`online_store_settings.auto_slide_rows` (migration V83), sent to the shop as `page.autoSlideRows` and saved through `PUT /api/online-store/page-content` (`autoSlideRows`). The banner slideshow keeps its own behaviour.
