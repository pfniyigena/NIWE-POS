import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OnlineStoreApiService, Shop, ShopPage, ShopProduct } from '../core/api/online-store-api.service';
import { LanguageService } from '../core/i18n/language.service';
import { T } from '../core/i18n/t';

/**
 * The public shop at /shop (no login). A visitor browses what the shop sells, fills a cart, and sends an order that waits for staff to accept it.
 * Prices and stock come from the server and are checked again when the order is sent, so nothing here can change what is charged.
 */
@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="shop-page" style="max-width:980px; margin:0 auto; padding:1rem 0.75rem 6rem;">
      <div class="alert alert-danger shop-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!shop && !error" class="text-muted">{{ 'Loading the shop…' | translate }}</div>

      <ng-container *ngIf="shop">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <h2 class="shop-name" style="margin:0;">{{ shop.name }}</h2>
            <div class="text-muted" *ngIf="shop.intro">{{ shop.intro }}</div>
            <div class="text-muted" *ngIf="shop.phone">📞 {{ shop.phone }}</div>
          </div>
          <div class="flex gap-1">
            <button class="btn btn-sm" [class.btn-primary]="lang.current === 'en'" [class.btn-outline-secondary]="lang.current !== 'en'" (click)="lang.choose('en')">EN</button>
            <button class="btn btn-sm" [class.btn-primary]="lang.current === 'fr'" [class.btn-outline-secondary]="lang.current !== 'fr'" (click)="lang.choose('fr')">FR</button>
          </div>
        </div>

        <div class="alert alert-warning shop-paused mt-2" *ngIf="!shop.open">{{ shop.message || ('We are not taking orders right now.' | translate) }}</div>

        <ng-container *ngIf="step === 'shop'">
        <div class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:center;">
          <input class="form-control shop-search" style="max-width:260px;" [placeholder]="'Search' | translate" [ngModel]="q" (ngModelChange)="searchChanged($event)" name="q" />
          <button class="btn btn-sm" [class.btn-primary]="category === ''" [class.btn-outline-secondary]="category !== ''" (click)="pickCategory('')">{{ 'All' | translate }}</button>
          <button *ngFor="let c of shop.categories" class="btn btn-sm" [class.btn-primary]="category === c" [class.btn-outline-secondary]="category !== c" (click)="pickCategory(c)">{{ c }}</button>
        </div>

        <div id="shop-top"></div>
        <div class="shop-grid mt-3" [style.opacity]="loading ? 0.5 : 1" style="display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:0.75rem;">
          <div class="card shop-product" *ngFor="let p of items" style="padding:0.6rem;">
            <img *ngIf="p.imageUrl" [src]="img(p)" [alt]="p.name" loading="lazy" style="width:100%; height:130px; object-fit:cover; border-radius:6px;" />
            <div *ngIf="!p.imageUrl" style="width:100%; height:130px; border-radius:6px; background:#f3f4f6; display:flex; align-items:center; justify-content:center; font-size:2rem;">🛍️</div>
            <div class="mt-1"><strong>{{ p.name }}</strong></div>
            <div class="text-muted" style="font-size:0.8rem; min-height:2.2em;" *ngIf="p.description">{{ p.description | slice: 0 : 80 }}</div>
            <div class="flex justify-between align-center mt-1">
              <strong>{{ p.price | number: '1.0-2' }}</strong>
              <span *ngIf="p.stock === 'LOW'" class="text-muted" style="font-size:0.75rem; color:#b45309;">{{ 'Few left' | translate }}</span>
              <span *ngIf="p.stock === 'SOLD_OUT'" class="text-muted" style="font-size:0.75rem;">{{ 'Sold out' | translate }}</span>
            </div>
            <div class="flex gap-1 mt-1" *ngIf="qty(p) > 0; else addBtn" style="align-items:center; justify-content:space-between;">
              <button class="btn btn-sm btn-outline-secondary" (click)="change(p, -1)">−</button>
              <span>{{ qty(p) }}</span>
              <button class="btn btn-sm btn-outline-secondary" (click)="change(p, 1)" [disabled]="qty(p) >= 20">+</button>
            </div>
            <ng-template #addBtn><button class="btn btn-sm btn-primary mt-1 shop-add" style="width:100%;" [disabled]="p.stock === 'SOLD_OUT' || !shop.open" (click)="change(p, 1)">{{ 'Add to cart' | translate }}</button></ng-template>
          </div>
        </div>
        <p class="text-muted mt-3 shop-empty" *ngIf="!loading && items.length === 0 && !q.trim() && !category">{{ 'Nothing is for sale yet. Please come back soon.' | translate }}</p>
        <p class="text-muted mt-3 shop-empty" *ngIf="!loading && items.length === 0 && (q.trim() || category)">{{ 'No products match.' | translate }}</p>

        <nav class="shop-pager flex justify-between align-center mt-3" *ngIf="totalPages > 1" style="flex-wrap:wrap; gap:0.5rem;">
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Showing {p0}–{p1} of {p2}' | translate: { p0: firstShown(), p1: lastShown(), p2: total } }}</span>
          <div class="flex gap-1" style="align-items:center;">
            <button class="btn btn-sm btn-outline-secondary shop-prev" [disabled]="page === 0 || loading" (click)="go(page - 1)">{{ 'Previous' | translate }}</button>
            <span style="font-size:0.85rem;">{{ 'Page {p0} of {p1}' | translate: { p0: page + 1, p1: totalPages } }}</span>
            <button class="btn btn-sm btn-outline-secondary shop-next" [disabled]="page + 1 >= totalPages || loading" (click)="go(page + 1)">{{ 'Next' | translate }}</button>
          </div>
        </nav>
        </ng-container>

        <button class="btn btn-sm btn-outline-secondary mt-3 shop-back" *ngIf="step === 'order'" (click)="back()">← {{ 'Continue shopping' | translate }}</button>

        <div class="card mt-3 shop-checkout" id="checkout" style="padding:1rem;" *ngIf="step === 'order' && count() > 0">
          <h4 class="mb-2">{{ 'Your order' | translate }}</h4>
          <div class="alert alert-danger shop-order-error" id="order-error" role="alert" *ngIf="orderError">{{ orderError }}</div>
          <div *ngFor="let l of cartLines()" class="flex justify-between align-center" style="padding:0.25rem 0; gap:0.5rem;">
            <span style="flex:1;">{{ l.p.name }}</span>
            <span class="flex gap-1" style="align-items:center;">
              <button class="btn btn-sm btn-outline-secondary" (click)="change(l.p, -1)">−</button>
              <span style="min-width:1.5em; text-align:center;">{{ l.qty }}</span>
              <button class="btn btn-sm btn-outline-secondary" (click)="change(l.p, 1)" [disabled]="l.qty >= 20">+</button>
            </span>
            <span style="min-width:4.5em; text-align:right;">{{ l.qty * l.p.price | number: '1.0-2' }}</span>
          </div>
          <hr />

          <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
            <label *ngIf="shop.delivery" class="flex gap-1" style="align-items:center;"><input type="radio" name="method" value="DELIVERY" [(ngModel)]="method" /> {{ 'Delivery' | translate }}</label>
            <label *ngIf="shop.pickup" class="flex gap-1" style="align-items:center;"><input type="radio" name="method" value="PICKUP" [(ngModel)]="method" /> {{ 'Pick-up' | translate }}</label>
          </div>
          <div class="text-muted mb-2" *ngIf="method === 'PICKUP' && shop.pickupAddress">📍 {{ shop.pickupAddress }}</div>

          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Your name' | translate }}</label><input class="form-control shop-name-input" maxlength="80" name="cname" [(ngModel)]="name" /></div>
            <div class="form-group"><label class="form-label">{{ 'Your phone number' | translate }}</label><input class="form-control shop-phone" maxlength="40" inputmode="tel" name="cphone" [(ngModel)]="phone" /></div>
            <div class="form-group"><label class="form-label">{{ 'E-mail (optional)' | translate }}</label><input class="form-control" maxlength="160" inputmode="email" name="cemail" [(ngModel)]="email" /></div>
          </div>
          <ng-container *ngIf="method === 'DELIVERY'">
            <div class="form-group" *ngIf="shop.zones.length"><label class="form-label">{{ 'Delivery area' | translate }}</label>
              <select class="form-select" style="max-width:320px;" name="zone" [(ngModel)]="zoneId">
                <option [ngValue]="null" disabled>{{ 'Choose your area…' | translate }}</option>
                <option *ngFor="let z of shop.zones" [ngValue]="z.id">{{ z.name }} ({{ z.fee | number: '1.0-2' }})</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Delivery address' | translate }}</label><input class="form-control shop-address" maxlength="240" name="caddr" [(ngModel)]="address" /></div>
          </ng-container>
          <div class="form-group"><label class="form-label">{{ 'Note for the shop (optional)' | translate }}</label><input class="form-control" maxlength="300" name="cnotes" [(ngModel)]="notes" /></div>
          <!-- A real visitor never sees this field; a bot that fills in every box does. -->
          <input type="text" name="website" [(ngModel)]="website" tabindex="-1" autocomplete="off" aria-hidden="true" style="position:absolute; left:-9999px; width:1px; height:1px;" />

          <hr />
          <div class="flex justify-between"><span>{{ 'Items' | translate }}</span><span>{{ subtotal() | number: '1.0-2' }}</span></div>
          <div class="flex justify-between" *ngIf="method === 'DELIVERY'"><span>{{ 'Delivery' | translate }}</span><span>{{ fee() | number: '1.0-2' }}</span></div>
          <div class="flex justify-between"><strong>{{ 'Total' | translate }}</strong><strong class="shop-total">{{ subtotal() + fee() | number: '1.0-2' }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;">{{ method === 'DELIVERY' ? ('Pay in cash when your order arrives. Prices include tax.' | translate) : ('Pay in cash when you collect. Prices include tax.' | translate) }}</div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="shop.minOrder > 0">{{ 'Minimum order {p0}' | translate: { p0: (shop.minOrder | number: '1.0-2') } }}</div>
          <button class="btn btn-primary shop-place mt-2" style="width:100%;" [disabled]="busy || !shop.open || !shop.cash" (click)="place()">{{ busy ? ('Sending…' | translate) : ('Place order' | translate) }}</button>
        </div>

        <div *ngIf="step === 'shop' && count() > 0" style="position:fixed; left:0; right:0; bottom:0; background:#111827; color:#fff; padding:0.6rem 1rem; display:flex; justify-content:space-between; align-items:center; z-index:50;">
          <span>🛒 {{ count() }} · {{ subtotal() | number: '1.0-2' }}</span>
          <button class="btn btn-sm btn-primary shop-checkout-btn" (click)="checkout()">{{ 'Proceed to checkout' | translate }}</button>
        </div>
      </ng-container>
    </div>
  `,
})
export class ShopComponent implements OnInit {
  shop: Shop | null = null;
  error: string | null = null;
  /** shop = browsing; order = the cart and the place-order form. */
  step: 'shop' | 'order' = 'shop';
  /** Why the order could not be sent. Shown at the top of the order form, where the customer is looking. */
  orderError: string | null = null;
  q = '';
  category = '';
  /** What the visitor has put in the cart, kept with the product so it survives moving between pages and searches. */
  cart: Record<number, number> = {};
  cartProducts: Record<number, ShopProduct> = {};
  items: ShopProduct[] = [];
  page = 0;
  readonly size = 16;
  total = 0;
  totalPages = 0;
  loading = false;
  private seq = 0;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  method: 'DELIVERY' | 'PICKUP' = 'DELIVERY';
  name = '';
  phone = '';
  email = '';
  address = '';
  notes = '';
  website = '';
  zoneId: number | null = null;
  busy = false;

  constructor(private api: OnlineStoreApiService, private router: Router, public lang: LanguageService) {}

  ngOnInit(): void {
    this.api.shop().subscribe({
      next: (s) => {
        this.shop = s;
        this.method = s.delivery ? 'DELIVERY' : 'PICKUP';
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? T('The shop is not available right now.')),
    });
  }

  /** Fetches the current page. A slower, older answer never replaces a newer one. */
  load(scrollToTop = false): void {
    const mine = ++this.seq;
    this.loading = true;
    this.api.products(this.page, this.size, this.q, this.category).subscribe({
      next: (r: ShopPage) => {
        if (mine !== this.seq) return;
        if (r.totalPages > 0 && r.page >= r.totalPages) {
          this.page = r.totalPages - 1;
          this.load(scrollToTop);
          return;
        }
        this.items = r.items;
        this.total = r.total;
        this.totalPages = r.totalPages;
        this.loading = false;
        if (scrollToTop) document.getElementById('shop-top')?.scrollIntoView({ behavior: 'smooth' });
      },
      error: (e) => {
        if (mine !== this.seq) return;
        this.loading = false;
        this.error = e?.error?.message ?? T('The shop is not available right now.');
      },
    });
  }

  searchChanged(v: string): void {
    this.q = v ?? '';
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page = 0;
      this.load();
    }, 300);
  }

  pickCategory(c: string): void {
    this.category = c;
    this.page = 0;
    this.load();
  }

  go(p: number): void {
    if (p < 0 || p >= this.totalPages || p === this.page) return;
    this.page = p;
    this.load(true);
  }

  firstShown(): number {
    return this.total === 0 ? 0 : this.page * this.size + 1;
  }

  lastShown(): number {
    return Math.min(this.total, this.page * this.size + this.items.length);
  }

  img(p: ShopProduct): string {
    return this.api.imageUrl(p.imageUrl as string);
  }

  qty(p: ShopProduct): number {
    return this.cart[p.id] ?? 0;
  }

  change(p: ShopProduct, by: number): void {
    const n = Math.max(0, Math.min(20, this.qty(p) + by));
    if (n === 0) {
      delete this.cart[p.id];
      delete this.cartProducts[p.id];
    } else {
      this.cart[p.id] = n;
      this.cartProducts[p.id] = p;
    }
    if (this.step === 'order' && this.count() === 0) this.back();
  }

  cartLines(): { p: ShopProduct; qty: number }[] {
    return Object.keys(this.cart).map((id) => ({ p: this.cartProducts[+id], qty: this.cart[+id] })).filter((l) => !!l.p && l.qty > 0);
  }

  count(): number {
    return Object.values(this.cart).reduce((a, b) => a + b, 0);
  }

  subtotal(): number {
    return this.cartLines().reduce((a, l) => a + l.qty * l.p.price, 0);
  }

  /** Same rule as the server (which checks it again): the area's fee, or nothing when the items reach the free-delivery amount. */
  fee(): number {
    if (!this.shop || this.method !== 'DELIVERY') return 0;
    if (this.shop.freeDeliveryOver != null && this.subtotal() >= this.shop.freeDeliveryOver) return 0;
    const z = this.shop.zones.find((x) => x.id === this.zoneId);
    return z ? z.fee : 0;
  }

  /** From the cart bar to the place-order step. */
  checkout(): void {
    if (this.count() === 0) return;
    this.orderError = null;
    this.step = 'order';
    window.scrollTo({ top: 0 });
  }

  back(): void {
    this.step = 'shop';
    this.orderError = null;
    window.scrollTo({ top: 0 });
  }

  place(): void {
    this.orderError = null;
    this.busy = true;
    this.api
      .place({
        name: this.name, phone: this.phone, email: this.email || null, address: this.method === 'DELIVERY' ? this.address : null, method: this.method,
        zoneId: this.method === 'DELIVERY' ? this.zoneId : null, notes: this.notes || null, language: this.lang.current, website: this.website,
        lines: this.cartLines().map((l) => ({ productId: l.p.id, quantity: l.qty })),
      })
      .subscribe({
        next: (t) => {
          this.busy = false;
          this.router.navigate(['/shop/order', t.code]);
        },
        error: (e) => {
          this.busy = false;
          this.orderError = e?.error?.message ?? T('We could not send your order. Please try again.');
          setTimeout(() => document.getElementById('order-error')?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
        },
      });
  }
}
