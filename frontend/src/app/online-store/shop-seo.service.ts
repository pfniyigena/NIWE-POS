import { Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

/** What a shop page tells the browser and search engines about itself. */
export interface SeoInfo {
  title: string;
  description?: string | null;
  /** A path or full address of the picture shown when the link is shared. */
  image?: string | null;
  /** The address people share (path starting with / or a full address). */
  url?: string | null;
  type?: 'website' | 'product';
  /** schema.org data for Google (a Product with its price and stock, for example). */
  structured?: Record<string, unknown> | null;
}

const DEFAULT_TITLE = 'POS';
const TAGS = ['description', 'og:type', 'og:title', 'og:description', 'og:url', 'og:image', 'og:site_name', 'twitter:card', 'twitter:title', 'twitter:description', 'twitter:image'];

/**
 * Page title, description, share-preview tags and Google product data of the public shop (v1.53.0). The browser tab shows a real title, Google
 * (which runs the app) reads the same tags, and the data is cleared when the visitor leaves the shop so the staff screens keep the plain title.
 * WhatsApp and Facebook do not run the app: they read the server pages described in docs/online-store-seo.md.
 */
@Injectable({ providedIn: 'root' })
export class ShopSeoService {
  constructor(private title: Title, private meta: Meta) {}

  set(info: SeoInfo): void {
    this.title.setTitle(info.title);
    const description = this.short(info.description ?? '');
    const url = this.absolute(info.url ?? (typeof location !== 'undefined' ? location.pathname + location.search : ''));
    const image = info.image ? this.absolute(info.image) : null;
    this.tag('description', description, 'name');
    this.tag('og:type', info.type ?? 'website');
    this.tag('og:title', info.title);
    this.tag('og:description', description);
    this.tag('og:url', url);
    this.tag('og:image', image);
    this.tag('twitter:card', image ? 'summary_large_image' : 'summary', 'name');
    this.tag('twitter:title', info.title, 'name');
    this.tag('twitter:description', description, 'name');
    this.tag('twitter:image', image, 'name');
    this.canonical(url);
    this.structured(info.structured ?? null);
  }

  /** Back to the plain title and no share tags: called when the visitor leaves the shop. */
  clear(): void {
    this.title.setTitle(DEFAULT_TITLE);
    for (const t of TAGS) this.remove(t);
    this.canonical(null);
    this.structured(null);
  }

  private absolute(path: string): string {
    if (/^https?:\/\//i.test(path)) return path;
    return (typeof location !== 'undefined' ? location.origin : '') + (path.startsWith('/') ? path : '/' + path);
  }

  private short(text: string): string {
    const t = text.replace(/\s+/g, ' ').trim();
    if (t.length <= 200) return t;
    const cut = t.lastIndexOf(' ', 200);
    return t.substring(0, cut > 80 ? cut : 200).trim() + '…';
  }

  private tag(key: string, value: string | null, attr: 'name' | 'property' = key.startsWith('og:') ? 'property' : 'name'): void {
    if (!value) {
      this.remove(key);
      return;
    }
    this.meta.updateTag({ [attr]: key, content: value } as { name: string; content: string });
  }

  private remove(key: string): void {
    this.meta.removeTag(`name="${key}"`);
    this.meta.removeTag(`property="${key}"`);
  }

  private canonical(url: string | null): void {
    if (typeof document === 'undefined') return;
    let el = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"][data-shop]');
    if (!url) {
      el?.remove();
      return;
    }
    if (!el) {
      el = document.createElement('link');
      el.rel = 'canonical';
      el.setAttribute('data-shop', '1');
      document.head.appendChild(el);
    }
    el.href = url;
  }

  private structured(data: Record<string, unknown> | null): void {
    if (typeof document === 'undefined') return;
    let el = document.getElementById('shop-ld') as HTMLScriptElement | null;
    if (!data) {
      el?.remove();
      return;
    }
    if (!el) {
      el = document.createElement('script');
      el.type = 'application/ld+json';
      el.id = 'shop-ld';
      document.head.appendChild(el);
    }
    el.textContent = JSON.stringify(data).replace(/<\//g, '<\\/');
  }
}
