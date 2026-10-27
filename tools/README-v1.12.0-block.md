### Version 1.12.0 - Thermal (Epson ESC/POS) printing and the COM customer display

POS and Restaurant now print for thermal roll printers, and the till can drive a two-line customer display on a COM (serial) port. Full set-up guide in `THERMAL-PRINTING.md`, summary in `RELEASE-NOTES-v1.12.0.md`.

- **Devices page** (`/devices`, POS menu): per PC, in the browser. Receipt, kitchen and bar printers: Windows driver, USB (WebUSB) or serial / COM (Web Serial), 80 mm or 58 mm, characters per line, code page, cut, copies; test page; cash drawer button and pin.
- **Direct ESC/POS** (Chrome / Edge): sale receipt (with logos, EFRIS block and native QR), return receipt, restaurant bill and receipt, X / Z shift reports, kitchen / bar tickets per station, cash drawer pulse on a cash sale. A printer that does not answer falls back to the Windows driver with a message.
- **Windows driver mode** now prints exactly the roll width, black and bold, without margins or blank paper after the receipt (`core/hardware/thermal-css.ts`).
- **Customer display on a COM port**: item and price as rung up, then the running total, PAID / CHANGE and THANK YOU, welcome text when idle; ESC/POS (Epson DM-D and compatible), CD5220 or plain text; baud rate, data bits, parity and stop bits are settings.
- **Restaurant**: "Send to kitchen" can print one ticket per station on the printer that takes the station; buttons Bill (pre-bill) and Tickets (reprint) on the order screen.
- No server change and no migration.
