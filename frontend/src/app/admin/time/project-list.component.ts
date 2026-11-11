import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ProjectSummary, TimeApiService, TimeSummary } from '../../core/api/time-api.service';

import { T } from '../../core/i18n/t';
/** Time Tracking > Projects: every project with its hours against budget and the time waiting to be billed. */
@Component({
  selector: 'app-time-project-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Projects' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/time/projects/new">{{ '+ New project' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid mb-3" *ngIf="s">
        <div class="stat-tile" [class.warn]="s.pendingApprovals > 0"><div class="stat-label">{{ 'Waiting for approval' | translate }}</div><div class="stat-value">{{ s.pendingApprovals }}</div>
          <a routerLink="/admin/time/approvals" style="font-size:0.8rem;">{{ 'Review' | translate }}</a></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Hours this month' | translate }}</div><div class="stat-value">{{ s.hoursThisMonth | number: '1.0-2' }}</div>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} h billable' | translate: { p0: (s.billableHoursThisMonth | number: '1.0-2') } }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Ready to invoice' | translate }}</div><div class="stat-value">{{ s.unbilledAmount | number: '1.0-2' }}</div></div>
        <div class="stat-tile" [class.warn]="s.overBudgetProjects > 0"><div class="stat-label">{{ 'Active projects' | translate }}</div><div class="stat-value">{{ s.activeProjects }}</div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="s.overBudgetProjects">{{ '{p0} over budget' | translate: { p0: (s.overBudgetProjects) } }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">{{ 'Active and completed' | translate }}</option>
          <option value="ACTIVE">{{ 'Active' | translate }}</option><option value="COMPLETED">{{ 'Completed' | translate }}</option><option value="ARCHIVED">{{ 'Archived' | translate }}</option>
        </select>
        <input class="form-control" style="width:16rem;" [placeholder]="'Search project or customer' | translate" name="q" [(ngModel)]="search" />
      </div>

      <table class="table-sm">
        <thead><tr><th>{{ 'Project' | translate }}</th><th>{{ 'Customer' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Hours' | translate }}</th><th class="text-end">{{ 'Budget' | translate }}</th><th style="min-width:9rem;">{{ 'Used' | translate }}</th><th class="text-end">{{ 'Ready to invoice' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of visible()">
            <td><a [routerLink]="['/admin/time/projects', p.id]">{{ p.name }}</a>
              <span class="text-muted" *ngIf="p.code"> · {{ p.code }}</span>
              <span class="badge badge-secondary" *ngIf="p.sourceType !== 'MANUAL'" style="margin-left:0.3rem;">{{ p.sourceType }}</span>
              <span class="badge badge-secondary" *ngIf="p.restrictToTeam" style="margin-left:0.3rem;" [title]="'Only the project team can log time' | translate">{{ 'team only' | translate }}</span>
              <span class="badge badge-warning" *ngIf="p.blockOverBudget" style="margin-left:0.3rem;" [title]="'Time over the budget is refused' | translate">{{ 'stops at budget' | translate }}</span></td>
            <td>{{ p.customerName || '—' }}</td>
            <td><span class="badge" [class.badge-success]="p.status === 'ACTIVE'" [class.badge-secondary]="p.status !== 'ACTIVE'">{{ p.status }}</span></td>
            <td class="text-end">{{ p.hoursLogged | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.budgetHours != null ? (p.budgetHours | number: '1.0-2') : '—' }}</td>
            <td>
              <div *ngIf="p.percentOfBudget != null" style="background:#e5e7eb; border-radius:4px; height:8px; overflow:hidden;">
                <div [style.width.%]="min100(p.percentOfBudget)" [style.background]="p.percentOfBudget > 100 ? '#c0392b' : p.percentOfBudget >= warnAt ? '#e6a700' : '#2e9e5b'" style="height:8px;"></div>
              </div>
              <span style="font-size:0.75rem;" *ngIf="p.percentOfBudget != null" [style.color]="p.percentOfBudget > 100 ? '#c0392b' : p.percentOfBudget >= warnAt ? '#e6a700' : ''">{{ p.percentOfBudget | number: '1.0-0' }}%<ng-container *ngIf="p.percentOfBudget > 100"> {{ 'over budget' | translate }}</ng-container></span>
              <span *ngIf="p.percentOfBudget == null" class="text-muted">—</span>
            </td>
            <td class="text-end">{{ p.unbilledAmount | number: '1.0-2' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!visible().length">{{ 'No projects yet. Create one, then everyone can log time on it.' | translate }}</p>
    </app-shell>
  `,
})
export class TimeProjectListComponent implements OnInit {
  projects: ProjectSummary[] = [];
  s: TimeSummary | null = null;
  status = '';
  search = '';
  error: string | null = null;
  warnAt = 80;

  constructor(private api: TimeApiService) {}

  ngOnInit(): void {
    this.load();
    this.api.settings().subscribe({ next: (c) => (this.warnAt = c.budgetWarnPercent > 0 ? c.budgetWarnPercent : 101), error: () => {} });
    this.api.summary().subscribe({ next: (s) => (this.s = s), error: () => {} });
  }

  load(): void {
    this.api.projects(this.status || undefined, this.status === 'ARCHIVED').subscribe({
      next: (p) => (this.projects = p),
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the projects')),
    });
  }

  visible(): ProjectSummary[] {
    const q = this.search.trim().toLowerCase();
    return this.projects.filter((p) => !q || p.name.toLowerCase().includes(q) || (p.customerName ?? '').toLowerCase().includes(q) || (p.code ?? '').toLowerCase().includes(q));
  }

  min100(v: number): number {
    return Math.min(100, v);
  }
}
