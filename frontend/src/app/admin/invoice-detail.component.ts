import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { InvoiceApiService } from '../core/api/invoice-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { openPdfForPrint } from '../core/download-file';
import { Branch, Invoice, PaymentDisposition, PaymentMethodOption } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf } from '../core/branch-util';
import { ModuleApiService } from '../core/api/module-api.service';
import { InstallmentApiService, InstallmentPlanSummary } from '../core/api/installment-api.service';

/**
 * Admin > Sales > Invoices > detail — `/admin/invoices/:id`, reached from the invoice list or
 * from a Sales Order's own "View invoice" link. Fourier: "View Invoice Button contain should look
 * same as the order view" — this used to be its own light, letterhead-styled, print-shaped
 * "receipt" page (480px wide, Courier New, outside the admin shell), left over from when this
 * page itself was the thing you printed. Now that {@link #print} hands that job to a real,
 * separately-built A4 PDF (see the backend {@code InvoiceService.pdf}'s javadoc), this screen has
 * no more reason to look different from {@code SalesOrderDetailComponent} — same {@code app-shell}
 * wide admin layout, same header-badge-plus-action-buttons row, same stat-grid and table-sm
 * conventions, so an admin moving between a Sales Order and its Invoice sees one consistent shape.
 *
 * <p>A DRAFT invoice (standalone only — see InvoiceStatus's backend javadoc) isn't a real
 * document yet, so it skips the print/email/void actions entirely in favor of Edit/Issue/Delete;
 * once ISSUED, this is also where a payment is recorded (Fourier's own request — the "Record
 * payment" link below replaces what used to live on the Sales Order itself) and, same as before,
 * where it's voided — see the backend InvoiceService.voidInvoice's javadoc for what voiding does
 * and doesn't reverse.
 *
 * <p>Fourier's own later follow-up: "Let record payment per invoice not per customer (all
 * outstanding invoices)" — "Record payment" now opens a dedicated per-invoice screen
 * (InvoiceRecordPaymentComponent) rather than the Customer-wide allocation-table one; the whole
 * amount always applies to this one invoice.
 *
 * <p>Fourier, on being refused a void because of exactly this: "VOID INVOICE → Only allowed when
 * there is NO payment. CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment →
 * Refund OR → Customer Credit." Once anything has been paid, "Void invoice" is replaced by
 * "Cancel & credit invoice" — same end state (VOID, with a credit note) but it also requires
 * picking how the already-collected amount is handled; see the backend
 * InvoiceService.cancelPaidInvoice's javadoc.
 */
