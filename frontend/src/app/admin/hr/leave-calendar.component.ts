import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { Department, HrApiService, LeaveCalendar, LeaveCalendarDay, LeaveCalendarEntry } from '../../core/api/hr-api.service';

interface CalendarRowView {
  employeeId: number;
  employeeName: string;
  departmentName: string | null;
  cells: (LeaveCalendarEntry | null)[];
  total: number;
}

/** Attendance & Leaves > Team calendar: who is away on which days of a month. */
@Component({
  selector: 'app-leave-calendar',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  styles: [`
    .cal-wrap { overflow-x: auto; max-width: 100%; border: 1px solid var(--border, #3a4156); border-radius: 6px; }
    table.cal { border-collapse: collapse; width: max-content; min-width: 100%; font-size: 0.8rem; }
    table.cal th, table.cal td { border: 1px solid var(--border, #3a4156); padding: 0.2rem 0.3rem; text-align: center; min-width: 1.9rem; }
    table.cal th.name, table.cal td.name { text-align: left; min-width: 11rem; position: sticky; left: 0; background: var(--surface, #1b2030); z-index: 1; white-space: nowrap; }
    table.cal .off { background: rgba(120,120,120,0.14); }
    table.cal .hol { background: rgba(120,90,200,0.2); }
    table.cal .approved { background: #2e9e5b; color: #fff; font-weight: 600; }
    table.cal .pending { background: #f2b53a; color: #222; font-weight: 600; }
    .legend { display: flex; gap: 1rem; flex-wrap: wrap; font-size: 0.8rem; align-items: center; }
    .chip { display: inline-block; width: 1rem; height: 1rem; border-radius: 3px; vertical-align: middle; margin-right: 0.25rem; }
  `],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Team leave calendar' | translate }}</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/leaves">{{ 'Back to leave' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;">
        <button class="btn btn-sm btn-outline-primary cal-prev" type="button" (click)="move(-1)">{{ '‹ Previous' | translate }}</button>
        <strong class="cal-title" style="min-width:9rem; text-align:center;">{{ title }}</strong>
        <button class="btn btn-sm btn-outline-primary cal-next" type="button" (click)="move(1)">{{ 'Next ›' | translate }}</button>
        <button class="btn btn-sm btn-outline-secondary" type="button" (click)="today()">{{ 'This month' | translate }}</button>
        <select class="form-select cal-dept" style="width:auto;" name="cd" [(ngModel)]="departmentId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'All departments' | translate }}</option>
          <option *ngFor="let d of departments" [ngValue]="d.id">{{ d.name }}</option>
        </select>
        <select class="form-select cal-branch" style="width:auto;" name="cb" [(ngModel)]="branchId" (ngModelChange)="load()" *ngIf="branches.length > 1">
          <option [ngValue]="null">{{ 'All branches' | translate }}</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
      </div>
      <div class="legend mb-2">
        <span><span class="chip" style="background:#2e9e5b;"></span>{{ 'Approved' | translate }}</span>
        <span><span class="chip" style="background:#f2b53a;"></span>{{ 'Waiting for a decision' | translate }}</span>
        <span><span class="chip" style="background:rgba(120,120,120,0.3);"></span>{{ 'Weekend / day off' | translate }}</span>
        <span><span class="chip" style="background:rgba(120,90,200,0.35);"></span>{{ 'Public holiday' | translate }}</span>
        <span>{{ '½ = half day' | translate }}</span>
      </div>
      <div class="cal-wrap" *ngIf="calendar">
        <table class="cal no-stack">
          <thead>
            <tr>
              <th class="name">{{ 'Employee' | translate }}</th>
              <th *ngFor="let d of calendar.days" [class.off]="d.weekend" [class.hol]="!!d.holiday" [attr.title]="d.holiday || ''">{{ dayNumber(d) }}</th>
              <th>{{ 'Days' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of rows" class="cal-row">
              <td class="name">{{ r.employeeName }}<span class="text-muted" *ngIf="r.departmentName"> · {{ r.departmentName }}</span></td>
              <td *ngFor="let c of r.cells; let i = index"
                  [class.off]="!c && calendar.days[i].weekend" [class.hol]="!c && !!calendar.days[i].holiday"
                  [class.approved]="c && c.status === 'APPROVED'" [class.pending]="c && c.status === 'PENDING'"
                  [attr.title]="c ? c.leaveTypeName + ' (' + c.status.toLowerCase() + ')' : ''" [attr.data-status]="c ? c.status : null">{{ c ? (c.half ? '½' : '●') : '' }}</td>
              <td>{{ r.total }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-muted" *ngIf="calendar && rows.length === 0">{{ 'Nobody has approved or waiting leave in this month.' | translate }}</p>
      <p class="text-muted">{{ 'Shows approved leave and requests still waiting for a decision. Rejected and cancelled requests are not shown.' | translate }}</p>
    </app-shell>
  `,
})
export class LeaveCalendarComponent implements OnInit, OnDestroy {
  private sub?: Subscription;
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  departmentId: number | null = null;
  branchId: number | null = null;
  departments: Department[] = [];
  branches: Branch[] = [];
  calendar: LeaveCalendar | null = null;
  rows: CalendarRowView[] = [];
  error: string | null = null;
  title = '';

  constructor(private api: HrApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.api.departments().subscribe({ next: (d) => (this.departments = d), error: () => {} });
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.load();
  }

  dayNumber(d: LeaveCalendarDay): number {
    return Number(d.date.slice(8, 10));
  }

  move(by: number): void {
    const d = new Date(this.year, this.month - 1 + by, 1);
    this.year = d.getFullYear();
    this.month = d.getMonth() + 1;
    this.load();
  }

  today(): void {
    this.year = new Date().getFullYear();
    this.month = new Date().getMonth() + 1;
    this.load();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  load(): void {
    // a newer request replaces one still running, so a slow answer can never overwrite a newer one
    this.sub?.unsubscribe();
    this.sub = this.api.leaveCalendar(this.year, this.month, this.departmentId, this.branchId).subscribe({
      next: (c) => {
        this.error = null;
        this.calendar = c;
        this.title = new Date(c.year, c.month - 1, 1).toLocaleString('en-GB', { month: 'long', year: 'numeric' });
        this.rows = c.rows.map((r) => {
          const byDate = new Map<string, LeaveCalendarEntry>();
          r.entries.forEach((e) => byDate.set(e.date, e));
          const cells = c.days.map((d) => byDate.get(d.date) ?? null);
          const total = r.entries.reduce((n, e) => n + (e.half ? 0.5 : 1), 0);
          return { employeeId: r.employeeId, employeeName: r.employeeName, departmentName: r.departmentName, cells, total };
        });
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the calendar'),
    });
  }
}
