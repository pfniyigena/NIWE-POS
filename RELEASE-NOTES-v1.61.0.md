# Release notes v1.61.0 - Online Store: delivery time slots

## What is new
**Shop settings > Delivery times** (new card):

- **Windows**: from / to time, **orders per day** (0 = no limit), the **weekdays** the window exists, active. Example: 08:00-11:00, 11:00-14:00, 14:00-17:00, 17:00-20:00.
- **Switches**: customers choose a delivery time (on/off), they **must** choose one (off = "As soon as possible" is allowed), **days ahead** (0-60) and **notice** (hours before a window starts that it closes, 0-72).
- **Days without deliveries**: dates (holiday, stock-taking) with an optional reason; no window can be chosen that day.

**At checkout** (delivery orders only), under the address: a **Delivery time** choice. The customer picks a day (Today, Tomorrow, ...) and a window. A window that is full shows "Full", one that starts too soon shows "Too late"; neither can be picked. Pick-up orders have no window.

**After the order**:
- the order page and the "received" and "accepted" messages say "Delivery planned on 2026-11-24, between 08:00 and 11:00";
- **Online orders** shows the window on each delivery order, with **Change time** (any window and day, or back to "As soon as possible"); the limit does not stop staff, the customer is told and the order history and audit log keep it;
- the **driver's page** shows the window of each delivery;
- the **delivery slip** has a "Delivery time" line and the **run sheet** is sorted by window then area and shows the window in each stop.

## Rules
- The count of a window is the orders that hold a place: everything except rejected and cancelled orders. A rejected or cancelled order frees its place.
- Two customers cannot take the last place at once: the window is locked while an order is placed.
- A window can be booked from today up to **days ahead** days, until **notice** hours before it starts, and only on the weekdays it exists and on days that are not closed. The server checks all of it again when the order is placed.
- The shop's own clock (the server's time zone) decides what "today" and "too late" mean.
- Orders placed before this version, and orders with "As soon as possible", have no window and do not count.

## Notes
- Switched off by default. Turning it on with no window leaves checkout unchanged.
- Cash on delivery, online payment, coupons and loyalty are not affected.
- A failed delivery goes back to Packed with its window unchanged: use Change time to give a new one.

## Technical
Migration V95: `online_delivery_slot`, `online_closed_day`, `online_store_settings.slots_enabled/slots_required/slot_days_ahead/slot_notice_hours`, `online_order.slot_date/slot_start/slot_end`. New `OnlineSlotService` and `OnlineSlotController` (`/api/online-store/slots`, `slot-settings`, `closed-days`, public `GET /api/public/store/delivery-slots`, staff `POST /api/online-store/orders/{id}/slot`). `PlaceRequest` has optional `slotId` and `slotDate`. Version 1.61.0.
