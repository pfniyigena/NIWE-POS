import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCategoryApiService, HelpDeskTicketApiService } from '../core/api/helpdesk-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, HelpDeskCategory, HelpDeskPriority, HelpDeskStatus, HelpDeskTicket, HelpDeskTicketType } from '../core/models';
import { hdDistance, hdPriorityClass, hdStatusClass, hdStatusLabel, hdWorstSla } from './helpdesk-ui';

import { T } from '../core/i18n/t';
/**
 * Admin > Help Desk > Tickets — the unified list for both ticket types, with ready-made views
 * (active, mine, unassigned, overdue, due soon, waiting, unanswered), a tag filter, deadline
 * badges, and a bulk bar for acting on several tickets at once.
 */
@Component({
  selector: 'app-helpdesk-ticket-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Help Desk tickets' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/overview">{{ 'Overview' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/canned">{{ 'Saved replies' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/helpdesk/categories">{{ 'Categories' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/helpdesk/tickets/new">{{ '+ New ticket' | translate }}</a>
        </div>
      </div>

      <div class="flex gap-2 mb-3 hd-views" style="flex-wrap:wrap;">
        <button type="button" *ngFor="let v of views" class="btn btn-sm" [ngClass]="view === v.key ? 'btn-primary' : 'btn-outline-primary'" (click)="setView(v.key)">{{ v.label }}</button>
      </div>

      <div class="card mb-3" style="padding:0.9rem;">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <input class="form-control" style="max-width:240px;" [placeholder]="'Search ticket #, subject, requester, tag…' | translate" name="q" [(ngModel)]="q" (ngModelChange)="load()" />
          <input class="form-control hd-tag-filter" style="max-width:140px;" [placeholder]="'Tag' | translate" name="tag" [(ngModel)]="tag" (ngModelChange)="load()" />
          <select class="form-select" style="max-width:150px;" name="type" [(ngModel)]="type" (ngModelChange)="load()">
            <option [ngValue]="null">{{ 'All types' | translate }}</option>
            <option value="CUSTOMER">{{ 'Customer' | translate }}</option>
            <option value="INTERNAL">{{ 'Internal' | translate }}</option>
          </select>
          <select class="form-select" style="max-width:190px;" name="status" [(ngModel)]="status" (ngModelChange)="load()">
            <option [ngValue]="null">{{ 'All statuses' | translate }}</option>
            <option value="OPEN">{{ 'Open' | translate }}</option>
            <option value="IN_PROGRESS">{{ 'In progress' | translate }}</option>
            <option value="WAITING">{{ 'Waiting for customer' | translate }}</option>
            <option value="RESOLVED">{{ 'Resolved' | translate }}</option>
            <option value="CLOSED">{{ 'Closed' | translate }}</option>
            <option value="MERGED">{{ 'Merged' | translate }}</option>
          </select>
          <select class="form-select" style="max-width:150px;" name="priority" [(ngModel)]="priority" (ngModelChange)="load()">
            <option [ngValue]="null">{{ 'All priorities' | translate }}</option>
            <option value="LOW">{{ 'Low' | translate }}</option>
            <option value="MEDIUM">{{ 'Medium' | translate }}</option>
            <option value="HIGH">{{ 'High' | translate }}</option>
            <option value="URGENT">{{ 'Urgent' | translate }}</option>
          </select>
          <select class="form-select" style="max-width:170px;" name="categoryId" [(ngModel)]="categoryId" (ngModelChange)="load()">
            <option [ngValue]="null">{{ 'All categories' | translate }}</option>
            <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
          </select>
          <select class="form-select" style="max-width:170px;" name="assignedToUserId" [(ngModel)]="assignedToUserId" (ngModelChange)="load()">
            <option [ngValue]="null">{{ 'All assignees' | translate }}</option>
            <option [ngValue]="-1">{{ 'Unassigned' | translate }}</option>
            <option *ngFor="let u of users" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
      </div>

      <div class="card mb-3 hd-bulk" style="padding:0.75rem;" *ngIf="selected.size > 0">
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <strong>{{ '{p0} selected' | translate: { p0: (selected.size) } }}</strong>
          <select class="form-select" style="max-width:190px;" name="bulkAssignee" [(ngModel)]="bulkAssignee">
            <option [ngValue]="undefined">{{ 'Assign to…' | translate }}</option>
            <option [ngValue]="null">{{ '— Unassigned —' | translate }}</option>
            <option *ngFor="let u of users" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
          <button type="button" class="btn btn-sm btn-outline-primary" [disabled]="busy || bulkAssignee === undefined" (click)="bulk('ASSIGN')">{{ 'Assign' | translate }}</button>
          <select class="form-select" style="max-width:150px;" name="bulkPriority" [(ngModel)]="bulkPriority">
            <option [ngValue]="null">{{ 'Priority…' | translate }}</option>
            <option value="LOW">{{ 'Low' | translate }}</option>
            <option value="MEDIUM">{{ 'Medium' | translate }}</option>
            <option value="HIGH">{{ 'High' | translate }}</option>
            <option value="URGENT">{{ 'Urgent' | translate }}</option>
          </select>
          <button type="button" class="btn btn-sm btn-outline-primary" [disabled]="busy || !bulkPriority" (click)="bulk('PRIORITY')">{{ 'Set priority' | translate }}</button>
          <input class="form-control" style="max-width:130px;" [placeholder]="'Tag' | translate" name="bulkTag" [(ngModel)]="bulkTag" />
          <button type="button" class="btn btn-sm btn-outline-primary" [disabled]="busy || !bulkTag.trim()" (click)="bulk('TAG')">{{ 'Add tag' | translate }}</button>
          <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="busy" (click)="bulk('START')">{{ 'Start' | translate }}</button>
          <button type="button" class="btn btn-sm btn-outline-secondary" [disabled]="busy" (click)="bulk('WAIT')">{{ 'Wait for customer' | translate }}</button>
          <button type="button" class="btn btn-sm btn-outline-secondary hd-bulk-close" [disabled]="busy" (click)="bulk('CLOSE')">{{ 'Close' | translate }}</button>
          <button type="button" class="btn btn-link btn-sm" (click)="clearSelection()">{{ 'Clear' | translate }}</button>
        </div>
        <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="skippedText">{{ skippedText }}</div>
      </div>

      <table class="table-sm">
        <thead>
          <tr>
            <th style="width:30px;"><input type="checkbox" name="all" [checked]="allSelected" (change)="toggleAll($event)" /></th>
            <th>{{ 'Ticket #' | translate }}</th>
            <th>{{ 'Subject' | translate }}</th>
            <th>{{ 'Requester' | translate }}</th>
            <th>{{ 'Priority' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th>{{ 'Deadline' | translate }}</th>
            <th>{{ 'Assigned to' | translate }}</th>
            <th>{{ 'Created' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of filteredTickets">
            <td><input type="checkbox" [checked]="selected.has(t.id)" (change)="toggle(t.id)" /></td>
            <td>{{ t.ticketNumber }}</td>
            <td>
              {{ t.subject }}
              <span class="badge badge-outline" style="margin-left:0.25rem;" *ngIf="t.type === 'INTERNAL'">{{ 'Internal' | translate }}</span>
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="t.tags.length">{{ t.tags.join(' · ') }}</div>
            </td>
            <td>{{ t.requesterName || t.customerName || '—' }}</td>
            <td><span class="badge" [ngClass]="priorityClass(t.priority)">{{ t.priority }}</span></td>
            <td><span class="badge" [ngClass]="statusClass(t.status)">{{ statusLabel(t.status) }}</span></td>
            <td>
              <ng-container *ngIf="worst(t) as w"><span class="badge" [ngClass]="w.cls">{{ w.label }}</span></ng-container>
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="t.resolutionDueAt && (t.status === 'OPEN' || t.status === 'IN_PROGRESS')">{{ distance(t.resolutionDueAt) }}</div>
              <span *ngIf="!t.resolutionDueAt">—</span>
            </td>
            <td>{{ t.assignedToUserFullName || '—' }}</td>
            <td>{{ t.createdAt | date: 'mediumDate' }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/helpdesk/tickets', t.id]">{{ 'View' | translate }}</a></td>
          </tr>
          <tr *ngIf="filteredTickets.length === 0">
            <td colspan="10" class="text-muted">{{ 'No tickets match these filters.' | translate }}</td>
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
  notice: string | null = null;
  busy = false;

  views = [
    { key: 'active', label: 'Active' },
    { key: 'mine', label: 'Mine' },
    { key: 'unassigned', label: 'Unassigned' },
    { key: 'unanswered', label: 'Unanswered' },
    { key: 'overdue', label: 'Overdue' },
    { key: 'due-soon', label: 'Due soon' },
    { key: 'waiting', label: 'Waiting' },
    { key: '', label: 'All' },
  ];
  view = 'active';

  q = '';
  tag = '';
  type: HelpDeskTicketType | null = null;
  status: HelpDeskStatus | null = null;
  priority: HelpDeskPriority | null = null;
  categoryId: number | null = null;
  /** -1 is the sentinel "Unassigned" option — see filteredTickets' own handling. */
  assignedToUserId: number | null = null;

  selected = new Set<number>();
  bulkAssignee: number | null | undefined = undefined;
  bulkPriority: HelpDeskPriority | null = null;
  bulkTag = '';
  skippedText = '';

  constructor(
    private ticketApi: HelpDeskTicketApiService,
    private categoryApi: HelpDeskCategoryApiService,
    private userApi: UserApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const v = this.route.snapshot.queryParamMap.get('view');
    if (v !== null) this.view = v;
    this.categoryApi.listAll().subscribe({ next: (c) => (this.categories = c) });
    this.userApi.list().subscribe({ next: (u) => (this.users = u) });
    this.load();
  }

  setView(key: string): void {
    this.view = key;
    this.status = null;
    this.load();
  }

  load(): void {
    this.error = null;
    // "Unassigned" (-1) is filtered client-side below rather than sent to the backend, which has no such sentinel of its own.
    const assignedToUserId = this.assignedToUserId != null && this.assignedToUserId > 0 ? this.assignedToUserId : null;
    this.ticketApi
      .search({
        q: this.q || null,
        tag: this.tag.trim() || null,
        view: this.view || null,
        type: this.type,
        status: this.status,
        priority: this.priority,
        categoryId: this.categoryId,
        assignedToUserId,
      })
      .subscribe({
        next: (t) => {
          this.tickets = t;
          const ids = new Set(t.map((x) => x.id));
          this.selected.forEach((id) => { if (!ids.has(id)) this.selected.delete(id); });
        },
        error: (err) => (this.error = err?.error?.message ?? T('Failed to load tickets')),
      });
  }

  get filteredTickets(): HelpDeskTicket[] {
    if (this.assignedToUserId === -1) {
      return this.tickets.filter((t) => t.assignedToUserId == null);
    }
    return this.tickets;
  }

  get allSelected(): boolean {
    const rows = this.filteredTickets;
    return rows.length > 0 && rows.every((t) => this.selected.has(t.id));
  }

  toggle(id: number): void {
    if (this.selected.has(id)) this.selected.delete(id);
    else this.selected.add(id);
  }

  toggleAll(ev: Event): void {
    const on = (ev.target as HTMLInputElement).checked;
    this.filteredTickets.forEach((t) => (on ? this.selected.add(t.id) : this.selected.delete(t.id)));
  }

  clearSelection(): void {
    this.selected.clear();
    this.skippedText = '';
  }

  bulk(action: string): void {
    if (this.selected.size === 0) return;
    this.error = null;
    this.notice = null;
    this.skippedText = '';
    this.busy = true;
    this.ticketApi
      .bulk({
        ids: Array.from(this.selected),
        action,
        assignedToUserId: action === 'ASSIGN' ? (this.bulkAssignee ?? null) : null,
        priority: action === 'PRIORITY' ? this.bulkPriority : null,
        tag: action === 'TAG' ? this.bulkTag.trim() : null,
      })
      .subscribe({
        next: (r) => {
          this.busy = false;
          this.notice = r.done + ' ticket(s) updated' + (r.skipped.length ? ', ' + r.skipped.length + ' skipped.' : '.');
          this.skippedText = r.skipped.map((s) => (s.ticketNumber || '#' + s.id) + ': ' + s.reason).join('  ·  ');
          this.selected.clear();
          if (action === 'TAG') this.bulkTag = '';
          this.load();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? T('Could not update the tickets');
        },
      });
  }

  priorityClass = hdPriorityClass;
  statusClass = hdStatusClass;
  statusLabel = hdStatusLabel;
  worst = hdWorstSla;
  distance = hdDistance;
}
