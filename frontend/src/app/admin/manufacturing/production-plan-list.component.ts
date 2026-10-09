import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ProductionPlanApiService } from '../../core/api/production-plan-api.service';
import { ProductionPlan } from '../../core/models';

/**
 * Manufacturing > Production plans (Fourier's own list, item 2; full MRP-style per "I need full
 * features"). Generating a new plan is right here at the top — each generation is itself a stored,
 * dated snapshot (mirrors how a Z-report is generated once and kept), not a live filter.
 */
@Component({
  selector: 'app-production-plan-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Production plans</h3>
      </div>

      <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;">
        <h4 class="mb-2">Generate a new plan</h4>
        <div class="flex gap-2 align-center mb-2">
          <div>
            <label class="form-label">Low-stock threshold</label>
            <input class="form-control" type="number" style="width:140px;" min="0" step="1" [(ngModel)]="lowStockThreshold" name="lowStockThreshold" />
          </div>
          <div class="grow">
            <label class="form-label">Note (optional)</label>
            <input class="form-control" [(ngModel)]="note" name="note" />
          </div>
        </div>
        <p class="text-muted" style="font-size:0.85rem;">
          Aggregates demand from every unfulfilled Sale Order line plus any manufactured product whose stock,
          after covering that sales demand, would still be below this threshold. Products without an active
          bill of materials, or with no unmet demand, aren't included.
        </p>
        <button class="btn btn-primary" (click)="generate()">Generate plan</button>
      </div>

      <table>
        <thead>
          <tr>
            <th>Plan #</th>
            <th>Generated</th>
            <th class="text-end">Lines</th>
            <th>Note</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of plans">
            <td>{{ p.planNumber }}</td>
            <td>{{ p.generatedAt | date: 'medium' }}</td>
            <td class="text-end">{{ p.lines.length }}</td>
            <td>{{ p.note || '—' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/production-plans', p.id]">View</a></td>
          </tr>
          <tr *ngIf="plans.length === 0">
            <td colspan="5" class="text-muted">No production plans yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ProductionPlanListComponent implements OnInit {
  plans: ProductionPlan[] = [];
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
    this.productionPlanApi.listAll().subscribe({
      next: (p) => (this.plans = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load production plans'),
    });
  }

  generate(): void {
    this.error = null;
    this.productionPlanApi.generate({ lowStockThreshold: this.lowStockThreshold, note: this.note || null }).subscribe({
      next: (plan) => this.router.navigate(['/admin/manufacturing/production-plans', plan.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to generate production plan'),
    });
  }
}
