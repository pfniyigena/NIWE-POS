import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { OverheadCostApiService } from '../../core/api/overhead-cost-api.service';
import { OverheadCost } from '../../core/models';

/** Manufacturing > Indirect Costs (Fourier's own term, item 4). Mirrors PaymentMethodListComponent's simple CRUD list pattern. */
@Component({
  selector: 'app-overhead-cost-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Indirect costs</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/overhead-costs/new">+ New indirect cost</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Code</th>
            <th>Name</th>
            <th>Allocation method</th>
            <th class="text-end">Rate</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of overheadCosts">
            <td>{{ o.code }}</td>
            <td>{{ o.name }}</td>
            <td>{{ methodLabel(o.allocationMethod) }}</td>
            <td class="text-end">{{ o.rate | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="o.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!o.active">Inactive</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/overhead-costs', o.id, 'edit']">Edit</a></td>
          </tr>
          <tr *ngIf="overheadCosts.length === 0">
            <td colspan="6" class="text-muted">No indirect costs yet.</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="margin-top:1rem;">
        "Flat" spreads a fixed total across a Manufacturing Order's runs proportional to quantity produced.
        "Per unit" charges the rate for every unit actually produced. "Percent of material cost" charges a
        percentage of each run's own material cost.
      </p>
    </app-shell>
  `,
})
export class OverheadCostListComponent implements OnInit {
  overheadCosts: OverheadCost[] = [];
  error: string | null = null;

  constructor(private overheadCostApi: OverheadCostApiService) {}

  ngOnInit(): void {
    this.overheadCostApi.listAll().subscribe({
      next: (o) => (this.overheadCosts = o),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load indirect costs'),
    });
  }

  methodLabel(method: string): string {
    switch (method) {
      case 'FLAT':
        return 'Flat (per order)';
      case 'PER_UNIT':
        return 'Per unit produced';
      case 'PERCENT_OF_MATERIAL_COST':
        return '% of material cost';
      default:
        return method;
    }
  }
}
