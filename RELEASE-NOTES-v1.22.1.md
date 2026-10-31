# NIWE ERP, version 1.22.1

Subscriptions: month-end billing fix. Versions 1.0.0 to 1.22.0 stay the base. One migration (V62). No screen changes.

## What was wrong

A subscription that starts on the 29th, 30th or 31st drifted to an earlier day. Starting on 31 January it billed 28 February, then 28 March, 28 April and so on, and never came back to the 31st. The proration quote for a plan change and the start date of a "billed after service" period were also a day out at month ends, because taking a month off a clamped date does not give back the date you started from.

## What changed

1. **A billing day is kept for each subscription.** It is the day of the month on which billing starts (the start date plus any free trial). Monthly, quarterly and yearly dates now fall on that day, or on the last day of a shorter month: 31 January gives 28 February, 31 March, 30 April, 31 May. A quarterly subscription from 30 November gives 28 February, 30 May. A yearly one from 29 February gives 28 February in ordinary years and 29 February in leap years. Weekly billing is unchanged.
2. **Periods join up.** Invoice periods run end to end with no gap or overlap (for example 31 Jan to 28 Feb, 28 Feb to 31 Mar), for both billed-in-advance and billed-after-service subscriptions.
3. **Plan-change quote** uses the real length of the period already billed, so the credit and charge at a month end are correct.
4. **Skip next** keeps the billing day. **Resume** after a pause that carried the date past today starts a new billing day from today.
5. **Existing subscriptions are repaired.** The migration works out the original day from the start date and the next billing date, so a subscription that had already drifted to the 28th goes back to the 31st on its next bill. A subscription whose day cannot be recovered (the next billing date is below the 28th) keeps the day of its next billing date, as before.

## Technical

- Migration `V62__subscription_billing_anchor.sql`: `subscription.billing_anchor_day` (integer, backfilled).
- `BillingInterval.addTo(date, anchorDay)` and `subtractFrom(date, anchorDay)`; the one-argument forms remain and behave as before. `Subscription.billingAnchorDay`. `SubscriptionService`: create, plan-change quote, resume, skip and billing use the anchor (an older row with no value falls back to the day of its next billing date).
- Tests: `t108` (subscriptions, 156 checks, date maths made anchor-aware) and the new `t112` (month-end billing, 16 checks: monthly from the 29th, 30th and 31st including a leap year, quarterly, yearly from 29 February, billed after service, skip, quote, resume). The migration chain applies cleanly on a fresh database.

## Not in this version

The billing day is not shown or editable on the subscription screen. Changing the day by editing the next billing date is a possible later addition.
