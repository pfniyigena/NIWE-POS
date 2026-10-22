import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PackageSummary, WorkOrderApiService } from '../../core/api/work-order-api.service';

/** Work Orders > Service packages: standard services with a checklist, usual parts and a fixed price. */
@Component({
  selector: 'app-service-package-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Service packages</h3>
        <a class="btn btn-primary" routerLink="/admin/work-orders/packages/new">+ New package</a>
      </div>
      <p class="text-muted">A package is a standard service you sell again and again: its steps, usual parts and price are copied onto a new work order.</p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <table class="table-sm">
        <thead><tr><th>Package</th><th class="text-end">Price</th><th class="text-end">Hours</th><th class="text-end">Steps</th><th class="text-end">Parts</th><th class="text-end">Used</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let p of list" [style.opacity]="p.active ? 1 : 0.55">
            <td><a [routerLink]="['/admin/work-orders/packages', p.id]">{{ p.name }}</a><span class="text-muted" *ngIf="p.code"> · {{ p.code }}</span></td>
            <td class="text-end">{{ p.fixedPrice > 0 ? (p.fixedPrice | number: '1.0-2') : '—' }}</td>
            <td class="text-end">{{ p.estimatedHours != null ? (p.estimatedHours | number: '1.0-2') : '—' }}</td>
            <td class="text-end">{{ p.steps }}</td><td class="text-end">{{ p.parts }}</td><td class="text-end">{{ p.timesUsed }}</td>
            <td><span class="badge badge-secondary" *ngIf="!p.active">Inactive</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length">No packages yet.</p>
    </app-shell>
  `,
})
export class ServicePackageListComponent implements OnInit {
  list: PackageSummary[] = [];
  error: string | null = null;

  constructor(private api: WorkOrderApiService) {}

  ngOnInit(): void {
    this.api.packages().subscribe({ next: (l) => (this.list = l), error: (e) => (this.error = e?.error?.message ?? 'Could not load the packages') });
  }
}
