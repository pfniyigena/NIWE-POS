import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ModuleApiService } from '../../core/api/module-api.service';
import { StaffOption, TimeApiService, Timer } from '../../core/api/time-api.service';
import { downloadBlob, openPdfForPrint } from '../../core/download-file';
import {
  Estimate, WoAttachment, WoDocKind, WoHistory, WoNote, WorkOrderApiService, WorkOrderDetail,
} from '../../core/api/work-order-api.service';

/**
 * v1.15.0 - the extra panels of one job: documents to print, the customer estimate and its approval, the timer, notes, photos and files,
 * the history, the completion e-mail and re-scheduling. Sits on the work order screen; the screen passes the job in and listens for changes.
 */
@Component({
  selector: 'app-work-order-extras',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, DatePickerComponent],
  template: `
    <ng-container *ngIf="d">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <!-- documents and timer -->
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap; align-items:center;">
        <button class="btn btn-outline-secondary" type="button" [disabled]="busy" (click)="print('jobcard')">{{ 'Print job card' | translate }}</button>
        <button class="btn btn-outline-secondary" type="button" *ngIf="d.type === 'CUSTOMER' && d.canManage" [disabled]="busy" (click)="print('estimate')">{{ 'Print estimate' | translate }}</button>
        <button class="btn btn-outline-secondary" type="button" *ngIf="d.type === 'CUSTOMER' && d.canManage && d.status === 'COMPLETED'" [disabled]="busy" (click)="print('completion')">{{ 'Print completion note' | translate }}</button>
        <button class="btn btn-outline-primary" type="button" *ngIf="d.type === 'CUSTOMER' && d.canManage && d.status === 'COMPLETED'" [disabled]="busy" (click)="emailCompletion()">{{ 'E-mail completion note' | translate }}</button>
        <span class="text-muted" style="font-size:0.8rem;" *ngIf="d.completionEmailedAt">{{ 'Last e-mailed {p0}' | translate: { p0: (d.completionEmailedAt | date: 'medium') } }}</span>
        <ng-container *ngIf="timerAvailable()">
          <span style="flex:1;"></span>
          <ng-container *ngIf="timer && timer.running && timer.projectId === d.timeProjectId">
            <span class="badge badge-primary" id="wo-timer">⏱ {{ clock() }}</span>
            <button class="btn btn-danger" type="button" [disabled]="busy" (click)="stopTimer()">{{ 'Stop timer' | translate }}</button>
          </ng-container>
          <ng-container *ngIf="timer && timer.running && timer.projectId !== d.timeProjectId">
            <span class="text-muted" style="font-size:0.85rem;">{{ 'Your timer is running on {p0}.' | translate: { p0: (timer.projectName || 'another project') } }}</span>
          </ng-container>
          <button class="btn btn-outline-primary" type="button" *ngIf="timer && !timer.running" [disabled]="busy" (click)="startTimer()">{{ 'Start timer' | translate }}</button>
        </ng-container>
      </div>

      <!-- estimate and approval -->
      <div class="card mb-3" style="padding:1rem; max-width:56rem;" *ngIf="d.type === 'CUSTOMER' && d.canManage && est">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <strong>{{ 'Estimate and customer approval' | translate }}</strong>
          <span class="badge" [class.badge-success]="est.status === 'APPROVED'" [class.badge-warning]="est.status === 'REQUESTED'"
                [class.badge-danger]="est.status === 'REJECTED'" [class.badge-secondary]="est.status === 'NONE'" id="wo-approval-badge">{{ statusLabel(est.status) }}</span>
        </div>
        <table class="table-sm" *ngIf="est.lines.length">
          <tbody>
            <tr *ngFor="let l of est.lines" [style.opacity]="l.kind === 'PART_INCLUDED' ? 0.7 : 1">
              <td>{{ l.description }}<span *ngIf="l.quantity && l.quantity !== 1 && l.kind !== 'PRICE'"> × {{ l.quantity }}</span>
                <span class="text-muted" *ngIf="l.kind === 'PART_INCLUDED'"> {{ '(included in the price)' | translate }}</span></td>
              <td class="text-end">{{ l.kind === 'PART_INCLUDED' ? '' : (l.amount | number: '1.0-2') }}</td>
            </tr>
            <tr><td><strong>{{ 'Estimated total (before tax)' | translate }}</strong></td><td class="text-end"><strong>{{ est.total | number: '1.0-2' }}</strong></td></tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!est.lines.length">{{ 'Nothing to estimate yet. Give the job a fixed price or an hourly rate with estimated hours, or add parts.' | translate }}</p>
        <div class="alert alert-warning" *ngIf="est.changedSinceRequest">{{ 'The price has changed since the customer was sent the estimate ({p0}).' | translate: { p0: (est.totalShownToCustomer | number: '1.0-2') } }}</div>
        <p class="text-muted" style="font-size:0.85rem;" *ngIf="est.status === 'APPROVED'">
          {{ 'Approved by' | translate }} <strong>{{ est.decidedByName }}</strong> {{ 'on {p0}' | translate: { p0: (est.decidedAt | date: 'medium') } }}<span *ngIf="est.note"> — "{{ est.note }}"</span>
          <span *ngIf="est.recordedBy"> {{ '(recorded by {p0})' | translate: { p0: (est.recordedBy) } }}</span>.</p>
        <p class="text-muted" style="font-size:0.85rem;" *ngIf="est.status === 'REJECTED'">
          {{ 'Declined' | translate }}<span *ngIf="est.decidedByName"> {{ 'by {p0}' | translate: { p0: (est.decidedByName) } }}</span> {{ 'on {p0}' | translate: { p0: (est.decidedAt | date: 'medium') } }}<span *ngIf="est.note"> — "{{ est.note }}"</span>{{ '. Change the price or parts and send a new estimate.' | translate }}</p>
        <p class="text-muted" style="font-size:0.85rem;" *ngIf="est.status === 'REQUESTED'">{{ 'Sent {p0} for {p1}. Work cannot start until the customer approves.' | translate: { p0: (est.requestedAt | date: 'medium'), p1: (est.totalShownToCustomer | number: '1.0-2') } }}</p>

        <div class="flex gap-3 align-center mb-2" style="flex-wrap:wrap;" *ngIf="d.status === 'OPEN'">
          <label style="display:flex; gap:0.4rem; align-items:center;">
            <input type="checkbox" name="ear" [(ngModel)]="reqApproval" /> {{ 'The customer must approve the estimate before work starts' | translate }}</label>
          <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Estimated hours' | translate }}</label>
            <input class="form-control" type="number" min="0" step="0.25" name="eh" style="width:7rem;" [(ngModel)]="estHours" /></div>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busy" (click)="saveEstimateSettings()">{{ 'Save' | translate }}</button>
        </div>

        <ng-container *ngIf="d.status === 'OPEN' && est.total > 0">
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <input class="form-control" style="flex:1; min-width:14rem;" name="en" [(ngModel)]="sendNote" [placeholder]="'Message to the customer (optional)' | translate" />
            <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ee" [(ngModel)]="sendEmail" /> {{ 'E-mail it with the PDF' | translate }}</label>
            <button class="btn btn-primary btn-sm" type="button" [disabled]="busy" (click)="sendEstimate()">{{ est.status === 'NONE' ? ('Send estimate' | translate) : ('Send again' | translate) }}</button>
          </div>
        </ng-container>
        <div class="alert alert-info mt-2" *ngIf="sentMessage">{{ sentMessage }}</div>

        <div class="mt-3" *ngIf="d.status === 'OPEN' && (est.status === 'REQUESTED' || est.status === 'NONE' || est.status === 'REJECTED') && est.total > 0">
          <strong style="font-size:0.9rem;">{{ 'Record the customer\\'s answer' | translate }}</strong>
          <div class="flex gap-2 align-center mt-1" style="flex-wrap:wrap;">
            <input class="form-control" style="width:14rem;" name="an" [(ngModel)]="answerName" [placeholder]="'Who answered (name)' | translate" />
            <input class="form-control" style="flex:1; min-width:12rem;" name="ano" [(ngModel)]="answerNote" [placeholder]="'How (phone, WhatsApp, signed paper…)' | translate" />
            <button class="btn btn-success btn-sm" type="button" [disabled]="busy || !answerName.trim()" (click)="approve()">{{ 'Customer approved' | translate }}</button>
            <button class="btn btn-outline-danger btn-sm" type="button" *ngIf="est.status === 'REQUESTED'" [disabled]="busy" (click)="reject()">{{ 'Customer declined' | translate }}</button>
          </div>
        </div>
      </div>

      <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
        <!-- notes -->
        <div class="card mb-3" style="padding:1rem; flex:1; min-width:20rem;" *ngIf="d.canWork || d.canManage">
          <strong class="mb-2">{{ 'Notes' | translate }}</strong>
          <div *ngFor="let n of notes" style="padding:0.4rem 0; border-bottom:1px solid var(--border);">
            <div style="white-space:pre-wrap;">{{ n.text }}</div>
            <div class="text-muted" style="font-size:0.75rem;">{{ n.authorName }} · {{ n.createdAt | date: 'medium' }}
              <a href="javascript:void(0)" *ngIf="n.canDelete" (click)="deleteNote(n)" style="margin-left:0.5rem; color:#c0392b;">{{ 'Delete' | translate }}</a></div>
          </div>
          <p class="text-muted" *ngIf="!notes.length">{{ 'No notes yet.' | translate }}</p>
          <div class="flex gap-2 mt-2">
            <textarea class="form-control" rows="2" name="nt" [(ngModel)]="noteText" placeholder="Write a note about the job"></textarea>
            <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="busy || !noteText.trim()" (click)="addNote()">{{ 'Add' | translate }}</button>
          </div>
        </div>

        <!-- photos and files -->
        <div class="card mb-3" style="padding:1rem; flex:1; min-width:20rem;" *ngIf="d.canWork || d.canManage">
          <strong class="mb-2">{{ 'Photos and files' | translate }}</strong>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div *ngFor="let a of files" style="width:7.5rem;">
              <a href="javascript:void(0)" (click)="openFile(a)" [title]="a.fileName">
                <img *ngIf="a.image && thumbs[a.id]" [src]="thumbs[a.id]" [alt]="a.fileName" style="width:7.5rem; height:5.5rem; object-fit:cover; border:1px solid var(--border); border-radius:4px;" />
                <div *ngIf="!a.image || !thumbs[a.id]" style="width:7.5rem; height:5.5rem; border:1px solid var(--border); border-radius:4px; display:flex; align-items:center; justify-content:center; font-size:0.75rem; padding:0.3rem; text-align:center; word-break:break-all;">{{ a.fileName }}</div>
              </a>
              <div class="text-muted" style="font-size:0.7rem; line-height:1.2;">{{ a.caption || a.fileName }}</div>
              <a href="javascript:void(0)" *ngIf="a.canDelete" (click)="deleteFile(a)" style="font-size:0.7rem; color:#c0392b;">{{ 'Remove' | translate }}</a>
            </div>
          </div>
          <p class="text-muted" *ngIf="!files.length">{{ 'No photos or files yet.' | translate }}</p>
          <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;" *ngIf="d.status !== 'CANCELLED'">
            <input type="file" id="wo-file" accept="image/*,application/pdf,text/plain" (change)="picked($event)" style="max-width:15rem;" />
            <input class="form-control" style="flex:1; min-width:8rem;" name="fcap" [(ngModel)]="caption" [placeholder]="'Caption (optional)' | translate" />
            <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="busy || !chosen" (click)="upload()">{{ 'Upload' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="d.status !== 'CANCELLED'">{{ 'Photos (JPG, PNG, GIF, WebP), PDF or text, up to 10 MB each. They are kept in the database and included in backups.' | translate }}</div>
        </div>
      </div>

      <!-- schedule -->
      <div class="card mb-3" style="padding:1rem; max-width:56rem;" *ngIf="d.canManage && (d.status === 'OPEN' || d.status === 'IN_PROGRESS' || d.status === 'ON_HOLD') && !d.invoiceId">
        <div class="flex justify-between align-center">
          <strong>{{ 'Schedule' | translate }}</strong>
          <a href="javascript:void(0)" (click)="showMove = !showMove">{{ showMove ? ('Close' | translate) : ('Move or reassign' | translate) }}</a>
        </div>
        <div class="flex gap-3 align-center mt-2" style="flex-wrap:wrap;" *ngIf="showMove">
          <div class="form-group"><label class="form-label">{{ 'Scheduled' | translate }}</label><app-date-picker name="msc" [(ngModel)]="move.scheduledDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Due' | translate }}</label><app-date-picker name="mdu" [(ngModel)]="move.dueDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Assigned to' | translate }}</label>
            <select class="form-select" name="mas" [(ngModel)]="move.assignedToUserId">
              <option [ngValue]="null">{{ 'Nobody' | translate }}</option>
              <option *ngFor="let s of staff" [ngValue]="s.id">{{ s.fullName }}</option>
            </select></div>
          <button class="btn btn-primary btn-sm" id="wo-move-save" type="button" [disabled]="busy" (click)="saveMove()">{{ 'Save' | translate }}</button>
        </div>
      </div>

      <!-- history -->
      <div class="card mb-3" style="padding:1rem; max-width:56rem;" *ngIf="d.canManage || d.canWork">
        <div class="flex justify-between align-center">
          <strong>{{ 'History' | translate }}</strong>
          <a href="javascript:void(0)" id="wo-history-toggle" (click)="toggleHistory()">{{ showHistory ? ('Hide' | translate) : ('Show' | translate) }}</a>
        </div>
        <ng-container *ngIf="showHistory">
          <table class="table-sm mt-2" *ngIf="history.length">
            <tbody>
              <tr *ngFor="let h of history">
                <td style="white-space:nowrap; width:11rem;">{{ h.at | date: 'short' }}</td>
                <td style="width:9rem;">{{ h.who || '—' }}</td>
                <td>{{ h.description || h.action }}</td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" *ngIf="!history.length">{{ 'Nothing recorded yet.' | translate }}</p>
        </ng-container>
      </div>
    </ng-container>
  `,
})
export class WorkOrderExtrasComponent implements OnChanges, OnDestroy {
  @Input() d: WorkOrderDetail | null = null;
  /** Emitted when the job itself changed (moved, re-assigned) so the screen can show the new version. */
  @Output() changed = new EventEmitter<WorkOrderDetail>();

