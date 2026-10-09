import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import * as QRCode from 'qrcode';
import { ReturnsApiService } from '../core/api/returns-api.service';
import { EfrisApiService } from '../core/api/efris-api.service';
import { EfrisChadApiService } from '../core/api/efris-chad-api.service';
import { EfrisChadLogos, EfrisLogos, SalesReturn } from '../core/models';

/**
 * A printable receipt for one processed return (Task: "I need to print out the return") —
 * `/returns/:id/receipt`. Reached two ways: automatically right after `return-form.component.ts`
 * processes a return (with `?autoPrint=1`, mirroring checkout's own auto-print-then-navigate-away
 * flow — see `checkout/receipt.component.ts`), or manually via a **Print** link on the Returns
 * list, to reprint one processed earlier. Deliberately light/printable like the sale receipt, not
 * the rest of this (otherwise dark) app — same reasoning: a dark background is a bad idea both on
 * paper and in a print preview.
 *
 * <p>Task: "The return will also integrate with EFRIS" — the EFRIS sections below (logos
 * letterhead, failure warning + retry, SDC/CIS INFORMATION block, QR code) are a direct copy of
 * `checkout/receipt.component.ts`'s own EFRIS treatment, reading off `SalesReturn`'s fields
 * instead of `Order`'s. See that component's own doc comments for why each piece looks the way it
 * does — nothing here changes any of that reasoning, just the model it reads from.
 */
