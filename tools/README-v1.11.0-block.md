### Version 1.11.0 - Receipt tax ID, Dashboard paging, sales feature switches

Three changes (see `RELEASE-NOTES-v1.11.0.md` for the summary, database and upgrade notes).

- **Customer tax ID on the POS receipt.** The printed receipt and the emailed receipt PDF show "Customer tax ID" under the customer when the customer has one (`customerTaxId` on the order).
- **Low-stock paging on the Dashboard.** The low-stock products and the per-warehouse low-stock lists show 10 rows per page with a pager.
- **Promotions, Gift cards and Loyalty points switched on in Sales > Settings.** `GET/PUT /api/sales/settings` (administrator) carries `promotionsEnabled`, `giftCardsEnabled` and `loyaltyEnabled`; `GET /api/sales/features` (any signed-in user) tells the screens which are on. Switched off = hidden in the menu, till and checkout and refused by the server for new use (existing data and refunds stay). Migration V51: after upgrade Promotions are on if any promotion exists, Gift cards on if any card exists, Loyalty keeps its setting; fresh installs start with all three off.
