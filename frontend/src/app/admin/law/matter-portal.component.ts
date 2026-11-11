import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LawApiService, PortalAdmin, PortalInviteResult } from '../../core/api/law-api.service';

import { T } from '../../core/i18n/t';
/**
 * The Client portal tab of a matter: whether the client can see this matter in the portal and what they may see and do, who at the client
 * may sign in, the short progress note, which deadlines the client sees, and the messages. The server decides who may change what; the
 * controls follow the flags it sends.
 */
@Component({
  selector: 'app-law-matter-portal',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="info">{{ info }}</div>
    <div *ngIf="!p && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

    <ng-container *ngIf="p">
      <div class="card mb-3" style="padding:1rem;" *ngIf="!p.firmPortalEnabled">
        <strong>{{ 'The client portal is switched off for the firm.' | translate }}</strong>
        <div class="text-muted">An admin can switch it on in Law Firm &gt; Settings. Until then no client can sign in.</div>
      </div>

      <!-- what the client sees -->
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveSettings()">
        <h4 class="mb-2">{{ 'What the client sees on this matter' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="en" [(ngModel)]="f.enabled" [disabled]="!p.canManage || !p.firmPortalEnabled" /> <strong>{{ 'Open this matter to the client portal' | translate }}</strong></label>
        <div style="display:grid; gap:0.35rem; margin-left:1.5rem;" [style.opacity]="f.enabled ? 1 : 0.55">
          <label style="display:flex; gap:0.5rem; align-items:center;"><input type="checkbox" name="sd" [(ngModel)]="f.showDeadlines" [disabled]="!p.canManage" /> {{ 'Important dates (only the deadlines you tick below)' | translate }}</label>
          <label style="display:flex; gap:0.5rem; align-items:center;"><input type="checkbox" name="sc" [(ngModel)]="f.showDocuments" [disabled]="!p.canManage" /> {{ 'Documents marked "shared with the client" (never confidential ones)' | translate }}</label>
          <label style="display:flex; gap:0.5rem; align-items:center;"><input type="checkbox" name="si" [(ngModel)]="f.showInvoices" [disabled]="!p.canManage" /> {{ 'Issued invoices and what is still to pay (no online payment)' | translate }}</label>
          <label style="display:flex; gap:0.5rem; align-items:center;"><input type="checkbox" name="st" [(ngModel)]="f.showTrust" [disabled]="!p.canManage" /> {{ 'Money held for the client in the trust account (posted entries only)' | translate }}</label>
          <label style="display:flex; gap:0.5rem; align-items:center;"><input type="checkbox" name="au" [(ngModel)]="f.allowUpload" [disabled]="!p.canManage" /> {{ 'Let the client send documents (you review each one before it joins the file)' | translate }}</label>
          <label style="display:flex; gap:0.5rem; align-items:center;"><input type="checkbox" name="am" [(ngModel)]="f.allowMessages" [disabled]="!p.canManage" /> {{ 'Messages between the client and the team' | translate }}</label>
        </div>
        <div class="text-muted mt-2" style="font-size:0.85rem;" *ngIf="!p.canManage">{{ 'Only the lead lawyer, a manager or an admin can change this.' | translate }}</div>
        <div class="mt-2" *ngIf="p.canManage"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button></div>
      </form>

      <!-- progress note -->
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="postUpdate()">
        <h4 class="mb-1">{{ 'Progress note for the client' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'A few plain words about where the matter stands. It appears at the top of the client\\'s page. Leave it empty to remove it.' | translate }}<span *ngIf="p.clientUpdateAt"> {{ 'Last updated {p0} by {p1}.' | translate: { p0: (p.clientUpdateAt | date: 'd MMM y, HH:mm'), p1: (p.clientUpdateBy) } }}</span></div>
        <textarea class="form-control mb-2" rows="3" name="up" maxlength="2000" [(ngModel)]="note" [disabled]="!p.canWork"></textarea>
        <button class="btn btn-primary" type="submit" [disabled]="busy || !p.canWork">{{ 'Save the note' | translate }}</button>
      </form>

      <!-- people -->
      <div class="card mb-3" style="padding:1rem;">
        <h4 class="mb-1">{{ 'People who can sign in for {p0}' | translate: { p0: (p.clientName) } }}</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'A person signs in with their own e-mail and password. They see every matter of this client that is open to the portal.' | translate }}</div>
        <table class="stack table-sm" *ngIf="p.accounts.length">
          <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'E-mail' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Last sign-in' | translate }}</th><th></th></tr></thead>
          <tbody><tr *ngFor="let a of p.accounts">
            <td data-label="Name">{{ a.name }}</td><td data-label="E-mail">{{ a.email }}</td>
            <td data-label="Status">{{ !a.active ? 'Switched off' : a.locked ? 'Locked (too many wrong passwords)' : !a.accepted ? ('Invited, not yet signed up' | translate) : ('Active' | translate) }}
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="a.active && !a.accepted && a.inviteExpiresAt">{{ 'link valid until {p0}' | translate: { p0: (a.inviteExpiresAt | date: 'd MMM y') } }}</div></td>
            <td data-label="Last sign-in">{{ a.lastLoginAt | date: 'd MMM y, HH:mm' }}</td>
            <td style="white-space:nowrap;" *ngIf="p.canManage">
              <button class="btn btn-sm btn-outline-secondary" *ngIf="a.active" (click)="reinvite(a.id)" [title]="'Makes a new one-time link. Use it for a forgotten password too.' | translate">{{ a.accepted ? ('New link / reset password' | translate) : ('New link' | translate) }}</button>
              <button class="btn btn-sm btn-outline-secondary" *ngIf="a.active && a.locked" (click)="setActive(a.id, true)">{{ 'Unlock' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" *ngIf="a.active" (click)="setActive(a.id, false)">{{ 'Switch off' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" *ngIf="!a.active" (click)="setActive(a.id, true)">{{ 'Switch on' | translate }}</button>
            </td>
          </tr></tbody>
        </table>
        <div class="text-muted" *ngIf="!p.accounts.length">{{ 'Nobody can sign in for this client yet.' | translate }}</div>

        <div class="alert alert-warning mt-2" *ngIf="invite">
          <div>{{ invite.emailNote }}</div>
          <div class="mt-1"><strong>{{ 'One-time link for {p0}' | translate: { p0: (invite.account.name) } }}</strong> {{ '(shown only now; it works once and expires in 7 days):' | translate }}</div>
          <input class="form-control mt-1" readonly [value]="invite.link" (focus)="$any($event.target).select()" />
        </div>

        <form class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="p.canManage && p.firmPortalEnabled" (ngSubmit)="sendInvite()">
          <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="in" [(ngModel)]="inv.name" /></div>
          <div class="form-group" style="min-width:14rem;"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="ie" [(ngModel)]="inv.email" /></div>
          <div class="form-group"><button class="btn btn-primary" type="submit" [disabled]="busy || !inv.name.trim() || !inv.email.trim()">{{ 'Invite' | translate }}</button></div>
        </form>
      </div>

      <!-- deadlines -->
      <div class="card mb-3" style="padding:1rem;">
        <h4 class="mb-1">{{ 'Dates the client can see' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Tick the deadlines and court dates to show. Only the title and date are shown; your notes stay private.' | translate }}</div>
        <table class="table-sm" *ngIf="p.deadlines.length"><tbody>
          <tr *ngFor="let d of p.deadlines">
            <td style="width:2rem;"><input type="checkbox" [checked]="d.visibleToClient" [disabled]="!p.canWork || busy" (change)="setVisible(d.id, $any($event.target).checked)" /></td>
            <td>{{ d.title }}<span class="text-muted"> · {{ d.dueDate | date: 'd MMM y' }}<span *ngIf="d.status === 'DONE'"> {{ '· done' | translate }}</span></span></td>
          </tr></tbody></table>
        <div class="text-muted" *ngIf="!p.deadlines.length">{{ 'This matter has no deadlines.' | translate }}</div>
      </div>

      <!-- messages -->
      <div class="card" style="padding:1rem;">
        <h4 class="mb-2">{{ 'Messages' | translate }} <span class="badge" *ngIf="p.unread">{{ '{p0} new' | translate: { p0: (p.unread) } }}</span></h4>
        <div class="text-muted mb-2" *ngIf="!p.allowMessages">{{ 'Messages are not switched on for this matter.' | translate }}</div>
        <div *ngFor="let x of p.messages" class="mb-2" [style.text-align]="x.fromClient ? 'left' : 'right'">
          <div style="display:inline-block; max-width:85%; text-align:left; padding:0.5rem 0.75rem; border-radius:0.5rem; background:rgba(127,127,127,0.14);">
            <div style="white-space:pre-wrap;">{{ x.body }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ x.senderName }}{{ x.fromClient ? ' (client)' : '' }} · {{ x.createdAt | date: 'd MMM y, HH:mm' }}<span *ngIf="x.fromClient && !x.read"> {{ '· new' | translate }}</span></div>
          </div>
        </div>
        <div class="text-muted mb-2" *ngIf="p.allowMessages && !p.messages.length">{{ 'No messages yet.' | translate }}</div>
        <div class="flex gap-2" *ngIf="p.unread && p.canWork"><button class="btn btn-sm btn-outline-secondary" (click)="markRead()">{{ 'Mark as read' | translate }}</button></div>
        <form class="mt-2" *ngIf="p.allowMessages && p.enabled && p.canWork" (ngSubmit)="reply()">
          <textarea class="form-control mb-2" rows="3" name="rp" maxlength="2000" [(ngModel)]="replyText" placeholder="Write to the client…"></textarea>
          <button class="btn btn-primary" type="submit" [disabled]="busy || !replyText.trim()">{{ 'Send' | translate }}</button>
        </form>
      </div>
    </ng-container>
  `,
})
export class MatterPortalComponent implements OnChanges {
  @Input() matterId!: number;
  @Output() changed = new EventEmitter<void>();

  p: PortalAdmin | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  f = { enabled: false, showDeadlines: true, showDocuments: true, showInvoices: false, showTrust: false, allowUpload: false, allowMessages: false };
  note = '';
  inv = { name: '', email: '' };
  invite: PortalInviteResult | null = null;
  replyText = '';

  constructor(private api: LawApiService) {}

  ngOnChanges(): void { this.load(); }

  private apply(p: PortalAdmin): void {
    this.p = p;
    this.f = { enabled: p.enabled, showDeadlines: p.showDeadlines, showDocuments: p.showDocuments, showInvoices: p.showInvoices, showTrust: p.showTrust, allowUpload: p.allowUpload, allowMessages: p.allowMessages };
    this.note = p.clientUpdate ?? '';
  }

  load(): void {
    this.api.matterPortal(this.matterId).subscribe({ next: (p) => { this.apply(p); this.error = null; }, error: (e) => (this.error = e?.error?.message ?? T('Could not load the client portal')) });
  }

  private run(call: import('rxjs').Observable<PortalAdmin>, done: string | null): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (p) => { this.busy = false; this.apply(p); this.info = done; this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); this.load(); },
    });
  }

  saveSettings(): void { this.invite = null; this.run(this.api.saveMatterPortal(this.matterId, this.f), T('Saved.')); }
  postUpdate(): void { this.run(this.api.postPortalUpdate(this.matterId, this.note), T('Saved.')); }
  setVisible(deadlineId: number, visible: boolean): void { this.run(this.api.setDeadlinePortal(deadlineId, visible), null); }
  setActive(accountId: number, active: boolean): void { this.invite = null; this.run(this.api.setPortalAccountActive(this.matterId, accountId, active), active ? T('Switched on.') : T('Switched off. Their sessions have ended.')); }
  markRead(): void { this.run(this.api.readPortalMessages(this.matterId), null); }

  reply(): void {
    const text = this.replyText.trim();
    if (!text) return;
    this.run(this.api.replyPortal(this.matterId, text), T('Sent.'));
    this.replyText = '';
  }

  private runInvite(call: import('rxjs').Observable<PortalInviteResult>): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (r) => { this.busy = false; this.invite = r; this.inv = { name: '', email: '' }; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }

  sendInvite(): void { this.runInvite(this.api.invitePortal(this.matterId, this.inv.name.trim(), this.inv.email.trim())); }
  reinvite(accountId: number): void { this.runInvite(this.api.reinvitePortal(this.matterId, accountId)); }
}
