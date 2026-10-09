import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';

/** Admin > Inventory > Brands > New/Edit. Mirrors CategoryFormComponent's own simple CRUD form pattern, minus the color field. */
@Component({
  selector: 'app-brand-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit brand' : 'New brand' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/brands">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class BrandFormComponent implements OnInit {
  id: number | null = null;
  name = '';
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
    this.catalogApi.listBrands().subscribe({
      next: (brands) => {
        const found = brands.find((b) => b.id === this.id);
        if (found) {
          this.name = found.name;
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load brand'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = { name: this.name };
    const call = this.id
      ? this.catalogApi.updateBrand(this.id, payload)
      : this.catalogApi.createBrand(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/brands']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save brand'),
    });
  }
}
