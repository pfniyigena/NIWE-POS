# Release notes v1.64.0 - Online Store: a new delivery time after a failed visit

## What is new
When staff record **Delivery failed** (Online orders), and delivery time windows are on:
- the order goes back to Packed as before, but its **old window is freed at once** (the place is free for other customers) and the order history says so;
- the customer's message now asks him to **open his order page and choose a new delivery time**;
- the **order page** shows a box "We could not deliver your order. Please choose a new delivery time.": a day list, the windows of that day (full or too-late ones are greyed out) and **Confirm this delivery time**. He is told the new time by message, and the shop gets a notice (e-mail / text as set up). He may change it again until the order goes out;
- **Online orders** shows "Waiting for a new time (the customer was asked to choose)" until a window is set; staff can still use **Change time** (phoning the customer, for example).

## Rules
- Same checks as at checkout: a free place (even two customers at once), notice, days ahead, closed days. The order code is the key, like following or cancelling an order.
- Only while the order is Packed after a failed visit. Not for pick-up orders, not while the order is out for delivery, not when windows are switched off (then staff arrange it by phone as before).
- If the shop made windows **compulsory**, a failed-visit order cannot go "Out for delivery" again until a new window is set (by the customer, or staff with Change time). If they are optional, staff may send it out without one ("as soon as possible").
- The order's history shows "The customer chose a new delivery time: ..." and the audit log keeps the old and new window.

## Technical
No migration. `POST /api/public/store/orders/{code}/slot` {slotId, slotDate}; `Track.canRebook`; `OnlineOrderService.deliveryFailed` clears the window; `OnlineSlotService.enabled()`. Version 1.64.0.
