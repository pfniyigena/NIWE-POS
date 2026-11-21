# v1.72.0: cash that drivers hold

Accounting gap 4 from `claude/online-store-accounting-gaps.md`.

- Cash a delivery driver collects at the door is booked to a new account, Cash with Drivers (1050), instead of Cash and Bank.
- Online Store > Driver cash: when the driver hands the cash in, the books clear Cash with Drivers, receive what he handed in in Cash and Bank, and book a difference in Cash Over / Short (short or over). The entry now carries the branch of the order.
- Pick-up orders paid in cash at the shop are unchanged. Orders delivered before this version settle as before (only the difference is posted).
- Migration V103.
