# Release 1.24.0 - French for the remaining modules

## What is new

- **French for every module.** The screens of these modules can now be shown in French, in addition to the core screens of 1.23: Restaurant, Hotel, Manufacturing, Subscriptions, Installments, Help Desk, HR and Payroll, Time tracking, Work orders, Booking, Law firm, Real estate, Property management, Assets, Gift cards, Loyalty and Promotions, the client portal and the public property listings, My profile and the Invoice list. About 4,600 more phrases (about 6,840 in all).
- Nothing changes for people who use English. The language picker, the Business language setting and the Chad chart default work as in 1.23.

## Still in English

- Messages the screens build in code (for example some confirmation questions and error texts), messages that come from the server (for example "Customer not found"), e-mails and PDF documents.
- Labels held in lists inside the code (for example some status names in filters) in a few screens.
- A phrase that has no French yet simply shows in English, so nothing is blank.

The French wording was written without a native review: please have a French speaker read it before the first customer uses it.

## Technical

- Frontend only; no new migration. The files changed are the templates of the modules above, `tools/i18n/keys.json`, `frontend/src/app/core/i18n/fr.json` and the generated `fr.ts`.
- To translate more text, see `tools/i18n/README.md`.

## Upgrade

Rebuild the frontend (`npm install` first if you have not yet since 1.23) and the backend, replace the jar. Nothing else is needed.

## Checked / not checked

The frontend compiles (`ngc`). Only the sign-in page has been looked at in a browser in French; the other screens were converted by script and have not been clicked through. The Java part of 1.23 (migration V63 and the language setting) has still not been compiled here: run `mvn -q clean package` and start once to confirm.
