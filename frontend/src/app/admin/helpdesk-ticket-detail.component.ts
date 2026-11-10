import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCannedApiService, HelpDeskTicketApiService } from '../core/api/helpdesk-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, HelpDeskAttachment, HelpDeskCanned, HelpDeskComment, HelpDeskEvent, HelpDeskTicket } from '../core/models';
import { downloadBlob, openPdfForPrint } from '../core/download-file';
import { HD_EVENT_LABELS, hdDistance, hdPriorityClass, hdSlaClass, hdSlaLabel, hdStatusClass, hdStatusLabel } from './helpdesk-ui';

/**
 * Admin > Help Desk > Tickets > detail — everything for one ticket on one screen: who has it,
 * where it stands against its deadlines, the conversation (internal notes and replies the
 * requester also receives by e-mail), files, tags, links to an invoice or job, merging a
 * duplicate, the customer's rating, and the full history.
 */
@Component({
  selector: 'app-helpdesk-ticket-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Ticket{p0}' | translate: { p0: (ticket ? ' — ' + ticket.ticketNumber : '') } }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" *ngIf="ticket" [disabled]="printing" (click)="print()">{{ printing ? ('Opening…' | translate) : ('Print' | translate) }}</button>
          <a class="btn btn-outline-primary" *ngIf="ticket && ticket.status !== 'MERGED'" [routerLink]="['/admin/helpdesk/tickets', ticket.id, 'edit']">{{ 'Edit' | translate }}</a>
          <a class="btn btn-link" routerLink="/admin/helpdesk/tickets">{{ 'Back to tickets' | translate }}</a>
        </div>
      </div>

      <div class="alert alert-info hd-merged" *ngIf="ticket && ticket.status === 'MERGED'">
        {{ 'This ticket was merged into' | translate }}
        <a [routerLink]="['/admin/helpdesk/tickets', ticket.mergedIntoId]">{{ ticket.mergedIntoNumber }}</a>{{ '. Its conversation and files now live there.' | translate }}
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="ticket as t">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div><strong>{{ 'Type:' | translate }}</strong> {{ t.type === 'CUSTOMER' ? ('Customer' | translate) : ('Internal' | translate) }}</div>
          <div><strong>{{ 'Status:' | translate }}</strong> <span class="badge" [ngClass]="statusClass(t.status)">{{ statusLabel(t.status) }}</span></div>
          <div><strong>{{ 'Priority:' | translate }}</strong> <span class="badge" [ngClass]="priorityClass(t.priority)">{{ t.priority }}</span></div>
          <div><strong>{{ 'Category:' | translate }}</strong> {{ t.categoryName || '—' }}</div>
          <div *ngIf="t.requesterName"><strong>{{ 'Requester:' | translate }}</strong> {{ t.requesterName }}</div>
          <div *ngIf="t.requesterEmail"><strong>{{ 'Email:' | translate }}</strong> {{ t.requesterEmail }}</div>
          <div *ngIf="t.requesterPhone"><strong>{{ 'Phone:' | translate }}</strong> {{ t.requesterPhone }}</div>
          <div *ngIf="t.customerName"><strong>{{ 'Customer record:' | translate }}</strong> {{ t.customerName }}</div>
          <div><strong>{{ 'Created:' | translate }}</strong> {{ t.createdAt | date: 'medium' }} <span *ngIf="t.createdByUserFullName">{{ 'by {p0}' | translate: { p0: (t.createdByUserFullName) } }}</span></div>
          <div *ngIf="t.resolvedAt"><strong>{{ 'Resolved:' | translate }}</strong> {{ t.resolvedAt | date: 'medium' }}</div>
          <div *ngIf="t.closedAt"><strong>{{ 'Closed:' | translate }}</strong> {{ t.closedAt | date: 'medium' }}</div>
        </div>

        <div class="hd-sla mt-3 flex gap-3" style="flex-wrap:wrap;" *ngIf="t.firstResponseDueAt || t.resolutionDueAt">
          <div *ngIf="t.firstResponseDueAt">
            <strong>{{ 'First reply due:' | translate }}</strong> {{ t.firstResponseDueAt | date: 'medium' }}
            <span class="badge" [ngClass]="slaClass(t.responseState)">{{ slaLabel(t.responseState, 'reply') }}</span>
            <span class="text-muted" style="font-size:0.8rem;" *ngIf="!t.firstRespondedAt && t.responseState !== 'PAUSED'"> ({{ distance(t.firstResponseDueAt) }})</span>
            <span class="text-muted" style="font-size:0.8rem;" *ngIf="t.firstRespondedAt"> {{ '· answered {p0}' | translate: { p0: (t.firstRespondedAt | date: 'short') } }}</span>
          </div>
          <div *ngIf="t.resolutionDueAt">
            <strong>{{ 'Resolve by:' | translate }}</strong> {{ t.resolutionDueAt | date: 'medium' }}
            <span class="badge" [ngClass]="slaClass(t.resolutionState)">{{ slaLabel(t.resolutionState, 'fix') }}</span>
            <span class="text-muted" style="font-size:0.8rem;" *ngIf="!t.resolvedAt && t.resolutionState !== 'PAUSED'"> ({{ distance(t.resolutionDueAt) }})</span>
          </div>
          <div *ngIf="t.waitingSince" class="text-muted">{{ 'Waiting for the customer since {p0} — the clock is paused.' | translate: { p0: (t.waitingSince | date: 'medium') } }}</div>
        </div>

        <h4 class="mt-3 mb-1">{{ t.subject }}</h4>
        <p style="white-space:pre-wrap;">{{ t.description }}</p>
        <div class="card mt-2" style="padding:0.75rem; background:var(--hover, rgba(0, 0, 0, 0.05));" *ngIf="t.resolutionNotes">
          <strong>{{ 'Resolution notes:' | translate }}</strong>
          <p style="white-space:pre-wrap; margin:0.25rem 0 0;">{{ t.resolutionNotes }}</p>
        </div>
        <div class="card mt-2 hd-rating" style="padding:0.75rem;" *ngIf="t.satisfactionRating">
          <strong>{{ 'Customer rating:' | translate }}</strong> <span class="hd-stars">{{ stars(t.satisfactionRating) }}</span> {{ '({p0} of 5)' | translate: { p0: (t.satisfactionRating) } }}
          <p style="white-space:pre-wrap; margin:0.25rem 0 0;" *ngIf="t.satisfactionComment">{{ t.satisfactionComment }}</p>
        </div>

        <div class="flex gap-3 mt-3" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="t.status !== 'MERGED'">
          <div class="form-group" style="min-width:260px; margin:0;">
            <label class="form-label">{{ 'Assigned to' | translate }}</label>
            <select class="form-select" name="assignedToUserId" [(ngModel)]="assignedToUserId" (ngModelChange)="assign()">
              <option [ngValue]="null">{{ '— Unassigned —' | translate }}</option>
              <option *ngFor="let u of users" [ngValue]="u.id">{{ u.fullName }}</option>
            </select>
          </div>
          <div class="form-group" style="min-width:300px; margin:0;">
            <label class="form-label">{{ 'Tags (comma separated)' | translate }}</label>
            <div class="flex gap-2">
              <input class="form-control hd-tags" name="tagsText" [(ngModel)]="tagsText" [placeholder]="'billing, vip' | translate" />
              <button type="button" class="btn btn-outline-primary btn-sm hd-save-tags" [disabled]="busy" (click)="saveTags()">{{ 'Save tags' | translate }}</button>
            </div>
          </div>
        </div>
        <div class="mt-2 mb-2" *ngIf="t.tags.length"><span class="badge badge-outline" style="margin-right:0.25rem;" *ngFor="let g of t.tags">{{ g }}</span></div>

        <div class="flex gap-3 mt-3" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="t.status !== 'MERGED'">
          <div class="form-group" style="margin:0;">
            <label class="form-label">{{ 'Invoice number' | translate }}</label>
            <input class="form-control hd-link-invoice" name="linkInvoice" [(ngModel)]="linkInvoice" [placeholder]="'INV-…' | translate" />
          </div>
          <div class="form-group" style="margin:0;">
            <label class="form-label">{{ 'Job (work order) number' | translate }}</label>
            <input class="form-control hd-link-wo" name="linkWo" [(ngModel)]="linkWo" [placeholder]="'WO-…' | translate" />
          </div>
          <button type="button" class="btn btn-outline-primary btn-sm hd-save-links" [disabled]="busy" (click)="saveLinks()">{{ 'Save links' | translate }}</button>
          <span *ngIf="t.linkedInvoiceId">→ <a [routerLink]="['/admin/invoices', t.linkedInvoiceId]">{{ 'Invoice {p0}' | translate: { p0: (t.linkedInvoiceNumber) } }}</a></span>
          <span *ngIf="t.linkedWorkOrderId">→ <a [routerLink]="['/admin/work-orders', t.linkedWorkOrderId]">{{ 'Job {p0}' | translate: { p0: (t.linkedWorkOrderNumber) } }}</a></span>
        </div>

        <div class="flex gap-2 mt-3" style="flex-wrap:wrap;" *ngIf="t.status !== 'MERGED'">
          <button class="btn btn-outline-primary hd-start" (click)="start()" *ngIf="t.status === 'OPEN' || t.status === 'WAITING'" [disabled]="busy">{{ t.status === 'WAITING' ? ('Back to work' | translate) : ('Start progress' | translate) }}</button>
          <button class="btn btn-outline-secondary hd-wait" (click)="wait()" *ngIf="t.status === 'OPEN' || t.status === 'IN_PROGRESS'" [disabled]="busy">{{ 'Wait for customer' | translate }}</button>
          <button class="btn btn-outline-primary hd-resolve" (click)="openResolve()" *ngIf="t.status === 'OPEN' || t.status === 'IN_PROGRESS' || t.status === 'WAITING'" [disabled]="busy">{{ 'Resolve' | translate }}</button>
          <button class="btn btn-outline-secondary hd-close" (click)="close()" *ngIf="t.status !== 'CLOSED'" [disabled]="busy">{{ 'Close' | translate }}</button>
          <button class="btn btn-outline-secondary hd-reopen" (click)="reopen()" *ngIf="t.status === 'RESOLVED' || t.status === 'CLOSED'" [disabled]="busy">{{ 'Reopen' | translate }}</button>
          <button class="btn btn-outline-secondary hd-merge-open" (click)="showMerge = !showMerge" [disabled]="busy">{{ 'Merge into another ticket…' | translate }}</button>
        </div>

        <div class="card mt-3" style="padding:0.9rem; max-width:480px;" *ngIf="showResolve">
          <div class="form-group">
            <label class="form-label">{{ 'Resolution notes (optional)' | translate }}</label>
            <textarea class="form-control" name="resolutionNotes" [(ngModel)]="resolutionNotesDraft" rows="3"></textarea>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-primary btn-sm hd-resolve-confirm" (click)="resolve()" [disabled]="busy">{{ 'Mark resolved' | translate }}</button>
            <button class="btn btn-link btn-sm" (click)="showResolve = false">{{ 'Never mind' | translate }}</button>
          </div>
        </div>

        <div class="card mt-3 hd-merge-panel" style="padding:0.9rem; max-width:560px;" *ngIf="showMerge">
          <p class="text-muted" style="margin-top:0;">{{ 'Use this when the same problem was reported twice. All of this ticket\\'s conversation and files move to the ticket you choose, and this one is closed as merged.' | translate }}</p>
          <div class="flex gap-2">
            <input class="form-control hd-merge-q" name="mergeQ" [(ngModel)]="mergeQ" [placeholder]="'Ticket number or subject' | translate" (keyup.enter)="searchMerge()" />
            <button class="btn btn-outline-primary btn-sm hd-merge-search" type="button" (click)="searchMerge()">{{ 'Find' | translate }}</button>
          </div>
          <div class="mt-2" *ngFor="let c of mergeCandidates">
            <label style="display:flex; gap:0.5rem; align-items:center;">
              <input type="radio" name="mergeTarget" [value]="c.id" [(ngModel)]="mergeTargetId" /> {{ c.ticketNumber }} — {{ c.subject }} ({{ statusLabel(c.status) }})
            </label>
          </div>
          <div class="text-muted mt-1" *ngIf="mergeSearched && !mergeCandidates.length">{{ 'No other open ticket matches.' | translate }}</div>
          <div class="flex gap-2 mt-2">
            <button class="btn btn-primary btn-sm hd-merge-confirm" type="button" [disabled]="busy || !mergeTargetId" (click)="doMerge()">{{ 'Merge' | translate }}</button>
            <button class="btn btn-link btn-sm" type="button" (click)="showMerge = false">{{ 'Never mind' | translate }}</button>
          </div>
        </div>
      </div>

      <h4 class="mt-3 mb-2">{{ 'Conversation' | translate }}</h4>
      <div class="flex flex-col gap-2 mb-3">
        <div class="card hd-comment" style="padding:0.75rem;" *ngFor="let c of comments">
          <div class="flex justify-between align-center">
            <strong>{{ c.fromRequester ? (c.authorUserFullName || 'Customer') + ' (customer)' : (c.authorUserFullName || 'System') }}</strong>
            <span class="badge" [ngClass]="c.internal ? 'badge-secondary' : (c.fromRequester ? 'badge-warning' : 'badge-success')">
              {{ c.internal ? 'Internal note' : (c.fromRequester ? 'From the customer' : (c.emailed ? 'Reply — sent by e-mail' : 'Reply — visible to customer')) }}
            </span>
          </div>
          <p style="white-space:pre-wrap; margin:0.4rem 0 0.2rem;">{{ c.body }}</p>
          <div class="text-muted" style="font-size:0.8rem;">{{ c.createdAt | date: 'medium' }}</div>
        </div>
        <p class="text-muted" *ngIf="comments.length === 0">{{ 'No updates yet.' | translate }}</p>
      </div>

      <div class="card mb-3 hd-composer" style="padding:0.9rem; max-width:640px;" *ngIf="ticket && ticket.status !== 'MERGED'">
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap; align-items:center;">
          <label style="display:flex; gap:0.35rem; align-items:center; margin:0;"><input type="radio" name="replyMode" value="note" [(ngModel)]="replyMode" /> {{ 'Internal note' | translate }}</label>
          <label style="display:flex; gap:0.35rem; align-items:center; margin:0;"><input type="radio" name="replyMode" value="reply" [(ngModel)]="replyMode" /> {{ 'Reply to the customer' | translate }}</label>
          <select class="form-select hd-canned" style="max-width:220px; margin-left:auto;" name="cannedId" [ngModel]="null" (ngModelChange)="useCanned($event)" *ngIf="canned.length">
            <option [ngValue]="null">{{ 'Insert a saved reply…' | translate }}</option>
            <option *ngFor="let r of canned" [ngValue]="r.id">{{ r.title }}</option>
          </select>
        </div>
        <textarea class="form-control hd-reply-body" name="newCommentBody" [(ngModel)]="newCommentBody" rows="4" [placeholder]="replyMode === 'reply' ? 'Your answer to the customer' : 'A note for your colleagues'"></textarea>
        <div class="text-muted mt-1" style="font-size:0.8rem;" *ngIf="replyMode === 'reply'">
          {{ 'The customer sees this on the ticket page{p0}.' | translate: { p0: (ticket.requesterEmail ? ' and gets it by e-mail at ' + ticket.requesterEmail : '') } }}
        </div>
        <div class="flex justify-between align-center mt-2" style="flex-wrap:wrap; gap:0.5rem;">
          <label style="display:flex; gap:0.35rem; align-items:center; margin:0;" *ngIf="replyMode === 'reply'">
            <input type="checkbox" class="hd-wait-after" name="waitAfter" [(ngModel)]="waitAfter" /> {{ 'Then wait for the customer\\'s answer' | translate }}
          </label>
          <span *ngIf="replyMode !== 'reply'"></span>
          <button class="btn btn-primary btn-sm hd-send" (click)="addComment()" [disabled]="busy || !newCommentBody.trim()">{{ replyMode === 'reply' ? ('Send reply' | translate) : ('Add note' | translate) }}</button>
        </div>
      </div>

      <h4 class="mt-3 mb-2">{{ 'Files' | translate }}</h4>
      <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
        <div class="hd-file card" style="padding:0.5rem; width:11rem;" *ngFor="let a of files">
          <img *ngIf="a.image && thumbs[a.id]" [src]="thumbs[a.id]" [alt]="a.fileName" style="width:100%; height:5.5rem; object-fit:cover; border-radius:4px;" />
          <div *ngIf="!a.image || !thumbs[a.id]" style="height:5.5rem; display:flex; align-items:center; justify-content:center; font-size:0.75rem; text-align:center; word-break:break-all;">{{ a.fileName }}</div>
          <div style="font-size:0.75rem; word-break:break-all;" class="mt-1"><a href="javascript:void(0)" (click)="openFile(a)">{{ a.fileName }}</a></div>
          <div class="text-muted" style="font-size:0.7rem;">{{ '{p0} KB · {p1}' | translate: { p0: (a.sizeBytes / 1024 | number: '1.0-0'), p1: (a.fromRequester ? 'from the customer' : (a.uploadedBy || '')) } }}</div>
          <label style="display:flex; gap:0.3rem; align-items:center; font-size:0.75rem; margin:0.25rem 0 0;" *ngIf="!a.fromRequester && ticket?.status !== 'MERGED'">
            <input type="checkbox" [checked]="a.visibleToRequester" (change)="toggleVisible(a, $event)" /> {{ 'Customer can see' | translate }}
          </label>
          <button type="button" class="btn btn-link btn-sm" style="padding:0;" (click)="removeFile(a)" *ngIf="ticket?.status !== 'MERGED'">{{ 'Remove' | translate }}</button>
        </div>
        <p class="text-muted" *ngIf="files.length === 0">{{ 'No files yet.' | translate }}</p>
      </div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap; align-items:center;" *ngIf="ticket && ticket.status !== 'MERGED'">
        <input type="file" id="hd-file-input" class="hd-file-input" (change)="picked($event)" accept="image/png,image/jpeg,image/gif,image/webp,application/pdf,text/plain" />
        <label style="display:flex; gap:0.35rem; align-items:center; margin:0;"><input type="checkbox" name="fileVisible" [(ngModel)]="fileVisible" /> {{ 'Customer can see it' | translate }}</label>
        <button class="btn btn-outline-primary btn-sm hd-upload" type="button" [disabled]="busy || !chosen" (click)="upload()">{{ 'Add file' | translate }}</button>
        <span class="text-muted" style="font-size:0.8rem;">{{ 'Pictures, PDF or text, up to 10 MB each.' | translate }}</span>
      </div>

      <h4 class="mt-3 mb-2">{{ 'History' | translate }}</h4>
      <table class="table-sm mb-3 hd-history">
        <tbody>
          <tr *ngFor="let e of events">
            <td style="white-space:nowrap;">{{ e.at | date: 'medium' }}</td>
            <td>{{ eventLabel(e.kind) }}<span class="text-muted" *ngIf="e.detail"> — {{ e.detail }}</span></td>
            <td class="text-muted">{{ e.by || '' }}</td>
          </tr>
          <tr *ngIf="events.length === 0"><td class="text-muted">{{ 'Nothing recorded yet.' | translate }}</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class HelpDeskTicketDetailComponent implements OnInit, OnDestroy {
  id!: number;
  ticket: HelpDeskTicket | null = null;
  comments: HelpDeskComment[] = [];
  events: HelpDeskEvent[] = [];
  files: HelpDeskAttachment[] = [];
  thumbs: Record<number, string> = {};
  canned: HelpDeskCanned[] = [];
  users: AppUser[] = [];
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  printing = false;

  assignedToUserId: number | null = null;
  tagsText = '';
  linkInvoice = '';
  linkWo = '';

  showResolve = false;
  resolutionNotesDraft = '';

  replyMode: 'note' | 'reply' = 'note';
  newCommentBody = '';
  waitAfter = false;

  chosen: File | null = null;
  fileVisible = false;

  showMerge = false;
  mergeQ = '';
  mergeCandidates: HelpDeskTicket[] = [];
  mergeTargetId: number | null = null;
  mergeSearched = false;

  constructor(
    private route: ActivatedRoute,
    private ticketApi: HelpDeskTicketApiService,
    private cannedApi: HelpDeskCannedApiService,
    private userApi: UserApiService
  ) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.userApi.list().subscribe({ next: (u) => (this.users = u) });
    this.cannedApi.list(true).subscribe({ next: (c) => (this.canned = c), error: () => {} });
    this.loadAll();
  }

  ngOnDestroy(): void {
    Object.values(this.thumbs).forEach((u) => URL.revokeObjectURL(u));
  }

  private loadAll(): void {
    this.ticketApi.getById(this.id).subscribe({
      next: (t) => this.setTicket(t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load ticket'),
    });
    this.loadComments();
    this.loadEvents();
    this.loadFiles();
  }

  private setTicket(t: HelpDeskTicket): void {
    this.ticket = t;
    this.assignedToUserId = t.assignedToUserId;
    this.tagsText = t.tags.join(', ');
    this.linkInvoice = t.linkedInvoiceNumber ?? '';
    this.linkWo = t.linkedWorkOrderNumber ?? '';
  }

  private loadComments(): void {
    this.ticketApi.listComments(this.id).subscribe({
      next: (c) => (this.comments = c),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load updates'),
    });
  }

  private loadEvents(): void {
    this.ticketApi.events(this.id).subscribe({ next: (e) => (this.events = e), error: () => {} });
  }

  private loadFiles(): void {
    this.ticketApi.attachments(this.id).subscribe({
      next: (l) => {
        this.files = l;
        l.filter((a) => a.image && !this.thumbs[a.id]).forEach((a) =>
          this.ticketApi.attachmentBlob(this.id, a.id).subscribe({ next: (b) => (this.thumbs = { ...this.thumbs, [a.id]: URL.createObjectURL(b) }), error: () => {} }));
      },
      error: () => {},
    });
  }

  private fail(err: any, fallback: string): void {
    this.busy = false;
    this.notice = null;
    this.error = err?.error?.message ?? fallback;
  }

  private ok(text: string): void {
    this.busy = false;
    this.error = null;
    this.notice = text;
  }

  assign(): void {
    this.error = null;
    this.busy = true;
    this.ticketApi.assign(this.id, this.assignedToUserId).subscribe({
      next: (t) => { this.busy = false; this.setTicket(t); this.loadEvents(); },
      error: (err) => this.fail(err, 'Failed to assign ticket'),
    });
  }

  saveTags(): void {
    const tags = this.tagsText.split(',').map((x) => x.trim()).filter((x) => x.length > 0);
    this.busy = true;
    this.ticketApi.setTags(this.id, tags).subscribe({
      next: (t) => { this.setTicket(t); this.ok('Tags saved.'); this.loadEvents(); },
      error: (err) => this.fail(err, 'Could not save the tags'),
    });
  }

  saveLinks(): void {
    this.busy = true;
    this.ticketApi.setLinks(this.id, this.linkInvoice.trim() || null, this.linkWo.trim() || null).subscribe({
      next: (t) => { this.setTicket(t); this.ok('Links saved.'); this.loadEvents(); },
      error: (err) => this.fail(err, 'Could not save the links'),
    });
  }

  /** Fourier: "print tick[et] on PDF" — opens the server-built A4 PDF in a new tab. */
  print(): void {
    if (!this.ticket) return;
    this.error = null;
    this.printing = true;
    this.ticketApi.pdf(this.ticket.id).subscribe({
      next: (blob) => { this.printing = false; openPdfForPrint(blob); },
      error: (err) => { this.printing = false; this.error = err?.error?.message ?? 'Failed to generate the ticket PDF'; },
    });
  }

  start(): void { this.runAction(this.ticketApi.start(this.id), 'Failed to start ticket'); }
  wait(): void { this.runAction(this.ticketApi.wait(this.id), 'Failed to put the ticket on hold'); }

  openResolve(): void {
    this.showResolve = true;
    this.resolutionNotesDraft = '';
  }

  resolve(): void {
    this.showResolve = false;
    this.runAction(this.ticketApi.resolve(this.id, this.resolutionNotesDraft || null), 'Failed to resolve ticket');
  }

  close(): void { this.runAction(this.ticketApi.close(this.id), 'Failed to close ticket'); }
  reopen(): void { this.runAction(this.ticketApi.reopen(this.id), 'Failed to reopen ticket'); }

  useCanned(id: number | null): void {
    if (id == null || !this.ticket) return;
    this.cannedApi.render(id, this.ticket.id).subscribe({
      next: (r) => {
        this.replyMode = 'reply';
        this.newCommentBody = this.newCommentBody.trim() ? this.newCommentBody.trimEnd() + '\n\n' + r.body : r.body;
      },
      error: (err) => this.fail(err, 'Could not load the saved reply'),
    });
  }

  addComment(): void {
    if (!this.newCommentBody.trim()) return;
    this.error = null;
    this.busy = true;
    const reply = this.replyMode === 'reply';
    this.ticketApi.addComment(this.id, this.newCommentBody.trim(), !reply, reply && this.waitAfter).subscribe({
      next: () => {
        this.newCommentBody = '';
        this.waitAfter = false;
        this.ok(reply ? 'Reply sent.' : 'Note added.');
        this.loadComments();
        this.loadEvents();
        this.ticketApi.getById(this.id).subscribe({ next: (t) => this.setTicket(t) });
      },
      error: (err) => this.fail(err, 'Failed to add update'),
    });
  }

  picked(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.chosen = input.files && input.files.length ? input.files[0] : null;
  }

  upload(): void {
    if (!this.chosen) return;
    this.busy = true;
    this.ticketApi.addAttachment(this.id, this.chosen, this.fileVisible).subscribe({
      next: () => {
        this.chosen = null;
        const el = document.getElementById('hd-file-input') as HTMLInputElement | null;
        if (el) el.value = '';
        this.ok('File added.');
        this.loadFiles();
        this.loadEvents();
      },
      error: (err) => this.fail(err, 'Could not upload the file'),
    });
  }

  openFile(a: HelpDeskAttachment): void {
    this.ticketApi.attachmentBlob(this.id, a.id).subscribe({
      next: (b) => (a.contentType === 'text/plain' ? downloadBlob(b, a.fileName) : openPdfForPrint(b)),
      error: (err) => this.fail(err, 'Could not open the file'),
    });
  }

  toggleVisible(a: HelpDeskAttachment, ev: Event): void {
    const value = (ev.target as HTMLInputElement).checked;
    this.ticketApi.setAttachmentVisible(this.id, a.id, value).subscribe({
      next: () => { a.visibleToRequester = value; },
      error: (err) => { (ev.target as HTMLInputElement).checked = !value; this.fail(err, 'Could not change who can see the file'); },
    });
  }

  removeFile(a: HelpDeskAttachment): void {
    this.ticketApi.deleteAttachment(this.id, a.id).subscribe({
      next: () => {
        const u = this.thumbs[a.id];
        if (u) URL.revokeObjectURL(u);
        const t = { ...this.thumbs };
        delete t[a.id];
        this.thumbs = t;
        this.files = this.files.filter((x) => x.id !== a.id);
        this.ok('File removed.');
      },
      error: (err) => this.fail(err, 'Could not remove the file'),
    });
  }

  searchMerge(): void {
    this.mergeSearched = false;
    this.ticketApi.search({ q: this.mergeQ || null, view: 'active' }).subscribe({
      next: (l) => {
        this.mergeCandidates = l.filter((x) => x.id !== this.id).slice(0, 15);
        this.mergeTargetId = null;
        this.mergeSearched = true;
      },
      error: (err) => this.fail(err, 'Could not search tickets'),
    });
  }

  doMerge(): void {
    if (!this.mergeTargetId) return;
    this.runAction(this.ticketApi.merge(this.id, this.mergeTargetId), 'Could not merge the tickets', () => {
      this.showMerge = false;
      this.loadComments();
      this.loadFiles();
    });
  }

  private runAction(call: Observable<HelpDeskTicket>, errorMessage: string, after?: () => void): void {
    this.error = null;
    this.notice = null;
    this.busy = true;
    call.subscribe({
      next: (t) => {
        this.busy = false;
        this.setTicket(t);
        this.loadEvents();
        if (after) after();
      },
      error: (err) => this.fail(err, errorMessage),
    });
  }

  stars(n: number): string { return '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n)); }
  eventLabel(k: string): string { return HD_EVENT_LABELS[k] ?? k; }
  priorityClass = hdPriorityClass;
  statusClass = hdStatusClass;
  statusLabel = hdStatusLabel;
  slaClass = hdSlaClass;
  slaLabel = hdSlaLabel;
  distance = hdDistance;
}
