import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { UserApiService } from '../core/api/user-api.service';
import { Branch, Permission } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';

/** One checklist row — see this component's own PERMISSION_OPTIONS for the fixed set. */
interface PermissionOption {
  value: Permission;
  label: string;
  hint: string;
}

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit staff account' : 'New staff account' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group" *ngIf="!id">
          <label class="form-label">Username</label>
          <input class="form-control" name="username" [(ngModel)]="username" required />
        </div>
        <div class="form-group" *ngIf="!id">
          <label class="form-label">Password</label>
          <input class="form-control" type="password" name="password" [(ngModel)]="password" required minlength="6" />
        </div>
        <div class="form-group">
          <label class="form-label">Full name</label>
          <input class="form-control" name="fullName" [(ngModel)]="fullName" required />
        </div>
        <div class="form-group">
          <label class="form-label">Email</label>
          <input class="form-control" type="email" name="email" [(ngModel)]="email" autocomplete="email" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Optional — needed only so this staff member can use "Forgot password?" on the login screen.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Role</label>
          <select class="form-select" name="role" [(ngModel)]="role">
            <option value="CASHIER">Cashier</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
        </div>
        <div class="form-group">
          <label class="form-label">Extra permissions</label>
          <div class="text-muted mb-2" style="font-size:0.8rem;">
            An Admin already has every power below — these let a Cashier be granted specific
            ADMIN-tier actions individually, without making their whole account an Admin.
          </div>
          <div *ngFor="let opt of permissionOptions" class="form-group" style="margin-bottom:0.5rem;">
            <label class="form-label" style="font-weight:normal;">
              <input type="checkbox" [checked]="hasPermission(opt.value)" (change)="togglePermission(opt.value, $event)" />
              {{ opt.label }}
            </label>
            <div class="text-muted" style="font-size:0.78rem; margin-left:1.4rem;">{{ opt.hint }}</div>
          </div>
        </div>
        <div class="form-group" *ngIf="branches.length > 1 && role === 'CASHIER'">
          <label class="form-label">Branch access</label>
          <div class="text-muted mb-2" style="font-size:0.8rem;">
            Limit this account to specific branches — it will only see and work those branches' tills, sales and approvals.
            Leave all unticked for access to every branch. Admins are never limited.
          </div>
          <div *ngFor="let b of branches" style="margin-bottom:0.25rem;">
            <label class="form-label" style="font-weight:normal;">
              <input type="checkbox" [checked]="branchIds.includes(b.id)" (change)="toggleBranch(b.id, $event)" />
              {{ b.name }}
            </label>
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/staff">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class UserFormComponent implements OnInit {
  id: number | null = null;
  username = '';
  password = '';
  fullName = '';
  email = '';
  role: 'ADMIN' | 'CASHIER' = 'CASHIER';
  active = true;
  permissions: Permission[] = [];
  branches: Branch[] = [];
  branchIds: number[] = [];
  error: string | null = null;

  /** Fourier: "Build a full permissions system" — the fixed set this app currently supports. */
  readonly permissionOptions: PermissionOption[] = [
    { value: 'CONFIRM_RESTAURANT_WASTAGE', label: 'Confirm/reject Restaurant wastage', hint: 'Review a logged wastage entry — applies the stock/accounting impact or discards it.' },
    { value: 'APPROVE_STOCK_ADJUSTMENT', label: 'Approve general Inventory stock adjustments', hint: 'Review a logged stock adjustment — applies the stock/accounting impact or discards it.' },
    { value: 'APPROVE_PURCHASE_REQUISITION', label: 'Approve Purchase requisitions', hint: 'Approve or reject a requisition awaiting approval.' },
    { value: 'OVERRIDE_DISCOUNT_CAP', label: 'Override the store-wide discount cap', hint: 'Apply a discount at checkout, and exceed the configured maximum discount.' },
    { value: 'APPROVE_EXPENSE', label: 'Approve/reject Expenses', hint: 'Approve or reject an expense awaiting approval, and void an already-approved one.' },
    { value: 'APPROVE_STOCK_TRANSFER', label: 'Approve/reject Stock transfers', hint: 'Review a logged transfer between warehouses — moves the stock or discards it.' },
  ];

  constructor(
    private userApi: UserApiService,
    private branchApi: BranchApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.userApi.list().subscribe({
      next: (users) => {
        const found = users.find((u) => u.id === this.id);
        if (found) {
          this.username = found.username;
          this.fullName = found.fullName;
          this.email = found.email ?? '';
          this.role = found.role;
          this.active = found.active;
          this.permissions = [...(found.permissions ?? [])];
          this.branchIds = [...(found.branchIds ?? [])];
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load staff account'),
    });
  }

  hasPermission(value: Permission): boolean {
    return this.permissions.includes(value);
  }

  togglePermission(value: Permission, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) {
      if (!this.permissions.includes(value)) this.permissions = [...this.permissions, value];
    } else {
      this.permissions = this.permissions.filter((p) => p !== value);
    }
  }

  toggleBranch(id: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.branchIds = checked ? (this.branchIds.includes(id) ? this.branchIds : [...this.branchIds, id]) : this.branchIds.filter((x) => x !== id);
  }

  /** Admins are never limited, so any stale selection is dropped when saving one. */
  private effectiveBranchIds(): number[] {
    return this.role === 'ADMIN' ? [] : this.branchIds;
  }

  submit(): void {
    this.error = null;
    if (this.id) {
      this.userApi
        .update(this.id, { fullName: this.fullName, email: this.email || null, role: this.role, active: this.active, permissions: this.permissions, branchIds: this.effectiveBranchIds() })
        .subscribe({
          next: () => this.router.navigate(['/admin/staff']),
          error: (err) => (this.error = err?.error?.message ?? 'Failed to save staff account'),
        });
    } else {
      this.userApi
        .create({
          username: this.username,
          password: this.password,
          fullName: this.fullName,
          email: this.email || null,
          role: this.role,
          permissions: this.permissions,
          branchIds: this.effectiveBranchIds(),
        })
        .subscribe({
          next: () => this.router.navigate(['/admin/staff']),
          error: (err) => (this.error = err?.error?.message ?? 'Failed to create staff account'),
        });
    }
  }
}
