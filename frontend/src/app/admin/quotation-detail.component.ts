import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { QuotationApiService } from '../core/api/quotation-api.service';
import { openPdfForPrint } from '../core/download-file';
import { Branch, Quotation } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf } from '../core/branch-util';

/**
 * Admin > Sales > Quotations > detail — where a DRAFT is sent, a SENT quote is accepted/
 * rejected/marked expired, and where an ACCEPTED quote is converted into a real Sales Order
 * (Fourier: "Add Quotation ... without touching warehousing"). Mirrors
 * SalesOrderDetailComponent's send/cancel-equivalent action-button split.
 *
 * <p>Fourier: "Add print A4 on Quotation and Label it as proforma" — a "Print" button, same
 * convention as every other document screen's own (Invoice/Sale Order/Bill): fetches the real A4
 * PDF and opens it in a new tab for the browser's own PDF viewer to print. Available at any
 * status, same as the existing "Email" button. The printed page itself is headed "Proforma
 * Invoice" rather than "Quotation" — see the backend DocumentPdfService.quotationPdf's javadoc —
 * but nothing on this screen or elsewhere in the app changes what this document is called.
 */
@Component({
  selector: 'app-quotation-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="q as quote">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ quote.quoteNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(quote.status)">{{ quote.status.replace('_', ' ') }}</span>
          </div>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" *ngIf="quote.status === 'DRAFT'" [routerLink]="['/admin/quotations', quote.id, 'edit']">Edit</a>
            <button class="btn btn-primary" *ngIf="quote.status === 'DRAFT'" (click)="send()">Send to customer</button>
            <button class="btn btn-primary" *ngIf="quote.status === 'SENT'" (click)="accept()">Mark accepted</button>
            <button class="btn btn-outline-secondary" *ngIf="quote.status === 'SENT'" (click)="markExpired()">Mark expired</button>
            <button class="btn btn-outline-danger" *ngIf="quote.status === 'DRAFT' || quote.status === 'SENT'" (click)="reject()">Reject</button>
            <button class="btn btn-primary" *ngIf="quote.status === 'ACCEPTED'" (click)="convert()">Convert to sale order</button>
            <a class="btn btn-outline-primary" *ngIf="quote.convertedSalesOrderId" [routerLink]="['/admin/sales-orders', quote.convertedSalesOrderId]">View sale order</a>
            <!-- Fourier: "sending ... quotations ... to the customer's email" — available at any status; see QuotationService.emailToCustomer's javadoc. -->
            <button class="btn btn-outline-primary" (click)="emailToCustomer()" [disabled]="emailing">
              {{ emailing ? 'Sending…' : 'Email' }}
            </button>
            <!-- Fourier: "Add print A4 on Quotation and Label it as proforma" — available at any status, same as Email above. -->
            <button class="btn btn-outline-primary" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
          </div>
        </div>
        <div class="alert alert-success mb-3" *ngIf="emailSent">Quotation emailed to {{ emailedTo }}.</div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Customer</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.customerName }}</div>
          </div>
          <div class="stat-tile" *ngIf="branches.length > 1">
            <div class="stat-label">Branch</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ branchName(quote.branchId) }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Quote date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.quoteDate | date: 'medium' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Valid until</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.validUntil || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Subtotal</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.subtotalAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Tax</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.taxAmount | number: '1.2-2' }}</div>
          </div>
          <!-- Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — only shown once this document actually has one, same convention as the printed PDF's own addSubtotalBreakdown. -->
          <div class="stat-tile" *ngIf="quote.discountAmount">
            <div class="stat-label">Discount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.discountAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Total amount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ quote.totalAmount | number: '1.2-2' }}</div>
          </div>
        </div>
        <p class="text-muted mb-3" *ngIf="quote.notes">{{ quote.notes }}</p>
        <p class="text-muted mb-3" *ngIf="quote.convertedSalesOrderNumber">
          Converted into sale order {{ quote.convertedSalesOrderNumber }}.
        </p>

        <h4 class="mb-2">Lines</h4>
        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Product</th>
              <th class="text-end">Quantity</th>
              <th class="text-end">Unit price</th>
              <!-- Fourier: "Same for discount ... it will be per line" — only shown once this document actually has a discount on at least one line. -->
              <th class="text-end" *ngIf="quote.discountAmount">Discount</th>
              <!-- Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill, Refund)" -->
              <th class="text-end">Tax</th>
              <th class="text-end">Line total</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of quote.lines">
              <td>{{ l.productName }}</td>
              <td class="text-end">{{ l.quantity }}</td>
              <td class="text-end">{{ l.unitPrice | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="quote.discountAmount">{{ l.discountAmount | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.taxTotal | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.lineTotal | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class QuotationDetailComponent implements OnInit {
  /** Branch shown on the detail only with more than one active branch. */
  branches: Branch[] = [];

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  q: Quotation | null = null;
  error: string | null = null;
  emailing = false;
  emailSent = false;
  emailedTo = '';
  printing = false;

  constructor(
    private quotationApi: QuotationApiService,
    private route: ActivatedRoute,
    private router: Router,
    private branchApi: BranchApiService
  ) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.quotationApi.getById(id).subscribe({
      next: (q) => (this.q = q),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load quotation'),
    });
  }

  send(): void {
    if (!this.q) return;
    this.error = null;
    this.quotationApi.send(this.q.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to send quotation'),
    });
  }

  accept(): void {
    if (!this.q) return;
    this.error = null;
    this.quotationApi.accept(this.q.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to accept quotation'),
    });
  }

  reject(): void {
    if (!this.q) return;
    if (!confirm(`Reject quotation ${this.q.quoteNumber}?`)) return;
    this.error = null;
    this.quotationApi.reject(this.q.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to reject quotation'),
    });
  }

  markExpired(): void {
    if (!this.q) return;
    this.error = null;
    this.quotationApi.expire(this.q.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to mark quotation expired'),
    });
  }

  convert(): void {
    if (!this.q) return;
    this.error = null;
    this.quotationApi.convert(this.q.id).subscribe({
      next: (so) => this.router.navigate(['/admin/sales-orders', so.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to convert quotation'),
    });
  }

  emailToCustomer(): void {
    if (!this.q) return;
    let email: string | null = this.q.customerEmail ?? null;
    const entered = prompt('Send this quotation to which email address?', email ?? '');
    if (entered === null) return; // cancelled
    email = entered.trim() || null;
    if (!email) {
      this.error = 'Enter an email address to send to';
      return;
    }
    this.error = null;
    this.emailSent = false;
    this.emailing = true;
    this.quotationApi.email(this.q.id, email).subscribe({
      next: () => {
        this.emailing = false;
        this.emailSent = true;
        this.emailedTo = email!;
      },
      error: (err) => {
        this.emailing = false;
        this.error = err?.error?.message ?? 'Failed to email quotation';
      },
    });
  }

  /** Fourier: "Add print A4 on Quotation and Label it as proforma" — fetches the real A4 PDF and opens it in a new tab for the browser's own PDF viewer to print. */
  print(): void {
    if (!this.q) return;
    this.error = null;
    this.printing = true;
    this.quotationApi.pdf(this.q.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to generate the quotation PDF';
      },
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'SENT':
        return 'badge-secondary';
      case 'ACCEPTED':
        return 'badge-success';
      case 'REJECTED':
        return 'badge-danger';
      case 'EXPIRED':
        return 'badge-warning';
      case 'CONVERTED':
        return 'badge-success';
      default:
        return 'badge-secondary';
    }
  }
}
