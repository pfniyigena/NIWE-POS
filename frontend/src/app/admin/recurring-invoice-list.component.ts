import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RecurringInvoiceApiService } from '../core/api/recurring-invoice-api.service';
import { RecurringInvoiceTemplate } from '../core/models';

/**
 * Admin > Sales > Recurring invoices (Fourier: "Add Recurring Invoice") — a saved template with
 * real invoice line items that auto-generates a brand-new, already-ISSUED invoice on a schedule
 * (see backend RecurringInvoiceTemplateService.generateDueTemplates), with "Run now" as a manual
 * escape hatch. Deliberately a separate module from Subscriptions (Fourier, via AskUserQuestion)
 * — a subscription bills one fixed plan at one price; this bills whatever mix of catalog products
 * the customer is actually being charged for.
 *
 * <p>Supports the same `?customerId=&customerName=` arrival convention as SubscriptionListComponent.
 */
@Component({
  selector: 'app-recurring-invoice-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? 'Recurring invoices for ' + customerName : 'Recurring invoices' }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/recurring-invoices" style="font-size:0.85rem;">Show every customer's recurring invoices</a>
        </div>
        <a class="btn btn-primary" routerLink="/admin/recurring-invoices/new">+ New recurring invoice</a>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>Name</th>
            <th>Customer</th>
            <th>Frequency</th>
            <th class="text-end">Est. amount</th>
            <th>Status</th>
            <th>Next run</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of templates">
            <td>{{ t.name }}</td>
            <td>{{ t.customerName }}</td>
            <td>{{ t.frequency }}</td>
            <td class="text-end">{{ t.estimatedAmount | number: '1.2-2' }}</td>
            <td><span class="badge" [ngClass]="t.active ? 'badge-success' : 'badge-secondary'">{{ t.active ? 'Active' : 'Paused' }}</span></td>
            <td>{{ t.nextRunDate }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/recurring-invoices', t.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="templates.length === 0">
            <td colspan="7" class="text-muted">
              {{ customerName ? 'No recurring invoices for ' + customerName + ' yet.' : 'No recurring invoices yet — create one with "+ New recurring invoice".' }}
            </td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RecurringInvoiceListComponent implements OnInit {
  templates: RecurringInvoiceTemplate[] = [];
  error: string | null = null;
  customerId: number | null = null;
  customerName: string | null = null;

  constructor(private recurringInvoiceApi: RecurringInvoiceApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
    this.recurringInvoiceApi.listAll(this.customerId ?? undefined).subscribe({
      next: (t) => (this.templates = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load recurring invoices'),
    });
  }
}
