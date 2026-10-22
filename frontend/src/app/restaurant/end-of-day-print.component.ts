import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RestaurantReportApiService } from '../core/api/restaurant-report-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { EndOfDay, StoreSettings } from '../core/models';

/** The printable Z-report of one day (no menus around it, like the receipt). */
@Component({
  selector: 'app-end-of-day-print',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="zr" *ngIf="e as r">
      <h3 style="text-align:center; margin-bottom:0;">{{ r.closed ? 'Z-REPORT Z-' + pad(r.zNumber) : 'DAY REPORT (not closed yet)' }}</h3>
      <div *ngIf="settings as s" style="text-align:center; margin:6px 0 10px;">
        <div *ngIf="s.businessName" style="font-weight:700;">{{ s.businessName }}</div>
        <div *ngIf="s.address" style="font-size:0.8rem; color:#444;">{{ s.address }}</div>
        <div *ngIf="s.taxId" style="font-size:0.8rem; color:#444;">Tax ID: {{ s.taxId }}</div>
      </div>
      <p style="text-align:center; margin:0 0 4px;" class="zr-date">Business day {{ r.date }}</p>
      <p style="text-align:center; margin:0 0 8px; color:#666; font-size:0.8rem;" *ngIf="r.closed">Closed by {{ r.closedBy }} on {{ r.closedAt | date: 'medium' }}</p>
      <p class="zr-warn" *ngIf="r.closed && r.changedSinceClose">Sales were recorded after this day was closed.</p>

      <h5>Sales</h5>
      <table>
        <tr><td>Checks</td><td class="n">{{ r.sales.checks }}</td></tr>
        <tr><td>Covers</td><td class="n">{{ r.sales.covers }}</td></tr>
        <tr><td>Gross sales</td><td class="n">{{ r.sales.gross | number: '1.2-2' }}</td></tr>
        <tr><td>Discounts</td><td class="n">-{{ r.sales.discounts | number: '1.2-2' }}</td></tr>
        <tr class="b"><td>Net sales</td><td class="n zr-net">{{ r.sales.netSales | number: '1.2-2' }}</td></tr>
        <tr><td>Service charge</td><td class="n">{{ r.sales.serviceCharge | number: '1.2-2' }}</td></tr>
        <tr><td>Tax</td><td class="n">{{ r.sales.tax | number: '1.2-2' }}</td></tr>
        <tr *ngIf="r.sales.rounding !== 0"><td>Rounding</td><td class="n">{{ r.sales.rounding | number: '1.2-2' }}</td></tr>
        <tr class="b"><td>Total billed</td><td class="n zr-billed">{{ r.sales.billed | number: '1.2-2' }}</td></tr>
        <tr><td>Tips</td><td class="n">{{ r.sales.tips | number: '1.2-2' }}</td></tr>
      </table>

      <h5 *ngIf="r.taxes.length">Tax</h5>
      <table *ngIf="r.taxes.length"><tr *ngFor="let t of r.taxes"><td>{{ t.label }} on {{ t.base | number: '1.2-2' }}</td><td class="n">{{ t.tax | number: '1.2-2' }}</td></tr></table>

      <h5>Payments</h5>
      <table>
        <tr *ngFor="let p of r.payments"><td>{{ p.kind === 'DEPOSIT' ? 'Booking deposit (paid earlier)' : p.method }} ({{ p.checks }})</td><td class="n">{{ p.amount | number: '1.2-2' }}</td></tr>
        <tr class="b"><td>Collected</td><td class="n">{{ r.sales.collected | number: '1.2-2' }}</td></tr>
      </table>

      <h5 *ngIf="r.tips.length">Tips by server</h5>
      <table *ngIf="r.tips.length"><tr *ngFor="let t of r.tips"><td>{{ t.server }}</td><td class="n">{{ t.tips | number: '1.2-2' }}</td></tr></table>

      <h5 *ngIf="r.controls.length">Voids, comps, cancelled</h5>
      <table *ngIf="r.controls.length"><tr *ngFor="let c of r.controls"><td>{{ pretty(c.type) }} ({{ c.count }})</td><td class="n">{{ c.type === 'REPRINT' ? '' : (c.amount | number: '1.2-2') }}</td></tr></table>

      <h5>Cash</h5>
      <table>
        <tr><td>Opening float</td><td class="n">{{ r.cash.openingFloat | number: '1.2-2' }}</td></tr>
        <tr><td>+ Cash sales</td><td class="n">{{ r.cash.cashSales | number: '1.2-2' }}</td></tr>
        <tr><td>+ Deposits in</td><td class="n">{{ r.cash.depositsIn | number: '1.2-2' }}</td></tr>
        <tr><td>- Deposits refunded</td><td class="n">{{ r.cash.depositsOut | number: '1.2-2' }}</td></tr>
        <tr><td>+ Paid in</td><td class="n">{{ r.cash.paidIn | number: '1.2-2' }}</td></tr>
        <tr><td>- Paid out</td><td class="n">{{ r.cash.paidOut | number: '1.2-2' }}</td></tr>
        <tr><td>- Tips paid out</td><td class="n">{{ r.cash.tipsPaid | number: '1.2-2' }}</td></tr>
        <tr class="b"><td>Should be in drawer</td><td class="n zr-expected">{{ r.cash.expected | number: '1.2-2' }}</td></tr>
        <ng-container *ngIf="r.closed">
          <tr><td>Counted</td><td class="n zr-counted">{{ r.cash.counted | number: '1.2-2' }}</td></tr>
          <tr class="b"><td>Difference</td><td class="n zr-variance">{{ r.cash.variance | number: '1.2-2' }}</td></tr>
          <tr><td>Left in drawer</td><td class="n">{{ r.cash.floatKept | number: '1.2-2' }}</td></tr>
          <tr><td>Banked</td><td class="n">{{ r.cash.banked | number: '1.2-2' }}</td></tr>
        </ng-container>
      </table>
      <p *ngIf="r.notes" style="margin-top:8px;">Note: {{ r.notes }}</p>
      <div *ngIf="r.closed" style="margin-top:28px; display:flex; gap:24px;"><div style="flex:1; border-top:1px solid #333; padding-top:2px; font-size:0.8rem;">Counted by</div><div style="flex:1; border-top:1px solid #333; padding-top:2px; font-size:0.8rem;">Checked by</div></div>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button class="zr-print" (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a routerLink="/admin/restaurant/end-of-day" style="flex:1; padding:10px; text-align:center; border:1px solid #999; border-radius:4px; color:#111; text-decoration:none;">Back</a>
      </div>
    </div>
    <p *ngIf="!e && error" class="no-print" style="text-align:center; margin-top:40px; color:#b00;">{{ error }}</p>
  `,
  styles: [
    `
      .zr { max-width: 420px; margin: 20px auto; padding: 16px; background: #fff; color: #111; border: 1px solid #ddd; font-size: 0.9rem; }
      .zr table { width: 100%; border-collapse: collapse; }
      .zr td { padding: 1px 2px; }
      .zr .n { text-align: right; white-space: nowrap; }
      .zr .b td { font-weight: 700; border-top: 1px solid #999; }
      .zr h5 { margin: 12px 0 2px 0; border-bottom: 1px dashed #999; }
      .zr-warn { color: #b00; text-align: center; font-weight: 600; }
      @media print { .no-print { display: none !important; } .zr { border: none; margin: 0 auto; } }
    `,
  ],
})
export class EndOfDayPrintComponent implements OnInit {
  e: EndOfDay | null = null;
  settings: StoreSettings | null = null;
  error: string | null = null;

  constructor(private route: ActivatedRoute, private api: RestaurantReportApiService, private storeApi: StoreSettingsApiService) {}

  ngOnInit(): void {
    const date = this.route.snapshot.paramMap.get('date') || '';
    this.storeApi.get().subscribe({ next: (s) => (this.settings = s), error: () => (this.settings = null) });
    this.api.endOfDay(date).subscribe({
      next: (r) => (this.e = r),
      error: (er) => (this.error = er?.error?.message || 'Could not load the report'),
    });
  }

  print(): void {
    window.print();
  }

  pad(n: number | null): string {
    return String(n ?? 0).padStart(4, '0');
  }

  pretty(code: string): string {
    const t = (code || '').toLowerCase().replace(/_/g, ' ');
    return t.charAt(0).toUpperCase() + t.slice(1);
  }
}
