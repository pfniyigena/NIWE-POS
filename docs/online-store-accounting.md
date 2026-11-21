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

## v1.71.0: money paid online is held, applied at delivery and cleared against the provider's payouts

Before this release a prepaid order was invisible in the books until the order was delivered, a cancelled and refunded one left no trace, and card and mobile money all went to Cash and Bank. Now:

| When | Entry (source type) | Debit | Credit |
|---|---|---|---|
| The provider confirms the payment | `ONLINE_PAYMENT` | Mobile Money Clearing (1020) or Card Clearing (1030) | Online Customer Deposits (2810) |
| The order is delivered (the payment is recorded on the invoice) | `CUSTOMER_PAYMENT` | Online Customer Deposits | Accounts Receivable |
| The order is cancelled or rejected and the money is paid back | `ONLINE_DEPOSIT_REFUND` | Online Customer Deposits | the provider's clearing account (refund through the provider) or Cash and Bank (refunded by hand) |
| Part of the goods is not delivered and the difference is paid back | `ONLINE_DEPOSIT_REFUND` | Online Customer Deposits | the same |
| A return of delivered goods is refunded | `ONLINE_REFUND` (v1.70.0) | Accounts Receivable | the provider's clearing account, or Cash and Bank when refunded by hand |
| The provider pays out to the bank (Online Store > Online payments > Payouts) | `ONLINE_PAYOUT` | Cash and Bank (net), Payment Provider Fees (5240) | the provider's clearing account (gross) |

- The money held for an order is `online_order.deposit_left`. It is reduced when the invoice payment takes it, and when it is paid back.
- Orders paid before this version have no deposit: when delivered, their payment is debited to the provider's clearing account (so the payout can still be settled against it), and a cancelled one posts nothing.
- A payment under review (wrong amount, or a second payment for an order already paid) is not posted until staff deal with it by hand.
- The balances on the Payouts panel are the clearing accounts of the ledger (debits minus credits), so they always agree with the books. A provider that pays out net of its fee and of refunds: enter the gross the payout covers and the fee, and the rest reaches Cash and Bank.
- New accounts (added to existing charts at start-up): 1020, 1030, 2810, 5240 (Rwanda chart); 5212, 5213, 4197, 631 (Chad chart). Migration V102 (`online_order.deposit_left`, table `online_payout`).