  busy = false;
  error: string | null = null;
  notice: string | null = null;

  est: Estimate | null = null;
  reqApproval = false;
  estHours: number | null = null;
  sendNote = '';
  sendEmail = true;
  sentMessage: string | null = null;
  answerName = '';
  answerNote = '';

  notes: WoNote[] = [];
  noteText = '';

  files: WoAttachment[] = [];
  thumbs: Record<number, string> = {};
  chosen: File | null = null;
  caption = '';

  history: WoHistory[] = [];
  showHistory = false;

  timer: Timer | null = null;
  private tick: any = null;
  private elapsedBase = 0;
  private elapsedAt = 0;

  staff: StaffOption[] = [];
  showMove = false;
  move: { scheduledDate: string | null; dueDate: string | null; assignedToUserId: number | null } = { scheduledDate: null, dueDate: null, assignedToUserId: null };

  private loadedFor: number | null = null;

  constructor(private api: WorkOrderApiService, private timeApi: TimeApiService, public modules: ModuleApiService) {}

  ngOnChanges(ch: SimpleChanges): void {
    if (!ch['d'] || !this.d) return;
    const d = this.d;
    if (this.loadedFor !== d.id) {
      this.loadedFor = d.id;
      this.notes = []; this.files = []; this.history = []; this.showHistory = false; this.est = null; this.sentMessage = null;
      this.revokeThumbs();
      if (d.canWork || d.canManage) { this.loadNotes(); this.loadFiles(); }
      if (d.canManage) this.timeApi.staff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    }
    this.move = { scheduledDate: d.scheduledDate, dueDate: d.dueDate, assignedToUserId: d.assignedToUserId };
    if (d.type === 'CUSTOMER' && d.canManage) this.loadEstimate();
    if (this.showHistory) this.loadHistory();
    this.loadTimer();
  }

