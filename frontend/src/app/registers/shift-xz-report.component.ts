import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { RegisterApiService } from '../core/api/register-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { AuthService } from '../core/auth.service';
import { API_BASE } from '../core/constants';
import { downloadBlob } from '../core/download-file';
import { DeviceService } from '../core/hardware/device.service';
import { PrintService } from '../core/hardware/print.service';
import { applyThermalPrintStyle } from '../core/hardware/thermal-css';
import { ShiftReport, StoreSettings } from '../core/models';

/**
 * The X report (the shift so far; nothing is closed or cleared) at `/sessions/:id/x-report`, and the Z report (a closed shift, numbered and
 * frozen as it was when closed) at `/sessions/:id/z-report`. A printable page in the style of the receipts: no menu, a Print button, and
 * Excel / PDF / CSV downloads for those who may. Printing a Z report counts it; the second and later printings are marked COPY.
 */
@Component({
  selector: 'app-shift-xz-report',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="receipt" *ngIf="r as r">
      <p class="no-print dev-direct-error" *ngIf="directError" style="color:#b00; font-size:0.85rem;">{{ directError }}</p>
      <div *ngIf="settings as s" style="text-align:center; margin:0 0 12px;">
        <img *ngIf="s.logoDataUrl" [src]="s.logoDataUrl" alt="" style="max-width:120px; max-height:80px; margin-bottom:6px;" />
        <div *ngIf="s.businessName" style="font-weight:700; font-size:1.1rem;">{{ s.businessName }}</div>
        <div *ngIf="s.address" style="font-size:0.8rem; color:#444;">{{ s.address }}</div>
        <div *ngIf="s.taxId" style="font-size:0.8rem; color:#444;">Tax ID: {{ s.taxId }}</div>
      </div>
      <h3 class="xz-title" style="text-align:center; margin-bottom:0;">{{ r.type }}-Report<span *ngIf="r.type === 'Z' && r.zNumber"> no. {{ r.zNumber }}</span></h3>
      <p *ngIf="r.copyNumber > 0" class="xz-copy" style="text-align:center; font-weight:700; margin:2px 0;">*** COPY {{ r.copyNumber }} ***</p>
      <p *ngIf="r.type === 'X'" style="text-align:center; margin:2px 0; font-size:0.8rem; color:#666;">Shift still open — nothing has been closed or cleared</p>
      <p style="text-align:center; margin:4px 0 0; color:#666; font-size:0.85rem;">{{ r.registerName }} · {{ r.cashierName }}</p>
      <p style="text-align:center; margin:0 0 6px; color:#666; font-size:0.8rem;">
        {{ r.openedAt | date: 'yyyy-MM-dd HH:mm' }} &rarr; {{ r.closedAt ? (r.closedAt | date: 'yyyy-MM-dd HH:mm') : 'now' }}<br />
        Printed {{ r.generatedAt | date: 'yyyy-MM-dd HH:mm' }}
      </p>

      <hr />
      <p class="h">Sales</p>
      <div class="row"><span>Number of sales</span><span class="xz-count">{{ r.sales.count }}</span></div>
      <div class="row"><span>Items sold</span><span>{{ r.sales.items }}</span></div>
      <div class="row"><span>Sales at shelf price</span><span>{{ r.sales.shelfValue | number: '1.2-2' }}</span></div>
      <div class="row"><span>Discounts and promotions</span><span class="xz-discounts">&minus;{{ r.sales.discounts | number: '1.2-2' }}</span></div>
      <div class="row"><span>Net sales (before tax)</span><span>{{ r.sales.netSales | number: '1.2-2' }}</span></div>
      <div class="row"><span>Tax</span><span>{{ r.sales.tax | number: '1.2-2' }}</span></div>
      <div class="row b"><span>Total sales</span><span class="xz-total">{{ r.sales.totalSales | number: '1.2-2' }}</span></div>
      <div class="row"><span>Average sale</span><span>{{ r.sales.averageSale | number: '1.2-2' }}</span></div>

      <ng-container *ngIf="r.payments.length">
        <hr />
        <p class="h">Paid by</p>
        <div class="row xz-pay" *ngFor="let p of r.payments"><span>{{ p.name }} ({{ p.count }})</span><span>{{ p.amount | number: '1.2-2' }}</span></div>
      </ng-container>

      <ng-container *ngIf="r.taxes.length">
        <hr />
        <p class="h">Tax</p>
        <div class="row" *ngFor="let t of r.taxes"><span>{{ t.code }}<span *ngIf="t.ratePercent != null"> {{ t.ratePercent }}%</span> on {{ t.net | number: '1.2-2' }}</span><span>{{ t.tax | number: '1.2-2' }}</span></div>
      </ng-container>

      <hr />
      <p class="h">Refunds</p>
      <div class="row"><span>Returns made</span><span class="xz-refunds">{{ r.refunds.count }}</span></div>
      <div class="row" *ngFor="let p of r.refunds.byMethod"><span>&nbsp;&nbsp;{{ p.name }} ({{ p.count }})</span><span>&minus;{{ p.amount | number: '1.2-2' }}</span></div>
      <div class="row b" *ngIf="r.refunds.count"><span>Total refunded</span><span>&minus;{{ r.refunds.total | number: '1.2-2' }}</span></div>

      <hr />
      <p class="h">Cash drawer</p>
      <div class="row"><span>Opening float</span><span>{{ r.drawer.openingFloat | number: '1.2-2' }}</span></div>
      <div class="row"><span>Cash sales</span><span>{{ r.drawer.cashSales | number: '1.2-2' }}</span></div>
      <div class="row"><span>Cash refunds</span><span>&minus;{{ r.drawer.cashRefunds | number: '1.2-2' }}</span></div>
      <div class="row" *ngIf="r.drawer.paidIn"><span>Paid in</span><span>{{ r.drawer.paidIn | number: '1.2-2' }}</span></div>
      <div class="row" *ngIf="r.drawer.paidOut"><span>Paid out</span><span>&minus;{{ r.drawer.paidOut | number: '1.2-2' }}</span></div>
      <div class="row" *ngIf="r.drawer.drops"><span>Dropped into the safe</span><span>&minus;{{ r.drawer.drops | number: '1.2-2' }}</span></div>
      <div class="row b"><span>Expected cash</span><span class="xz-expected">{{ r.drawer.expectedCash | number: '1.2-2' }}</span></div>
      <ng-container *ngIf="r.drawer.countedCash != null">
        <div class="row"><span>Counted cash</span><span class="xz-counted">{{ r.drawer.countedCash | number: '1.2-2' }}</span></div>
        <div class="row b"><span>Difference</span><span class="xz-variance">{{ r.drawer.variance | number: '1.2-2' }}</span></div>
        <p *ngIf="r.drawer.varianceNote" style="font-size:0.85rem; margin:4px 0 0;">Note: {{ r.drawer.varianceNote }}</p>
      </ng-container>
      <ng-container *ngIf="moves().length">
        <p class="h" style="margin-top:8px;">Cash in and out</p>
        <div class="row" *ngFor="let m of moves()"><span>{{ m.createdAt | date: 'HH:mm' }} {{ kind(m.kind) }} — {{ m.reason }}{{ m.note ? ' (' + m.note + ')' : '' }}</span><span>{{ m.kind === 'PAID_IN' ? '' : '−' }}{{ m.amount | number: '1.2-2' }}</span></div>
      </ng-container>

      <hr />
      <p class="h">Watch points</p>
      <div class="row"><span>Sales with a discount</span><span>{{ r.controls.discountedSales }}</span></div>
      <div class="row"><span>Carts dropped with items</span><span class="xz-abandoned">{{ r.controls.abandonedCarts }}<span *ngIf="r.controls.abandonedCarts"> ({{ r.controls.abandonedValue | number: '1.2-2' }})</span></span></div>
      <div class="row" *ngIf="r.controls.offlineSales"><span>Sales rung offline</span><span>{{ r.controls.offlineSales }}</span></div>
      <div class="row" *ngIf="r.controls.creditSales"><span>Sold on credit</span><span>{{ r.controls.creditSales | number: '1.2-2' }}</span></div>
      <div class="row" *ngIf="r.controls.roomCharges"><span>Charged to rooms</span><span>{{ r.controls.roomCharges | number: '1.2-2' }}</span></div>
      <div class="row" *ngIf="r.controls.giftCardsSold"><span>Gift cards sold</span><span>{{ r.controls.giftCardsSold | number: '1.2-2' }}</span></div>
      <div class="row" *ngIf="r.controls.voidedCashMovements"><span>Cash entries voided</span><span>{{ r.controls.voidedCashMovements }}</span></div>

      <ng-container *ngIf="r.cashiers.length">
        <hr />
        <p class="h">By cashier</p>
        <div class="row" *ngFor="let c of r.cashiers"><span>{{ c.name }} ({{ c.sales }})</span><span>{{ c.total | number: '1.2-2' }}</span></div>
      </ng-container>

      <p *ngIf="r.closedBy" style="font-size:0.8rem; color:#666; margin:10px 0 0;">Closed by {{ r.closedBy }}</p>
      <p style="text-align:center; margin-top:16px; color:#666; font-size:0.85rem;">— End of {{ r.type }}-Report —</p>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px; flex-wrap:wrap;">
        <button class="xz-print" (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a [routerLink]="back()" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">Back</a>
      </div>
      <div class="no-print" *ngIf="canExport" style="display:flex; gap:8px; margin-top:8px; font-size:0.85rem;">
        <span style="align-self:center;">Download:</span>
        <button class="xz-x-xlsx" (click)="download('xlsx')">Excel</button>
        <button class="xz-x-pdf" (click)="download('pdf')">PDF</button>
        <button class="xz-x-csv" (click)="download('csv')">CSV</button>
      </div>
    </div>
    <div class="receipt" *ngIf="!r && error"><p style="color:#b91c1c;">{{ error }}</p><a [routerLink]="['/admin/shifts']">Back to Shifts</a></div>
  `,
  styles: [
    `
      :host { display: block; background: #fff; color: #111; min-height: 100vh; font-family: 'Courier New', monospace; }
      .receipt { max-width: 380px; margin: 0 auto; padding: 24px 16px; }
      hr { border: none; border-top: 1px dashed #999; margin: 10px 0; }
      .h { font-weight: 700; margin: 0 0 4px; font-size: 0.9rem; text-transform: uppercase; }
      .row { display: flex; justify-content: space-between; gap: 8px; font-size: 0.88rem; padding: 2px 0; }
      .row.b { font-weight: 700; }
      @media print { .no-print { display: none !important; } }
    `,
  ],
})
export class ShiftXzReportComponent implements OnInit, OnDestroy {
  r: ShiftReport | null = null;
  settings: StoreSettings | null = null;
  error: string | null = null;
  canExport = false;
  private id = 0;
  private z = false;
  private autoPrint = false;
  private readonly afterPrintHandler = () => this.router.navigate(['/admin/shifts']);

  constructor(private route: ActivatedRoute, private router: Router, private registerApi: RegisterApiService, private storeSettingsApi: StoreSettingsApiService,
              private http: HttpClient, private auth: AuthService, private devices: DeviceService, private printSvc: PrintService) {}

  private paperCfg: string | null = null;
  private pageStyle: HTMLStyleElement | null = null;
  directError: string | null = null;

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.z = this.route.snapshot.data['kind'] === 'z';
    this.autoPrint = this.route.snapshot.queryParamMap.get('autoPrint') === '1';
    this.canExport = this.auth.isAdmin() || this.auth.canView('SALES');
    this.storeSettingsApi.get().subscribe({ next: (s) => (this.settings = s), error: () => (this.settings = null) });
    this.registerApi.posSettings().subscribe({
      next: (c) => {
        this.paperCfg = c.receiptPaper;
        this.pageStyle = applyThermalPrintStyle(this.devices.layout(c.receiptPaper).paper, 'app-shift-xz-report', this.pageStyle);
      },
      error: () => (this.pageStyle = applyThermalPrintStyle(this.devices.layout(null).paper, 'app-shift-xz-report', this.pageStyle)),
    });
    // The first load of a Z report is a look; printing (the button, or arriving with ?autoPrint=1) counts it, so the next print says COPY.
    const load = this.z ? this.registerApi.zReport(this.id, this.autoPrint) : this.registerApi.xReport(this.id);
    load.subscribe({
      next: (rep) => {
        this.r = rep;
        if (this.autoPrint) {
          window.addEventListener('afterprint', this.afterPrintHandler);
          setTimeout(() => this.output(), 150);
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the report'),
    });
  }

  ngOnDestroy(): void {
    window.removeEventListener('afterprint', this.afterPrintHandler);
    this.pageStyle?.remove();
  }

  /** Thermal printer connected directly: raw ESC/POS; otherwise the browser print dialog. */
  private output(): void {
    const rep = this.r;
    if (!rep || !this.devices.isDirect('receipt')) {
      window.print();
      return;
    }
    this.directError = null;
    this.printSvc.shiftReport(rep, this.settings, this.paperCfg).then((res) => {
      if (res.ok) {
        if (this.autoPrint) this.afterPrintHandler();
        return;
      }
      this.directError = `The thermal printer did not answer (${res.error}). Printed through the Windows driver instead.`;
      window.print();
    });
  }

  moves() {
    return (this.r?.cashMovements ?? []).filter((m) => !m.voided && m.kind !== 'COUNT');
  }

  kind(k: string): string {
    return k === 'PAID_IN' ? 'Paid in' : k === 'PAID_OUT' ? 'Paid out' : 'Drop';
  }

  back(): string[] {
    return this.z ? ['/admin/shifts'] : ['/cash-control', String(this.id)];
  }

  print(): void {
    if (this.z && !this.autoPrint) {
      // count this printing, then show the number it was printed under
      this.registerApi.zReport(this.id, true).subscribe((rep) => {
        this.r = rep;
        setTimeout(() => this.output(), 100);
      });
      return;
    }
    this.output();
  }

  download(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.http.get(`${API_BASE}/sessions/${this.id}/shift-report/export`, { params: { format }, responseType: 'blob' }).subscribe({
      next: (blob) => downloadBlob(blob, `${this.z ? 'z' : 'x'}-report-${this.id}.${format}`),
      error: () => (this.error = 'The download failed'),
    });
  }
}
