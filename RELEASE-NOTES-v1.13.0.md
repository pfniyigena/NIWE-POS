# NIWE ERP, version 1.13.0

A till (register) can be tied to one known device. Versions 1.0.0 to 1.12.0 stay the base. One small migration (V52).

## What changed

1. **Bind to this device.** On *POS > Registers & Terminal* an administrator sitting at the till PC presses **Bind to this device**, gives the PC a name ("Front counter PC") and chooses whether the PC's address (IP) must also match. From then on the till belongs to that PC.
2. **Other devices are blocked, by the server.** On any other device (or from the right device at another address) the terminal shows *"This terminal belongs to another device"*, and the server refuses: opening a shift, entering the terminal (PIN or switch cashier), starting or changing a sale (lines, discount, customer, coupons, park, discard, checkout), offline-sale sync and cash movements (paid in / out / drop). There is no administrator bypass; the way round is to unbind.
3. **Unbind.** *Unbind* (administrator, from any device) frees the till so it can be bound again, for example when the PC is replaced or its browser data was cleared.
4. **Still open from the back office.** Registers list, shift and cash reports, receipts and order history, who worked a shift, closing a shift. A manager can look after a till from any PC.
5. **One device, one till.** A device can be the till of one register only; binding it to a second is refused with a message.
6. **Audit log.** *REGISTER_DEVICE_BOUND* and *REGISTER_DEVICE_UNBOUND*. Refused attempts are written to the server log.
7. **Tills that are not bound behave exactly as before.**

Printer and customer-display settings stay per PC (Devices page), as in 1.12.0.

## How the device is recognised

The browser keeps a random device id (key `niwe.deviceId.v1`) and sends it with every request (`X-Device-Id`). The address is the one the server sees. Behind a web server or proxy on the same machine the server believes `X-Forwarded-For` (only when the request comes from `127.0.0.1` / `::1`). For another proxy host add it to `application.properties`:

    app.device-binding.trusted-proxies=127.0.0.1,::1,10.0.0.2

`127.0.0.1` and `::1` count as the same address (a browser on the server machine itself can arrive as either). With `ng serve` and a proxy in front of the API, every request reaches the server from the same machine; set `"xfwd": true` in the proxy configuration so the real address is forwarded, or bind with *Also require this address* switched off (the device id is still checked).

## Good to know

- This is an operational control, not hard security: the device id lives in the browser, so someone who can copy that browser's site data could pose as the till PC. It stops the everyday case (the same till opened on a second PC, a phone, another office).
- Clearing the browser's site data on the till PC gives it a new id: an administrator then unbinds and binds again.
- If the PC gets a new address (DHCP), the till is refused until it is bound again, or bind it with the address check switched off / give the PC a fixed address.
- A till bound while its shift is open keeps its shift; only the device allowed to work it changes.

## Upgrading

Unpack, run `python3 tools/apply-v1130.py` from the repository folder (version numbers and README), then build and start as usual; Flyway adds V52. No wiring script is needed.

## Tested

The whole set was re-run on a frozen copy (all earlier suites plus the new ones). New: `t102` (55 checks over the API: binding rules, wrong device, wrong address, no device id, address not checked, a device that is the till of one register only, every blocked till call, the calls that stay open, unbinding, audit trail, a forwarded address from an untrusted caller is ignored) and `walk86` (24 checks in two separate browsers: binding from the Registers page, the "This device" / "Another device" display, no terminal link for the other device, the blocking message on the terminal, Open session refused with a message, unbinding from another PC).