  ngOnDestroy(): void {
    if (this.tick) clearInterval(this.tick);
    this.revokeThumbs();
  }

  private fail(e: any, fallback: string): void {
    this.busy = false;
    this.notice = null;
    this.error = e?.error?.message ?? fallback;
  }

  private ok(msg: string): void {
    this.busy = false;
    this.error = null;
    this.notice = msg;
  }

  // ---- documents
  print(kind: WoDocKind): void {
    if (!this.d) return;
    this.busy = true;
    this.api.pdf(this.d.id, kind).subscribe({
      next: (b) => { this.busy = false; this.error = null; openPdfForPrint(b); },
      error: (e) => this.failBlob(e, 'Could not make the document'),
    });
  }

  /** An error response of a blob request arrives as a Blob; read the message out of it. */
  private failBlob(e: any, fallback: string): void {
    const body = e?.error;
    if (body instanceof Blob) {
      body.text().then((t) => {
        let m = fallback;
        try { m = JSON.parse(t).message || fallback; } catch { /* keep the fallback */ }
        this.fail({ error: { message: m } }, fallback);
      }).catch(() => this.fail(e, fallback));
    } else this.fail(e, fallback);
  }

  emailCompletion(): void {
    if (!this.d) return;
    this.busy = true;
    this.api.emailCompletion(this.d.id).subscribe({
      next: (r) => {
        this.ok(r.message);
        this.api.get(this.d!.id).subscribe({ next: (d) => this.changed.emit(d), error: () => {} });
      },
      error: (e) => this.fail(e, 'Could not e-mail the completion note'),
    });
  }

