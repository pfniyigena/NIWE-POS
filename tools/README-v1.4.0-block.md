### Version 1.4.0 - Law Firm improvements, version 1.4.0

Seven phases, all inside the Law Firm module (see `RELEASE-NOTES-v1.4.0.md` for the summary, database and upgrade notes).

- **Paging and phone layout.** `page` and `size` on the matters, clients, contacts, deadlines and tasks lists; total in the `X-Total-Count` header; without them the whole list comes back as before.
- **Client money papers.** `GET /api/law/trust/{id}/receipt`, `GET /api/law/matters/{id}/trust/statement`, `POST /api/law/matters/{id}/trust/statement/email`, `GET /api/law/trust/reconciliation/{id}/print`.
- **Exports.** `GET /api/law/export/{matters|clients|contacts|deadlines|tasks|billing|trust-balances|trust-ledger|collections}?format=xlsx|pdf|csv`, same filters and access as the screens.
- **Calendar and limitation warnings.** `GET /api/law/calendar`, `GET /api/law/calendar/ics`, `GET /api/law/escalations`, `POST /api/law/escalations/send`; daily job about 06:25 (off by default); migration V13.
- **Matter papers.** `GET /api/law/matters/{id}/papers/checks`, `POST /api/law/matters/{id}/papers/{cover|engagement|closing}[/save|/email]`.
- **Fee collections.** `GET /api/law/collections`, `GET /api/law/collections/clients/{id}/statement`, `POST .../statement/email`, `POST /api/law/collections/remind`, `GET /api/law/collections/reminders`, `POST /api/law/collections/reminders/run`; daily job about 06:35 (off by default); migration V14.
- **Imports.** `GET /api/law/import/{clients|matters}/template`, `POST /api/law/import/{clients|matters}?dryRun&createClients&clearConflicts` (managers and admins; dry run by default).

Limits: full lists are built before paging; the calendar file is a download, not a subscription; only LIMITATION deadlines are escalated; imports read the first sheet only, up to 2000 rows, `.xlsx` or CSV.
