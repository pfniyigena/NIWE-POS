# NIWE ERP, version 1.19.0

Subscriptions: editing, plan changes with proration, seats and setup fees, trials, fixed terms, pauses, scheduled cancellation, overview, reports and overdue reminders. Versions 1.0.0 to 1.18.0 stay the base. One migration (V58). Two new pages (*Subscriptions > Overview* and *Subscriptions > Reports*), added by `tools/wire-v190.py`.

## What changed

1. **Edit a subscription.** *Edit terms* changes price, quantity (seats), interval, discount, tax, setup fee (until it is billed), term, renew-or-end and notes. Changes apply from the next bill; invoices already issued are not touched.
2. **Change plan with proration.** *Change plan* shows a live quote. For a prepaid subscription that is already billed for the current period, the unused part is settled by day rate: an upgrade raises an adjustment invoice now; a downgrade becomes a credit that is taken off the next invoice(s) as a negative line. Postpaid subscriptions simply use the new terms from the next cycle. You can switch the settlement off.
3. **Seats and setup fee.** A quantity multiplies the price on every invoice. A one-time setup fee is added as its own line on the first invoice.
4. **Free trial.** A trial of N days delays the first bill; the subscription shows a TRIAL badge and a banner with the end date.
5. **Fixed term.** A term of N cycles, then either renews automatically (counted as renewals) or ends (status *Ended*).
6. **Pause and skip.** *Pause* takes a reason and an optional date to resume by itself (if the billing date passed while paused, it becomes today, so billing is not lost). *Skip next billing* moves the next bill one cycle without an invoice.
7. **Cancel at the end of the period.** Besides *Cancel now*, a cancellation can be scheduled for the end of the paid period (prepaid) or after the last cycle is billed (postpaid), and withdrawn until then.
8. **Activity timeline.** Every change (created, edited, plan changed, billed, skipped, paused, resumed, cancellation scheduled or withdrawn, cancelled, renewed, ended, reminder sent) is recorded with who and when.
9. **Overview and list.** *Subscriptions > Overview*: monthly and yearly recurring revenue, paying, trial and paused counts, churn over 30 days, billing in 7 and 30 days, unpaid invoices, subscriptions ending. The list gains search, status and plan filters and views (on trial, billing within 7 days, cancelling, term ending, resuming), plus quantity and per-cycle columns.
10. **Reports.** *Subscriptions by Plan*, *Cancelled and Ended Subscriptions* (date range) and *Unpaid Subscription Invoices*; all three are also available as scheduled reports.
11. **Overdue reminders and catch-up.** *Subscriptions > Job settings* has a new card: e-mail the customer when a subscription invoice is overdue (first reminder after N days, then every N days, at most N; off by default; needs e-mail set up and a customer e-mail), and how many missed billing cycles one run may bill (default 1). The daily digest counts resumed subscriptions and reminders.

Invoice e-mailing on issue already existed and is unchanged.

## Technical

- Migration `V58__subscriptions_v119.sql`: status check now ACTIVE, PAUSED, CANCELLED, ENDED; new columns on `subscription`; new tables `subscription_event` and `subscription_settings`.
- Backend: `SubscriptionService` (update, quote and change plan, pause/resume/skip, cancel and undo, events, summary, billing with setup fee, credit and term handling), `SubscriptionAutomationService` (auto-resume, reminders, catch-up), `SubscriptionReportService`, `InvoiceService.generateForSubscription(SubscriptionInvoiceSpec)`. New endpoints under `/api/subscriptions`: `PUT /{id}`, `POST /{id}/change-plan/quote|change-plan|pause|skip-next|undo-cancel`, `GET /{id}/events`, `GET /summary`, `GET|PUT /settings`; list accepts `status`, `planId`, `search`, `view`; reports at `/api/reports/subscriptions-by-plan|subscriptions-churn|subscriptions-unpaid`. Writes need the operations manage permission.
- Frontend: new `subscription-overview` and `subscription-report-list` pages; rewritten `subscription-detail`; `subscription-list`, `subscription-form`, `subscription-job-settings`, `report-defs`, `access`, report viewer.
- Tests: `t108` (API, 156 checks), `walk92` (browser); full regression on the frozen tree.

## Not in this version

Usage-based (metered) billing, coupons, customer self-service portal, payment links, SMS reminders.