  // ---- estimate
  statusLabel(s: string): string {
    return s === 'NONE' ? (this.est?.approvalRequired ? 'Not sent yet' : 'No approval needed') : s === 'REQUESTED' ? 'Waiting for the customer'
      : s === 'APPROVED' ? 'Approved' : 'Declined';
  }

  private loadEstimate(): void {
    if (!this.d) return;
    this.api.estimate(this.d.id).subscribe({
      next: (e) => { this.est = e; this.reqApproval = e.approvalRequired; this.estHours = e.estimatedHours; },
      error: () => {},
    });
  }

  private afterEstimate(e: Estimate, msg: string): void {
    this.est = e;
    this.reqApproval = e.approvalRequired;
    this.estHours = e.estimatedHours;
    this.ok(msg);
    this.api.get(this.d!.id).subscribe({ next: (d) => this.changed.emit(d), error: () => {} });
  }

  saveEstimateSettings(): void {
    if (!this.d) return;
    this.busy = true;
    this.sentMessage = null;
    this.api.saveEstimateSettings(this.d.id, this.reqApproval, this.estHours == null || (this.estHours as any) === '' ? null : Number(this.estHours)).subscribe({
      next: (e) => this.afterEstimate(e, 'Saved.'),
      error: (e) => this.fail(e, 'Could not save'),
    });
  }

