import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AccountOrder, AccountProfile, AccountReturn, OnlineStoreApiService, Reorder, Shop, ShopAddress, ShopProduct, shopToken } from '../core/api/online-store-api.service';
import { ShopAccountComponent } from './shop-account.component';
import { ShopMoneyPipe } from './shop-money.pipe';
import { WishlistService } from './wishlist.service';
import { T } from '../core/i18n/t';

export const PORTAL_SECTIONS = ['orders', 'invoices', 'wishlist', 'returns', 'addresses', 'points', 'profile'] as const;
export type PortalSection = (typeof PORTAL_SECTIONS)[number];

/**
 * The customer portal of the shop (v1.55.0): a page with a menu (My orders, My invoices, My wish list, My returns, My addresses, Loyalty points, My profile)
 * at /shop/account/<section>. Signed out it shows the sign-in; signed in, the menu and the chosen page. The shop page owns the cart, the profile and the
 * addresses, so "Order again" and "Add to cart" are handed back to it. Nothing here is needed to buy: the shop works without an account.
 */
@Component({
  selector: 'app-shop-portal',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShopMoneyPipe, ShopAccountComponent],
  styles: [`
    .portal { display: grid; grid-template-columns: 230px 1fr; gap: 1.25rem; align-items: start; margin-top: 1rem; }
    .portal-menu { display: flex; flex-direction: column; gap: 0.25rem; position: sticky; top: 4.5rem; }
    .portal-link { display: flex; align-items: center; gap: 0.6rem; padding: 0.55rem 0.75rem; border-radius: 8px; color: inherit; text-decoration: none; font-weight: 500; }
    .portal-link:hover { background: rgba(128,128,128,0.12); }
    .portal-link.active { background: var(--shop-brand, #1d4ed8); color: #fff; }
    .portal-count { margin-left: auto; font-size: 0.75rem; padding: 0 0.45rem; border-radius: 999px; background: rgba(128,128,128,0.25); }
    .portal-link.active .portal-count { background: rgba(255,255,255,0.3); }
    .portal-row { padding: 0.75rem 0; border-top: 1px solid rgba(128,128,128,0.25); }
    .portal-row:first-of-type { border-top: 0; }
    @media (max-width: 760px) {
      .portal { grid-template-columns: 1fr; gap: 0.75rem; }
      .portal-menu { flex-direction: row; overflow-x: auto; position: static; padding-bottom: 0.25rem; }
      .portal-link { white-space: nowrap; padding: 0.45rem 0.7rem; }
    }
  `],
  template: `
    <div class="shop-portal" style="max-width:1100px; margin:0 auto;">
      <div class="flex justify-between align-center" style="gap:0.5rem; flex-wrap:wrap; margin-top:1rem;">
        <h3 style="margin:0;">{{ 'My account' | translate }}</h3>
        <a class="btn btn-sm btn-outline-secondary portal-back" routerLink="/shop">← {{ 'Continue shopping' | translate }}</a>
      </div>

      <!-- Signed out -->
      <app-shop-account *ngIf="!profile" section="all" [profile]="null" [currency]="currency" [textChannel]="shop.textChannel" [loyalty]="shop.loyalty" [addresses]="addresses" [zones]="shop.zones"
        (profileChange)="profileChange.emit($event)" (closed)="closed.emit()"></app-shop-account>

      <!-- Signed in -->
      <div class="portal" *ngIf="profile">
        <nav class="portal-menu" [attr.aria-label]="'My account' | translate">
          <a *ngFor="let m of menu()" class="portal-link" [class.active]="section === m.key" [routerLink]="['/shop/account', m.key]" [attr.data-section]="m.key">
            <span aria-hidden="true">{{ m.icon }}</span> {{ m.label | translate }}<span class="portal-count" *ngIf="count(m.key) > 0">{{ count(m.key) }}</span>
          </a>
          <div class="text-muted" style="font-size:0.8rem; padding:0.5rem 0.75rem;">{{ profile.name || profile.email || profile.phone }}</div>
        </nav>

        <section class="portal-page" style="min-width:0;">
          <div class="alert alert-danger" role="alert" *ngIf="error">{{ error }}</div>
          <div class="alert alert-success" *ngIf="info">{{ info }}</div>

          <!-- My orders -->
          <div *ngIf="section === 'orders'" class="portal-orders">
            <h4 style="margin-top:0;">{{ 'My orders' | translate }}</h4>
            <div class="flex gap-1" style="flex-wrap:wrap; margin-bottom:0.5rem;">
              <button *ngFor="let f of orderFilters" class="btn btn-sm portal-filter" [class.btn-primary]="orderFilter === f.key" [class.btn-outline-secondary]="orderFilter !== f.key" (click)="orderFilter = f.key">{{ f.label | translate }} ({{ ordersIn(f.key).length }})</button>
            </div>
            <div class="text-muted" *ngIf="orders === null">{{ 'Loading…' | translate }}</div>
            <div class="text-muted portal-no-orders" *ngIf="orders && ordersIn(orderFilter).length === 0">{{ 'No orders here yet. Orders you place while signed in, and earlier ones sent with your e-mail address, appear here.' | translate }}</div>
            <div class="portal-row portal-order" *ngFor="let o of ordersIn(orderFilter)">
              <div class="flex justify-between" style="gap:0.5rem; flex-wrap:wrap;">
                <div>
                  <a [routerLink]="['/shop/order', o.code]"><strong>{{ o.code }}</strong></a>
                  <span class="badge" style="margin-left:0.4rem;" [style.color]="statusColor(o.status)">{{ statusLabel(o.status) | translate }}</span>
                  <div class="text-muted" style="font-size:0.85rem;">{{ o.placedAt | date: 'mediumDate' }} · {{ (o.deliveryMethod === 'DELIVERY' ? 'Delivery' : 'Pick-up') | translate }} · {{ o.items }} {{ 'items' | translate }}</div>
                </div>
                <strong>{{ o.total | shopMoney: currency }}</strong>
              </div>
              <div class="portal-code mt-1" *ngIf="o.canConfirm && o.receiptCode" style="font-size:0.9rem;">
                {{ (o.deliveryMethod === 'DELIVERY' ? 'Your delivery code' : 'Your pick-up code') | translate }}: <strong class="portal-code-value" style="font-size:1.2rem; letter-spacing:0.2em;">{{ o.receiptCode }}</strong>
                <span class="text-muted" style="font-size:0.8rem;"> · {{ 'Give it only when you hold your goods.' | translate }}</span>
              </div>
              <div class="flex gap-1 mt-1" style="flex-wrap:wrap;">
                <a class="btn btn-sm btn-outline-secondary portal-track" [routerLink]="['/shop/order', o.code]">{{ 'Track' | translate }}</a>
                <a class="btn btn-sm btn-success portal-received" *ngIf="o.canConfirm" [routerLink]="['/shop/order', o.code]">✔ {{ 'I received my order' | translate }}</a>
                <button class="btn btn-sm btn-outline-primary portal-reorder" [disabled]="busy" (click)="reorder(o)">{{ 'Order again' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary portal-invoice-btn" *ngIf="o.invoiceNumber" [disabled]="busy" (click)="invoice(o)">⬇ {{ 'Invoice' | translate }} {{ o.invoiceNumber }}</button>
              </div>
            </div>
          </div>

          <!-- My invoices -->
          <div *ngIf="section === 'invoices'" class="portal-invoices">
            <h4 style="margin-top:0;">{{ 'My invoices' | translate }}</h4>
            <div class="text-muted" *ngIf="orders === null">{{ 'Loading…' | translate }}</div>
            <div class="text-muted portal-no-invoices" *ngIf="orders && invoiced().length === 0">{{ 'Your invoices appear here once your orders are completed.' | translate }}</div>
            <div class="portal-row portal-invoice flex justify-between align-center" *ngFor="let o of invoiced()" style="gap:0.5rem; flex-wrap:wrap;">
              <div>
                <strong>{{ o.invoiceNumber }}</strong>
                <div class="text-muted" style="font-size:0.85rem;">{{ 'Order {p0}' | translate: { p0: o.code } }} · {{ o.placedAt | date: 'mediumDate' }}</div>
              </div>
              <div class="flex gap-2 align-center">
                <strong>{{ o.total | shopMoney: currency }}</strong>
                <button class="btn btn-sm btn-outline-primary portal-download" [disabled]="busy" (click)="invoice(o)">⬇ {{ 'Download (PDF)' | translate }}</button>
              </div>
            </div>
          </div>

          <!-- My wish list -->
          <div *ngIf="section === 'wishlist'" class="portal-wishlist">
            <h4 style="margin-top:0;"><span style="color:#dc2626;">♥</span> {{ 'My wish list' | translate }}</h4>
            <div class="text-muted" *ngIf="wishBusy">{{ 'Loading…' | translate }}</div>
            <p class="text-muted portal-no-wish" *ngIf="!wishBusy && wishShown().length === 0">{{ 'Your wish list is empty. Tap the heart on a product to keep it here.' | translate }}</p>
            <div style="display:grid; grid-template-columns:repeat(auto-fill,minmax(190px,1fr)); gap:0.75rem;">
              <div class="card portal-wish" *ngFor="let p of wishShown()" style="padding:0.6rem; display:flex; flex-direction:column; gap:0.3rem;">
                <a [routerLink]="['/shop']" [queryParams]="{ product: p.id }"><img *ngIf="p.imageUrl" [src]="img(p.imageUrl)" [alt]="p.name" style="width:100%; height:130px; object-fit:contain;" /></a>
                <a [routerLink]="['/shop']" [queryParams]="{ product: p.id }" style="font-weight:600; color:inherit; text-decoration:none;">{{ p.name }}</a>
                <div>{{ p.price | shopMoney: currency }}</div>
                <div style="font-size:0.8rem;" [style.color]="p.stock === 'SOLD_OUT' ? '#b91c1c' : p.stock === 'LOW' ? '#b45309' : '#15803d'">{{ (p.stock === 'SOLD_OUT' ? 'Out of stock' : p.stock === 'LOW' ? 'Few left' : 'In stock') | translate }}</div>
                <div class="flex gap-1" style="margin-top:auto;">
                  <button class="btn btn-sm btn-primary portal-wish-add" style="flex:1;" [disabled]="p.stock === 'SOLD_OUT' || !shop.open" (click)="addToCart.emit(p)">{{ (p.variants ? 'Choose options' : 'Add to cart') | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary portal-wish-remove" [attr.aria-label]="'Remove from my wish list' | translate" (click)="wish.toggle(p.id)">♥</button>
                </div>
              </div>
            </div>
          </div>

          <!-- My returns -->
          <div *ngIf="section === 'returns'" class="portal-returns">
            <h4 style="margin-top:0;">{{ 'My returns' | translate }}</h4>
            <p class="text-muted" style="font-size:0.9rem;">{{ 'To return goods, open the order (My orders > Track) once it is completed.' | translate }}</p>
            <div class="text-muted" *ngIf="returns === null">{{ 'Loading…' | translate }}</div>
            <div class="text-muted portal-no-returns" *ngIf="returns && returns.length === 0">{{ 'You have not asked to return anything.' | translate }}</div>
            <div class="portal-row portal-return" *ngFor="let r of returns">
              <div class="flex justify-between" style="gap:0.5rem; flex-wrap:wrap;">
                <div>
                  <strong>{{ r.data.code }}</strong>
                  <span class="badge" style="margin-left:0.4rem;" [style.color]="returnColor(r.data.status)">{{ returnLabel(r.data.status) | translate }}</span>
                  <div class="text-muted" style="font-size:0.85rem;">{{ r.data.requestedAt | date: 'mediumDate' }} · <a [routerLink]="['/shop/order', r.orderCode]">{{ 'Order {p0}' | translate: { p0: r.orderCode } }}</a></div>
                </div>
                <strong *ngIf="r.data.status === 'REFUNDED'" style="color:#15803d;">{{ 'Refunded {p0}.' | translate: { p0: (r.data.refundAmount | shopMoney: currency) } }}</strong>
              </div>
              <ul style="margin:0.3rem 0; padding-left:1.1rem;"><li *ngFor="let l of r.data.lines">{{ l.quantity }} × {{ l.name }}</li></ul>
              <div class="text-muted" style="font-size:0.85rem;">{{ 'Reason: {p0}' | translate: { p0: r.data.reason } }}</div>
              <div *ngIf="r.data.staffNote" class="text-muted" style="font-size:0.85rem;">{{ 'The shop says: {p0}' | translate: { p0: r.data.staffNote } }}</div>
            </div>
          </div>

          <!-- Addresses, points, profile: the same screens as before, one page each -->
          <ng-container *ngIf="section === 'addresses' || section === 'points' || section === 'profile'">
            <h4 style="margin-top:0;">{{ (section === 'addresses' ? 'My delivery addresses' : section === 'points' ? 'Loyalty points' : 'My profile') | translate }}</h4>
            <app-shop-account [section]="$any(section)" [profile]="profile" [currency]="currency" [textChannel]="shop.textChannel" [loyalty]="shop.loyalty" [addresses]="addresses" [zones]="shop.zones"
              (addressesChange)="addressesChange.emit($event)" (profileChange)="profileChange.emit($event)" (closed)="closed.emit()"></app-shop-account>
          </ng-container>
        </section>
      </div>
    </div>
  `,
})
export class ShopPortalComponent implements OnInit, OnChanges {
  @Input() section: PortalSection = 'orders';
  @Input() shop!: Shop;
  @Input() profile: AccountProfile | null = null;
  @Input() currency = '';
  @Input() addresses: ShopAddress[] = [];
  @Output() addressesChange = new EventEmitter<ShopAddress[]>();
  @Output() profileChange = new EventEmitter<AccountProfile | null>();
  @Output() reordered = new EventEmitter<Reorder>();
  @Output() addToCart = new EventEmitter<ShopProduct>();
  @Output() closed = new EventEmitter<void>();

