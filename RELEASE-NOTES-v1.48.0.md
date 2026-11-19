# Release 1.48.0 - Online Store: product rows that slide

One look and one behaviour for every product row of the shop: **Featured**, **Best sellers**, **New arrivals**, **Special offers**, **You may also like** (product page) and **Recently viewed**.

- Products stay on **one horizontal line**; every card in a row has the same width and height.
- **Arrow buttons** (left and right) move the row by about one screen; the arrow at an end is dimmed, and arrows only show when there are more products than fit.
- **Horizontal mouse wheel or trackpad swipe**, and a **finger swipe on a phone**, move the row; the movement is smooth and the scrollbar is hidden.
- **Automatic sliding is off by default.** Staff can switch it on in Online Store > Shop settings > Front page ("Product rows slide by themselves"). When on, rows move one screen every few seconds, stop while the visitor points at or touches them, return to the first product after the last, and are never used on devices set to reduced motion.
- Before this version the four front-page rows moved by themselves with no way to turn that off; the banner slideshow is unchanged.
- **Fix, changing an ad**: saving a changed ad could answer "Choose the advertiser.". A changed ad now keeps its advertiser when the choice is not sent (screen and server), and the advertiser list matches by number. A new ad still needs an advertiser.
- Migration V83 (`online_store_settings.auto_slide_rows`, default off). French texts. See `docs/online-store-product-rows.md`.

Known limits: a plain vertical mouse wheel scrolls the page, not the row (use the arrows, Shift + wheel, a horizontal wheel or a trackpad); the "Frequently bought together" box on the product page is a bundle, not a row, and is unchanged.

Tested on PostgreSQL and in a headless browser: same card sizes in every row, no sliding by itself when off, sliding and wrap-around when on, arrow dimming at the end, horizontal wheel, touch-sized screen, saving the setting through the API. Not checked: French screens, the real Maven build, `ng build --configuration store`, a real phone.
