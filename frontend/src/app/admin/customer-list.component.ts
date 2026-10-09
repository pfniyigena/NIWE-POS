import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CustomerApiService } from '../core/api/customer-api.service';
import { Customer } from '../core/models';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Customers</h3>
        <a class="btn btn-primary" routerLink="/admin/customers/new">+ New customer</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Phone</th>
            <th>Email</th>
            <th>Tax ID</th>
            <th>Credit</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of customers">
            <td>{{ c.name }}</td>
            <td>{{ c.phone }}</td>
            <td>{{ c.email }}</td>
            <td>{{ c.taxId || '—' }}</td>
            <td>
              <span *ngIf="c.creditEnabled" class="badge badge-success">Enabled · limit {{ c.creditLimit | number: '1.2-2' }}</span>
              <span *ngIf="!c.creditEnabled" class="text-muted">Off</span>
            </td>
            <td class="flex gap-2">
              <!-- Fourier: "Better add one button (View) on customer then put all others buttons
                   on view form" — Edit/Invoices/Payments/Statement/Record payment all now live on
                   CustomerDetailComponent, reached from here. -->
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/customers', c.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="customers.length === 0">
            <td colspan="6" class="text-muted">No customers yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class CustomerListComponent implements OnInit {
  customers: Customer[] = [];
  error: string | null = null;

  constructor(private customerApi: CustomerApiService) {}

  ngOnInit(): void {
    this.customerApi.list().subscribe({
      next: (c) => (this.customers = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load customers'),
    });
  }
}
