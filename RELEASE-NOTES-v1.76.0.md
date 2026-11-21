# v1.76.0: choose the accounts of each payment provider

Apply after v1.75.0.

- Online Store > Payments > Accounts by provider lists every payment provider with two choices: the account for what the provider still owes you, and the wallet or bank account its payouts arrive in. Each can be an existing asset account or a new one created on the spot (with the next free code suggested).
- MTN and Airtel keep the accounts from v1.75.0 until changed. A provider with no accounts uses the shared Card or Mobile Money accounts and its payouts arrive in Cash and Bank.
- Rules: a clearing account belongs to one provider and must be an ordinary asset account; it cannot be changed while the provider still owes money on it.
- Payouts: one balance per provider with a clearing account, then Card; each payout is booked to the provider's own accounts.
- Migration V106 (`online_provider_account`; v1.75.0 channel values MTN and AIRTEL become the provider codes).
