import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AccessAreaInfo, RoleApiService } from '../core/api/role-api.service';

type Level = 'NONE' | 'VIEW' | 'MANAGE';

/** Create or edit a custom role: pick None / View / Manage for each access area. */
@Component({
  selector: 'app-role-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit role' : 'New role' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Role name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required maxlength="100" placeholder="e.g. Manager, Accountant, Store keeper" />
        </div>
        <div class="form-group">
          <label class="form-label">Description</label>
          <input class="form-control" name="description" [(ngModel)]="description" maxlength="500" />
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label"><input type="checkbox" name="active" [(ngModel)]="active" /> Active</label>
          <div class="text-muted" style="font-size:0.8rem;">An inactive role gives its staff no extra access until it is switched back on.</div>
        </div>

        <label class="form-label">What this role can do</label>
        <div class="text-muted mb-2" style="font-size:0.8rem;">
          <strong>View</strong> opens the screens and lists read-only. <strong>Manage</strong> also lets them create and change things.
        </div>
        <div *ngFor="let a of areas" class="form-group" style="margin-bottom:0.9rem;">
          <div class="flex justify-between align-center" style="gap:1rem;">
            <div>
              <div style="font-weight:600;">{{ a.label }}</div>
              <div class="text-muted" style="font-size:0.78rem;">{{ a.description }}</div>
            </div>
            <select class="form-select" style="width:auto; min-width:8rem;" [name]="'area-' + a.code" [ngModel]="levels[a.code]" (ngModelChange)="levels[a.code] = $event">
              <option value="NONE">No access</option>
              <option value="VIEW">View only</option>
              <option value="MANAGE" *ngIf="!a.viewOnly">Manage</option>
            </select>
          </div>
        </div>

        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/staff/roles">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class RoleFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  description = '';
  active = true;
  areas: AccessAreaInfo[] = [];
  levels: Record<string, Level> = {};
  error: string | null = null;

  constructor(private api: RoleApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.id = idParam ? Number(idParam) : null;
    this.api.areas().subscribe({
      next: (areas) => {
        this.areas = areas;
        for (const a of areas) this.levels[a.code] = 'NONE';
        if (this.id) this.loadRole();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load access areas'),
    });
  }

  private loadRole(): void {
    this.api.list().subscribe({
      next: (roles) => {
        const role = roles.find((r) => r.id === this.id);
        if (!role) {
          this.error = 'Role not found';
          return;
        }
        this.name = role.name;
        this.description = role.description ?? '';
        this.active = role.active;
        for (const a of this.areas) {
          this.levels[a.code] = role.grants.includes(a.code + '_MANAGE') ? 'MANAGE' : role.grants.includes(a.code + '_VIEW') ? 'VIEW' : 'NONE';
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load role'),
    });
  }

  private grants(): string[] {
    return this.areas.filter((a) => this.levels[a.code] !== 'NONE').map((a) => `${a.code}_${this.levels[a.code]}`);
  }

  submit(): void {
    this.error = null;
    const payload = { name: this.name.trim(), description: this.description.trim() || null, active: this.active, grants: this.grants() };
    const request = this.id ? this.api.update(this.id, payload) : this.api.create(payload);
    request.subscribe({
      next: () => this.router.navigate(['/admin/staff/roles']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save role'),
    });
  }
}
