import { Component, ElementRef, ViewChild } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { downloadBlob } from '../../core/download-file';
import { EmployeeImportResult, HrApiService } from '../../core/api/hr-api.service';

/** Employees > Import: bring staff in from an Excel or CSV file. The file is checked first and nothing is saved until you press Import. */
@Component({
  selector: 'app-employee-import',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Import employees' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/hr/employees">{{ 'Back to employees' | translate }}</a>
      </div>
      <div class="card" style="padding:1rem;">
        <div class="text-muted mb-2" style="font-size:0.9rem;">
          {{ '1. Download the template and fill it in (or use your own sheet: the first row must have headings such as First name, Last name and Hire date). 2. Choose the file. It is checked first and nothing is saved. 3. Read the result and import. Employees who are already in the system are left as they are.' | translate }}
        </div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
          <button class="btn btn-sm btn-outline-secondary imp-tpl-xlsx" type="button" (click)="template('xlsx')">{{ 'Template (Excel)' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary imp-tpl-csv" type="button" (click)="template('csv')">{{ 'Template (CSV)' | translate }}</button>
          <input #pick class="imp-file" type="file" accept=".xlsx,.csv,.txt" (change)="chosen(pick.files)" style="max-width:22rem;" />
          <label style="display:flex; gap:0.4rem; align-items:center;" [title]="'Departments named in the file that do not exist yet are created' | translate">
            <input class="imp-depts" type="checkbox" name="cd" [(ngModel)]="createDepartments" (ngModelChange)="recheck()" /> {{ 'Add missing departments' | translate }}
          </label>
        </div>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <div class="alert alert-success imp-done" *ngIf="done">{{ done }} <a routerLink="/admin/hr/employees">{{ 'See the employees' | translate }}</a></div>
        <div class="text-muted" *ngIf="busy">{{ 'Working…' | translate }}</div>

        <ng-container *ngIf="result && !done">
          <div class="alert imp-summary" [ngClass]="result.ok ? 'alert-info' : 'alert-warning'">
            <strong>{{ result.message }}</strong>
            <div style="font-size:0.85rem;" *ngIf="result.ignoredColumns.length">{{ 'Columns not used: {p0}' | translate: { p0: (result.ignoredColumns.join(', ')) } }}</div>
          </div>
          <div class="mb-2" *ngIf="result.sample.length"><span class="text-muted" style="font-size:0.85rem;">{{ 'First rows that will go in:' | translate }}</span> {{ result.sample.join(', ') }}<span class="text-muted" *ngIf="result.ok > result.sample.length"> {{ 'and {p0} more' | translate: { p0: (result.ok - result.sample.length) } }}</span></div>
          <div class="mb-2" *ngIf="result.notes.length" style="font-size:0.9rem;">
            <div *ngFor="let n of result.notes">{{ 'Row {p0} ({p1}): {p2}' | translate: { p0: (n.row), p1: (n.label), p2: (n.message) } }}</div>
          </div>
          <div *ngIf="result.problems.length">
            <h5 class="mb-1">{{ 'Rows that will be skipped ({p0})' | translate: { p0: (result.skipped) } }}</h5>
            <div class="text-muted mb-1" style="font-size:0.85rem;">{{ 'Fix them in your file and choose it again, or import the rest now. The row number is the spreadsheet\\'s row.' | translate }}</div>
            <table class="stack table-sm imp-problems">
              <thead><tr><th>{{ 'Row' | translate }}</th><th>{{ 'Employee' | translate }}</th><th>{{ 'Why' | translate }}</th></tr></thead>
              <tbody><tr *ngFor="let p of shown(); trackBy: byRow">
                <td data-label="Row">{{ p.row }}</td><td data-label="Employee">{{ p.label }}</td><td data-label="Why">{{ p.message }}</td>
              </tr></tbody>
            </table>
            <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="result.problems.length > 20" (click)="all = !all">{{ all ? ('Show fewer' | translate) : ('Show all {p0}' | translate: { p0: result.problems.length }) }}</button>
          </div>
          <div class="mt-2">
            <button class="btn btn-primary imp-go" type="button" (click)="go()" [disabled]="busy || !result.ok">{{ 'Import {p0} {p1}' | translate: { p0: (result.ok), p1: (result.ok === 1 ? 'employee' : 'employees') } }}</button>
          </div>
        </ng-container>
      </div>
    </app-shell>
  `,
})
export class EmployeeImportComponent {
  @ViewChild('pick') picker?: ElementRef<HTMLInputElement>;
  file: File | null = null;
  result: EmployeeImportResult | null = null;
  createDepartments = false;
  busy = false;
  all = false;
  error: string | null = null;
  done: string | null = null;

  constructor(private api: HrApiService) {}

  byRow(_: number, p: { row: number }): number { return p.row; }
  shown() { return this.result ? (this.all ? this.result.problems : this.result.problems.slice(0, 20)) : []; }

  template(format: 'xlsx' | 'csv'): void {
    this.error = null;
    this.api.importEmployeesTemplate(format).subscribe({
      next: (b) => downloadBlob(b, `employees-import-template.${format}`),
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
    this.api.importEmployees(this.file, true, this.createDepartments).subscribe({
      next: (r) => { this.busy = false; this.result = r; },
      error: (e) => { this.busy = false; this.result = null; this.error = e?.error?.message ?? 'The file could not be checked'; },
    });
  }

  go(): void {
    if (!this.file) return;
    this.busy = true;
    this.error = null;
    this.api.importEmployees(this.file, false, this.createDepartments).subscribe({
      next: (r) => {
        this.busy = false;
        this.done = r.message + (r.skipped ? ' The skipped rows were not saved.' : '');
        this.result = null;
        this.file = null;
        if (this.picker) this.picker.nativeElement.value = '';
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'The import failed'; },
    });
  }
}
