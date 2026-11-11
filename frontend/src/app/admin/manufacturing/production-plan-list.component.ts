import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListState } from '../../core/api/list-state';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { PagerComponent } from '../../shared/pager.component';
import { ProductionPlanApiService } from '../../core/api/production-plan-api.service';
import { ProductionPlan } from '../../core/models';

import { T } from '../../core/i18n/t';
/**
 * Manufacturing > Production plans (Fourier's own list, item 2; full MRP-style per "I need full
 * features"). Generating a new plan is right here at the top — each generation is itself a stored,
 * dated snapshot (mirrors how a Z-report is generated once and kept), not a live filter.
 */
@Component({
  selector: 'app-production-plan-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Production plans' | translate }}</h3>
      </div>

      <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;">
        <h4 class="mb-2">{{ 'Generate a new plan' | translate }}</h4>
        <div class="flex gap-2 align-center mb-2">
          <div>
            <label class="form-label">{{ 'Low-stock threshold' | translate }}</label>
            <input class="form-control" type="number" style="width:140px;" min="0" step="any" [(ngModel)]="lowStockThreshold" name="lowStockThreshold" />
          </div>
          <div class="grow">
            <label class="form-label">{{ 'Note (optional)' | translate }}</label>
            <input class="form-control" [(ngModel)]="note" name="note" />
          </div>
        </div>
        <p class="text-muted" style="font-size:0.85rem;">
          {{ 'Aggregates demand from every unfulfilled Sale Order line plus any manufactured product whose stock, after covering that sales demand, would still be below this threshold. Products without an active bill of materials, or with no unmet demand, aren\\'t included.' | translate }}
        </p>
        <button class="btn btn-primary" (click)="generate()">{{ 'Generate plan' | translate }}</button>
      </div>

      <app-list-filter exportPath="/manufacturing/production-plans/export" [placeholder]="'Search plan no., note, product' | translate" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Plan #' | translate }}</th>
            <th>{{ 'Generated' | translate }}</th>
            <th class="text-end">{{ 'Lines' | translate }}</th>
            <th>{{ 'Note' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of list.rows" class="pp-row">
            <td>{{ p.planNumber }}</td>
            <td>{{ p.generatedAt | date: 'medium' }}</td>
            <td class="text-end">{{ p.lines.length }}</td>
            <td>{{ p.note || '—' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/production-plans', p.id]">{{ 'View' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No production plans match these filters.' | translate) : ('No production plans yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ProductionPlanListComponent implements OnInit {
  list = new ListState<ProductionPlan>((f, p, sz) => this.productionPlanApi.page(f, p, sz), 'Failed to load production plans');
  lowStockThreshold = 10;
  note = '';
  error: string | null = null;

  constructor(
    private productionPlanApi: ProductionPlanApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.list.load();
  }

  generate(): void {
    this.error = null;
    this.productionPlanApi.generate({ lowStockThreshold: this.lowStockThreshold, note: this.note || null }).subscribe({
      next: (plan) => this.router.navigate(['/admin/manufacturing/production-plans', plan.id]),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to generate production plan')),
    });
  }
}
