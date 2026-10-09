import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { CostCenterApiService } from '../../core/api/cost-center-api.service';

/** Admin > Accounting > Cost Centers > New/Edit. Mirrors AccountFormComponent's own simple CRUD form pattern. */
@Component({
  selector: 'app-cost-center-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit cost center' : 'New cost center' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Code</label>
          <input class="form-control" name="code" [(ngModel)]="code" [disabled]="!!id" placeholder="e.g. CC-01" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
            The code can't be changed once created.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Front Store" required />
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/accounting/cost-centers">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class CostCenterFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  active = true;
  error: string | null = null;

  constructor(
    private costCenterApi: CostCenterApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.costCenterApi.getById(this.id).subscribe({
      next: (c) => {
        this.code = c.code;
        this.name = c.name;
        this.active = c.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load cost center'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = { code: this.code, name: this.name, active: this.active };
    const call = this.id ? this.costCenterApi.update(this.id, payload) : this.costCenterApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/accounting/cost-centers']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save cost center'),
    });
  }
}
