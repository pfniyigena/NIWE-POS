import { Component, Input, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LawApiService, PaperChecks, PaperKind, PaperRequest } from '../../core/api/law-api.service';
import { showPdf } from '../../core/open-pdf';

import { T } from '../../core/i18n/t';
/** A matter's Papers tab: the file cover sheet, the engagement and fee letter and the closing letter. Print them, keep a copy in the documents, or e-mail the letters to the client. */
@Component({
  selector: 'app-law-matter-papers',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success pp-info" *ngIf="info">{{ info }}</div>
    <ng-container *ngIf="c">
      <!-- cover sheet -->
      <div class="card mb-3 pp-cover" style="padding:1rem;">
        <strong>{{ 'File cover sheet' | translate }}</strong>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'One page for the front of the paper file: the client, the matter, the team, the other people on it, the open deadlines and room to write where the file is kept.' | translate }}</div>
        <div style="display:flex; gap:0.5rem; flex-wrap:wrap;">
          <button class="btn btn-outline-secondary pp-print" type="button" (click)="print('cover')" [disabled]="busy">{{ 'Print' | translate }}</button>
          <button class="btn btn-outline-secondary pp-save" type="button" *ngIf="c.canWork" (click)="save('cover')" [disabled]="busy">{{ 'Keep in documents' | translate }}</button>
        </div>
      </div>

      <!-- engagement letter -->
      <div class="card mb-3 pp-engagement" style="padding:1rem;">
        <strong>{{ 'Engagement and fee letter' | translate }}</strong>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Tells the client what you will do, who will do it and how you charge. The fees come from the fee terms on the Billing tab.' | translate }}</div>
        <div class="alert alert-warning" *ngIf="!c.feeTermsSet">{{ 'No fee terms are set on this matter yet. Fill them in on the Billing tab first, or the letter will not say what you charge.' | translate }}</div>
        <ng-container *ngIf="c.canWork">
          <div class="form-group mb-2"><label class="form-label">{{ 'What you will do' | translate }} <span class="text-muted">{{ '(leave empty to use the matter description)' | translate }}</span></label>
            <textarea class="form-control pp-scope" rows="3" name="scope" [(ngModel)]="scope"></textarea></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Other terms' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
            <textarea class="form-control pp-terms" rows="2" name="terms" [(ngModel)]="terms"></textarea></div>
          <div class="form-group mb-2" style="max-width:14rem;"><label class="form-label">{{ 'Date on the letter' | translate }}</label><input class="form-control pp-edate" type="date" name="edate" [(ngModel)]="engDate" /></div>
          <div style="display:flex; gap:0.5rem; flex-wrap:wrap; align-items:center;">
            <button class="btn btn-outline-secondary pp-print" type="button" (click)="print('engagement')" [disabled]="busy">{{ 'Print' | translate }}</button>
            <button class="btn btn-outline-secondary pp-save" type="button" (click)="save('engagement')" [disabled]="busy">{{ 'Keep in documents' | translate }}</button>
            <button class="btn btn-outline-primary pp-mail" type="button" (click)="mail('engagement')" [disabled]="busy || !canMail">{{ 'E-mail to the client' | translate }}</button>
            <span class="text-muted" style="font-size:0.8rem;" *ngIf="!canMail">{{ mailWhy }}</span>
          </div>
        </ng-container>
        <div class="text-muted" *ngIf="!c.canWork">{{ 'Only the matter\\'s team can make this letter.' | translate }}</div>
      </div>

      <!-- closing letter -->
      <div class="card mb-3 pp-closing" style="padding:1rem;">
        <strong>{{ 'Closing letter' | translate }}</strong>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Tells the client the matter is finished, with the final invoices, anything unpaid and whether you hold their money. Close the matter first.' | translate }}</div>
        <div class="alert alert-warning" *ngIf="!c.closed">{{ 'This matter is still open. Close it first, then make the closing letter.' | translate }}</div>
        <ng-container *ngIf="c.closed && warnings.length">
          <div class="alert alert-warning">
            <strong>{{ 'Look at these first:' | translate }}</strong>
            <ul class="mb-0"><li *ngFor="let w of warnings">{{ w }}</li></ul>
          </div>
        </ng-container>
        <ng-container *ngIf="c.canWork && c.closed">
          <div class="form-group mb-2"><label class="form-label">{{ 'A few words to the client' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
            <textarea class="form-control pp-notes" rows="2" name="notes" [(ngModel)]="notes"></textarea></div>
          <div class="form-group mb-2" style="max-width:14rem;"><label class="form-label">{{ 'Date on the letter' | translate }}</label><input class="form-control pp-cdate" type="date" name="cdate" [(ngModel)]="closeDate" /></div>
          <div style="display:flex; gap:0.5rem; flex-wrap:wrap; align-items:center;">
            <button class="btn btn-outline-secondary pp-print" type="button" (click)="print('closing')" [disabled]="busy">{{ 'Print' | translate }}</button>
            <button class="btn btn-outline-secondary pp-save" type="button" (click)="save('closing')" [disabled]="busy">{{ 'Keep in documents' | translate }}</button>
            <button class="btn btn-outline-primary pp-mail" type="button" (click)="mail('closing')" [disabled]="busy || !canMail">{{ 'E-mail to the client' | translate }}</button>
            <span class="text-muted" style="font-size:0.8rem;" *ngIf="!canMail">{{ mailWhy }}</span>
          </div>
        </ng-container>
      </div>
    </ng-container>
  `,
})
export class MatterPapersComponent implements OnInit {
  @Input() matterId!: number;
  c: PaperChecks | null = null;
  scope = '';
  terms = '';
  notes = '';
  engDate = '';
  closeDate = '';
  busy = false;
  error: string | null = null;
  info: string | null = null;
  warnings: string[] = [];

  constructor(private api: LawApiService) {}

  ngOnInit(): void {
    this.api.paperChecks(this.matterId).subscribe({
      next: (c) => {
        this.c = c;
        const w: string[] = [];
        if (c.openDeadlines) w.push(c.openDeadlines + ' deadline' + (c.openDeadlines === 1 ? ' is' : 's are') + ' still open.');
        if (c.openTasks) w.push(c.openTasks + ' task' + (c.openTasks === 1 ? ' is' : 's are') + ' still open.');
        if (c.unbilledTime > 0) w.push('Time worth ' + c.unbilledTime.toLocaleString('en', { minimumFractionDigits: 2 }) + ' has not been billed.');
        if (c.unbilledCosts > 0) w.push('Costs of ' + c.unbilledCosts.toLocaleString('en', { minimumFractionDigits: 2 }) + ' have not been billed.');
        if (c.outstanding > 0) w.push('The client still owes ' + c.outstanding.toLocaleString('en', { minimumFractionDigits: 2 }) + '. The letter will say so.');
        if (c.trustEnabled && c.trustHeld > 0) w.push('Client money of ' + c.trustHeld.toLocaleString('en', { minimumFractionDigits: 2 }) + ' is still held.');
        this.warnings = w;
      },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load this tab')),
    });
  }

  get canMail(): boolean { return !!this.c && !!this.c.clientEmail && this.c.mailReady; }
  get mailWhy(): string {
    if (!this.c) return '';
    if (!this.c.clientEmail) return 'The client has no e-mail address on the customer record.';
    return 'E-mail is not set up yet (Settings > Email).';
  }

  private body(kind: PaperKind): PaperRequest {
    if (kind === 'engagement') return { scope: this.scope, terms: this.terms, letterDate: this.engDate || null };
    if (kind === 'closing') return { notes: this.notes, letterDate: this.closeDate || null };
    return {};
  }

  print(kind: PaperKind): void {
    this.error = null; this.info = null; this.busy = true;
    showPdf(this.api.paperPdf(this.matterId, kind, this.body(kind)), kind + '.pdf', (e) => { this.busy = false; this.error = e; });
  }

  save(kind: PaperKind): void {
    this.error = null; this.info = null; this.busy = true;
    this.api.paperSave(this.matterId, kind, this.body(kind)).subscribe({
      next: (r) => { this.busy = false; this.info = r.message + ' (' + r.name + ')'; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not keep the paper'); },
    });
  }

  mail(kind: PaperKind): void {
    this.error = null; this.info = null; this.busy = true;
    this.api.paperEmail(this.matterId, kind, this.body(kind)).subscribe({
      next: (r) => { this.busy = false; this.info = r.message; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('The e-mail could not be sent'); },
    });
  }
}
