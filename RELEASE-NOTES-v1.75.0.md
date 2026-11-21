# v1.75.0: an account for each payment provider

Apply after v1.74.0 (which carries v1.73.0).

- Each payment provider has its own clearing account (what it still owes the shop): MTN Mobile Money (1021), Airtel Money (1022), Card (1030). The old shared mobile money account (1020) stays for the test provider and for orders paid before this version.
- Each provider also has the account its payouts arrive in: MTN Mobile Money Wallet (1011), Airtel Money Wallet (1012), Bank, card settlements (1013). A payout is recorded against one provider (Online Store > Payments > Payouts) and lands in that account, with the fee in Payment Provider Fees.
- Orders paid before this version keep using the old accounts, so no balance moves.
- Chad chart: 5215 to 5219.
- Migration V105.
