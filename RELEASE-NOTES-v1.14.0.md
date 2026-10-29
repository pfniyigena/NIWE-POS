# NIWE ERP, version 1.14.0

Logging, the business name on module requests, and the currency that follows the chart of accounts. Versions 1.0.0 to 1.13.0 stay the base. One migration (V53).

## What changed

1. **Server log files.** The server writes three rotating files into the `logs` folder (next to where it is started):
   - `niwe-erp.log` - everything the application logs (INFO and above),
   - `niwe-erp-error.log` - warnings and errors only, kept longer,
   - `niwe-access.log` - one line for each API call that changed something, failed (status 400 and above) or was slow. Only the method, path, status, time taken, user, address and device are written: never the query string, the request body, passwords or tokens.

   Every line carries a short request id and the user: `[3f9a12bc|admin]`. The same id is sent back in the `X-Request-Id` header and in the body of an error answer (`requestId`), so a problem a user reports can be found in the log by its id. Unexpected errors are logged with the full stack trace (before, some were swallowed).
2. **View the logs in the app.** *Settings > Server logs* (administrator): pick a file, show the last 100 to 2000 entries, filter by level or text, auto-refresh every 5 seconds, or download the whole file. Only real `.log` files of the log folder can be opened (no paths); each download is written to the audit log (`SERVER_LOG_DOWNLOADED`).
3. **Sign-in log.** *Settings > Sign-in log* (administrator): every sign-in and every failed attempt (wrong password, unknown user, switched-off or locked account, not a staff account) with time, user name, reason, address and device, filters (user name, result, dates) and tiles for the last 24 hours (signed in, failed, addresses with failures). Passwords are never stored. Text that is not a plausible user name is stored as "(not a user name)" so the log cannot be filled with junk or scripts.
4. **Audit log coverage.** New entries: customer created/updated (including credit switched on/off and limit changes), customer payment recorded, category and brand created/updated/deleted, data imported (products, customers, opening stock, suppliers, categories, brands, balances, price lists) and exported (products, customers, year-end), POS shift opened, cashier working session started/closed, offline sale synced, time tracking tasks/rates/entries/submissions, own profile updated, help desk settings updated.
5. **Business name on module requests.** The request text and the e-mail subject now name the business (from *Settings > Business details*): `NIWE ERP module request: Booking - Kigali Fresh Mart Ltd (installation id)`. If no name was entered, the text says so.
6. **Currency follows the chart.** Choosing the Rwanda chart when the store is first set up sets the currency to RWF (shown as RWF); the Chad chart sets XAF (Central African CFA franc). This happens only while the currency is still the untouched default (USD shown as $), so a currency someone already chose is never replaced. Existing stores: migration V53 changes a Rwanda or Chad store that still has the untouched default; otherwise nothing changes. The currency can still be changed in *Settings > System settings*. Note that RWF and XAF have no cents but amounts are still shown with two decimals, as before.

## Settings (all optional, `application.properties`)

    app.logging.dir=logs
    app.logging.retention-days=30
    app.logging.error-retention-days=90
    app.logging.max-file-size=20MB
    app.logging.total-size-cap=2GB
    app.logging.level=INFO
    app.logging.slow-request-ms=2000

`logging.level.com.posapp.order=DEBUG` and similar still work for a single package. A `logging.file.name` setting of your own is not used any more: the files above replace it.

## Good to know

- The logs folder is added to `.gitignore` by the apply script. Old files are deleted after the retention days or when the total size cap is reached.
- Failed sign-ins are recorded in their own transaction, so they are kept even though the request fails.
- A cashier or any other non-administrator gets nothing from the two new endpoints or screens.

## Upgrading

Unpack, run `python3 tools/apply-v1140.py` from the repository folder (version numbers, README, `.gitignore`), then `python3 tools/wire-v140.py frontend/src/app` (adds the two routes and the two menu links; safe to run twice), then build and start as usual; Flyway adds V53.

## Tested

The whole set was re-run on a frozen copy. New: `t103` (86 checks over the API: sign-in log rows, filters, paging, masking, access rules; request id and access log content, no query string or token in the log; log file list, tail, level and text filters, caps, path tricks refused, download and its audit entry, access rules; new audit entries; currency on a Rwanda chart), `t103b` (currency on a Chad chart and a typed currency kept), the licence-request suite `t26` (business name in the request text, subject and mail) and `walk87` (20 checks in the browser: both pages, menu links, tiles, filters, search, download, a cashier turned away).
