import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { IncomeApiService } from '../../core/api/income-api.service';
import { Branch, Income } from '../../core/models';
import { BranchApiService } from '../../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../../core/branch-util';

/** Admin > Accounting > Incomes — ExpenseListComponent's mirror image for money received outside the POS/Sale Order checkout flow. Read-only, same as ExpenseListComponent (see its own javadoc). */
@Component({
  selector: 'app-income-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Incomes' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/accounting/incomes/new">{{ '+ New income' | translate }}</a>
      </div>
      <div class="flex gap-2 align-center mb-3" *ngIf="branches.length > 1">
        <select class="form-select" style="width:auto;" [(ngModel)]="branchFilter" [title]="'Branch' | translate">
          <option [ngValue]="null">{{ 'All branches' | translate }}</option>
          <option *ngFor="let br of branches" [ngValue]="br.id">{{ br.name }}</option>
        </select>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Category' | translate }}</th>
            <th>{{ 'Payer' | translate }}</th>
            <th>{{ 'Amount' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
            <th>{{ 'Cost center' | translate }}</th>
            <th>{{ 'Memo' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let i of shown">
            <td>{{ i.incomeDate }}</td>
            <td>{{ i.accountCode }} — {{ i.accountName }}</td>
            <td>{{ i.payeeName }} <span class="text-muted">({{ i.payeeType }})</span></td>
            <td class="text-end">{{ i.amount | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="i.received">{{ 'Received' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!i.received">{{ 'Owed to us' | translate }}</span>
            </td>
            <td *ngIf="branches.length > 1">{{ branchName(i.branchId) }}</td>
            <td>{{ i.costCenterName || '—' }}</td>
            <td class="text-muted">{{ i.memo || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="incomes.length === 0">{{ 'No incomes recorded yet.' | translate }}</p>
    </app-shell>
  `,
})
export class IncomeListComponent implements OnInit {
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  get shown(): Income[] {
    return this.branchFilter == null ? this.incomes : this.incomes.filter((i) => effectiveBranchId(i.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  incomes: Income[] = [];
  error: string | null = null;

  constructor(private incomeApi: IncomeApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.incomeApi.listAll().subscribe({
      next: (i) => (this.incomes = i),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load incomes'),
    });
  }
}
