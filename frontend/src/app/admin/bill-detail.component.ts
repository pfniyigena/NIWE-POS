import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { BillApiService } from '../core/api/bill-api.service';
import { openPdfForPrint } from '../core/download-file';
import { Bill, Branch } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf } from '../core/branch-util';

/**
 * Admin > Purchases > Bills > detail — `/admin/bills/:id`, reached from the bill list or from a
 * Purchase Order's own "View bill" link. Fourier: "The Purchase order should flow same workflow as
 * Sale order (with Bills and Refunds equivalent with Invoices and Credit note)" — same shape as
 * InvoiceDetailComponent, scoped to what a Bill actually supports: unlike Invoice there is no
 * standalone bill to edit (every bill is generated from a Purchase Order, see the backend Bill's
 * class javadoc), and no "cancel & credit" path — a bill that's already been paid is cancelled via
 * a Purchase Return against its own Purchase Order instead, so "Void bill" simply stays hidden
 * once anything has been paid (see the backend BillService.voidBill's javadoc).
 *
 * <p>This is also where a payment is recorded (a dedicated per-bill screen, same "Let record
 * payment per invoice not per customer" convention Invoice already follows) and where it's voided,
 * stamping a refund number — see the backend BillService.voidBill's javadoc for what voiding does.
 */
@Component({
  selector: 'app-bill-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="bill as b">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ b.billNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(b.status)">{{ b.status }}</span>
          </div>
          <div class="flex gap-2">
            <!-- DRAFT — not yet a real document: issue it, or discard it. -->
            <button class="btn btn-primary" *ngIf="b.status === 'DRAFT'" (click)="issue()">Issue bill</button>
            <button class="btn btn-outline-danger" *ngIf="b.status === 'DRAFT'" (click)="deleteDraft()">Delete draft</button>

            <button class="btn btn-outline-primary" *ngIf="b.status !== 'DRAFT'" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
            <a class="btn btn-outline-primary" *ngIf="b.status === 'ISSUED' && b.amountOutstanding > 0" [routerLink]="['/admin/bills', b.id, 'record-payment']">
              Record payment
            </a>
            <!-- Fourier: "VOID ... Only allowed when there is NO payment" mirrored here — hidden once anything has been paid; a Purchase Return against the order is the path for that case. -->
            <button class="btn btn-outline-danger" *ngIf="b.status === 'ISSUED' && b.amountPaid <= 0" (click)="showVoidForm = !showVoidForm">Void bill</button>

            <a class="btn btn-outline-primary" *ngIf="b.purchaseOrderId" [routerLink]="['/admin/purchase-orders', b.purchaseOrderId]">Back to purchase order</a>
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

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Supplier</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.supplierName ?? '—' }}</div>
          </div>
          <div class="stat-tile" *ngIf="branches.length > 1">
            <div class="stat-label">Branch</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ branchName(b.branchId) }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Purchase order</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.purchaseOrderNumber ?? '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Bill date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.billDate ? (b.billDate | date: 'medium') : '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Subtotal</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.subtotalAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Tax</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.taxAmount | number: '1.2-2' }}</div>
          </div>
          <!-- Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — only shown once this document actually has one, same convention as the printed PDF's own addSubtotalBreakdown. -->
          <div class="stat-tile" *ngIf="b.discountAmount">
            <div class="stat-label">Discount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.discountAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Total amount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.totalAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile" *ngIf="b.status !== 'DRAFT'">
            <div class="stat-label">Paid</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.amountPaid | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile" *ngIf="b.status !== 'DRAFT'">
            <div class="stat-label">Outstanding</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ b.amountOutstanding | number: '1.2-2' }}</div>
          </div>
        </div>
        <p class="text-muted mb-3" *ngIf="b.notes">{{ b.notes }}</p>
        <p class="text-muted mb-3" *ngIf="b.voidReason">Voided — {{ b.voidReason }}</p>

        <h4 class="mb-2">Lines</h4>
        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Product</th>
              <th class="text-end">Qty</th>
              <th class="text-end">Unit cost</th>
              <!-- Fourier: "Same for discount ... it will be per line" — only shown once this document actually has a discount on at least one line. -->
              <th class="text-end" *ngIf="b.discountAmount">Discount</th>
              <!-- Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill, Refund)" -->
              <th class="text-end">Tax</th>
              <th class="text-end">Total</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of b.lines">
              <td>{{ l.productName }}</td>
              <td class="text-end">{{ l.quantity }}</td>
              <td class="text-end">{{ l.unitCost | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="b.discountAmount">{{ l.discountAmount | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.taxTotal | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.lineTotal | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="b.lines.length === 0">
              <td [attr.colspan]="b.discountAmount ? 6 : 5" class="text-muted">No lines on this bill.</td>
            </tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class BillDetailComponent implements OnInit {
  /** Branch shown on the detail only with more than one active branch. */
  branches: Branch[] = [];

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  bill: Bill | null = null;
  error: string | null = null;
  showVoidForm = false;
  voidReason = '';
  printing = false;

  constructor(private route: ActivatedRoute, private router: Router, private billApi: BillApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(id);
  }

  load(id: number): void {
    this.billApi.getById(id).subscribe({
      next: (b) => (this.bill = b),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load bill'),
    });
  }

  print(): void {
    if (!this.bill) return;
    this.error = null;
    this.printing = true;
    this.billApi.pdf(this.bill.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to generate the bill PDF';
      },
    });
  }

  confirmVoid(): void {
    if (!this.bill) return;
    this.error = null;
    this.billApi.voidBill(this.bill.id, { reason: this.voidReason || null }).subscribe({
      next: (b) => {
        this.bill = b;
        this.showVoidForm = false;
        this.voidReason = '';
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to void bill'),
    });
  }

  issue(): void {
    if (!this.bill) return;
    this.error = null;
    this.billApi.issue(this.bill.id).subscribe({
      next: (b) => (this.bill = b),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to issue bill'),
    });
  }

  deleteDraft(): void {
    if (!this.bill) return;
    if (!confirm(`Delete draft bill ${this.bill.billNumber}? This cannot be undone.`)) return;
    this.error = null;
    this.billApi.deleteDraft(this.bill.id).subscribe({
      next: () => this.router.navigate(['/admin/bills']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete draft'),
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
