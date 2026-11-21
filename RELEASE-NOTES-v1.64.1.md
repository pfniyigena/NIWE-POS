# Release notes v1.64.1 - French check of the recent screens

The Online Store screens of v1.59 to v1.64 (driver page and cash, delivery times, return pick-ups, area estimate, new time after a failed visit, shop checkout, order page, Online orders, Returns, store setup, texts) were opened with French selected, on a phone (390 px) and a desktop (1280 px), and every text was read. Everything on those screens was already in French, except the points below.

## Fixed
- **Language picker**: it showed "English" while the page was French (the selected value was set before the list existed). It now shows the language in use.
- **Top bar on a phone**: with French the "Se déconnecter" button was pushed off the screen. The buttons now wrap to a second line when there is no room.
- **Store setup > Delivery areas**: the table ran wider than its card; it now scrolls inside it, like Delivery times and Drivers.
- **Messages with a status word**: "This order is completed, so that is not possible now" (and the same for returns) put the English status word inside a French sentence. The words (waiting for payment, just placed, accepted, packed, out for delivery, ready for pick-up, completed, cancelled, rejected, and the return ones) are now translated.
- **Missing French texts**: 53 texts the server writes (checkout errors such as "{0} is sold out", "Only {0} of {1} left", too many requests, shop not available, cancelled by the customer/staff, invoice and order labels, field-name errors) and 8 on older screens (advertising, newsletter, reviews) are added.
- **"Chauffeur" became "livreur"** in the 11 screen texts and 7 server texts of the return pick-up feature (v1.62.0), as everywhere else in the shop.

## Seen, not changed
Phone-number placeholders on the order page are cut on a 390 px phone ("Numéro de téléphone de cette comma..."); this is cosmetic and the field still works. Names, notes and page titles typed by staff are shown as they were typed.

## Technical
No migration. Version 1.64.1. Files: `OnlineOrderService`, `OnlineReturnService` (`statusWord`), `store-setup.component.ts`, `language-switcher.component.ts`, `styles.scss`, both `fr.json`.
