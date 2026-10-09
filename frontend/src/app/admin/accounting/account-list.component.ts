import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountApiService } from '../../core/api/account-api.service';
import { Account, AccountType } from '../../core/models';

/**
 * Admin > Accounting > Chart of Accounts. Fourier: "Seed a standard starter chart" — the list an
 * admin sees the moment Accounting is turned on (Admin > Accounting > Settings), grouped by
 * account type the way a real chart of accounts is always presented (Assets, Liabilities, Equity,
 * Revenue, Expenses, in that order — see the backend AccountService.displayOrder's own javadoc).
 */
@Component({
  selector: 'app-account-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Chart of accounts</h3>
        <a class="btn btn-primary" routerLink="/admin/accounting/accounts/new">+ New account</a>
      </div>
      <ng-container *ngFor="let group of groupOrder">
        <ng-container *ngIf="grouped[group]?.length">
          <h4 class="mb-2" style="margin-top:1.5rem;">{{ groupLabel(group) }}</h4>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Description</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let a of grouped[group]">
                <td>{{ a.code }}</td>
                <td>
                  {{ a.name }}
                  <span class="badge badge-secondary" *ngIf="a.systemAccount" style="margin-left:0.5rem;" title="Posted to automatically — cannot be deactivated">system</span>
                </td>
                <td class="text-muted">{{ a.description || '—' }}</td>
                <td>
                  <span class="badge badge-success" *ngIf="a.active">Active</span>
                  <span class="badge badge-secondary" *ngIf="!a.active">Inactive</span>
                </td>
                <td class="flex gap-2">
                  <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/accounting/accounts', a.id, 'ledger']">Ledger</a>
                  <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/accounting/accounts', a.id, 'edit']">Edit</a>
                </td>
              </tr>
            </tbody>
          </table>
        </ng-container>
      </ng-container>
      <p class="text-muted" *ngIf="accounts.length === 0">
        No accounts yet — turn on Accounting under Admin &gt; Accounting &gt; Settings to seed the standard starter chart, or add one manually above.
      </p>
    </app-shell>
  `,
})
export class AccountListComponent implements OnInit {
  accounts: Account[] = [];
  grouped: Record<string, Account[]> = {};
  groupOrder: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'];
  error: string | null = null;

  constructor(private accountApi: AccountApiService) {}

  ngOnInit(): void {
    this.accountApi.listAll().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.grouped = {};
        for (const a of accounts) {
          (this.grouped[a.type] ??= []).push(a);
        }
        for (const key of Object.keys(this.grouped)) {
          this.grouped[key].sort((x, y) => x.code.localeCompare(y.code));
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load chart of accounts'),
    });
  }

  groupLabel(type: AccountType): string {
    switch (type) {
      case 'ASSET':
        return 'Assets';
      case 'LIABILITY':
        return 'Liabilities';
      case 'EQUITY':
        return 'Equity';
      case 'REVENUE':
        return 'Revenue';
      case 'EXPENSE':
        return 'Expenses';
      default:
        return type;
    }
  }
}
