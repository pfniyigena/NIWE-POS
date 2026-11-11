import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PortalApiService, PortalMatter } from '../core/api/portal-api.service';
import { STATUS_LABEL } from '../core/api/law-api.service';
import { downloadBlob } from '../core/download-file';
import { PortalLayoutComponent } from './portal-layout.component';

import { T } from '../core/i18n/t';
/** One matter in the client portal: progress note, dates, shared documents, what the client sent, invoices, money held, and messages. Only what the firm switched on is shown. */
@Component({
  selector: 'app-portal-matter',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, PortalLayoutComponent],
  template: `
    <app-portal-layout>
      <a routerLink="/portal">&larr; All my matters</a>
      <div class="alert alert-danger mt-2" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success mt-2" *ngIf="info">{{ info }}</div>
      <div *ngIf="!m && !error" class="text-muted mt-2">{{ 'Loading…' | translate }}</div>

      <ng-container *ngIf="m">
        <div class="mt-2 mb-3">
          <h3>{{ m.number }} · {{ m.title }}</h3>
          <div class="text-muted" style="font-size:0.9rem;">
            <span class="badge">{{ status(m.status) }}</span>
            <span *ngIf="m.practiceArea"> · {{ m.practiceArea }}</span>
            <span *ngIf="m.leadName"> {{ '· Your lawyer: {p0}' | translate: { p0: (m.leadName) } }}</span>
            <span *ngIf="m.openedDate"> {{ '· Opened {p0}' | translate: { p0: (m.openedDate | date: 'd MMM y') } }}</span>
            <span *ngIf="m.court"> · {{ m.court }}<span *ngIf="m.courtCaseNumber"> ({{ m.courtCaseNumber }})</span></span>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.clientUpdate">
          <h4 class="mb-1">{{ 'Latest news' | translate }}</h4>
          <div style="white-space:pre-wrap;">{{ m.clientUpdate }}</div>
          <div class="text-muted mt-1" style="font-size:0.8rem;" *ngIf="m.clientUpdateAt">{{ m.clientUpdateAt | date: 'd MMM y' }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.features.deadlines">
          <h4 class="mb-2">{{ 'Important dates' | translate }}</h4>
          <table class="table-sm" *ngIf="m.deadlines.length"><tbody>
            <tr *ngFor="let d of m.deadlines" [style.opacity]="d.status === 'DONE' ? 0.6 : 1">
              <td style="white-space:nowrap;"><strong>{{ d.dueDate | date: 'd MMM y' }}</strong><span *ngIf="d.dueTime"> {{ d.dueTime | slice: 0 : 5 }}</span></td>
              <td>{{ d.title }}</td><td>{{ d.status === 'DONE' ? ('Done' | translate) : '' }}</td>
            </tr></tbody></table>
          <div class="text-muted" *ngIf="!m.deadlines.length">{{ 'No dates to show at the moment.' | translate }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.features.documents">
          <h4 class="mb-2">{{ 'Documents from the firm' | translate }}</h4>
          <table class="table-sm" *ngIf="m.documents.length"><tbody>
            <tr *ngFor="let d of m.documents">
              <td><strong>{{ d.name }}</strong><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} · {p1} · updated {p2}' | translate: { p0: (d.fileName), p1: (size(d.sizeBytes)), p2: (d.updatedAt | date: 'd MMM y') } }}</div></td>
              <td style="text-align:right;"><button class="btn btn-sm btn-outline-primary" (click)="download(d.id, d.fileName)">{{ 'Download' | translate }}</button></td>
            </tr></tbody></table>
          <div class="text-muted" *ngIf="!m.documents.length">{{ 'Nothing has been shared with you yet.' | translate }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.features.upload">
          <h4 class="mb-2">{{ 'Send a document to the firm' | translate }}</h4>
          <form class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;" (ngSubmit)="upload(fileInput)">
            <div class="form-group" style="min-width:14rem;"><label class="form-label">{{ 'File' | translate }} <span class="text-muted">{{ '(up to 25 MB)' | translate }}</span></label><input #fileInput class="form-control" type="file" name="file" (change)="file = pick($event)" /></div>
            <div class="form-group" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Name' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label><input class="form-control" name="n" [(ngModel)]="uploadName" /></div>
            <div class="form-group"><button class="btn btn-primary" type="submit" [disabled]="busy || !file">{{ 'Send' | translate }}</button></div>
          </form>
          <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'A lawyer looks at each document before it is added to your file.' | translate }}</div>
          <table class="table-sm mt-2" *ngIf="m.uploads.length"><tbody>
            <tr *ngFor="let u of m.uploads">
              <td><a href="#" (click)="$event.preventDefault(); download(u.id, u.fileName)">{{ u.name }}</a><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} · {p1} · sent {p2}' | translate: { p0: (u.fileName), p1: (size(u.sizeBytes)), p2: (u.createdAt | date: 'd MMM y') } }}</div></td>
              <td>{{ u.reviewStatus === 'PENDING' ? 'Waiting for the firm' : u.reviewStatus === 'ACCEPTED' ? ('Received' | translate) : ('Not accepted' | translate) }}
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="u.reviewNote">{{ u.reviewNote }}</div></td>
            </tr></tbody></table>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.features.invoices">
          <h4 class="mb-2">{{ 'Invoices' | translate }}</h4>
          <table class="table-sm" *ngIf="m.invoices.length">
            <thead><tr><th>{{ 'Invoice' | translate }}</th><th>{{ 'Date' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'To pay' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let i of m.invoices">
              <td>{{ i.number }}</td><td>{{ i.date | date: 'd MMM y' }}</td><td>{{ i.dueDate | date: 'd MMM y' }}</td>
              <td class="text-end">{{ i.total | number: '1.0-2' }}</td><td class="text-end">{{ i.paid | number: '1.0-2' }}</td>
              <td class="text-end"><strong>{{ i.outstanding | number: '1.0-2' }}</strong></td></tr></tbody></table>
          <div class="text-muted" *ngIf="!m.invoices.length">{{ 'No invoices yet.' | translate }}</div>
          <div class="text-muted mt-1" style="font-size:0.8rem;">{{ 'To pay an invoice, contact the firm. Payments are not made on this site.' | translate }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.features.trust && m.trust">
          <h4 class="mb-1">{{ 'Money the firm holds for you' | translate }}</h4>
          <div style="font-size:1.3rem;"><strong>{{ m.trust.balance | number: '1.0-2' }}</strong></div>
          <table class="table-sm mt-2" *ngIf="m.trust.entries.length">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Reference' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th class="text-end">{{ 'Balance' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let e of m.trust.entries">
              <td>{{ e.date | date: 'd MMM y' }}</td><td>{{ e.kind }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="e.purpose">{{ e.purpose }}</div></td><td>{{ e.reference }}</td>
              <td class="text-end">{{ e.amount | number: '1.0-2' }}</td><td class="text-end">{{ e.balanceAfter | number: '1.0-2' }}</td></tr></tbody></table>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="m.features.messages">
          <h4 class="mb-2">{{ 'Messages' | translate }}</h4>
          <div *ngFor="let x of m.messages" class="mb-2" [style.text-align]="x.fromClient ? 'right' : 'left'">
            <div style="display:inline-block; max-width:85%; text-align:left; padding:0.5rem 0.75rem; border-radius:0.5rem; background:rgba(127,127,127,0.14);">
              <div style="white-space:pre-wrap;">{{ x.body }}</div>
              <div class="text-muted" style="font-size:0.75rem;">{{ x.fromClient ? 'You' : x.senderName }} · {{ x.createdAt | date: 'd MMM y, HH:mm' }}</div>
            </div>
          </div>
          <div class="text-muted mb-2" *ngIf="!m.messages.length">{{ 'No messages yet.' | translate }}</div>
          <form (ngSubmit)="send()">
            <textarea class="form-control mb-2" rows="3" name="msg" [(ngModel)]="msg" maxlength="2000" placeholder="Write to your lawyers…"></textarea>
            <button class="btn btn-primary" type="submit" [disabled]="busy || !msg.trim()">{{ 'Send the message' | translate }}</button>
            <span class="text-muted ml-2" style="font-size:0.8rem;"> {{ 'Not for emergencies: the firm answers during office hours.' | translate }}</span>
          </form>
        </div>
      </ng-container>
    </app-portal-layout>
  `,
})
export class PortalMatterComponent implements OnInit {
  m: PortalMatter | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  file: File | null = null;
  uploadName = '';
  msg = '';
  private id = 0;

