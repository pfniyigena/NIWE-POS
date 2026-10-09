import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BomApiService } from '../../core/api/bom-api.service';
import { Bom } from '../../core/models';

/** Manufacturing > Bill of Materials (Fourier's own list, item 1). */
@Component({
  selector: 'app-bom-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Bills of materials</h3>
        <a class="btn btn-primary" routerLink="/admin/manufacturing/boms/new">+ New bill of materials</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Code</th>
            <th>Product</th>
            <th>Name</th>
            <th>Routing</th>
            <th class="text-end">Components</th>
            <th class="text-end">Est. unit material cost</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of boms">
            <td>{{ b.code }}</td>
            <td>{{ b.productName }}</td>
            <td>{{ b.name || '—' }}</td>
            <td>{{ b.routingName || '—' }}</td>
            <td class="text-end">{{ b.lines.length }}</td>
            <td class="text-end">{{ b.estimatedUnitMaterialCost | number: '1.2-2' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="b.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!b.active">Inactive</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/manufacturing/boms', b.id, 'edit']">Edit</a></td>
          </tr>
          <tr *ngIf="boms.length === 0">
            <td colspan="8" class="text-muted">No bills of materials yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class BomListComponent implements OnInit {
  boms: Bom[] = [];
  error: string | null = null;

  constructor(private bomApi: BomApiService) {}

  ngOnInit(): void {
    this.bomApi.listAll().subscribe({
      next: (b) => (this.boms = b),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load bills of materials'),
    });
  }
}
