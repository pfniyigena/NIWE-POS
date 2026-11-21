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

## v1.72.0: cash that drivers hold

Before this release the cash a driver took at the door was counted as cash in hand (Cash and Bank) at delivery, and only a difference was posted when he handed it in. Now:

| When | Entry (source type) | Debit | Credit |
|---|---|---|---|
| A delivery order paid in cash is completed | `CUSTOMER_PAYMENT` | Cash with Drivers (1050) | Accounts Receivable |
| Online Store > Driver cash: the driver hands in the cash | `DRIVER_CASH_SETTLEMENT` | Cash and Bank (what he handed in), Cash Over / Short (when short) | Cash with Drivers (what he collected), Cash Over / Short (when over) |

- A pick-up order paid in cash at the shop still goes straight to Cash and Bank.
- The settlement entry carries the branch of the order (before, the difference had none).
- `online_order.driver_cash_booked` is what the books hold in Cash with Drivers for the order. Orders delivered before this version have none: their cash went to Cash and Bank at delivery, so settling them posts only the difference, as before.
- The balance of Cash with Drivers is the cash drivers still have to hand in; it should agree with the "Waiting" total on the Driver cash screen. Migration V103; new account 1050 (Rwanda chart) / 5214 (Chad chart).

## v1.73.0: discounts given, delivery income and loyalty points as accounts

Until now the books held one figure, Sales Revenue, after discounts and with the delivery fee inside it, and loyalty points did not appear at all. Now, when an online order is delivered:

| When | Entry (source type) | Debit | Credit |
|---|---|---|---|
| The order is delivered | `ONLINE_SALE_SPLIT` | Sales Discounts Given (coupon and points discount, net of tax), Sales Revenue (the delivery fee, net of tax) | Sales Revenue (the discount: so revenue is the full price), Delivery Income (the delivery fee) |
| A customer earns points (order completed) | `ONLINE_LOYALTY_EARN` | Customer Rewards Expense | Loyalty Points Liability |
| Points spent on the order are used (order delivered; only the delivered share on a partial delivery) | `ONLINE_LOYALTY_SPENT` | Loyalty Points Liability | Customer Rewards Expense |
| A return is processed | `ONLINE_DISCOUNT_REVERSAL` | Sales Revenue | Sales Discounts Given (the discount that belonged to the returned goods) |
| A return gives points back to the customer | `ONLINE_LOYALTY_RET_BACK` | Customer Rewards Expense | Loyalty Points Liability |
| A return takes back points that the order earned | `ONLINE_LOYALTY_RET_TAKE` | Loyalty Points Liability | Customer Rewards Expense |

- The split is a reclassification: the total of income for an order does not change. Revenue plus delivery income minus discounts equals the invoice before tax.
- Amounts are before tax. The sale order already puts the VAT on the discounted price, so the split divides the discount and the fee by the product's (or the delivery area's) tax rate when prices include tax.
- The cost of loyalty points shows once: as an expense when they are earned, and, when they are spent, as Sales Discounts Given (the expense is released then). So Customer Rewards Expense is the cost of points still held, and Loyalty Points Liability is what the shop owes in discounts (1 point = 1 currency unit).
- Orders that are cancelled or rejected post nothing: the discount and the points are only booked when the goods are delivered. The free-delivery waiver of a coupon has no account of its own: the fee is simply smaller.
- Points earned before this version are not in the liability. Their spending releases the liability, so Loyalty Points Liability can show a debit balance until an opening balance is posted by hand (a manual journal entry of the points customers hold, Dr Customer Rewards Expense / Cr Loyalty Points Liability).
- New accounts (added to existing charts at start-up): 4030 Delivery Income, 4040 Sales Discounts Given, 2820 Loyalty Points Liability (Rwanda chart); 7071, 7091, 4198 (Chad chart). No migration.

## v1.74.0: returns at the cost sold, and a branch for the online shop

**Returns restock at the cost the goods were sold at.** Until now a return took the goods back into Inventory (and out of Cost of Goods Sold) at the product's cost price on the day of the return, so a cost that moved after the sale left a difference in the books. Now each sale order line keeps what its units cost when they left stock (`sales_order_line.cost_total` and `cost_quantity`, added at every fulfilment), and a return uses the average of that: Dr Inventory / Cr Cost of Goods Sold at the sold cost. Lines fulfilled before this version have no record, and keep using the cost price of the product today. This applies to every sale order return, not only online ones.

**A branch for the online shop.** Online sale orders always went to head office. Online Store > Shop settings has a new choice, "Branch the shop sells for" (shown when the business has more than one branch). The sale order, its invoice, the sales, VAT, cost and payment entries, the deposit entries (money paid before delivery, and paid back) and the loyalty entries then carry that branch, so branch reports include the online shop. Head office stays the default. If the chosen branch is switched off later, new orders go to head office until another is chosen. Payouts from the providers stay unattributed (they concern the whole shop).

- Migration V104: `sales_order_line.cost_total`, `cost_quantity`; `online_store_settings.branch_id`.
- Orders accepted before the choice keep the branch they were given.

## v1.75.0: an account for each payment provider

Until now all mobile money (MTN and Airtel) shared one clearing account, and every payout landed in Cash and Bank. Each provider now has its own clearing account (what the provider still owes the shop) and its own wallet or bank account (where it pays out to), so each balance can be matched to that provider's own statement.

| Provider | Clearing account (Rwanda / Chad chart) | Wallet or bank account (Rwanda / Chad chart) |
|---|---|---|
| MTN Mobile Money | 1021 / 5215 | MTN Mobile Money Wallet 1011 / 5217 |
| Airtel Money | 1022 / 5216 | Airtel Money Wallet 1012 / 5218 |
| Card | 1030 / 5213 (as before) | Bank (card settlements) 1013 / 5219 |
| Other mobile money (test provider, and payments made before this version) | 1020 / 5212 (as before) | Cash and Bank |

- The provider that took a payment decides the account: `ONLINE_PAYMENT` debits that provider's clearing account (credit Online Customer Deposits). A refund through the provider, a deposit paid back and a payment applied at delivery use the same account. `online_order.clearing_channel` remembers it (MTN, AIRTEL, CARD or MOBILE_MONEY). Orders paid before this version have none and keep using the old accounts, so nothing moves.
- Online Store > Payments > Payouts shows one balance per provider (the old shared mobile money balance appears only while it is not zero) and a payout is recorded against one provider: `ONLINE_PAYOUT` debits that provider's wallet or bank account (net) and Payment Provider Fees (fee), and credits its clearing account (gross).
- To move money from a provider's wallet to the main bank account, or to pay a supplier from it, post a transfer with a manual journal entry (or the usual payment screens) on those accounts.
- A balance on the old shared mobile money account (payments before this version) is cleared with a payout of the channel "Mobile money (earlier payments)", which lands in Cash and Bank as before.
- New accounts are added to existing charts at start-up. Migration V105 (`online_order.clearing_channel`; the payout channel check now allows MTN and AIRTEL).
