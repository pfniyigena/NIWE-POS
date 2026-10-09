import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import * as QRCode from 'qrcode';
import { OrderApiService } from '../core/api/order-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { ReturnsApiService } from '../core/api/returns-api.service';
import { EfrisApiService } from '../core/api/efris-api.service';
import { EfrisChadApiService } from '../core/api/efris-chad-api.service';
import { BranchReceiptInfo, EfrisChadLogos, EfrisLogos, Order, StoreSettings } from '../core/models';

/**
 * Deliberately light/printable, unlike the rest of this (otherwise all-dark) app: a dark
 * background is a bad idea both on paper and in a print preview. See README "Design system".
 */
@Component({
  selector: 'app-receipt',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="receipt" *ngIf="order as o">
      <!-- Fourier's own follow-up: "those two logos ... right at the top of receipt. Better put
           Receipt Label in center" — moved here from the SDC INFORMATION footer (where it first
           landed) to the very top of the printout, revenue-authority logo on the left and EFRIS on
           the right, flanking the "Receipt" title letterhead-style (see loadEfrisLogos's doc
           comment for where the images come from). Task 3: "Add also Chad Revenue Authority logo
           and EFRIS logo on EFRIS Chad setting" — authorityLogoDataUrl/printEfrisLogoDataUrl below
           merge Rwanda's RRA logo and Chad's CRA logo into one pair of slots, since "One Terminal
           can have only one EFRIS mapped with" guarantees at most one of the two lookups ever
           returns a logo for a given terminal. Deliberately no longer gated on o.sdcId now that
           it's the page header rather than part of the fiscal footer: a terminal with these logos
           configured prints them on every receipt, submitted to EFRIS or not, the same way the
           business logo/name below always prints regardless of EFRIS status. Falls back to the
           plain centered "Receipt" heading (unchanged from before this logo feature existed) when
           the terminal has no logos configured yet. -->
      <div
        *ngIf="authorityLogoDataUrl || printEfrisLogoDataUrl; else plainReceiptTitle"
        style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;"
      >
        <div style="width:25%; text-align:left;">
          <img *ngIf="authorityLogoDataUrl" [src]="authorityLogoDataUrl" alt="Revenue authority logo" style="max-height:44px; max-width:100%;" />
        </div>
        <h3 style="margin:0; text-align:center;">Receipt</h3>
        <div style="width:25%; text-align:right;">
          <img *ngIf="printEfrisLogoDataUrl" [src]="printEfrisLogoDataUrl" alt="EFRIS logo" style="max-height:44px; max-width:100%;" />
        </div>
      </div>
      <ng-template #plainReceiptTitle>
        <h3 style="text-align:center; margin-bottom:0;">Receipt</h3>
      </ng-template>
      <!-- Task: "Add Business details + logo on the header of receipt printout" — settings is null
           until the GET resolves and every field on it is nullable, so this whole block degrades
           gracefully to nothing extra when nothing has been configured yet (unchanged behavior). -->
      <div *ngIf="settings as s" style="text-align:center; margin:8px 0 12px;">
        <img *ngIf="s.logoDataUrl" [src]="s.logoDataUrl" alt="" style="max-width:120px; max-height:80px; margin-bottom:6px;" />
        <div *ngIf="s.businessName" style="font-weight:700; font-size:1.1rem;">{{ s.businessName }}</div>
        <!-- Branches: a branch's own name/address/contact/tax id replace the business-wide ones when it has them; its receipt header prints beneath. -->
        <div *ngIf="branchInfo as b" style="font-size:0.9rem; font-weight:600;">{{ b.name }}</div>
        <div *ngIf="branchInfo?.address || s.address" style="font-size:0.8rem; color:#444;">{{ branchInfo?.address || s.address }}</div>
        <div *ngIf="(branchInfo?.phone || s.phone) || (branchInfo?.email || s.email)" style="font-size:0.8rem; color:#444;">
          <span *ngIf="branchInfo?.phone || s.phone">{{ branchInfo?.phone || s.phone }}</span><span *ngIf="(branchInfo?.phone || s.phone) && (branchInfo?.email || s.email)"> &middot; </span><span *ngIf="branchInfo?.email || s.email">{{ branchInfo?.email || s.email }}</span>
        </div>
        <div *ngIf="branchInfo?.taxId || s.taxId" style="font-size:0.8rem; color:#444;">Tax ID: {{ branchInfo?.taxId || s.taxId }}</div>
        <div *ngIf="branchInfo?.receiptHeader" style="font-size:0.8rem; color:#444; white-space:pre-line; margin-top:4px;">{{ branchInfo?.receiptHeader }}</div>
      </div>
      <p style="text-align:center; margin:4px 0 12px;">{{ o.invoiceNumber }}</p>
      <p style="text-align:center; margin:0 0 8px; color:#666; font-size:0.85rem;">
        {{ o.registerName }} &middot; served by {{ o.cashierName }}
      </p>
      <p style="margin:0 0 8px;">Customer: {{ o.customerName }}</p>
      <!-- Task: "local queuing of sales that sync once connectivity returns" — this sale was rung
           offline (network was down at the terminal) and only actually created on the server once
           connectivity returned; flagged here rather than silently printing an ordinary receipt. -->
      <p class="no-print" *ngIf="o.offlineQueuedAt" style="margin:0 0 8px; font-size:0.8rem; color:#666;">
        📴 Rung offline at {{ o.offlineQueuedAt | date: 'medium' }}, synced once back online.
      </p>
      <p class="no-print" *ngIf="o.syncNote" style="margin:0 0 8px; font-size:0.8rem; color:#b00;">
        ⚠ {{ o.syncNote }}
      </p>
      <!-- "Sale still completes, flagged for review" — a failed EFRIS submission never blocks or
           voids the sale, it just means the fiscal fields below stayed empty. Staff-facing only
           (no-print), same as syncNote above. -->
      <div class="no-print" *ngIf="o.efrisError" style="margin:0 0 8px;">
        <p style="margin:0 0 4px; font-size:0.8rem; color:#b00;">⚠ EFRIS submission failed: {{ o.efrisError }}</p>
        <button (click)="retryEfris()" [disabled]="retryingEfris" style="padding:4px 10px; cursor:pointer; font-size:0.8rem;">
          {{ retryingEfris ? 'Retrying…' : 'Retry EFRIS submission' }}
        </button>
        <span *ngIf="retryError" style="margin-left:8px; font-size:0.8rem; color:#b00;">{{ retryError }}</span>
      </div>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr *ngFor="let l of o.lines">
            <td style="padding:4px 0;">
              {{ l.description }}
              <div style="color:#666; font-size:0.8rem;">{{ l.quantity }} &times; {{ l.unitPrice | number: '1.2-2' }}<span *ngIf="l.discountPercent"> (−{{ l.discountPercent }}%)</span><span *ngIf="l.taxInclusive"> (tax incl.)</span></div>
              <!-- Task: "on the receipt which was returned, I should mark it as returned" — plain text,
                   not color, since this is a monochrome-printable receipt. -->
              <div *ngIf="returnedQuantities[l.id]" style="font-weight:700; font-size:0.8rem;">
                [RETURNED {{ returnedQuantities[l.id] }} of {{ l.quantity }}]
              </div>
            </td>
            <!-- Fourier's own request: "Add tax code after total order_line" — l.taxCode is the
                 EFRIS tax code snapshotted onto this line (e.g. "B"), printed right after the line
                 total; simply omitted for a tax-exempt/no-tax-code line rather than showing blank
                 parentheses. -->
            <td style="text-align:right; padding:4px 0; vertical-align:top;">
              {{ l.lineTotal | number: '1.2-2' }}<span *ngIf="l.taxCode" style="color:#666; font-size:0.8rem;"> ({{ l.taxCode }})</span>
            </td>
          </tr>
        </tbody>
      </table>
      <hr />
      <table style="width:100%; font-size:0.9rem;">
        <tbody>
          <tr>
            <td>Subtotal</td>
            <td style="text-align:right;">{{ o.subtotal | number: '1.2-2' }}</td>
          </tr>
          <tr>
            <td>Tax</td>
            <td style="text-align:right;">{{ o.taxTotal | number: '1.2-2' }}</td>
          </tr>
          <tr style="font-weight:700;">
            <td>Total</td>
            <td style="text-align:right;">{{ o.total | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <hr />
      <p style="margin:0 0 4px; font-weight:600;">Payment</p>
      <div *ngFor="let p of o.payments" style="display:flex; justify-content:space-between; font-size:0.9rem;">
        <span>{{ p.method }}</span>
        <span>{{ p.amount | number: '1.2-2' }}</span>
      </div>

      <!-- EFRIS (Rwanda RRA e-invoicing) fiscal receipt data — Fourier's own layout spec, printed
           right after Payment. sdcDate/cisDate each carry a combined "date time" string (see
           EfrisSubmissionResult's backend javadoc for how they're parsed out of EFRIS's raw
           response), split here into separate DATE/TIME labels for the printout. Printed (not
           no-print) since these are real fiscal-receipt fields meant for the customer's own
           copy — the whole block simply doesn't render for any sale that hasn't actually been
           submitted to EFRIS (sdcId null). -->
      <div *ngIf="o.sdcId" style="margin-top:10px; font-size:0.8rem;">
        <hr />
        <p style="text-align:center; font-weight:700; margin:0 0 6px;">SDC INFORMATION</p>
        <div *ngIf="o.sdcDate && dateTimeParts(o.sdcDate) as sdcDT" style="display:flex; justify-content:space-between;">
          <span>SDC DATE:{{ sdcDT.date }}</span>
          <span>TIME:{{ sdcDT.time }}</span>
        </div>
        <div *ngIf="o.sdcReceiptNumber" style="display:flex; justify-content:space-between;">
          <span>SDC RECEIPT NUMBER:</span>
          <span>{{ o.sdcReceiptNumber }}</span>
        </div>
        <div style="display:flex; justify-content:space-between;">
          <span>SDC ID:</span>
          <span>{{ o.sdcId }}</span>
        </div>
        <div *ngIf="o.internalData" style="text-align:center; margin-top:6px;">
          <div>Internal Data:</div>
          <div style="word-break:break-all;">{{ o.internalData }}</div>
        </div>
        <div *ngIf="o.signatureData" style="text-align:center; margin-top:6px;">
          <div>Receipt Signature:</div>
          <div style="word-break:break-all;">{{ o.signatureData }}</div>
        </div>
        <hr *ngIf="o.cisNumber || o.cisDate || o.mrcData" />
        <div *ngIf="o.cisNumber" style="display:flex; justify-content:space-between;">
          <span>CIS RECEIPT NUMBER:</span>
          <span>{{ o.cisNumber }}</span>
        </div>
        <div *ngIf="o.cisDate && dateTimeParts(o.cisDate) as cisDT" style="display:flex; justify-content:space-between;">
          <span>CIS DATE:{{ cisDT.date }}</span>
          <span>TIME:{{ cisDT.time }}</span>
        </div>
        <div *ngIf="o.mrcData" style="display:flex; justify-content:space-between;">
          <span>MRC:</span>
          <span>{{ o.mrcData }}</span>
        </div>
        <!-- Fourier's own request: "add QR CODE on the printout of receipt" — encodes the same
             input_data format EFRIS verification apps expect (see generateQrCode's doc comment for
             the exact field spec). Only ever set once data-URL generation for this order finishes,
             so it simply doesn't render (rather than showing a broken image) if that's still in
             flight or failed. -->
        <div *ngIf="qrCodeDataUrl" style="text-align:center; margin-top:8px;">
          <img [src]="qrCodeDataUrl" alt="EFRIS QR code" style="width:120px; height:120px;" />
        </div>
      </div>

      <p style="text-align:center; margin-top:20px; color:#666; font-size:0.85rem;">Thank you!</p>

      <div class="no-print" *ngIf="emailError || emailSent" style="margin-top:10px;">
        <p *ngIf="emailError" style="color:#b00020; font-size:0.85rem;">{{ emailError }}</p>
        <p *ngIf="emailSent" style="color:#0a7a0a; font-size:0.85rem;">Receipt emailed to {{ emailedTo }}.</p>
      </div>
      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <!-- Fourier: "sending ... receipts ... to the customer's email" — see OrderService.emailReceipt's javadoc. -->
        <button (click)="emailReceipt()" [disabled]="emailingReceipt" style="flex:1; padding:10px; cursor:pointer;">
          {{ emailingReceipt ? 'Sending…' : 'Email receipt' }}
        </button>
        <a [routerLink]="['/pos', o.sessionId]" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px;">
          Back to terminal
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
export class ReceiptComponent implements OnInit, OnDestroy {
  order: Order | null = null;
  /** Task: "Add Business details + logo on the header of receipt printout". Left null on error/unset — see template's graceful degradation. */
  settings: StoreSettings | null = null;
  /** The sale's branch header details; null for a sale with no branch (older) or if the lookup fails — the business-wide header prints alone then. */
  branchInfo: BranchReceiptInfo | null = null;
  /**
   * Task: "on the receipt which was returned, I should mark it as returned" — orderLineId -> total
   * quantity returned against it. Fetched defensively alongside settings: an error here should
   * never block printing the receipt itself, so a missing/failed fetch just means no line gets
   * marked, same as an order with nothing returned against it.
   */
  returnedQuantities: Record<number, number> = {};

  /**
   * Fourier's own request: "if the sale order did not pass through with EFRIS (when there is an
   * error) add a button to retry if the error is fixed" — shown right next to the existing
   * failure warning above (o.efrisError), so whoever notices the failure can fix the underlying
   * cause (e.g. correct the terminal's EFRIS URL in Configure) and try again without needing a
   * developer to trigger a resubmission by hand. `retryingEfris` disables the button while the
   * request is in flight so a slow/hung EFRIS endpoint can't be double-submitted by an impatient
   * click; `retryError` is only for a failure of the retry call *itself* (network error reaching
   * this app's own backend) — a normal "EFRIS rejected it again" outcome instead lands back in
   * `order.efrisError` and re-renders the same warning, no separate message needed.
   */
  retryingEfris = false;
  retryError: string | null = null;

  /** Fourier: "sending ... receipts ... to the customer's email" — see the emailReceipt() method below. */
  emailingReceipt = false;
  emailSent = false;
  emailError: string | null = null;
  emailedTo = '';

  /**
   * Fourier's own request: "add QR CODE on the printout of receipt" — a data: URL PNG generated
   * client-side by `generateQrCode`, only once the order's fiscal fields are known. Null (and the
   * template's `*ngIf` just doesn't render anything) until then, or for any sale never submitted
   * to EFRIS at all.
   */
  qrCodeDataUrl: string | null = null;

  /**
   * Task: "On EFRIS setting add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the
   * printout" — fetched by `loadEfrisLogos` once the order's `registerId` is known. Null (and the
   * template's letterhead row just doesn't render) until that GET resolves, on any error, or for
   * an order with no register at all — never blocks printing the rest of the receipt.
   */
  efrisLogos: EfrisLogos | null = null;

  /** Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — Chad's own copy of {@link efrisLogos}, fetched the same way and at the same time. */
  efrisChadLogos: EfrisChadLogos | null = null;

  /**
   * The letterhead's left-hand image, whichever of Rwanda's RRA logo or Chad's CRA logo this
   * terminal actually has configured — "One Terminal can have only one EFRIS mapped with" (an
   * earlier task) guarantees at most one of {@link efrisLogos}/{@link efrisChadLogos} is ever
   * non-empty for a given terminal, so this is never a real conflict, just "whichever one exists."
   */
  get authorityLogoDataUrl(): string | null {
    return this.efrisLogos?.rraLogoDataUrl ?? this.efrisChadLogos?.craLogoDataUrl ?? null;
  }

  /** The letterhead's right-hand EFRIS logo, merged the same way as {@link authorityLogoDataUrl}. */
  get printEfrisLogoDataUrl(): string | null {
    return this.efrisLogos?.efrisLogoDataUrl ?? this.efrisChadLogos?.efrisLogoDataUrl ?? null;
  }

  /**
   * Task: "when I press Complete sale it should automatically open the kiosk print page ... and
   * once I leave that page take me back to the terminal to make another order." Set only when
   * checkout.component.ts navigates here with `?autoPrint=1` right after a sale — a reprint
   * opened from the Receipts list has no such flag and behaves exactly as before (manual Print
   * button, manual "Back to terminal" link, no forced navigation).
   */
  private autoPrint = false;
  private readonly afterPrintHandler = () => this.returnToTerminal();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private orderApi: OrderApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private branchApi: BranchApiService,
    private returnsApi: ReturnsApiService,
    private efrisApi: EfrisApiService,
    private efrisChadApi: EfrisChadApiService
  ) {}

  ngOnInit(): void {
    const orderId = Number(this.route.snapshot.paramMap.get('orderId'));
    this.autoPrint = this.route.snapshot.queryParamMap.get('autoPrint') === '1';
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.settings = s),
      error: () => (this.settings = null), // header just falls back to plain "Receipt" — never blocks printing
    });
    this.returnsApi.returnedQuantitiesForOrder(orderId).subscribe({
      next: (qtys) => (this.returnedQuantities = qtys),
      error: () => (this.returnedQuantities = {}), // no lines get marked — never blocks printing
    });
    this.orderApi.getOrder(orderId).subscribe((o) => {
      this.order = o;
      this.generateQrCode(o);
      this.loadEfrisLogos(o);
      const startAutoPrint = () => {
        if (this.autoPrint) {
          // The browser's print dialog is where a printer actually gets picked ("the kiosk print
          // page") — `afterprint` fires once it's dismissed either way (printed or cancelled),
          // which is what sends the cashier back to the terminal to start the next order.
          window.addEventListener('afterprint', this.afterPrintHandler);
          setTimeout(() => window.print(), 150);
        }
      };
      if (o.branchId != null) {
        // Auto-print waits for the branch header so it is on the printed receipt; a failed lookup just prints without it.
        this.branchApi.receiptInfo(o.branchId).subscribe({
          next: (b) => {
            this.branchInfo = b;
            startAutoPrint();
          },
          error: () => {
            this.branchInfo = null;
            startAutoPrint();
          },
        });
      } else {
        startAutoPrint();
      }
    });
  }

  ngOnDestroy(): void {
    window.removeEventListener('afterprint', this.afterPrintHandler);
  }

  print(): void {
    window.print();
  }

  /**
   * Fourier's own printout spec: `sdcDate`/`cisDate` each carry a combined "date time" string
   * (e.g. "16/09/2026 07:38:15" — see `EfrisSubmissionResult`'s backend javadoc for how they're
   * parsed out of EFRIS's raw response), and the printed SDC/CIS blocks show DATE and TIME as
   * two separate labels on one line. Splits on the first space; a value with no space just puts
   * everything in `date` and leaves `time` blank, so a differently-shaped response still prints
   * something sensible instead of breaking the layout.
   */
  dateTimeParts(raw: string | null): { date: string; time: string } {
    if (!raw) return { date: '', time: '' };
    const spaceIndex = raw.indexOf(' ');
    if (spaceIndex === -1) return { date: raw, time: '' };
    return { date: raw.slice(0, spaceIndex), time: raw.slice(spaceIndex + 1) };
  }

  /**
   * Fourier's own exact QR payload spec: `input_data=sdc_date#sdc_id#sdc_receipt_number#+internal_data#+signature_data`
   * — five of the sale's fiscal fields joined with `#`, with the last two additionally prefixed by
   * a literal `+` (EFRIS verification apps split on `#` and use the `+` to recognize the
   * internal-data/signature-data segments). Skipped entirely for a sale with no `sdcId` yet (never
   * submitted to EFRIS, or the submission failed) — same gating as the SDC INFORMATION block
   * itself, so there's nothing to encode. Missing individual sub-fields (a short/garbled EFRIS
   * response) degrade to empty segments rather than the literal string "null", consistent with how
   * every other field in this printout already handles that. Generates a PNG data: URL client-side
   * (no backend round trip, no extra dependency on `SaleOrder` gaining its own QR column) and
   * fails silently into `qrCodeDataUrl = null` if the `qrcode` library ever rejects — a receipt
   * missing its QR code is far better than one that fails to render at all.
   */
  private generateQrCode(o: Order): void {
    this.qrCodeDataUrl = null;
    if (!o.sdcId) return;
    const inputData = `${o.sdcDate ?? ''}#${o.sdcId ?? ''}#${o.sdcReceiptNumber ?? ''}#+${o.internalData ?? ''}#+${o.signatureData ?? ''}`;
    QRCode.toDataURL(inputData, { margin: 1, width: 240 })
      .then((dataUrl) => (this.qrCodeDataUrl = dataUrl))
      .catch(() => (this.qrCodeDataUrl = null));
  }

  /**
   * Task: "On EFRIS setting add two logo (URA LOGO, EFRIS LOGO) ... added left and right on the
   * printout" — fetched from the dedicated `EfrisApiService.getLogosForRegister` endpoint rather
   * than being on `Order` itself: the logos live on that terminal's `EfrisSetting` row (per
   * terminal, same as the URL/db/uid/password already there), not on the sale, and any logged-in
   * cashier viewing this receipt is allowed to see them even though the rest of that row is
   * admin-only (see EfrisSettingController.getLogos's javadoc). Skipped for an order with no
   * `registerId` at all; any fetch error just leaves `efrisLogos` null, same "never blocks
   * printing" convention as every other best-effort fetch in this component. Called once when the
   * order first loads — a terminal's own logos don't change mid-print, so there's no need to
   * re-fetch after a QR/EFRIS retry the way `generateQrCode` does.
   *
   * <p>Task 3: "Add also Chad Revenue Authority logo and EFRIS logo on EFRIS Chad setting" — also
   * fetches Chad's own logos via `EfrisChadApiService.getLogosForRegister` the same way, in
   * parallel. There's no need to know up front which integration (if any) this terminal is mapped
   * to: "One Terminal can have only one EFRIS mapped with" already guarantees at most one of the
   * two GETs ever comes back with a real row, so firing both and letting {@link
   * authorityLogoDataUrl}/{@link printEfrisLogoDataUrl} merge the results is simpler than looking
   * up the mapping first.</p>
   */
  private loadEfrisLogos(o: Order): void {
    this.efrisLogos = null;
    this.efrisChadLogos = null;
    if (!o.registerId) return;
    this.efrisApi.getLogosForRegister(o.registerId).subscribe({
      next: (logos) => (this.efrisLogos = logos),
      error: () => (this.efrisLogos = null),
    });
    this.efrisChadApi.getLogosForRegister(o.registerId).subscribe({
      next: (logos) => (this.efrisChadLogos = logos),
      error: () => (this.efrisChadLogos = null),
    });
  }

  /** See the `retryingEfris`/`retryError` field javadoc above for what each state means. */
  retryEfris(): void {
    if (!this.order || this.retryingEfris) return;
    this.retryingEfris = true;
    this.retryError = null;
    this.orderApi.retryEfris(this.order.id).subscribe({
      next: (o) => {
        this.order = o;
        this.generateQrCode(o);
        this.retryingEfris = false;
      },
      error: (err) => {
        this.retryError = err?.error?.message ?? 'Failed to retry EFRIS submission';
        this.retryingEfris = false;
      },
    });
  }

  emailReceipt(): void {
    if (!this.order) return;
    const entered = prompt('Send this receipt to which email address?', this.order.customerEmail ?? '');
    if (entered === null) return; // cancelled
    const email = entered.trim() || null;
    if (!email) {
      this.emailError = 'Enter an email address to send to';
      return;
    }
    this.emailError = null;
    this.emailSent = false;
    this.emailingReceipt = true;
    this.orderApi.emailReceipt(this.order.id, email).subscribe({
      next: () => {
        this.emailingReceipt = false;
        this.emailSent = true;
        this.emailedTo = email;
      },
      error: (err) => {
        this.emailingReceipt = false;
        this.emailError = err?.error?.message ?? 'Failed to email receipt';
      },
    });
  }

  private returnToTerminal(): void {
    window.removeEventListener('afterprint', this.afterPrintHandler);
    if (this.order?.sessionId) {
      this.router.navigate(['/pos', this.order.sessionId]);
    }
  }
}
