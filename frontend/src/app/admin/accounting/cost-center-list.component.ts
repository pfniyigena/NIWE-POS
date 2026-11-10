import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';
import { CostCenter } from '../../core/models';

/**
 * Admin > Accounting > Cost Centers — the flat lookup list an admin manages before tagging any
 * journal line, manual entry, Expense, or Income with one. See the backend CostCenter's own
 * javadoc for why this is deliberately flat (no hierarchy) and why there's no delete, only
 * deactivate.
 */
@Component({
  selector: 'app-cost-center-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Cost centers' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/accounting/cost-centers/new">{{ '+ New cost center' | translate }}</a>
      </div>
      <table class="table-sm">
        <thead>
          <tr>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of costCenters">
            <td>{{ c.code }}</td>
            <td>{{ c.name }}</td>
            <td>
              <span class="badge badge-success" *ngIf="c.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!c.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/accounting/cost-centers', c.id, 'edit']">{{ 'Edit' | translate }}</a>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="costCenters.length === 0">
        {{ 'No cost centers yet — add one to start tagging journal entries, expenses and incomes by department, branch or project.' | translate }}
      </p>
    </app-shell>
  `,
})
export class CostCenterListComponent implements OnInit {
  costCenters: CostCenter[] = [];
  error: string | null = null;

  constructor(private costCenterApi: CostCenterApiService) {}

  ngOnInit(): void {
    this.costCenterApi.listAll().subscribe({
      next: (c) => (this.costCenters = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load cost centers'),
    });
  }
}
