import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CustomerCreditApiService } from '../core/api/customer-credit-api.service';
import { CustomerPayment } from '../core/models';

/**
 * Store-wide customer payment history, across every customer — Task: "Add Sale order and
 * Customer payment... Let have Sale as Menu" (the new Sales sidenav group's "Customer payments"
 * link). Recording a payment for one specific customer still happens on RecordPaymentComponent,
 * reached from that customer's own row; this is a read-only overview of every payment recorded,
 * mirroring the new store-wide Supplier Payments list on the purchasing side.
 *
 * <p>Fourier's own follow-up: "On Customer payment list no POS payment for cash sale." This list
 * now also includes every POS sale's own checkout tender (a {@code source: 'POS_SALE'} row, no
 * receipt#/recorded-by since nothing was "recorded" — it was tendered at checkout), not just a
 * later credit settlement recorded through Record Payment ({@code source: 'SETTLEMENT'}) — the
 * "Type" column tells the two apart.
 *
 * <p>Fourier: "on the customer ... view button where we can have button to view the list of
 * invoices, payments etc" — arriving from a customer's own row via `?customerId=&customerName=`
 * (see CustomerListComponent's "Payments" link) narrows this to just that customer's own payment
 * history, reusing the backend's existing per-customer {@code
 * CustomerCreditApiService.paymentHistory} rather than fetching every customer's and filtering
 * client-side.
 */
@Component({
  selector: 'app-customer-payment-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? 'Payments for ' + customerName : 'Customer payments' }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/customer-payments" style="font-size:0.85rem;">Show every customer's payments</a>
        </div>
        <a class="btn btn-link" routerLink="/admin/customers">Back to customers</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Type</th>
            <th>Receipt #</th>
            <th>Customer</th>
            <th>Date</th>
            <th class="text-end">Amount</th>
            <th>Method</th>
            <th>Recorded by</th>
            <th>Note</th>
            <th>Applied to</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of payments">
            <td>
              <span class="badge" [class.badge-secondary]="p.source === 'POS_SALE'">
                {{ p.source === 'POS_SALE' ? 'POS sale' : 'Settlement' }}
              </span>
            </td>
            <td>{{ p.receiptNumber || '—' }}</td>
            <td>
              <a [routerLink]="['/admin/customers', p.customerId, 'record-payment']">{{ p.customerName }}</a>
            </td>
            <td>{{ p.paidAt | date: 'medium' }}</td>
            <td class="text-end">{{ p.amount | number: '1.2-2' }}</td>
            <td>{{ p.method || '—' }}</td>
            <td>{{ p.recordedByName || '—' }}</td>
            <td>{{ p.note || '—' }}</td>
            <td>
              <span *ngFor="let a of p.allocations" style="display:block; font-size:0.85rem;">
                {{ a.invoiceNumber || '—' }}{{ a.orderType === 'SALES_ORDER' ? ' (Sales order)' : '' }}: {{ a.amountApplied | number: '1.2-2' }}
              </span>
            </td>
            <td>
              <!-- Fourier: "Add view details on Customer payments" — only a 'SETTLEMENT' row has
                   a backing CustomerPayment id to look up (see CustomerPayment.id's own javadoc);
                   a POS_SALE row's own detail already lives on its own receipt (reachable via its
                   Applied-to line's order), so nothing to add here for those rows. -->
              <a *ngIf="p.id" class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/customer-payments', p.id]">View details</a>
            </td>
          </tr>
          <tr *ngIf="payments.length === 0">
            <td colspan="10" class="text-muted">
              {{ customerName ? 'No payments for ' + customerName + ' yet.' : 'No customer payments recorded yet.' }}
            </td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class CustomerPaymentListComponent implements OnInit {
  payments: CustomerPayment[] = [];
  error: string | null = null;
  /** Fourier: "on the customer ... view button" — set from `?customerId=&customerName=`, null for the store-wide list. */
  customerId: number | null = null;
  customerName: string | null = null;

  constructor(private creditApi: CustomerCreditApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
    const source$ = this.customerId != null ? this.creditApi.paymentHistory(this.customerId) : this.creditApi.allPayments();
    source$.subscribe({
      next: (p) => (this.payments = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load customer payments'),
    });
  }
}
