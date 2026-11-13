import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, StoreCampaign, StoreCampaignForm, StoreCampaignInfo, StoreDelivery } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Send a newsletter: write a message (subject, text, an optional button), choose who gets it, send a test to yourself, then send it. It goes out
 * in small batches within a daily limit; this screen shows how far it is, lets you stop it, and lists the addresses that failed so you can try them again.
 */
@Component({
  selector: 'app-store-campaigns',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Send a newsletter' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-primary nc-new" *ngIf="view === 'list'" (click)="newOne()" [disabled]="busy">{{ 'New newsletter' | translate }}</button>
          <button class="btn btn-outline-secondary nc-back" *ngIf="view !== 'list'" (click)="backToList()">{{ 'Back to the list' | translate }}</button>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/newsletter">{{ 'Subscribers' | translate }}</a>
        </div>
      </div>

      <div class="alert alert-warning nc-nomail" *ngIf="info && !info.mailConfigured">{{ 'E-mail is not set up yet, so nothing can be sent. Ask your administrator to set it up.' | translate }}</div>
      <p class="text-muted" *ngIf="info">
        {{ 'People who confirmed the newsletter: {p0} (English {p1}, French {p2}).' | translate: { p0: info.confirmedAll, p1: info.confirmedEn, p2: info.confirmedFr } }}
        <span *ngIf="info.dailyLimit > 0">{{ 'Sent in the last 24 hours: {p0} of at most {p1}.' | translate: { p0: info.sentLast24h, p1: info.dailyLimit } }}</span>
      </p>

      <!-- the list -->
      <ng-container *ngIf="view === 'list'">
        <table class="table-sm" *ngIf="items.length > 0">
          <thead><tr><th>{{ 'Subject' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Sent' | translate }}</th><th>{{ 'Failed' | translate }}</th><th>{{ 'Created' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of items">
              <td class="nc-subject">{{ c.subject }}</td>
              <td [style.color]="color(c)">{{ statusLabel(c) | translate }}</td>
              <td>{{ c.sent }} / {{ c.recipients }}</td>
              <td>{{ c.failed }}</td>
              <td>{{ c.createdAt | date: 'short' }}</td>
              <td><button class="btn btn-sm btn-outline-primary nc-open" (click)="open(c)">{{ c.status === 'DRAFT' ? ('Edit' | translate) : ('Open' | translate) }}</button></td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted nc-empty" *ngIf="loaded && items.length === 0">{{ 'No newsletter yet. Press New newsletter to write the first one.' | translate }}</p>
      </ng-container>

      <!-- writing a draft -->
      <div *ngIf="view === 'edit'" class="nc-form" style="max-width:720px;">
        <div class="mb-2">
          <label>{{ 'Subject' | translate }}</label>
          <input class="form-control nc-f-subject" [(ngModel)]="form.subject" name="subject" maxlength="160" />
        </div>
        <div class="mb-2">
          <label>{{ 'Message' | translate }}</label>
          <textarea class="form-control nc-f-body" rows="10" [(ngModel)]="form.body" name="body" maxlength="5000"></textarea>
          <small class="text-muted">{{ 'A blank line starts a new paragraph. Plain text only. The shop name and each person\'s own unsubscribe link are added at the bottom.' | translate }}</small>
        </div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <div style="flex:1; min-width:200px;">
            <label>{{ 'Button text (optional)' | translate }}</label>
            <input class="form-control" [(ngModel)]="form.buttonLabel" name="buttonLabel" maxlength="60" />
          </div>
          <div style="flex:2; min-width:240px;">
            <label>{{ 'Button link (starts with https:// or /)' | translate }}</label>
            <input class="form-control" [(ngModel)]="form.buttonLink" name="buttonLink" maxlength="300" placeholder="https://" />
          </div>
        </div>
        <div class="mb-3">
          <label>{{ 'Who gets it' | translate }}</label>
          <select class="form-control nc-f-audience" [(ngModel)]="form.audience" name="audience">
            <option value="ALL">{{ 'Everyone who confirmed' | translate }}{{ info ? ' (' + info.confirmedAll + ')' : '' }}</option>
            <option value="EN">{{ 'Only English readers' | translate }}{{ info ? ' (' + info.confirmedEn + ')' : '' }}</option>
            <option value="FR">{{ 'Only French readers' | translate }}{{ info ? ' (' + info.confirmedFr + ')' : '' }}</option>
          </select>
        </div>
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <button class="btn btn-primary nc-save" (click)="save()" [disabled]="busy">{{ 'Save draft' | translate }}</button>
          <button class="btn btn-outline-danger nc-delete" *ngIf="current" (click)="deleteDraft()" [disabled]="busy">{{ 'Delete draft' | translate }}</button>
        </div>
        <div class="card" *ngIf="current" style="padding:1rem;">
          <h5>{{ 'Try it, then send it' | translate }}</h5>
          <p class="text-muted">{{ 'Save the draft first, then send a test to your own address to see how it looks. Sending to everyone cannot be undone, but you can stop it while it is going out.' | translate }}</p>
          <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
            <input class="form-control nc-test-email" style="max-width:280px;" type="email" [(ngModel)]="testEmail" name="testEmail" [placeholder]="'Your e-mail address' | translate" />
            <button class="btn btn-outline-primary nc-test" (click)="sendTest()" [disabled]="busy || !testEmail">{{ 'Send me a test' | translate }}</button>
          </div>
          <button class="btn btn-success nc-send" (click)="send()" [disabled]="busy || (info && !info.mailConfigured)">{{ 'Send to {p0} people' | translate: { p0: audienceCount() } }}</button>
        </div>
      </div>

      <!-- a newsletter that was started -->
      <div *ngIf="view === 'detail' && current" class="nc-detail" style="max-width:720px;">
        <h4>{{ current.subject }}</h4>
        <p><strong [style.color]="color(current)">{{ statusLabel(current) | translate }}</strong>
          <span class="text-muted"> · {{ audienceLabel(current.audience) | translate }} · {{ current.startedAt | date: 'short' }}<span *ngIf="current.finishedAt"> → {{ current.finishedAt | date: 'short' }}</span></span></p>
        <div style="background:#e5e7eb; border-radius:6px; height:14px; overflow:hidden; max-width:480px;">
          <div class="nc-bar" [style.width.%]="percent(current)" style="background:#16a34a; height:14px;"></div>
        </div>
        <p class="mt-2 nc-counts">{{ 'Sent {p0} of {p1}. Waiting {p2}. Failed {p3}. Skipped (left before their turn) {p4}.' | translate: { p0: current.sent, p1: current.recipients, p2: current.pending, p3: current.failed, p4: current.skipped } }}</p>
        <div class="alert alert-warning nc-lasterror" *ngIf="current.lastError">{{ current.lastError }}</div>
        <p class="text-muted" *ngIf="current.status === 'SENDING'">{{ 'It goes out in small batches about every minute. This page updates by itself.' | translate }}</p>
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <button class="btn btn-outline-danger nc-cancel" *ngIf="current.status === 'SENDING'" (click)="cancel()" [disabled]="busy">{{ 'Stop sending' | translate }}</button>
          <button class="btn btn-outline-primary nc-retry" *ngIf="current.failed > 0 && (current.status === 'SENDING' || current.status === 'SENT')" (click)="retry()" [disabled]="busy">{{ 'Try the failed ones again' | translate }}</button>
        </div>
        <div *ngIf="failedList.length > 0">
          <h5>{{ 'Addresses that failed' | translate }}</h5>
          <table class="table-sm"><thead><tr><th>{{ 'E-mail address' | translate }}</th><th>{{ 'Reason' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let d of failedList"><td>{{ d.email }}</td><td>{{ d.error }}</td></tr></tbody></table>
        </div>
        <h5 class="mt-3">{{ 'The message' | translate }}</h5>
        <pre style="white-space:pre-wrap; font-family:inherit;">{{ current.body }}</pre>
        <p *ngIf="current.buttonLabel">{{ 'Button' | translate }}: {{ current.buttonLabel }} → {{ current.buttonLink }}</p>
      </div>
    </app-shell>
  `,
})
export class StoreCampaignsComponent implements OnInit, OnDestroy {
  view: 'list' | 'edit' | 'detail' = 'list';
  items: StoreCampaign[] = [];
  loaded = false;
  info: StoreCampaignInfo | null = null;
  current: StoreCampaign | null = null;
  failedList: StoreDelivery[] = [];
  form: StoreCampaignForm = { subject: '', body: '', buttonLabel: '', buttonLink: '', audience: 'ALL' };
  testEmail = '';
  error: string | null = null;
  message: string | null = null;
  busy = false;
  private timer: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.reload();
    this.timer = setInterval(() => this.tick(), 10000);
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  /** While a newsletter is going out, keep its numbers fresh. */
  private tick(): void {
    if (this.busy) return;
    if (this.view === 'detail' && this.current && this.current.status === 'SENDING') this.refreshCurrent();
    else if (this.view === 'list' && this.items.some((c) => c.status === 'SENDING')) this.reload();
  }

  private fail(e: any): void {
    this.busy = false;
    this.error = e?.error?.message ?? T('That did not work');
  }

  reload(): void {
    this.api.campaigns().subscribe({
      next: (l) => { this.items = l; this.loaded = true; },
      error: (e) => this.fail(e),
    });
    this.api.campaignInfo().subscribe({ next: (i) => (this.info = i), error: () => {} });
  }

  color(c: StoreCampaign): string {
    return c.status === 'SENT' ? '#15803d' : c.status === 'SENDING' ? '#b45309' : c.status === 'CANCELLED' ? '#b91c1c' : '#6b7280';
  }

  statusLabel(c: StoreCampaign): string {
    return ({ DRAFT: 'Draft', SENDING: 'Sending', SENT: 'Sent', CANCELLED: 'Stopped' } as Record<string, string>)[c.status] ?? c.status;
  }

  audienceLabel(a: string): string {
    return a === 'EN' ? 'Only English readers' : a === 'FR' ? 'Only French readers' : 'Everyone who confirmed';
  }

  audienceCount(): number {
    if (!this.info) return 0;
    return this.form.audience === 'EN' ? this.info.confirmedEn : this.form.audience === 'FR' ? this.info.confirmedFr : this.info.confirmedAll;
  }

  percent(c: StoreCampaign): number {
    return c.recipients > 0 ? Math.round(((c.sent + c.failed + c.skipped) * 100) / c.recipients) : 0;
  }

  private clear(): void {
    this.error = null;
    this.message = null;
  }

  newOne(): void {
    this.clear();
    this.current = null;
    this.form = { subject: '', body: '', buttonLabel: '', buttonLink: '', audience: 'ALL' };
    this.view = 'edit';
  }

  open(c: StoreCampaign): void {
    this.clear();
    this.current = c;
    if (c.status === 'DRAFT') {
      this.form = { subject: c.subject, body: c.body, buttonLabel: c.buttonLabel ?? '', buttonLink: c.buttonLink ?? '', audience: c.audience };
      this.view = 'edit';
    } else {
      this.view = 'detail';
      this.loadFailed();
    }
  }

  backToList(): void {
    this.clear();
    this.view = 'list';
    this.current = null;
    this.reload();
  }

  private refreshCurrent(): void {
    if (!this.current) return;
    this.api.campaign(this.current.id).subscribe({
      next: (c) => { this.current = c; this.loadFailed(); },
      error: () => {},
    });
  }

  private loadFailed(): void {
    if (!this.current || this.current.failed === 0) { this.failedList = []; return; }
    this.api.campaignDeliveries(this.current.id, 'FAILED').subscribe({ next: (l) => (this.failedList = l), error: () => {} });
  }

  save(): void {
    this.clear();
    this.busy = true;
    const call = this.current ? this.api.updateCampaign(this.current.id, this.form) : this.api.createCampaign(this.form);
    call.subscribe({
      next: (c) => {
        this.busy = false;
        this.current = c;
        this.message = T('Saved');
      },
      error: (e) => this.fail(e),
    });
  }

  deleteDraft(): void {
    if (!this.current || !confirm(T('Delete this draft?'))) return;
    this.clear();
    this.busy = true;
    this.api.deleteCampaign(this.current.id).subscribe({
      next: () => { this.busy = false; this.message = T('Deleted'); this.backToList(); this.message = T('Deleted'); },
      error: (e) => this.fail(e),
    });
  }

  sendTest(): void {
    if (!this.current) return;
    this.clear();
    this.busy = true;
    this.api.testCampaign(this.current.id, this.testEmail).subscribe({
      next: () => { this.busy = false; this.message = T('Test sent to {p0}. Check your inbox.', { p0: this.testEmail }); },
      error: (e) => this.fail(e),
    });
  }

  send(): void {
    if (!this.current) return;
    const n = this.audienceCount();
    if (!confirm(T('Send "{p0}" to {p1} people now? You can stop it while it is going out, but what was sent cannot be taken back.', { p0: this.form.subject, p1: n }))) return;
    this.clear();
    this.busy = true;
    this.api.sendCampaign(this.current.id).subscribe({
      next: (c) => { this.busy = false; this.current = c; this.view = 'detail'; this.message = T('Sending has started.'); this.failedList = []; this.api.campaignInfo().subscribe({ next: (i) => (this.info = i), error: () => {} }); },
      error: (e) => this.fail(e),
    });
  }

  cancel(): void {
    if (!this.current || !confirm(T('Stop sending? What was already sent stays sent; the rest will not be sent.'))) return;
    this.clear();
    this.busy = true;
    this.api.cancelCampaign(this.current.id).subscribe({
      next: (c) => { this.busy = false; this.current = c; this.message = T('Stopped'); },
      error: (e) => this.fail(e),
    });
  }

  retry(): void {
    if (!this.current) return;
    this.clear();
    this.busy = true;
    this.api.retryCampaign(this.current.id).subscribe({
      next: (c) => { this.busy = false; this.current = c; this.failedList = []; this.message = T('The failed addresses are in the queue again.'); },
      error: (e) => this.fail(e),
    });
  }
}
