import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OnlineStoreApiService, TextMessage } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Text messages: every text the shop sent, tried to send or skipped (newest first), and a form to send a test message through the
 * provider chosen in the shop settings. Sign-in codes are shown without the code.
 */
@Component({
  selector: 'app-store-texts',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success tx-ok" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Text messages sent' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/orders">{{ 'Online orders' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'Every text the shop sends is listed here, also the ones that failed or were not sent (a number that is not valid, or the daily limit). Choose the provider and the limits in the shop settings. A text goes to a customer only if they ticked the box at checkout, or asked for a sign-in code.' | translate }}</p>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="sendTest()">
        <h4 class="mb-2">{{ 'Send a test message' | translate }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Phone number' | translate }}</label><input class="form-control tx-phone" style="width:200px;" maxlength="40" inputmode="tel" name="phone" [(ngModel)]="phone" /></div>
          <div class="form-group" style="min-width:260px; flex:1;"><label class="form-label">{{ 'Message (optional)' | translate }}</label><input class="form-control" maxlength="200" name="text" [(ngModel)]="text" /></div>
          <div class="form-group"><button class="btn btn-primary tx-send" type="submit" [disabled]="busy || !phone.trim()">{{ 'Send test message' | translate }}</button></div>
        </div>
      </form>

      <div class="card" style="padding:1rem;">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <span class="text-muted">{{ '{p0} messages' | translate: { p0: total } }}</span>
          <button class="btn btn-sm btn-outline-secondary" (click)="load(page)" [disabled]="busy">{{ 'Refresh' | translate }}</button>
        </div>
        <div class="table-wrap">
          <table class="table-sm" *ngIf="items.length">
            <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'Sent to' | translate }}</th><th>{{ 'For' | translate }}</th><th>{{ 'Message' | translate }}</th><th>{{ 'Result' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of items" class="tx-row">
                <td style="white-space:nowrap;">{{ m.at | date: 'short' }}</td>
                <td style="white-space:nowrap;">{{ m.to }}<div class="text-muted" style="font-size:0.75rem;">{{ m.channel === 'WHATSAPP' ? 'WhatsApp' : 'SMS' }}</div></td>
                <td>{{ purposeLabel(m.purpose) | translate }}</td>
                <td style="max-width:420px;">{{ m.body }}</td>
                <td><span class="badge" [ngClass]="m.status === 'SENT' ? 'badge-success' : m.status === 'FAILED' ? 'badge-danger' : 'badge-warning'">{{ statusLabel(m.status) | translate }}</span>
                  <div class="text-muted" style="font-size:0.75rem;" *ngIf="m.error">{{ m.error }}</div></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="text-muted tx-empty" *ngIf="!items.length && !busy">{{ 'No text messages yet.' | translate }}</div>
        <div class="flex gap-2 mt-2 align-center" *ngIf="pages > 1">
          <button class="btn btn-sm btn-outline-secondary" [disabled]="busy || page === 0" (click)="load(page - 1)">{{ 'Previous' | translate }}</button>
          <span class="text-muted">{{ 'Page {p0} of {p1}' | translate: { p0: page + 1, p1: pages } }}</span>
          <button class="btn btn-sm btn-outline-secondary" [disabled]="busy || page + 1 >= pages" (click)="load(page + 1)">{{ 'Next' | translate }}</button>
        </div>
      </div>
    </app-shell>
  `,
})
export class StoreTextsComponent implements OnInit {
  items: TextMessage[] = [];
  page = 0;
  pages = 0;
  total = 0;
  size = 20;
  phone = '';
  text = '';
  busy = false;
  error: string | null = null;
  message: string | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.busy = true;
    this.api.texts(page, this.size).subscribe({
      next: (r) => {
        this.busy = false;
        this.items = r.items;
        this.page = r.page;
        this.pages = r.totalPages;
        this.total = r.total;
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Failed to load the text messages');
      },
    });
  }

  sendTest(): void {
    this.error = null;
    this.message = null;
    this.busy = true;
    this.api.testText(this.phone, this.text).subscribe({
      next: (r) => {
        this.busy = false;
        if (r.status === 'SENT') this.message = T('Test message sent to {p0}.', { p0: r.to ?? this.phone });
        else this.error = T('The test message was not sent: {p0}', { p0: r.error ?? r.status });
        this.load(0);
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('The test message was not sent.');
      },
    });
  }

  purposeLabel(p: string): string {
    switch (p) {
      case 'ORDER': return 'Order update';
      case 'OWNER': return 'Shop alert';
      case 'SIGNIN': return 'Sign-in code';
      case 'TEST': return 'Test';
      default: return p;
    }
  }

  statusLabel(s: string): string {
    switch (s) {
      case 'SENT': return 'Sent';
      case 'FAILED': return 'Failed';
      case 'SKIPPED': return 'Not sent';
      default: return s;
    }
  }
}