  sendEstimate(): void {
    if (!this.d) return;
    this.busy = true;
    this.sentMessage = null;
    this.api.sendEstimate(this.d.id, this.sendEmail, this.sendNote.trim() || null).subscribe({
      next: (e) => {
        this.afterEstimate(e, 'Estimate recorded as sent.');
        this.sentMessage = e.emailNote;
      },
      error: (e) => this.fail(e, 'Could not send the estimate'),
    });
  }

  approve(): void {
    if (!this.d) return;
    this.busy = true;
    this.sentMessage = null;
    this.api.approveEstimate(this.d.id, this.answerName.trim(), this.answerNote.trim() || null).subscribe({
      next: (e) => { this.answerName = ''; this.answerNote = ''; this.afterEstimate(e, 'Approval recorded. The job can be started.'); },
      error: (e) => this.fail(e, 'Could not record the approval'),
    });
  }

  reject(): void {
    if (!this.d) return;
    this.busy = true;
    this.sentMessage = null;
    this.api.rejectEstimate(this.d.id, this.answerName.trim() || null, this.answerNote.trim() || null).subscribe({
      next: (e) => { this.answerName = ''; this.answerNote = ''; this.afterEstimate(e, 'Recorded as declined.'); },
      error: (e) => this.fail(e, 'Could not record the answer'),
    });
  }

  // ---- notes
  private loadNotes(): void {
    if (!this.d) return;
    this.api.notes(this.d.id).subscribe({ next: (n) => (this.notes = n), error: () => {} });
  }

  addNote(): void {
    if (!this.d) return;
    this.busy = true;
    this.api.addNote(this.d.id, this.noteText.trim()).subscribe({
      next: () => { this.noteText = ''; this.ok('Note added.'); this.loadNotes(); if (this.showHistory) this.loadHistory(); },
      error: (e) => this.fail(e, 'Could not add the note'),
    });
  }

  deleteNote(n: WoNote): void {
    if (!this.d || !confirm('Delete this note?')) return;
    this.api.deleteNote(this.d.id, n.id).subscribe({ next: () => { this.loadNotes(); if (this.showHistory) this.loadHistory(); }, error: (e) => this.fail(e, 'Could not delete the note') });
  }

  // ---- photos and files
  private revokeThumbs(): void {
    Object.values(this.thumbs).forEach((u) => URL.revokeObjectURL(u));
    this.thumbs = {};
  }

  private loadFiles(): void {
    if (!this.d) return;
    const id = this.d.id;
    this.api.attachments(id).subscribe({
      next: (l) => {
        this.files = l;
        l.filter((a) => a.image && !this.thumbs[a.id]).forEach((a) =>
          this.api.attachmentBlob(id, a.id).subscribe({ next: (b) => (this.thumbs = { ...this.thumbs, [a.id]: URL.createObjectURL(b) }), error: () => {} }));
      },
      error: () => {},
    });
  }

  picked(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.chosen = input.files && input.files.length ? input.files[0] : null;
  }

