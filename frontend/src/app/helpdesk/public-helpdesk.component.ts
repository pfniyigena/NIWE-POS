import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicHelpDeskApiService } from '../core/api/public-helpdesk-api.service';
import { PublicHelpDeskCategory, PublicHelpDeskTicketConfirmation, PublicHelpDeskTicketStatus } from '../core/models';

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
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center; padding:1rem 0;">
      <div class="card" style="width:480px; padding:2rem;">
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
              <span class="badge" [ngClass]="statusClass(s.status)">{{ s.status }}</span>
            </div>
            <h4 class="mb-1">{{ s.subject }}</h4>
            <div class="text-muted mb-2" style="font-size:0.85rem;">
              {{ s.categoryName || 'Uncategorized' }} · submitted {{ s.createdAt | date: 'medium' }}
            </div>
            <div class="card mt-2" style="padding:0.75rem;" *ngIf="s.resolutionNotes">
              <strong>Resolution:</strong>
              <p style="white-space:pre-wrap; margin:0.25rem 0 0;">{{ s.resolutionNotes }}</p>
            </div>
            <h5 class="mt-3 mb-1">Updates</h5>
            <div class="flex flex-col gap-2">
              <div class="card" style="padding:0.6rem;" *ngFor="let u of s.updates">
                <p style="white-space:pre-wrap; margin:0;">{{ u.body }}</p>
                <div class="text-muted" style="font-size:0.75rem;">{{ u.createdAt | date: 'medium' }}</div>
              </div>
              <p class="text-muted" *ngIf="s.updates.length === 0">No updates yet.</p>
            </div>
            <button type="button" class="btn btn-link mt-2" (click)="status = null">Check another ticket</button>
          </ng-container>
        </ng-container>

        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/login">Staff login</a>
        </p>
      </div>
    </div>
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
