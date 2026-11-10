import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskTicketApiService } from '../core/api/helpdesk-api.service';
import { HelpDeskSummary } from '../core/models';
import { hdDistance, hdPriorityClass, hdStatusLabel } from './helpdesk-ui';

/** Help Desk > Overview — what needs attention right now, workload per agent, how fast and how well the desk is answering. */
@Component({
  selector: 'app-helpdesk-overview',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Help Desk overview' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/reports">{{ 'Reports' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/tickets">{{ 'All tickets' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="s">
        <div class="stat-grid mb-3">
          <a class="stat-tile" routerLink="/admin/helpdesk/tickets" [queryParams]="{ view: 'unanswered' }" style="text-decoration:none; color:inherit;"><div class="stat-label">{{ 'Unanswered' | translate }}</div><div class="stat-value">{{ s.unanswered }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'No reply sent yet' | translate }}</div></a>
          <a class="stat-tile" [class.warn]="s.overdue > 0" routerLink="/admin/helpdesk/tickets" [queryParams]="{ view: 'overdue' }" style="text-decoration:none; color:inherit;"><div class="stat-label">{{ 'Overdue' | translate }}</div><div class="stat-value">{{ s.overdue }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Past a deadline' | translate }}</div></a>
          <a class="stat-tile" routerLink="/admin/helpdesk/tickets" [queryParams]="{ view: 'due-soon' }" style="text-decoration:none; color:inherit;"><div class="stat-label">{{ 'Due soon' | translate }}</div><div class="stat-value">{{ s.dueSoon }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Inside the warning window' | translate }}</div></a>
          <a class="stat-tile" routerLink="/admin/helpdesk/tickets" [queryParams]="{ view: 'unassigned' }" style="text-decoration:none; color:inherit;"><div class="stat-label">{{ 'Unassigned' | translate }}</div><div class="stat-value">{{ s.unassigned }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Nobody owns them yet' | translate }}</div></a>
        </div>
        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Open / in progress' | translate }}</div><div class="stat-value">{{ s.open }} / {{ s.inProgress }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} waiting for customer · {p1} resolved, not closed' | translate: { p0: (s.waiting), p1: (s.resolvedNotClosed) } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Mine' | translate }}</div><div class="stat-value">{{ s.mine }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Assigned to you' | translate }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Last 7 days' | translate }}</div><div class="stat-value">{{ '{p0} in · {p1} out' | translate: { p0: (s.createdLast7), p1: (s.resolvedLast7) } }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Created and resolved' | translate }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'On time (30 days)' | translate }}</div><div class="stat-value">{{ s.slaCompliancePercent == null ? '—' : (s.slaCompliancePercent | number: '1.0-0') + '%' }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Tickets resolved within their deadline' | translate }}</div></div>
        </div>
        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Avg first reply' | translate }}</div><div class="stat-value">{{ s.avgFirstResponseHours == null ? '—' : (s.avgFirstResponseHours | number: '1.0-1') + ' h' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Avg time to resolve' | translate }}</div><div class="stat-value">{{ s.avgResolutionHours == null ? '—' : (s.avgResolutionHours | number: '1.0-1') + ' h' }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Not counting time waiting for the customer' | translate }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Customer rating' | translate }}</div><div class="stat-value">{{ s.avgRating == null ? '—' : (s.avgRating | number: '1.0-1') + ' / 5' }}</div><div class="text-muted" style="font-size:0.75rem;">{{ '{p0} rating(s) in 30 days' | translate: { p0: (s.ratingsLast30) } }}</div></div>
        </div>

        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
          <div style="flex:1; min-width:280px;">
            <h4 class="mb-2">{{ 'By priority (active tickets)' | translate }}</h4>
            <table class="table-sm mb-3">
              <tbody>
                <tr *ngFor="let p of s.byPriority"><td><span class="badge" [ngClass]="priorityClass(p.priority)">{{ p.priority }}</span></td><td class="text-end">{{ p.count }}</td></tr>
              </tbody>
            </table>
            <h4 class="mb-2">{{ 'How long they have been open' | translate }}</h4>
            <table class="table-sm mb-3">
              <tbody>
                <tr *ngFor="let a of s.byAge"><td>{{ a.label }}</td><td class="text-end">{{ a.count }}</td></tr>
              </tbody>
            </table>
          </div>
          <div style="flex:2; min-width:340px;">
            <h4 class="mb-2">{{ 'Workload' | translate }}</h4>
            <table class="table-sm mb-3">
              <thead><tr><th>{{ 'Agent' | translate }}</th><th class="text-end">{{ 'Active' | translate }}</th><th class="text-end">{{ 'Overdue' | translate }}</th><th class="text-end">{{ 'Resolved (30 days)' | translate }}</th></tr></thead>
              <tbody>
                <tr *ngFor="let a of s.agents"><td>{{ a.name }}</td><td class="text-end">{{ a.open }}</td><td class="text-end">{{ a.overdue }}</td><td class="text-end">{{ a.resolvedLast30 }}</td></tr>
                <tr *ngIf="!s.agents.length"><td colspan="4" class="text-muted">{{ 'No tickets yet.' | translate }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <h4 class="mb-2">{{ 'Needs attention' | translate }}</h4>
        <table class="table-sm hd-attention">
          <thead><tr><th>{{ 'Ticket #' | translate }}</th><th>{{ 'Subject' | translate }}</th><th>{{ 'Priority' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Assigned to' | translate }}</th><th>{{ 'Why' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let a of s.attention">
              <td>{{ a.ticketNumber }}</td><td>{{ a.subject }}</td>
              <td><span class="badge" [ngClass]="priorityClass(a.priority)">{{ a.priority }}</span></td>
              <td>{{ statusLabel(a.status) }}</td><td>{{ a.assignedTo || '—' }}</td>
              <td>{{ a.reason }}<span class="text-muted" *ngIf="a.due"> ({{ distance(a.due) }})</span></td>
              <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/helpdesk/tickets', a.id]">{{ 'View' | translate }}</a></td>
            </tr>
            <tr *ngIf="!s.attention.length"><td colspan="7" class="text-muted">{{ 'Nothing is overdue or waiting for a first reply.' | translate }}</td></tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class HelpDeskOverviewComponent implements OnInit {
  s: HelpDeskSummary | null = null;
  error: string | null = null;

  constructor(private api: HelpDeskTicketApiService) {}

  ngOnInit(): void {
    this.api.summary().subscribe({
      next: (s) => (this.s = s),
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the overview'),
    });
  }

  priorityClass = hdPriorityClass;
  statusLabel = hdStatusLabel;
  distance = hdDistance;
}
