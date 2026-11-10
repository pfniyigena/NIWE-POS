import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DOC_CATEGORY_LABEL, DocCategory, DocItem, DocList, DocTemplate, DocVersions, LawApiService } from '../../core/api/law-api.service';
import { downloadBlob } from '../../core/download-file';

/**
 * The Documents tab of a matter: the files kept on it, with version history, a check-out lock while someone edits, a confidential mark and
 * a "shared with the client" mark. Letters and contracts can also be made from a template that fills in the matter's details. The server
 * decides who may see and change what; the buttons follow the flags it sends.
 */
@Component({
  selector: 'app-law-matter-documents',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="info">{{ info }}</div>
    <div class="alert alert-warning" *ngIf="unresolved.length">{{ 'These placeholders were not recognised and are left as written in the document:' | translate }} <strong>{{ unresolved.join(', ') }}</strong>.</div>
    <div *ngIf="!l && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

    <ng-container *ngIf="l">
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap; align-items:center;">
        <button class="btn" [class.btn-primary]="mode === 'upload'" [class.btn-outline-primary]="mode !== 'upload'" *ngIf="l.canUpload" (click)="open('upload')">{{ 'Add a document' | translate }}</button>
        <button class="btn" [class.btn-primary]="mode === 'generate'" [class.btn-outline-primary]="mode !== 'generate'" *ngIf="l.canUpload" (click)="open('generate')">{{ 'Make one from a template…' | translate }}</button>
        <label style="display:flex; gap:0.4rem; align-items:center; margin-left:auto;"><input type="checkbox" [(ngModel)]="showArchived" (change)="load()" /> {{ 'Show archived' | translate }}</label>
        <span class="text-muted" style="font-size:0.85rem;">{{ '{p0} document{p1} · {p2}' | translate: { p0: (l.documents.length), p1: (l.documents.length === 1 ? '' : 's'), p2: (size(l.totalBytes)) } }}</span>
      </div>

      <!-- add -->
      <form class="card mb-3" style="padding:1rem;" *ngIf="mode === 'upload'" (ngSubmit)="upload(fileInput)">
        <h4 class="mb-2">{{ 'Add a document' | translate }}</h4>
        <div class="form-group mb-2"><label class="form-label">{{ 'File' | translate }} <span class="text-muted">{{ '(up to 25 MB)' | translate }}</span></label>
          <input #fileInput class="form-control" type="file" name="file" (change)="upFile = pick($event)" /></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Name' | translate }} <span class="text-muted">{{ '(the file name if left empty)' | translate }}</span></label><input class="form-control" name="un" [(ngModel)]="up.name" /></div>
          <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Kind' | translate }}</label>
            <select class="form-select" name="uc" [(ngModel)]="up.category"><option *ngFor="let c of categories" [value]="c.key">{{ c.label }}</option></select></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Description' | translate }}</label><input class="form-control" name="ud" [(ngModel)]="up.description" /></div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="uf" [(ngModel)]="up.confidential" /> {{ 'Confidential: only this matter\\'s team and admins can see it' | translate }}</label>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy || !upFile">{{ 'Add the document' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
      </form>

      <!-- generate -->
      <form class="card mb-3" style="padding:1rem;" *ngIf="mode === 'generate'" (ngSubmit)="generate()">
        <h4 class="mb-2">{{ 'Make a document from a template' | translate }}</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="!templates.length">There is no template yet. An admin or an Operations manager can add them in Law Firm &gt; Templates.</div>
        <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="templates.length">
          <div class="form-group" style="min-width:14rem;"><label class="form-label">{{ 'Template' | translate }}</label>
            <select class="form-select" name="gt" [(ngModel)]="gen.templateId"><option [ngValue]="null">{{ 'Choose…' | translate }}</option><option *ngFor="let t of templates" [ngValue]="t.id">{{ t.name }}{{ t.kind === 'DOCX' ? ' (Word)' : '' }}</option></select></div>
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Name' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label><input class="form-control" name="gn" [(ngModel)]="gen.name" /></div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2" *ngIf="templates.length"><input type="checkbox" name="gf" [(ngModel)]="gen.confidential" /> {{ 'Confidential' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The client, matter, team and today\\'s date are filled in. The result is a Word file you can download, edit and add back as a new version.' | translate }}</div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy || !gen.templateId">{{ 'Make the document' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="mode = null">{{ 'Cancel' | translate }}</button></div>
      </form>

      <!-- waiting for review -->
      <div class="card mb-3" style="padding:1rem; border-left:4px solid #b45309;" *ngIf="pending().length">
        <h4 class="mb-2">{{ 'Sent by the client, waiting for review' | translate }}</h4>
        <table class="table-sm"><tbody>
          <tr *ngFor="let d of pending()">
            <td><strong>{{ d.name }}</strong><div class="text-muted" style="font-size:0.8rem;">{{ d.fileName }} · {{ size(d.sizeBytes) }} · {{ d.createdByName }} · {{ d.createdAt | date: 'd MMM y, HH:mm' }}</div></td>
            <td style="white-space:nowrap; text-align:right;">
              <button class="btn btn-sm btn-outline-primary" (click)="download(d)">{{ 'Download' | translate }}</button>
              <ng-container *ngIf="l.canUpload">
                <button class="btn btn-sm btn-primary" [disabled]="busy" (click)="review(d)">{{ 'Accept' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="busy" (click)="startReject(d)">{{ 'Reject…' | translate }}</button>
              </ng-container>
            </td>
          </tr>
        </tbody></table>
        <form class="mt-2" *ngIf="rejecting" (ngSubmit)="confirmReject()">
          <label class="form-label">{{ 'Why is "{p0}" not accepted? The client will read this.' | translate: { p0: (rejecting.name) } }}</label>
          <input class="form-control mb-2" name="rj" [(ngModel)]="rejectNote" />
          <div class="flex gap-2"><button class="btn btn-danger" type="submit" [disabled]="busy || !rejectNote.trim()">{{ 'Reject the document' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="rejecting = null">{{ 'Cancel' | translate }}</button></div>
        </form>
      </div>

      <!-- details -->
      <form class="card mb-3" style="padding:1rem;" *ngIf="editing" (ngSubmit)="saveEdit()">
        <h4 class="mb-2">{{ 'Change details' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="en" [(ngModel)]="edit.name" /></div>
          <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Kind' | translate }}</label>
            <select class="form-select" name="ec" [(ngModel)]="edit.category"><option *ngFor="let c of categories" [value]="c.key">{{ c.label }}</option></select></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Description' | translate }}</label><input class="form-control" name="ed" [(ngModel)]="edit.description" /></div>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button><button class="btn btn-outline-secondary" type="button" (click)="editing = null">{{ 'Cancel' | translate }}</button></div>
      </form>

      <div class="card" style="padding:1rem;">
        <table class="stack table-sm" *ngIf="l.documents.length">
          <thead><tr><th>{{ 'Document' | translate }}</th><th>{{ 'Kind' | translate }}</th><th>{{ 'Version' | translate }}</th><th>{{ 'Size' | translate }}</th><th>{{ 'Updated' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let d of l.documents" [style.opacity]="d.archived ? 0.6 : 1">
              <td data-label="Document">
                <a href="#" (click)="$event.preventDefault(); download(d)"><strong>{{ d.name }}</strong></a>
                <span [title]="'Confidential: only the team and admins see it' | translate" *ngIf="d.confidential"> 🔒</span>
                <span class="badge" *ngIf="d.sharedWithClient" [title]="'The client can see this document' | translate"> {{ 'shared with client' | translate }}</span>
                <span class="badge" *ngIf="d.archived"> {{ 'archived' | translate }}</span>
                <span class="badge" *ngIf="d.fromClient"> {{ 'from client{p0}' | translate: { p0: (d.reviewStatus === 'PENDING' ? ' · waiting' : d.reviewStatus === 'REJECTED' ? ' · rejected' : '') } }}</span>
                <div class="text-muted" style="font-size:0.8rem;">{{ d.fileName }}<span *ngIf="d.description"> · {{ d.description }}</span> {{ '· added by {p0}' | translate: { p0: (d.createdByName) } }}</div>
                <div style="font-size:0.8rem; color:#b45309;" *ngIf="d.checkedOutByName">{{ 'Checked out by {p0} since {p1}' | translate: { p0: (d.checkedOutByMe ? 'you' : d.checkedOutByName), p1: (d.checkedOutAt | date: 'd MMM, HH:mm') } }}</div>
              </td>
              <td data-label="Kind">{{ catLabel(d.category) }}</td>
              <td data-label="Version">{{ 'v{p0}' | translate: { p0: (d.currentVersion) } }}</td>
              <td data-label="Size">{{ size(d.sizeBytes) }}</td>
              <td data-label="Updated">{{ d.updatedAt | date: 'd MMM y, HH:mm' }}</td>
              <td style="white-space:nowrap;">
                <button class="btn btn-sm btn-outline-primary" (click)="download(d)">{{ 'Download' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" (click)="versions(d)">{{ 'Versions' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="d.canLock && !d.checkedOutByName" (click)="act(api.checkoutDocument(d.id), 'Checked out. Others cannot add a version until you check it in.')" [title]="'Take it to edit; others cannot add a version meanwhile' | translate">{{ 'Check out' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="d.canRelease" (click)="act(api.releaseDocument(d.id), 'Checked in.')">{{ d.checkedOutByMe ? ('Check in' | translate) : ('Take over lock' | translate) }}</button>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="d.canEdit" (click)="startEdit(d)">{{ 'Details' | translate }}</button>
                <ng-container *ngIf="d.canManage && !d.archived">
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="!d.sharedWithClient && !d.confidential && (!d.fromClient || d.reviewStatus === 'ACCEPTED')" (click)="flags(d, { sharedWithClient: true }, 'Shared with the client.')">{{ 'Share with client' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="d.sharedWithClient" (click)="flags(d, { sharedWithClient: false }, 'No longer shared with the client.')">{{ 'Stop sharing' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="!d.confidential" (click)="flags(d, { confidential: true }, 'Marked confidential.')">{{ 'Make confidential' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="d.confidential" (click)="flags(d, { confidential: false }, 'No longer confidential.')">{{ 'Remove confidential' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger" (click)="act(api.archiveDocument(d.id), 'Archived.')">{{ 'Archive' | translate }}</button>
                </ng-container>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="d.canManage && d.archived" (click)="act(api.restoreDocument(d.id), 'Restored.')">{{ 'Restore' | translate }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="text-muted" *ngIf="!l.documents.length">{{ 'No documents on this matter yet.' | translate }}<span *ngIf="l.canUpload"> {{ 'Use "Add a document" to upload one.' | translate }}</span></div>
        <div class="text-muted mt-2" style="font-size:0.8rem;">{{ 'Documents are stored in the system\\'s database and are included in its backups. Confidential documents are not listed for people outside the matter\\'s team. Earlier versions are always kept.' | translate }}</div>
      </div>

      <!-- versions -->
      <div class="card mt-3" style="padding:1rem;" *ngIf="sel">
        <div class="flex justify-between align-center mb-2"><h4>{{ 'Versions of {p0}' | translate: { p0: (sel.document.name) } }}</h4><button class="btn btn-sm btn-outline-secondary" (click)="sel = null">{{ 'Close' | translate }}</button></div>
        <table class="stack table-sm">
          <thead><tr><th>{{ 'Version' | translate }}</th><th>{{ 'File' | translate }}</th><th>{{ 'Size' | translate }}</th><th>{{ 'Added by' | translate }}</th><th>{{ 'When' | translate }}</th><th>{{ 'Note' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let v of sel.versions">
              <td data-label="Version">{{ 'v{p0}' | translate: { p0: (v.versionNo) } }}<span *ngIf="v.versionNo === sel.document.currentVersion"> {{ '(current)' | translate }}</span></td>
              <td data-label="File">{{ v.fileName }}<div class="text-muted" style="font-size:0.7rem;" [title]="'SHA-256 fingerprint of the file' | translate">{{ v.sha256 | slice: 0 : 12 }}…</div></td>
              <td data-label="Size">{{ size(v.sizeBytes) }}</td><td data-label="Added by">{{ v.uploadedByName }}</td><td data-label="When">{{ v.createdAt | date: 'd MMM y, HH:mm' }}</td><td data-label="Note">{{ v.note }}</td>
              <td><button class="btn btn-sm btn-outline-primary" (click)="downloadVersion(sel.document, v.versionNo, v.fileName)">{{ 'Download' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <form class="mt-3" *ngIf="sel.document.canLock && !sel.document.archived" (ngSubmit)="addVersion()">
          <h5 class="mb-1">{{ 'Add a new version' | translate }}</h5>
          <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="sel.document.checkedOutByName && !sel.document.checkedOutByMe">{{ '{p0} has this checked out; a new version can be added once they check it in.' | translate: { p0: (sel.document.checkedOutByName) } }}</div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="!sel.document.checkedOutByName || sel.document.checkedOutByMe">
            <div class="form-group" style="min-width:14rem;"><label class="form-label">{{ 'File' | translate }}</label><input class="form-control" type="file" name="vf" (change)="vFile = pick($event); vInput = $any($event.target)" /></div>
            <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'What changed' | translate }}</label><input class="form-control" name="vn" [(ngModel)]="vnote" /></div>
          </div>
          <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2" *ngIf="sel.document.checkedOutByMe"><input type="checkbox" name="vk" [(ngModel)]="vKeep" /> {{ 'Keep it checked out after saving' | translate }}</label>
          <button class="btn btn-primary" type="submit" [disabled]="busy || !vFile || (!!sel.document.checkedOutByName && !sel.document.checkedOutByMe)">{{ 'Save the new version' | translate }}</button>
        </form>
      </div>
    </ng-container>
  `,
})
export class MatterDocumentsComponent implements OnChanges {
  @Input() matterId!: number;

  l: DocList | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  showArchived = false;
  mode: 'upload' | 'generate' | null = null;
  categories = Object.entries(DOC_CATEGORY_LABEL).map(([key, label]) => ({ key, label }));
  up = { name: '', category: 'OTHER' as DocCategory, description: '', confidential: false };
  upFile: File | null = null;
  gen: { templateId: number | null; name: string; confidential: boolean } = { templateId: null, name: '', confidential: false };
  templates: DocTemplate[] = [];
  unresolved: string[] = [];
  sel: DocVersions | null = null;
  vFile: File | null = null;
  vInput: HTMLInputElement | null = null;
  vnote = '';
  vKeep = false;
  editing: DocItem | null = null;
  rejecting: DocItem | null = null;
  rejectNote = '';
  edit = { name: '', category: 'OTHER' as DocCategory, description: '' };

  constructor(public api: LawApiService) {}

  ngOnChanges(): void { this.load(); }

  load(): void {
    this.api.matterDocuments(this.matterId, this.showArchived).subscribe({
      next: (l) => { this.l = l; this.error = null; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the documents'),
    });
  }

  catLabel(c: DocCategory): string { return DOC_CATEGORY_LABEL[c] ?? c; }

  size(b: number): string {
    if (b < 1024) return `${b} B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(0)} KB`;
    return `${(b / 1024 / 1024).toFixed(1)} MB`;
  }

  pick(ev: Event): File | null { return (ev.target as HTMLInputElement).files?.[0] ?? null; }

  open(m: 'upload' | 'generate'): void {
    this.mode = this.mode === m ? null : m;
    this.info = null;
    this.error = null;
    this.unresolved = [];
    this.editing = null;
    if (m === 'generate' && !this.templates.length) {
      this.api.docTemplates(true).subscribe({ next: (t) => (this.templates = t), error: () => (this.templates = []) });
    }
  }

  private fail(e: any): void { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; }

  upload(input: HTMLInputElement): void {
    if (!this.upFile) return;
    const fd = new FormData();
    fd.append('file', this.upFile);
    if (this.up.name.trim()) fd.append('name', this.up.name.trim());
    fd.append('category', this.up.category);
    if (this.up.description.trim()) fd.append('description', this.up.description.trim());
    fd.append('confidential', String(this.up.confidential));
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.uploadDocument(this.matterId, fd).subscribe({
      next: () => { this.busy = false; this.info = 'Document added.'; this.mode = null; this.upFile = null; input.value = ''; this.up = { name: '', category: 'OTHER', description: '', confidential: false }; this.load(); },
      error: (e) => this.fail(e),
    });
  }

  generate(): void {
    if (!this.gen.templateId) return;
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.generateDocument(this.matterId, { templateId: this.gen.templateId, name: this.gen.name.trim() || null, confidential: this.gen.confidential }).subscribe({
      next: (r) => { this.busy = false; this.mode = null; this.info = `"${r.document.name}" was made. Download it to check and edit it.`; this.unresolved = r.unresolved; this.gen = { templateId: null, name: '', confidential: false }; this.load(); },
      error: (e) => this.fail(e),
    });
  }

  download(d: DocItem): void { this.downloadVersion(d, null, d.fileName); }

  downloadVersion(d: DocItem, version: number | null, fileName: string): void {
    this.error = null;
    this.api.downloadDocument(d.id, version).subscribe({ next: (b) => downloadBlob(b, fileName), error: () => (this.error = 'Could not download the file') });
  }

  versions(d: DocItem): void {
    this.api.documentVersions(d.id).subscribe({ next: (v) => { this.sel = v; this.vFile = null; this.vnote = ''; this.vKeep = false; }, error: (e) => this.fail(e) });
  }

  addVersion(): void {
    if (!this.sel || !this.vFile) return;
    const fd = new FormData();
    fd.append('file', this.vFile);
    if (this.vnote.trim()) fd.append('note', this.vnote.trim());
    fd.append('keepLocked', String(this.vKeep));
    const id = this.sel.document.id;
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.newDocumentVersion(id, fd).subscribe({
      next: () => { this.busy = false; this.info = 'New version saved.'; this.vFile = null; this.vnote = ''; if (this.vInput) this.vInput.value = ''; this.load(); this.api.documentVersions(id).subscribe((v) => (this.sel = v)); },
      error: (e) => this.fail(e),
    });
  }

  /** Runs a call that returns the changed document, then reloads the list (and the open versions panel). */
  act(call: import('rxjs').Observable<DocItem>, done: string): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (d) => {
        this.busy = false; this.info = done; this.load();
        if (this.sel && this.sel.document.id === d.id) this.api.documentVersions(d.id).subscribe((v) => (this.sel = v));
      },
      error: (e) => this.fail(e),
    });
  }

  flags(d: DocItem, f: { confidential?: boolean; sharedWithClient?: boolean }, done: string): void { this.act(this.api.setDocumentFlags(d.id, f), done); }

  pending(): DocItem[] { return (this.l?.documents ?? []).filter((d) => d.fromClient && d.reviewStatus === 'PENDING'); }

  review(d: DocItem): void { this.act(this.api.reviewDocument(d.id, true, null), 'Accepted. It is now one of the matter\'s documents.'); }

  startReject(d: DocItem): void { this.rejecting = d; this.rejectNote = ''; }

  confirmReject(): void {
    if (!this.rejecting || !this.rejectNote.trim()) return;
    const d = this.rejecting;
    this.rejecting = null;
    this.act(this.api.reviewDocument(d.id, false, this.rejectNote.trim()), 'Rejected. The client can see the reason.');
  }

  startEdit(d: DocItem): void { this.editing = d; this.edit = { name: d.name, category: d.category, description: d.description ?? '' }; this.mode = null; this.info = null; this.error = null; }

  saveEdit(): void {
    if (!this.editing) return;
    this.act(this.api.updateDocument(this.editing.id, { name: this.edit.name, category: this.edit.category, description: this.edit.description.trim() || null }), 'Saved.');
    this.editing = null;
  }
}
