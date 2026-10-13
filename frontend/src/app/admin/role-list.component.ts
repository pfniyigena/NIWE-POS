import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AccessAreaInfo, CustomRole, RoleApiService } from '../core/api/role-api.service';

/**
 * Staff > Roles: the custom roles an admin has built. A role is a named set of access areas (Sales, Inventory,
 * Accounting, Reports...) at View or Manage level; assign one to a Cashier account on the staff form to give them
 * a "Manager" or "Accountant" style of access without making them an Admin.
 */
@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Roles</h3>
        <a class="btn btn-primary" routerLink="/admin/staff/roles/new">+ New role</a>
      </div>
      <p class="text-muted" style="font-size:0.85rem;">
        A role decides which parts of the system a staff member can open. Admins always have full access; users
        management, settings, backup and audit log stay Admin-only. Approvals (expenses, stock adjustments...) are
        still granted per person as "Extra permissions" on the staff account.
      </p>
      <table>
        <thead>
          <tr>
            <th>Role</th>
            <th>Access</th>
            <th>Staff</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of roles">
            <td>
              <strong>{{ r.name }}</strong>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="r.description">{{ r.description }}</div>
            </td>
            <td style="font-size:0.85rem;">{{ summary(r) }}</td>
            <td>{{ r.userCount }}</td>
            <td>
              <span class="badge badge-success" *ngIf="r.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!r.active">Inactive</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/staff/roles', r.id, 'edit']">Edit</a>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(r)" [disabled]="r.userCount > 0" [title]="r.userCount > 0 ? 'Move its staff to another role first' : 'Delete this role'">Delete</button>
            </td>
          </tr>
          <tr *ngIf="roles.length === 0">
            <td colspan="5" class="text-muted">No roles yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RoleListComponent implements OnInit {
  roles: CustomRole[] = [];
  areas: AccessAreaInfo[] = [];
  error: string | null = null;

  constructor(private api: RoleApiService) {}

  ngOnInit(): void {
    this.api.areas().subscribe({ next: (a) => (this.areas = a), error: () => {} });
    this.load();
  }

  private load(): void {
    this.api.list().subscribe({
      next: (r) => (this.roles = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load roles'),
    });
  }

  /** "Sales (manage), Inventory (view), Reports (view)" — or "No access" for an empty role. */
  summary(role: CustomRole): string {
    const parts: string[] = [];
    for (const area of this.areas) {
      const level = role.grants.includes(area.code + '_MANAGE') ? 'manage' : role.grants.includes(area.code + '_VIEW') ? 'view' : null;
      if (level) parts.push(`${area.label} (${level})`);
    }
    return parts.length ? parts.join(', ') : 'No access';
  }

  remove(role: CustomRole): void {
    if (!confirm(`Delete the role "${role.name}"?`)) return;
    this.api.remove(role.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete role'),
    });
  }
}
