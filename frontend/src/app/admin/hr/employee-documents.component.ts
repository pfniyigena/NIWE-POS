import { Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { downloadBlob } from '../../core/download-file';
import { DOC_CATEGORIES, DocCategory, EmployeeDocument, HrApiService, docCategoryLabel } from '../../core/api/hr-api.service';

/** The documents kept for one employee: contract copies, ID, qualifications, permits. Files can carry an expiry date and be shared with the employee. */
@Component({
  selector: 'app-employee-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePickerComponent],
  template: `
    <div class="card mb-3 emp-docs" style="padding:1rem;">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <strong>Documents</strong>
        <button class="btn btn-sm btn-outline-primary doc-add" type="button" *ngIf="canManage" (click)="showForm = !showForm">{{ showForm ? 'Close' : '+ Add document' }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="showForm" class="mb-3 doc-form" (ngSubmit)="upload()" style="border:1px solid var(--border, #ddd); border-radius:6px; padding:0.75rem;">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:14rem;"><label class="form-label">File</label>
            <input #pick class="form-control doc-file" type="file" (change)="chosen(pick.files)" />
          </div>
          <div class="form-group" style="min-width:12rem;"><label class="form-label">Name</label><input class="form-control doc-name" name="dn" [(ngModel)]="form.name" placeholder="Same as the file" /></div>
          <div class="form-group"><label class="form-label">Kind</label>
            <select class="form-control doc-cat" name="dc" [(ngModel)]="form.category"><option *ngFor="let c of categories" [ngValue]="c.value">{{ c.label }}</option></select>
          </div>
          <div class="form-group"><label class="form-label">Expires (optional)</label><app-date-picker name="de" [(ngModel)]="form.expiryDate"></app-date-picker></div>
        </div>
        <div class="form-group"><label class="form-label">Note (optional)</label><input class="form-control doc-desc" name="dd" [(ngModel)]="form.description" /></div>
        <label style="display:flex; gap:0.4rem; align-items:center;" class="mb-2"><input class="doc-share" type="checkbox" name="ds" [(ngModel)]="form.shared" /> Show it to the employee in My HR</label>
        <button class="btn btn-primary doc-save" type="submit" [disabled]="busy || !file">Upload</button>
      </form>

      <table class="stack table-sm doc-table" *ngIf="docs.length">
        <thead><tr><th>Name</th><th>Kind</th><th>Expires</th><th>Shared</th><th>Added</th><th></th></tr></thead>
        <tbody><tr *ngFor="let d of docs; trackBy: byId">
          <td data-label="Name">{{ d.name }}<div class="text-muted" style="font-size:0.8rem;">{{ d.fileName }} · {{ size(d.sizeBytes) }}<span *ngIf="d.description"> · {{ d.description }}</span></div></td>
          <td data-label="Kind">{{ label(d.category) }}</td>
          <td data-label="Expires">
            <span *ngIf="!d.expiryDate" class="text-muted">—</span>
            <span *ngIf="d.expiryDate">{{ d.expiryDate }}
              <span class="badge badge-danger" *ngIf="d.expiryState === 'EXPIRED'">Expired</span>
              <span class="badge badge-warning" *ngIf="d.expiryState === 'SOON'">{{ d.daysToExpiry }} days left</span>
            </span>
          </td>
          <td data-label="Shared">{{ d.sharedWithEmployee ? 'Yes' : 'No' }}</td>
          <td data-label="Added">{{ d.createdAt | date: 'yyyy-MM-dd' }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="d.uploadedByName">{{ d.uploadedByName }}</div></td>
          <td data-label="" class="no-wrap">
            <button class="btn btn-sm btn-outline-secondary doc-open" type="button" (click)="open(d)">Open</button>
            <button class="btn btn-sm btn-outline-secondary doc-download" type="button" (click)="download(d)">Download</button>
            <ng-container *ngIf="canManage">
              <button class="btn btn-sm btn-outline-secondary doc-share-toggle" type="button" (click)="toggleShared(d)">{{ d.sharedWithEmployee ? 'Hide from employee' : 'Share' }}</button>
              <button class="btn btn-sm btn-outline-danger doc-delete" type="button" (click)="remove(d)">Delete</button>
            </ng-container>
          </td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="loaded && !docs.length">No documents yet.</p>
    </div>
  `,
})
export class EmployeeDocumentsComponent implements OnChanges {
  @Input() employeeId = 0;
  @Input() canManage = false;
  @ViewChild('pick') picker?: ElementRef<HTMLInputElement>;

  docs: EmployeeDocument[] = [];
  categories = DOC_CATEGORIES;
  showForm = false;
  loaded = false;
  busy = false;
  error: string | null = null;
  file: File | null = null;
  form: { name: string; category: DocCategory; description: string; expiryDate: string; shared: boolean } = this.blank();

  constructor(private api: HrApiService) {}

  ngOnChanges(): void {
    if (this.employeeId) this.load();
  }

  private blank() {
    return { name: '', category: 'OTHER' as DocCategory, description: '', expiryDate: '', shared: false };
  }

  byId(_: number, d: EmployeeDocument): number { return d.id; }
  label(c: string): string { return docCategoryLabel(c); }
  size(n: number): string { return n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB'; }

  load(): void {
    this.api.employeeDocuments(this.employeeId).subscribe({
      next: (d) => { this.docs = d; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the documents'; },
    });
  }

  chosen(files: FileList | null): void {
    this.file = files && files.length ? files[0] : null;
    if (this.file && !this.form.name) this.form.name = this.file.name.replace(/\.[^.]+$/, '');
  }

  upload(): void {
    if (!this.file) return;
    this.busy = true;
    this.error = null;
    this.api.uploadDocument(this.employeeId, this.file, this.form).subscribe({
      next: () => {
        this.busy = false;
        this.file = null;
        this.form = this.blank();
        if (this.picker) this.picker.nativeElement.value = '';
        this.showForm = false;
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'The document could not be saved'; },
    });
  }

  open(d: EmployeeDocument): void {
    this.api.documentFile(d.id).subscribe({
      next: (b) => window.open(URL.createObjectURL(b), '_blank'),
      error: () => (this.error = 'Could not open the file'),
    });
  }

  download(d: EmployeeDocument): void {
    this.api.documentFile(d.id).subscribe({
      next: (b) => downloadBlob(b, d.fileName),
      error: () => (this.error = 'Could not download the file'),
    });
  }

  toggleShared(d: EmployeeDocument): void {
    this.api.updateDocument(d.id, {
      name: d.name, category: d.category, description: d.description, expiryDate: d.expiryDate, clearExpiry: false, sharedWithEmployee: !d.sharedWithEmployee,
    }).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = e?.error?.message ?? 'Could not change the document'),
    });
  }

  remove(d: EmployeeDocument): void {
    if (!confirm(`Delete "${d.name}"? The file is removed for good.`)) return;
    this.api.deleteDocument(d.id).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the document'),
    });
  }
}
