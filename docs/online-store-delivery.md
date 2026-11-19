# Online Store: drivers, delivery slips and run sheets (v1.52.0)

## For the shop team

1. **Add your drivers** in Online Store > Shop settings > Drivers: name, phone, an optional note. Switch a driver off (Active) when he stops working with you: he disappears from the choices but stays on past orders.
2. **Assign** on any delivery order: the "Driver" list on the order card. You can change it until the order is delivered. The history of the order records every change.
3. **Print the delivery slip** (button on the order): a one-page PDF for the driver to carry with the goods. The customer signs "Received by the customer" when the goods are handed over.
4. **Print the run sheet** for the day: in the orders list choose the driver in the filter, then "Run sheet". Choose "Not assigned" to see the delivery orders nobody has yet.
5. **Out for delivery**: press it when the driver leaves. If no driver is set you are asked to choose one first. The customer then sees the driver's name and phone on the order page and receives it in the "on its way" message.
6. **WhatsApp**: "Send to the driver on WhatsApp" opens a chat with the order's details (the phone must be saved on the driver).

The amount to collect is the order total unless the customer already paid online (then the slip says nothing is to be collected). When the driver is back, press "Complete" on the order as before: it takes the stock out, invoices and records the cash.

## Notes for the technical team

- Tables: `online_driver`; `online_order.driver_id`, `driver_name`, `driver_phone` (copies, so an order keeps the name even if the driver is renamed or removed) and `driver_assigned_at`.
- Endpoints listed in `RELEASE-NOTES-v1.52.0.md`. PDFs use `PdfKit` and follow the interface language of the staff member.
- The driver's phone is shown to the customer only while the order is OUT_FOR_DELIVERY (public tracking endpoint).
