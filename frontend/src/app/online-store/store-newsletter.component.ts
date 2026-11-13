import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListState, emptyFilter } from '../core/api/list-state';
import { OnlineStoreApiService, StoreSubscriber } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';
import { downloadBlob } from '../core/download-file';

/**
 * Online Store > Newsletter: the e-mail addresses that asked for the shop's newsletter. Only confirmed ones (the link in the e-mail was opened) are
 * subscribers. The confirmed addresses can be downloaded as a CSV file, and a newsletter can be sent to them from Send a newsletter.
 */
@Component({
  selector: 'app-store-newsletter',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Newsletter' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-primary nl-export" (click)="download()" [disabled]="busy">{{ 'Download confirmed addresses (CSV)' | translate }}</button>
          <a class="btn btn-success nl-compose" routerLink="/admin/online-store/newsletters">{{ 'Send a newsletter' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/reviews">{{ 'Reviews' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'Visitors ask for the newsletter at the bottom of the shop page. An address counts only once its owner opens the link in the confirmation e-mail.' | translate }}</p>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm nl-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">
          {{ t.label | translate }}<span *ngIf="t.key && counts[t.key] !== undefined"> ({{ counts[t.key] }})</span>
        </button>
        <input class="form-control nl-search" style="width:220px;" [placeholder]="'Search an address' | translate" [(ngModel)]="q" name="nlq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <table class="table-sm" *ngIf="list.rows.length > 0">
        <thead><tr><th>{{ 'E-mail address' | translate }}</th><th>{{ 'Language' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Asked on' | translate }}</th><th>{{ 'Confirmed on' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of list.rows">
            <td class="nl-email">{{ r.email }}</td>
            <td>{{ r.language | uppercase }}</td>
            <td [style.color]="color(r)">{{ label(r.status) | translate }}</td>
            <td>{{ r.createdAt | date: 'short' }}</td>
            <td>{{ r.confirmedAt | date: 'short' }}</td>
            <td><button class="btn btn-sm btn-outline-danger nl-delete" [disabled]="busy" (click)="remove(r)">{{ 'Remove' | translate }}</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted nl-empty" *ngIf="list.loaded && list.rows.length === 0">{{ 'Nothing to show.' | translate }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreNewsletterComponent implements OnInit {
  tabs = [
    { key: 'CONFIRMED', label: 'Subscribed' },
    { key: 'PENDING', label: 'Not confirmed' },
    { key: 'UNSUBSCRIBED', label: 'Unsubscribed' },
    { key: '', label: 'Everything' },
  ];
  tab = 'CONFIRMED';
  q = '';
  counts: Record<string, number> = {};
  error: string | null = null;
  message: string | null = null;
  busy = false;
  list = new ListState<StoreSubscriber>((f, p, sz) => this.api.subscribersPage(f, p, sz), 'Failed to load the newsletter list');

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  label(s: string): string {
    return ({ CONFIRMED: 'Subscribed', PENDING: 'Not confirmed', UNSUBSCRIBED: 'Unsubscribed' } as Record<string, string>)[s] ?? s;
  }

  color(r: StoreSubscriber): string {
    return r.status === 'CONFIRMED' ? '#15803d' : r.status === 'UNSUBSCRIBED' ? '#6b7280' : '#b45309';
  }

  setTab(t: string): void {
    this.tab = t;
    this.reload();
  }

  reload(): void {
    const f = emptyFilter();
    f.q = this.q;
    f.status = this.tab;
    this.list.apply(f);
    this.api.subscriberCounts().subscribe({ next: (c) => (this.counts = c), error: () => {} });
  }

  download(): void {
    this.busy = true;
    this.api.exportSubscribers().subscribe({
      next: (blob) => {
        this.busy = false;
        downloadBlob(blob, 'newsletter-subscribers.csv');
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }

  remove(r: StoreSubscriber): void {
    if (!confirm(T('Remove {p0} from the list?', { p0: r.email }))) return;
    this.busy = true;
    this.error = null;
    this.api.deleteSubscriber(r.id).subscribe({
      next: () => {
        this.busy = false;
        this.message = T('Removed');
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
