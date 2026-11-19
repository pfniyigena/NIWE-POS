# Online Store: order reception (v1.56.0)

When the goods reach the customer, the order can be completed without the shop pressing "Complete" itself. Completing does everything the shop's own Complete does: takes the stock out, issues the invoice and sends it, gives the loyalty points and, for an order paid in cash, records the cash as collected.

## The delivery code

- The code (4 digits) is made when the order is set **Out for delivery** (or **Ready for pick-up**). It is sent with the "on its way" / "ready" e-mail and text message, and is shown to the customer in **My account > My orders** and on the order page (`/shop/order/CODE`), after proof that the order is theirs (signed in to the account that holds it, or the phone number of the order).
- Staff and drivers never see it, and it is not printed on the delivery slip. The customer reads it out when the goods are in his hands.
- The driver, or the staff at the counter, types it on **My deliveries** or on the order in **Online orders**. After five wrong codes it is blocked for half an hour; a signature still works.

## Three ways to confirm

| Who | How | Recorded as |
|---|---|---|
| The customer | "I received my order" on the order page (optional signature, name) | Customer |
| Driver or counter staff | Types the code the customer reads out | Code (by the staff user) |
| The customer, on the driver's phone | Draws a signature in **My deliveries > Hand over** (or in the Orders screen) | Driver (by the staff user) |

The order must be out for delivery or ready for pick-up. The history line says how it was received; the order shows "Received ... · how" and **View signature**; the delivery slip prints the date, how, and the signature picture.

## My deliveries (the driver's screen)

Online Store > My deliveries (`/admin/online-store/driver`). Pick your name once (it is remembered on that phone) or "All drivers". Each order shows the customer (tap the phone to call), the address (with a map link), the goods, and what to collect (or "Already paid"). **Hand over** opens the code box and the signature box. The list refreshes by itself. Staff need the same permission as for the Orders screen (Operations: manage, to complete).

## Good to know

- A customer's own tap cannot prove that the driver collected the cash: keep checking the driver's cash with the run sheet.
- A customer who loses the code can open the order page again; a signature always works.
- No setting to switch on: it works for every delivery and pick-up order.
