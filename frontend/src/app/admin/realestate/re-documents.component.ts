import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DOC_CATEGORIES, DocSubject, ReDocCategory, ReDocument, RealEstateApiService, docCategoryLabel } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
/** The documents of one Real Estate record (a property, project, unit, deal, unit sale, lead or valuation): upload, download, change details, delete. */
@Component({
  selector: 'app-re-documents',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="flex justify-between align-center mb-2">
      <h4 *ngIf="title">{{ title }}</h4>
      <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="canManage && !adding" (click)="openAdd()">{{ '+ Add a document' | translate }}</button>
    </div>
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

    <form *ngIf="adding" (ngSubmit)="upload()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <div class="form-group mb-2" style="flex:2; min-width:14rem;">
          <label class="form-label">{{ 'File *' | translate }} <span class="text-muted">{{ '(PDF, picture, Word, Excel or text; up to 20 MB)' | translate }}</span></label>
          <input class="form-control" type="file" name="file" (change)="picked($event)" accept=".pdf,.jpg,.jpeg,.png,.webp,.docx,.xlsx,.txt" />
        </div>
        <div class="form-group mb-2" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="nm" [(ngModel)]="f.name" maxlength="200" [placeholder]="'the file name if left empty' | translate" /></div>
        <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Kind' | translate }}</label>
          <select class="form-select" name="cat" [(ngModel)]="f.category"><option *ngFor="let c of categories" [ngValue]="c.value">{{ c.label }}</option></select></div>
        <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Expires on' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label><input class="form-control" type="date" name="ex" [(ngModel)]="f.expiryDate" /></div>
      </div>
      <div class="form-group mb-2"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="ds" [(ngModel)]="f.description" maxlength="1000" /></div>
      <div class="flex gap-2">
        <button class="btn btn-sm btn-primary" type="submit" [disabled]="busy">{{ busy ? ('Uploading…' | translate) : ('Upload' | translate) }}</button>
        <button class="btn btn-sm btn-outline-secondary" type="button" (click)="adding = false" [disabled]="busy">{{ 'Cancel' | translate }}</button>
      </div>
    </form>

    <table class="stack table-sm" *ngIf="list.length">
      <thead><tr><th>{{ 'Document' | translate }}</th><th>{{ 'Kind' | translate }}</th><th>{{ 'Expires' | translate }}</th><th class="text-end">{{ 'Size' | translate }}</th><th></th></tr></thead>
      <tbody>
        <ng-container *ngFor="let d of list">
          <tr>
            <td data-label="Document">
              <a href="#" (click)="download(d); $event.preventDefault()"><strong>{{ d.name }}</strong></a>
              <div class="text-muted" style="font-size:0.8rem;">{{ d.fileName }} · {{ d.uploadedByName || '—' }} · {{ d.createdAt | date: 'd MMM y' }}<span *ngIf="d.description"> · {{ d.description }}</span></div>
            </td>
            <td data-label="Kind">{{ kind(d.category) }}</td>
            <td style="white-space:nowrap;" data-label="Expires">
              <span *ngIf="d.expiryDate">{{ d.expiryDate | date: 'd MMM y' }}</span><span *ngIf="!d.expiryDate" class="text-muted">—</span>
              <span class="badge badge-danger" *ngIf="d.expiryState === 'EXPIRED'" style="margin-left:0.3rem;">{{ 'Expired' | translate }}</span>
              <span class="badge badge-warning" *ngIf="d.expiryState === 'SOON'" style="margin-left:0.3rem;">{{ '{p0} day{p1} left' | translate: { p0: (d.daysToExpiry), p1: (d.daysToExpiry === 1 ? '' : 's') } }}</span>
            </td>
            <td class="text-end" style="white-space:nowrap;" data-label="Size">{{ size(d.sizeBytes) }}</td>
            <td style="white-space:nowrap; text-align:right;" data-label="">
              <button class="btn btn-sm btn-outline-secondary" type="button" (click)="download(d)">{{ 'Download' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="canManage" style="margin-left:0.2rem;" (click)="startEdit(d)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="canManage" style="margin-left:0.2rem;" (click)="remove(d)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="editing?.id === d.id">
            <td colspan="5">
              <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                <div class="form-group" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="enm" [(ngModel)]="e.name" maxlength="200" /></div>
                <div class="form-group" style="min-width:11rem;"><label class="form-label">{{ 'Kind' | translate }}</label>
                  <select class="form-select" name="ecat" [(ngModel)]="e.category"><option *ngFor="let c of categories" [ngValue]="c.value">{{ c.label }}</option></select></div>
                <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Expires on' | translate }}</label><input class="form-control" type="date" name="eex" [(ngModel)]="e.expiryDate" /></div>
                <div class="form-group" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="eds" [(ngModel)]="e.description" maxlength="1000" /></div>
                <button class="btn btn-sm btn-primary" type="button" (click)="saveEdit()" [disabled]="busy">{{ 'Save' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" type="button" (click)="editing = null">{{ 'Cancel' | translate }}</button>
              </div>
            </td>
          </tr>
        </ng-container>
      </tbody>
    </table>
    <div class="text-muted" *ngIf="!list.length && loaded">{{ 'No documents yet.' | translate }}</div>
  `,
})
export class ReDocumentsComponent implements OnChanges {
  @Input({ required: true }) subjectType!: DocSubject;
  @Input({ required: true }) subjectId!: number;
  @Input() canManage = false;
  @Input() title: string | null = 'Documents';
  @Input() defaultCategory: ReDocCategory = 'OTHER';
  @Output() changed = new EventEmitter<number>();

  categories = DOC_CATEGORIES;
  list: ReDocument[] = [];
  loaded = false;
  adding = false;
  busy = false;
  error: string | null = null;
  file: File | null = null;
  f = { name: '', category: 'OTHER' as ReDocCategory, description: '', expiryDate: '' };
  editing: ReDocument | null = null;
  e = { name: '', category: 'OTHER' as ReDocCategory, description: '', expiryDate: '' };

  constructor(private api: RealEstateApiService) {}

  ngOnChanges(): void {
    this.load();
  }

  private load(): void {
    if (this.subjectId == null) return;
    this.api.documents({ subjectType: this.subjectType, subjectId: this.subjectId }).subscribe({
      next: (l) => { this.list = l; this.loaded = true; this.changed.emit(l.length); },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Could not load the documents'); },
    });
  }

  kind(c: string): string { return docCategoryLabel(c); }

  size(b: number): string {
    if (b >= 1048576) return (b / 1048576).toFixed(1) + ' MB';
    if (b >= 1024) return Math.round(b / 1024) + ' KB';
    return b + ' B';
  }

  openAdd(): void {
    this.adding = true;
    this.error = null;
    this.file = null;
    this.f = { name: '', category: this.defaultCategory, description: '', expiryDate: '' };
  }

  picked(ev: Event): void {
    this.file = (ev.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload(): void {
    this.error = null;
    if (!this.file) { this.error = T('Choose a file.'); return; }
    this.busy = true;
    this.api.uploadDocument(this.subjectType, this.subjectId, this.file, {
      name: this.f.name.trim() || null, category: this.f.category, description: this.f.description.trim() || null, expiryDate: this.f.expiryDate || null,
    }).subscribe({
      next: () => { this.busy = false; this.adding = false; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not upload the file'); },
    });
  }

  download(d: ReDocument): void {
    this.error = null;
    this.api.downloadDocument(d).subscribe({ error: (e) => (this.error = e?.error?.message ?? T('Could not download the file')) });
  }

  startEdit(d: ReDocument): void {
    this.editing = this.editing?.id === d.id ? null : d;
    this.e = { name: d.name, category: d.category, description: d.description ?? '', expiryDate: d.expiryDate ?? '' };
  }

  saveEdit(): void {
    if (!this.editing) return;
    this.error = null;
    this.busy = true;
    const had = !!this.editing.expiryDate;
    this.api.updateDocument(this.editing.id, {
      name: this.e.name.trim() || null, category: this.e.category, description: this.e.description, expiryDate: this.e.expiryDate || null, clearExpiry: had && !this.e.expiryDate,
    }).subscribe({
      next: () => { this.busy = false; this.editing = null; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not save the changes'); },
    });
  }

  remove(d: ReDocument): void {
    if (!confirm(T('Delete "{p0}"? The file is removed for good.', { p0: d.name }))) return;
    this.error = null;
    this.api.deleteDocument(d.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? T('Could not delete the document')) });
  }
}
