import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { downloadBlob } from '../../core/download-file';
import { ImportResult, RealEstateApiService } from '../../core/api/realestate-api.service';

/** Bring leads or properties in from an Excel or CSV file: download the template, choose the filled-in file, read what the check says, then import. The check saves nothing. */
@Component({
  selector: 'app-re-import',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card mb-3 re-import" style="padding:1rem;">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>Import {{ kind }} from a file</h4>
        <button class="btn btn-sm btn-outline-secondary imp-close" type="button" (click)="closed.emit()">Close</button>
      </div>
      <div class="text-muted mb-2" style="font-size:0.9rem;">
        1. Download the template and fill it in (or use your own sheet: the first row must have headings such as {{ kind === 'leads' ? 'Name and Phone' : 'Title and Price' }}).
        2. Choose the file. It is checked first and nothing is saved. 3. Read the result and import.
      </div>
      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <button class="btn btn-sm btn-outline-secondary imp-tpl-xlsx" type="button" (click)="template('xlsx')">Template (Excel)</button>
        <button class="btn btn-sm btn-outline-secondary imp-tpl-csv" type="button" (click)="template('csv')">Template (CSV)</button>
        <input #pick class="imp-file" type="file" accept=".xlsx,.csv,.txt" (change)="chosen(pick.files)" style="max-width:22rem;" />
        <label *ngIf="kind === 'properties'" style="display:flex; gap:0.4rem; align-items:center;" title="Properties that have the price they need are listed; the others stay as drafts">
          <input class="imp-list" type="checkbox" name="lt" [(ngModel)]="listThem" (ngModelChange)="recheck()" /> List them as available
        </label>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success imp-done" *ngIf="done">{{ done }}</div>
      <div class="text-muted" *ngIf="busy">Working…</div>

      <ng-container *ngIf="result && !done">
        <div class="alert imp-summary" [ngClass]="result.ok ? 'alert-info' : 'alert-warning'">
          <strong>{{ result.message }}</strong>
          <div style="font-size:0.85rem;" *ngIf="result.ignoredColumns.length">Columns not used: {{ result.ignoredColumns.join(', ') }}</div>
        </div>
        <div class="mb-2" *ngIf="result.sample.length"><span class="text-muted" style="font-size:0.85rem;">First rows that will go in:</span> {{ result.sample.join(', ') }}<span class="text-muted" *ngIf="result.ok > result.sample.length"> and {{ result.ok - result.sample.length }} more</span></div>
        <div class="mb-2" *ngIf="result.notes.length" style="font-size:0.9rem;">
          <div *ngFor="let n of result.notes">Row {{ n.row }} ({{ n.label }}): {{ n.message }}</div>
        </div>
        <div *ngIf="result.problems.length">
          <h5 class="mb-1">Rows that will be skipped ({{ result.skipped }})</h5>
          <div class="text-muted mb-1" style="font-size:0.85rem;">Fix them in your file and choose it again, or import the rest now. The row number is the spreadsheet's row.</div>
          <table class="stack table-sm imp-problems">
            <thead><tr><th>Row</th><th>{{ kind === 'leads' ? 'Lead' : 'Property' }}</th><th>Why</th></tr></thead>
            <tbody><tr *ngFor="let p of shown(); trackBy: byRow">
              <td data-label="Row">{{ p.row }}</td><td [attr.data-label]="kind === 'leads' ? 'Lead' : 'Property'">{{ p.label }}</td><td data-label="Why">{{ p.message }}</td>
            </tr></tbody>
          </table>
          <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="result.problems.length > 20" (click)="all = !all">{{ all ? 'Show fewer' : 'Show all ' + result.problems.length }}</button>
        </div>
        <div class="mt-2 flex gap-2">
          <button class="btn btn-primary imp-go" type="button" (click)="go()" [disabled]="busy || !result.ok">Import {{ result.ok }} {{ kind === 'leads' ? (result.ok === 1 ? 'lead' : 'leads') : (result.ok === 1 ? 'property' : 'properties') }}</button>
        </div>
      </ng-container>
    </div>
  `,
})
export class ReImportComponent {
  /** leads or properties */
  @Input() kind: 'leads' | 'properties' = 'leads';
  /** Emits after rows were imported, so the list can reload. */
  @Output() imported = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('pick') picker?: ElementRef<HTMLInputElement>;
  file: File | null = null;
  result: ImportResult | null = null;
  listThem = false;
  busy = false;
  all = false;
  error: string | null = null;
  done: string | null = null;

  constructor(private api: RealEstateApiService) {}

  byRow(_: number, p: { row: number }): number { return p.row; }
  shown() { return this.result ? (this.all ? this.result.problems : this.result.problems.slice(0, 20)) : []; }

  template(format: 'xlsx' | 'csv'): void {
    this.error = null;
    this.api.importTemplate(this.kind, format).subscribe({
      next: (b) => downloadBlob(b, `${this.kind}-import-template.${format}`),
      error: () => (this.error = 'Could not download the template'),
    });
  }

  chosen(files: FileList | null): void {
    this.file = files && files.length ? files[0] : null;
    this.result = null;
    this.done = null;
    this.error = null;
    this.all = false;
    if (this.file) this.recheck();
  }

  recheck(): void {
    if (!this.file) return;
    this.busy = true;
    this.error = null;
    this.api.importFile(this.kind, this.file, true, this.listThem).subscribe({
      next: (r) => { this.busy = false; this.result = r; },
      error: (e) => { this.busy = false; this.result = null; this.error = e?.error?.message ?? 'The file could not be checked'; },
    });
  }

  go(): void {
    if (!this.file) return;
    this.busy = true;
    this.error = null;
    this.api.importFile(this.kind, this.file, false, this.listThem).subscribe({
      next: (r) => {
        this.busy = false;
        this.done = r.message + (r.skipped ? ' The skipped rows were not saved.' : '');
        this.result = null;
        this.file = null;
        if (this.picker) this.picker.nativeElement.value = '';
        this.imported.emit();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'The import failed'; },
    });
  }
}
