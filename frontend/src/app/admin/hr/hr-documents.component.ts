import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { downloadBlob } from '../../core/download-file';
import { DOC_CATEGORIES, DocumentSummary, EmployeeDocument, HrApiService, docCategoryLabel } from '../../core/api/hr-api.service';

/** Employees > Documents: every employee document in one place, with a warning for the ones that have expired or are about to. */
@Component({
  selector: 'app-hr-documents',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Employee documents</h3>
        <div class="text-muted" style="font-size:0.9rem;">Add a document from the employee's own page.</div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="flex gap-3 mb-3 doc-summary" style="flex-wrap:wrap;" *ngIf="summary">
        <button type="button" class="card doc-card-all" style="padding:0.75rem 1.25rem; cursor:pointer;" (click)="setState('')"><div class="text-muted">Documents</div><strong style="font-size:1.5rem;">{{ summary.total }}</strong></button>
        <button type="button" class="card doc-card-soon" style="padding:0.75rem 1.25rem; cursor:pointer;" (click)="setState('SOON')"><div class="text-muted">Expired or expiring in 30 days</div><strong style="font-size:1.5rem;">{{ summary.expired + summary.expiringSoon }}</strong></button>
        <button type="button" class="card doc-card-expired" style="padding:0.75rem 1.25rem; cursor:pointer;" (click)="setState('EXPIRED')"><div class="text-muted">Already expired</div><strong style="font-size:1.5rem;">{{ summary.expired }}</strong></button>
      </div>

      <form class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;" (ngSubmit)="load()">
        <input class="form-control doc-q" name="q" [(ngModel)]="q" placeholder="Search name, file or employee…" style="max-width:22rem;" />
        <select class="form-select doc-category" style="width:auto;" name="cat" [(ngModel)]="category" (ngModelChange)="load()">
          <option value="">All kinds</option>
          <option *ngFor="let c of categories" [value]="c.value">{{ c.label }}</option>
        </select>
        <select class="form-select doc-state" style="width:auto;" name="st" [(ngModel)]="state" (ngModelChange)="load()">
          <option value="">Any expiry</option>
          <option value="WITH_EXPIRY">Has an expiry date</option>
          <option value="SOON">Expired or expiring soon</option>
          <option value="EXPIRED">Expired</option>
        </select>
        <button class="btn btn-outline-secondary" type="submit">Search</button>
      </form>

      <table class="stack table-sm doc-table">
        <thead><tr><th>Employee</th><th>Document</th><th>Kind</th><th>Expires</th><th>Shared</th><th></th></tr></thead>
        <tbody><tr *ngFor="let d of rows; trackBy: byId">
          <td data-label="Employee"><a [routerLink]="['/admin/hr/employees', d.employeeId]">{{ d.employeeName }}</a><div class="text-muted" style="font-size:0.8rem;">{{ d.employeeNumber }}</div></td>
          <td data-label="Document">{{ d.name }}<div class="text-muted" style="font-size:0.8rem;">{{ d.fileName }}</div></td>
          <td data-label="Kind">{{ label(d.category) }}</td>
          <td data-label="Expires">
            <span *ngIf="!d.expiryDate" class="text-muted">—</span>
            <span *ngIf="d.expiryDate">{{ d.expiryDate }}
              <span class="badge badge-danger" *ngIf="d.expiryState === 'EXPIRED'">Expired</span>
              <span class="badge badge-warning" *ngIf="d.expiryState === 'SOON'">{{ d.daysToExpiry }} days left</span>
            </span>
          </td>
          <td data-label="Shared">{{ d.sharedWithEmployee ? 'Yes' : 'No' }}</td>
          <td data-label="" class="no-wrap">
            <button class="btn btn-sm btn-outline-secondary doc-open" type="button" (click)="open(d)">Open</button>
            <button class="btn btn-sm btn-outline-secondary doc-download" type="button" (click)="download(d)">Download</button>
          </td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="loaded && !rows.length">{{ q || category || state ? 'Nothing matches these filters.' : 'No documents have been added yet.' }}</p>
    </app-shell>
  `,
})
export class HrDocumentsComponent implements OnInit, OnDestroy {
  private sub?: Subscription;
  rows: EmployeeDocument[] = [];
  summary: DocumentSummary | null = null;
  categories = DOC_CATEGORIES;
  q = '';
  category = '';
  state = '';
  loaded = false;
  error: string | null = null;

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  byId(_: number, d: EmployeeDocument): number { return d.id; }
  label(c: string): string { return docCategoryLabel(c); }

  setState(s: string): void {
    this.state = s;
    this.load();
  }

  load(): void {
    this.api.documentSummary().subscribe({ next: (s) => (this.summary = s), error: () => {} });
    // a newer search replaces an older one that is still running, so a slow answer can never overwrite a newer one
    this.sub?.unsubscribe();
    this.sub = this.api.allDocuments({ q: this.q.trim(), category: this.category, state: this.state }).subscribe({
      next: (r) => { this.rows = r; this.loaded = true; this.error = null; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the documents'; },
    });
  }

  open(d: EmployeeDocument): void {
    this.api.documentFile(d.id).subscribe({ next: (b) => window.open(URL.createObjectURL(b), '_blank'), error: () => (this.error = 'Could not open the file') });
  }

  download(d: EmployeeDocument): void {
    this.api.documentFile(d.id).subscribe({ next: (b) => downloadBlob(b, d.fileName), error: () => (this.error = 'Could not download the file') });
  }
}
