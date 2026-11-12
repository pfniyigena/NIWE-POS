# Release 1.34.1 - Online Store: the shop is the front page

When the **Online Store module is on**, the root address of the site (`/`) now opens **the shop** for a visitor who is not logged in, instead of the general home page. The shop has a **Staff Login** link (top right, and again at the very bottom of the page, which also shows when the shop could not load), so staff reach the login page from there.

- A logged-in user is not affected: opening `/` still sends them to their usual page.
- With the Online Store module **off**, nothing changes: `/` shows the home page as before.
- If the module settings cannot be read, the home page shows (the safe fallback).
- The other public pages (table reservation, help desk, booking, rooms, listings) stay reachable at their own addresses (`/reserve`, `/helpdesk`, `/book`, `/stay`, `/listings`); with the shop as the front page they are no longer linked from the first page. Say if you want a link to them on the shop.
- Frontend only: no backend change, no database change. A new route guard `shopFirstGuard` (`core/auth.guard.ts`) on the root route; `shop.component.ts` gets the link. No new texts (Staff Login already has its French).
- Checked: strict type-check passes. Not run in a browser.
