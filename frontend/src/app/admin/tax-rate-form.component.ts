import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';

@Component({
  selector: 'app-tax-rate-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit tax rate' : 'New tax rate' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Code</label>
          <input class="form-control" name="code" [(ngModel)]="code" [disabled]="!!id" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
            The code can't be changed once created — it's stored on past sales and refunds.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">Rate (%)</label>
          <input class="form-control" type="number" step="0.01" min="0" max="100" name="ratePercent" [(ngModel)]="ratePercent" required />
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="inclusive" [(ngModel)]="inclusive" /> Tax-inclusive price
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            When checked, a product's price already has this tax folded in (the shelf price is the final price).
            When unchecked (default), tax is added on top of the price at checkout.
          </div>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active (selectable on a product)
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/settings/taxes">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class TaxRateFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  ratePercent: number | null = null;
  active = true;
  inclusive = false;
  error: string | null = null;

  constructor(
    private taxRateApi: TaxRateApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.taxRateApi.listAll().subscribe({
      next: (rates) => {
        const found = rates.find((t) => t.id === this.id);
        if (found) {
          this.code = found.code;
          this.name = found.name;
          this.ratePercent = found.ratePercent;
          this.active = found.active;
          this.inclusive = found.inclusive;
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load tax rate'),
    });
  }

  submit(): void {
    if (this.ratePercent == null) return;
    this.error = null;
    const call = this.id
      ? this.taxRateApi.update(this.id, {
          name: this.name,
          ratePercent: this.ratePercent,
          active: this.active,
          inclusive: this.inclusive,
        })
      : this.taxRateApi.create({
          code: this.code,
          name: this.name,
          ratePercent: this.ratePercent,
          inclusive: this.inclusive,
        });
    call.subscribe({
      next: () => this.router.navigate(['/admin/settings/taxes']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save tax rate'),
    });
  }
}
