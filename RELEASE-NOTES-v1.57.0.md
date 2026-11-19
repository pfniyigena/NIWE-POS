# Release 1.57.0 - Online Store: driver cash hand-in

Cash a driver takes at the door is now followed until he hands it in.

- When a delivery order is completed with the payment taken in cash (by the customer's tap, the code, the driver's signature screen, or staff pressing Complete), the amount recorded is kept on the order as **cash to be handed in**.
- New screen **Online Store > Driver cash**: tab **To hand in** (per driver totals, the orders with customer, delivery time and the cash collected) and tab **Settled** (the latest 200: collected, handed in, difference, who and when, the reason). Filter by driver or "Not assigned".
- For each order type what the driver really **handed in** (it starts at the amount collected), tick the orders and press **Settle the selected**. All selected orders are settled together or none is.
- A **difference needs a reason** (3+ characters). The difference is **posted to Cash over / short** in the accounts: short = an expense (Dr Cash over / short, Cr Cash), over = income. The order's history shows "Driver cash handed in: X (difference Y)" and the audit log records it. A settlement cannot be undone from this screen: correct a mistake with a journal entry.
- Only delivery orders completed after this version, paid in cash on delivery, appear (older orders and orders paid online or by mobile money never do; pick-up cash belongs to the counter).
- Database: migration V90 (`cash_collected`, `cash_settled_at/by`, `cash_handed_in`, `cash_difference`, `cash_note` on `online_order`). API: `GET /api/online-store/driver-cash?driverId=` (view), `POST /api/online-store/driver-cash/settle` (manage). French texts.

Good to know: the cash recorded is the **invoice** total. If the product chosen as the delivery fee carries a tax added on top, the invoice (and the cash recorded) is higher than the total the customer was shown, and every order would show a difference when the driver hands in what the customer paid. Set that product's tax to inclusive (or none) so the two match.

Tested in the cloud (PostgreSQL, real server, headless browser): orders completed by staff, by code, and with "payment not collected" (not listed); refusal of unknown cash, a difference without a reason, a negative amount, a double settlement; all-or-nothing settlement; per-driver totals and filter; the screen (difference hint, missing reason, settle two orders, settled tab); the journal entry for a short of 100 (Dr Cash over / short 100, Cr Cash 100). Not tested: French screens, an "over" entry in the browser, a closed accounting period.
