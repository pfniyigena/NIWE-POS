import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { SupplierPaymentApiService } from '../core/api/supplier-payment-api.service';
import { SupplierPayment } from '../core/models';

/**
 * Store-wide supplier payment history, across every supplier — the accounts-payable mirror of the
 * new store-wide Customer Payments list. Reached from Admin > Inventory > Suppliers ("Supplier
 * payments" link); recording a payment for one specific supplier still happens on
 * RecordSupplierPaymentComponent, reached from that supplier's own row.
 *
 * <p>Fourier: "Add view details on supplier payments list" — a "View details" link per row,
 * routing to SupplierPaymentDetailComponent, same convention as the Customer Payments list's own
 * "View details" link. Unlike that list, every row here has a backing SupplierPayment id (there's
 * no POS-sale-tender synthetic-row concept on the purchasing side), so the link is unconditional.
 *
 * <p>Fourier: "Add payment method when recording bill payment" — a Method column, same as the
 * Customer Payments list's own; shows '—' for a payment recorded through the general supplier-wide
 * allocation screen, which doesn't collect one (see SupplierPayment.method's backend javadoc).
 */
@Component({
  selector: 'app-supplier-payment-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Supplier payments</h3>
        <a class="btn btn-link" routerLink="/admin/suppliers">Back to suppliers</a>
      </div>
      <app-list-filter exportKind="supplier-payments" statusParam="method" placeholder="Search supplier, receipt no., note…" [statuses]="methods" statusAll="All methods" [dates]="true" [branches]="[]" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>Receipt #</th>
            <th>Supplier</th>
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
          <tr *ngFor="let p of list.rows">
            <td>{{ p.receiptNumber || '—' }}</td>
            <td>
              <a [routerLink]="['/admin/suppliers', p.supplierId, 'record-payment']">{{ p.supplierName }}</a>
            </td>
            <td>{{ p.paidAt | date: 'medium' }}</td>
            <td class="text-end">{{ p.amount | number: '1.2-2' }}</td>
            <td>{{ p.method || '—' }}</td>
            <td>{{ p.recordedByName }}</td>
            <td>{{ p.note || '—' }}</td>
            <td>
              <span *ngFor="let a of p.allocations" style="display:block; font-size:0.85rem;">
                {{ a.orderNumber || '—' }}{{ a.payableType === 'BILL' ? ' (Bill)' : '' }}: {{ a.amountApplied | number: '1.2-2' }}
              </span>
            </td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/supplier-payments', p.id]">View details</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="9" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">Nothing matches these filters.</ng-container><ng-template #nothingYet>No supplier payments recorded yet.</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class SupplierPaymentListComponent implements OnInit {
  error: string | null = null;

  list = new ListState<SupplierPayment>((f, p, sz) => this.paymentApi.allPaymentsPage(f, p, sz), 'Failed to load supplier payments');
  methods: { value: string; label: string }[] = [];
  constructor(private paymentApi: SupplierPaymentApiService, private methodApi: PaymentMethodApiService) {}

  ngOnInit(): void {
    this.methodApi.listAll().subscribe({ next: (m) => (this.methods = m.map((x) => ({ value: x.code, label: x.name }))), error: () => (this.methods = []) });
    this.list.load();
  }
}
