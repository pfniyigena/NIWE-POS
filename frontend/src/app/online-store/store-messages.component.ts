import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListState, emptyFilter } from '../core/api/list-state';
import { ContactMessage, OnlineStoreApiService } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Messages: what visitors wrote in the shop's contact form. A message is New until someone answers (by e-mail, phone or WhatsApp, outside
 * this screen) and marks it Answered, with a note of what was said.
 */
@Component({
  selector: 'app-store-messages',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Messages' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/pages">{{ 'Shop pages' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'Messages sent from the contact form of the shop. Answer the customer by e-mail, phone or WhatsApp, then mark the message as answered.' | translate }}</p>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button *ngFor="let t of tabs" class="btn btn-sm msg-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">
          {{ t.label | translate }}<span *ngIf="t.key === 'NEW' && news > 0"> ({{ news }})</span>
        </button>
        <input class="form-control msg-search" style="width:220px;" [placeholder]="'Search name, phone, e-mail, text' | translate" [(ngModel)]="q" name="mq" (keyup.enter)="reload()" (blur)="reload()" />
      </div>

      <div class="card msg-card mb-2" *ngFor="let m of list.rows" style="padding:0.75rem;">
        <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <strong class="msg-name">{{ m.name }}</strong>
            <span class="badge msg-status" style="margin-left:0.4rem;" [style.color]="m.status === 'NEW' ? '#b45309' : '#15803d'">{{ (m.status === 'NEW' ? 'New' : 'Answered') | translate }}</span>
            <div class="text-muted" style="font-size:0.85rem;">
              <span *ngIf="m.phone">📞 {{ m.phone }}</span><span *ngIf="m.phone && m.email"> · </span><span *ngIf="m.email">✉️ {{ m.email }}</span>
              <span *ngIf="m.orderCode"> · {{ 'Order {p0}' | translate: { p0: m.orderCode } }}</span>
            </div>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ m.createdAt | date: 'short' }}<span *ngIf="m.handledBy"> · {{ m.handledBy }}</span></div>
        </div>
        <div class="msg-subject mt-1" *ngIf="m.subject"><strong>{{ m.subject }}</strong></div>
        <p class="msg-text mt-1" style="white-space:pre-line; margin-bottom:0;">{{ m.message }}</p>
        <p class="text-muted msg-note mt-1" *ngIf="m.note && m.status === 'ANSWERED'" style="margin-bottom:0;">{{ 'Note' | translate }}: {{ m.note }}</p>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:center;">
          <a class="btn btn-sm btn-outline-secondary msg-mail" *ngIf="m.email" [href]="mailto(m)">{{ 'Reply by e-mail' | translate }}</a>
          <a class="btn btn-sm btn-outline-secondary msg-wa" *ngIf="m.phone" [href]="wa(m)" target="_blank" rel="noopener">WhatsApp</a>
          <ng-container *ngIf="m.status === 'NEW'">
            <input class="form-control msg-note-in" style="width:240px;" maxlength="500" [name]="'n' + m.id" [(ngModel)]="notes[m.id]" [placeholder]="'Note (optional)' | translate" />
            <button class="btn btn-sm btn-primary msg-done" [disabled]="busy" (click)="done(m)">{{ 'Mark as answered' | translate }}</button>
          </ng-container>
          <button class="btn btn-sm btn-outline-secondary msg-reopen" *ngIf="m.status === 'ANSWERED'" [disabled]="busy" (click)="reopen(m)">{{ 'Mark as new' | translate }}</button>
          <button class="btn btn-sm btn-outline-danger msg-delete" [disabled]="busy" (click)="remove(m)">{{ 'Delete' | translate }}</button>
        </div>
      </div>
      <p class="text-muted msg-empty" *ngIf="list.loaded && list.rows.length === 0">{{ tab === 'NEW' ? ('No new message.' | translate) : ('Nothing to show.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class StoreMessagesComponent implements OnInit, OnDestroy {
  tabs = [
    { key: 'NEW', label: 'New' },
    { key: 'ANSWERED', label: 'Answered' },
    { key: '', label: 'Everything' },
  ];
  tab = 'NEW';
  q = '';
  news = 0;
  notes: Record<number, string> = {};
  error: string | null = null;
  message: string | null = null;
  busy = false;
  list = new ListState<ContactMessage>((f, p, sz) => this.api.messagesPage(f, p, sz), 'Failed to load the messages');
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.reload();
    this.poll = setInterval(() => {
      if (!this.busy) this.list.load();
      this.count();
    }, 20000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
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
    this.count();
  }

  count(): void {
    this.api.pendingCount().subscribe({ next: (c) => (this.news = c.messagesNew ?? 0), error: () => {} });
  }

  mailto(m: ContactMessage): string {
    const subject = 'Re: ' + (m.subject || (m.language === 'fr' ? 'Votre message' : 'Your message'));
    return 'mailto:' + (m.email ?? '') + '?subject=' + encodeURIComponent(subject);
  }

  wa(m: ContactMessage): string {
    return 'https://wa.me/' + (m.phone ?? '').replace(/[^0-9]/g, '');
  }

  private run(call: import('rxjs').Observable<unknown>, done: string): void {
    this.busy = true;
    this.error = null;
    this.message = null;
    call.subscribe({
      next: () => { this.busy = false; this.message = done; this.reload(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); this.list.load(); },
    });
  }

  done(m: ContactMessage): void {
    this.run(this.api.messageAnswered(m.id, this.notes[m.id] ?? ''), T('Marked as answered.'));
  }

  reopen(m: ContactMessage): void {
    this.run(this.api.messageReopen(m.id), T('Marked as new.'));
  }

  remove(m: ContactMessage): void {
    if (!confirm(T('Delete this message for good?'))) return;
    this.run(this.api.deleteMessage(m.id), T('The message was deleted.'));
  }
}
