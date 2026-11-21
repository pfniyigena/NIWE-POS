# v1.73.0: discounts, delivery income and loyalty points in the books

Accounting gaps 5 and 6 from `claude/online-store-accounting-gaps.md`.

- Sales Revenue now shows the full price of the goods. The coupon and loyalty-points discount is in a new account, Sales Discounts Given (4040), before tax.
- The delivery fee is in a new account, Delivery Income (4030), before tax, instead of inside Sales Revenue.
- Loyalty points are a liability (2820): earned points are an expense and a liability; points spent on a delivered order release the liability (the cost then shows in Sales Discounts Given); a return that gives points back or takes them back moves the liability the other way.
- A return reverses the discount that belonged to the returned goods.
- Nothing is booked for orders that are cancelled or rejected.
- Points earned before this version are not in the liability (see `docs/online-store-accounting.md`).
- No migration. New accounts are added to existing charts at start-up.
