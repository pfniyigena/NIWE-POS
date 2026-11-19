import { Injectable, isDevMode } from '@angular/core';

/** The browser's "install this app" offer (not in the standard typings). */
interface InstallOffer extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISSED_KEY = 'niwe.pwa.dismissed';
const DISMISS_DAYS = 14;
const FALLBACK_COLOR = '#12151f';

/**
 * Progressive Web App support: registers the service worker (public/sw.js, copied to the site root), keeps the "Install the app" offer,
 * tells the screens when the device is offline or a new version of the worker is waiting, and lets the shop name the installed app
 * after the shop. Nothing here changes what the app does online.
 */
@Injectable({ providedIn: 'root' })
export class PwaService {
  online = typeof navigator === 'undefined' ? true : navigator.onLine;
  /** The browser can install the app and the person has not said no lately. */
  canInstall = false;
  /** iPhone / iPad Safari has no install button: the person is shown the Share > Add to Home Screen way instead. */
  iosHint = false;
  /** A new version of the service worker is installed and waiting for a reload. */
  updateReady = false;

  private offer: InstallOffer | null = null;
  private waiting: ServiceWorker | null = null;
  private reloading = false;
  private manifestUrl: string | null = null;

  constructor() {
    if (typeof window === 'undefined') return;
    window.addEventListener('online', () => (this.online = true));
    window.addEventListener('offline', () => (this.online = false));
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.offer = e as InstallOffer;
      this.canInstall = !this.standalone && !this.recentlyDismissed();
    });
    window.addEventListener('appinstalled', () => {
      this.offer = null;
      this.canInstall = false;
      this.iosHint = false;
    });
    const ua = navigator.userAgent || '';
    const ios = /iphone|ipad|ipod/i.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    const safari = /safari/i.test(ua) && !/crios|fxios|edgios|chrome|android/i.test(ua);
    this.iosHint = ios && safari && !this.standalone && !this.recentlyDismissed();
    this.register();
  }

  /** Is the app running as an installed app (own window, no browser bar)? */
  get standalone(): boolean {
    return window.matchMedia?.('(display-mode: standalone)').matches || (navigator as unknown as { standalone?: boolean }).standalone === true;
  }

  private recentlyDismissed(): boolean {
    try {
      const at = Number(localStorage.getItem(DISMISSED_KEY) ?? 0);
      return at > 0 && Date.now() - at < DISMISS_DAYS * 86400000;
    } catch {
      return false;
    }
  }

  /** "Not now": the offer stays away for two weeks. */
  dismiss(): void {
    this.canInstall = false;
    this.iosHint = false;
    try {
      localStorage.setItem(DISMISSED_KEY, String(Date.now()));
    } catch {
      // storage can be blocked; the offer then simply comes back next time
    }
  }

  /** Shows the browser's own install question. */
  async install(): Promise<void> {
    const offer = this.offer;
    if (!offer) return;
    this.offer = null;
    this.canInstall = false;
    try {
      await offer.prompt();
      const choice = await offer.userChoice;
      if (choice.outcome === 'dismissed') this.dismiss();
    } catch {
      // the browser refused to show it: nothing to do
    }
  }

  /** The waiting worker takes over and the page reloads once. */
  reload(): void {
    if (this.waiting) this.waiting.postMessage({ type: 'SKIP_WAITING' });
    else location.reload();
  }

  private register(): void {
    // Only on a real build: a development build has no hashed file names, and a worker would serve stale files while coding.
    if (isDevMode() || !('serviceWorker' in navigator)) return;
    const secure = location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1';
    if (!secure) return;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (this.reloading) return;
      this.reloading = true;
      location.reload();
    });
    const start = () => {
      navigator.serviceWorker.register('/sw.js', { scope: '/' }).then((reg) => {
        const watch = (w: ServiceWorker | null) => {
          if (!w) return;
          w.addEventListener('statechange', () => {
            if (w.state === 'installed' && navigator.serviceWorker.controller) {
              this.waiting = w;
              this.updateReady = true;
            }
          });
        };
        if (reg.waiting && navigator.serviceWorker.controller) {
          this.waiting = reg.waiting;
          this.updateReady = true;
        }
        reg.addEventListener('updatefound', () => watch(reg.installing));
        // A till stays open all day: look for a new worker every hour and when the tab comes back.
        setInterval(() => reg.update().catch(() => {}), 3600000);
        document.addEventListener('visibilitychange', () => {
          if (!document.hidden) reg.update().catch(() => {});
        });
      }).catch(() => {});
    };
    if (document.readyState === 'complete') start();
    else window.addEventListener('load', start, { once: true });
  }

  /** The shop is installed under the shop's own name and colour (the start page is the shop). */
  useShopManifest(name: string, color: string | null): void {
    if (typeof document === 'undefined') return;
    const link = document.querySelector<HTMLLinkElement>('link[rel="manifest"]');
    if (!link || !name.trim()) return;
    const theme = color && /^#[0-9a-fA-F]{6}$/.test(color) ? color : FALLBACK_COLOR;
    const origin = location.origin;
    const manifest = {
      id: '/shop',
      name: name.trim().slice(0, 45),
      short_name: name.trim().slice(0, 12),
      description: name.trim(),
      start_url: `${origin}/shop`,
      scope: `${origin}/`,
      display: 'standalone',
      background_color: '#ffffff',
      theme_color: theme,
      icons: [
        { src: `${origin}/icons/icon-192.png`, sizes: '192x192', type: 'image/png', purpose: 'any' },
        { src: `${origin}/icons/icon-512.png`, sizes: '512x512', type: 'image/png', purpose: 'any' },
        { src: `${origin}/icons/icon-maskable-512.png`, sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    };
    if (this.manifestUrl) URL.revokeObjectURL(this.manifestUrl);
    this.manifestUrl = URL.createObjectURL(new Blob([JSON.stringify(manifest)], { type: 'application/manifest+json' }));
    link.href = this.manifestUrl;
    document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')?.setAttribute('content', theme);
  }
}
