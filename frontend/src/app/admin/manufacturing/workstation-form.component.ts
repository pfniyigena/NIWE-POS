import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { WorkstationApiService } from '../../core/api/workstation-api.service';

/** Manufacturing > Workstations > New/Edit. Mirrors PaymentMethodFormComponent's simple CRUD form pattern. */
@Component({
  selector: 'app-workstation-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit workstation' : 'New workstation' }}</h3>
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
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">Description</label>
          <textarea class="form-control" name="description" rows="2" [(ngModel)]="description"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">Hourly rate</label>
          <input class="form-control" style="max-width:160px;" type="number" min="0" step="0.01" name="hourlyRate" [(ngModel)]="hourlyRate" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            This workstation's own operating cost per hour — used with a routing operation's estimated minutes to estimate labor/overhead cost.
          </div>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/manufacturing/workstations">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class WorkstationFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  description = '';
  hourlyRate = 0;
  active = true;
  error: string | null = null;

  constructor(
    private workstationApi: WorkstationApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.workstationApi.getById(this.id).subscribe({
      next: (w) => {
        this.code = w.code;
        this.name = w.name;
        this.description = w.description ?? '';
        this.hourlyRate = w.hourlyRate;
        this.active = w.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load workstation'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = {
      code: this.id ? undefined : this.code,
      name: this.name,
      description: this.description || null,
      hourlyRate: this.hourlyRate,
      active: this.active,
    };
    const call = this.id ? this.workstationApi.update(this.id, payload) : this.workstationApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/manufacturing/workstations']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save workstation'),
    });
  }
}