@Component({
  selector: 'app-invoice-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="emailSent">Invoice emailed to {{ emailedTo }}.</div>
      <ng-container *ngIf="inv as o">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ o.invoiceNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(o.status)">{{ o.status }}</span>
          </div>
          <div class="flex gap-2">
            <!-- DRAFT — not yet a real document: edit it, issue it, or discard it. Fourier:
                 "instead of making invoice DRAFT make then ISSUED and hit accounting" — a
                 subscription-generated invoice is now created already ISSUED (see
                 InvoiceService.generateForSubscription's own javadoc), so it never actually
                 reaches this DRAFT branch; the !o.subscriptionId guard is kept only as a
                 defensive leftover from when "Bill now" used to create a DRAFT invoice — a
                 subscription line still has no catalog product behind it (see
                 InvoiceLine.product's own javadoc), so the ordinary Edit form (built around
                 picking a product per line) still wouldn't suit it if one ever did. -->
            <a class="btn btn-outline-primary" *ngIf="o.status === 'DRAFT' && !o.subscriptionId" [routerLink]="['/admin/invoices', o.id, 'edit']">Edit</a>
            <button class="btn btn-primary" *ngIf="o.status === 'DRAFT'" (click)="issue()">Issue invoice</button>
            <button class="btn btn-outline-danger" *ngIf="o.status === 'DRAFT'" (click)="deleteDraft()">Delete draft</button>

            <!-- Fourier: "add print (PDF with A4)". -->
            <button class="btn btn-outline-primary" *ngIf="o.status !== 'DRAFT'" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
            <!-- Fourier: "sending ... invoices ... to the customer's email" — see InvoiceService.emailToCustomer's javadoc. -->
            <button class="btn btn-outline-primary" *ngIf="o.status !== 'DRAFT'" [disabled]="emailing" (click)="emailToCustomer()">
              {{ emailing ? 'Sending…' : 'Email to customer' }}
            </button>
            <!-- Fourier: "create invoice related to the Sale Order and record payment on invoice" — this replaces the "Record payment" link that used to live on the Sales Order itself. Fourier's own later follow-up: "Let record payment per invoice not per customer (all outstanding invoices)" — now a dedicated per-invoice screen, not the customer-wide allocation-table one. -->
            <a class="btn btn-outline-primary" *ngIf="o.status === 'ISSUED' && o.customerId && o.amountOutstanding > 0" [routerLink]="['/admin/invoices', o.id, 'record-payment']">
              Record payment
            </a>
            <a class="btn btn-outline-primary" *ngIf="modules.on('INSTALLMENTS') && o.status === 'ISSUED' && o.customerId && o.amountOutstanding > 0 && !hasActivePlan()" routerLink="/admin/installments/new" [queryParams]="{ invoiceId: o.id }">
              Pay in instalments
            </a>
            <a class="btn btn-outline-secondary" *ngFor="let p of plans" [routerLink]="['/admin/installments', p.id]">Plan {{ p.planNumber }} ({{ p.status }})</a>
            <!-- Fourier: "VOID INVOICE → Only allowed when there is NO payment" — hidden (not just blocked server-side) once anything has been paid, in favor of the Cancel & credit action below. -->
            <button class="btn btn-outline-danger" *ngIf="o.status === 'ISSUED' && !hasPayment(o)" (click)="showVoidForm = !showVoidForm">Void invoice</button>
            <!-- Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit" — the separate path for an invoice Void invoice would otherwise refuse. -->
            <button class="btn btn-outline-danger" *ngIf="o.status === 'ISSUED' && hasPayment(o)" (click)="toggleCancelPaidForm()">Cancel &amp; credit invoice</button>

            <a class="btn btn-outline-primary" *ngIf="o.salesOrderId" [routerLink]="['/admin/sales-orders', o.salesOrderId]">Back to sale order</a>
          </div>
        </div>

        <div *ngIf="showVoidForm" class="form-group mb-3" style="max-width:400px;">
          <label>Void reason (optional)</label>
          <input class="form-control" placeholder="Reason" [(ngModel)]="voidReason" name="voidReason" />
          <div class="flex gap-2 mt-2">
            <button class="btn btn-outline-danger" (click)="confirmVoid()">Confirm void</button>
            <button class="btn btn-link" (click)="showVoidForm = false">Cancel</button>
          </div>
        </div>

        <!-- Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit" -->
        <div *ngIf="showCancelPaidForm" class="card mb-3" style="max-width:420px; padding:1rem;">
          <p class="text-muted" style="margin-top:0;">
            {{ o.amountPaid | number: '1.2-2' }} has already been paid on this invoice. Cancelling it creates a credit note — choose what happens to that amount.
          </p>
          <div class="form-group">
            <label class="form-label">Handle the payment</label>
            <div class="flex gap-3">
              <label class="flex align-center gap-1"><input type="radio" name="disposition" value="REFUND" [(ngModel)]="disposition" /> Refund</label>
              <label class="flex align-center gap-1"><input type="radio" name="disposition" value="CUSTOMER_CREDIT" [(ngModel)]="disposition" /> Customer credit</label>
            </div>
          </div>
          <div class="form-group" *ngIf="disposition === 'REFUND'">
            <label class="form-label">Refund method</label>
            <select class="form-select" [(ngModel)]="refundMethod">
              <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
            </select>
          </div>
          <div class="text-muted mb-2" *ngIf="disposition === 'CUSTOMER_CREDIT'" style="font-size:0.85rem;">
            {{ o.amountPaid | number: '1.2-2' }} will be added to {{ o.customerName }}'s customer credit balance instead of being paid back out now.
          </div>
          <div class="form-group">
            <label class="form-label">Reason (optional)</label>
            <input class="form-control" placeholder="Reason" [(ngModel)]="cancelPaidReason" />
          </div>
          <div class="flex gap-2 mt-2">
            <button class="btn btn-outline-danger" [disabled]="!canConfirmCancelPaid()" (click)="confirmCancelPaid()">Confirm cancel &amp; credit</button>
            <button class="btn btn-link" (click)="showCancelPaidForm = false">Cancel</button>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Customer</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.customerName ?? '—' }}</div>
          </div>
          <div class="stat-tile" *ngIf="branches.length > 1">
            <div class="stat-label">Branch</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ branchName(o.branchId) }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Sale order</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.salesOrderNumber ?? '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Invoice date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.invoiceDate ? (o.invoiceDate | date: 'medium') : '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Due date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.dueDate || '—' }}</div>
          </div>
          <!-- Fourier: "create an invoice from the last end date" — only a subscription-generated invoice ever has a billing period; see the backend Invoice.periodStart's own javadoc. -->
          <div class="stat-tile" *ngIf="o.periodStart">
            <div class="stat-label">Billing period</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.periodStart | date: 'mediumDate' }} – {{ o.periodEnd | date: 'mediumDate' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Subtotal</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.subtotalAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Tax</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.taxAmount | number: '1.2-2' }}</div>
          </div>
          <!-- Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — only shown once this document actually has one, same convention as the printed PDF's own addSubtotalBreakdown. -->
          <div class="stat-tile" *ngIf="o.discountAmount">
            <div class="stat-label">Discount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.discountAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Total amount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalAmount | number: '1.2-2' }}</div>
          </div>
          <!-- Fourier: "record payment on invoice" — only meaningful once this is a real, ISSUED document; a VOID invoice's amountPaid still shows (it isn't reversed just by voiding), amountOutstanding reads zero since it's no longer payable. -->
          <div class="stat-tile" *ngIf="o.status !== 'DRAFT'">
            <div class="stat-label">Paid</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.amountPaid | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.status !== 'DRAFT'">
            <div class="stat-label">Outstanding</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.amountOutstanding | number: '1.2-2' }}</div>
          </div>
        </div>
        <p class="text-muted mb-3" *ngIf="o.notes">{{ o.notes }}</p>
        <p class="text-muted mb-3" *ngIf="o.voidReason">Voided — {{ o.voidReason }}</p>
        <!-- Fourier: "CANCEL/CREDIT PAID INVOICE → Handle the payment → Refund OR → Customer Credit" -->
        <p class="text-muted mb-3" *ngIf="o.paymentDisposition === 'REFUND'">Cancelled — {{ o.amountPaid | number: '1.2-2' }} refunded via {{ o.refundMethod }}.</p>
        <p class="text-muted mb-3" *ngIf="o.paymentDisposition === 'CUSTOMER_CREDIT'">Cancelled — {{ o.amountPaid | number: '1.2-2' }} converted to customer credit.</p>

        <h4 class="mb-2">Lines</h4>
        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Product</th>
              <th class="text-end">Qty</th>
              <th class="text-end">Unit price</th>
              <!-- Fourier: "Same for discount ... it will be per line" — only shown once this document actually has a discount on at least one line. -->
              <th class="text-end" *ngIf="o.discountAmount">Discount</th>
              <!-- Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill, Refund)" -->
              <th class="text-end">Tax</th>
              <th class="text-end">Total</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of o.lines">
              <td>
                {{ l.productName }}
                <div *ngIf="l.description" class="text-muted" style="font-size:0.8rem;">{{ l.description }}</div>
              </td>
              <td class="text-end">{{ l.quantity }}</td>
              <td class="text-end">{{ l.unitPrice | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="o.discountAmount">{{ l.discountAmount | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.taxTotal | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.lineTotal | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="o.lines.length === 0">
              <td [attr.colspan]="o.discountAmount ? 6 : 5" class="text-muted">No lines on this invoice.</td>
            </tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class InvoiceDetailComponent implements OnInit {
  /** Branch shown on the detail only with more than one active branch. */
  branches: Branch[] = [];

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  inv: Invoice | null = null;
  error: string | null = null;
  showVoidForm = false;
  voidReason = '';
  emailing = false;
  emailSent = false;
  emailedTo = '';
  printing = false;

  // Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit"
  showCancelPaidForm = false;
  disposition: PaymentDisposition = 'REFUND';
  refundMethod = '';
  paymentMethods: PaymentMethodOption[] = [];
  cancelPaidReason = '';
  cancellingPaid = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private invoiceApi: InvoiceApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private branchApi: BranchApiService,
    public modules: ModuleApiService,
    private installmentApi: InstallmentApiService
  ) {}

  /** Installments module: this invoice's instalment plans (loaded once the invoice is). */
  plans: InstallmentPlanSummary[] = [];

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
    this.paymentMethodApi.listActive().subscribe((methods) => {
      this.paymentMethods = methods;
      if (methods.length > 0 && !this.refundMethod) {
        this.refundMethod = methods[0].code;
      }
    });
  }

  /** Fourier: "VOID INVOICE → Only allowed when there is NO payment" — drives which of the two cancel actions the template shows. */
  hasPayment(inv: Invoice): boolean {
    return inv.amountPaid > 0;
  }

  hasActivePlan(): boolean {
    return this.plans.some((p) => p.status === 'ACTIVE');
  }

  load(id: number): void {
    this.invoiceApi.getById(id).subscribe({
      next: (inv) => {
        this.inv = inv;
        this.modules.whenLoaded().subscribe(() => {
          if (this.modules.on('INSTALLMENTS')) this.installmentApi.forInvoice(inv.id).subscribe({ next: (p) => (this.plans = p), error: () => (this.plans = []) });
        });
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load invoice'),
    });
  }

  /** Fourier: "add print (PDF with A4)" — fetches the real A4 PDF and opens it in a new tab for the browser's own PDF viewer to print. */
  print(): void {
    if (!this.inv) return;
    this.error = null;
    this.printing = true;
    this.invoiceApi.pdf(this.inv.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to generate the invoice PDF';
      },
    });
  }

  confirmVoid(): void {
    if (!this.inv) return;
    this.error = null;
    this.invoiceApi.voidInvoice(this.inv.id, this.voidReason || null).subscribe({
      next: (inv) => {
        this.inv = inv;
        this.showVoidForm = false;
        this.voidReason = '';
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to void invoice'),
    });
  }

  /** Fourier: "CANCEL/CREDIT PAID INVOICE → Create Credit Note → Handle the payment → Refund OR → Customer Credit" */
  toggleCancelPaidForm(): void {
    this.showCancelPaidForm = !this.showCancelPaidForm;
  }

  canConfirmCancelPaid(): boolean {
    if (this.cancellingPaid) return false;
    if (this.disposition === 'REFUND') return !!this.refundMethod;
    return this.disposition === 'CUSTOMER_CREDIT';
  }

  confirmCancelPaid(): void {
    if (!this.inv || !this.canConfirmCancelPaid()) return;
    this.error = null;
    this.cancellingPaid = true;
    this.invoiceApi
      .cancelPaidInvoice(this.inv.id, {
        disposition: this.disposition,
        refundMethod: this.disposition === 'REFUND' ? this.refundMethod : null,
        reason: this.cancelPaidReason || null,
      })
      .subscribe({
        next: (inv) => {
          this.inv = inv;
          this.cancellingPaid = false;
          this.showCancelPaidForm = false;
          this.cancelPaidReason = '';
        },
        error: (err) => {
          this.cancellingPaid = false;
          this.error = err?.error?.message ?? 'Failed to cancel invoice';
        },
      });
  }

  issue(): void {
    if (!this.inv) return;
    this.error = null;
    this.invoiceApi.issue(this.inv.id).subscribe({
      next: (inv) => (this.inv = inv),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to issue invoice'),
    });
  }

  deleteDraft(): void {
    if (!this.inv) return;
    if (!confirm(`Delete draft invoice ${this.inv.invoiceNumber}? This cannot be undone.`)) return;
    this.error = null;
    this.invoiceApi.deleteDraft(this.inv.id).subscribe({
      next: () => this.router.navigate(['/admin/invoices']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete draft'),
    });
  }

  emailToCustomer(): void {
    if (!this.inv) return;
    const entered = prompt('Send this invoice to which email address?', this.inv.customerEmail ?? '');
    if (entered === null) return; // cancelled
    const email = entered.trim() || null;
    if (!email) {
      this.error = 'Enter an email address to send to';
      return;
    }
    this.error = null;
    this.emailSent = false;
    this.emailing = true;
    this.invoiceApi.email(this.inv.id, email).subscribe({
      next: () => {
        this.emailing = false;
        this.emailSent = true;
        this.emailedTo = email;
      },
      error: (err) => {
        this.emailing = false;
        this.error = err?.error?.message ?? 'Failed to email invoice';
      },
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'ISSUED':
        return 'badge-success';
      case 'VOID':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
