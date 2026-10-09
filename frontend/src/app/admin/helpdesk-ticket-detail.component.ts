import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskTicketApiService } from '../core/api/helpdesk-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, HelpDeskComment, HelpDeskTicket } from '../core/models';
import { openPdfForPrint } from '../core/download-file';

/**
 * Admin > Help Desk > Tickets > detail — "from creation through resolution" (Fourier's own
 * phrasing) happens entirely on this one screen: assign/reassign, move through the status actions
 * (start/resolve/close/reopen — see the backend HelpDeskTicketService's own javadoc for why these
 * are named actions rather than a generic status setter), and the comment timeline (internal notes
 * vs. updates visible to the requester on the public "check my ticket" lookup page).
 */
@Component({
  selector: 'app-helpdesk-ticket-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Ticket{{ ticket ? ' — ' + ticket.ticketNumber : '' }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" *ngIf="ticket" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
          <a class="btn btn-outline-primary" *ngIf="ticket" [routerLink]="['/admin/helpdesk/tickets', ticket.id, 'edit']">Edit</a>
          <a class="btn btn-link" routerLink="/admin/helpdesk/tickets">Back to tickets</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="ticket as t">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div><strong>Type:</strong> {{ t.type === 'CUSTOMER' ? 'Customer' : 'Internal' }}</div>
          <div><strong>Status:</strong> <span class="badge" [ngClass]="statusClass(t.status)">{{ t.status }}</span></div>
          <div><strong>Priority:</strong> <span class="badge" [ngClass]="priorityClass(t.priority)">{{ t.priority }}</span></div>
          <div><strong>Category:</strong> {{ t.categoryName || '—' }}</div>
          <div *ngIf="t.requesterName"><strong>Requester:</strong> {{ t.requesterName }}</div>
          <div *ngIf="t.requesterEmail"><strong>Email:</strong> {{ t.requesterEmail }}</div>
          <div *ngIf="t.requesterPhone"><strong>Phone:</strong> {{ t.requesterPhone }}</div>
          <div *ngIf="t.customerName"><strong>Customer record:</strong> {{ t.customerName }}</div>
          <div><strong>Created:</strong> {{ t.createdAt | date: 'medium' }} <span *ngIf="t.createdByUserFullName">by {{ t.createdByUserFullName }}</span></div>
          <div *ngIf="t.resolvedAt"><strong>Resolved:</strong> {{ t.resolvedAt | date: 'medium' }}</div>
          <div *ngIf="t.closedAt"><strong>Closed:</strong> {{ t.closedAt | date: 'medium' }}</div>
        </div>
        <h4 class="mt-3 mb-1">{{ t.subject }}</h4>
        <p style="white-space:pre-wrap;">{{ t.description }}</p>
        <div class="card mt-2" style="padding:0.75rem; background:var(--hover, rgba(0, 0, 0, 0.05));" *ngIf="t.resolutionNotes">
          <strong>Resolution notes:</strong>
          <p style="white-space:pre-wrap; margin:0.25rem 0 0;">{{ t.resolutionNotes }}</p>
        </div>

        <div class="form-group mt-3" style="max-width:320px;">
          <label class="form-label">Assigned to</label>
          <select class="form-select" name="assignedToUserId" [(ngModel)]="assignedToUserId" (ngModelChange)="assign()">
            <option [ngValue]="null">— Unassigned —</option>
            <option *ngFor="let u of users" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>

        <div class="flex gap-2 mt-3" style="flex-wrap:wrap;">
          <button class="btn btn-outline-primary" (click)="start()" *ngIf="t.status === 'OPEN'" [disabled]="busy">Start progress</button>
          <button class="btn btn-outline-primary" (click)="openResolve()" *ngIf="t.status === 'OPEN' || t.status === 'IN_PROGRESS'" [disabled]="busy">Resolve</button>
          <button class="btn btn-outline-secondary" (click)="close()" *ngIf="t.status !== 'CLOSED'" [disabled]="busy">Close</button>
          <button class="btn btn-outline-secondary" (click)="reopen()" *ngIf="t.status === 'RESOLVED' || t.status === 'CLOSED'" [disabled]="busy">Reopen</button>
        </div>

        <div class="card mt-3" style="padding:0.9rem; max-width:480px;" *ngIf="showResolve">
          <div class="form-group">
            <label class="form-label">Resolution notes (optional)</label>
            <textarea class="form-control" name="resolutionNotes" [(ngModel)]="resolutionNotesDraft" rows="3"></textarea>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-primary btn-sm" (click)="resolve()" [disabled]="busy">Mark resolved</button>
            <button class="btn btn-link btn-sm" (click)="showResolve = false">Never mind</button>
          </div>
        </div>
      </div>

      <h4 class="mt-3 mb-2">Updates &amp; notes</h4>
      <div class="flex flex-col gap-2 mb-3">
        <div class="card" style="padding:0.75rem;" *ngFor="let c of comments">
          <div class="flex justify-between align-center">
            <strong>{{ c.authorUserFullName || 'System' }}</strong>
            <span class="badge" [ngClass]="c.internal ? 'badge-secondary' : 'badge-success'">
              {{ c.internal ? 'Internal note' : 'Visible to requester' }}
            </span>
          </div>
          <p style="white-space:pre-wrap; margin:0.4rem 0 0.2rem;">{{ c.body }}</p>
          <div class="text-muted" style="font-size:0.8rem;">{{ c.createdAt | date: 'medium' }}</div>
        </div>
        <p class="text-muted" *ngIf="comments.length === 0">No updates yet.</p>
      </div>

      <div class="card" style="padding:0.9rem; max-width:560px;">
        <div class="form-group">
          <label class="form-label">Add an update</label>
          <textarea class="form-control" name="newCommentBody" [(ngModel)]="newCommentBody" rows="3" placeholder="What's the latest?"></textarea>
        </div>
        <div class="flex justify-between align-center">
          <label class="form-label" style="margin:0;">
            <input type="checkbox" name="newCommentInternal" [(ngModel)]="newCommentInternal" /> Internal note (not shown to the requester)
          </label>
          <button class="btn btn-primary btn-sm" (click)="addComment()" [disabled]="busy || !newCommentBody.trim()">Add</button>
        </div>
      </div>
    </app-shell>
  `,
})
export class HelpDeskTicketDetailComponent implements OnInit {
  id!: number;
  ticket: HelpDeskTicket | null = null;
  comments: HelpDeskComment[] = [];
  users: AppUser[] = [];
  error: string | null = null;
  busy = false;
  printing = false;

  assignedToUserId: number | null = null;

  showResolve = false;
  resolutionNotesDraft = '';

  newCommentBody = '';
  newCommentInternal = true;

  constructor(private route: ActivatedRoute, private ticketApi: HelpDeskTicketApiService, private userApi: UserApiService) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.userApi.list().subscribe({ next: (u) => (this.users = u) });
    this.loadAll();
  }

  private loadAll(): void {
    this.ticketApi.getById(this.id).subscribe({
      next: (t) => {
        this.ticket = t;
        this.assignedToUserId = t.assignedToUserId;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load ticket'),
    });
    this.ticketApi.listComments(this.id).subscribe({
      next: (c) => (this.comments = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load updates'),
    });
  }

  assign(): void {
    this.error = null;
    this.busy = true;
    this.ticketApi.assign(this.id, this.assignedToUserId).subscribe({
      next: (t) => {
        this.busy = false;
        this.ticket = t;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to assign ticket';
      },
    });
  }

  /** Fourier: "print tick[et] on PDF" — opens the server-built A4 PDF in a new tab, same convention as InvoiceDetailComponent/SalesOrderDetailComponent's own "Print" buttons. */
  print(): void {
    if (!this.ticket) return;
    this.error = null;
    this.printing = true;
    this.ticketApi.pdf(this.ticket.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to generate the ticket PDF';
      },
    });
  }

  start(): void {
    this.runAction(this.ticketApi.start(this.id), 'Failed to start ticket');
  }

  openResolve(): void {
    this.showResolve = true;
    this.resolutionNotesDraft = '';
  }

  resolve(): void {
    this.showResolve = false;
    this.runAction(this.ticketApi.resolve(this.id, this.resolutionNotesDraft || null), 'Failed to resolve ticket');
  }

  close(): void {
    this.runAction(this.ticketApi.close(this.id), 'Failed to close ticket');
  }

  reopen(): void {
    this.runAction(this.ticketApi.reopen(this.id), 'Failed to reopen ticket');
  }

  addComment(): void {
    if (!this.newCommentBody.trim()) return;
    this.error = null;
    this.busy = true;
    this.ticketApi.addComment(this.id, this.newCommentBody.trim(), this.newCommentInternal).subscribe({
      next: (c) => {
        this.busy = false;
        this.comments = [...this.comments, c];
        this.newCommentBody = '';
        this.newCommentInternal = true;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to add update';
      },
    });
  }

  private runAction(call: Observable<HelpDeskTicket>, errorMessage: string): void {
    this.error = null;
    this.busy = true;
    call.subscribe({
      next: (t) => {
        this.busy = false;
        this.ticket = t;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? errorMessage;
      },
    });
  }

  priorityClass(p: string): string {
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

  statusClass(s: string): string {
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
