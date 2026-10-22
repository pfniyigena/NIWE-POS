# NIWE ERP, version 1.5.0

Core improvements: Sales, Purchases, Customers, Suppliers, Inventory papers, Accounting and the till. No new business module; versions 1.0.0 to 1.4.0 stay the base (see their release notes). Everything below works with the modules an installation already has licensed.

## What changed (eight steps)

- **Paging, search and phone layout on every core list.** Invoices, credit notes, quotations, sales orders, purchase orders, bills, refunds, customer payments, supplier payments, customers and suppliers get a search bar, status / method / date / branch filters and pages of 25, done on the server. Tables turn into cards on a phone.
- **Exports of every core list.** Excel, PDF and CSV of the same lists, with the filter that is on screen (the whole filtered list, not just the page shown).
- **Customer collections.** Collections screen (Customers menu): unpaid invoices and till sales on credit, days late, an ageing table (not yet due, 1-30, 31-60, 61-90, over 90 days), a reminder e-mail per customer with the statement attached, a statement PDF per customer, exports, and optional automatic reminders (daily at about 07:15, off by default).
- **Delivery and stock papers.** Delivery note for a sales order, goods received note for a purchase order, stock count sheet (blind, or with the system quantity on request), stock transfer note, all A4 PDF with signature lines.
- **Imports with a check first, and opening balances.** Every import can be checked first: nothing is saved, you see what will be created and what is skipped and why, then confirm. New imports: suppliers, categories, brands, customer opening balances, supplier opening balances and ledger opening balances. Opening balances are real documents (an OB- invoice or bill) so they are collected, paid and shown like any other, and do not count as sales or purchases.
- **Accounting controls.** Close the books up to a date (every ledger writer then refuses entries on or before it, with a clear message; admin only, reason required, every change logged). Bank reconciliation against a statement (tick lines, post a bank charge from the screen, finish only at zero difference). Ledger opening balances as one balanced entry.
- **Dashboard that acts.** "Needs your attention" tiles (overdue invoices, bills to pay, out of stock, low stock, expenses and stock adjustments to approve, sales orders to deliver, purchase orders to receive, open shifts), sales against the same weekday last week and the last 7 days against the 7 before, customers to chase, oldest supplier bills, top sellers. Each tile opens its list.
- **POS selling tools** (Sales menu and the till):
  - **Price lists and customer groups.** A customer in a group pays that group's price list: set prices per product (with quantity steps such as "from 10 units 800"), or move every other product up or down by a percentage; dates and an on/off switch; loaded from CSV with a check first.
  - **Promotions and coupons.** Percent off, amount off, or buy-some-get-some-free for a product, a category or the whole cart; dates, coupon codes, customer group, limits on uses (in all and per customer), minimum quantity or spend. The till has a coupon box and shows what the customer saved on the cart, the payment page and the receipt.
  - **Gift cards.** Sold at the till or in the back office, with a code, optional buyer, recipient and expiry. Paid for now, owed back as goods: in the books the money is a liability (Gift Card Liability), not sales, and becomes sales when the card is spent. Add money, block, set expiry, release the balance of expired cards into income. A card can pay all or part of a sale (mixed with cash and points); returns put the money back on the card.
  - **Loyalty points.** Points earned on what a named customer pays, spent at the till (each point worth a set amount, at most a set share of one sale). Switched off until you set the rules. Returns give back the points spent and take back the points earned. Opening balances can be loaded by an administrator.

Details of each step, with the API and the limits, are in the README (entry "Core improvements, version 1.5.0").

## Database

Six migrations were added after `V14__law_fee_collections.sql`; Flyway applies them in order on the next start. Delivered migrations are never edited.

| Migration | Step |
|---|---|
| `V15__core_collections.sql` | customer collections |
| `V16__core_opening_balances.sql` | opening balances |
| `V17__core_accounting_controls.sql` | accounting controls |
| `V18__core_price_lists.sql` | price lists and customer groups |
| `V19__core_promotions.sql` | promotions and coupons |
| `V20__core_gift_cards_loyalty.sql` | gift cards and loyalty points |

