import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Brand } from '../core/models';

/** Admin > Inventory > Brands (Fourier: "Add product brands") — same simple CRUD shape as Categories, minus the color swatch. */
@Component({
  selector: 'app-brand-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Brands</h3>
        <a class="btn btn-primary" routerLink="/admin/brands/new">+ New brand</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of brands">
            <td>{{ b.name }}</td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/brands', b.id, 'edit']">Edit</a>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(b)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="brands.length === 0">
            <td colspan="2" class="text-muted">No brands yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class BrandListComponent implements OnInit {
  brands: Brand[] = [];
  error: string | null = null;

  constructor(private catalogApi: CatalogApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.catalogApi.listBrands().subscribe({
      next: (b) => (this.brands = b),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load brands'),
    });
  }

  remove(b: Brand): void {
    if (!confirm(`Delete brand "${b.name}"?`)) return;
    this.error = null;
    this.catalogApi.deleteBrand(b.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete brand'),
    });
  }
}
