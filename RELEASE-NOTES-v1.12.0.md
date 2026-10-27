# NIWE ERP, version 1.12.0

Thermal printing for POS and Restaurant (Epson ESC/POS) and a customer display on a COM port. No server change, no migration. Versions 1.0.0 to 1.11.0 stay the base.

## What changed

1. **Thermal receipts.** The sale receipt, return receipt, restaurant bill / receipt and the X / Z shift reports print on 80 mm and 58 mm rolls: directly as ESC/POS from Chrome / Edge (serial / COM or USB), or through the Epson Windows driver with a layout made for the roll (exact page width, no margins, black bold text, no blank paper after the receipt).
2. **Cash drawer.** Opens with a cash sale (and a cash restaurant bill) in direct mode; *Open drawer* button on the till and on the Devices page.
3. **Restaurant tickets.** Kitchen and bar tickets per station on their own printers when items are sent to the kitchen; *Bill* (pre-bill) and *Tickets* (reprint) buttons on the order screen.
4. **Customer display on a COM port.** Two-line pole display: item and price, total, paid / change, thank you, welcome text; ESC/POS, CD5220 or plain text.
5. **Devices page** (POS > Devices): the printers and the display of this PC, with test page, drawer test, test message, paper width, characters per line, code page, cut, copies.

Details and the set-up steps are in `THERMAL-PRINTING.md`.

## Upgrading

Install the new build; nothing else. Every till then sets up its own devices once on POS > Devices (the settings live in that browser). Until it does, receipts print through the browser as before, now laid out for the roll set in POS settings > Receipt paper.

## Wiring the screens

After unpacking, run from the repository folder: `python3 tools/wire-v120.py frontend/src/app` (safe to run twice; it adds the Devices route, its access rule and its menu entry), then `python3 tools/apply-v1120.py`.

## Tested, and not tested

The whole set was re-run on a frozen copy of the code (all earlier suites plus the new ones). New: `unit84` (182 checks of the ESC/POS encoder, the receipt, ticket, bill and report builders at 48 and 32 columns, and the display frames), `walk84` (34 checks in the browser with fake COM and USB devices that record every byte: set-up and test pages, receipt text with customer tax ID, drawer pulse on a cash sale only, cut, COPY on a reprint and no drawer, fall-back to the driver when the printer fails, display following the cart, X report, return receipt, USB, 58 mm and 80 mm driver layout) and `walk85` (15 checks: three printers, one ticket per station on the right printer, notes, reprint, pre-bill, paid receipt with drawer).

Not tested: real Epson printers and a real pole display (only fake devices); Windows driver print preview on a real roll; WinUSB set-up.

## Known limits

- Needs Chrome or Edge on a secure address (`https://` or `http://localhost`) for direct printing and the COM display. The driver mode works everywhere.
- LAN printers (port 9100) are reached through their Windows driver only.
- The cashier-session report and the A4 documents still print on A4 through the browser.
- Logos print as black-and-white pictures; accents on the customer display are shown without accents.