@Component({
  selector: 'app-return-receipt',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="receipt" *ngIf="salesReturn as r">
      <div
        *ngIf="authorityLogoDataUrl || printEfrisLogoDataUrl; else plainReceiptTitle"
        style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;"
      >
        <div style="width:25%; text-align:left;">
          <img *ngIf="authorityLogoDataUrl" [src]="authorityLogoDataUrl" alt="Revenue authority logo" style="max-height:44px; max-width:100%;" />
        </div>
        <h3 style="margin:0; text-align:center;">Return Receipt</h3>
        <div style="width:25%; text-align:right;">
          <img *ngIf="printEfrisLogoDataUrl" [src]="printEfrisLogoDataUrl" alt="EFRIS logo" style="max-height:44px; max-width:100%;" />
        </div>
      </div>
      <ng-template #plainReceiptTitle>
        <h3 style="text-align:center; margin-bottom:0;">Return Receipt</h3>
      </ng-template>
      <p style="text-align:center; margin:4px 0 12px; color:#666; font-size:0.85rem;">
        {{ r.returnDate | date: 'yyyy-MM-dd HH:mm' }}
      </p>
      <p style="margin:0 0 4px;">Original invoice: {{ r.invoiceNumber }}</p>
      <p style="margin:0 0 4px;">Customer: {{ r.customerName }}</p>
      <!-- "Return still completes, flagged for review" — same policy as the sale receipt's own
           efrisError banner: a failed EFRIS submission never blocks or undoes a return, it just
           means the fiscal fields below stayed empty. Staff-facing only (no-print). -->
      <div class="no-print" *ngIf="r.efrisError" style="margin:0 0 8px;">
        <p style="margin:0 0 4px; font-size:0.8rem; color:#b00;">⚠ EFRIS submission failed: {{ r.efrisError }}</p>
        <button (click)="retryEfris()" [disabled]="retryingEfris" style="padding:4px 10px; cursor:pointer; font-size:0.8rem;">
          {{ retryingEfris ? 'Retrying…' : 'Retry EFRIS submission' }}
        </button>
        <span *ngIf="retryError" style="margin-left:8px; font-size:0.8rem; color:#b00;">{{ retryError }}</span>
      </div>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr *ngFor="let l of r.lines">
            <td style="padding:4px 0;">
              {{ l.description }}
              <div style="color:#666; font-size:0.8rem;">Qty returned: {{ l.quantity }}</div>
            </td>
            <td style="text-align:right; padding:4px 0; vertical-align:top;">
              {{ l.refundAmount != null ? '−' + (l.refundAmount | number: '1.2-2') : '—' }}
            </td>
          </tr>
          <tr *ngIf="r.lines.length === 0">
            <td colspan="2" class="text-muted">No line detail recorded for this return.</td>
          </tr>
        </tbody>
      </table>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr style="font-weight:700;">
            <td>Refund total</td>
            <td style="text-align:right;">−{{ r.refundTotal | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td>Refund method</td>
            <td style="text-align:right;">{{ r.refundMethod }}</td>
          </tr>
        </tbody>
      </table>
      <p *ngIf="r.reason" style="margin:8px 0 0; font-size:0.85rem; color:#444;">Reason: {{ r.reason }}</p>

      <!-- EFRIS (Rwanda RRA e-invoicing) fiscal receipt data — same layout as the sale receipt's
           own SDC INFORMATION block; simply doesn't render for a return never submitted to EFRIS
           (r.sdcId null). -->
      <div *ngIf="r.sdcId" style="margin-top:10px; font-size:0.8rem;">
        <hr />
        <p style="text-align:center; font-weight:700; margin:0 0 6px;">SDC INFORMATION</p>
        <div *ngIf="r.sdcDate && dateTimeParts(r.sdcDate) as sdcDT" style="display:flex; justify-content:space-between;">
          <span>SDC DATE:{{ sdcDT.date }}</span>
          <span>TIME:{{ sdcDT.time }}</span>
        </div>
        <div *ngIf="r.sdcReceiptNumber" style="display:flex; justify-content:space-between;">
          <span>SDC RECEIPT NUMBER:</span>
          <span>{{ r.sdcReceiptNumber }}</span>
        </div>
        <div style="display:flex; justify-content:space-between;">
          <span>SDC ID:</span>
          <span>{{ r.sdcId }}</span>
        </div>
        <div *ngIf="r.internalData" style="text-align:center; margin-top:6px;">
          <div>Internal Data:</div>
          <div style="word-break:break-all;">{{ r.internalData }}</div>
        </div>
        <div *ngIf="r.signatureData" style="text-align:center; margin-top:6px;">
          <div>Receipt Signature:</div>
          <div style="word-break:break-all;">{{ r.signatureData }}</div>
        </div>
        <hr *ngIf="r.cisNumber || r.cisDate || r.mrcData" />
        <div *ngIf="r.cisNumber" style="display:flex; justify-content:space-between;">
          <span>CIS RECEIPT NUMBER:</span>
          <span>{{ r.cisNumber }}</span>
        </div>
        <div *ngIf="r.cisDate && dateTimeParts(r.cisDate) as cisDT" style="display:flex; justify-content:space-between;">
          <span>CIS DATE:{{ cisDT.date }}</span>
          <span>TIME:{{ cisDT.time }}</span>
        </div>
        <div *ngIf="r.mrcData" style="display:flex; justify-content:space-between;">
          <span>MRC:</span>
          <span>{{ r.mrcData }}</span>
        </div>
        <div *ngIf="qrCodeDataUrl" style="text-align:center; margin-top:8px;">
          <img [src]="qrCodeDataUrl" alt="EFRIS QR code" style="width:120px; height:120px;" />
        </div>
      </div>

      <p style="text-align:center; margin-top:20px; color:#666; font-size:0.85rem;">— Returned —</p>

      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a routerLink="/returns" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">
          Back to returns
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
export class ReturnReceiptComponent implements OnInit, OnDestroy {
  salesReturn: SalesReturn | null = null;

  /** Set only when return-form.component.ts navigates here right after processing a return — see receipt.component.ts's identical convention. */
  private autoPrint = false;
  private readonly afterPrintHandler = () => this.router.navigate(['/returns']);

  /** See checkout/receipt.component.ts's identical fields for what each state means — this is a direct copy, just for a return's own "Retry EFRIS submission" button. */
  retryingEfris = false;
  retryError: string | null = null;

  /** Same "generate once the fiscal fields are known" data: URL PNG as the sale receipt's own qrCodeDataUrl. */
  qrCodeDataUrl: string | null = null;

  /** This return's original sale's own terminal logos — same EfrisApiService.getLogosForRegister lookup the sale receipt uses, keyed off SalesReturn.registerId instead of Order.registerId. */
  efrisLogos: EfrisLogos | null = null;

  /** Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — Chad's own copy of {@link efrisLogos}, same lookup pattern. */
  efrisChadLogos: EfrisChadLogos | null = null;

  /** Merges Rwanda's RRA logo and Chad's CRA logo into one slot — see checkout/receipt.component.ts's identical getter for why this is always safe. */
  get authorityLogoDataUrl(): string | null {
    return this.efrisLogos?.rraLogoDataUrl ?? this.efrisChadLogos?.craLogoDataUrl ?? null;
  }

  /** Merges the two integrations' EFRIS logo, same as {@link authorityLogoDataUrl}. */
  get printEfrisLogoDataUrl(): string | null {
    return this.efrisLogos?.efrisLogoDataUrl ?? this.efrisChadLogos?.efrisLogoDataUrl ?? null;
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private returnsApi: ReturnsApiService,
    private efrisApi: EfrisApiService,
    private efrisChadApi: EfrisChadApiService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.autoPrint = this.route.snapshot.queryParamMap.get('autoPrint') === '1';
    this.returnsApi.getById(id).subscribe((r) => {
      this.salesReturn = r;
      this.generateQrCode(r);
      this.loadEfrisLogos(r);
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

  /** Identical splitting rule to checkout/receipt.component.ts's own dateTimeParts — see its doc comment. */
  dateTimeParts(raw: string | null): { date: string; time: string } {
    if (!raw) return { date: '', time: '' };
    const spaceIndex = raw.indexOf(' ');
    if (spaceIndex === -1) return { date: raw, time: '' };
    return { date: raw.slice(0, spaceIndex), time: raw.slice(spaceIndex + 1) };
  }

  /** Identical QR payload spec to checkout/receipt.component.ts's own generateQrCode — see its doc comment for the exact field spec and why each step degrades the way it does. */
  private generateQrCode(r: SalesReturn): void {
    this.qrCodeDataUrl = null;
    if (!r.sdcId) return;
    const inputData = `${r.sdcDate ?? ''}#${r.sdcId ?? ''}#${r.sdcReceiptNumber ?? ''}#+${r.internalData ?? ''}#+${r.signatureData ?? ''}`;
    QRCode.toDataURL(inputData, { margin: 1, width: 240 })
      .then((dataUrl) => (this.qrCodeDataUrl = dataUrl))
      .catch(() => (this.qrCodeDataUrl = null));
  }

  /** Identical lookup to checkout/receipt.component.ts's own loadEfrisLogos (including the Task 3 Chad logos fetch), keyed off this return's original sale's registerId instead. */
  private loadEfrisLogos(r: SalesReturn): void {
    this.efrisLogos = null;
    this.efrisChadLogos = null;
    if (!r.registerId) return;
    this.efrisApi.getLogosForRegister(r.registerId).subscribe({
      next: (logos) => (this.efrisLogos = logos),
      error: () => (this.efrisLogos = null),
    });
    this.efrisChadApi.getLogosForRegister(r.registerId).subscribe({
      next: (logos) => (this.efrisChadLogos = logos),
      error: () => (this.efrisChadLogos = null),
    });
  }

  /** See checkout/receipt.component.ts's identical retryEfris for what each state means. */
  retryEfris(): void {
    if (!this.salesReturn || this.retryingEfris) return;
    this.retryingEfris = true;
    this.retryError = null;
    this.returnsApi.retryEfris(this.salesReturn.id).subscribe({
      next: (r) => {
        this.salesReturn = r;
        this.generateQrCode(r);
        this.retryingEfris = false;
      },
      error: (err) => {
        this.retryError = err?.error?.message ?? 'Failed to retry EFRIS submission';
        this.retryingEfris = false;
      },
    });
  }
}
