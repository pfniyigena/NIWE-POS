# Online Store: invoice download, address book, wishlist, WhatsApp (v1.47.0)

## Invoice download
A completed order has an invoice (made when staff complete the order). The order page (`/shop/order/CODE`) shows **Invoice INV-... / Download invoice (PDF)**. The customer types the **phone number of the order**, or is signed in to the account that holds the order. The PDF is the same A4 invoice staff print. Endpoint: `POST /api/public/store/orders/{code}/invoice` with `{ "phone": "..." }` (30 requests per hour per connection). Before the order is completed there is no invoice and the button is not shown.

## Address book (signed-in customers)
- Account page: **My delivery addresses**: add, edit, remove, **Make default**. Each address has a name (Home, Work...), an optional delivery area and the address. Up to 10 per customer.
- Checkout: the default address fills the form; **My saved addresses** picks another (area and address are filled together). A new address shows **Save this address in my account** with a name field; it is saved when the order is placed.
- Deleting the account deletes its addresses and wishlist.
- Endpoints: `GET/POST /api/public/store/account/addresses`, `PUT/DELETE .../addresses/{id}`.

## Wishlist (the heart)
- A heart on every product card and a button on the product page; a heart with a count in the shop header opens **My wishlist** (cards with Add to cart and Quick view).
- A guest's hearts stay in the browser only (`niwe.shop.wish`). A signed-in customer's hearts are kept in the account (up to 100), and the hearts given as a guest join the account at sign-in.
- Endpoints: `GET /account/wishlist`, `PUT/DELETE /account/wishlist/{productId}`, `POST /account/wishlist/merge`, and `GET /wishlist?ids=` (the products, listed ones only).

## WhatsApp
- **Shop settings > General > WhatsApp number of the shop** (empty = the phone shown on the shop; a number starting with 0 gets the country code of the text settings). 
- Customer: the order page has **Send this order to the shop on WhatsApp**, which opens WhatsApp with the order code, items and total ready to send. Nothing is sent by the system.
- Staff: **Online orders** shows a **WhatsApp** link beside the customer's phone that opens a chat with a short opening line.
- E-mails and text messages (SMS or WhatsApp provider, when the customer ticked the box at checkout) work as before.

## Upgrade (migration V82)
New tables `shop_address` and `shop_wish`; new column `online_store_settings.whatsapp_number`.
