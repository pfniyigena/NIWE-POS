import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PurchaseOrderApiService } from '../core/api/purchase-order-api.service';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { PurchaseOrder, StoreSettings, Supplier, Warehouse } from '../core/models';

/**
 * A printable purchase order (Task 3: "Print out purchase order") — `/admin/purchase-orders/:id/print`,
 * reached via a "Print" link on the detail page. Deliberately light/printable like the sale and
 * return receipts (see those components' own doc comments for why a dark background is a bad
 * idea both on paper and in a print preview) — this is the one document in this admin section
 * meant to leave the building (handed or emailed to a supplier), so it gets the same treatment.
 * Unlike a sale/return receipt, there's no EFRIS fiscal data here at all — a purchase order is
 * this store buying from a supplier, not a customer-facing fiscal sale, so none of that applies.
 *
 * Supplier contact details (phone/email/address) aren't on `PurchaseOrderDto` itself — only
 * `supplierId`/`supplierName` are, since nothing else in the app needed more than the name. Rather
 * than snapshot those onto the PO (there's no reason to prefer a stale address over the supplier's
 * current one when printing an order to send them), this just looks the supplier up by id from
 * `SupplierApiService.listAll()` — there's no single-supplier GET endpoint, and the supplier list
 * is small enough that fetching all of them client-side (same as the purchase-order form's own
 * supplier picker already does) is simpler than adding one just for this.
 */
