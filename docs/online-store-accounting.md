# Online Store and Accounting

How an online order reaches the books, release by release. The accounting side has one door (`AccountingService`); the Online Store never writes journal lines itself.

## v1.70.0: returns post the VAT and the refund

Before this release a return took only the net price off Sales Revenue and credited Accounts Receivable, so the VAT of the returned goods stayed on VAT Payable and the money handed back was never posted.

What a return of 1 bag (45.00 + 18% VAT = 53.10) posts now:

| Entry | Debit | Credit |
|---|---|---|
| Sales order return | Sales Revenue 45.00, VAT Payable 8.10, Inventory 30.00 (cost) | Accounts Receivable 53.10, Cost of Goods Sold 30.00 |
| Online refund (`ONLINE_REFUND`) | Accounts Receivable 53.10 | Cash and Bank 53.10 |

- The return amount is worked out like the delivery was: the line or order discount first, then the tax (on top of the price or inside it). The "refund" on a sale order return is now the customer's money back, tax included (`SalesOrderReturn.refundTotal`). Reports, "amount still owed" and "refund due" use that figure, so they are now right for orders with tax. Returns made before this version keep their old (net) figure.
- This applies to every sale order return, not only online ones.
- The refund entry is posted by the Online Store when staff process a return (cash handed back, or a refund through the payment provider). A cash-on-delivery order whose payment was never collected has nothing to hand back: no cash entry, the credit only lowers what the customer owes.
- Memos carry the online order number.
