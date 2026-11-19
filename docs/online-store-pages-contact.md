# Online Store: contact form and information pages (v1.51.0)

## Information pages
- Slugs: `terms`, `privacy`, `returns`, `delivery`. Table `online_info_page` (slug, title, body, published, updated_by). **A null body means the starter text** from `OnlineInfoDefaults` (English and French), chosen by the visitor's language (`?lang=`). Once staff save, title and body are stored and shown as written to everyone.
- Placeholders `{shop}`, `{phone}`, `{email}`, `{address}` are replaced when a visitor reads the page (`OnlineInfoService.fill`): the shop name (else the business name), the shop's contact phone and e-mail (else the business e-mail) and the pick-up address. A missing detail leaves no stray separator.
- Text format: paragraphs separated by an empty line, `## ` heading, `- ` bullet. The shop shows it as plain text (never HTML).
- Public: `GET /api/public/store/info` (published pages for the footer), `GET /api/public/store/info/{slug}`. Staff (Operations area): `GET/PUT /api/online-store/info-pages[/{slug}]`, `POST /api/online-store/info-pages/{slug}/restore`. Saving and restoring are recorded in the audit log.
- Screens: `/shop/info/:slug` (`shop-info.component.ts`), staff `/admin/online-store/pages` (`store-pages.component.ts`). Checkout shows an agreement sentence with links to Terms and Returns when they are shown.

## Contact form and messages
- `POST /api/public/store/contact` {name, email, phone, subject, message, orderCode, language, website}. Rules: name required; e-mail (valid) or phone (6+ digits) required; message 5-2000 characters; `website` is a hidden field (filled = robot: answered "ok", not saved); 5 messages per hour per connection.
- Saved in `online_contact_message` (status NEW or ANSWERED, note, handled_by/at). The shop gets an e-mail (and the owner's SMS alert when set) through `OnlineMessages.notifyShopNote`; a visitor with an e-mail gets a confirmation with a copy.
- Staff: `/admin/online-store/messages` (`store-messages.component.ts`): `GET /api/online-store/messages?status=NEW|ANSWERED&q=`, `POST .../{id}/answered` {note}, `POST .../{id}/reopen`, `DELETE .../{id}`. `messagesNew` and `messagesAnswered` are part of the `pending-count` answer.

## Limits
No reply from inside the ERP, no attachments, one language per edited page, the starter texts are general (not legal advice).