  constructor(private portal: PortalApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    if (!this.portal.signedIn()) { this.router.navigate(['/portal/login']); return; }
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.load(true);
  }

  private fail(e: any): void {
    this.busy = false;
    if (e?.status === 401 || e?.status === 403) { this.portal.signOut(); this.router.navigate(['/portal/login']); return; }
    this.error = e?.error?.message ?? T('That did not work');
  }

  load(markRead = false): void {
    this.portal.matter(this.id).subscribe({
      next: (m) => {
        this.m = m;
        if (markRead && m.features.messages && m.messages.some((x) => !x.fromClient)) this.portal.markRead(this.id).subscribe({ error: () => undefined });
      },
      error: (e) => this.fail(e),
    });
  }

  status(s: string): string { return (STATUS_LABEL as Record<string, string>)[s] ?? s; }

  size(b: number): string {
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(0)} KB`;
    return `${(b / 1024 / 1024).toFixed(1)} MB`;
  }

  pick(ev: Event): File | null { return (ev.target as HTMLInputElement).files?.[0] ?? null; }

  download(id: number, fileName: string): void {
    this.error = null;
    this.portal.download(id).subscribe({ next: (b) => downloadBlob(b, fileName), error: () => (this.error = T('Could not download the file')) });
  }

  upload(input: HTMLInputElement): void {
    if (!this.file) return;
    const fd = new FormData();
    fd.append('file', this.file);
    if (this.uploadName.trim()) fd.append('name', this.uploadName.trim());
    this.busy = true;
    this.error = null;
    this.info = null;
    this.portal.upload(this.id, fd).subscribe({
      next: () => { this.busy = false; this.info = T('Sent. A lawyer will look at it.'); this.file = null; this.uploadName = ''; input.value = ''; this.load(); },
      error: (e) => { input.value = ''; this.file = null; this.fail(e); },
    });
  }

  send(): void {
    const body = this.msg.trim();
    if (!body) return;
    this.busy = true;
    this.error = null;
    this.info = null;
    this.portal.post(this.id, body).subscribe({
      next: (list) => { this.busy = false; this.msg = ''; if (this.m) this.m = { ...this.m, messages: list }; },
      error: (e) => this.fail(e),
    });
  }
}
