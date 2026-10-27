# Thermal printers and the COM customer display

Version 1.12.0. This guide sets up an Epson (or compatible ESC/POS) thermal printer and a two-line customer display on a till.

## How it works

The settings are made **per PC, in the browser** (menu POS > Devices, `/devices`) because every till has its own hardware. Nothing is stored on the server and nothing changes in the database.

Each printer (Receipt, Kitchen, Bar) has a connection:

| Connection | What it does | Needs |
|---|---|---|
| **Windows driver** | The normal print dialog, now laid out for the roll: page exactly 80 mm or 58 mm wide, no margins, black bold text, no minimum page height (no blank paper after the receipt). | Any browser. Install the Epson driver and choose the roll size. In the driver tick *Document cut* and *Open drawer*. |
| **Serial / COM** | Sends raw ESC/POS: native text, logo, QR code, cut, cash drawer. | Chrome or Edge on this PC, on `https://` or `http://localhost`. A printer with an RS-232 port, or a virtual COM port (Epson *TM Virtual Port Driver*). |
| **USB** | Same raw ESC/POS over WebUSB. | Chrome or Edge. The printer must use the **WinUSB** driver (for example with Zadig); with the normal Epson driver Windows keeps the printer and the browser cannot open it. |

Chrome / Edge ask once to allow each device (click *Connect device...* and pick it). The permission is remembered for the site.

If a direct printer does not answer, the document prints through the Windows driver and a red line on the page says why.

## Setting up a till

1. Open **POS > Devices**.
2. **Receipt printer**: choose the connection, click *Connect device...*, pick the printer. For COM choose the baud rate set on the printer (often 9600, 19200 or 115200; the printer self-test page shows it).
3. Under **Paper and options**: paper width (as in POS settings, or 80 / 58 mm), characters per line (0 = 48 on 80 mm, 32 on 58 mm; lower it if text wraps), code page (PC858 shows accents and the euro sign; use *Plain ASCII* if the printer prints strange characters), cut after each document, open the cash drawer on a cash sale, drawer pin (pin 2 is the usual), copies.
4. Press **Print test page** (it prints the characters-per-line ruler, accents and a QR code) and **Open cash drawer**.
5. Press **Save**.

### Restaurant

- **Kitchen printer** and **Bar printer** are optional. In *Stations on this printer* write the station names as they are on the menu items (for example `Grill, Cold` on the kitchen printer and `Bar` on the bar printer). A kitchen printer with no names takes every station not claimed by another printer.
- Tick *Print kitchen / bar tickets when items are sent to the kitchen*. "Send to kitchen" (and "Fire course") then prints one ticket per station on its printer: station name, table, order number, server, guests, courses, quantity and name in large type, choices, notes and allergy warnings. No prices.
- On the order screen: **Bill** prints the bill for the guest before payment (marked "not a receipt", never opens the drawer); **Tickets** reprints the tickets of everything still in the kitchen (marked REPRINT). Without a direct kitchen printer these use the print dialog.
- The paid receipt (Receipts > Print) goes to the receipt printer and opens the drawer for a cash bill; a reprint says COPY and leaves the drawer shut.

### What prints where

| Document | Printer |
|---|---|
| Sale receipt (and its COPY n reprints) | Receipt |
| Return receipt | Receipt |
| Restaurant bill, pre-bill, paid receipt | Receipt |
| X report, Z report (shift close) | Receipt |
| Kitchen / bar tickets | Kitchen / Bar |
| Cash drawer pulse | Receipt printer (cash sale, restaurant cash bill, *Open drawer* button on the till) |

Not on the thermal printer in this version: the cashier-session report and the other A4 reports and documents (they print through the browser on A4).

## Customer display on a COM port

On the same page, *Customer display (COM)*:

1. Tick *Use a customer display on this PC*, click *Connect COM port...* and pick the port.
2. Command set: **ESC/POS** (Epson DM-D and compatible, cursor positioned with `US $`), **CD5220** (Logic Controls, Aedex: `ESC Q A` / `ESC Q B`) or **Plain text**.
3. Baud rate, data bits, parity, stop bits as set on the display (usual: 9600, 8, none, 1). Characters per line: 20 for a 2 x 20 display.
4. Welcome lines shown when the till is idle.
5. *Send test message*.

On the till the display shows: the item just added (name on line 1, `qty x price` and the line total on line 2), after about two and a half seconds the running total, the total when you switch to another open cart, `PAID` with `THANK YOU` (or `CHANGE`) after the sale, and the welcome text again a few seconds later or when the cart is emptied. Only plain ASCII goes to the display (accents are shown without them).

## Limits and things to know

- Direct printing and the COM display need **Chrome or Edge**, and a secure address (`https://`, or `http://localhost`). On plain `http://192.168...` the browser hides Web Serial and WebUSB; the Windows driver mode still works.
- One browser tab at a time can hold a COM port. If the customer display is open in another tab or program, the till shows the error on the Devices page and keeps working.
- A network (LAN) printer on port 9100 is not reached directly from the browser. Use its Windows driver, or tell us if you want a small service for it.
- Logos are printed as black-and-white pictures (at most 384 dots wide); big or very light logos look best with strong contrast.
- Real printers were not available while this was built. The ESC/POS bytes (initialise, code page 858, text, size, bold, alignment, QR, raster logo, cut `GS V 66`, drawer `ESC p`) and the display frames were checked byte by byte against fake USB and COM devices in the browser; try the test page on your own printer and tell us if a model needs a different setting.
