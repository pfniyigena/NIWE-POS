import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { CashierSessionApiService } from '../core/api/cashier-session-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, CashierSession, RegisterSession } from '../core/models';

/**
 * Admin-only oversight of every till shift (register session) across the store — the plain
 * "Sessions" screen shows the same rows to any cashier for opening/closing their own till; this
 * adds a status filter and each shift's sales total inline, without an extra click into a report.
 */
@Component({
  selector: 'app-shift-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Shifts</h3>
        <select class="form-select" style="width:auto;" [(ngModel)]="statusFilter">
          <option value="ALL">All shifts</option>
          <option value="OPEN">Open only</option>
          <option value="CLOSED">Closed only</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>Register</th>
            <th>Cashier</th>
            <th>Opened</th>
            <th>Closed</th>
            <th>Status</th>
            <th class="text-end">Sales</th>
            <th class="text-end">Variance</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let s of filteredShifts()">
            <tr>
              <td>{{ s.registerName }}</td>
              <td>{{ s.cashierName }}</td>
              <td>{{ s.openedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
              <td>{{ s.closedAt ? (s.closedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}</td>
              <td>
                <span class="badge" [class.badge-success]="s.status === 'OPEN'" [class.badge-secondary]="s.status !== 'OPEN'">{{ s.status }}</span>
              </td>
              <td class="text-end">{{ s.saleCount }} &middot; {{ s.grossSales | number: '1.2-2' }}</td>
              <td class="text-end">{{ s.cashVariance != null ? (s.cashVariance | number: '1.2-2') : '—' }}</td>
              <td class="flex gap-2">
                <a *ngIf="s.status === 'OPEN'" [routerLink]="['/sessions', s.id, 'close']">Force close</a>
                <a *ngIf="s.status === 'CLOSED'" [routerLink]="['/sessions', s.id, 'report']">Report</a>
                <button class="btn btn-sm btn-outline-secondary" (click)="toggleCashiers(s.id)">
                  {{ expandedShiftId === s.id ? '▾' : '▸' }} Cashiers
                </button>
              </td>
            </tr>
            <tr *ngIf="expandedShiftId === s.id">
              <td colspan="8" style="padding:0.5rem 1rem; background:rgba(255,255,255,0.03);">
                <div class="text-muted" *ngIf="!cashierSessionsByShift[s.id]">Loading…</div>
                <div
                  class="flex justify-between align-center mb-2"
                  *ngIf="cashierSessionsByShift[s.id] as sessions"
                >
                  <div class="flex gap-2 align-center" *ngIf="s.status === 'OPEN'">
                    <select class="form-select form-select-sm" style="width:auto;" [(ngModel)]="addCashierId[s.id]">
                      <option [ngValue]="null" disabled>Add cashier…</option>
                      <option *ngFor="let u of activeStaff" [ngValue]="u.id">{{ u.fullName }}</option>
                    </select>
                    <button
                      class="btn btn-sm btn-outline-primary"
                      [disabled]="!addCashierId[s.id]"
                      (click)="addCashier(s.id)"
                      title="Gives this staff member their own cashier session on this shift, no PIN needed"
                    >
                      + Add cashier
                    </button>
                  </div>
                  <span *ngIf="s.status !== 'OPEN'"></span>
                  <button
                    class="btn btn-sm btn-outline-danger"
                    (click)="closeAllSessions(s.id)"
                    [disabled]="!sessions.some(hasOpenSession)"
                    title="Ends every open cashier session on this shift — the shift itself stays open"
                  >
                    Close all sessions
                  </button>
                </div>
                <table class="table-sm" *ngIf="cashierSessionsByShift[s.id] as sessions">
                  <thead>
                    <tr>
                      <th>Cashier</th>
                      <th>Joined</th>
                      <th>Ended</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let cs of sessions">
                      <td>{{ cs.cashierName }}</td>
                      <td>{{ cs.startedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                      <td>{{ cs.endedAt ? (cs.endedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}</td>
                      <td>
                        <span class="badge" [class.badge-success]="cs.status === 'OPEN'" [class.badge-secondary]="cs.status !== 'OPEN'">{{ cs.status }}</span>
                      </td>
                    </tr>
                    <tr *ngIf="sessions.length === 0">
                      <td colspan="4" class="text-muted">No cashier sessions recorded on this shift.</td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="filteredShifts().length === 0">
            <td colspan="8" class="text-muted">No shifts match this filter.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ShiftListComponent implements OnInit {
  shifts: RegisterSession[] = [];
  statusFilter: 'ALL' | 'OPEN' | 'CLOSED' = 'ALL';
  error: string | null = null;

  /** Which shift's cashier-session breakdown is expanded, and what's been loaded for it (lazily, per row). */
  expandedShiftId: number | null = null;
  cashierSessionsByShift: Record<number, CashierSession[]> = {};

  /** Staff picker for "+ Add cashier", keyed by shift id so each expanded row keeps its own selection. */
  activeStaff: AppUser[] = [];
  addCashierId: Record<number, number | null> = {};

  constructor(
    private registerApi: RegisterApiService,
    private cashierSessionApi: CashierSessionApiService,
    private userApi: UserApiService
  ) {}

  ngOnInit(): void {
    this.registerApi.listSessions().subscribe({
      next: (s) => (this.shifts = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load shifts'),
    });
    this.userApi.list().subscribe({
      next: (users) => (this.activeStaff = users.filter((u) => u.active)),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load staff list'),
    });
  }

  filteredShifts(): RegisterSession[] {
    if (this.statusFilter === 'ALL') return this.shifts;
    return this.shifts.filter((s) => s.status === this.statusFilter);
  }

  /** Shows which cashiers worked a shift (their own PIN-joined sessions) — loaded on demand, once per shift. */
  toggleCashiers(shiftId: number): void {
    if (this.expandedShiftId === shiftId) {
      this.expandedShiftId = null;
      return;
    }
    this.expandedShiftId = shiftId;
    if (this.cashierSessionsByShift[shiftId]) return;
    this.loadCashierSessions(shiftId);
  }

  private loadCashierSessions(shiftId: number): void {
    this.cashierSessionApi.listForShift(shiftId).subscribe({
      next: (sessions) => (this.cashierSessionsByShift = { ...this.cashierSessionsByShift, [shiftId]: sessions }),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load cashier sessions'),
    });
  }

  hasOpenSession(cs: CashierSession): boolean {
    return cs.status === 'OPEN';
  }

  /**
   * Gives the chosen staff member their own cashier session on this shift directly — the
   * admin-side fix for "opened a session for one person, now there's no way to add another
   * cashier" (no PIN needed, unlike the terminal's "Switch cashier").
   */
  addCashier(shiftId: number): void {
    const cashierId = this.addCashierId[shiftId];
    if (!cashierId) return;
    this.error = null;
    this.cashierSessionApi.assign(shiftId, cashierId).subscribe({
      next: () => {
        this.addCashierId[shiftId] = null;
        this.loadCashierSessions(shiftId);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to add cashier'),
    });
  }

  /** Admin bulk action: ends every open cashier session on this shift — the shift (till) itself stays open. */
  closeAllSessions(shiftId: number): void {
    if (!confirm('End every open cashier session on this shift? Any unfinished carts will be discarded.')) return;
    this.error = null;
    this.cashierSessionApi.closeAllForShift(shiftId).subscribe({
      next: () => this.loadCashierSessions(shiftId),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to close sessions'),
    });
  }
}
