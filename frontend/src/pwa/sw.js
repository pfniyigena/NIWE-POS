/*
 * NIWE service worker (Progressive Web App).
 *
 * What it does, and what it deliberately does not:
 *  - The page itself (index.html) is fetched from the network first; the last good copy is kept so the app can open without a connection.
 *  - The built files (JS, CSS, fonts, pictures of the app) have a hash in their name, so they are kept and served from the device: the app starts fast.
 *  - Shop pictures (products, ads, banners) are shown from the device and refreshed in the background.
 *  - Nothing else from /api is ever kept or answered by this file: sales, stock, customers and prices always come from the server. Offline, a sale cannot be saved.
 *  - A new version of this file waits until the person agrees to reload (so a till is never reloaded in the middle of a sale).
 * Change VERSION to force every device to drop its old copies.
 */
const VERSION = 'v1';
const STATIC = 'niwe-static-' + VERSION;
const PAGES = 'niwe-pages-' + VERSION;
const PICTURES = 'niwe-pictures-' + VERSION;
const KEEP = [STATIC, PAGES, PICTURES];
const SHELL = '/__shell';
const MAX_STATIC = 400;
const MAX_PICTURES = 200;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC).then((c) => c.addAll(['/offline.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png']))
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((names) => Promise.all(names.filter((n) => n.startsWith('niwe-') && !KEEP.includes(n)).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') self.skipWaiting();
});

/** Keeps a cache from growing for ever: the oldest entries go first. */
async function trim(name, max) {
  const cache = await caches.open(name);
  const keys = await cache.keys();
  for (let i = 0; i < keys.length - max; i++) await cache.delete(keys[i]);
}

const HASHED = /[-.][A-Za-z0-9_]{8,}\.(?:js|css|woff2?|ttf)$/;
const STATIC_FILE = /\.(?:js|css|woff2?|ttf|svg|png|jpe?g|webp|gif|ico)$/i;
const SHOP_PICTURE = /^\/api\/public\/store\/(?:.*\/image|.*\/images\/\d+|.*\/banner.*)$/;

async function page(request) {
  try {
    const response = await fetch(request);
    if (response.ok && (response.headers.get('content-type') || '').includes('text/html')) {
      const cache = await caches.open(PAGES);
      await cache.put(SHELL, response.clone());
    }
    if (response.status < 500) return response;
    throw new Error('server ' + response.status);
  } catch (e) {
    const cached = await caches.match(SHELL);
    return cached || (await caches.match('/offline.html')) || Response.error();
  }
}

async function cacheFirst(request) {
  const hit = await caches.match(request);
  if (hit) return hit;
  const response = await fetch(request);
  if (response.ok) {
    const cache = await caches.open(STATIC);
    await cache.put(request, response.clone());
    trim(STATIC, MAX_STATIC);
  }
  return response;
}

async function staleWhileRevalidate(request, name, max) {
  const cache = await caches.open(name);
  const hit = await cache.match(request);
  const fresh = fetch(request)
    .then((response) => {
      if (response.ok) { cache.put(request, response.clone()); trim(name, max); }
      return response;
    })
    .catch(() => null);
  return hit || (await fresh) || Response.error();
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET' || request.headers.has('range')) return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  const path = url.pathname;

  if (path.startsWith('/api/')) {
    // pages opened from the API (a PDF, a download) and every other /api answer come from the server, never from this device
    if (request.mode !== 'navigate' && SHOP_PICTURE.test(path)) event.respondWith(staleWhileRevalidate(request, PICTURES, MAX_PICTURES));
    return;
  }
  if (request.mode === 'navigate') {
    event.respondWith(page(request));
    return;
  }
  if (path === '/sw.js') return;
  if (STATIC_FILE.test(path)) {
    event.respondWith(HASHED.test(path) ? cacheFirst(request) : staleWhileRevalidate(request, STATIC, MAX_STATIC));
  }
});
