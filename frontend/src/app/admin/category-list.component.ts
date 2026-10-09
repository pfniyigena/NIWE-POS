import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Category } from '../core/models';

@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Categories</h3>
        <a class="btn btn-primary" routerLink="/admin/categories/new">+ New category</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Color</th>
            <th>Name</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of categories">
            <td><span class="color-swatch" [style.background]="c.colorHex"></span></td>
            <td>{{ c.name }}</td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/categories', c.id, 'edit']">Edit</a>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(c)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="categories.length === 0">
            <td colspan="3" class="text-muted">No categories yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class CategoryListComponent implements OnInit {
  categories: Category[] = [];
  error: string | null = null;

  constructor(private catalogApi: CatalogApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.catalogApi.listCategories().subscribe({
      next: (c) => (this.categories = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load categories'),
    });
  }

  remove(c: Category): void {
    if (!confirm(`Delete category "${c.name}"?`)) return;
    this.error = null;
    this.catalogApi.deleteCategory(c.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete category'),
    });
  }
}
