import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { WorkstationApiService } from '../../core/api/workstation-api.service';
import { Workstation } from '../../core/models';

/** Manufacturing > Workstations (Fourier's own list, item 5). Mirrors PaymentMethodListComponent's simple CRUD list pattern. */
@Component({
  selector: 'app-workstation-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Workstations</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/workstations/new">+ New workstation</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Code</th>
            <th>Name</th>
            <th>Description</th>
            <th class="text-end">Hourly rate</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let w of workstations">
            <td>{{ w.code }}</td>
            <td>{{ w.name }}</td>
            <td>{{ w.description || '—' }}</td>
            <td class="text-end">{{ w.hourlyRate | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="w.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!w.active">Inactive</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/workstations', w.id, 'edit']">Edit</a></td>
          </tr>
          <tr *ngIf="workstations.length === 0">
            <td colspan="6" class="text-muted">No workstations yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class WorkstationListComponent implements OnInit {
  workstations: Workstation[] = [];
  error: string | null = null;

  constructor(private workstationApi: WorkstationApiService) {}

  ngOnInit(): void {
    this.workstationApi.listAll().subscribe({
      next: (w) => (this.workstations = w),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load workstations'),
    });
  }
}
