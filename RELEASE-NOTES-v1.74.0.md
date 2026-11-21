# v1.74.0: returns at the cost sold, and a branch for the online shop

This release also carries v1.73.0 (see `RELEASE-NOTES-v1.73.0.md`): discounts given, delivery income and loyalty points as accounts. Both were built and tested together.

Accounting gaps 7 and 8 from `claude/online-store-accounting-gaps.md`.

- Each sale order line keeps what its units cost when they were fulfilled. A return (online or not) takes the goods back into Inventory, and out of Cost of Goods Sold, at that cost instead of the product's cost price on the day of the return. Lines fulfilled before this version keep using today's cost price.
- Online Store > Shop settings > Branch the shop sells for: the online shop's sale orders, invoices, sales, payments, deposits and loyalty entries carry this branch (head office by default). Shown when there is more than one branch.
- Migration V104.
