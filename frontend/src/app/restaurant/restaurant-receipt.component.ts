import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { DeviceService } from '../core/hardware/device.service';
import { PrintService } from '../core/hardware/print.service';
import { applyThermalPrintStyle } from '../core/hardware/thermal-css';
import { RegisterApiService } from '../core/api/register-api.service';
import { RestaurantOrder, StoreSettings } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Restaurant order receipt — lookup + reprint (Restaurant Receipts' "View / Reprint" link), the
 * simplified restaurant counterpart to `checkout/receipt.component.ts`. Deliberately light/
 * printable like that one (see its own doc comment on why a dark background is a bad idea on
 * paper), but without any of the POS receipt's EFRIS/QR/email machinery — restaurant checkout has
 * no fiscal-integration story of its own, so this is just business header + items + totals +
 * payments.
 */
@Component({
  selector: 'app-restaurant-receipt',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="receipt" *ngIf="order as o">
      <p class="no-print dev-direct-error" *ngIf="directError" style="color:#b00; font-size:0.85rem;">{{ directError }}</p>
      <h3 style="text-align:center; margin-bottom:0;">{{ 'Receipt' | translate }}<span class="r-copy" *ngIf="(o.printCount ?? 0) > 1"> {{ '— COPY {p0}' | translate: { p0: ((o.printCount ?? 1) - 1) } }}</span></h3>
      <div *ngIf="settings as s" style="text-align:center; margin:8px 0 12px;">
        <img *ngIf="s.logoDataUrl" [src]="s.logoDataUrl" alt="" style="max-width:120px; max-height:80px; margin-bottom:6px;" />
        <div *ngIf="s.businessName" style="font-weight:700; font-size:1.1rem;">{{ s.businessName }}</div>
        <div *ngIf="s.address" style="font-size:0.8rem; color:#444;">{{ s.address }}</div>
        <div *ngIf="s.phone || s.email" style="font-size:0.8rem; color:#444;">
          <span *ngIf="s.phone">{{ s.phone }}</span><span *ngIf="s.phone && s.email"> &middot; </span><span *ngIf="s.email">{{ s.email }}</span>
        </div>
        <div *ngIf="s.taxId" style="font-size:0.8rem; color:#444;">{{ 'Tax ID: {p0}' | translate: { p0: (s.taxId) } }}</div>
      </div>
      <p style="text-align:center; margin:4px 0 12px;">{{ o.ticketNumber }}</p>
      <p style="text-align:center; margin:0 0 8px; color:#666; font-size:0.85rem;">
        {{ o.displayLabel }}<span *ngIf="o.serverName"> &middot; served by {{ o.serverName }}</span>
      </p>
      <p style="margin:0 0 8px;" *ngIf="o.customerPhone">{{ 'Phone: {p0}' | translate: { p0: (o.customerPhone) } }}</p>
      <p style="margin:0 0 8px;" *ngIf="o.customerAddress">{{ 'Address: {p0}' | translate: { p0: (o.customerAddress) } }}</p>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr *ngFor="let l of o.lines">
            <td style="padding:4px 0;">
              {{ l.description }}
              <div style="color:#666; font-size:0.8rem;">
                {{ l.quantity }} &times; {{ l.unitPrice + (l.optionsPrice ?? 0) | number: '1.2-2' }}<span *ngIf="l.station"> &middot; {{ l.station }}</span>
              </div>
              <div class="r-options" style="color:#666; font-size:0.8rem;" *ngIf="l.options && l.options.length > 0">
                <span *ngFor="let o2 of l.options; let last = last">{{ o2.name }}<span *ngIf="o2.priceDelta !== 0"> ({{ o2.priceDelta > 0 ? '+' : '' }}{{ o2.priceDelta | number: '1.2-2' }})</span><span *ngIf="!last">, </span></span>
              </div>
              <div style="color:#666; font-size:0.8rem;" *ngIf="l.notes">{{ l.notes }}</div>
              <div style="color:#666; font-size:0.8rem;" *ngIf="(l.discountTotal ?? 0) > 0">{{ 'discount −{p0}' | translate: { p0: (l.discountTotal | number: '1.2-2') } }}</div>
            </td>
            <td style="text-align:right; padding:4px 0; vertical-align:top;">{{ l.lineGrandTotal | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr class="r-discount" *ngIf="(o.discountTotal ?? 0) > 0">
            <td>{{ 'Discounts given' | translate }}<span *ngIf="o.discountReason"> ({{ o.discountReason }})</span></td>
            <td style="text-align:right;">−{{ o.discountTotal | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td>{{ 'Subtotal' | translate }}</td>
            <td style="text-align:right;">{{ o.subtotal | number: '1.2-2' }}</td>
          </tr>
          <tr class="r-service" *ngIf="(o.serviceCharge ?? 0) > 0">
            <td>{{ 'Service charge ({p0}%)' | translate: { p0: (o.serviceChargePercent) } }}</td>
            <td style="text-align:right;">{{ o.serviceCharge | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td>{{ 'Tax' | translate }}</td>
            <td style="text-align:right;">{{ o.taxTotal | number: '1.2-2' }}</td>
          </tr>
          <tr class="r-rounding" *ngIf="(o.roundingAdjustment ?? 0) !== 0">
            <td>{{ 'Rounding' | translate }}</td>
            <td style="text-align:right;">{{ o.roundingAdjustment | number: '1.2-2' }}</td>
          </tr>
          <tr style="font-weight:700;">
            <td>{{ 'Total' | translate }}</td>
            <td style="text-align:right;">{{ o.total | number: '1.2-2' }}</td>
          </tr>
          <tr class="r-tip" *ngIf="(o.tipAmount ?? 0) > 0">
            <td>{{ 'Tip' | translate }}</td>
            <td style="text-align:right;">{{ o.tipAmount | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <hr />
      <p style="margin:0 0 4px; font-weight:600;">{{ 'Payment' | translate }}</p>
      <div *ngFor="let p of o.payments" style="display:flex; justify-content:space-between; font-size:0.9rem;">
        <span>{{ p.method }}</span>
        <span>{{ p.amount | number: '1.2-2' }}</span>
      </div>
      <div class="r-deposit" *ngIf="(o.depositApplied ?? 0) > 0" style="display:flex; justify-content:space-between; font-size:0.9rem;">
        <span>{{ 'Reservation deposit (paid earlier)' | translate }}</span>
        <span>{{ o.depositApplied | number: '1.2-2' }}</span>
      </div>
      <div class="r-change" *ngIf="(o.changeGiven ?? 0) > 0" style="display:flex; justify-content:space-between; font-size:0.9rem; color:#666;">
        <span>{{ 'Change given' | translate }}</span>
        <span>{{ o.changeGiven | number: '1.2-2' }}</span>
      </div>

      <p style="text-align:center; margin-top:20px; color:#666; font-size:0.85rem;">{{ 'Thank you!' | translate }}</p>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">{{ 'Print' | translate }}</button>
        <a routerLink="/admin/restaurant/receipts" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">
          {{ 'Back to receipts' | translate }}
        </a>
      </div>
    </div>
    <p *ngIf="!order && error" class="no-print" style="text-align:center; margin-top:40px; color:#b00;">{{ error }}</p>
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
export class RestaurantReceiptComponent implements OnInit, OnDestroy {
  directError: string | null = null;
  private paperCfg: string | null = null;
  private pageStyle: HTMLStyleElement | null = null;
  order: RestaurantOrder | null = null;
  settings: StoreSettings | null = null;
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private orderApi: RestaurantOrderApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private devices: DeviceService,
    private printSvc: PrintService,
    private registerApi: RegisterApiService
  ) {}

  ngOnDestroy(): void {
    this.pageStyle?.remove();
  }

  ngOnInit(): void {
    const orderId = Number(this.route.snapshot.paramMap.get('orderId'));
    this.registerApi.posSettings().subscribe({
      next: (c) => {
        this.paperCfg = c.receiptPaper;
        this.pageStyle = applyThermalPrintStyle(this.devices.layout(c.receiptPaper).paper, 'app-restaurant-receipt', this.pageStyle);
      },
      error: () => (this.pageStyle = applyThermalPrintStyle(this.devices.layout(null).paper, 'app-restaurant-receipt', this.pageStyle)),
    });
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.settings = s),
      error: () => (this.settings = null), // header just falls back to plain "Receipt" — never blocks printing
    });
    this.orderApi.getById(orderId).subscribe({
      next: (o) => (this.order = o),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load receipt')),
    });
  }

  /** Records the print first (the second and later prints are marked as copies and logged), then opens the print dialog. */
  print(): void {
    const o = this.order;
    if (o && o.status === 'CLOSED') {
      this.orderApi.recordPrint(o.id).subscribe({
        next: (updated) => {
          this.order = updated;
          setTimeout(() => this.output(), 50);
        },
        error: () => this.output(),
      });
    } else {
      this.output();
    }
  }

  /** Thermal printer connected directly: raw ESC/POS (and the cash drawer for a cash bill); otherwise the browser print dialog. */
  private output(): void {
    const o = this.order;
    if (!o || !this.devices.isDirect('receipt')) {
      window.print();
      return;
    }
    this.directError = null;
    this.printSvc.restaurantBill(o, this.settings, this.paperCfg, o.status !== 'CLOSED').then((res) => {
      if (res.ok) return;
      this.directError = `The thermal printer did not answer (${res.error}). Printed through the Windows driver instead.`;
      window.print();
    });
  }
}
