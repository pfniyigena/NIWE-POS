import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { OverheadCostApiService } from '../../core/api/overhead-cost-api.service';
import { OverheadAllocationMethod } from '../../core/models';

/** Manufacturing > Indirect Costs > New/Edit. Mirrors PaymentMethodFormComponent's simple CRUD form pattern. */
@Component({
  selector: 'app-overhead-cost-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit indirect cost' : 'New indirect cost' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Code</label>
          <input class="form-control" name="code" [(ngModel)]="code" [disabled]="!!id" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
            The code can't be changed once created.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Electricity, Factory rent share" required />
        </div>
        <div class="form-group">
          <label class="form-label">Allocation method</label>
          <select class="form-select" name="allocationMethod" [(ngModel)]="allocationMethod" required>
            <option value="FLAT">Flat — a fixed total for the whole order</option>
            <option value="PER_UNIT">Per unit produced</option>
            <option value="PERCENT_OF_MATERIAL_COST">Percent of material cost</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Rate</label>
          <input class="form-control" style="max-width:160px;" type="number" min="0" step="0.01" name="rate" [(ngModel)]="rate" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ rateHint() }}
          </div>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/manufacturing/overhead-costs">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class OverheadCostFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  allocationMethod: OverheadAllocationMethod = 'PER_UNIT';
  rate = 0;
  active = true;
  error: string | null = null;

  constructor(
    private overheadCostApi: OverheadCostApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.overheadCostApi.getById(this.id).subscribe({
      next: (o) => {
        this.code = o.code;
        this.name = o.name;
        this.allocationMethod = o.allocationMethod;
        this.rate = o.rate;
        this.active = o.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load indirect cost'),
    });
  }

  rateHint(): string {
    switch (this.allocationMethod) {
      case 'FLAT':
        return 'A fixed total charged against this order, spread across its runs proportional to quantity produced.';
      case 'PER_UNIT':
        return 'Amount charged for every unit actually produced.';
      case 'PERCENT_OF_MATERIAL_COST':
        return 'A percentage (e.g. 10 for 10%) of each run\'s own material cost.';
      default:
        return '';
    }
  }

  submit(): void {
    this.error = null;
    const payload = {
      code: this.id ? undefined : this.code,
      name: this.name,
      allocationMethod: this.allocationMethod,
      rate: this.rate,
      active: this.active,
    };
    const call = this.id ? this.overheadCostApi.update(this.id, payload) : this.overheadCostApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/manufacturing/overhead-costs']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save indirect cost'),
    });
  }
}
