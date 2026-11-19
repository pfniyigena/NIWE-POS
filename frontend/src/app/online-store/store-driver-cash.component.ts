import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { Driver, DriverCashData, DriverCashRow, OnlineStoreApiService } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Driver cash (v1.57.0): the cash drivers took at the door and have to hand in. Each delivery order completed with cash collected waits here
 * until staff settle it with the amount really handed in. A difference needs a reason and is posted to Cash over / short in the accounts.
 */
@Component({
  selector: 'app-store-driver-cash',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success dc-done" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h2 style="margin:0;">{{ 'Driver cash' | translate }}</h2>
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <select class="form-select dc-driver" style="width:210px;" [(ngModel)]="driverId" name="dcdriver" (ngModelChange)="load()" [attr.aria-label]="'Driver' | translate">
            <option [ngValue]="null">{{ 'All drivers' | translate }}</option>
            <option [ngValue]="0">{{ 'Not assigned' | translate }}</option>
            <option *ngFor="let d of drivers" [ngValue]="d.id">{{ d.name }}</option>
          </select>
          <button class="btn btn-sm btn-outline-secondary" (click)="load()">↻ {{ 'Refresh' | translate }}</button>
        </div>
      </div>
      <p class="text-muted" style="font-size:0.9rem;">{{ 'Cash taken by a driver at the door is recorded when the order is completed. It waits here until the driver hands it in: type what he handed in. A difference needs a reason and is posted to Cash over / short.' | translate }}</p>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
        <button class="btn btn-sm dc-tab-waiting" [class.btn-primary]="tab === 'waiting'" [class.btn-outline-secondary]="tab !== 'waiting'" (click)="tab = 'waiting'">{{ 'To hand in' | translate }} ({{ data?.waiting?.length ?? 0 }})</button>
        <button class="btn btn-sm dc-tab-settled" [class.btn-primary]="tab === 'settled'" [class.btn-outline-secondary]="tab !== 'settled'" (click)="tab = 'settled'">{{ 'Settled' | translate }} ({{ data?.settled?.length ?? 0 }})</button>
      </div>

      <ng-container *ngIf="tab === 'waiting'">
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;" *ngIf="data && data.totals.length">
          <span class="card dc-total" *ngFor="let t of data.totals" style="padding:0.4rem 0.75rem;"><strong>{{ t.driverName || ('Not assigned' | translate) }}</strong>: {{ t.expected | number: '1.0-2' }} <span class="text-muted">({{ t.orders }})</span></span>
        </div>
        <p class="text-muted dc-none" *ngIf="data && data.waiting.length === 0">{{ 'No cash waiting to be handed in.' | translate }}</p>
        <div class="table-wrap" *ngIf="data && data.waiting.length">
          <table class="table">
            <thead><tr><th></th><th>{{ 'Order' | translate }}</th><th>{{ 'Driver' | translate }}</th><th>{{ 'Delivered' | translate }}</th><th style="text-align:right;">{{ 'Collected' | translate }}</th><th>{{ 'Handed in' | translate }}</th><th>{{ 'Reason if different' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let r of data.waiting" class="dc-row">
                <td><input type="checkbox" [(ngModel)]="sel[r.orderId]" [name]="'dcs' + r.orderId" /></td>
                <td><strong>{{ r.code }}</strong><div class="text-muted" style="font-size:0.8rem;">{{ r.customerName }}</div></td>
                <td>{{ r.driverName || '—' }}</td>
                <td>{{ r.deliveredAt | date: 'short' }}</td>
                <td style="text-align:right;">{{ r.expected | number: '1.0-2' }}</td>
                <td><input class="form-control dc-amount" type="number" min="0" step="any" style="width:130px;" [ngModel]="amount(r)" (ngModelChange)="amounts[r.orderId] = $event" [name]="'dca' + r.orderId" /></td>
                <td><input class="form-control dc-note" maxlength="200" [class.is-invalid]="differs(r) && !(notes[r.orderId] || '').trim()" [(ngModel)]="notes[r.orderId]" [name]="'dcn' + r.orderId" [placeholder]="differs(r) ? ('Required' | translate) : ''" />
                  <div *ngIf="differs(r)" style="font-size:0.8rem;" [style.color]="diff(r) < 0 ? '#b91c1c' : '#15803d'">{{ (diff(r) < 0 ? 'Short' : 'Over') | translate }} {{ diff(r) | number: '1.0-2' }}</div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="flex gap-2 align-center" *ngIf="data && data.waiting.length" style="flex-wrap:wrap;">
          <button class="btn btn-outline-secondary btn-sm dc-all" (click)="selectAll()">{{ 'Select all' | translate }}</button>
          <button class="btn btn-primary dc-settle" [disabled]="busy || selected().length === 0" (click)="settle()">{{ 'Settle the selected' | translate }} ({{ selected().length }})</button>
          <span class="text-muted" *ngIf="selected().length">{{ 'Collected' | translate }} {{ sum(selected(), 'expected') | number: '1.0-2' }} · {{ 'Handed in' | translate }} {{ handedSum() | number: '1.0-2' }}</span>
        </div>
      </ng-container>

      <ng-container *ngIf="tab === 'settled'">
        <p class="text-muted dc-none-settled" *ngIf="data && data.settled.length === 0">{{ 'Nothing settled yet.' | translate }}</p>
        <div class="table-wrap" *ngIf="data && data.settled.length">
          <table class="table">
            <thead><tr><th>{{ 'Order' | translate }}</th><th>{{ 'Driver' | translate }}</th><th style="text-align:right;">{{ 'Collected' | translate }}</th><th style="text-align:right;">{{ 'Handed in' | translate }}</th><th style="text-align:right;">{{ 'Difference' | translate }}</th><th>{{ 'Settled' | translate }}</th><th>{{ 'Reason' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let r of data.settled" class="dc-settled-row">
                <td><strong>{{ r.code }}</strong><div class="text-muted" style="font-size:0.8rem;">{{ r.customerName }}</div></td>
                <td>{{ r.driverName || '—' }}</td>
                <td style="text-align:right;">{{ r.expected | number: '1.0-2' }}</td>
                <td style="text-align:right;">{{ r.handedIn | number: '1.0-2' }}</td>
                <td style="text-align:right;" [style.color]="(r.difference ?? 0) < 0 ? '#b91c1c' : (r.difference ?? 0) > 0 ? '#15803d' : ''">{{ r.difference | number: '1.0-2' }}</td>
                <td>{{ r.settledAt | date: 'short' }}<div class="text-muted" style="font-size:0.8rem;">{{ r.settledBy }}</div></td>
                <td>{{ r.note }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="text-muted" style="font-size:0.85rem;">{{ 'The latest 200 settlements. A settlement cannot be undone here: correct a mistake with a journal entry.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class StoreDriverCashComponent implements OnInit {
  data: DriverCashData | null = null;
  drivers: Driver[] = [];
  driverId: number | null = null;
  tab: 'waiting' | 'settled' = 'waiting';
  sel: Record<number, boolean> = {};
  amounts: Record<number, number | null> = {};
  notes: Record<number, string> = {};
  busy = false;
  error: string | null = null;
  message: string | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.api.drivers().subscribe({ next: (d) => (this.drivers = d), error: () => {} });
    this.load();
  }

  load(): void {
    this.api.driverCash(this.driverId).subscribe({
      next: (d) => {
        this.data = d;
        this.error = null;
        const ids = new Set(d.waiting.map((r) => r.orderId));
        for (const k of Object.keys(this.sel)) if (!ids.has(Number(k))) delete this.sel[Number(k)];
      },
      error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
    });
  }

  /** What is typed for an order; the cash collected until something else is typed. */
  amount(r: DriverCashRow): number {
    const v = this.amounts[r.orderId];
    return v === null || v === undefined ? r.expected : v;
  }

  diff(r: DriverCashRow): number {
    return Math.round((this.amount(r) - r.expected) * 100) / 100;
  }

  differs(r: DriverCashRow): boolean {
    return this.diff(r) !== 0;
  }

  selected(): DriverCashRow[] {
    return (this.data?.waiting ?? []).filter((r) => this.sel[r.orderId]);
  }

  selectAll(): void {
    const all = this.selected().length < (this.data?.waiting.length ?? 0);
    for (const r of this.data?.waiting ?? []) this.sel[r.orderId] = all;
  }

  sum(rows: DriverCashRow[], key: 'expected'): number {
    return rows.reduce((a, r) => a + r[key], 0);
  }

  handedSum(): number {
    return this.selected().reduce((a, r) => a + this.amount(r), 0);
  }

  settle(): void {
    const rows = this.selected();
    for (const r of rows) {
      if (this.differs(r) && (this.notes[r.orderId] ?? '').trim().length < 3) {
        this.error = T('Order {p0}: write the reason for the difference.', { p0: r.code });
        return;
      }
    }
    const short = rows.reduce((a, r) => a + this.diff(r), 0);
    if (!confirm(T('Settle {p0} orders? Collected {p1}, handed in {p2}. This cannot be undone here.', { p0: String(rows.length), p1: String(this.sum(rows, 'expected')), p2: String(this.handedSum()) }) + (short !== 0 ? ' ' + T('Difference {p0} goes to Cash over / short.', { p0: String(Math.round(short * 100) / 100) }) : ''))) return;
    this.busy = true;
    this.error = null;
    this.api.settleDriverCash(rows.map((r) => ({ orderId: r.orderId, amount: this.amount(r), note: (this.notes[r.orderId] ?? '').trim() || null }))).subscribe({
      next: (r) => {
        this.busy = false;
        this.message = T('{p0} orders settled.', { p0: String(r.settled) });
        for (const row of rows) { delete this.sel[row.orderId]; delete this.amounts[row.orderId]; delete this.notes[row.orderId]; }
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
