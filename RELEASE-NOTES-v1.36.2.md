# Release 1.36.2 - Online Store: "Sellable now" under each product in Shop settings

Under each product in Online Store > Shop settings > Products on the shop, a line now shows how the shop got its stock figure:

> In sellable warehouse locations **N** − reserved on confirmed orders **R** − shop buffer **B** = sellable now **S**

The shop shows "Out of stock" when S is 0 or less (red line), "Low stock" at 5 or fewer, otherwise "In stock". A product whose stock is not tracked shows "Stock is not tracked for this product: the shop always shows it as in stock." This answers "why does the shop say out of stock when I have stock": the buffer (default 1), stock in a location that is not sellable (damaged, inactive), stock not entered per warehouse, or stock already promised on confirmed or part-delivered sales orders.

Backend: `GET /api/online-store/listings` rows carry `stockInfo` (tracked, inWarehouses, reserved, buffer, sellable). Frontend: `store-setup.component.ts`, two French texts. No migration. Checked on PostgreSQL (97 checks) and by the strict type-check; not run in a browser.
