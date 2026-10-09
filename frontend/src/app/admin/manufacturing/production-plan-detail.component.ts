import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ProductionPlanApiService } from '../../core/api/production-plan-api.service';
import { OverheadCostApiService } from '../../core/api/overhead-cost-api.service';
import { OverheadCost, ProductionPlan, ProductionPlanLine } from '../../core/models';

/**
 * Manufacturing > Production plans > detail — a stored MRP snapshot. Every line shows demand
 * breakdown and today's live component shortfalls (see ProductionPlanDto's own javadoc for why
 * shortfalls are computed at read time, not generation time); "Generate order" converts one
 * reviewed line into a real DRAFT Manufacturing Order, same "review before committing" shape as
 * every other plan → order flow in this app.
 */
@Component({
  selector: 'app-production-plan-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="plan as p">
        <div class="mb-3">
          <h3 style="margin-bottom:0.25rem;">{{ p.planNumber }}</h3>
          <span class="text-muted">Generated {{ p.generatedAt | date: 'medium' }} — low-stock threshold {{ p.lowStockThreshold }}</span>
        </div>
        <p class="text-muted mb-3" *ngIf="p.note">{{ p.note }}</p>

        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Product</th>
              <th>Bill of materials</th>
              <th class="text-end">From sales</th>
              <th class="text-end">From low stock</th>
              <th class="text-end">Suggested qty</th>
              <th>Component shortfalls</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let line of p.lines">
              <td>{{ line.productName }}</td>
              <td>{{ line.bomCode || '—' }}</td>
              <td class="text-end">{{ line.demandFromSales }}</td>
              <td class="text-end">{{ line.demandFromLowStock }}</td>
              <td class="text-end">{{ line.suggestedQuantity }}</td>
              <td>
                <span *ngIf="line.componentShortfalls.length === 0" class="text-muted">None</span>
                <span *ngFor="let s of shortfallsOnly(line); let last = last" [class.text-danger]="s.shortfall > 0">
                  {{ s.componentName }}: {{ s.availableQuantity }}/{{ s.requiredQuantity }}{{ !last ? ', ' : '' }}
                </span>
              </td>
              <td>
                <a *ngIf="line.generatedManufacturingOrderId" class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/manufacturing/orders', line.generatedManufacturingOrderId]">
                  {{ line.generatedManufacturingOrderNumber }}
                </a>
                <button
                  *ngIf="!line.generatedManufacturingOrderId"
                  type="button"
                  class="btn btn-sm btn-primary"
                  [disabled]="!line.bomId"
                  (click)="openGenerate(line)"
                >
                  Generate order
                </button>
              </td>
            </tr>
            <tr *ngIf="p.lines.length === 0">
              <td colspan="7" class="text-muted">Nothing needed producing at the time this plan was generated.</td>
            </tr>
          </tbody>
        </table>

        <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="generatingLine">
          <div class="alert alert-danger" *ngIf="generateError">{{ generateError }}</div>
          <h4 class="mb-2">Generate manufacturing order — {{ generatingLine.productName }}</h4>
          <div class="flex gap-2 mb-2">
            <div>
              <label class="form-label">Quantity</label>
              <input class="form-control" type="number" style="width:140px;" min="1" [(ngModel)]="generateQuantity" name="generateQuantity" />
            </div>
            <div>
              <label class="form-label">Scheduled date (optional)</label>
              <app-date-picker [(ngModel)]="generateScheduledDate" name="generateScheduledDate" ></app-date-picker>
            </div>
          </div>
          <div class="mb-2" *ngIf="overheadCosts.length > 0">
            <label class="form-label">Indirect costs to apply (optional)</label>
            <div *ngFor="let o of overheadCosts">
              <label class="form-label">
                <input type="checkbox" [checked]="selectedOverheadIds.has(o.id)" (change)="toggleOverhead(o.id)" />
                {{ o.name }}
              </label>
            </div>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-primary btn-sm" (click)="generateOrder()">Create manufacturing order</button>
            <button class="btn btn-link btn-sm" (click)="cancelGenerate()">Cancel</button>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class ProductionPlanDetailComponent implements OnInit {
  plan: ProductionPlan | null = null;
  error: string | null = null;
  overheadCosts: OverheadCost[] = [];

  generatingLine: ProductionPlanLine | null = null;
  generateQuantity = 1;
  generateScheduledDate: string | null = null;
  selectedOverheadIds = new Set<number>();
  generateError: string | null = null;

  constructor(
    private productionPlanApi: ProductionPlanApiService,
    private overheadCostApi: OverheadCostApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.overheadCostApi.listActive().subscribe({
      next: (o) => (this.overheadCosts = o),
      error: () => {},
    });
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.productionPlanApi.getById(id).subscribe({
      next: (p) => (this.plan = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load production plan'),
    });
  }

  shortfallsOnly(line: ProductionPlanLine) {
    return line.componentShortfalls;
  }

  openGenerate(line: ProductionPlanLine): void {
    this.generatingLine = line;
    this.generateQuantity = line.suggestedQuantity;
    this.generateScheduledDate = null;
    this.selectedOverheadIds = new Set<number>();
    this.generateError = null;
  }

  cancelGenerate(): void {
    this.generatingLine = null;
  }

  toggleOverhead(id: number): void {
    if (this.selectedOverheadIds.has(id)) {
      this.selectedOverheadIds.delete(id);
    } else {
      this.selectedOverheadIds.add(id);
    }
  }

  generateOrder(): void {
    if (!this.plan || !this.generatingLine) return;
    if (!this.generateQuantity || this.generateQuantity <= 0) {
      this.generateError = 'Enter a quantity greater than zero';
      return;
    }
    this.generateError = null;
    this.productionPlanApi
      .generateOrder(this.plan.id, this.generatingLine.id, {
        quantity: this.generateQuantity,
        scheduledDate: this.generateScheduledDate ? new Date(this.generateScheduledDate).toISOString().slice(0, 10) : null,
        overheadCostIds: Array.from(this.selectedOverheadIds),
      })
      .subscribe({
        next: () => {
          this.generatingLine = null;
          this.load();
        },
        error: (err) => (this.generateError = err?.error?.message ?? 'Failed to generate manufacturing order'),
      });
  }
}
