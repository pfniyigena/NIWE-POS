# NIWE ERP, version 1.1.0

Module licensing. No new business module; version 1.0.0 stays the base (see `RELEASE-NOTES-v1.0.0.md`).

## What changed

- **Signed license files decide which modules an installation can use.** Sales, Purchases and Accounting are core. Every other module needs to be in the license and then activated by an admin. Details and the rules: README, "Module licensing".
- **Settings > Modules** shows, for each module, Core, Active, In your license (activate it), Locked (Request), Requested (waiting for NIWE, withdraw) or License expired (Request renewal); the installation id with a Copy button; the license holder, number and expiry; an upload button; and the list of installed licenses.
- **Requests.** An admin presses Request; NIWE gets an e-mail (or the admin gets text to send) and answers with a new license file.
- **Server-side lock.** A request to a module that is not licensed gets HTTP 403 for the endpoints that belong to that module. The Subscriptions daily job now also needs its module to be usable.
- **License tool for NIWE** in `tools/license-tool`.
- **Database:** `V2__module_licensing.sql` adds `app_installation`, `app_license` and `module_request`. V1 is unchanged.

## Before the first customer build (NIWE)

1. `java tools/license-tool/LicenseTool.java keygen --dir <a folder outside the repository>`
2. Paste the printed public key line into `backend/src/main/resources/license/public-key.b64` (below the comments) and build. Back the private key up in two places.
3. Set `app.license.vendor-email` in the customer's `application-*.properties` to the address that should receive module requests.

## Upgrading an installation that ran 1.0.0

- The database gets V2 on the next start. Because no license exists yet, **every optional module locks until a license is installed**. Before upgrading, note the installation id (it is created at the first start of 1.1.0), have NIWE issue a license that lists the modules the customer uses, upload it, and the activated modules work again without being re-activated.
- For development, run with `-Dspring-boot.run.profiles=niwe,dev` (the `dev` profile switches the license check off). Never use the `dev` profile in a deployment.

## Tested, and not tested

Tested against PostgreSQL 16 over the HTTP API (the 1.1.0 suite is described in the project notes): clean install, a tampered or foreign or expired or old or wrong-installation license, missing requirements, unknown modules, the lock on 23 module endpoints, open shared endpoints, request, e-mail, withdraw and fulfil, restart, a license row edited in the database, the developer switch, and a renewal after expiry. The Property Management suites were re-run with the developer switch on. The frontend type-checks (`ngc`) and the Modules screen was driven in a browser.

Not tested: Flyway applying V2 on your machine (as for V1, applied with psql here), a real SMTP server, a phone layout, and a license signed with your real key (the tests used a throw-away key).

## Commit and tag (run these yourself)

Version 1.0.0 was never committed or tagged, and 1.1.0 builds on the same uncommitted work, so tag one version: **v1.1.0** (the 1.0.0 notes stay as history). The private key and any `.niwelic` file or `licenses.csv` are ignored by git, but check `git status` for them anyway.

```bash
cd "D:/2026/NIWE-GROUP/TECH LTD/ERP/codes/NIWE-POS"
git rm -f backend/_verify-src.tgz
git add -A
git status                      # check the list first: no keys, no licenses
git commit -m "NIWE ERP 1.1.0: Law Firm, Real Estate, Property Management, single baseline migration, module licensing"
git tag -a v1.1.0 -m "NIWE ERP version 1.1.0"
git push origin main --follow-tags
```
