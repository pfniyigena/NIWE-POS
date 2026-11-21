# v1.71.0: money paid online is held, applied at delivery and cleared against the provider's payouts

Accounting gaps 2 and 3 from `claude/online-store-accounting-gaps.md`.

- When a payment provider confirms a payment, the books record it at once: Dr the provider's clearing account (Mobile Money 1020 / Card 1030), Cr Online Customer Deposits (2810).
- At delivery the deposit is released against the invoice (Dr Online Customer Deposits, Cr Accounts Receivable). Cash and Bank is no longer used for online payments until the provider pays out.
- A cancelled or rejected order whose money is paid back, and the part of a partly delivered order that is paid back, reverse the deposit (provider refund: out of the clearing account; refunded by hand: out of Cash and Bank). Returns of delivered goods take the refund out of the same accounts.
- New panel Online Store > Online payments > Payouts: what each provider still owes the shop and a form to record a payout (gross, provider fee, date, reference). Cash and Bank gets the net, Payment Provider Fees (5240) the fee.
- Orders paid before this version keep working: delivered, they are debited to the clearing account; cancelled, nothing is posted.
- New accounts are added to existing charts at start-up. Migration V102.
