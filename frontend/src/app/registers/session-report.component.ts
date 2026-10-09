import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { RegisterApiService } from '../core/api/register-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { SessionReport, StoreSettings } from '../core/models';

/**
 * The Z-report — `/sessions/:id/report`. Task: "Add print session report (Z-report) same we
 * print receipt. Not PDF actually" — this used to be an ordinary screen inside `ShellComponent`
 * (the app's dark-themed sidebar chrome), with no print treatment at all. Rebuilt as a direct copy
 * of `checkout/receipt.component.ts`/`returns/return-receipt.component.ts`'s own printable-page
 * shape instead: no shell, a light/monospace `.receipt`-style layout, a `no-print` **Print** button
 * that just calls `window.print()`, and the business letterhead (logo/name/address/tax ID) those
 * two already print — a Z-report is exactly the same kind of one-shot printable document a receipt
 * is, not something browsed around inside the app's normal navigation. Explicitly *not* a PDF —
 * Fourier's own words — so there's no jsPDF/export step here, just the browser's own print dialog,
 * same as every other receipt in this app.
 *
 * Reached two ways, mirroring the same auto-print convention checkout/return receipts use:
 * automatically right after `session-close.component.ts` closes a shift (with `?autoPrint=1`), or
 * manually via the "Report" link on a closed shift's row on the admin "Shifts" screen
 * (`shift-list.component.ts`), to reprint one closed earlier.
 *
 * <p>Task: "We need to differentiate CashierSession Z Report with RegisterSession Z Report" — this
 * is specifically the *shift*-wide report (every cashier who worked it, cash-drawer
 * reconciliation, refunds). It is NOT reached from the plain "Sessions" screen's per-row "Report"
 * link anymore — that now goes to `cashier-session-report.component.ts`, a smaller report scoped
 * to just one cashier's own stint. This component's "By cashier" section below still shows every
 * cashier session on the shift inline, for a quick overview, but the source of truth for one
 * cashier's own numbers is that separate report.</p>
 */
@Component({
  selector: 'app-session-report',
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
      <h3 style="text-align:center; margin-bottom:0;">Z-Report</h3>
      <p style="text-align:center; margin:4px 0 4px; color:#666; font-size:0.85rem;">
        {{ r.session.registerName }}
      </p>
      <p style="text-align:center; margin:0 0 8px; color:#666; font-size:0.85rem;">
        {{ r.session.openedAt | date: 'yyyy-MM-dd HH:mm' }} &rarr; {{ r.session.closedAt | date: 'yyyy-MM-dd HH:mm' }}<br />
        Cashier: {{ r.session.cashierName }}
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
          <tr>
            <td style="padding:3px 0;">Cash refunds</td>
            <td style="text-align:right; padding:3px 0;">&minus;{{ r.cashRefundsOut | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Task: "The report for register_session (Shift) should show all cashier_session (session
           report)" — one block per cashier who worked this shift, each with its own start/end time
           and its own slice of the shift's sales. Ordinarily just one; more when "Switch cashier"
           (or an admin's "assign") brought a teammate onto the same till during the shift. -->
      <ng-container *ngIf="r.cashierSessions && r.cashierSessions.length">
        <hr />
        <p style="text-align:center; font-weight:700; margin:0 0 6px; font-size:0.9rem;">By cashier</p>
        <div *ngFor="let cs of r.cashierSessions" style="margin-bottom:8px; font-size:0.85rem;">
          <div style="display:flex; justify-content:space-between; font-weight:700;">
            <span>{{ cs.cashierName }}</span>
            <span style="font-weight:400; color:#666;">{{ cs.status }}</span>
          </div>
          <div style="color:#666; font-size:0.8rem;">
            {{ cs.startedAt | date: 'yyyy-MM-dd HH:mm' }} &rarr; {{ cs.endedAt ? (cs.endedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}
          </div>
          <div style="display:flex; justify-content:space-between;">
            <span>{{ cs.saleCount }} sale(s)</span>
            <span>{{ cs.grossSales | number: '1.2-2' }}</span>
          </div>
        </div>
      </ng-container>

      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr>
            <td style="padding:3px 0;">Opening float</td>
            <td style="text-align:right; padding:3px 0;">{{ r.session.openingFloat | number: '1.2-2' }}</td>
          </tr>
          <tr style="font-weight:700;">
            <td style="padding:3px 0;">Expected cash</td>
            <td style="text-align:right; padding:3px 0;">{{ r.expectedCash | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td style="padding:3px 0;">Counted cash</td>
            <td style="text-align:right; padding:3px 0;">{{ r.session.countedCash | number: '1.2-2' }}</td>
          </tr>
          <tr style="font-weight:700;">
            <td style="padding:3px 0;">Variance</td>
            <td style="text-align:right; padding:3px 0;">{{ r.session.cashVariance | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>

      <p style="text-align:center; margin-top:20px; color:#666; font-size:0.85rem;">— End of Z-Report —</p>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a routerLink="/admin/shifts" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">
          Back to Shifts
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
export class SessionReportComponent implements OnInit, OnDestroy {
  report: SessionReport | null = null;
  /** Task: "Add Business details + logo on the header of receipt printout" equivalent for the Z-report — same graceful "just don't show it" degradation as the sale/return receipts if this fetch fails or nothing's configured. */
  settings: StoreSettings | null = null;

  /** Set only when session-close.component.ts navigates here right after closing a shift — same convention as checkout/return-form's own `?autoPrint=1`. */
  private autoPrint = false;
  /** Dismissing the auto-print dialog lands back on the admin Shifts screen — same place the manual "Back to Shifts" link below goes, now that this report is reached from there rather than the plain "Sessions" screen (see this class's own doc comment). */
  private readonly afterPrintHandler = () => this.router.navigate(['/admin/shifts']);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private registerApi: RegisterApiService,
    private storeSettingsApi: StoreSettingsApiService
  ) {}

  ngOnInit(): void {
    const sessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.autoPrint = this.route.snapshot.queryParamMap.get('autoPrint') === '1';
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.settings = s),
      error: () => (this.settings = null), // header just falls back to no letterhead — never blocks printing
    });
    this.registerApi.getSessionReport(sessionId).subscribe((r) => {
      this.report = r;
      if (this.autoPrint) {
        window.addEventListener('afterprint', this.afterPrintHandler);
        setTimeout(() => window.print(), 150);
      }
    });
  }

  ngOnDestroy(): void {
    window.removeEventListener('afterprint', this.afterPrintHandler);
  }

  print(): void {
    window.print();
  }
}
