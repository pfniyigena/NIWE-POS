import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../core/models';

@Component({
  selector: 'app-payment-method-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Payment methods' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/payment-methods/new">{{ '+ New payment method' | translate }}</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Order' | translate }}</th>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Cash-equivalent' | translate }}</th>
            <th>{{ 'Store credit' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let m of methods">
            <td>{{ m.sortOrder }}</td>
            <td>{{ m.code }}</td>
            <td>{{ m.name }}</td>
            <td>
              <span class="badge badge-success" *ngIf="m.cash">{{ 'Cash' | translate }}</span>
              <span class="text-muted" *ngIf="!m.cash">—</span>
            </td>
            <td>
              <span class="badge badge-success" *ngIf="m.credit">{{ 'Credit' | translate }}</span>
              <span class="badge badge-warning" *ngIf="m.roomCharge">{{ 'Room charge' | translate }}</span>
              <span class="text-muted" *ngIf="!m.credit && !m.roomCharge">—</span>
            </td>
            <td>
              <span class="badge badge-success" *ngIf="m.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!m.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/payment-methods', m.id, 'edit']">{{ 'Edit' | translate }}</a></td>
          </tr>
          <tr *ngIf="methods.length === 0">
            <td colspan="7" class="text-muted">{{ 'No payment methods yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="margin-top:1rem;">
        {{ 'Exactly the methods that should count as cash-in-drawer for till reconciliation should be flagged "Cash-equivalent" — everything else is treated as non-cash at session close. "Order" controls the sequence methods appear in on the checkout screen (lower first) — set it on the edit screen.' | translate }}
      </p>
    </app-shell>
  `,
})
export class PaymentMethodListComponent implements OnInit {
  methods: PaymentMethodOption[] = [];
  error: string | null = null;

  constructor(private paymentMethodApi: PaymentMethodApiService) {}

  ngOnInit(): void {
    this.paymentMethodApi.listAll().subscribe({
      next: (m) => (this.methods = m),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment methods'),
    });
  }
}
