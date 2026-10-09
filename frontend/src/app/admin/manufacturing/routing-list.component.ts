import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { RoutingApiService } from '../../core/api/routing-api.service';
import { Routing } from '../../core/models';

/** Manufacturing > Settings > Routings (Fourier's own naming, item 5: "Settings (Production routings etc)"). */
@Component({
  selector: 'app-routing-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Production routings</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/routings/new">+ New routing</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
            <th class="text-end">Operations</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of routings">
            <td>{{ r.name }}</td>
            <td>{{ r.description || '—' }}</td>
            <td class="text-end">{{ r.operations.length }}</td>
            <td>
              <span class="badge badge-success" *ngIf="r.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!r.active">Inactive</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/routings', r.id, 'edit']">Edit</a></td>
          </tr>
          <tr *ngIf="routings.length === 0">
            <td colspan="5" class="text-muted">No routings yet.</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="margin-top:1rem;">
        A routing is optional — a Bill of Materials without one still works end to end; a routing just adds an operation-by-operation time/labor estimate on top.
      </p>
    </app-shell>
  `,
})
export class RoutingListComponent implements OnInit {
  routings: Routing[] = [];
  error: string | null = null;

  constructor(private routingApi: RoutingApiService) {}

  ngOnInit(): void {
    this.routingApi.listAll().subscribe({
      next: (r) => (this.routings = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load routings'),
    });
  }
}
