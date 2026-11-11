import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { CashierSessionApiService } from '../core/api/cashier-session-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, CashierSession, RegisterSession, RegisterSummary } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

import { T } from '../core/i18n/t';
/**
 * Admin-only oversight of every till shift (register session) across the store — the plain
 * "Sessions" screen shows the same rows to any cashier for opening/closing their own till; this
 * adds a status filter and each shift's sales total inline, without an extra click into a report.
 */
@Component({
  selector: 'app-shift-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Shifts' | translate }}</h3>
      </div>
      <app-list-filter exportPath="/sessions/export" [exportExtra]="extra" [extraActive]="registerId !== ''" [placeholder]="'Search register or cashier' | translate" [statuses]="statuses" statusAll="All shifts" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select sh-register" style="width:auto;" [(ngModel)]="registerId" (ngModelChange)="extraChanged()" name="register" [title]="'Register' | translate">
          <option value="">{{ 'Any register' | translate }}</option>
          <option *ngFor="let r of registers" [value]="r.id">{{ r.name }}</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;">{{ 'Dates filter by the day the shift was opened.' | translate }}</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Register' | translate }}</th>
            <th>{{ 'Z' | translate }}</th>
            <th>{{ 'Cashier' | translate }}</th>
            <th>{{ 'Opened' | translate }}</th>
            <th>{{ 'Closed' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th class="text-end">{{ 'Sales' | translate }}</th>
            <th class="text-end">{{ 'Variance' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let s of list.rows">
            <tr>
              <td>{{ s.registerName }}</td>
              <td>{{ s.zNumber ? '#' + s.zNumber : '—' }}</td>
              <td>{{ s.cashierName }}</td>
              <td>{{ s.openedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
              <td>{{ s.closedAt ? (s.closedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}</td>
              <td>
                <span class="badge" [class.badge-success]="s.status === 'OPEN'" [class.badge-secondary]="s.status !== 'OPEN'">{{ s.status }}</span>
              </td>
              <td class="text-end">{{ s.saleCount }} &middot; {{ s.grossSales | number: '1.2-2' }}</td>
              <td class="text-end">{{ s.cashVariance != null ? (s.cashVariance | number: '1.2-2') : '—' }}</td>
              <td class="flex gap-2">
                <a *ngIf="s.status === 'OPEN'" [routerLink]="['/sessions', s.id, 'close']">{{ 'Force close' | translate }}</a>
                <a *ngIf="s.status === 'OPEN'" [routerLink]="['/cash-control', s.id]">{{ 'Cash' | translate }}</a>
                <a *ngIf="s.status === 'OPEN'" [routerLink]="['/sessions', s.id, 'x-report']">{{ 'X report' | translate }}</a>
                <a *ngIf="s.status === 'CLOSED'" [routerLink]="['/sessions', s.id, 'z-report']">{{ 'Z report' | translate }}</a>
                <button class="btn btn-sm btn-outline-secondary" (click)="toggleCashiers(s.id)">
                  {{ '{p0} Cashiers' | translate: { p0: (expandedShiftId === s.id ? '▾' : '▸') } }}
                </button>
              </td>
            </tr>
            <tr *ngIf="expandedShiftId === s.id">
              <td colspan="9" style="padding:0.5rem 1rem; background:rgba(255,255,255,0.03);">
                <div class="text-muted" *ngIf="!cashierSessionsByShift[s.id]">{{ 'Loading…' | translate }}</div>
                <div
                  class="flex justify-between align-center mb-2"
                  *ngIf="cashierSessionsByShift[s.id] as sessions"
                >
                  <div class="flex gap-2 align-center" *ngIf="s.status === 'OPEN'">
                    <select class="form-select form-select-sm" style="width:auto;" [(ngModel)]="addCashierId[s.id]">
                      <option [ngValue]="null" disabled>{{ 'Add cashier…' | translate }}</option>
                      <option *ngFor="let u of activeStaff" [ngValue]="u.id">{{ u.fullName }}</option>
                    </select>
                    <button
                      class="btn btn-sm btn-outline-primary"
                      [disabled]="!addCashierId[s.id]"
                      (click)="addCashier(s.id)"
                      [title]="'Gives this staff member their own cashier session on this shift, no PIN needed' | translate"
                    >
                      {{ '+ Add cashier' | translate }}
                    </button>
                  </div>
                  <span *ngIf="s.status !== 'OPEN'"></span>
                  <button
                    class="btn btn-sm btn-outline-danger"
                    (click)="closeAllSessions(s.id)"
                    [disabled]="!sessions.some(hasOpenSession)"
                    [title]="'Ends every open cashier session on this shift — the shift itself stays open' | translate"
                  >
                    {{ 'Close all sessions' | translate }}
                  </button>
                </div>
                <table class="table-sm" *ngIf="cashierSessionsByShift[s.id] as sessions">
                  <thead>
                    <tr>
                      <th>{{ 'Cashier' | translate }}</th>
                      <th>{{ 'Joined' | translate }}</th>
                      <th>{{ 'Ended' | translate }}</th>
                      <th>{{ 'Status' | translate }}</th>
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
                      <td colspan="4" class="text-muted">{{ 'No cashier sessions recorded on this shift.' | translate }}</td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || registerId ? ('No shifts match these filters.' | translate) : ('No shifts yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ShiftListComponent implements OnInit {
  statuses = [{ value: 'OPEN', label: 'Open only' }, { value: 'CLOSED', label: 'Closed only' }];
  registers: RegisterSummary[] = [];
  registerId = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<RegisterSession>((f, p, sz) => this.registerApi.sessionsPage(f, p, sz, { registerId: this.registerId }), 'Failed to load shifts');
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
    this.list.load();
    this.registerApi.listRegisters().subscribe({ next: (r) => (this.registers = r), error: () => {} });
    this.userApi.list().subscribe({
      next: (users) => (this.activeStaff = users.filter((u) => u.active)),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load staff list')),
    });
  }

  extraChanged(): void {
    this.extra = { registerId: this.registerId };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.registerId = '';
    this.extra = {};
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
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load cashier sessions')),
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
      error: (err) => (this.error = err?.error?.message ?? T('Failed to add cashier')),
    });
  }

  /** Admin bulk action: ends every open cashier session on this shift — the shift (till) itself stays open. */
  closeAllSessions(shiftId: number): void {
    if (!confirm(T('End every open cashier session on this shift? Any unfinished carts will be discarded.'))) return;
    this.error = null;
    this.cashierSessionApi.closeAllForShift(shiftId).subscribe({
      next: () => this.loadCashierSessions(shiftId),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to close sessions')),
    });
  }
}
