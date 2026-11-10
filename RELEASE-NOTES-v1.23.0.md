# Release 1.23.0 - French (Français), default set by the chart of accounts

## What is new

- **French screens.** The core screens can be shown in French: sign-in, the menus and top bar, the dashboard, POS terminal, receipts, returns, registers and shifts, Sales, Customers, Purchases, Suppliers, Inventory (products, stock, warehouses), Accounting (chart, journals, reports, controls, expenses, incomes), and Settings. About 2,200 phrases are translated.
- **The chart of accounts sets the default.** Choosing the **Chad** chart makes **French** the business language; the **Rwanda** chart keeps English. Like the currency, this happens only while the language is still the untouched default (English), so a language chosen by an administrator is never overwritten. The page reloads straight after the chart is confirmed, so the first screens are already in French.
- **Business language** in **Settings > System settings** (English or Français) - changes the default for everyone who has not chosen their own.
- **Language picker** (English / Français) in the top bar and on the sign-in page. A person's own choice is kept in that browser and wins over the business default. Dates and numbers follow the language (for example 12 novembre 2026, 1 234,50).

## Not yet translated

These modules are still in English and will follow: Restaurant, Hotel, Manufacturing, Subscriptions, Installments, Help Desk, HR and Payroll, Time tracking, Work orders, Booking, Law firm, Real estate, Property management, Assets, Gift cards, Loyalty and Promotions. Messages that come from the server (for example "Customer not found"), the e-mails and the PDF documents are still in English. A phrase that has no French yet simply shows in English.

The French wording was written without a native review: please have someone in Chad read it before the first customer uses it.

## Technical

- Backend: migration `V63__language.sql` (`store_settings.language`, `en` or `fr`, default `en`); `GET /api/public/settings/branding` also returns `language`; `SystemSettingsRequest.language` is optional; `AccountService.applyCountryLanguage` runs when the chart is seeded.
- Frontend: `@ngx-translate/core` 16.0.4 (run `npm install`). The English text in a template is the key (`{{ 'Save' | translate }}`), so English needs no file and a missing French text shows English. French is in `src/app/core/i18n/fr.json`; `node ../tools/i18n/build-fr.mjs` turns it into `fr.ts`.
- To translate another module: `node ../tools/i18n/transform.mjs <its .component.ts files>` (turns its templates into keys and lists what it could not), add the phrases to `fr.json`, run `build-fr.mjs`. See `tools/i18n/README.md`.

## Upgrade

Run `npm install` in `frontend`, rebuild both parts, replace the jar. The migration runs by itself. An existing Chad installation keeps English until an administrator picks French in Settings > System settings (or each person picks it in the top bar).

## Checked / not checked

The frontend compiles (`ngc`, `ng build`) and the sign-in page was tested in a browser in English and French, with the business default, the person's own choice and the picker. The Java part was **not** compiled and the migration was not run (Maven Central and the project libraries were not reachable): run `mvn -q clean package`, start once with a Chad database, and check that choosing the Chad chart gives French.