Paging, exports, papers and the dashboard change no table. Three accounts are added to the chart of accounts by the program itself, on existing installations too, with no migration: Gift Card Liability, Expired Gift Card Income and Customer Rewards Expense (Rwanda 2880 / 4160 / 5970, Chad 4195 / 7581 / 6285).

## Upgrading an installation that ran 1.4.0

Back up the database, install the new build and start it. Flyway applies V15 to V20. Nothing else is needed: the license is unchanged, and existing customers, invoices, bills, stock and ledger keep working. Price lists, promotions, gift cards, loyalty points and the collection reminders do nothing until you set them up (loyalty and the automatic reminders are off by default).

## Before the first customer build (NIWE), unchanged from 1.1.0

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties`.

## Tested, and not tested

Every step was tested against PostgreSQL 16 over the HTTP API and driven in a browser, and the whole set (all earlier modules included) was re-run after the last step with no failure: 76 result files, 0 failures. Core suites (checks passed, none failed) and browser walks: backend t51 to t60 (147, 196, 110, 64, 108, 118, 53, 113, 111 and 209 checks) and browser walks walk34 to walk43 (50, 31, 46, 24, 27, 40, 33, 44, 35 and 60 checks). The frontend type-checks (`ngc`) and builds.

Not tested: Flyway itself applying V1 to V20 on an empty database on your machine (here the migrations were applied with `psql` and checked with Hibernate's schema validation), a real SMTP server (a local test mail sink was used), the daily jobs firing by themselves (07:15 collection reminders; run through their endpoint), imports of very large files (the largest tried was a few thousand rows), a real bank statement, real data in volume, two tills at the very same moment (a gift card spent twice is guarded by a lock, but only tested one after the other), a license signed with your real key, and the real EFRIS services with gift-card or points payments (the words GIFT_CARD and LOYALTY are sent as the payment mode for those parts).

## Known limits of the new features

- Lists are built in full on the server and then paged; fine for thousands of rows, not for hundreds of thousands. A reconciliation shows at most 2000 ledger lines at a time.
- All cash, bank and mobile-money payments post to one Cash account in the ledger (unchanged); a reconciliation works on the accounts you choose.
- Promotions, gift cards and loyalty points are till features: they are not used on invoices, quotations or sales orders, and they cannot be used on a sale rung offline (the server refuses with a message; promotions are simply not applied to an offline sale, and price lists are applied when it syncs).
- A typed price on an invoice, quotation or sales order line is never replaced by a price list. Reports do not yet show how much a price list or promotion took off.
- A whole-cart "amount off" is spread over the lines as one percent, so a line can differ by a cent from an even split.
- Two checkouts using the last use of a limited coupon at the same moment are not guarded.
- Gift cards: one balance in the shop's single currency; a card cannot be moved between customers or merged. Loyalty points do not expire and are not accrued as a liability (the cost is booked when points are spent). Cash in plus other in on a shift's report can exceed the shift's sales by exactly the gift cards sold on it.
- The dashboard counts days as UTC days; returns on the till are not netted off its sales figures.
- Closing the books is not tested against the overnight jobs posting at the same moment.

## Commit and tag (run these yourself)

Nothing from 1.0.0 onwards was committed or tagged by me. Tag the new version: **v1.5.0**. Remove the delivery leftovers from the repository folder first: they are not part of the code.

```bash
cd "D:/2026/NIWE-GROUP/TECH LTD/ERP/codes/NIWE-POS"
rm -f niwe-*.tgz apply-*.py _law-src.tgz _core-be.tar _core-fe.tar
git add -A
git status                      # check the list first: no keys, no licenses, no .tgz, .tar or apply-* files
git commit -m "NIWE ERP 1.5.0: core improvements (paging, exports, collections, papers, checked imports and opening balances, accounting controls, dashboard, price lists, promotions, gift cards, loyalty)"
git tag -a v1.5.0 -m "NIWE ERP version 1.5.0"
git push origin main --follow-tags
```

If an earlier commit for 1.4.0 has not been made yet, the same commit simply carries both; tag v1.4.0 first on the earlier commit if you want both tags. The private license key and any `.niwelic` file or `licenses.csv` are ignored by git, but look for them in `git status` anyway.
