# Release notes v1.63.0 - Online Store: delivery estimate per area

## What is new
**Shop settings > Delivery areas** has a new **Delivery days** column: *from* and *to*, in days after the shop accepts the order (0 = the same day). Leave both empty to use the estimate of the whole shop (Shop settings > Front page). One number alone is used for both.

The customer sees the estimate of **his** area:
- **At checkout**, right under the Delivery area choice, as dates ("Estimated delivery: Tue 24 Nov - Thu 26 Nov"), counted from today.
- **On the order page**, as days after acceptance ("1 to 2 days after we accept your order"), while the order is not yet on its way.
- **In the messages** "we received your order" and "your order is accepted" (customer's language), and in the texts.
- **Online orders** (staff) shows the same line on each delivery order.

## Rules
- The estimate is kept on the order when it is placed: changing an area later does not change orders already placed. Orders placed before this version show none.
- Pick-up orders have no estimate. The product page still shows the shop's own estimate (it does not know the area yet).
- It works next to the delivery time slots without any link: a customer who picks a window sees the window and the estimate. Windows are not blocked by the estimate.
- Numbers must be 0 to 60, the first not above the second.

## Technical
Migration V97: `online_delivery_zone.delivery_days_min/max`, `online_order.eta_days_min/max`. `PublicZone` has `daysMin/daysMax` (already the area's or the shop's); `Track` and `OrderDto` have `etaMin/etaMax`; `ZoneDto`/`ZoneRequest` have `deliveryDaysMin/Max`. Version 1.63.0.
