import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListFilter, ListState, localPage } from '../core/api/list-state';
import { PageResult } from '../core/api/paged';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { Observable, map } from 'rxjs';
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
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? 'Payments for ' + customerName : 'Customer payments' }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/customer-payments" style="font-size:0.85rem;">{{ 'Show every customer\\'s payments' | translate }}</a>
        </div>
        <a class="btn btn-link" routerLink="/admin/customers">{{ 'Back to customers' | translate }}</a>
      </div>
      <app-list-filter [exportKind]="customerId == null ? 'customer-payments' : ''" statusParam="method" [placeholder]="'Search customer, receipt no., note…' | translate" [statuses]="methods" statusAll="All methods" [dates]="true" [branches]="[]" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Type' | translate }}</th>
            <th>{{ 'Receipt #' | translate }}</th>
            <th>{{ 'Customer' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th class="text-end">{{ 'Amount' | translate }}</th>
            <th>{{ 'Method' | translate }}</th>
            <th>{{ 'Recorded by' | translate }}</th>
            <th>{{ 'Note' | translate }}</th>
            <th>{{ 'Applied to' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of list.rows">
            <td>
              <span class="badge" [class.badge-secondary]="p.source === 'POS_SALE'">
                {{ p.source === 'POS_SALE' ? ('POS sale' | translate) : ('Settlement' | translate) }}
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
              <a *ngIf="p.id" class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/customer-payments', p.id]">{{ 'View details' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="10" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ customerName ? 'No payments for ' + customerName + ' yet.' : 'No customer payments recorded yet.' }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class CustomerPaymentListComponent implements OnInit {
  error: string | null = null;
  /** Fourier: "on the customer ... view button" — set from `?customerId=&customerName=`, null for the store-wide list. */
  customerId: number | null = null;
  customerName: string | null = null;

  list = new ListState<CustomerPayment>((f, p, sz) => this.fetchPage(f, p, sz), 'Failed to load customer payments');
  methods: { value: string; label: string }[] = [];

  /** The store-wide list is paged by the server; one customer's own history comes whole and is paged here. */
  private fetchPage(f: ListFilter, p: number, sz: number): Observable<PageResult<CustomerPayment>> {
    if (this.customerId == null) return this.creditApi.allPaymentsPage(f, p, sz);
    return this.creditApi.paymentHistory(this.customerId).pipe(
      map((all) => localPage(all, f, p, sz, (x) => [x.customerName, x.receiptNumber, x.note], (x) => x.paidAt, (x) => x.method)),
    );
  }

  constructor(private creditApi: CustomerCreditApiService, private route: ActivatedRoute, private methodApi: PaymentMethodApiService) {}

  ngOnInit(): void {
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
    this.methodApi.listAll().subscribe({ next: (m) => (this.methods = m.map((x) => ({ value: x.code, label: x.name }))), error: () => (this.methods = []) });
    this.list.load();
  }
}
