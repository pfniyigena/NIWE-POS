# Delivery drivers without a login: private link and PIN (v1.59.0)

## Why
A driver is only a name and a phone number in the shop's list (v1.52.0), not an ERP user. The screens that complete a delivery (Online orders, My deliveries) need a staff login. The private link gives the driver a safe, narrow way in from his own phone.

## Set it up (staff)
1. Online Store > Shop settings > Drivers. Add the driver with his phone number and keep **Active** ticked.
2. Click **Give a link**. A panel shows the link and the 4-digit PIN.
3. Click **Send the link on WhatsApp**, then **Send the PIN on WhatsApp** (or tell him the PIN by voice). Sending them apart means a lost message does not give access.
4. The driver opens the link, types the PIN, and can add the page to his phone's home screen.

The PIN is shown **only once**. If it is forgotten, click **New PIN**. **Show link** shows the link again (never the PIN). **Switch off** removes the link and PIN; **Give a link** makes new ones (the old link never works again).

## A delivery (driver)
1. Staff assign the driver to the order and press **Out for delivery**. The customer receives the 4-digit delivery code.
2. The order appears on the driver's page (it refreshes every 20 seconds, or tap the refresh button).
3. At the door: **Hand over**, then type the code the customer reads out and tap **Confirm with the code**. The driver does not take a signature: the customer can sign only on his own page.
4. The order is completed: stock out, invoice, cash recorded. For an order paid on delivery, the cash appears in **Driver cash** for the hand-in at the end of the day.

If the customer is absent, refuses, or keeps only part of the goods, the driver does not hand over: he calls the shop, and staff record the failed or partial delivery on the order (see `online-store-delivery-problems.md`).

## Rules
- The driver sees only orders assigned to him that are out for delivery. An order of another driver is refused.
- 5 wrong PINs lock the link for 30 minutes; a new PIN from staff unlocks it at once. Wrong delivery codes: 5, then 30 minutes (same as the staff screen; staff can send the customer a new code).
- A session lasts up to 14 hours after the last use, then the PIN is asked again. One session per driver. A new link, a new PIN, switching off, or unticking **Active** ends it.
- Everything the driver does is in the order history as "Driver: name"; making, re-PIN and switching off a link are in the audit log.

## Address of the link
The link starts with `app.frontend-url` (the public address of the app). Without it, staff only see the end (`/driver/...`) and must add the address themselves. Use HTTPS.
