# Online Store: the customer portal (v1.55.0)

The shop has an optional customer account (sign in with a code sent by e-mail or text message). Since v1.55.0 the signed-in customer has a proper area, "My account", instead of a single card.

## Where it is

- The 👤 button in the shop header opens `/shop/account`. Signed out, the page shows the sign-in; signed in, it shows the menu and the page of the address:
  `/shop/account/orders`, `/invoices`, `/wishlist`, `/returns`, `/addresses`, `/points`, `/profile`. An unknown address falls back to My orders.
- `/shop`, `/shop/account` and `/shop/account/<page>` are one route of the app, so moving between the shop and the portal keeps the cart in the browser.
- The sign-in card that opens from the order form ("Sign in to save your details") is unchanged, so a customer never loses the order form.

## The pages

| Page | What the customer sees |
|---|---|
| My orders | Orders of the account with filters (All, In progress, Delivered, Cancelled); Track, Order again, Invoice (PDF) once the order is completed. |
| My invoices | One line per invoice: number, order, date, total, Download (PDF). |
| My wish list | Hearted products with price and stock; Add to cart or Choose options; remove the heart. |
| My returns | Every return asked for, in any order: status, goods, reason, shop note, refunded amount. |
| My addresses | Saved delivery addresses. |
| Loyalty points | Balance and history; shown only when the shop has loyalty points. |
| My profile | Name, e-mail, phone (with confirmation code) and address; sign out; delete my account. |

## For the developer

- Front end: `shop-portal.component.ts` (menu and pages) uses `shop-account.component.ts` with `[section]` for addresses, points and profile; `shop.component.ts` shows the portal in place of the shop's pages when the address starts with `/shop/account` (`shopMatcher` in `app.routes.ts`).
- Back end: `GET /api/public/store/account/returns` (header `X-Shop-Token`) lists the returns of the account's orders; `GET .../account/orders` carries `invoiceNumber`. The invoice PDF uses the existing `POST /api/public/store/orders/{code}/invoice` with the account token (no phone number needed).
- No database change. `robots.txt` disallows `/shop/account`.
