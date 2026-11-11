# Release 1.25.0 - French for messages, lists and report headings; fix for texts with values

## What is new

- **Messages written in the program code are translated.** About 1,270 error messages, "Are you sure?" questions and notices (for example "Could not save the contract", "Delete this lead? This cannot be undone.") now follow the chosen language in every module.
- **Lists written in the code are translated**: status filters, weekdays, permission names and their explanations, document and charge types, colour themes, hotel and restaurant sections, and similar (about 190 phrases).
- **Reports.** The titles and the column headings of every report, the report section names and the report filter labels are translated (about 350 new phrases; about 8,640 French texts in all).

## Fixed

- **Texts that carry a value showed the raw `{p0}` in English** (and in French wherever a phrase had no translation yet), for example "{p0} days left" instead of "3 days left". They are now filled in correctly. This affected the screens translated in 1.23 and 1.24, so upgrade from those.

## Still in English

- Messages that come from the server (for example "Customer not found"), e-mails and PDF documents.
- A few texts that screens build from several pieces, and field names inside the exported Excel / PDF files made by the server.
- A phrase with no French simply shows in English.

The French wording was written without a native review: please have a French speaker read it before the first customer uses it. The words "asset" (actif / immobilisation) and a few other terms are not yet used in a single way everywhere.

## Technical

- Frontend only; no migration. `core/i18n/t.ts` adds `T('English text', { p0: value })` for code that is not a component; `translate-setup.ts` adds `InterpolatingMissingHandler`.
- `tools/i18n/transform-ts.mjs` converts messages in `.ts` files (`this.error = '...'`, `confirm('...')`, `this.run(..., '...')`); with `I18N_LABELS=label,hint` it also converts label lists (in a module-level list the label becomes a getter so it is translated when read). See `tools/i18n/README.md`.
- Report titles and column labels stay as plain text in `report-defs.ts`; the report screens show them with `| translate`.

## Upgrade

Rebuild the frontend and the backend. Nothing else is needed.

## Checked / not checked

The frontend compiles (`ngc`). Only the sign-in page has been looked at in a browser in French; the other screens were converted by script and not clicked through. The Java part of 1.23 (migration V63, the language setting) has still not been compiled here: run `mvn -q clean package`.
