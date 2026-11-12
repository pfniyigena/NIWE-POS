# Release 1.31.0 - Online Store phase 3 (2 of 4): coupons

Customers can type a **coupon code** at checkout. A coupon gives **a percentage off the items** (with an optional cap), **a fixed amount off the items**, or **free delivery**. Staff create and manage coupons in **Online Store > Coupons**.

There is no switch: the coupon box appears on the shop as soon as at least one coupon is active, and disappears when none is. Orders without a coupon work exactly as before.

## How it works for the customer

1. On the order form, **Coupon code (optional)** and **Apply**. The shop says what the code is worth on this cart, or why it does not work ("expired", "needs an order of at least ...", "already used", "for delivery orders", "used up").
2. The totals show **Discount (CODE)** and, for a free-delivery coupon, the delivery line marked free. Changing the cart, the delivery or pick-up choice, or the area, checks the coupon again by itself.
3. The order page and the e-mails show the total the customer actually pays. Online payment asks for that total.

## For staff

- **Online Store > Coupons** (new): code, what it gives (percent, amount or free delivery), a cap for percentages, minimum order, valid from / until (both days included), number of orders it can be used on, **once per customer** (same phone number or the same signed-in account), active. The list shows how many orders hold a use. Delete is allowed at any time: orders that used a coupon keep its code and discount.
- **Online orders** show the coupon and the discount (or the waived delivery fee) on each order.
- **The books**: the discount is carried onto the **sale order and the invoice by lowering the line prices**, so the invoice total equals what the customer was charged and nothing else (the shop's own discount setting is not used). A fixed or percentage discount is shared over the lines in proportion to what each costs, to the cent. Because prices have 2 decimals, a line is sometimes split in two prices one cent apart (for example 3 x 1,500 less 500 = 2 x 1,333.33 + 1 x 1,333.34 = 4,000.00).

## What keeps it safe

- The server works out the discount, never the browser: the order asks again under a **lock on the coupon**, so two orders cannot both take the last use. A refused coupon places no order (the customer is never silently charged full price).
- Uses are counted from the orders: a **rejected or cancelled order gives its use back**.
- Once per customer matches the **last 9 digits** of the phone number, so 0788 123 456, 0788-123-456 and +250 788 123 456 are one person, and also a signed-in account.
- "Apply" is limited to 60 tries per connection per hour, so codes cannot be guessed one after another.
- A discount never takes off more than the items cost, and never touches the delivery fee unless the coupon is a free-delivery one. An order that becomes completely free cannot be paid online (nothing to pay); it can be sent as cash.

## Technical

- Migration `V69__online_store_coupons.sql`: table `online_coupon`; `online_order.coupon_id`, `coupon_code`, `coupon_key`, `discount_amount`, `fee_waived`; `online_order_line.discount_amount`.
- New: `OnlineCoupon`, `OnlineCouponRepository`, `OnlineCouponService` (the one place that decides what a code is worth, used by both the preview and the order). New endpoints: public `POST /api/public/store/coupons/check`; staff `GET/POST /api/online-store/coupons`, `PUT/DELETE /api/online-store/coupons/{id}`. The shop answer gains `coupons`; the order, the tracking answer and the staff order gain the coupon code, discount and waived fee; `PlaceRequest` gains `couponCode`.
- `tools/online-store-smoke.mjs` now also runs a coupon flow (it makes and deletes its own 10% coupon) and checks the invoice against the customer's total. Its test phone numbers are now 12 digits and distinct for every order.
- 19 new French texts on the server, 35 on the screens.

## Tested

- Against PostgreSQL 16 with V1 to V69 and Hibernate's schema validation, 58 checks, all passed, placing real orders through `OnlineStoreService.place`: no coupon unchanged; percent (also typed in lower case with spaces), cap, fixed amount, an amount larger than the order (never below 0, never takes the delivery fee), free delivery (and refused on pick-up); minimum order; ended yesterday, starts tomorrow, valid today only; unknown and switched-off codes; a refused coupon places no order; total uses (the third order refused, a cancelled order frees its use, rejected too); once per customer for the same phone, for +250 and dash spellings, for the same account with a new phone; the preview equals the order; the preview with and without phone; the per-connection limit on "Apply"; sharing a discount over lines (2,000 random orders: always adds up, never exceeds a line); the sale-order lines (5,000 random lines: the total is exactly the discounted amount, quantity kept, at most 2 lines); every staff rule (duplicate, bad code, 0% / 150%, bad dates, negative minimum, 0 uses, free delivery keeps no value); change and delete; deleting keeps the code and discount on the orders that used it.
- The screens pass the Angular compiler with strict templates; the backend compiles.

## Not tested

- **The screens in a browser, and the smoke script's coupon part, have not been run against the real application.** In particular the step **accept > complete > invoice total equals the customer's total** goes through the real sales-order and invoice code, which my cloud tests could not run (they check the line arithmetic only). Please run `node tools/online-store-smoke.mjs http://localhost:9090 admin <password> dev-simulated-secret 123456` and send me the output.
- Products whose tax is added on top of the price (not included): the invoice can differ from the customer's total by a cent or so, because the sale order adds the tax itself. The existing "paid online ... but the invoice is ..." note in the order history will say so for online payments.
- The French wording, real e-mail, a full Maven build.

## Known limits

- One coupon per order; coupons do not combine.
- A coupon applies to the whole order, not to chosen products or categories, and there are no automatic promotions without a code (not asked for in this version).
- The per-customer rule cannot stop someone who uses another phone number and no account.
- The "Apply" limit is kept in memory: it resets when the server restarts.
- A coupon that ends "today" works until midnight, server time.
