# Release 1.36.0 - Online Store: uploaded banner pictures with a slideshow, a sliding Featured row, no Popular categories

- **Popular categories removed** from the shop's front page (the category buttons above the product list stay).
- **Banner pictures are uploaded** in Online Store > Shop settings > Front page > Banner pictures, instead of typing an image address. Upload **up to 8 pictures** (JPEG, PNG, WebP or GIF, 8 MB each; several at once), put them in order with the arrow buttons, remove one with Remove. A picture is checked by its content (a renamed file is refused); a JPEG or PNG wider than 1920 pixels is shrunk when it is added; a GIF or WebP is kept as uploaded.
- **The banner slides**: the pictures move from right to left every 5 seconds with a smooth slide, with arrows, dots, and swipe on a phone. It stops while the visitor points at it or the browser tab is hidden. With one picture it stays still; with none, the banner is a plain colour. The title, text and button stay in front. Visitors who set "reduce motion" in their system get no automatic movement (arrows and dots still work).
- **Featured products slide left and right**: the row is a rail that moves by itself every 3.5 seconds (back to the start after the last product), with arrow buttons and finger/trackpad scrolling; it stops while pointed at or touched. The other three rows (Best-selling, Special offers, New arrivals) stay as grids.
- The old "banner picture address" field is gone; an address saved there by v1.35.0 is no longer used.

## Technical
- Migration **V73**: table `online_banner` (picture bytes in the database, like other attachments). Staff API `GET/POST /api/online-store/banners`, `DELETE /banners/{id}`, `POST /banners/{id}/move?direction=up|down` (view / manage rights as the rest of the module); public picture `GET /api/public/store/banners/{id}` (only while the module is on, cached 1 hour). `GET /api/public/store/home` returns `banners` (addresses in order) instead of `categories`.
- Backup size grows with the pictures (at most 8, usually well under 1 MB each after shrinking).

## Checked
- Backend: 95 checks on PostgreSQL 16 with all migrations through V73 (upload, shrink, wrong type, empty and oversized files, limit of 8, order, move, delete, thumbnails, home list). Frontend: strict type-check passes.
- **Not done**: Maven build; a run in a browser (the slide and rail movement, and how the banner looks with your own pictures, need a look).
