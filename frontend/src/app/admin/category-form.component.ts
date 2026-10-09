import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';

@Component({
  selector: 'app-category-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit category' : 'New category' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">Color</label>
          <div class="flex gap-2 align-center">
            <input class="color-swatch-input" type="color" name="colorHex" [(ngModel)]="colorHex" />
            <span class="text-muted">{{ colorHex }}</span>
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/categories">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class CategoryFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  colorHex = '#2fbe72';
  error: string | null = null;

  constructor(
    private catalogApi: CatalogApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.catalogApi.listCategories().subscribe({
      next: (categories) => {
        const found = categories.find((c) => c.id === this.id);
        if (found) {
          this.name = found.name;
          this.colorHex = found.colorHex;
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load category'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = { name: this.name, colorHex: this.colorHex };
    const call = this.id
      ? this.catalogApi.updateCategory(this.id, payload)
      : this.catalogApi.createCategory(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/categories']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save category'),
    });
  }
}
