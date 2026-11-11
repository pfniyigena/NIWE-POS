# Release 1.26.0 - French for printed documents, e-mails and server messages

## What is new

- **Printed documents (PDF) follow the business language.** Invoices, quotations, sale orders, credit notes, receipts, supplier bills, statements of account, delivery notes, goods-received notes, stock counts and transfers, payslips and payroll papers, hotel bills and registration cards, tenant statements and fee invoices, real estate offers and agreements, law-firm letters and trust papers, instalment agreements and statements, manufacturing job cards, the modules list and report exports are written in French when the business language (Settings > System settings) is Français. About 790 texts.
- **E-mails follow the business language.** Reminders, statements, payslips, booking and reservation messages, help desk, subscription, hotel, client portal, password reset and alert e-mails, in subject and body. About 900 texts.
- **Messages returned by the server** (for example "Customer not found", "The deposit must be more than zero") are in French too.
- **Dates and amounts** on papers and e-mails are written the French way (5 mars 2026, 1 234,50); English keeps the old style.
- The language is read from the business settings and kept for 10 seconds, so a change in Settings reaches papers and e-mails almost at once. It is the language of the **business**, not of the user who clicks, so a customer always receives a document in the business language.

## Not translated on purpose

- Audit trail entries, accounting journal descriptions, names already stored in the database (for example the "Receiving" and "Main Stock" locations) and codes.
- A few messages that end with a status word (for example "This order is already ...") stay fully in English rather than mixing languages.
- A text with no French version simply shows in English.

The French wording was written without a native review: please have a French speaker read it before customers receive it.

## Technical

- Backend only; no migration. New `com.posapp.i18n.I18n` (`I18n.t("English text")`, `I18n.t("... {0}", value)`, `I18n.symbols()`, `I18n.longDay()`, `I18n.shortDay()`, `I18n.pattern(...)`). French texts live in `src/main/resources/i18n/fr.json`, with the English text as the key.
- About 2,300 string literals in 53 PDF and e-mail classes are now wrapped with `I18n.t(...)`.
- `SubscriptionAutomationService.java` was read-only on disk; it was replaced and the old copy kept as `SubscriptionAutomationService.java.readonly-backup` (not compiled; delete it when you are happy).

## Upgrade

Rebuild the backend. Nothing else is needed.

## Checked / not checked

The changed classes compile here with JDK 21 against your compiled classes and the project libraries, and the French helper was run (text, dates, amounts). The full Maven build, a real PDF and a real e-mail in French have **not** been run: please run `mvn -q clean package`, set the language to Français, print an invoice and send one reminder.
