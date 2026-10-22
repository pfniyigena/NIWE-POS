import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { RestaurantReportApiService } from '../core/api/restaurant-report-api.service';
import { DayCloseRow, EndOfDay } from '../core/models';

/** Admin > Restaurant > End of day: the day's takings, the cash count against what the drawer should hold, and closing the day (Z-report). */
@Component({
  selector: 'app-end-of-day',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:8px;">
        <h3>End of day</h3>
        <span class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <input type="date" class="form-control eod-date" style="width:auto;" [(ngModel)]="date" name="eoddate" (change)="load()" />
          <a class="btn btn-outline-secondary eod-print" [routerLink]="['/admin/restaurant/end-of-day/print', date]">Print</a>
          <button class="btn btn-outline-secondary btn-sm eod-export-xlsx" (click)="download('xlsx')">Excel</button>
          <button class="btn btn-outline-secondary btn-sm eod-export-pdf" (click)="download('pdf')">PDF</button>
          <button class="btn btn-outline-secondary btn-sm eod-export-csv" (click)="download('csv')">CSV</button>
          <a class="btn btn-outline-primary" routerLink="/admin/restaurant/analysis">Sales analysis</a>
        </span>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success eod-ok" *ngIf="info">{{ info }}</div>

      <ng-container *ngIf="e as r">
        <div class="alert alert-success eod-closed" *ngIf="r.closed">
          <b>Closed as Z-{{ pad(r.zNumber) }}</b> by {{ r.closedBy }} on {{ r.closedAt | date: 'medium' }}.
          <span *ngIf="r.notes"> Note: {{ r.notes }}</span>
          <button class="btn btn-sm btn-outline-secondary eod-reopen ms-2" (click)="reopenOpen = !reopenOpen">Reopen…</button>
          <div *ngIf="reopenOpen" class="flex gap-2 mt-2 eod-reopen-panel">
            <input class="form-control eod-reopen-reason" placeholder="Why is the day being reopened?" [(ngModel)]="reopenReason" name="eodreopen" maxlength="200" />
            <button class="btn btn-warning eod-reopen-go" (click)="reopen()" [disabled]="busy">Reopen the day</button>
          </div>
        </div>
        <div class="alert alert-warning eod-changed" *ngIf="r.closed && r.changedSinceClose">
          Something was paid or changed after this day was closed, so the figures below (frozen at closing) no longer match the books. Reopen the day and close it again to include it.
        </div>
        <div class="alert alert-warning eod-open" *ngIf="!r.closed && r.openOrders.count > 0">
          {{ r.openOrders.count }} order{{ r.openOrders.count === 1 ? ' is' : 's are' }} still open ({{ r.openOrders.labels.slice(0, 6).join(', ') }}{{ r.openOrders.labels.length > 6 ? '…' : '' }}), worth {{ r.openOrders.value | number: '1.2-2' }}. Settle or cancel them before closing.
        </div>

        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <!-- left: the numbers -->
          <div style="flex:1; min-width:340px;">
            <div class="card mb-3" style="padding:1rem;">
              <h4 style="font-size:1rem;" class="mb-2">Sales</h4>
              <table class="eod-sales">
                <tr><td>Checks / parties / covers</td><td class="text-end eod-checks">{{ r.sales.checks }} / {{ r.sales.parties }} / {{ r.sales.covers }}</td></tr>
                <tr><td>Items sold</td><td class="text-end">{{ r.sales.itemsSold }}</td></tr>
                <tr><td>Gross sales</td><td class="text-end">{{ r.sales.gross | number: '1.2-2' }}</td></tr>
                <tr><td>Discounts</td><td class="text-end">− {{ r.sales.discounts | number: '1.2-2' }}</td></tr>
                <tr class="eod-strong"><td>Net sales</td><td class="text-end eod-net">{{ r.sales.netSales | number: '1.2-2' }}</td></tr>
                <tr><td>Service charge</td><td class="text-end">{{ r.sales.serviceCharge | number: '1.2-2' }}</td></tr>
                <tr><td>Tax</td><td class="text-end">{{ r.sales.tax | number: '1.2-2' }}</td></tr>
                <tr *ngIf="r.sales.rounding !== 0"><td>Rounding</td><td class="text-end">{{ r.sales.rounding | number: '1.2-2' }}</td></tr>
                <tr class="eod-strong"><td>Total billed</td><td class="text-end eod-billed">{{ r.sales.billed | number: '1.2-2' }}</td></tr>
                <tr><td>Tips</td><td class="text-end eod-tips">{{ r.sales.tips | number: '1.2-2' }}</td></tr>
                <tr class="eod-strong"><td>Collected (bills + tips)</td><td class="text-end eod-collected">{{ r.sales.collected | number: '1.2-2' }}</td></tr>
              </table>
            </div>

            <div class="card mb-3" style="padding:1rem;">
              <h4 style="font-size:1rem;" class="mb-2">How it was paid</h4>
              <table class="eod-payments">
                <tr *ngFor="let p of r.payments" class="eod-pay-row">
                  <td>{{ p.kind === 'DEPOSIT' ? 'Booking deposit paid earlier' : p.method }} <span class="text-muted" *ngIf="p.kind === 'CASH'">(cash)</span><span class="text-muted" *ngIf="p.kind === 'ROOM'">(charged to a room)</span></td>
                  <td class="text-end">{{ p.checks }} check{{ p.checks === 1 ? '' : 's' }}</td>
                  <td class="text-end">{{ p.amount | number: '1.2-2' }}</td>
                </tr>
              </table>
              <p class="text-muted" *ngIf="r.payments.length === 0">No bills were paid.</p>
            </div>

            <div class="card mb-3" style="padding:1rem;" *ngIf="r.types.length">
              <h4 style="font-size:1rem;" class="mb-2">By order type</h4>
              <table>
                <tr *ngFor="let t of r.types"><td>{{ pretty(t.type) }}</td><td class="text-end">{{ t.checks }}</td><td class="text-end">{{ t.netSales | number: '1.2-2' }}</td></tr>
              </table>
            </div>

            <div class="card mb-3" style="padding:1rem;" *ngIf="r.taxes.length">
              <h4 style="font-size:1rem;" class="mb-2">Tax</h4>
              <table class="eod-taxes">
                <thead><tr><th></th><th class="text-end">On</th><th class="text-end">Tax</th></tr></thead>
                <tr *ngFor="let t of r.taxes" class="eod-tax-row"><td>{{ t.label }}</td><td class="text-end">{{ t.base | number: '1.2-2' }}</td><td class="text-end">{{ t.tax | number: '1.2-2' }}</td></tr>
              </table>
            </div>

            <div class="card mb-3" style="padding:1rem;" *ngIf="r.tips.length">
              <h4 style="font-size:1rem;" class="mb-2">Tips by server</h4>
              <table class="eod-tip-table">
                <tr *ngFor="let t of r.tips" class="eod-tip-row"><td>{{ t.server }}</td><td class="text-end">{{ t.checks }} check{{ t.checks === 1 ? '' : 's' }}</td><td class="text-end">{{ t.tips | number: '1.2-2' }}</td></tr>
              </table>
            </div>

            <div class="card mb-3" style="padding:1rem;" *ngIf="r.deposits.length || r.controls.length">
              <h4 style="font-size:1rem;" class="mb-2" *ngIf="r.deposits.length">Booking deposits today</h4>
              <table *ngIf="r.deposits.length" class="eod-deposits">
                <tr *ngFor="let d of r.deposits" class="eod-dep-row"><td>{{ depLabel(d.kind) }}</td><td class="text-end">{{ d.count }}</td><td class="text-end">{{ d.amount | number: '1.2-2' }}</td></tr>
              </table>
              <h4 style="font-size:1rem;" class="mb-2 mt-3" *ngIf="r.controls.length">Voids, comps and cancelled orders</h4>
              <table *ngIf="r.controls.length" class="eod-controls">
                <tr *ngFor="let c of r.controls" class="eod-control-row"><td>{{ pretty(c.type) }}</td><td class="text-end">{{ c.count }}</td><td class="text-end">{{ c.type === 'REPRINT' ? '' : (c.amount | number: '1.2-2') }}</td></tr>
              </table>
            </div>
          </div>

          <!-- right: the cash -->
          <div style="flex:1; min-width:340px;">
            <div class="card mb-3" style="padding:1rem;">
              <h4 style="font-size:1rem;" class="mb-2">Cash in the drawer</h4>
              <table class="eod-cash">
                <tr>
                  <td>Cash left from the last close (opening float)</td>
                  <td class="text-end">
                    <input *ngIf="!r.closed" type="number" min="0" step="0.01" class="form-control eod-float" style="width:120px; display:inline-block; text-align:right;" [(ngModel)]="openingFloat" name="eodfloat" (change)="load(true)" />
                    <span *ngIf="r.closed">{{ r.cash.openingFloat | number: '1.2-2' }}</span>
                  </td>
                </tr>
                <tr><td>+ Cash sales</td><td class="text-end eod-cash-sales">{{ r.cash.cashSales | number: '1.2-2' }}</td></tr>
                <tr><td>+ Deposits taken in cash</td><td class="text-end">{{ r.cash.depositsIn | number: '1.2-2' }}</td></tr>
                <tr><td>− Deposits refunded in cash</td><td class="text-end">{{ r.cash.depositsOut | number: '1.2-2' }}</td></tr>
                <tr><td>+ Paid in</td><td class="text-end">{{ r.cash.paidIn | number: '1.2-2' }}</td></tr>
                <tr><td>− Paid out</td><td class="text-end">{{ r.cash.paidOut | number: '1.2-2' }}</td></tr>
                <tr><td>− Tips paid out</td><td class="text-end">{{ r.cash.tipsPaid | number: '1.2-2' }}</td></tr>
                <tr class="eod-strong"><td>Should be in the drawer</td><td class="text-end eod-expected">{{ r.cash.expected | number: '1.2-2' }}</td></tr>
              </table>

              <div *ngIf="!r.closed" class="mt-3">
                <div class="flex gap-2 align-center mb-2" style="flex-wrap:wrap;">
                  <label style="min-width:170px;">Cash counted</label>
                  <input type="number" min="0" step="0.01" class="form-control eod-counted" style="width:160px;" [(ngModel)]="counted" name="eodcounted" />
                  <span class="eod-variance" [class.text-danger]="variance !== null && variance !== 0" [class.text-success]="variance === 0">
                    <ng-container *ngIf="variance !== null">{{ variance === 0 ? 'Balanced' : (variance > 0 ? 'Over by ' : 'Short by ') + (abs(variance) | number: '1.2-2') }}</ng-container>
                  </span>
                </div>
                <div class="flex gap-2 align-center mb-2" style="flex-wrap:wrap;">
                  <label style="min-width:170px;">Leave in the drawer</label>
                  <input type="number" min="0" step="0.01" class="form-control eod-keep" style="width:160px;" [(ngModel)]="floatKept" name="eodkeep" placeholder="{{ defaultKeep() | number: '1.2-2' }}" />
                  <span class="text-muted">Banked: <b class="eod-banked">{{ banked() | number: '1.2-2' }}</b></span>
                </div>
                <div class="mb-2">
                  <input class="form-control eod-notes" placeholder="Note (needed when the drawer is over or short)" [(ngModel)]="notes" name="eodnotes" maxlength="300" />
                </div>
                <label *ngIf="r.openOrders.count > 0" class="mb-2" style="display:block;"><input type="checkbox" class="eod-force" [(ngModel)]="force" name="eodforce" /> Close anyway with {{ r.openOrders.count }} order(s) still open</label>
                <button class="btn btn-primary eod-close" (click)="close()" [disabled]="busy || counted === null">Close the day</button>
              </div>

              <div *ngIf="r.closed" class="mt-3">
                <table class="eod-cash-closed">
                  <tr><td>Cash counted</td><td class="text-end eod-c-counted">{{ r.cash.counted | number: '1.2-2' }}</td></tr>
                  <tr><td>Difference</td><td class="text-end eod-c-variance" [class.text-danger]="(r.cash.variance ?? 0) !== 0">{{ r.cash.variance | number: '1.2-2' }}</td></tr>
                  <tr><td>Left in the drawer</td><td class="text-end">{{ r.cash.floatKept | number: '1.2-2' }}</td></tr>
                  <tr class="eod-strong"><td>Banked</td><td class="text-end">{{ r.cash.banked | number: '1.2-2' }}</td></tr>
                </table>
              </div>
            </div>

            <div class="card mb-3" style="padding:1rem;">
              <h4 style="font-size:1rem;" class="mb-2">Money in and out of the drawer</h4>
              <div class="flex gap-2 mb-2 eod-ce-form" style="flex-wrap:wrap;" *ngIf="!r.closed">
                <select class="form-control eod-ce-kind" style="width:auto;" [(ngModel)]="ceKind" name="ecek">
                  <option value="PAID_IN">Paid in</option>
                  <option value="PAID_OUT">Paid out</option>
                  <option value="TIPS_PAID">Tips paid out</option>
                </select>
                <input type="number" min="0" step="0.01" class="form-control eod-ce-amount" style="width:120px;" placeholder="Amount" [(ngModel)]="ceAmount" name="ecea" />
                <input class="form-control eod-ce-reason" style="flex:1; min-width:160px;" placeholder="What for" [(ngModel)]="ceReason" name="eceR" maxlength="200" />
                <button class="btn btn-outline-primary eod-ce-add" (click)="addCash()" [disabled]="busy">Add</button>
              </div>
              <table class="eod-ce-table">
                <tr *ngFor="let c of manualEvents()" class="eod-ce-row">
                  <td>{{ c.at | date: 'shortTime' }}</td><td>{{ c.label }}</td><td>{{ c.reason }}</td><td class="text-muted">{{ c.by }}</td>
                  <td class="text-end">{{ c.amount | number: '1.2-2' }}</td>
                  <td class="text-end"><button *ngIf="!r.closed" class="btn btn-sm btn-outline-danger eod-ce-del" (click)="removeCash(c.id)" title="Remove this entry (manager)">×</button></td>
                </tr>
              </table>
              <p class="text-muted" *ngIf="manualEvents().length === 0">Nothing yet. Record petty cash, change put in or tips handed to staff here so the drawer balances.</p>
            </div>
          </div>
        </div>
      </ng-container>

      <div class="card mt-3" style="padding:1rem;">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:8px;">
          <h4 style="font-size:1rem;">Closed days</h4>
          <span class="flex gap-2">
            <button class="btn btn-sm btn-outline-secondary eod-h-xlsx" (click)="downloadHistory('xlsx')">Excel</button>
            <button class="btn btn-sm btn-outline-secondary eod-h-csv" (click)="downloadHistory('csv')">CSV</button>
          </span>
        </div>
        <table class="eod-history">
          <thead><tr><th>Z</th><th>Date</th><th>Status</th><th class="text-end">Checks</th><th class="text-end">Billed</th><th class="text-end">Should be</th><th class="text-end">Counted</th><th class="text-end">Difference</th><th class="text-end">Banked</th><th>Closed by</th><th>Note</th></tr></thead>
          <tbody>
            <tr *ngFor="let h of history" class="eod-h-row" [class.text-muted]="h.status !== 'CLOSED'">
              <td>Z-{{ pad(h.zNumber) }}</td>
              <td><a href="javascript:void(0)" (click)="openDay(h.date)">{{ h.date }}</a></td>
              <td>{{ h.status === 'CLOSED' ? 'Closed' : 'Reopened' }}<span *ngIf="h.reopenReason" class="text-muted"> — {{ h.reopenReason }}</span></td>
              <td class="text-end">{{ h.checks }}</td><td class="text-end">{{ h.totalBilled | number: '1.2-2' }}</td>
              <td class="text-end">{{ h.cashExpected | number: '1.2-2' }}</td><td class="text-end">{{ h.cashCounted | number: '1.2-2' }}</td>
              <td class="text-end" [class.text-danger]="h.variance !== 0">{{ h.variance | number: '1.2-2' }}</td>
              <td class="text-end">{{ h.banked | number: '1.2-2' }}</td><td>{{ h.closedBy }}</td><td>{{ h.notes }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="historyLoaded && history.length === 0">No day has been closed yet.</p>
        <app-pager [page]="hPage" [size]="hSize" [total]="hTotal" (pageChange)="loadHistory($event)"></app-pager>
      </div>
    </app-shell>
  `,
  styles: [
    `
      table { width: 100%; border-collapse: collapse; }
      td, th { padding: 3px 6px; border-bottom: 1px solid var(--border, #eee); }
      .eod-strong td { font-weight: 700; }
    `,
  ],
})
export class EndOfDayComponent implements OnInit {
  date = today();
  e: EndOfDay | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  openingFloat: number | null = null;
  counted: number | null = null;
  floatKept: number | null = null;
  notes = '';
  force = false;
  reopenOpen = false;
  reopenReason = '';
  ceKind = 'PAID_OUT';
  ceAmount: number | null = null;
  ceReason = '';
  history: DayCloseRow[] = [];
  historyLoaded = false;
  hPage = 0;
  hSize = 10;
  hTotal = 0;

  constructor(private api: RestaurantReportApiService) {}

  ngOnInit(): void {
    this.load();
    this.loadHistory(0);
  }

  /** Loads the day. {@code keepFloat} keeps the opening float typed on screen instead of the suggested one. */
  load(keepFloat = false): void {
    this.error = null;
    this.api.endOfDay(this.date, keepFloat ? this.openingFloat : null).subscribe({
      next: (r) => {
        this.e = r;
        if (!keepFloat) this.openingFloat = r.cash.openingFloat;
        if (r.closed) {
          this.counted = null;
        }
      },
      error: (er) => (this.error = er?.error?.message || 'Could not load the day'),
    });
  }

  get variance(): number | null {
    if (this.counted === null || this.counted === undefined || this.e === null || (this.counted as unknown) === '') return null;
    return Math.round((Number(this.counted) - this.e.cash.expected) * 100) / 100;
  }

  defaultKeep(): number {
    if (this.counted === null || !this.e) return 0;
    return Math.min(Number(this.counted), this.e.cash.openingFloat);
  }

  banked(): number {
    if (this.counted === null) return 0;
    const keep = this.floatKept !== null && (this.floatKept as unknown) !== '' ? Number(this.floatKept) : this.defaultKeep();
    return Math.round((Number(this.counted) - keep) * 100) / 100;
  }

  abs(v: number | null): number {
    return Math.abs(v ?? 0);
  }

  close(): void {
    if (this.counted === null) return;
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api
      .close({
        date: this.date,
        cashCounted: Number(this.counted),
        floatKept: this.floatKept !== null && (this.floatKept as unknown) !== '' ? Number(this.floatKept) : null,
        openingFloat: this.openingFloat,
        notes: this.notes.trim() || null,
        closeWithOpenOrders: this.force,
      })
      .subscribe({
        next: (r) => {
          this.busy = false;
          this.e = r;
          this.info = `The day is closed as Z-${this.pad(r.zNumber)}.`;
          this.counted = null;
          this.floatKept = null;
          this.notes = '';
          this.force = false;
          this.loadHistory(0);
        },
        error: (er) => {
          this.busy = false;
          this.error = er?.error?.message || 'Could not close the day';
        },
      });
  }

  reopen(): void {
    this.busy = true;
    this.error = null;
    this.api.reopen(this.date, this.reopenReason).subscribe({
      next: (r) => {
        this.busy = false;
        this.e = r;
        this.reopenOpen = false;
        this.reopenReason = '';
        this.info = 'The day is open again.';
        this.openingFloat = r.cash.openingFloat;
        this.loadHistory(0);
      },
      error: (er) => {
        this.busy = false;
        this.error = er?.error?.message || 'Could not reopen the day';
      },
    });
  }

  manualEvents() {
    return (this.e?.cashEvents ?? []).filter((c) => !c.kind.startsWith('DEPOSIT'));
  }

  addCash(): void {
    if (this.ceAmount === null) return;
    this.busy = true;
    this.error = null;
    this.api.addCash(this.ceKind, Number(this.ceAmount), this.ceReason).subscribe({
      next: () => {
        this.busy = false;
        this.ceAmount = null;
        this.ceReason = '';
        this.load(true);
      },
      error: (er) => {
        this.busy = false;
        this.error = er?.error?.message || 'Could not save the entry';
      },
    });
  }

  removeCash(id: number): void {
    if (!confirm('Remove this entry?')) return;
    this.api.removeCash(id).subscribe({
      next: () => this.load(true),
      error: (er) => (this.error = er?.error?.message || 'Could not remove the entry'),
    });
  }

  loadHistory(page: number): void {
    this.api.history(page, this.hSize).subscribe({
      next: (r) => {
        this.history = r.items;
        this.hTotal = r.total;
        this.hPage = page;
        this.historyLoaded = true;
      },
      error: () => (this.historyLoaded = true),
    });
  }

  openDay(date: string): void {
    this.date = date;
    this.info = null;
    this.load();
    window.scrollTo({ top: 0 });
  }

  download(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.api.download('end-of-day', { date: this.date, openingFloat: this.e && !this.e.closed ? String(this.openingFloat ?? '') : '' }, format, (err) => {
      if (err) this.error = 'The export failed.';
    });
  }

  downloadHistory(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.api.download('history', {}, format, (err) => {
      if (err) this.error = 'The export failed.';
    });
  }

  pad(n: number | null): string {
    return String(n ?? 0).padStart(4, '0');
  }

  pretty(code: string): string {
    const t = (code || '').toLowerCase().replace(/_/g, ' ');
    return t.charAt(0).toUpperCase() + t.slice(1);
  }

  depLabel(kind: string): string {
    return kind === 'DEPOSIT_RECEIVED' ? 'Taken' : kind === 'DEPOSIT_REFUNDED' ? 'Refunded' : 'Kept (no-show or late cancellation)';
  }
}

function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
