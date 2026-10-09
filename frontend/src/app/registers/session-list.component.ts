import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CashierSessionApiService } from '../core/api/cashier-session-api.service';
import { CashierSession } from '../core/models';

/**
 * Every individual cashier session, not one row per shift — a shift (RegisterSession) can have
 * several cashiers working it at once (see CashierSession's doc comment in models.ts). Open to any
 * logged-in user, but what shows up is scoped by role on the backend (see
 * CashierSessionQueryController): an ADMIN sees and can close any cashier session across every
 * shift without first drilling into which shift it belongs to; a cashier sees only their own
 * sessions and can only close their own (Change: "Cashier does not have access to his session
 * data"). Shift-level oversight (force-close a whole shift, per-shift Z-report) lives on the admin
 * Shifts screen instead — this screen is deliberately just about individual cashier sessions.
 *
 * <p>Task: "We need to differentiate CashierSession Z Report with RegisterSession Z Report" — each
 * row's own "Report" link goes to `cashier-session-report.component.ts` (this one cashier's own
 * start/end time and sales), not the whole shift's Z-report at `session-report.component.ts` (that
 * one's reached from the admin Shifts screen instead, one row per shift).</p>
 */
@Component({
  selector: 'app-session-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Sessions</h3>
        <select class="form-select" style="width:auto;" [(ngModel)]="statusFilter">
          <option value="ALL">All sessions</option>
          <option value="OPEN">Open only</option>
          <option value="CLOSED">Closed only</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>Register / Shift</th>
            <th>Cashier</th>
            <th>Joined</th>
            <th>Ended</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let cs of filteredSessions()">
            <td>{{ cs.registerName }}</td>
            <td>{{ cs.cashierName }}</td>
            <td>{{ cs.startedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ cs.endedAt ? (cs.endedAt | date: 'yyyy-MM-dd HH:mm') : '—' }}</td>
            <td>
              <span class="badge" [class.badge-success]="cs.status === 'OPEN'" [class.badge-secondary]="cs.status !== 'OPEN'">{{ cs.status }}</span>
            </td>
            <td class="flex gap-2">
              <a [routerLink]="['/cashier-sessions', cs.id, 'orders']">Orders</a>
              <a *ngIf="cs.status === 'OPEN' && cs.registerSessionId" [routerLink]="['/pos', cs.registerSessionId]">Terminal</a>
              <a *ngIf="cs.status !== 'OPEN'" [routerLink]="['/cashier-sessions', cs.id, 'report']">Report</a>
              <button *ngIf="cs.status === 'OPEN'" class="btn btn-sm btn-outline-danger" (click)="closeSession(cs)">Close</button>
            </td>
          </tr>
          <tr *ngIf="filteredSessions().length === 0">
            <td colspan="6" class="text-muted">No sessions match this filter.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SessionListComponent implements OnInit {
  sessions: CashierSession[] = [];
  statusFilter: 'ALL' | 'OPEN' | 'CLOSED' = 'ALL';
  error: string | null = null;

  constructor(private cashierSessionApi: CashierSessionApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.cashierSessionApi.listAll().subscribe({
      next: (s) => (this.sessions = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load sessions'),
    });
  }

  filteredSessions(): CashierSession[] {
    if (this.statusFilter === 'ALL') return this.sessions;
    return this.sessions.filter((s) => s.status === this.statusFilter);
  }

  /** Ends this one cashier's session — discards any cart they never checked out, same as the terminal's own "End my session". */
  closeSession(cs: CashierSession): void {
    if (!cs.registerSessionId) return;
    if (!confirm(`End ${cs.cashierName}'s session? Any unfinished cart will be discarded.`)) return;
    this.error = null;
    this.cashierSessionApi.close(cs.registerSessionId, cs.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to close session'),
    });
  }
}
