import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListState, emptyFilter } from '../core/api/list-state';
import { OnlineStoreApiService, StockAlert, StockDemand } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Waiting customers: the customers who asked to be told when a sold-out product is back. The top shows the products they want, the most
 * wanted first (what to restock); the list shows each person. When a product can be sold again the shop e-mails (and texts) them by itself within a few
 * minutes; here staff can also write to them on WhatsApp or by phone and mark them as told.
 */
@Component({
  selector: 'app-store-stock-alerts',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Waiting customers' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary sa-notify" [disabled]="busy" (click)="notifyNow()">{{ 'Tell them now' | translate }}</button>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'Customers who asked to be told when a sold-out product is back. When the product can be sold again the shop tells them by e-mail (and by text message when that is set up) within a few minutes. Others you can reach by WhatsApp or phone.' | translate }}</p>

      <div class="card mb-3 sa-demand" style="padding:0.75rem;" *ngIf="demand.length">
        <strong>{{ 'Products customers are waiting for' | translate }}</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <button *ngFor="let d of demand" type="button" class="btn btn-sm sa-product" [class.btn-primary]="productFilter === d.productId" [class.btn-outline-secondary]="productFilter !== d.productId" (click)="pickProduct(d.productId)">
            {{ d.productName }} · {{ d.waiting }}
            <span class="sa-back" *ngIf="d.stock !== 'SOLD_OUT' && d.stock !== 'OFF'" style="color:#15803d; font-weight:600;"> · {{ 'back in stock' | translate }}</span>
            <span *ngIf="d.stock === 'OFF'" class="text-muted"> · {{ 'not on the shop' | translate }}</span>
          </button>
          <button *ngIf="productFilter !== null" type="button" class="btn btn-sm btn-outline-secondary" (click)="pickProduct(null)">✕ {{ 'All products' | translate }}</button>
        </div>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm sa-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">{{ t.label | translate }}</button>
        <input class="form-control sa-search" style="width:220px;" [placeholder]="'Search product, e-mail, phone' | translate" [(ngModel)]="q" name="saq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <div class="card sa-card mb-2" *ngFor="let a of list.rows" style="padding:0.75rem;">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="sa-name">{{ a.productName }}</strong>
            <span class="badge sa-status" style="margin-left:0.4rem;" [style.color]="color(a)">{{ statusLabel(a) | translate }}</span>
            <div class="text-muted">
              <span *ngIf="a.email">✉️ {{ a.email }}</span><span *ngIf="a.phone && a.email"> · </span><span *ngIf="a.phone">📞 {{ a.phone }}</span>
            </div>
          </div>
          <div class="text-muted" style="font-size:0.8rem; text-align:right;">
            {{ a.createdAt | date: 'short' }}
            <div *ngIf="a.notifiedAt">{{ 'Told {p0}' | translate: { p0: (a.notifiedAt | date: 'short') } }}<span *ngIf="a.notifiedBy"> · {{ a.notifiedBy === 'automatic' ? ('automatically' | translate) : a.notifiedBy }}</span></div>
          </div>
        </div>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:center;">
          <a class="btn btn-sm btn-outline-secondary sa-wa" *ngIf="waLink(a)" [href]="waLink(a)" target="_blank" rel="noopener">WhatsApp</a>
          <button class="btn btn-sm btn-primary sa-told" *ngIf="a.status !== 'NOTIFIED'" [disabled]="busy" (click)="told(a)">{{ 'Mark as told' | translate }}</button>
          <button class="btn btn-sm btn-outline-danger sa-delete" [disabled]="busy" (click)="remove(a)">{{ 'Remove' | translate }}</button>
        </div>
      </div>
      <p class="text-muted sa-empty" *ngIf="list.loaded && list.rows.length === 0">{{ 'Nobody is waiting.' | translate }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreStockAlertsComponent implements OnInit, OnDestroy {
  tabs = [
    { key: 'WAITING', label: 'Waiting' },
    { key: 'NOTIFIED', label: 'Told' },
    { key: 'FAILED', label: 'Could not be told' },
    { key: '', label: 'Everything' },
  ];
  tab = 'WAITING';
  q = '';
  productFilter: number | null = null;
  demand: StockDemand[] = [];
  error: string | null = null;
  message: string | null = null;
  busy = false;
  private countryCode = '';
  list = new ListState<StockAlert>((f, p, sz) => this.api.stockAlerts(f, p, sz, this.productFilter), 'Failed to load the waiting customers');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.countryCode = (s.textCountryCode ?? '').replace(/[^0-9]/g, '')), error: () => {} });
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.loadDemand();
    }, 30000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  reload(): void {
    const f = emptyFilter();
    f.q = this.q;
    f.status = this.tab;
    this.list.apply(f);
    this.loadDemand();
  }

  private loadDemand(): void {
    this.api.stockDemand().subscribe({ next: (d) => (this.demand = d), error: () => {} });
  }

  setTab(t: string): void {
    this.tab = t;
    this.reload();
  }

  pickProduct(id: number | null): void {
    this.productFilter = this.productFilter === id ? null : id;
    this.reload();
  }

  statusLabel(a: StockAlert): string {
    return a.status === 'NOTIFIED' ? 'Told' : a.status === 'FAILED' ? 'Could not be told' : 'Waiting';
  }

  color(a: StockAlert): string {
    return a.status === 'NOTIFIED' ? '#15803d' : a.status === 'FAILED' ? '#b91c1c' : '#b45309';
  }

  /** A WhatsApp chat with the customer, with the good news and the link already written. */
  waLink(a: StockAlert): string | null {
    let d = (a.phone ?? '').replace(/[^0-9]/g, '');
    if (d.startsWith('0') && this.countryCode) d = this.countryCode + d.substring(1);
    if (d.length < 8) return null;
    const text = T('Hello! {p0} is back in stock: {p1}', { p0: a.productName, p1: `${location.origin}/shop?product=${a.productId}` });
    return `https://wa.me/${d}?text=${encodeURIComponent(text)}`;
  }

  private run(call: import('rxjs').Observable<unknown>, done?: string): void {
    this.busy = true;
    this.error = null;
    this.message = null;
    call.subscribe({
      next: () => {
        this.busy = false;
        if (done) this.message = done;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }

  told(a: StockAlert): void {
    this.run(this.api.stockAlertTold(a.id));
  }

  remove(a: StockAlert): void {
    if (!confirm(T('Remove this request?'))) return;
    this.run(this.api.stockAlertDelete(a.id));
  }

  notifyNow(): void {
    this.busy = true;
    this.error = null;
    this.message = null;
    this.api.stockAlertsNotify().subscribe({
      next: (r) => {
        this.busy = false;
        this.message = r.told > 0 ? T('{p0} customer(s) told.', { p0: r.told }) : T('Nobody to tell right now: no waiting product can be sold yet, or e-mail and text messages are not set up.');
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
