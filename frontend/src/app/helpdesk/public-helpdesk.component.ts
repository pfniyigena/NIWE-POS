import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicHelpDeskApiService } from '../core/api/public-helpdesk-api.service';
import { PublicHelpDeskCategory, PublicHelpDeskTicketConfirmation, PublicHelpDeskTicketStatus } from '../core/models';
import { NiweFooterComponent } from '../shared/niwe-footer.component';

/**
 * Fourier: "Add Help Desk module (with its menu) should manage customer and internal support
 * requests from creation through resolution", then "Customer login is not necessary to create a
 * Help Desk ticket. In fact, supporting both authenticated and guest ticket creation." This is the
 * guest half — no login, reachable at /helpdesk — same chrome-free centered-card layout as
 * PublicReservationComponent (no app-shell, since an anonymous visitor has no sidenav).
 *
 * <p>Two tabs: submit a new ticket, or check an existing one by ticket number + the email given at
 * creation (the only "credential" a guest has — see the backend HelpDeskTicketService.publicLookup's
 * own javadoc). Staff/"authenticated" ticket creation lives entirely in the admin area instead
 * (Admin > Help Desk > Tickets > New), reachable only after logging in.
 */
@Component({
  selector: 'app-public-helpdesk',
  standalone: true,
  imports: [NiweFooterComponent, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center; padding:1rem 0;">
      <div class="card" style="width:520px; max-width:96vw; padding:2rem;">
        <h3 class="mb-3" style="text-align:center;">Help Desk</h3>
        <div class="flex gap-2 mb-3" style="justify-content:center;">
          <button type="button" class="btn" [class.btn-primary]="tab === 'submit'" [class.btn-outline-secondary]="tab !== 'submit'" (click)="switchTab('submit')">
            Submit a ticket
          </button>
          <button type="button" class="btn" [class.btn-primary]="tab === 'check'" [class.btn-outline-secondary]="tab !== 'check'" (click)="switchTab('check')">
            Check my ticket
          </button>
        </div>

        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

        <!-- Submit a ticket -->
        <ng-container *ngIf="tab === 'submit'">
          <ng-container *ngIf="!confirmation">
            <form (ngSubmit)="submit()">
              <div class="form-group mb-2">
                <label class="form-label">Your name</label>
                <input class="form-control" name="requesterName" [(ngModel)]="requesterName" required />
              </div>
              <div class="flex gap-2 mb-2">
                <div class="grow">
                  <label class="form-label">Your email</label>
                  <input class="form-control" type="email" name="requesterEmail" [(ngModel)]="requesterEmail" required />
                </div>
                <div class="grow">
                  <label class="form-label">Phone (optional)</label>
                  <input class="form-control" name="requesterPhone" [(ngModel)]="requesterPhone" />
                </div>
              </div>
              <div class="form-group mb-2" *ngIf="categories.length > 0">
                <label class="form-label">Category (optional)</label>
                <select class="form-select" name="categoryId" [(ngModel)]="categoryId">
                  <option [ngValue]="null">— Not sure —</option>
                  <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
                </select>
              </div>
              <div class="form-group mb-2">
                <label class="form-label">Subject</label>
                <input class="form-control" name="subject" [(ngModel)]="subject" required />
              </div>
              <div class="form-group mb-3">
                <label class="form-label">Tell us what's going on</label>
                <textarea class="form-control" rows="4" name="description" [(ngModel)]="description" required></textarea>
              </div>
              <button class="btn btn-primary btn-block" type="submit" [disabled]="loading">
                {{ loading ? 'Submitting…' : 'Submit ticket' }}
              </button>
            </form>
          </ng-container>
          <ng-container *ngIf="confirmation as c">
            <h4 class="mb-2" style="text-align:center;">Ticket submitted</h4>
            <p>Thanks — we've got your request and someone will be in touch.</p>
            <div class="card" style="padding:1rem; text-align:center; margin:1rem 0;">
              <div class="text-muted" style="font-size:0.8rem;">Your ticket number</div>
              <div style="font-size:1.4rem; font-weight:600; letter-spacing:0.03em;">{{ c.ticketNumber }}</div>
            </div>
            <p class="text-muted" style="font-size:0.85rem;">
              Keep this number — use the "Check my ticket" tab above with it and your email to see updates.
            </p>
          </ng-container>
        </ng-container>

        <!-- Check my ticket -->
        <ng-container *ngIf="tab === 'check'">
          <ng-container *ngIf="!status">
            <form (ngSubmit)="lookup()">
              <div class="form-group mb-2">
                <label class="form-label">Ticket number</label>
                <input class="form-control" name="lookupTicketNumber" [(ngModel)]="lookupTicketNumber" placeholder="e.g. HD-1234567890" required />
              </div>
              <div class="form-group mb-3">
                <label class="form-label">Email used when you submitted it</label>
                <input class="form-control" type="email" name="lookupEmail" [(ngModel)]="lookupEmail" required />
              </div>
              <button class="btn btn-primary btn-block" type="submit" [disabled]="loading">
                {{ loading ? 'Checking…' : 'Check status' }}
              </button>
            </form>
          </ng-container>
          <ng-container *ngIf="status as s">
            <div class="flex justify-between align-center mb-2">
              <strong>{{ s.ticketNumber }}</strong>
              <span class="badge" [ngClass]="statusClass(s.status)">{{ statusLabel(s.status) }}</span>
            </div>
            <h4 class="mb-1">{{ s.subject }}</h4>
            <div class="text-muted mb-2" style="font-size:0.85rem;">
              {{ s.categoryName || 'Uncategorized' }} · submitted {{ s.createdAt | date: 'medium' }}
            </div>
            <div class="alert alert-info" *ngIf="s.status === 'WAITING'">We are waiting for your answer. Reply below to carry on.</div>
            <div class="alert alert-info" *ngIf="s.status === 'MERGED'">This request was combined with another one. Please check the newer ticket number we sent you.</div>
            <div class="card mt-2" style="padding:0.75rem;" *ngIf="s.resolutionNotes">
              <strong>Resolution:</strong>
              <p style="white-space:pre-wrap; margin:0.25rem 0 0;">{{ s.resolutionNotes }}</p>
            </div>
            <h5 class="mt-3 mb-1">Updates</h5>
            <div class="flex flex-col gap-2">
              <div class="card hd-update" style="padding:0.6rem;" *ngFor="let u of s.updates" [style.background]="u.fromRequester ? 'var(--hover, rgba(0,0,0,0.04))' : ''">
                <div class="text-muted" style="font-size:0.75rem;">{{ u.fromRequester ? 'You' : 'Help desk' }} · {{ u.createdAt | date: 'medium' }}</div>
                <p style="white-space:pre-wrap; margin:0.2rem 0 0;">{{ u.body }}</p>
              </div>
              <p class="text-muted" *ngIf="s.updates.length === 0">No updates yet.</p>
            </div>

            <ng-container *ngIf="s.files.length">
              <h5 class="mt-3 mb-1">Files</h5>
              <div *ngFor="let f of s.files" style="font-size:0.9rem;">
                <a href="javascript:void(0)" (click)="openFile(f.id, f.fileName, f.contentType)">{{ f.fileName }}</a>
                <span class="text-muted" style="font-size:0.75rem;"> · {{ f.fromRequester ? 'from you' : 'from the help desk' }}</span>
              </div>
            </ng-container>

            <div class="mt-3 hd-reply-box" *ngIf="s.canReply">
              <h5 class="mb-1">Add a reply</h5>
              <textarea class="form-control hd-public-reply" rows="3" name="replyBody" [(ngModel)]="replyBody" placeholder="Write your message here" maxlength="5000"></textarea>
              <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:center;">
                <button type="button" class="btn btn-primary btn-sm hd-public-send" [disabled]="loading || !replyBody.trim()" (click)="sendReply()">Send</button>
                <input type="file" id="hd-public-file" class="hd-public-file" (change)="pickFile($event)" accept="image/png,image/jpeg,image/gif,image/webp,application/pdf,text/plain" />
                <button type="button" class="btn btn-outline-secondary btn-sm hd-public-upload" [disabled]="loading || !pickedFile" (click)="sendFile()">Attach file</button>
              </div>
              <div class="text-muted mt-1" style="font-size:0.75rem;">Pictures, PDF or text files, up to 10 MB.</div>
            </div>

            <div class="mt-3 hd-rate-box" *ngIf="s.canRate">
              <h5 class="mb-1">How did we do?</h5>
              <div style="font-size:1.6rem; line-height:1;">
                <span *ngFor="let n of [1,2,3,4,5]" class="hd-star" [attr.data-star]="n" style="cursor:pointer;" [style.color]="n <= ratingChoice ? '#f5a623' : '#bbb'" (click)="ratingChoice = n">★</span>
              </div>
              <textarea class="form-control mt-2 hd-rate-comment" rows="2" name="ratingComment" [(ngModel)]="ratingComment" placeholder="Anything you'd like to add? (optional)" maxlength="1000"></textarea>
              <button type="button" class="btn btn-primary btn-sm mt-2 hd-rate-send" [disabled]="loading || !ratingChoice" (click)="sendRating()">Send rating</button>
            </div>
            <div class="mt-3 hd-rated" *ngIf="s.rating">
              <strong>Your rating:</strong> <span style="color:#f5a623;">{{ stars(s.rating) }}</span> Thank you!
            </div>

            <button type="button" class="btn btn-link mt-2" (click)="status = null">Check another ticket</button>
          </ng-container>
        </ng-container>

        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/login">Staff login</a>
        </p>
      </div>
    </div>
    <app-niwe-footer [fixed]="true"></app-niwe-footer>
  `,
})
export class PublicHelpDeskComponent implements OnInit {
  tab: 'submit' | 'check' = 'submit';

  categories: PublicHelpDeskCategory[] = [];

  requesterName = '';
  requesterEmail = '';
  requesterPhone = '';
  categoryId: number | null = null;
  subject = '';
  description = '';
  confirmation: PublicHelpDeskTicketConfirmation | null = null;

  lookupTicketNumber = '';
  lookupEmail = '';
  status: PublicHelpDeskTicketStatus | null = null;
  replyBody = '';
  pickedFile: File | null = null;
  ratingChoice = 0;
  ratingComment = '';

  error: string | null = null;
  loading = false;

  constructor(private publicHelpDeskApi: PublicHelpDeskApiService) {}

  ngOnInit(): void {
    this.publicHelpDeskApi.categories().subscribe({ next: (c) => (this.categories = c) });
  }

  switchTab(tab: 'submit' | 'check'): void {
    this.tab = tab;
    this.error = null;
  }

  submit(): void {
    if (!this.requesterName.trim() || !this.requesterEmail.trim() || !this.subject.trim() || !this.description.trim()) {
      this.error = 'Please fill in your name, email, subject, and description.';
      return;
    }
    this.error = null;
    this.loading = true;
    this.publicHelpDeskApi
      .create({
        subject: this.subject.trim(),
        description: this.description.trim(),
        categoryId: this.categoryId,
        requesterName: this.requesterName.trim(),
        requesterEmail: this.requesterEmail.trim(),
        requesterPhone: this.requesterPhone || null,
      })
      .subscribe({
        next: (c) => {
          this.loading = false;
          this.confirmation = c;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.error?.message ?? 'Failed to submit — please try again';
        },
      });
  }

  lookup(): void {
    if (!this.lookupTicketNumber.trim() || !this.lookupEmail.trim()) {
      this.error = 'Enter your ticket number and email.';
      return;
    }
    this.error = null;
    this.loading = true;
    this.publicHelpDeskApi.lookup(this.lookupTicketNumber.trim(), this.lookupEmail.trim()).subscribe({
      next: (s) => {
        this.loading = false;
        this.status = s;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'No ticket matches that ticket number and email';
      },
    });
  }

  private applyStatus(s: PublicHelpDeskTicketStatus): void {
    this.loading = false;
    this.status = s;
  }

  private fail(err: any, fallback: string): void {
    this.loading = false;
    this.error = err?.error?.message ?? fallback;
  }

  sendReply(): void {
    if (!this.replyBody.trim()) return;
    this.error = null;
    this.loading = true;
    this.publicHelpDeskApi.reply(this.lookupTicketNumber.trim(), this.lookupEmail.trim(), this.replyBody.trim()).subscribe({
      next: (s) => { this.replyBody = ''; this.applyStatus(s); },
      error: (err) => this.fail(err, 'Could not send your reply'),
    });
  }

  pickFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.pickedFile = input.files && input.files.length ? input.files[0] : null;
  }

  sendFile(): void {
    if (!this.pickedFile) return;
    this.error = null;
    this.loading = true;
    this.publicHelpDeskApi.addFile(this.lookupTicketNumber.trim(), this.lookupEmail.trim(), this.pickedFile).subscribe({
      next: (s) => {
        this.pickedFile = null;
        const el = document.getElementById('hd-public-file') as HTMLInputElement | null;
        if (el) el.value = '';
        this.applyStatus(s);
      },
      error: (err) => this.fail(err, 'Could not attach the file'),
    });
  }

  openFile(id: number, name: string, type: string): void {
    this.publicHelpDeskApi.fileBlob(id, this.lookupTicketNumber.trim(), this.lookupEmail.trim()).subscribe({
      next: (b) => {
        const url = URL.createObjectURL(b);
        if (type === 'text/plain') {
          const a = document.createElement('a');
          a.href = url;
          a.download = name;
          a.click();
        } else {
          window.open(url, '_blank');
        }
      },
      error: (err) => this.fail(err, 'Could not open the file'),
    });
  }

  sendRating(): void {
    if (!this.ratingChoice) return;
    this.error = null;
    this.loading = true;
    this.publicHelpDeskApi.rate(this.lookupTicketNumber.trim(), this.lookupEmail.trim(), this.ratingChoice, this.ratingComment.trim() || null).subscribe({
      next: (s) => { this.ratingChoice = 0; this.ratingComment = ''; this.applyStatus(s); },
      error: (err) => this.fail(err, 'Could not save your rating'),
    });
  }

  stars(n: number): string { return '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n)); }

  statusLabel(s: string): string {
    switch (s) {
      case 'IN_PROGRESS': return 'In progress';
      case 'WAITING': return 'Waiting for your reply';
      case 'OPEN': return 'Received';
      case 'RESOLVED': return 'Resolved';
      case 'CLOSED': return 'Closed';
      case 'MERGED': return 'Combined with another ticket';
      default: return s;
    }
  }

  statusClass(s: string): string {
    switch (s) {
      case 'OPEN':
      case 'WAITING':
        return 'badge-warning';
      case 'IN_PROGRESS':
        return 'badge-secondary';
      case 'RESOLVED':
        return 'badge-success';
      default:
        return 'badge-outline';
    }
  }
}