@Component({
  selector: 'app-purchase-order-print',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="receipt" *ngIf="po as o">
      <div *ngIf="settings as s" style="text-align:center; margin:8px 0 12px;">
        <img *ngIf="s.logoDataUrl" [src]="s.logoDataUrl" alt="" style="max-width:120px; max-height:80px; margin-bottom:6px;" />
        <div *ngIf="s.businessName" style="font-weight:700; font-size:1.1rem;">{{ s.businessName }}</div>
        <div *ngIf="s.address" style="font-size:0.8rem; color:#444;">{{ s.address }}</div>
        <div *ngIf="s.phone || s.email" style="font-size:0.8rem; color:#444;">
          <span *ngIf="s.phone">{{ s.phone }}</span><span *ngIf="s.phone && s.email"> &middot; </span><span *ngIf="s.email">{{ s.email }}</span>
        </div>
        <div *ngIf="s.taxId" style="font-size:0.8rem; color:#444;">Tax ID: {{ s.taxId }}</div>
      </div>
      <h3 style="text-align:center; margin:4px 0 0;">Purchase Order</h3>
      <p style="text-align:center; margin:4px 0 12px; color:#666; font-size:0.85rem;">
        {{ o.orderNumber }} &middot; {{ o.status.replace('_', ' ') }} &middot; {{ o.orderDate | date: 'mediumDate' }}
      </p>

      <div style="display:flex; justify-content:space-between; gap:16px; margin-bottom:10px;">
        <div style="flex:1;">
          <div style="font-weight:700; font-size:0.8rem; color:#666;">SUPPLIER</div>
          <div>{{ o.supplierName ?? '—' }}</div>
          <div *ngIf="supplier?.contactName" style="font-size:0.85rem;">Attn: {{ supplier?.contactName }}</div>
          <div *ngIf="supplier?.address" style="font-size:0.85rem;">{{ supplier?.address }}</div>
          <div *ngIf="supplier?.phone || supplier?.email" style="font-size:0.85rem;">
            <span *ngIf="supplier?.phone">{{ supplier?.phone }}</span
            ><span *ngIf="supplier?.phone && supplier?.email"> &middot; </span
            ><span *ngIf="supplier?.email">{{ supplier?.email }}</span>
          </div>
        </div>
        <div style="flex:1; text-align:right;">
          <div style="font-weight:700; font-size:0.8rem; color:#666;">EXPECTED</div>
          <div>{{ o.expectedDate ? (o.expectedDate | date: 'mediumDate') : '—' }}</div>
          <!-- Multi-warehouse: where to deliver — shown only once the store has more than one warehouse, so single-warehouse shops' printouts don't change. -->
          <ng-container *ngIf="deliverTo as w">
            <div style="font-weight:700; font-size:0.8rem; color:#666; margin-top:8px;">DELIVER TO</div>
            <div>{{ w.name }}</div>
            <div *ngIf="w.address" style="font-size:0.85rem;">{{ w.address }}</div>
          </ng-container>
        </div>
      </div>

      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <thead>
          <tr style="text-align:left; color:#666; font-size:0.8rem;">
            <th style="padding:2px 0;">Product</th>
            <th style="text-align:right; padding:2px 0;">Qty</th>
            <th style="text-align:right; padding:2px 0;">Unit cost</th>
            <!-- Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — only shown once this order actually has a discount on at least one line, same convention as every other printed document's own addSubtotalBreakdown. -->
            <th style="text-align:right; padding:2px 0;" *ngIf="discountAmount()">Discount</th>
            <th style="text-align:right; padding:2px 0;">Total</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let l of o.lines">
            <td style="padding:4px 0;">
              {{ l.productName }}
              <div *ngIf="l.quantityReceived > 0" style="color:#666; font-size:0.8rem;">
                Received so far: {{ l.quantityReceived }}
              </div>
            </td>
            <td style="text-align:right; padding:4px 0; vertical-align:top;">{{ l.quantityOrdered }}</td>
            <td style="text-align:right; padding:4px 0; vertical-align:top;">{{ l.unitCost | number: '1.2-2' }}</td>
            <td style="text-align:right; padding:4px 0; vertical-align:top;" *ngIf="discountAmount()">{{ lineDiscountAmount(l) | number: '1.2-2' }}</td>
            <td style="text-align:right; padding:4px 0; vertical-align:top;">{{ lineTotal(l) | number: '1.2-2' }}</td>
          </tr>
          <tr *ngIf="o.lines.length === 0">
            <td [attr.colspan]="discountAmount() ? 5 : 4" class="text-muted">No lines on this order.</td>
          </tr>
        </tbody>
      </table>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr>
            <td>Total quantity ordered</td>
            <td style="text-align:right;">{{ totalQuantity() }}</td>
          </tr>
          <!-- Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — this view has no backend-rendered PDF (see class javadoc), so the tax breakdown is computed client-side from each line's own taxRatePercent/taxInclusive, mirroring the backend's TaxMath formula. -->
          <tr>
            <td>Subtotal</td>
            <td style="text-align:right;">{{ subtotalAmount() | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td>Tax</td>
            <td style="text-align:right;">{{ taxAmount() | number: '1.2-2' }}</td>
          </tr>
          <!-- Fourier: "Same for discount ... it will be per line" — same zero-suppressed convention as every other printed document; purely informational, already folded into Subtotal/Grand total above. -->
          <tr *ngIf="discountAmount()">
            <td>Discount</td>
            <td style="text-align:right;">-{{ discountAmount() | number: '1.2-2' }}</td>
          </tr>
          <tr style="font-weight:700;">
            <td>Grand total</td>
            <td style="text-align:right;">{{ grandTotal() | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <p *ngIf="o.notes" style="margin:8px 0 0; font-size:0.85rem; color:#444;">Notes: {{ o.notes }}</p>

      <p style="text-align:center; margin-top:20px; color:#666; font-size:0.85rem;">— End of purchase order —</p>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a [routerLink]="['/admin/purchase-orders', o.id]" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">
          Back to purchase order
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
        max-width: 480px;
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
export class PurchaseOrderPrintComponent implements OnInit {
  po: PurchaseOrder | null = null;
  supplier: Supplier | null = null;
  /** The order's receiving warehouse, set only when the store has more than one active warehouse. */
  deliverTo: Warehouse | null = null;
  settings: StoreSettings | null = null;

  constructor(
    private route: ActivatedRoute,
    private purchaseOrderApi: PurchaseOrderApiService,
    private supplierApi: SupplierApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private warehouseApi: WarehouseApiService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.settings = s),
      error: () => (this.settings = null), // header just falls back to no letterhead — never blocks printing
    });
    this.purchaseOrderApi.getById(id).subscribe((po) => {
      this.po = po;
      this.warehouseApi.listAll().subscribe({
        next: (all) => {
          const active = all.filter((w) => w.active);
          this.deliverTo = active.length > 1 ? (all.find((w) => w.id === po.receivingWarehouseId) ?? null) : null;
        },
        error: () => (this.deliverTo = null), // extra detail only — never blocks printing
      });
      if (po.supplierId != null) {
        this.supplierApi.listAll().subscribe({
          next: (suppliers) => (this.supplier = suppliers.find((s) => s.id === po.supplierId) ?? null),
          error: () => (this.supplier = null), // extra contact detail only — never blocks printing
        });
      }
    });
  }

  print(): void {
    window.print();
  }

  /**
   * Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — mirrors the
   * backend's `com.posapp.common.TaxMath.taxOnAmount`. This print view has no backend-rendered PDF
   * (unlike Quotation/Invoice/SalesOrder/Bill, which go through DocumentPdfService), so the tax
   * figures are derived here, client-side, from each line's own snapshot (`taxRatePercent`,
   * `taxInclusive`) rather than fetched pre-computed.
   */
  private taxOnAmount(amount: number, ratePercent: number | null, inclusive: boolean): number {
    if (!ratePercent) return 0;
    const rate = ratePercent / 100;
    const raw = inclusive ? (amount * rate) / (1 + rate) : amount * rate;
    return Math.round(raw * 100) / 100;
  }

  /** The line's raw gross amount (`unitCost * quantityOrdered`), before any discount is taken off. */
  lineGrossBeforeDiscount(line: { quantityOrdered: number; unitCost: number }): number {
    return Math.round(line.quantityOrdered * line.unitCost * 100) / 100;
  }

  /**
   * Fourier: "Except POS other module can have global discount ... Either on line or global" —
   * mirrors the backend's `com.posapp.common.TaxMath.effectiveDiscountPercent`: the order's own
   * header-level `discountPercent` (GLOBAL mode) wins whenever it's positive, otherwise the
   * line's own (PER_LINE mode, or no discount at all if both are zero).
   */
  private effectiveDiscountPercent(lineDiscountPercent: number | null): number {
    const header = this.po?.discountPercent;
    if (header && header > 0) return header;
    return lineDiscountPercent ?? 0;
  }

  /**
   * Fourier: "Same for discount (but discount should be activate in the setting or
   * configuration) and it will be per line" — mirrors the backend's
   * `com.posapp.common.TaxMath.applyDiscount`: discount is taken off the gross amount BEFORE tax
   * is computed on what's left (same ordering {@code OrderCalculator.lineTotal} already uses for
   * the POS terminal's own discount) — already tax-inclusive when `taxInclusive` is true.
   */
  lineGross(line: { quantityOrdered: number; unitCost: number; discountPercent: number | null }): number {
    const gross = this.lineGrossBeforeDiscount(line);
    const discountPercent = this.effectiveDiscountPercent(line.discountPercent);
    if (!discountPercent) return gross;
    return Math.round(gross * (1 - discountPercent / 100) * 100) / 100;
  }

  /** How much this line's discount actually knocked off — purely informational, same as the backend's own `lineDiscountAmount` helpers. */
  lineDiscountAmount(line: { quantityOrdered: number; unitCost: number; discountPercent: number | null }): number {
    return Math.round((this.lineGrossBeforeDiscount(line) - this.lineGross(line)) * 100) / 100;
  }

  /** The tax portion of this line, extracted (inclusive) or added (exclusive) from its (already-discounted) gross amount. */
  lineTax(line: { quantityOrdered: number; unitCost: number; discountPercent: number | null; taxRatePercent: number | null; taxInclusive: boolean }): number {
    return this.taxOnAmount(this.lineGross(line), line.taxRatePercent, line.taxInclusive);
  }

  /** The line's net (pre-tax) amount — equal to its (already-discounted) gross amount when tax is exclusive. */
  lineNet(line: { quantityOrdered: number; unitCost: number; discountPercent: number | null; taxRatePercent: number | null; taxInclusive: boolean }): number {
    const gross = this.lineGross(line);
    const tax = this.lineTax(line);
    return line.taxInclusive ? Math.round((gross - tax) * 100) / 100 : gross;
  }

  /** What this line actually costs in total — (already-discounted) gross when tax is inclusive, gross + tax when exclusive. */
  lineTotal(line: { quantityOrdered: number; unitCost: number; discountPercent: number | null; taxRatePercent: number | null; taxInclusive: boolean }): number {
    const gross = this.lineGross(line);
    return line.taxInclusive ? gross : Math.round((gross + this.lineTax(line)) * 100) / 100;
  }

  totalQuantity(): number {
    return this.po?.lines.reduce((sum, l) => sum + l.quantityOrdered, 0) ?? 0;
  }

  subtotalAmount(): number {
    return Math.round((this.po?.lines.reduce((sum, l) => sum + this.lineNet(l), 0) ?? 0) * 100) / 100;
  }

  taxAmount(): number {
    return Math.round((this.po?.lines.reduce((sum, l) => sum + this.lineTax(l), 0) ?? 0) * 100) / 100;
  }

  /** Fourier: "Same for discount ... it will be per line" — sum of every line's own discount; purely informational, already folded into subtotalAmount()/grandTotal() above. */
  discountAmount(): number {
    return Math.round((this.po?.lines.reduce((sum, l) => sum + this.lineDiscountAmount(l), 0) ?? 0) * 100) / 100;
  }

  grandTotal(): number {
    return Math.round((this.po?.lines.reduce((sum, l) => sum + this.lineTotal(l), 0) ?? 0) * 100) / 100;
  }
}
