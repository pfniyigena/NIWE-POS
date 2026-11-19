# Release 1.47.0 - Online Store: invoice download, address book, wishlist, WhatsApp

- **Downloadable invoice**: a completed order shows "Download invoice (PDF)" on its order page. The phone number of the order (or the signed-in account) is needed.
- **Address book**: signed-in customers keep up to 10 delivery addresses (name, area, address, default) on the account page and pick one at checkout; a new address can be saved while ordering.
- **Wishlist**: a heart on product cards and on the product page, a "My wishlist" page from the shop header. Kept in the browser for guests, in the account for signed-in customers (guest hearts join the account at sign-in).
- **WhatsApp**: new setting "WhatsApp number of the shop"; the order page offers "Send this order to the shop on WhatsApp"; staff get a WhatsApp link beside the customer's phone on Online orders. E-mail and SMS/WhatsApp provider messages are unchanged.
- **Link to the order in every e-mail**: customer e-mails end with a "View your order" link to the order page (in the customer's language); the shop's own e-mails and alerts link to Online orders in the admin.
- **Counts on all Online orders tabs**: besides New (with the late count), the tabs Open, Completed, Rejected, Cancelled and All now show their numbers.
- **Recently viewed as one sliding row**: on the front page and on the product page the recently viewed products are now a single row that slides by itself (stops while the visitor points at it; arrows left and right), instead of several rows.
- Migration V82. French texts. See `docs/online-store-account-extras.md`.

Known limits: the WhatsApp links open the customer's or the staff member's own WhatsApp (no automatic sending; for that, use the text provider); the invoice PDF is in the business language; the wishlist page shows at most 60 products; product variants and bank card payments are still left for later.

Tested on PostgreSQL through the API (invoice with wrong, right and missing phone; addresses add, edit, default switch, delete default, validation; wishlist add, duplicate, unknown product, remove, merge, guest product list) and in a headless browser (hearts, count, reload, wishlist page, merge at sign-in, account addresses, checkout prefill and saving an address while ordering, PDF download, WhatsApp links, staff link and setting). Not yet checked: French screens, the real Maven build, `ng build --configuration store`.
