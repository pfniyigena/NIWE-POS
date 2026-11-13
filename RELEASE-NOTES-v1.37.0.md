# Release 1.37.0 - Online Store: sales reports

**Online Store > Sales reports** opens six reports with a from and to date and the usual Excel and PDF download. They are also in the list of reports that can be e-mailed on a schedule.

**A sale counts on the day its online order was completed** (the day staff pressed Complete and the invoice was issued), not the day it was placed or paid. The money is what the invoice says, so the totals agree with the invoices. Returns are shown on the day they were refunded.

- **Online sales by day**: orders, delivery and pick-up counts, goods, coupon discounts, delivery fees, invoiced, returns refunded and net.
- **Online sales orders**: every completed order with its invoice, invoiced, paid and refunded.
- **Online sales by payment method**: how the invoices were paid (cash, mobile money, card) and what is not collected yet.
- **Online orders not counted as sales**: orders placed in the period that are open, waiting for payment, never paid, cancelled or rejected, with their value, the money paid online and the money refunded.
- **Online sales by coupon**: orders, discount given, delivery fees waived, invoiced.
- **Online best-selling products**: quantity, revenue after coupons, returned quantity, refunds and net.

An order whose invoice was voided still shows as an order, with invoiced 0, and payments on a voided invoice are not counted. Days are cut at midnight in the time zone of the server.

New: `OnlineSalesReportService`, six endpoints `/api/reports/online-sales-*`, the report screen list at `/admin/online-store/reports` (menu link "Sales reports", open to the Reports or Operations area), six entries in the scheduled-report list, French texts (screens and server). No database migration.

Checked on PostgreSQL (26 checks, in UTC and in Africa/Kampala): daily totals equal the invoices table, a void invoice and its payment are left out, orders completed just outside the period are left out (midnight boundary), payments by method add up with "Not collected yet" to the invoiced total, not-sales groups (open, waiting, never paid, cancelled incl. a paid-then-refunded one, rejected), coupon and product figures including a refunded return. The Angular code was checked with a strict compile. Not checked in a browser.
