import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { StockCountApiService } from '../core/api/stock-count-api.service';
import { StockCount } from '../core/models';

/** Admin > Inventory > Stock counts — physical stocktakes, newest first. */
@Component({
  selector: 'app-stock-count-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Stock counts' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/stock-counts/new">{{ '+ Start a count' | translate }}</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Count #' | translate }}</th>
            <th>{{ 'Started' | translate }}</th>
            <th>{{ 'Warehouse' | translate }}</th>
            <th>{{ 'Category' | translate }}</th>
            <th class="text-end">{{ 'Lines' | translate }}</th>
            <th class="text-end">{{ 'Counted' | translate }}</th>
            <th class="text-end">{{ 'Differences' | translate }}</th>
            <th class="text-end">{{ 'Variance value' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of counts">
            <td><a [routerLink]="['/admin/stock-counts', c.id]">{{ c.countNumber }}</a></td>
            <td>{{ c.createdAt | date: 'medium' }}</td>
            <td>{{ c.warehouseName }}</td>
            <td>{{ c.categoryName || 'All' }}</td>
            <td class="text-end">{{ c.lineCount }}</td>
            <td class="text-end">{{ c.countedCount }}</td>
            <td class="text-end">{{ c.differenceCount }}</td>
            <td class="text-end" [style.color]="c.varianceValue < 0 ? 'var(--danger-text)' : 'inherit'">{{ c.varianceValue | number: '1.2-2' }}</td>
            <td>{{ c.status === 'IN_PROGRESS' ? 'In progress' : c.status === 'SUBMITTED' ? 'Awaiting approval' : c.status === 'APPROVED' ? ('Approved' | translate) : ('Cancelled' | translate) }}</td>
          </tr>
          <tr *ngIf="counts.length === 0">
            <td colspan="9" class="text-muted">{{ 'No stock counts yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class StockCountListComponent implements OnInit {
  counts: StockCount[] = [];
  error: string | null = null;

  constructor(private api: StockCountApiService) {}

  ngOnInit(): void {
    this.api.listAll().subscribe({
      next: (c) => (this.counts = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load stock counts'),
    });
  }
}
