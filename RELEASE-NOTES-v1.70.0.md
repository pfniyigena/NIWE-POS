# v1.70.0: returns post the VAT and the refund

Accounting gap 1 from `claude/online-store-accounting-gaps.md`.

- A sale order return now reverses the net sale and the VAT separately (Dr Sales Revenue, Dr VAT Payable, Cr Accounts Receivable). The tax is worked out like at delivery, after any discount.
- `refundTotal` of a sale order return is now the gross amount (tax included), so "still owed", "refund due" and the returns report are right on orders with tax.
- Online returns post the money that left: Dr Accounts Receivable, Cr Cash and Bank (journal source `ONLINE_REFUND`). Nothing is posted when a cash-on-delivery payment was never collected.
- No migration. Returns made before this version keep their old figures.
