# Release 1.38.0 - Online Store: send the newsletter from the program

**Online Store > Send a newsletter** (also a button on the Newsletter screen) lets staff write and send the shop's newsletter without an outside mailing tool.

- **Write**: a subject, a plain-text message (a blank line starts a new paragraph; nothing typed is treated as HTML), and an optional button (text + a link starting with `https://`, `http://` or `/`).
- **Audience**: everyone who confirmed, only English readers or only French readers (the language chosen at sign-up). The count is shown.
- **Test first**: **Send me a test** sends the real e-mail to an address you type, with [TEST] in the subject.
- **Send**: the list is fixed when sending starts (one delivery per address; the database refuses a second delivery of the same newsletter to the same address, and a started newsletter cannot be started again). Every e-mail has the shop name and the person's own unsubscribe link at the bottom, in the person's language. Someone who unsubscribes (or is removed) before their turn is skipped.
- **Batches and limits**: a job runs every minute and sends a batch (`app.online-store.newsletter.batch-size`, default 25) within a limit per 24 hours (`app.online-store.newsletter.daily-limit`, default 500, 0 = no limit). After five failures in a row it pauses and shows the reason.
- **Follow it**: a progress bar and the numbers Sent, Waiting, Failed, Skipped; the page updates by itself. **Stop sending** stops the rest (what was sent stays sent). Failed addresses are listed with the mail server's reason and **Try the failed ones again** re-queues only those.
- **No double sending**: an e-mail is marked as taken before it is handed to the mail server, so a crash can lose one e-mail but never sends one twice; a delivery stuck for more than 10 minutes is shown as failed ("Interrupted").

New: migration `V74__online_store_newsletter_sending.sql` (tables `newsletter_campaign`, `newsletter_delivery`; runs by itself at start-up), `NewsletterCampaignService`, `NewsletterSendJob`, endpoints under `/api/online-store/newsletter/campaigns` (view = Operations view, write = Operations manage), the screen `/admin/online-store/newsletters`, French texts (screens and server). Not included: pictures in the message, scheduling for a later time, sending to a chosen few, open/click tracking.

Checked on PostgreSQL (55 checks): validation of subject, message, button and audience; HTML escaping; test e-mail; the list fixed at start; batches of 3; one copy per address; each person's own unsubscribe link (French footer for French readers); skipping people who unsubscribed or were deleted; failures listed with the reason and retried without re-sending to others; five failures in a row pause the batch; daily limit across days; cancel; stuck deliveries; the unique constraint. The Angular screen files were checked with a strict compile. Not run in a browser, and not tried against a real mail server.
