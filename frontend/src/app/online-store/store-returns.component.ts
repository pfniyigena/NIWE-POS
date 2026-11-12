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
import { OnlineStoreApiService, StoreReturn } from '../core/api/online-store-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { Warehouse, WarehouseLocation } from '../core/models';
import { T } from '../core/i18n/t';

interface LocationOption { id: number; label: string }

/**
 * Online Store > Returns: what customers asked to send back after their order was completed. Staff approve or refuse; when the goods are back they
 * process the return, which puts them in stock through the sale order return (so the books and reports follow) and gives the money back the way the
 * customer paid: through the payment provider or by hand for money paid online, in cash for cash on delivery.
 */
@Component({
  selector: 'app-store-returns',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Returns' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/orders">{{ 'Online orders' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'A customer asks to return goods from a completed order. Approve or refuse the request; when the goods are back, process the return: they go back in stock, the sale is reduced in the books, and the money is given back the way the customer paid. The delivery fee is not refunded.' | translate }}</p>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm rt-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">
          {{ t.label | translate }}<span *ngIf="t.key === 'REQUESTED' && requested > 0"> ({{ requested }})</span><span *ngIf="t.key === 'APPROVED' && toRefund > 0"> ({{ toRefund }})</span>
        </button>
        <input class="form-control rt-search" style="width:220px;" [placeholder]="'Search code, name, phone' | translate" [(ngModel)]="q" name="rtq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <div class="card rt-card mb-2" *ngFor="let r of list.rows" style="padding:0.75rem;">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="rt-code">{{ r.code }}</strong>
            <span class="badge rt-status" style="margin-left:0.4rem;" [style.color]="color(r)">{{ label(r.status) | translate }}</span>
            <div>{{ r.customerName }} · {{ r.customerPhone }}<span class="text-muted" *ngIf="r.customerEmail"> · {{ r.customerEmail }}</span></div>
            <div class="text-muted">{{ 'Order {p0}' | translate: { p0: r.orderCode } }} · {{ payWay(r) | translate }}</div>
          </div>
          <div style="text-align:right;">
            <div><strong>{{ r.refundAmount | number: '1.0-2' }}</strong></div>
            <div class="text-muted" style="font-size:0.8rem;">{{ r.requestedAt | date: 'short' }}</div>
          </div>
        </div>
        <ul style="margin:0.4rem 0; padding-left:1.1rem;">
          <li *ngFor="let l of r.lines">{{ l.quantity }} × {{ l.name }} <span class="text-muted">({{ l.amount | number: '1.0-2' }})</span></li>
        </ul>
        <div>“{{ r.reason }}”</div>
        <div class="text-muted" *ngIf="r.staffNote" style="font-size:0.85rem;">{{ 'Staff note: {p0}' | translate: { p0: r.staffNote } }}<span *ngIf="r.decidedBy"> · {{ r.decidedBy }}</span></div>

        <div *ngIf="r.status === 'REQUESTED'" class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <input class="form-control rt-note" style="width:300px;" maxlength="300" [placeholder]="'Note for the customer (needed to refuse)' | translate" [(ngModel)]="note[r.id]" [name]="'rn' + r.id" />
          <button class="btn btn-primary rt-approve" [disabled]="busy" (click)="approve(r)">{{ 'Approve' | translate }}</button>
          <button class="btn btn-outline-danger rt-refuse" [disabled]="busy" (click)="refuse(r)">{{ 'Refuse' | translate }}</button>
        </div>

        <div *ngIf="r.status === 'APPROVED'" class="rt-process mt-2" style="border:1px solid #1d4ed8; border-radius:6px; padding:0.6rem;">
          <strong style="color:#1d4ed8;">{{ 'Waiting for the goods' | translate }}</strong>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'When the customer has brought the goods back, process the return: stock back, books, and the refund.' | translate }}</div>
          <div class="flex gap-2 mt-1" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" *ngIf="locations.length">
              <label class="form-label">{{ 'Put the goods back in' | translate }}</label>
              <select class="form-select rt-location" style="width:240px;" [(ngModel)]="location[r.id]" [name]="'rl' + r.id">
                <option [ngValue]="null">{{ 'Where they were taken from' | translate }}</option>
                <option *ngFor="let l of locations" [ngValue]="l.id">{{ l.label }}</option>
              </select>
            </div>
            <ng-container *ngIf="online(r); else cash">
              <label class="flex gap-1" style="align-items:center;"><input type="radio" [name]="'rw' + r.id" [value]="false" [(ngModel)]="manual[r.id]" /> {{ 'Refund through the provider' | translate }}</label>
              <label class="flex gap-1" style="align-items:center;"><input type="radio" [name]="'rw' + r.id" [value]="true" [(ngModel)]="manual[r.id]" /> {{ 'I refunded it myself' | translate }}</label>
              <input *ngIf="manual[r.id]" class="form-control rt-ref" style="width:200px;" maxlength="80" [placeholder]="'Reference of your refund' | translate" [(ngModel)]="ref[r.id]" [name]="'rr' + r.id" />
            </ng-container>
            <ng-template #cash>
              <span class="text-muted">{{ 'The customer paid in cash: hand {p0} back.' | translate: { p0: (r.refundAmount | number: '1.0-2') } }}</span>
            </ng-template>
            <button class="btn btn-primary rt-process-btn" [disabled]="busy" (click)="process(r)">{{ 'Goods received: put back and refund' | translate }}</button>
            <button class="btn btn-outline-danger rt-refuse" [disabled]="busy" (click)="refuse(r)">{{ 'Refuse' | translate }}</button>
          </div>
          <input class="form-control mt-1 rt-note" style="max-width:420px;" maxlength="300" [placeholder]="'Note (optional; needed to refuse)' | translate" [(ngModel)]="note[r.id]" [name]="'rn' + r.id" />
        </div>

        <div *ngIf="r.status === 'REFUNDED'" class="text-muted rt-done mt-1" style="font-size:0.85rem;">
          {{ (r.refundMethod === 'CASH' ? 'Handed back {p0} in cash' : r.refundMethod === 'MANUAL' ? 'Refunded {p0} by hand' : 'Refunded {p0} through the provider') | translate: { p0: (r.refundAmount | number: '1.0-2') } }}<span *ngIf="r.refundReference"> · {{ r.refundReference }}</span>
          · {{ r.refundedAt | date: 'short' }}<span *ngIf="r.refundedBy"> · {{ r.refundedBy }}</span><span *ngIf="r.salesOrderReturnNumber"> · {{ 'Sale order return {p0}' | translate: { p0: r.salesOrderReturnNumber } }}</span>
        </div>
      </div>
      <p class="text-muted rt-empty" *ngIf="list.loaded && list.rows.length === 0">{{ tab === 'REQUESTED' ? ('No return is waiting for an answer.' | translate) : ('Nothing to show.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreReturnsComponent implements OnInit, OnDestroy {
  tabs = [
    { key: 'REQUESTED', label: 'Waiting' },
    { key: 'APPROVED', label: 'To refund' },
    { key: 'REFUNDED', label: 'Done' },
    { key: 'REFUSED', label: 'Refused' },
    { key: '', label: 'Everything' },
  ];
  tab = 'REQUESTED';
  q = '';
  requested = 0;
  toRefund = 0;
  error: string | null = null;
  message: string | null = null;
  busy = false;
  note: Record<number, string> = {};
  ref: Record<number, string> = {};
  manual: Record<number, boolean> = {};
  location: Record<number, number | null> = {};
  locations: LocationOption[] = [];
  list = new ListState<StoreReturn>((f, p, sz) => this.api.returnsPage(f, p, sz), 'Failed to load the returns');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService, private warehouseApi: WarehouseApiService) {}

  ngOnInit(): void {
    this.loadLocations();
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.list.load();
      this.count();
    }, 15000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  /** Never blocks the page: without locations the goods go back where they were taken from. */
  private loadLocations(): void {
    this.warehouseApi.listAll().pipe(catchError(() => of([] as Warehouse[]))).subscribe((all) => {
      const active = all.filter((w) => w.active);
      if (active.length === 0) return;
      forkJoin(active.map((w) => this.warehouseApi.listLocations(w.id).pipe(catchError(() => of([] as WarehouseLocation[]))))).subscribe((perWarehouse) => {
        const options: LocationOption[] = [];
        active.forEach((w, i) => {
          for (const l of perWarehouse[i]) if (l.active) options.push({ id: l.id, label: `${w.name} / ${l.name}` });
        });
        this.locations = options.length > 1 ? options : [];
      });
    });
  }

  label(s: string): string {
    return ({ REQUESTED: 'Waiting', APPROVED: 'Approved', REFUSED: 'Refused', REFUNDED: 'Done' } as Record<string, string>)[s] ?? s;
  }

  color(r: StoreReturn): string {
    return r.status === 'REFUSED' ? '#b91c1c' : r.status === 'REFUNDED' ? '#15803d' : r.status === 'APPROVED' ? '#1d4ed8' : '#b45309';
  }

  /** Money paid online is given back through the provider or by hand; cash on delivery is handed back in cash. */
  online(r: StoreReturn): boolean {
    return r.paymentMethod === 'ONLINE';
  }

  payWay(r: StoreReturn): string {
    return this.online(r) ? 'Paid online' : 'Paid in cash';
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

  count(): void {
    this.api.pendingCount().subscribe({
      next: (c) => {
        this.requested = c.returnsRequested ?? 0;
        this.toRefund = c.returnsToRefund ?? 0;
      },
      error: () => {},
    });
  }

  private run(call: import('rxjs').Observable<StoreReturn>, done: string): void {
    this.busy = true;
    this.error = null;
    this.message = null;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.message = done;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
        this.list.load();
      },
    });
  }

  approve(r: StoreReturn): void {
    this.run(this.api.approveReturn(r.id, this.note[r.id] ?? ''), T('Return {p0} approved. The customer was told.', { p0: r.code }));
  }

  refuse(r: StoreReturn): void {
    const note = (this.note[r.id] ?? '').trim();
    if (!note) {
      this.error = T('Write a reason: the customer will see it.');
      return;
    }
    this.run(this.api.refuseReturn(r.id, note), T('Return {p0} refused. The customer was told.', { p0: r.code }));
  }

  process(r: StoreReturn): void {
    const manual = this.online(r) && this.manual[r.id] === true;
    const ref = (this.ref[r.id] ?? '').trim();
    if (manual && ref.length < 3) {
      this.error = T('Enter the reference of the refund you made (at least 3 characters)');
      return;
    }
    const text = !this.online(r) ? 'Put the goods of return {p0} back in stock and record {p1} handed back in cash?'
      : manual ? 'Put the goods of return {p0} back in stock and record that you refunded {p1} yourself?'
        : 'Put the goods of return {p0} back in stock and refund {p1} through the payment provider?';
    if (!confirm(T(text, { p0: r.code, p1: String(r.refundAmount) }))) return;
    this.run(this.api.processReturn(r.id, { locationId: this.location[r.id] ?? null, manual, reference: manual ? ref : null, note: (this.note[r.id] ?? '').trim() || null }),
      T('Return {p0} done. The customer was told.', { p0: r.code }));
  }
}
