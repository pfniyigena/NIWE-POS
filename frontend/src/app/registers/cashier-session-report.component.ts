import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CashierSessionApiService } from '../core/api/cashier-session-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { CashierSessionZReport, StoreSettings } from '../core/models';

/**
 * One cashier's own printable report — `/cashier-sessions/:id/report`. Task: "We need to
 * differentiate CashierSession Z Report with RegisterSession Z Report." Before this, the plain
 * "Sessions" screen's per-row "Report" link (one row per `CashierSession`) pointed at
 * `session-report.component.ts` — the whole *shift*'s Z-report — so a cashier looking up their own
 * ended session saw every cashier who worked that till and the shift's cash-drawer reconciliation,
 * not just their own stint. This is a deliberately separate, smaller report: this cashier's own
 * start/end time and their own sales only. `session-report.component.ts` (the RegisterSession-level
 * Z-report, reached from the admin "Shifts" screen and from closing a shift) is untouched — it's
 * still the one place the shift-wide totals and cash reconciliation live, now also showing this
 * same "By cashier" breakdown inline for a quick overview across everyone who worked it.
 *
 * <p>Same printable-page shape as every other receipt-like screen in this app (light/monospace
 * `.receipt` layout, business letterhead, `no-print` Print button, `@media print` hiding it) — but
 * titled "Cashier Session Report", not "Z-Report", so the two are never mistaken for each other on
 * the printed page itself. No cash-drawer reconciliation and no refunds here — both are shift-wide,
 * physical-till concepts the backend DTO deliberately leaves out (see
 * `CashierSessionZReportDto`'s own javadoc). Reached only manually, via the "Report" link on an
 * ended cashier session's row on `/sessions` — there's no auto-print entry point into this one
 * (closing a single cashier session doesn't navigate anywhere, unlike closing a whole shift).
 */
@Component({
  selector: 'app-cashier-session-report',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="receipt" *ngIf="report as r">
      <div *ngIf="settings as s" style="text-align:center; margin:0 0 12px;">
        <img *ngIf="s.logoDataUrl" [src]="s.logoDataUrl" alt="" style="max-width:120px; max-height:80px; margin-bottom:6px;" />
        <div *ngIf="s.businessName" style="font-weight:700; font-size:1.1rem;">{{ s.businessName }}</div>
        <div *ngIf="s.address" style="font-size:0.8rem; color:#444;">{{ s.address }}</div>
        <div *ngIf="s.phone || s.email" style="font-size:0.8rem; color:#444;">
          <span *ngIf="s.phone">{{ s.phone }}</span><span *ngIf="s.phone && s.email"> &middot; </span><span *ngIf="s.email">{{ s.email }}</span>
        </div>
        <div *ngIf="s.taxId" style="font-size:0.8rem; color:#444;">Tax ID: {{ s.taxId }}</div>
      </div>
      <h3 style="text-align:center; margin-bottom:0;">Cashier Session Report</h3>
      <p style="text-align:center; margin:4px 0 4px; color:#666; font-size:0.85rem;">
        {{ r.registerName }}<span *ngIf="r.shiftStatus"> &middot; shift {{ r.shiftStatus | lowercase }}</span>
      </p>
      <p style="text-align:center; margin:0 0 8px; color:#666; font-size:0.85rem;">
        {{ r.startedAt | date: 'yyyy-MM-dd HH:mm' }} &rarr; {{ r.endedAt ? (r.endedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}<br />
        Cashier: {{ r.cashierName }} &middot; {{ r.status }}
      </p>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr>
            <td style="padding:3px 0;">Sales count</td>
            <td style="text-align:right; padding:3px 0;">{{ r.saleCount }}</td>
          </tr>
          <tr>
            <td style="padding:3px 0;">Gross sales</td>
            <td style="text-align:right; padding:3px 0;">{{ r.grossSales | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td style="padding:3px 0;">Tax collected</td>
            <td style="text-align:right; padding:3px 0;">{{ r.taxCollected | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td style="padding:3px 0;">Cash sales</td>
            <td style="text-align:right; padding:3px 0;">{{ r.cashIn | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td style="padding:3px 0;">Non-cash sales</td>
            <td style="text-align:right; padding:3px 0;">{{ r.nonCashIn | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>

      <p style="text-align:center; margin-top:20px; color:#666; font-size:0.85rem;">— End of report —</p>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a routerLink="/sessions" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">
          Back to sessions
        </a>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        background: #fff;
        color: #111;
        min-height: 100vh;
        font-family: 'Courier New', monospace;
      }
      .receipt {
        max-width: 380px;
        margin: 0 auto;
        padding: 24px 16px;
      }
      hr {
        border: none;
        border-top: 1px dashed #999;
        margin: 10px 0;
      }
      @media print {
        .no-print {
          display: none !important;
        }
      }
    `,
  ],
})
export class CashierSessionReportComponent implements OnInit {
  report: CashierSessionZReport | null = null;
  /** Same graceful "just don't show it" degradation as every other receipt-like screen if this fetch fails or nothing's configured. */
  settings: StoreSettings | null = null;

  constructor(
    private route: ActivatedRoute,
    private cashierSessionApi: CashierSessionApiService,
    private storeSettingsApi: StoreSettingsApiService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.settings = s),
      error: () => (this.settings = null),
    });
    this.cashierSessionApi.getReport(id).subscribe((r) => (this.report = r));
  }

  print(): void {
    window.print();
  }
}
