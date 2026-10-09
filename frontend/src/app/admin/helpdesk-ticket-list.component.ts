import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCategoryApiService, HelpDeskTicketApiService } from '../core/api/helpdesk-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, HelpDeskCategory, HelpDeskPriority, HelpDeskStatus, HelpDeskTicket, HelpDeskTicketType } from '../core/models';

/**
 * Admin > Help Desk > Tickets — the unified list for both HelpDeskTicketType values (Fourier, via
 * AskUserQuestion: "One unified list with a Type field"). Filters mirror the fields a real queue
 * needs to triage by: type, status, priority, category, assignee, free-text search.
 */
@Component({
  selector: 'app-helpdesk-ticket-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Help Desk tickets</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/categories">Categories</a>
          <a class="btn btn-primary" routerLink="/admin/helpdesk/tickets/new">+ New ticket</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:0.9rem;">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <input class="form-control" style="max-width:220px;" placeholder="Search ticket #, subject, requester…" name="q" [(ngModel)]="q" (ngModelChange)="load()" />
          <select class="form-select" style="max-width:160px;" name="type" [(ngModel)]="type" (ngModelChange)="load()">
            <option [ngValue]="null">All types</option>
            <option value="CUSTOMER">Customer</option>
            <option value="INTERNAL">Internal</option>
          </select>
          <select class="form-select" style="max-width:160px;" name="status" [(ngModel)]="status" (ngModelChange)="load()">
            <option [ngValue]="null">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </select>
          <select class="form-select" style="max-width:160px;" name="priority" [(ngModel)]="priority" (ngModelChange)="load()">
            <option [ngValue]="null">All priorities</option>
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="URGENT">Urgent</option>
          </select>
          <select class="form-select" style="max-width:180px;" name="categoryId" [(ngModel)]="categoryId" (ngModelChange)="load()">
            <option [ngValue]="null">All categories</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
          </select>
          <select class="form-select" style="max-width:180px;" name="assignedToUserId" [(ngModel)]="assignedToUserId" (ngModelChange)="load()">
            <option [ngValue]="null">All assignees</option>
            <option [ngValue]="-1">Unassigned</option>
            <option *ngFor="let u of users" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
      </div>

      <table class="table-sm">
        <thead>
          <tr>
            <th>Ticket #</th>
            <th>Type</th>
            <th>Subject</th>
            <th>Requester</th>
            <th>Category</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Assigned to</th>
            <th>Created</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of filteredTickets">
            <td>{{ t.ticketNumber }}</td>
            <td><span class="badge" [ngClass]="t.type === 'CUSTOMER' ? 'badge-secondary' : 'badge-outline'">{{ t.type === 'CUSTOMER' ? 'Customer' : 'Internal' }}</span></td>
            <td>{{ t.subject }}</td>
            <td>{{ t.requesterName || t.customerName || '—' }}</td>
            <td>{{ t.categoryName || '—' }}</td>
            <td><span class="badge" [ngClass]="priorityClass(t.priority)">{{ t.priority }}</span></td>
            <td><span class="badge" [ngClass]="statusClass(t.status)">{{ t.status }}</span></td>
            <td>{{ t.assignedToUserFullName || '—' }}</td>
            <td>{{ t.createdAt | date: 'mediumDate' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/helpdesk/tickets', t.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="filteredTickets.length === 0">
            <td colspan="10" class="text-muted">No tickets match these filters.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class HelpDeskTicketListComponent implements OnInit {
  tickets: HelpDeskTicket[] = [];
  categories: HelpDeskCategory[] = [];
  users: AppUser[] = [];
  error: string | null = null;

  q = '';
  type: HelpDeskTicketType | null = null;
  status: HelpDeskStatus | null = null;
  priority: HelpDeskPriority | null = null;
  categoryId: number | null = null;
  /** -1 is the sentinel "Unassigned" option — see filteredTickets' own handling. */
  assignedToUserId: number | null = null;

  constructor(
    private ticketApi: HelpDeskTicketApiService,
    private categoryApi: HelpDeskCategoryApiService,
    private userApi: UserApiService
  ) {}

  ngOnInit(): void {
    this.categoryApi.listAll().subscribe({ next: (c) => (this.categories = c) });
    this.userApi.list().subscribe({ next: (u) => (this.users = u) });
    this.load();
  }

  load(): void {
    this.error = null;
    // "Unassigned" (-1) is filtered client-side below rather than sent to the backend, which has no such sentinel of its own.
    const assignedToUserId = this.assignedToUserId != null && this.assignedToUserId > 0 ? this.assignedToUserId : null;
    this.ticketApi
      .search({
        q: this.q || null,
        type: this.type,
        status: this.status,
        priority: this.priority,
        categoryId: this.categoryId,
        assignedToUserId,
      })
      .subscribe({
        next: (t) => (this.tickets = t),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load tickets'),
      });
  }

  get filteredTickets(): HelpDeskTicket[] {
    if (this.assignedToUserId === -1) {
      return this.tickets.filter((t) => t.assignedToUserId == null);
    }
    return this.tickets;
  }

  priorityClass(p: HelpDeskPriority): string {
    switch (p) {
      case 'URGENT':
        return 'badge-danger';
      case 'HIGH':
        return 'badge-warning';
      case 'MEDIUM':
        return 'badge-secondary';
      default:
        return 'badge-outline';
    }
  }

  statusClass(s: HelpDeskStatus): string {
    switch (s) {
      case 'OPEN':
        return 'badge-warning';
      case 'IN_PROGRESS':
        return 'badge-secondary';
      case 'RESOLVED':
        return 'badge-success';
      case 'CLOSED':
        return 'badge-outline';
      default:
        return 'badge-secondary';
    }
  }
}
