# Progressive Web App (installable app)

## What the user gets
- An "Install the app" card (Chrome, Edge, Android) or a hint (iPhone: Share > Add to Home Screen). The app then has its own icon and window.
- The shop (`/shop`) is installed with the shop's own name and colour; the staff screens are installed as "NIWE ERP" (start page `/`).
- Faster start and an "offline" notice when the connection drops. **Selling and saving still need the connection**: no data from `/api` is kept by the worker.

## Files
- `frontend/src/pwa/manifest.webmanifest` - name, icons, colours, start page. Shown to the browser from the site root.
- `frontend/src/pwa/sw.js` - the service worker. Change `VERSION` at its top to make every device drop its old copies.
- `frontend/src/pwa/offline.html` - plain page shown when a page was never opened on the device and there is no connection.
- `frontend/src/pwa/icons/*` - icons (the NIWE "N"). Replace the PNG files (same names and sizes: 192, 512, maskable 512, apple-touch 180, favicon 32) to change the look.
- `angular.json` copies `src/pwa` to the root of the build; `index.html` links the manifest, theme colour and icons.
- `core/pwa.service.ts` registers the worker (not in a development build), keeps the install offer, watches offline / updates, and names the installed shop (`useShopManifest`). `shared/pwa-bar.component.ts` shows the strip and the cards.

## What the worker keeps
| Request | Behaviour |
|---|---|
| Page (navigation) | network first; the last good page is kept to open the app offline; `/api/...` pages (PDF, downloads) are never touched |
| Built files with a hash in the name (`main-XXXX.js`, `styles-XXXX.css`, fonts) | kept, served from the device first |
| Other files of the app (icons, assets) | served from the device, refreshed in the background |
| Shop pictures (`/api/public/store/.../image`, ads, banner) | served from the device, refreshed in the background (at most 200 kept) |
| Every other `/api` request, every POST / PUT / DELETE, other sites | not touched: always the server |

## Web server (needed once)
The browser must always ask the server for the worker and the manifest, so they must not be cached for long. For nginx, next to the existing `location /` of the site:

```nginx
location = /sw.js               { add_header Cache-Control "no-cache"; try_files $uri =404; }
location = /manifest.webmanifest { add_header Cache-Control "no-cache"; add_header Content-Type application/manifest+json; try_files $uri =404; }
location = /index.html          { add_header Cache-Control "no-cache"; }
```

The site must use HTTPS (browsers install and run workers only on HTTPS or localhost). The worker is served from `/` so that its scope is the whole site; a prefix such as `/app/` is not supported.

## Notes
- Update: when `sw.js` changes, devices show "A new version of the app is ready" and a Reload button. Normal releases only change hashed files, which the page already picks up on the next visit.
- If a device shows an old version, clearing the site's data in the browser removes the worker.
- Not part of this release: offline selling, background sync, push notifications, a shop logo as the installed icon.
