import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCategoryApiService } from '../core/api/helpdesk-api.service';
import { HelpDeskCategory } from '../core/models';

/**
 * Admin > Help Desk > Categories — the configurable list tickets route through (Fourier, via
 * AskUserQuestion: "Configurable categories/departments ... that you can manage"). Toggled
 * inactive rather than deleted once a ticket uses it, same convention as Product Category/Brand.
 */
@Component({
  selector: 'app-helpdesk-category-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Help Desk categories</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/tickets">Tickets</a>
          <a class="btn btn-primary" routerLink="/admin/helpdesk/categories/new">+ New category</a>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Description</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of categories">
            <td>{{ c.name }}</td>
            <td>{{ c.description }}</td>
            <td>
              <span class="badge badge-success" *ngIf="c.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!c.active">Inactive</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/helpdesk/categories', c.id, 'edit']">Edit</a>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(c)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="categories.length === 0">
            <td colspan="4" class="text-muted">No categories yet — tickets can still be created without one.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class HelpDeskCategoryListComponent implements OnInit {
  categories: HelpDeskCategory[] = [];
  error: string | null = null;

  constructor(private categoryApi: HelpDeskCategoryApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.categoryApi.listAll().subscribe({
      next: (c) => (this.categories = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load categories'),
    });
  }

  remove(c: HelpDeskCategory): void {
    if (!confirm(`Delete category "${c.name}"?`)) return;
    this.error = null;
    this.categoryApi.delete(c.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete category — mark it inactive instead if tickets still use it'),
    });
  }
}