  orders: AccountOrder[] | null = null;
  returns: AccountReturn[] | null = null;
  wishProducts: ShopProduct[] = [];
  wishBusy = false;
  busy = false;
  error: string | null = null;
  info: string | null = null;
  orderFilter: 'ALL' | 'OPEN' | 'DONE' | 'CLOSED' = 'ALL';
  orderFilters: { key: 'ALL' | 'OPEN' | 'DONE' | 'CLOSED'; label: string }[] = [
    { key: 'ALL', label: 'All' }, { key: 'OPEN', label: 'In progress' }, { key: 'DONE', label: 'Delivered' }, { key: 'CLOSED', label: 'Cancelled' },
  ];

  constructor(private api: OnlineStoreApiService, public wish: WishlistService) {}

  ngOnInit(): void {
    this.loadAll();
  }

  ngOnChanges(c: SimpleChanges): void {
    if (c['profile'] && !c['profile'].firstChange) this.loadAll();
    if (c['section'] && !c['section'].firstChange) {
      this.error = null;
      this.info = null;
      if (this.section === 'wishlist') this.loadWish();
    }
  }

  menu(): { key: PortalSection; icon: string; label: string }[] {
    const all: { key: PortalSection; icon: string; label: string }[] = [
      { key: 'orders', icon: '📦', label: 'My orders' }, { key: 'invoices', icon: '🧾', label: 'My invoices' }, { key: 'wishlist', icon: '♥', label: 'My wish list' },
      { key: 'returns', icon: '↩', label: 'My returns' }, { key: 'addresses', icon: '📍', label: 'My addresses' }, { key: 'points', icon: '⭐', label: 'Loyalty points' },
      { key: 'profile', icon: '👤', label: 'My profile' },
    ];
    return all.filter((m) => m.key !== 'points' || this.shop?.loyalty);
  }

