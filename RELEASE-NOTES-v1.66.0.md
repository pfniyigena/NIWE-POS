# Release notes v1.66.0 - Online Store: stock held from checkout, buffer per product, share for the till

## Fixed
- **Two customers could be sold the same last units.** Stock was only reserved when staff accepted the order, so every order placed before that saw the full stock. A shop order now holds its units from the moment it is placed; the products of an order are locked while it is checked and saved, so customers ordering at the same instant cannot both win.
- **An unanswered order hid stock forever.** An order waiting for a payment, or a cash order nobody accepted, is now cancelled after a number of hours and the stock is free again. The customer is told by the usual e-mail or text.

## New
- **Shop settings > Release unanswered orders after (hours)**: default 24, 0 = never. An order **paid online** is never cancelled by it (the customer paid; staff must answer it).
- **Shop settings > Share kept for the till (%)**: this share of the available units of every product is kept back for the till (rounded up), on top of the buffer.
- **Buffer for this product** (list of products, and each option of a product with options): its own safety buffer; empty = the shop-wide one.
- The line under each product now shows every figure: in the sellable locations, reserved on confirmed orders, held by orders nobody answered yet, buffer, kept for the till, sellable now.

## Rules
Held = units of orders waiting for a payment or placed and not yet accepted. When staff accept, the sale order reserves the units and the hold ends (never counted twice). Reject, cancel and the automatic release free them. The card word, "Only n left", the In stock filter, the checkout check and the back-in-stock alerts all use the same figure.

## Technical
Migration V99 (`online_store_settings.auto_release_hours`, `till_share_percent`; `online_listing.stock_buffer`). `OnlineShopStock.held/bufferOf/tillShare/lockProducts`, `OnlineOrderService.autoRelease`, `OnlineOrderReleaseJob` (every 10 minutes). Version 1.66.0. See `docs/online-store-held-stock.md`.
