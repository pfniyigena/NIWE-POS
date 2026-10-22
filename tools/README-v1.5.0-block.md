### Version 1.5.0 - Core improvements, version 1.5.0

Eight steps across Sales, Purchases, Customers, Suppliers, Inventory papers, Accounting and the till (see `RELEASE-NOTES-v1.5.0.md` for the summary, database and upgrade notes).

- **Paging and phone layout.** `page`, `size`, `q`, `status`, `branchId`, `from`, `to`, `method`, `customerId`/`supplierId` on the invoices, credit notes, quotations, sales orders, purchase orders, bills, refunds, customer payments, supplier payments, customers and suppliers lists; total in the `X-Total-Count` header; without them the whole list comes back as before.
- **Exports.** `GET /api/export/{invoices|credit-notes|quotations|sales-orders|customer-payments|customers|purchase-orders|bills|refunds|supplier-payments|suppliers|collections|collection-customers}?format=xlsx|pdf|csv`, same filters and access as the screens, whole filtered list.
- **Customer collections.** `/api/collections` (list with ageing, statement PDF and e-mail per customer, reminders, settings); daily job about 07:15 (off by default); migration V15.
- **Delivery and stock papers.** `GET /api/sales-orders/{id}/delivery-note`, `GET /api/purchase-orders/{id}/goods-received-note`, `GET /api/stock-counts/{id}/sheet[?showSystem=true]`, `GET /api/stock-transfers/{id}/note`.
- **Imports with a check first.** Every importer takes `?dryRun=true` (processed for real, then rolled back). New: `POST /api/data/import/{suppliers|categories|brands|customer-balances|supplier-balances|ledger-balances}` with a template for each; opening balances are real `OB-` invoices and bills; migration V16.
- **Accounting controls.** `/api/accounting/controls/...`: close the books up to a date, bank reconciliation, ledger opening balances; migration V17.
- **Dashboard that acts.** `GET /api/dashboard/overview?branchId=`.
- **Price lists and customer groups.** Sales > Price lists and Customer groups; one price rule (`PricingService.resolve`) for the till, synced offline sales and, when no price is typed, invoices, quotations and sales orders; migration V18.
- **Promotions and coupons.** Sales > Promotions; one rule (`PromotionService.apply`) run on every change to the till cart and again at payment; migration V19.
- **Gift cards and loyalty points.** `/api/giftcards` (sell, top up, block, expiry, release expired, lookup) and `/api/loyalty` (settings, customers, adjust, balance); gift-card money is a liability until spent; migration V20.

Limits: full lists are built before paging; reconciliation shows at most 2000 ledger lines; promotions, gift cards and loyalty points are till features and are not used on offline sales, invoices, quotations or sales orders; a typed price is never replaced by a price list; imports are untested on very large files; loyalty points do not expire and are not accrued as a liability.
