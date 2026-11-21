# Release notes v1.62.0 - Online Store: return pick-ups

## What is new
A return of a **delivered** order is now **collected from the customer by a driver**, at no charge. The driver confirms the collection with a **return code** the customer gives him, the same way a delivery is confirmed with the delivery code.

**Online Store > Returns**
- When you **approve** a return of a delivery order, a 4-digit **return code** is made and sent to the customer by **e-mail and text** ("A driver will come to collect the goods at ... Give the code to the driver only when you hand over the goods"). You may pick the **driver** at the same time, or later.
- The approved return shows a **"A driver collects the goods"** box: the address, the **driver** list (change or remove him until the goods are collected) and **New return code (send again)** (the old code stops working and wrong tries start again).
- New status **Collected**: the driver has the goods. A new **Collected** tab lists them; "Goods received: put back and refund" works from Approved and from Collected, exactly as before (stock back through the sale order return, refund the way the customer paid, delivery fee not refunded).
- A return of an order the customer **picked up at the shop** has no address: it stays "Please bring the goods back to us", with no code and no driver.

**The driver's page** (private link + PIN)
- A new **Returns to collect** list under his deliveries: who, phone, address, map, reason and the goods. He taps **Collect the goods**, types the customer's **return code** and confirms. He sees only the returns assigned to him, and never the code.
- Five wrong codes lock that return for half an hour. Staff can make a new code to unlock it.

**The customer**
- Gets the code by e-mail and text; signed in, **My returns** also shows the code, the driver's name and phone. The order page says a driver will come and, once collected, that the shop will check the goods and refund.
- When the driver collects, the customer gets a message, and the shop's notice address too ("Check them and refund").

## Rules
- Collection is for delivery orders with an address. No fee is charged; the delivery fee is still not refunded.
- A return can still be refused while it is Approved; once Collected it can only be processed.
- Only the driver chosen for the return can collect it. The code is never shown to anyone but the customer who is signed in (the order code alone does not show it).
- The order history says who collected, when, and how the code message went (e-mail / SMS).

## Notes
- Texts work like the delivery code texts: with no SMS company connected, the text is kept as "waiting for an SMS company" and the e-mail still goes.
- Returns approved before this version have no code: they stay "bring the goods back" and are processed as before.

## Technical
Migration V96: status check now allows `COLLECTED`; `online_return.pickup_driver_id/_name/_phone`, `pickup_code`, `pickup_failures`, `pickup_failed_at`, `collected_at`, `collected_by`. New endpoints: `POST /api/online-store/returns/{id}/driver`, `POST /api/online-store/returns/{id}/code`, `POST /api/public/driver/{token}/returns/{id}/collect`; `approve` takes an optional `driverId`; `DriverBoard.returnJobs`; `PublicReturn` has `collection`, `pickupCode` (owner only), `driverName/Phone`, `collectedAt`; pending counts add `returnsCollected`. Version 1.62.0.
