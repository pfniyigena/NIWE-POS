# Release 1.27.0 - Online Store (phase 1): a public shop

A new optional module, **Online Store**. Customers open `/shop`, choose products, and order for delivery or pick-up. Staff accept the order, and it becomes a normal sale order, invoice and cash payment, so stock, accounting and reports need no change. This is phase 1 of 3: payment is cash on delivery or at pick-up. Mobile money and card (phase 2), and accounts, coupons, loyalty and returns (phase 3) follow.

## New

- **Public shop** at `/shop` (English and French): product grid with search and categories, cart, checkout (name, phone, optional e-mail, delivery address or pick-up), and an order page at `/shop/order/<code>` where the customer follows the order by its code and may cancel it while it is still new. Stock is shown as In stock, Few left or Sold out, never as a number.
- **Online Store > Online orders** (staff): new orders wait for an answer. **Accept** creates a confirmed sale order (this holds the stock; if the stock does not cover it you are asked, and can accept anyway). **Reject** needs a reason the customer sees. Then **Mark packed**, **Out for delivery** or **Ready for pick-up**, and **Complete**: the goods leave from the sale order, the invoice is issued and the cash is recorded. **Cancel order** releases the stock. Every change is written to the order's history and, when the customer gave an e-mail address, e-mailed (in the business language).
- **Online Store > Shop settings**: open or pause the shop, delivery and pick-up switches, minimum order, free delivery from an amount, units kept back for the till (default 1), limits per phone and per connection, the **products on the shop** (only products you add are shown or sold; the picture is the product's own; optional online price and title), and **delivery areas** with a fee and a minimum order each. A **service product for the delivery fee** must be chosen before an order with a delivery fee can be accepted.
- The module is licensed like the others (key `ONLINE_STORE`) and starts switched off. The shop answers "not available" until the module is activated and **Shop is open to the public** is ticked.

## Safety of the public side

- The public API is separate (`/api/public/store/**`, no login) from the staff API (`/api/online-store/**`). It never returns costs, suppliers or other customers.
- Prices, stock and totals are worked out on the server when the order is sent; the browser's figures are not trusted.
- Limits: orders waiting per phone number, requests per connection per hour, a hidden field that bots fill in, at most 40 lines and 20 of each product per order.
- Behind a reverse proxy set `server.forward-headers-strategy=native` so the per-connection limit sees the visitor's real address. Without it every visitor looks like the proxy.

## Technical

- Migration `V64__online_store.sql`: `online_store_settings`, `online_listing`, `online_delivery_zone`, `online_order`, `online_order_line`, `online_order_event`.
- Backend package `com.posapp.onlinestore`. `ModuleKey.ONLINE_STORE`; licence gate rule for `/api/online-store`; `/api/public/modules` now also reports the shop.
- Frontend `online-store/` (shop, order page, staff orders, shop settings), `core/api/online-store-api.service.ts`, a menu group, and about 80 French texts.
- The shop is an Angular route in the same application (like the restaurant's `/order` page), not a separate static site. A separate static storefront on a CDN can follow when needed.

## Known limits in phase 1

- Cash only. No customer accounts, coupons, loyalty points or automatic returns: use Sales orders > Returns for a return.
- Prices are shown with tax included. If the delivery-fee service product carries tax added on top, the invoice total is higher than the total the customer saw: use a tax-free or tax-inclusive product for it.
- Photos are the product's own picture, served as it is (not resized).
- No alert yet for orders nobody answered; staff see the number of new orders on the **New** tab.
- Messages to the customer are e-mail only (no SMS or WhatsApp yet).

## Upgrade

Rebuild and restart the backend (Flyway applies V64), rebuild the frontend. Then request **Online Store** in Settings > Modules (it must be in your licence), activate it, open **Online Store > Shop settings**, add products and delivery areas, choose the delivery-fee product, tick **Shop is open to the public**, and place a test order from `/shop`.

## Checked / not checked

- The backend was compiled in the cloud against the project's classes, not built with Maven. The migration was run on a PostgreSQL 16 database and creates all six tables. The frontend passed the Angular compiler with strict templates.
- Not run: the application started with the module, a real order from the shop through accept, delivery, complete and payment, the French screens in a browser, and the shop on a phone. Test one full order of each kind (delivery and pick-up) before opening the shop.
- The French wording needs a native reader.