  upload(): void {
    if (!this.d || !this.chosen) return;
    this.busy = true;
    this.api.addAttachment(this.d.id, this.chosen, this.caption.trim() || null).subscribe({
      next: () => {
        this.chosen = null;
        this.caption = '';
        const el = document.getElementById('wo-file') as HTMLInputElement | null;
        if (el) el.value = '';
        this.ok('File added.');
        this.loadFiles();
        if (this.showHistory) this.loadHistory();
      },
      error: (e) => this.fail(e, 'Could not upload the file'),
    });
  }

  openFile(a: WoAttachment): void {
    if (!this.d) return;
    this.api.attachmentBlob(this.d.id, a.id).subscribe({
      next: (b) => (a.contentType === 'text/plain' ? downloadBlob(b, a.fileName) : openPdfForPrint(b)),
      error: (e) => this.fail(e, 'Could not open the file'),
    });
  }

  deleteFile(a: WoAttachment): void {
    if (!this.d || !confirm('Remove this file?')) return;
    this.api.deleteAttachment(this.d.id, a.id).subscribe({
      next: () => {
        const u = this.thumbs[a.id];
        if (u) URL.revokeObjectURL(u);
        const t = { ...this.thumbs };
        delete t[a.id];
        this.thumbs = t;
        this.loadFiles();
        if (this.showHistory) this.loadHistory();
      },
      error: (e) => this.fail(e, 'Could not remove the file'),
    });
  }

  // ---- history
  toggleHistory(): void {
    this.showHistory = !this.showHistory;
    if (this.showHistory) this.loadHistory();
  }

  private loadHistory(): void {
    if (!this.d) return;
    this.api.history(this.d.id).subscribe({ next: (h) => (this.history = h), error: () => {} });
  }

  // ---- timer
  timerAvailable(): boolean {
    return !!this.d && this.d.canWork && !!this.d.timeProjectId && this.modules.on('TIME_TRACKING') && (this.d.status === 'IN_PROGRESS' || this.d.status === 'OPEN');
  }

  private loadTimer(): void {
    if (!this.d || !this.d.canWork || !this.d.timeProjectId || !this.modules.on('TIME_TRACKING')) { this.timer = null; return; }
    this.timeApi.timer().subscribe({ next: (t) => this.setTimer(t), error: () => (this.timer = null) });
  }

  private setTimer(t: Timer): void {
    this.timer = t;
    this.elapsedBase = t.elapsedSeconds || 0;
    this.elapsedAt = Date.now();
    if (this.tick) { clearInterval(this.tick); this.tick = null; }
    if (t.running) this.tick = setInterval(() => { /* the template re-reads clock() */ }, 1000);
  }

  clock(): string {
    if (!this.timer || !this.timer.running) return '00:00:00';
    const s = Math.max(0, Math.floor(this.elapsedBase + (Date.now() - this.elapsedAt) / 1000));
    const p = (n: number) => String(n).padStart(2, '0');
    return p(Math.floor(s / 3600)) + ':' + p(Math.floor((s % 3600) / 60)) + ':' + p(s % 60);
  }

  startTimer(): void {
    if (!this.d || !this.d.timeProjectId) return;
    this.busy = true;
    this.timeApi.startTimer({ projectId: this.d.timeProjectId, description: this.d.number + ' ' + this.d.title }).subscribe({
      next: (t) => { this.busy = false; this.error = null; this.notice = null; this.setTimer(t); },
      error: (e) => this.fail(e, 'Could not start the timer'),
    });
  }

  stopTimer(): void {
    this.busy = true;
    this.timeApi.stopTimer().subscribe({
      next: () => {
        this.ok('Timer stopped and the time was saved.');
        this.loadTimer();
        this.api.get(this.d!.id).subscribe({ next: (d) => this.changed.emit(d), error: () => {} });
      },
      error: (e) => this.fail(e, 'Could not stop the timer'),
    });
  }

  // ---- schedule
  saveMove(): void {
    if (!this.d) return;
    this.busy = true;
    const assigneeChanged = this.move.assignedToUserId !== this.d.assignedToUserId;
    this.api.reschedule(this.d.id, this.move.scheduledDate, this.move.dueDate, this.move.assignedToUserId, assigneeChanged).subscribe({
      next: (d) => { this.ok('Schedule saved.'); this.showMove = false; this.changed.emit(d); if (this.showHistory) this.loadHistory(); },
      error: (e) => this.fail(e, 'Could not change the schedule'),
    });
  }
}
