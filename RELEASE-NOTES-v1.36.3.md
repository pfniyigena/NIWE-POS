# Release 1.36.3 - Online Store: an unsubscribe link for every newsletter address

The file downloaded from **Online Store > Newsletter > Download confirmed addresses (CSV)** now has a fourth column, **unsubscribe_link**: the person's own link (`<app.frontend-url>/shop/newsletter/unsubscribe/<token>`). A mailing tool puts it in every message it sends; opening it unsubscribes that person at once, and they no longer appear in the next download. Download the list again before each mailing.

Set `app.frontend-url` to the public address of the site, or the links point to the wrong place. The program still sends no newsletters itself.

Backend only (`NewsletterService.csv()`); no migration, no new texts. The manuals, wiki and quick cards were updated in the same release (User Manual ch.23 Newsletter, Administrator Manual ch.7 and ch.14). Checked on PostgreSQL (99 checks): each confirmed address has its own link, the link works, and an unsubscribed address leaves the export.
