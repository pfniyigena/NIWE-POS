import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AccountProfile, CouponCheckResult, OnlineStoreApiService, Reorder, Shop, ShopPage, ShopProduct, shopToken } from '../core/api/online-store-api.service';
import { ShopAccountComponent } from './shop-account.component';
import { ShopMoneyPipe } from './shop-money.pipe';
import { LanguageService } from '../core/i18n/language.service';
import { T } from '../core/i18n/t';

/**
 * The public shop at /shop (no login). A visitor browses what the shop sells, fills a cart, and sends an order that waits for staff to accept it.
 * Prices and stock come from the server and are checked again when the order is sent, so nothing here can change what is charged.
 */
@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShopAccountComponent, ShopMoneyPipe],
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
          <div class="flex gap-1" style="align-items:center;">
            <button *ngIf="shop.accounts" class="btn btn-sm shop-account-btn" [class.btn-primary]="showAccount" [class.btn-outline-secondary]="!showAccount" (click)="showAccount = !showAccount">👤 {{ account ? (account.name || account.email || account.phone) : ('Sign in' | translate) }}</button>
            <a class="btn btn-sm btn-outline-secondary shop-staff-login" routerLink="/login">{{ 'Staff Login' | translate }}</a>
            <button class="btn btn-sm" [class.btn-primary]="lang.current === 'en'" [class.btn-outline-secondary]="lang.current !== 'en'" (click)="lang.choose('en')">EN</button>
            <button class="btn btn-sm" [class.btn-primary]="lang.current === 'fr'" [class.btn-outline-secondary]="lang.current !== 'fr'" (click)="lang.choose('fr')">FR</button>
          </div>
        </div>

        <div class="alert alert-warning shop-paused mt-2" *ngIf="!shop.open">{{ shop.message || ('We are not taking orders right now.' | translate) }}</div>

        <div class="alert alert-warning shop-reorder-note mt-2" *ngIf="reorderNote">{{ reorderNote }}</div>
        <app-shop-account *ngIf="showAccount && shop.accounts" [profile]="account" [currency]="cur" [textChannel]="shop.textChannel" (profileChange)="accountChanged($event)" (reordered)="reordered($event)" (closed)="showAccount = false"></app-shop-account>

        <ng-container *ngIf="step === 'shop' && !showAccount">
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
              <strong>{{ p.price | shopMoney: cur }}</strong>
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

        <button class="btn btn-sm btn-outline-secondary mt-3 shop-back" *ngIf="step === 'order' && !showAccount" (click)="back()">← {{ 'Continue shopping' | translate }}</button>

        <div class="card mt-3 shop-checkout" id="checkout" style="padding:1rem;" *ngIf="step === 'order' && count() > 0 && !showAccount">
          <h4 class="mb-2">{{ 'Your order' | translate }}</h4>
          <div class="alert alert-danger shop-order-error" id="order-error" role="alert" *ngIf="orderError">{{ orderError }}</div>
          <div *ngFor="let l of cartLines()" class="flex justify-between align-center" style="padding:0.25rem 0; gap:0.5rem;">
            <span style="flex:1;">{{ l.p.name }}</span>
            <span class="flex gap-1" style="align-items:center;">
              <button class="btn btn-sm btn-outline-secondary" (click)="change(l.p, -1)">−</button>
              <span style="min-width:1.5em; text-align:center;">{{ l.qty }}</span>
              <button class="btn btn-sm btn-outline-secondary" (click)="change(l.p, 1)" [disabled]="l.qty >= 20">+</button>
            </span>
            <span style="min-width:4.5em; text-align:right;">{{ l.qty * l.p.price | shopMoney: cur }}</span>
          </div>
          <hr />

          <div class="text-muted mb-2 shop-account-hint" *ngIf="shop.accounts" style="font-size:0.85rem;">
            <ng-container *ngIf="account">{{ 'Signed in as {p0}. This order will be saved in your account.' | translate: { p0: account.email || account.phone } }}</ng-container>
            <ng-container *ngIf="!account">{{ 'Buying as a guest.' | translate }} <a href="javascript:void(0)" class="shop-signin-link" (click)="showAccount = true">{{ 'Sign in' | translate }}</a> {{ 'to save your details and see your orders.' | translate }}</ng-container>
          </div>

          <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
            <label *ngIf="shop.delivery" class="flex gap-1" style="align-items:center;"><input type="radio" name="method" value="DELIVERY" [(ngModel)]="method" (ngModelChange)="recheckCoupon()" /> {{ 'Delivery' | translate }}</label>
            <label *ngIf="shop.pickup" class="flex gap-1" style="align-items:center;"><input type="radio" name="method" value="PICKUP" [(ngModel)]="method" (ngModelChange)="recheckCoupon()" /> {{ 'Pick-up' | translate }}</label>
          </div>
          <div class="text-muted mb-2" *ngIf="method === 'PICKUP' && shop.pickupAddress">📍 {{ shop.pickupAddress }}</div>

          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Your name' | translate }}</label><input class="form-control shop-name-input" maxlength="80" name="cname" [(ngModel)]="name" /></div>
            <div class="form-group"><label class="form-label">{{ 'Your phone number' | translate }}</label><input class="form-control shop-phone" maxlength="40" inputmode="tel" name="cphone" [(ngModel)]="phone" /></div>
            <div class="form-group"><label class="form-label">{{ 'E-mail (optional)' | translate }}</label><input class="form-control" maxlength="160" inputmode="email" name="cemail" [(ngModel)]="email" /></div>
          </div>
          <ng-container *ngIf="method === 'DELIVERY'">
            <div class="form-group" *ngIf="shop.zones.length"><label class="form-label">{{ 'Delivery area' | translate }}</label>
              <select class="form-select" style="max-width:320px;" name="zone" [(ngModel)]="zoneId" (ngModelChange)="recheckCoupon()">
                <option [ngValue]="null" disabled>{{ 'Choose your area…' | translate }}</option>
                <option *ngFor="let z of shop.zones" [ngValue]="z.id">{{ z.name }} ({{ z.fee | shopMoney: cur }})</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Delivery address' | translate }}</label><input class="form-control shop-address" maxlength="240" name="caddr" [(ngModel)]="address" /></div>
          </ng-container>
          <div class="form-group"><label class="form-label">{{ 'Note for the shop (optional)' | translate }}</label><input class="form-control" maxlength="300" name="cnotes" [(ngModel)]="notes" /></div>
          <label class="flex gap-1 mb-2 shop-text-opt" *ngIf="shop.textChannel" style="align-items:flex-start;">
            <input type="checkbox" name="textUpdates" [(ngModel)]="textUpdates" style="margin-top:0.25rem;" />
            <span>{{ shop.textChannel === 'WHATSAPP' ? ('Send me updates about this order by WhatsApp message' | translate) : ('Send me updates about this order by text message (SMS)' | translate) }}</span>
          </label>
          <!-- A real visitor never sees this field; a bot that fills in every box does. -->
          <input type="text" name="website" [(ngModel)]="website" tabindex="-1" autocomplete="off" aria-hidden="true" style="position:absolute; left:-9999px; width:1px; height:1px;" />

          <div class="mb-2" *ngIf="shop.payOnline">
            <label class="form-label">{{ 'How do you want to pay?' | translate }}</label>
            <div class="flex gap-3" style="flex-wrap:wrap;">
              <label *ngIf="shop.cash" class="flex gap-1" style="align-items:center;"><input type="radio" name="pay" value="CASH" [(ngModel)]="payment" /> {{ method === 'DELIVERY' ? ('Cash on delivery' | translate) : ('Cash at pick-up' | translate) }}</label>
              <label *ngIf="shop.payMethods.includes('MOBILE_MONEY')" class="flex gap-1" style="align-items:center;"><input type="radio" name="pay" value="MOBILE_MONEY" [(ngModel)]="payment" /> {{ 'Mobile money' | translate }}</label>
              <label *ngIf="shop.payMethods.includes('CARD')" class="flex gap-1" style="align-items:center;"><input type="radio" name="pay" value="CARD" [(ngModel)]="payment" /> {{ 'Card' | translate }}</label>
            </div>
            <div class="form-group mt-1" *ngIf="payment === 'MOBILE_MONEY'">
              <label class="form-label">{{ 'Mobile money number to pay from' | translate }}</label>
              <input class="form-control shop-pay-phone" style="max-width:260px;" maxlength="40" inputmode="tel" name="payphone" [(ngModel)]="payPhone" [placeholder]="phone" />
            </div>
          </div>
          <div class="mb-2 shop-coupon-box" *ngIf="shop.coupons">
            <label class="form-label">{{ 'Coupon code (optional)' | translate }}</label>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <input class="form-control shop-coupon" style="max-width:220px; text-transform:uppercase;" maxlength="40" name="coupon" [(ngModel)]="couponCode" [disabled]="!!coupon" (keyup.enter)="applyCoupon()" />
              <button type="button" class="btn btn-outline-primary shop-coupon-apply" *ngIf="!coupon" [disabled]="couponBusy || !couponCode.trim()" (click)="applyCoupon()">{{ couponBusy ? ('Checking…' | translate) : ('Apply' | translate) }}</button>
              <button type="button" class="btn btn-outline-secondary shop-coupon-remove" *ngIf="coupon" (click)="removeCoupon()">{{ 'Remove' | translate }}</button>
            </div>
            <div class="shop-coupon-ok" *ngIf="coupon" style="color:#15803d; font-size:0.9rem;">{{ 'Coupon {p0} applied.' | translate: { p0: coupon.code } }}</div>
            <div class="shop-coupon-error" *ngIf="couponError" style="color:#b91c1c; font-size:0.9rem;">{{ couponError }}</div>
          </div>
          <hr />
          <div class="flex justify-between"><span>{{ 'Items' | translate }}</span><span>{{ subtotal() | shopMoney: cur }}</span></div>
          <div class="flex justify-between shop-discount" *ngIf="discount() > 0" style="color:#15803d;"><span>{{ 'Discount ({p0})' | translate: { p0: coupon?.code } }}</span><span>−{{ discount() | shopMoney: cur }}</span></div>
          <div class="flex justify-between" *ngIf="method === 'DELIVERY'"><span>{{ 'Delivery' | translate }}</span><span>{{ feeShown() | shopMoney: cur }}<span *ngIf="waived() > 0" style="color:#15803d;"> ({{ 'free with the coupon' | translate }})</span></span></div>
          <div class="flex justify-between"><strong>{{ 'Total' | translate }}</strong><strong class="shop-total">{{ orderTotal() | shopMoney: cur }}</strong></div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="payment === 'CASH'">{{ method === 'DELIVERY' ? ('Pay in cash when your order arrives. Prices include tax.' | translate) : ('Pay in cash when you collect. Prices include tax.' | translate) }}</div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="payment !== 'CASH'">{{ 'You pay now. The shop gets your order as soon as the payment is confirmed. Prices include tax.' | translate }}</div>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="shop.minOrder > 0">{{ 'Minimum order {p0}' | translate: { p0: (shop.minOrder | shopMoney: cur) } }}</div>
          <button class="btn btn-primary shop-place mt-2" style="width:100%;" [disabled]="busy || !shop.open || (!shop.cash && !shop.payOnline)" (click)="place()">{{ busy ? ('Sending…' | translate) : payment === 'CASH' ? ('Place order' | translate) : ('Place order and pay' | translate) }}</button>
        </div>

        <div *ngIf="step === 'shop' && !showAccount && count() > 0" style="position:fixed; left:0; right:0; bottom:0; background:#111827; color:#fff; padding:0.6rem 1rem; display:flex; justify-content:space-between; align-items:center; z-index:50;">
          <span>🛒 {{ count() }} · {{ subtotal() | shopMoney: cur }}</span>
          <button class="btn btn-sm btn-primary shop-checkout-btn" (click)="checkout()">{{ 'Proceed to checkout' | translate }}</button>
        </div>
      </ng-container>
      <div class="text-muted shop-staff-footer mt-3" style="text-align:center; font-size:0.8rem;"><a routerLink="/login">{{ 'Staff Login' | translate }}</a></div>
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
  /** How the customer pays: cash on delivery or at pick-up, or online. */
  payment: 'CASH' | 'MOBILE_MONEY' | 'CARD' = 'CASH';
  payPhone = '';
  /** "Send me updates by text message": off unless ticked, and only shown when the shop texts customers. */
  textUpdates = false;
  busy = false;
  /** The signed-in customer (accounts are optional: null = guest). */
  account: AccountProfile | null = null;
  showAccount = false;
  /** The coupon box: what the customer typed, and what the server says it is worth on this cart (the order asks the server again). */
  couponCode = '';
  coupon: CouponCheckResult | null = null;
  couponError: string | null = null;
  couponBusy = false;
  private couponSeq = 0;
  /** What could not be put back in the cart when ordering again. */
  reorderNote: string | null = null;

  constructor(private api: OnlineStoreApiService, private router: Router, public lang: LanguageService) {}

  /** The shop's currency, written after every price. */
  get cur(): string {
    return this.shop?.currency ?? '';
  }

  ngOnInit(): void {
    this.api.shop().subscribe({
      next: (s) => {
        this.shop = s;
        this.method = s.delivery ? 'DELIVERY' : 'PICKUP';
        if (!s.cash && s.payMethods.length > 0) this.payment = s.payMethods[0] as 'MOBILE_MONEY' | 'CARD';
        if (s.accounts && shopToken.get()) {
          // A stale token is simply dropped: the customer shops as a guest.
          this.api.accountMe().subscribe({ next: (p) => this.accountChanged(p), error: () => shopToken.clear() });
        }
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? T('The shop is not available right now.')),
    });
  }

  /** The customer signed in, saved their details, or signed out. The order form is filled from the account, never over what was already typed. */
  accountChanged(p: AccountProfile | null): void {
    this.account = p;
    if (!p) return;
    if (!this.name.trim() && p.name) this.name = p.name;
    if (!this.phone.trim() && p.phone) this.phone = p.phone;
    if (!this.email.trim() && p.email) this.email = p.email;
    if (!this.address.trim() && p.address) this.address = p.address;
  }

  /** "Order again": today's products and prices go into the cart, and the customer lands on the order form. */
  reordered(r: Reorder): void {
    this.cart = {};
    this.cartProducts = {};
    for (const l of r.lines) {
      this.cart[l.product.id] = Math.max(1, Math.min(20, Math.round(l.quantity)));
      this.cartProducts[l.product.id] = l.product;
    }
    this.reorderNote = r.unavailable.length > 0 ? T('Not added, because they are gone or sold out: {p0}', { p0: r.unavailable.join(', ') }) : null;
    this.showAccount = false;
    if (this.count() > 0) this.checkout();
    else this.step = 'shop';
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
    // A small copy is enough for the product cards; the server shrinks it once and keeps it.
    return this.api.imageUrl(p.imageUrl as string) + '?w=420';
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
    this.recheckCoupon();
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

  discount(): number {
    return this.coupon?.discount ?? 0;
  }

  waived(): number {
    return this.coupon?.feeWaived ?? 0;
  }

  /** The delivery fee the customer pays: the area's fee, less what a free-delivery coupon waives. */
  feeShown(): number {
    return Math.max(0, this.fee() - this.waived());
  }

  orderTotal(): number {
    return this.subtotal() - this.discount() + this.feeShown();
  }

  /** "Apply": asks the server what the code is worth on this cart. silent = re-asking after the cart changed (a failure then just drops the coupon, with the reason). */
  applyCoupon(silent = false): void {
    const code = this.couponCode.trim();
    if (!code) return;
    const mine = ++this.couponSeq;
    if (!silent) {
      this.couponBusy = true;
      this.couponError = null;
    }
    this.api.couponCheck({ code, method: this.method, zoneId: this.method === 'DELIVERY' ? this.zoneId : null, phone: this.phone, lines: this.cartLines().map((l) => ({ productId: l.p.id, quantity: l.qty })) }).subscribe({
      next: (r) => {
        if (mine !== this.couponSeq) return;
        this.couponBusy = false;
        this.coupon = r;
        this.couponCode = r.code;
        this.couponError = null;
      },
      error: (e) => {
        if (mine !== this.couponSeq) return;
        this.couponBusy = false;
        this.coupon = null;
        this.couponError = e?.error?.message ?? T('That coupon could not be checked. Please try again.');
      },
    });
  }

  removeCoupon(): void {
    this.couponSeq++;
    this.coupon = null;
    this.couponError = null;
    this.couponCode = '';
  }

  /** The cart, the way of getting it, or the area changed: what the coupon is worth may have changed too. */
  recheckCoupon(): void {
    if (!this.coupon) return;
    setTimeout(() => this.applyCoupon(true));
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
        payment: this.payment, payPhone: this.payment === 'MOBILE_MONEY' ? (this.payPhone || this.phone) : null,
        couponCode: this.couponCode.trim() || null,
        textUpdates: !!this.shop?.textChannel && this.textUpdates,
      })
      .subscribe({
        next: (t) => {
          this.busy = false;
          // A card payment (or a provider that shows its own page) sends the customer there; otherwise the order page shows what to do next.
          if (t.payment?.status === 'AWAITING' && t.payment.redirectUrl) {
            window.location.assign(t.payment.redirectUrl);
            return;
          }
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