  count(key: PortalSection): number {
    switch (key) {
      case 'orders': return this.ordersIn('OPEN').length;
      case 'invoices': return this.invoiced().length;
      case 'wishlist': return this.wish.count;
      case 'returns': return this.returns ? this.returns.filter((r) => r.data.status === 'REQUESTED' || r.data.status === 'APPROVED').length : 0;
      default: return 0;
    }
  }

  private loadAll(): void {
    if (!this.profile) return;
    this.orders = null;
    this.returns = null;
    this.api.accountOrders().subscribe({ next: (o) => (this.orders = o), error: (e) => this.failed(e) });
    this.api.accountReturns().subscribe({ next: (r) => (this.returns = r), error: (e) => { this.returns = []; this.failed(e); } });
    this.loadWish();
  }

  private failed(e: { status?: number; error?: { message?: string } }): void {
    if (e?.status === 401) {
      // the session ended (or was removed elsewhere): back to signed out
      shopToken.clear();
      this.profileChange.emit(null);
      return;
    }
    this.error = e?.error?.message ?? T('Something went wrong. Please try again.');
  }

  /** The orders of one filter: in progress = anything not yet delivered, delivered = completed, cancelled = cancelled or refused. */
  ordersIn(key: string): AccountOrder[] {
    const all = this.orders ?? [];
    switch (key) {
      case 'OPEN': return all.filter((o) => !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(o.status));
      case 'DONE': return all.filter((o) => o.status === 'COMPLETED');
      case 'CLOSED': return all.filter((o) => o.status === 'CANCELLED' || o.status === 'REJECTED');
      default: return all;
    }
  }

