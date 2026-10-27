# NIWE ERP, version 1.11.0

Three requested changes before the next module. Versions 1.0.0 to 1.10.1 stay the base.

## What changed

1. **Customer tax ID on the POS receipt.** When the customer on the sale has a tax ID (TIN), the receipt shows "Customer tax ID: ..." under the customer details. The emailed receipt PDF shows the same line.
2. **Low-stock products are paged on the Dashboard.** Both lists (all products and per warehouse) show 10 rows per page with a pager. The paging is done on the screen; the dashboard summary still returns the full list.
3. **Promotions, Gift cards and Loyalty points are switched on in Sales > Settings** (Features box, administrator only). When a feature is off:
   - its menu entry, the till buttons (gift card, coupon) and the checkout boxes (gift card, loyalty) are hidden;
   - the server refuses new use: creating or changing promotions and coupons, selling or topping up gift cards, paying with a gift card; promotions are not applied to orders; loyalty earns and redeems nothing;
   - existing promotions, cards, points and refunds are kept and can be read.
   Switching a feature is written to the audit log (`SALES_FEATURE_CHANGED`, `LOYALTY_SETTINGS_CHANGED`). The Loyalty page no longer has its own on/off checkbox; it shows a note with a link to Sales > Settings.

## API

- `GET /api/sales/features` (any signed-in user): `{promotions, giftCards, loyalty}`.
- `GET/PUT /api/sales/settings` (administrator, same path as before). In the PUT body `promotionsEnabled`, `giftCardsEnabled`, `loyaltyEnabled` and `invoiceReceiverEmails` are optional; a field left out stays as it is.

## Database

| Migration | Content |
|---|---|
| `V51__sales_feature_switches.sql` | `promotions_enabled` and `gift_cards_enabled` on `sales_settings`; creates the settings row if missing |

## Upgrading an installation that ran 1.10.x

Back up the database, install the new build and start it. After the upgrade Promotions are **on** if any promotion exists, Gift cards are **on** if any gift card exists, and Loyalty keeps its current setting, so nothing stops working by itself. On a new installation all three start off.

## Wiring the screens

After unpacking, run from the repository folder: `python3 tools/wire-v110.py frontend/src/app` (safe to run twice; it now also hides the three menu entries while switched off), then `python3 tools/apply-v1110.py`.

## Tested, and not tested

The whole set (all earlier modules included, about 150 suites over the HTTP API and in a browser) was re-run on a frozen copy of the code. New suites: `t101` (31 checks: switches, blocking, receipt tax ID, emailed PDF, low-stock data) and `walk83` (18 checks in the browser: receipt line, dashboard paging, Features box, menu, till). Two older loyalty tests were changed because the Loyalty page no longer has its own switch.

Not tested: Flyway applying V1 to V51 on an empty database on your machine, real receipt printers.

## Known limits

- Dashboard paging is on the screen; with thousands of low-stock products the summary still sends them all.
- Loyalty is read from its own settings row; Sales > Settings only changes its on/off value.
