# Release notes v1.60.0 - Online Store: the driver's cash balance on his own page

## What is new
The driver's private page (the link with the PIN) has a new card at the top, **Cash I hold**:

- the **total** of the cash he took at the door and has not handed in (amber when there is something to hand in, green when nothing);
- the **orders** behind it: code, customer, date and amount, newest last;
- **Handed in lately**: his last ten settlements, with what he handed in and the difference (red when short, green when over).

The balance is the same list staff settle in **Online Store > Driver cash**, so when staff record a hand-in the order leaves "Cash I hold" and appears under "Handed in lately". The driver sees only his own cash, never another driver's. The page refreshes every 20 seconds.

## Notes
- Cash only counts for delivery orders paid in cash and completed with the cash collected. Orders paid online or completed without cash never appear.
- Nothing changes for staff screens, accounting or settlement.

## Technical
No migration. `DriverBoard` has a new `cash` ({holding, waiting[], settled[]}) built by `OnlineDriverAccessService.cash`, from two new repository methods filtered by driver (`findByDriverIdAndCashCollectedGreaterThanAndCashSettledAtIsNull...`, `findTop10ByDriverIdAndCashSettledAtIsNotNull...`). Version 1.60.0.