  invoiced(): AccountOrder[] {
    return (this.orders ?? []).filter((o) => !!o.invoiceNumber);
  }

  statusLabel(s: string): string {
    return ({ AWAITING_PAYMENT: 'Waiting for payment', PLACED: 'Received', ACCEPTED: 'Accepted', PACKED: 'Packed', OUT_FOR_DELIVERY: 'On its way', READY_FOR_PICKUP: 'Ready for pick-up',
      COMPLETED: 'Done', REJECTED: 'Not accepted', CANCELLED: 'Cancelled' } as Record<string, string>)[s] ?? s;
  }

  statusColor(s: string): string {
    return s === 'COMPLETED' ? '#15803d' : s === 'CANCELLED' || s === 'REJECTED' ? '#b91c1c' : '#b45309';
  }

  returnLabel(s: string): string {
    return ({ REQUESTED: 'Waiting for an answer', APPROVED: 'Approved', REFUSED: 'Refused', REFUNDED: 'Refunded' } as Record<string, string>)[s] ?? s;
  }

  returnColor(s: string): string {
    return s === 'REFUSED' ? '#b91c1c' : s === 'REFUNDED' ? '#15803d' : s === 'APPROVED' ? '#1d4ed8' : '#b45309';
  }

  reorder(o: AccountOrder): void {
    this.error = null;
    this.busy = true;
    this.api.accountReorder(o.code).subscribe({
      next: (r) => { this.busy = false; this.reordered.emit(r); },
      error: (e) => { this.busy = false; this.failed(e); },
    });
  }

  /** The invoice as a PDF in a new tab (the signed-in account proves the order is theirs). */
  invoice(o: AccountOrder): void {
    this.error = null;
    this.busy = true;
    this.api.invoicePdf(o.code, null).subscribe({
      next: (b) => { this.busy = false; window.open(URL.createObjectURL(b), '_blank'); },
      error: () => { this.busy = false; this.error = T('The invoice could not be opened. Please try again.'); },
    });
  }

  // ---- wish list: the products behind the hearts, as the shop has them today
  private loadWish(): void {
    const ids = this.wish.ids();
    if (!ids.length) { this.wishProducts = []; return; }
    this.wishBusy = true;
    this.api.wished(ids).subscribe({ next: (r) => { this.wishProducts = r; this.wishBusy = false; }, error: () => { this.wishProducts = []; this.wishBusy = false; } });
  }

  wishShown(): ShopProduct[] {
    const byId = new Map(this.wishProducts.map((p) => [p.id, p]));
    return this.wish.ids().map((id) => byId.get(id)).filter((p): p is ShopProduct => !!p);
  }

  img(u: string): string {
    return this.api.imageUrl(u) + '?w=320';
  }
}
