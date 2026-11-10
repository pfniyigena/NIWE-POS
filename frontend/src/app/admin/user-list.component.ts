import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser } from '../core/models';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Staff accounts' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/staff/new">{{ '+ New staff account' | translate }}</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Username' | translate }}</th>
            <th>{{ 'Full name' | translate }}</th>
            <th>{{ 'Role' | translate }}</th>
            <th>{{ 'Custom role' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th>{{ 'Extra permissions' | translate }}</th>
            <th>{{ 'PIN' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let u of users">
            <td>{{ u.username }}</td>
            <td>{{ u.fullName }}</td>
            <td>{{ u.role }}</td>
            <td>
              <span *ngIf="u.customRoleName">{{ u.customRoleName }}</span>
              <span class="text-muted" *ngIf="!u.customRoleName">—</span>
            </td>
            <td>
              <span class="badge badge-success" *ngIf="u.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!u.active">{{ 'Inactive' | translate }}</span>
            </td>
            <!-- Fourier: "Build a full permissions system" — an Admin already holds every power, so this is only meaningful for a Cashier's individual grants. -->
            <td style="font-size:0.8rem;">
              <span class="text-muted" *ngIf="!u.permissions?.length">—</span>
              <span *ngIf="u.permissions?.length">{{ permissionSummary(u) }}</span>
            </td>
            <td>
              <span class="badge badge-success" *ngIf="u.hasPin">{{ 'Set' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!u.hasPin">{{ 'Not set' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/staff', u.id, 'edit']">{{ 'Edit' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary" (click)="resetPassword(u)">{{ 'Reset password' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="setPin(u)">{{ '{p0} PIN' | translate: { p0: (u.hasPin ? 'Change' : 'Set') } }}</button>
            </td>
          </tr>
          <tr *ngIf="users.length === 0">
            <td colspan="8" class="text-muted">{{ 'No staff accounts yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted mt-3" style="font-size:0.85rem;">
        {{ 'A PIN lets a staff member identify themselves at a shared terminal without a full login — see "Switch cashier" in the terminal — so more than one cashier can ring sales on the same till session. PINs must be 4–6 digits and unique among active staff.' | translate }}
      </p>
    </app-shell>
  `,
})
export class UserListComponent implements OnInit {
  users: AppUser[] = [];
  error: string | null = null;

  constructor(private userApi: UserApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.userApi.list().subscribe({
      next: (u) => (this.users = u),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load staff accounts'),
    });
  }

  resetPassword(u: AppUser): void {
    const newPassword = prompt(`New password for "${u.username}" (min 6 characters):`);
    if (!newPassword) return;
    this.error = null;
    this.userApi.resetPassword(u.id, newPassword).subscribe({
      next: () => alert(`Password reset for "${u.username}".`),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to reset password'),
    });
  }

  /** Fourier: "Build a full permissions system" — a short, readable summary of the raw permission names for this list screen. */
  permissionSummary(u: AppUser): string {
    const labels: Record<string, string> = {
      CONFIRM_RESTAURANT_WASTAGE: 'Confirm wastage',
      APPROVE_STOCK_ADJUSTMENT: 'Approve stock adjustments',
      APPROVE_PURCHASE_REQUISITION: 'Approve requisitions',
      OVERRIDE_DISCOUNT_CAP: 'Override discount cap',
      APPROVE_POS_VOID_RETURN: 'Approve voids/returns',
      APPROVE_POS_PRICE: 'Approve price changes',
      APPROVE_POS_CASH: 'Approve paid-outs',
      APPROVE_LARGE_EXPENSE: 'Approve large expenses',
      APPROVE_ASSET_DISPOSAL: 'Approve large asset disposals',
    };
    return (u.permissions ?? []).map((p) => labels[p] ?? p).join(', ');
  }

  setPin(u: AppUser): void {
    const pin = prompt(`New PIN for "${u.username}" (4-6 digits, used at the terminal):`);
    if (!pin) return;
    this.error = null;
    this.userApi.setPin(u.id, pin).subscribe({
      next: (updated) => {
        this.users = this.users.map((existing) => (existing.id === updated.id ? updated : existing));
        alert(`PIN set for "${u.username}".`);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to set PIN'),
    });
  }
}
