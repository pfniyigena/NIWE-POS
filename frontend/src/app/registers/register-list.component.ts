import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { AuthService } from '../core/auth.service';
import { RegisterSummary } from '../core/models';

@Component({
  selector: 'app-register-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Registers</h3>
        <a *ngIf="auth.canManage('SALES')" class="btn btn-primary" routerLink="/registers/new">+ New register</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Location</th>
            <th>Warehouse</th>
            <th>Location</th>
            <th>Float</th>
            <th>Cashiers</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of registers">
            <td>{{ r.name }}</td>
            <td>{{ r.location }}</td>
            <td>{{ r.warehouseName || '— (primary)' }}</td>
            <td>{{ r.defaultLocationName || '— (default)' }}</td>
            <td class="rl-float">{{ r.defaultFloat !== null ? (r.defaultFloat | number: '1.0-2') : '—' }}<span class="text-muted" *ngIf="r.maxFloat !== null"> (max {{ r.maxFloat | number: '1.0-2' }})</span></td>
            <td class="rl-cashiers">{{ r.allowedCashierNames.length ? r.allowedCashierNames.join(', ') : 'Anyone' }}</td>
            <td>
              <span class="badge badge-warning rl-off" *ngIf="!r.active">Switched off</span>
              <span class="badge badge-success" *ngIf="r.active && r.openSessionId">Open session</span>
              <span class="badge badge-secondary" *ngIf="r.active && !r.openSessionId">Closed</span>
            </td>
            <td class="flex gap-2">
              <a *ngIf="r.openSessionId" class="btn btn-sm btn-success" [routerLink]="['/pos', r.openSessionId]">Go to terminal</a>
              <a *ngIf="r.active && !r.openSessionId && auth.canManage('SALES')" class="btn btn-sm btn-outline-primary" [routerLink]="['/registers', r.id, 'open-session']">Open session</a>
              <a *ngIf="auth.canManage('SALES')" [routerLink]="['/registers', r.id, 'edit']">Edit</a>
            </td>
          </tr>
          <tr *ngIf="registers.length === 0">
            <td colspan="8" class="text-muted">No registers yet.</td>
          </tr>
        </tbody>
      </table>
      <p style="margin-top:1rem;">
        <a routerLink="/sessions">View sessions &rarr;</a>
      </p>
    </app-shell>
  `,
})
export class RegisterListComponent implements OnInit {
  registers: RegisterSummary[] = [];
  error: string | null = null;

  constructor(private registerApi: RegisterApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.registerApi.listRegisters().subscribe({
      next: (r) => (this.registers = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load registers'),
    });
  }
}
