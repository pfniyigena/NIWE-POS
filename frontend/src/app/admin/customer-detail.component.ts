import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CustomerApiService } from '../core/api/customer-api.service';
import { Customer } from '../core/models';
import { AuthService } from '../core/auth.service';

/**
 * Admin > Customers > detail — Fourier: "Better add one button (View) on customer then put all
 * others buttons on view form". Previously every one of these actions (Edit, Invoices, Payments,
 * Statement, Record payment) sat directly on the customer list row, five buttons deep per row;
 * now the list shows a single "View" button and this screen hosts the rest, styled like every
 * other module's own detail page (header + status badges + action-buttons row, stat-grid for the
 * record's fields) — the same house style Fourier asked for on Invoice detail ("View Invoice
 * Button contain should look same as the order view").
 *
 * <p>Loads via CustomerApiService.getById (a dedicated backend {@code GET /api/customers/{id}}),
 * the same single-record convention SalesOrderDetailComponent/InvoiceDetailComponent use, rather
 * than the older list()+client-side-find() pattern CustomerFormComponent still uses for editing.
 *
 * <p>"Payments"/"Statement"/"Record payment" stay ADMIN-only, same gating as before (see
 * CustomerListComponent's own prior javadoc / CustomerController's "Only Admin can enable Customer
 * Credit" class doc) — Edit itself is open to any logged-in user (Task: "Cashier can add or modify
 * customer").
 *
 * <p>Fourier's own later follow-up: "Let record payment per invoice not per customer (all
 * outstanding invoices)" — "Record payment" below now only ever settles a POS credit sale that
 * was never invoiced (see RecordPaymentComponent's own javadoc); an issued invoice is paid from
 * its own detail screen's "Record payment" button instead (see InvoiceRecordPaymentComponent),
 * reached via the "Invoices" link below.
 */
@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="customer as c">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ c.name }}</h3>
            <span *ngIf="c.creditEnabled" class="badge badge-success">Credit enabled</span>
            <span *ngIf="!c.creditEnabled" class="badge badge-secondary">Credit off</span>
          </div>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" [routerLink]="['/admin/customers', c.id, 'edit']">Edit</a>
            <a class="btn btn-outline-primary" [routerLink]="['/admin/invoices']" [queryParams]="{ customerId: c.id, customerName: c.name }">
              Invoices
            </a>
            <a *ngIf="isAdmin" class="btn btn-outline-primary" [routerLink]="['/admin/customer-payments']" [queryParams]="{ customerId: c.id, customerName: c.name }">
              Payments
            </a>
            <a *ngIf="isAdmin" class="btn btn-outline-primary" routerLink="/admin/customers/statement" [queryParams]="{ customerId: c.id, customerName: c.name }">
              Statement
            </a>
            <!-- Record payment is a credit/financial action — stays ADMIN-only even though Edit itself is open to any logged-in user. -->
            <a *ngIf="c.creditEnabled && isAdmin" class="btn btn-primary" [routerLink]="['/admin/customers', c.id, 'record-payment']">Record payment</a>
            <a class="btn btn-link" routerLink="/admin/customers">Back to customers</a>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Phone</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ c.phone || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Email</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ c.email || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Tax ID</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ c.taxId || '—' }}</div>
          </div>
          <div class="stat-tile" *ngIf="c.creditEnabled">
            <div class="stat-label">Credit limit</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ c.creditLimit | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile" *ngIf="c.creditEnabled">
            <div class="stat-label">Default payment term</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ c.defaultCreditTermDays ? c.defaultCreditTermDays + ' days' : '—' }}</div>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class CustomerDetailComponent implements OnInit {
  customer: Customer | null = null;
  error: string | null = null;

  constructor(private customerApi: CustomerApiService, private route: ActivatedRoute, private auth: AuthService) {}

  get isAdmin(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.customerApi.getById(id).subscribe({
      next: (c) => (this.customer = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load customer'),
    });
  }
}
