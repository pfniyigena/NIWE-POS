-- v1.22.1 Subscriptions: remember the day of the month a subscription bills on, so a subscription that started on the 31st
-- goes back to the 31st after a short month instead of staying on the 28th for good.
alter table subscription add column billing_anchor_day integer;

-- Existing rows: use the day the billing started (start date plus trial) when the next billing date sits at a month end that a
-- short month may have pulled down (28 or later); otherwise the day of the next billing date is already the right one.
update subscription
   set billing_anchor_day = case
         when extract(day from (start_date + trial_days)) > 28 and extract(day from next_billing_date) >= 28
           then extract(day from (start_date + trial_days))::integer
         else extract(day from next_billing_date)::integer
       end;
