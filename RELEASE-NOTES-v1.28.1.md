# Release 1.28.1 - Demo data only for development

Until now one switch, `app.seed-demo-data`, decided everything, and it was on by default. With it on, a deployment got the accounts `admin` / `admin123` and `cashier` / `cashier123` and sample products. With it off, a new database got only an administrator, and no payment methods, register or walk-in customer. This release separates the two.

## Changed

- **Created on every first start, in every profile** (only what is missing): the administrator `admin` (only when there are no users at all), the payment methods (Cash, Card, Mobile Money, Bank Transfer, Store Credit), a register ("Front Counter") and the walk-in customer.
- **Created only in demo mode:** the sample cashier (`cashier` / `cashier123`, PIN 5678), 4 categories, 8 products and the Standard tax rate they use. A real installation gets no tax rate: the right one depends on the country, so set it in Settings > Taxes.
- **No fixed administrator password.** The administrator gets `app.initial-admin-password` when it is set, otherwise a random password written once to the log (look for `FIRST START`). In demo mode the administrator also gets the demo PIN 1234, when it has none.
- **`app.seed-demo-data` now defaults to `false`.** `application-dev.properties` sets it to `true` and sets `app.initial-admin-password=admin123`, so your usual development login is unchanged. `application-niwe.properties` is now `false`.
- **A warning in the log** at every start when the account `admin` or `cashier` still has its demo password, unless the dev profile is on, and when demo data is switched on outside the dev profile.

## Things to know

- Existing databases keep what they have. Seeding only fills what is missing, so nothing is removed or changed, except that a start with demo mode off no longer re-creates a demo `admin` or `cashier` that was deleted or renamed.
- A start with the old default (no setting at all) on a new empty database used to give demo data; it now gives the minimal set above.
- Any other deployment profile that sets `app.seed-demo-data=true` keeps demo data (and the demo `cashier` account) until you set it to `false`. This applies to a deployment's own private properties file too.
- Change the demo passwords on any installation that has them: the warning in the log lists them.

## Tested

- The seeder was run with real repositories against PostgreSQL 16 built from migrations V1 to V65, on an empty database in four situations: production (administrator with a random password, 5 payment methods, 1 register, the walk-in customer, and no categories, products, cashier or tax rate); development (adds the cashier, the PINs, 4 categories, 8 products and a tax rate); a second development start (nothing changes); production started on a database that has the demo accounts (the warnings appear); and demo mode outside the dev profile (the warnings appear).
- It compiles. Not tested: a full application start under Spring with Maven.
