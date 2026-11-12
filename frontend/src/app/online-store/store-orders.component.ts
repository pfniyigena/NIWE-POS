import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListState, emptyFilter } from '../core/api/list-state';
import { OnlineStoreApiService, StoreOrder } from '../core/api/online-store-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption, Warehouse, WarehouseLocation } from '../core/models';
import { T } from '../core/i18n/t';

interface LocationOption { id: number; label: string; type: string | null; primaryWarehouse: boolean }

/**
 * Online Store > Orders: what customers ordered on the public shop. Accepting makes a confirmed sale order (it holds the stock); the order then
 * moves to packed, out for delivery or ready for pick-up, and completing it takes the stock out, issues the invoice and records the cash. The list
 * refreshes by itself so it can stay open on a counter screen.
 */
@Component({
  selector: 'app-store-orders',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Online orders' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
          <a class="btn btn-outline-secondary" href="/shop" target="_blank" rel="noopener">{{ 'Open the shop' | translate }}</a>
        </div>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm so-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">
          {{ t.label | translate }}<span *ngIf="t.key === 'PLACED' && pending > 0"> ({{ pending }}<ng-container *ngIf="overdue > 0">, {{ overdue }} {{ 'late' | translate }}</ng-container>)</span>
        </button>
        <input class="form-control so-search" style="width:220px;" [placeholder]="'Search code, name, phone' | translate" [(ngModel)]="q" name="soq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <div class="card so-card mb-2" *ngFor="let o of list.rows" style="padding:0.75rem;" [style.border-left]="o.status === 'PLACED' && late(o) ? '4px solid #b91c1c' : ''">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="so-code">{{ o.code }}</strong>
            <span class="badge" style="margin-left:0.4rem;">{{ o.deliveryMethod === 'DELIVERY' ? ('Delivery' | translate) : ('Pick-up' | translate) }}</span>
            <span class="so-status text-muted" style="margin-left:0.4rem;">{{ statusLabel(o.status) | translate }}<ng-container *ngIf="o.decidedBy"> · {{ o.decidedBy }}</ng-container></span>
            <div>{{ o.customerName }} · {{ o.customerPhone }}<span class="text-muted" *ngIf="o.customerEmail"> · {{ o.customerEmail }}</span></div>
            <div class="text-muted" *ngIf="o.customerAddress">📍 {{ o.zoneName ? o.zoneName + ' — ' : '' }}{{ o.customerAddress }}</div>
            <div class="text-muted" *ngIf="o.notes">“{{ o.notes }}”</div>
          </div>
          <div style="text-align:right;">
            <div><strong>{{ o.total | number: '1.0-2' }}</strong></div>
            <div class="text-muted" style="font-size:0.8rem;">{{ o.createdAt | date: 'short' }}</div>
            <div *ngIf="o.status === 'PLACED'" class="so-waiting" style="font-size:0.8rem;" [style.color]="late(o) ? '#b91c1c' : ''" [style.font-weight]="late(o) ? '600' : '400'">{{ 'Waiting {p0} min' | translate: { p0: waiting(o) } }}</div>
            <a *ngIf="o.salesOrderId" [routerLink]="['/admin/sales-orders', o.salesOrderId]">{{ o.salesOrderNumber }}</a>
            <a *ngIf="o.invoiceId" style="margin-left:0.5rem;" [routerLink]="['/admin/invoices', o.invoiceId]">{{ o.invoiceNumber }}</a>
          </div>
        </div>
        <ul style="margin:0.4rem 0; padding-left:1.1rem;">
          <li *ngFor="let l of o.lines">{{ l.quantity }} × {{ l.name }} <span class="text-muted">({{ l.lineTotal | number: '1.0-2' }})</span></li>
          <li *ngIf="o.deliveryFee > 0">{{ 'Delivery fee {p0}' | translate: { p0: (o.deliveryFee | number: '1.0-2') } }}</li>
        </ul>
        <div class="text-muted" *ngIf="o.rejectReason">{{ 'Reason: {p0}' | translate: { p0: (o.rejectReason) } }}</div>

        <div *ngIf="o.status === 'PLACED'" class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <button class="btn btn-primary so-accept" [disabled]="busy" (click)="accept(o, false)">{{ 'Accept' | translate }}</button>
          <button *ngIf="shortage[o.id]" class="btn btn-outline-warning so-accept-anyway" [disabled]="busy" (click)="accept(o, true)">{{ 'Accept anyway' | translate }}</button>
          <input class="form-control so-reason" style="width:220px;" maxlength="160" [placeholder]="'Reason, if you refuse' | translate" [(ngModel)]="reason[o.id]" [name]="'rsn' + o.id" />
          <button class="btn btn-outline-danger so-reject" [disabled]="busy" (click)="reject(o)">{{ 'Reject' | translate }}</button>
        </div>

        <div *ngIf="o.status === 'ACCEPTED' || o.status === 'PACKED'" class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <button class="btn btn-outline-secondary so-pack" *ngIf="o.status === 'ACCEPTED'" [disabled]="busy" (click)="run(api.pack(o.id))">{{ 'Mark packed' | translate }}</button>
          <button class="btn btn-primary so-out" *ngIf="o.deliveryMethod === 'DELIVERY'" [disabled]="busy" (click)="run(api.outForDelivery(o.id))">{{ 'Out for delivery' | translate }}</button>
          <button class="btn btn-primary so-ready" *ngIf="o.deliveryMethod === 'PICKUP'" [disabled]="busy" (click)="run(api.readyForPickup(o.id))">{{ 'Ready for pick-up' | translate }}</button>
          <button class="btn btn-outline-danger so-cancel" [disabled]="busy" (click)="cancel(o)">{{ 'Cancel order' | translate }}</button>
        </div>

        <div *ngIf="o.status === 'OUT_FOR_DELIVERY' || o.status === 'READY_FOR_PICKUP'" class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" *ngIf="multiWarehouse || fulfilOptions.length > 1">
            <label class="form-label">{{ 'Take stock from' | translate }}</label>
            <select class="form-select" style="width:240px;" [(ngModel)]="location[o.id]" [name]="'loc' + o.id">
              <option [ngValue]="null" [disabled]="multiWarehouse">{{ multiWarehouse ? ('Select a location…' | translate) : ('Default (main warehouse)' | translate) }}</option>
              <option *ngFor="let l of fulfilOptions" [ngValue]="l.id">{{ l.label }}</option>
            </select>
          </div>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" [ngModel]="collect[o.id] !== false" (ngModelChange)="collect[o.id] = $event" [name]="'col' + o.id" /> {{ 'Cash collected' | translate }}</label>
          <select *ngIf="collect[o.id] !== false && methods.length > 1" class="form-select" style="width:160px;" [(ngModel)]="method[o.id]" [name]="'met' + o.id">
            <option *ngFor="let m of methods" [ngValue]="m.code">{{ m.name }}</option>
          </select>
          <button class="btn btn-primary so-complete" [disabled]="busy" (click)="complete(o)">{{ 'Complete: hand over, invoice and record payment' | translate }}</button>
          <button class="btn btn-outline-danger so-cancel" [disabled]="busy" (click)="cancel(o)">{{ 'Cancel order' | translate }}</button>
        </div>

        <details class="mt-2" *ngIf="o.events.length > 1">
          <summary class="text-muted" style="cursor:pointer;">{{ 'History' | translate }}</summary>
          <div *ngFor="let e of o.events" class="text-muted" style="font-size:0.85rem;">{{ e.at | date: 'short' }} · {{ statusLabel(e.status) | translate }}<span *ngIf="e.by"> · {{ e.by }}</span><span *ngIf="e.note"> — {{ e.note }}</span></div>
        </details>
      </div>
      <p class="text-muted so-empty" *ngIf="list.loaded && list.rows.length === 0">{{ tab === 'PLACED' ? ('Nothing is waiting for an answer.' | translate) : ('Nothing to show.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreOrdersComponent implements OnInit, OnDestroy {
  tabs = [
    { key: 'PLACED', label: 'New' },
    { key: 'OPEN', label: 'Open' },
    { key: 'COMPLETED', label: 'Completed' },
    { key: 'REJECTED', label: 'Rejected' },
    { key: 'CANCELLED', label: 'Cancelled' },
    { key: '', label: 'Everything' },
  ];
  tab = 'PLACED';
  q = '';
  pending = 0;
  overdue = 0;
  alertMinutes = 0;
  error: string | null = null;
  busy = false;
  reason: Record<number, string> = {};
  shortage: Record<number, boolean> = {};
  location: Record<number, number | null> = {};
  collect: Record<number, boolean> = {};
  method: Record<number, string> = {};
  multiWarehouse = false;
  fulfilOptions: LocationOption[] = [];
  methods: PaymentMethodOption[] = [];
  list = new ListState<StoreOrder>((f, p, sz) => this.api.page(f, p, sz), 'Failed to load the online orders');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(public api: OnlineStoreApiService, private warehouseApi: WarehouseApiService, private storeSettingsApi: StoreSettingsApiService, private paymentMethodApi: PaymentMethodApiService) {}

  ngOnInit(): void {
    this.loadOptions();
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.list.load();
      this.count();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  /** Never blocks the page: if settings, warehouses or payment methods cannot be read, the pickers stay hidden and the defaults apply. */
  private loadOptions(): void {
    this.paymentMethodApi.listActive().pipe(catchError(() => of([] as PaymentMethodOption[]))).subscribe((m) => (this.methods = m.filter((x) => x.cash)));
    this.storeSettingsApi.get().pipe(catchError(() => of(null))).subscribe((settings) => {
      this.multiWarehouse = !!settings?.multiWarehouseEnabled;
      this.warehouseApi.listAll().pipe(catchError(() => of([] as Warehouse[]))).subscribe((all) => {
        const active = all.filter((w) => w.active);
        if (active.length === 0) return;
        forkJoin(active.map((w) => this.warehouseApi.listLocations(w.id).pipe(catchError(() => of([] as WarehouseLocation[]))))).subscribe((perWarehouse) => {
          const options: LocationOption[] = [];
          active.forEach((w, i) => {
            for (const l of perWarehouse[i]) {
              if (l.active) options.push({ id: l.id, label: `${w.name} / ${l.name}`, type: l.type, primaryWarehouse: w.primary });
            }
          });
          // Goods only ever leave from SALES locations, and with multi-warehouse on never from the Main warehouse.
          this.fulfilOptions = options.filter((o) => o.type === 'SALES' && !(this.multiWarehouse && o.primaryWarehouse));
        });
      });
    });
  }

  statusLabel(s: string): string {
    const labels: Record<string, string> = {
      PLACED: 'New', ACCEPTED: 'Accepted', PACKED: 'Packed', OUT_FOR_DELIVERY: 'Out for delivery', READY_FOR_PICKUP: 'Ready for pick-up',
      COMPLETED: 'Completed', REJECTED: 'Rejected', CANCELLED: 'Cancelled',
    };
    return labels[s] ?? s;
  }

  setTab(t: string): void {
    this.tab = t;
    this.reload();
  }

  reload(): void {
    const f = emptyFilter();
    f.q = this.q;
    f.status = this.tab;
    this.list.apply(f);
    this.count();
  }

  /** Whole minutes since the customer placed the order. */
  waiting(o: StoreOrder): number {
    return Math.max(0, Math.floor((Date.now() - new Date(o.createdAt).getTime()) / 60000));
  }

  /** Waiting longer than the alert time set in the shop settings (no alert time = never late). */
  late(o: StoreOrder): boolean {
    return this.alertMinutes > 0 && this.waiting(o) >= this.alertMinutes;
  }

  count(): void {
    this.api.pendingCount().subscribe({
      next: (r) => {
        this.pending = r.pending;
        this.overdue = r.overdue ?? 0;
        this.alertMinutes = r.alertMinutes ?? 0;
      },
      error: () => {},
    });
  }

  /** Runs one order action and refreshes the list. */
  run(call: import('rxjs').Observable<StoreOrder>, fail?: (e: any) => void): void {
    this.busy = true;
    this.error = null;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        if (fail) fail(e);
        else this.error = e?.error?.message ?? T('That did not work');
        this.list.load();
      },
    });
  }

  accept(o: StoreOrder, anyway: boolean): void {
    this.run(this.api.accept(o.id, anyway), (e) => {
      // 409: the stock does not cover the order. Staff may accept it anyway (the sale order is then over-promised) or reject it.
      this.shortage[o.id] = e?.status === 409;
      this.error = e?.error?.message ?? T('Could not accept the order');
    });
  }

  reject(o: StoreOrder): void {
    const reason = (this.reason[o.id] ?? '').trim();
    if (!reason) {
      this.error = T('Write a reason: the customer will see it.');
      return;
    }
    this.run(this.api.reject(o.id, reason));
  }

  cancel(o: StoreOrder): void {
    if (!confirm(T('Cancel order {p0}? The stock it holds is released.', { p0: o.code }))) return;
    this.run(this.api.cancelOrder(o.id, ''));
  }

  complete(o: StoreOrder): void {
    if (this.multiWarehouse && (this.location[o.id] ?? null) === null) {
      this.error = T('Choose the warehouse location the goods leave from.');
      return;
    }
    this.run(this.api.complete(o.id, { locationId: this.location[o.id] ?? null, collectPayment: this.collect[o.id] !== false, method: this.method[o.id] || 'CASH' }));
  }
}
