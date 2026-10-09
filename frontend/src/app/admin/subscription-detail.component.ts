import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SubscriptionApiService } from '../core/api/subscription-api.service';
import { openPdfForPrint } from '../core/download-file';
import { Invoice, Subscription } from '../core/models';

/**
 * Admin > Sales > Subscriptions > detail — `/admin/subscriptions/:id`. Shows the subscription's
 * own state plus every invoice the daily billing sweep (or a manual "Bill now") has generated for
 * it (see backend SubscriptionService.billingHistory/InvoiceService.generateForSubscription).
 *
 * <p>Pause/resume toggle between ACTIVE and PAUSED; cancel is one-way into CANCELLED (see backend
 * Subscription's javadoc on {@code SubscriptionStatus}).
 *
 * <p>Fourier: "Print also subscription" — "Print" opens an A4 PDF of this subscription's own
 * terms plus its billing history in a new tab (see {@code SubscriptionService.pdf}), same
 * convention as the Invoice/Sale Order detail screens' own "Print" buttons.
 */
@Component({
  selector: 'app-subscription-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Subscription{{ sub ? ' — ' + sub.customerName : '' }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-primary" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
          <a class="btn btn-link" routerLink="/admin/subscriptions">Back to subscriptions</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="sub as s">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div><strong>Customer:</strong> {{ s.customerName }}</div>
          <div><strong>Plan:</strong> {{ s.planName || '—' }}</div>
          <div><strong>Price:</strong> {{ s.price | number: '1.2-2' }}</div>
          <div><strong>Interval:</strong> {{ s.billingInterval }}</div>
          <!-- Fourier: "may be some subscription will be paid after service other will be prepaid" -->
          <div><strong>Billing timing:</strong> {{ s.billInAdvance ? 'Prepaid' : 'Paid after service' }}</div>
          <div><strong>Status:</strong> <span class="badge" [ngClass]="statusClass(s.status)">{{ s.status }}</span></div>
          <div><strong>Start date:</strong> {{ s.startDate }}</div>
          <div *ngIf="s.status !== 'CANCELLED'"><strong>Next billing:</strong> {{ s.nextBillingDate }}</div>
          <div *ngIf="s.lastBilledAt"><strong>Last billed:</strong> {{ s.lastBilledAt | date: 'medium' }}</div>
          <div *ngIf="s.cancelledAt"><strong>Cancelled:</strong> {{ s.cancelledAt | date: 'medium' }}</div>
        </div>
        <div class="text-muted mt-2" *ngIf="s.notes">{{ s.notes }}</div>
        <div class="text-muted mt-2" *ngIf="s.cancelReason">Cancel reason: {{ s.cancelReason }}</div>

        <div class="flex gap-2 mt-3" *ngIf="s.status !== 'CANCELLED'">
          <button class="btn btn-outline-primary" (click)="billNow()" [disabled]="s.status !== 'ACTIVE' || busy">Bill now</button>
          <button class="btn btn-outline-secondary" (click)="pause()" *ngIf="s.status === 'ACTIVE'" [disabled]="busy">Pause</button>
          <button class="btn btn-outline-secondary" (click)="resume()" *ngIf="s.status === 'PAUSED'" [disabled]="busy">Resume</button>
          <button class="btn btn-outline-danger" (click)="showCancelForm = !showCancelForm" [disabled]="busy">Cancel subscription</button>
        </div>

        <div class="card mt-3" style="padding:1rem; max-width:420px;" *ngIf="showCancelForm">
          <div class="form-group">
            <label class="form-label">Reason (optional)</label>
            <input class="form-control" [(ngModel)]="cancelReason" name="cancelReason" />
          </div>
          <div class="flex gap-2">
            <button class="btn btn-danger" (click)="cancel()" [disabled]="busy">Confirm cancel</button>
            <button class="btn btn-link" (click)="showCancelForm = false">Never mind</button>
          </div>
        </div>
      </div>

      <h4 class="mt-3 mb-2">Billing history</h4>
      <table>
        <thead>
          <tr>
            <th>Invoice #</th>
            <th>Billed on</th>
            <th>Period covered</th>
            <th>Status</th>
            <th class="text-end">Total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let inv of history">
            <td>{{ inv.invoiceNumber }}</td>
            <td>{{ inv.invoiceDate | date: 'medium' }}</td>
            <!-- Fourier: "create an invoice from the last end date" — each row's own cycle, so it's visually obvious one invoice's period picks up where the previous one's left off rather than repeating it. -->
            <td>{{ inv.periodStart ? (inv.periodStart | date: 'mediumDate') + ' – ' + (inv.periodEnd | date: 'mediumDate') : '—' }}</td>
            <td><span class="badge" [ngClass]="inv.status === 'ISSUED' ? 'badge-success' : 'badge-secondary'">{{ inv.status }}</span></td>
            <td class="text-end">{{ inv.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/invoices', inv.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="history.length === 0">
            <td colspan="6" class="text-muted">No invoices generated for this subscription yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SubscriptionDetailComponent implements OnInit {
  id!: number;
  sub: Subscription | null = null;
  history: Invoice[] = [];
  error: string | null = null;
  busy = false;
  printing = false;
  showCancelForm = false;
  cancelReason: string | null = null;

  constructor(private route: ActivatedRoute, private subscriptionApi: SubscriptionApiService) {}

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadAll();
  }

  private loadAll(): void {
    this.subscriptionApi.getById(this.id).subscribe({
      next: (s) => (this.sub = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load subscription'),
    });
    this.subscriptionApi.billingHistory(this.id).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load billing history'),
    });
  }

  billNow(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.billNow(this.id).subscribe({
      next: () => {
        this.busy = false;
        this.loadAll();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to bill subscription';
      },
    });
  }

  pause(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.pause(this.id).subscribe({
      next: (s) => {
        this.busy = false;
        this.sub = s;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to pause subscription';
      },
    });
  }

  resume(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.resume(this.id).subscribe({
      next: (s) => {
        this.busy = false;
        this.sub = s;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to resume subscription';
      },
    });
  }

  cancel(): void {
    this.error = null;
    this.busy = true;
    this.subscriptionApi.cancel(this.id, this.cancelReason || null).subscribe({
      next: (s) => {
        this.busy = false;
        this.sub = s;
        this.showCancelForm = false;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to cancel subscription';
      },
    });
  }

  print(): void {
    this.printing = true;
    this.subscriptionApi.pdf(this.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to open subscription PDF';
      },
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'badge-success';
      case 'PAUSED':
        return 'badge-secondary';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
