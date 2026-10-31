# NIWE ERP, version 1.22.2

First-start administrator and tidier error codes. Versions 1.0.0 to 1.22.1 stay the base. No migration, no screen changes.

## Why

A clean-install rehearsal (empty database, the committed code only, the production frontend build behind a proxy) found one blocker: with `app.seed-demo-data=false`, which is the setting for a real business, a brand-new database had **no users at all**, so nobody could sign in and there was no way in short of editing the database.

## What changed

1. **First-start administrator.** When demo data is off and the user table is empty, the system creates the administrator `admin`.
   - If `app.initial-admin-password` is set, that is the password.
   - If it is empty, a random 16-character password is made and written **once** to the log in a line that starts with `FIRST START`. Sign in and change it at once.
   - It happens only when there are no users, so a restart never creates a second account or changes a password. With demo data on (the default) nothing changes: `admin` / `admin123` and `cashier` / `cashier123`, as before.
2. **404 and 405 instead of 500.** A mistyped address under `/api` now answers *404 Not found*, and a known address used with the wrong method answers *405 Method not allowed*. They used to be reported as "Unexpected error" with status 500 and filled the error log.

## Settings

Add to `application.properties` (or pass on the command line) for the first start of a real installation:

```
app.seed-demo-data=false
app.initial-admin-password=choose-a-long-password
```

Remove the password line after the first sign-in; it is only read when there are no users. Without demo data there are also no payment methods, tax rates or registers: create them in *Settings*.

## Technical

- `DataSeeder.ensureInitialAdmin()` runs in the `!seedEnabled` branch only; `app.initial-admin-password` is read with a blank default.
- `ApiExceptionHandler`: handlers for `NoResourceFoundException` (404) and `HttpRequestMethodNotSupportedException` (405).
- Tests: the committed code from a clean checkout compiled, all 62 migrations applied to an empty database, started with demo data (8 products, 2 users, a second start changes nothing), started without demo data (generated password works, restart creates nothing, own password works, `admin123` is refused), unknown path 404, wrong method 405; production frontend build (no warnings) served behind a proxy with sign-in, dashboard and module pages; an invoice issued and paid with its journal entries. Regression after the change: `t33` 83/0, `t110` 288/0, `t112` 16/0.
- Not covered here: a real `mvn package` and Flyway itself (Maven Central cannot be reached from the test machine). The SQL migrations were applied with `psql` in order, and the application started with Flyway off and schema validation on.

## Found, not changed

- `application.properties` as committed points at the developer's own database (port 5434) with a real password in it. Change it for every installation, and consider moving the password out of git.
- A licence is needed for any module beyond Sales, Purchases and Accounting, including the tills (POS). This is by design.
