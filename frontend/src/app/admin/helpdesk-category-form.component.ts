import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCategoryApiService } from '../core/api/helpdesk-api.service';

/** Admin > Help Desk > Categories > New/Edit — see HelpDeskCategoryListComponent's own javadoc. */
@Component({
  selector: 'app-helpdesk-category-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit Help Desk category' : 'New Help Desk category' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Billing" required />
        </div>
        <div class="form-group">
          <label class="form-label">Description (optional)</label>
          <textarea class="form-control" name="description" [(ngModel)]="description" rows="2"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Inactive categories stay visible on existing tickets but can't be picked for new ones.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/helpdesk/categories">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class HelpDeskCategoryFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  description: string | null = null;
  active = true;
  error: string | null = null;

  constructor(private categoryApi: HelpDeskCategoryApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.categoryApi.listAll().subscribe({
      next: (categories) => {
        const c = categories.find((x) => x.id === this.id);
        if (!c) {
          this.error = 'Category not found';
          return;
        }
        this.name = c.name;
        this.description = c.description;
        this.active = c.active;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load category'),
    });
  }

  submit(): void {
    this.error = null;
    if (!this.name.trim()) {
      this.error = 'Name is required';
      return;
    }
    const payload = { name: this.name.trim(), description: this.description || null, active: this.active };
    const call = this.id ? this.categoryApi.update(this.id, payload) : this.categoryApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/helpdesk/categories']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save category'),
    });
  }
}
