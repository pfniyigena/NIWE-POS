import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ShellComponent } from '../shared/shell.component';
import { AuditLogApiService } from '../core/api/audit-log-api.service';
import { AuditLogEntry } from '../core/models';

/**
 * Admin > Audit Log (Task: "Implement an audit log of who did what and when") — a searchable/
 * filterable table of every {@code AuditLogEntry} the backend has recorded, same server-side
 * paging shape as ProductListComponent's own table (Task: "check the loading of 100000
 * products") since this log only grows over time and could get just as large.
 */
@Component({
  selector: 'app-audit-log-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Audit Log</h3>
      </div>

      <div class="flex gap-2 mb-3" style="align-items:center; flex-wrap:wrap;">
        <input
          class="form-control"
          style="max-width:280px;"
          placeholder="Search by user or description"
          [(ngModel)]="searchTerm"
          (input)="onFilterChange()"
        />
        <select class="form-select" style="max-width:220px;" [(ngModel)]="actionFilter" (ngModelChange)="onFilterChange()">
          <option [ngValue]="null">All actions</option>
          <option *ngFor="let a of actions" [ngValue]="a">{{ a }}</option>
        </select>
        <div class="form-group" style="margin:0;">
          <label class="form-label" style="font-size:0.75rem;">From</label>
          <app-date-picker [(ngModel)]="fromDate" (ngModelChange)="onFilterChange()" ></app-date-picker>
        </div>
        <div class="form-group" style="margin:0;">
          <label class="form-label" style="font-size:0.75rem;">To</label>
          <app-date-picker [(ngModel)]="toDate" (ngModelChange)="onFilterChange()" ></app-date-picker>
        </div>
        <select class="form-select" style="max-width:140px;" [(ngModel)]="pageSize" (ngModelChange)="onPageSizeChange()">
          <option [ngValue]="25">25 / page</option>
          <option [ngValue]="50">50 / page</option>
          <option [ngValue]="100">100 / page</option>
          <option [ngValue]="200">200 / page</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;" *ngIf="!loading">
          {{ totalElements | number }} entr{{ totalElements === 1 ? 'y' : 'ies' }}
        </span>
      </div>

      <table>
        <thead>
          <tr>
            <th>When</th>
            <th>Who</th>
            <th>Action</th>
            <th>What happened</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let e of entries">
            <td style="white-space:nowrap;">{{ e.occurredAt | date: 'yyyy-MM-dd HH:mm:ss' }}</td>
            <td>{{ e.actorFullName || e.actorUsername }}</td>
            <td><span class="badge badge-secondary">{{ e.action }}</span></td>
            <td>{{ e.description }}</td>
          </tr>
          <tr *ngIf="!loading && entries.length === 0">
            <td colspan="4" class="text-muted">No audit log entries {{ hasFilters() ? 'match your filters' : 'yet' }}.</td>
          </tr>
          <tr *ngIf="loading">
            <td colspan="4" class="text-muted">Loading…</td>
          </tr>
        </tbody>
      </table>

      <div class="flex gap-2 mt-3" style="align-items:center;" *ngIf="totalPages > 1">
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page === 0" (click)="goToPage(page - 1)">&laquo; Prev</button>
        <span class="text-muted" style="font-size:0.85rem;">Page {{ page + 1 }} of {{ totalPages }}</span>
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page >= totalPages - 1" (click)="goToPage(page + 1)">Next &raquo;</button>
      </div>
    </app-shell>
  `,
})
export class AuditLogListComponent implements OnInit {
  entries: AuditLogEntry[] = [];
  actions: string[] = [];
  error: string | null = null;
  loading = false;

  searchTerm = '';
  actionFilter: string | null = null;
  /** "yyyy-MM-dd" strings straight out of app-date-picker, or null — same convention as ReportViewerComponent's own filter.from/to. */
  fromDate: string | null = null;
  toDate: string | null = null;
  page = 0;
  pageSize = 50;
  totalElements = 0;
  totalPages = 0;

  private filterDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(private auditLogApi: AuditLogApiService) {}

  ngOnInit(): void {
    this.auditLogApi.listActions().subscribe({
      next: (actions) => (this.actions = actions),
      error: () => (this.actions = []),
    });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.auditLogApi.search(this.searchTerm || null, this.actionFilter, this.fromDate, this.toDate, this.page, this.pageSize).subscribe({
      next: (result) => {
        this.entries = result.content;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load the audit log';
        this.loading = false;
      },
    });
  }

  /** Debounced so every keystroke/filter tweak doesn't fire its own request. */
  onFilterChange(): void {
    if (this.filterDebounce) clearTimeout(this.filterDebounce);
    this.filterDebounce = setTimeout(() => {
      this.page = 0;
      this.load();
    }, 300);
  }

  onPageSizeChange(): void {
    this.page = 0;
    this.load();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.page = page;
    this.load();
  }

  hasFilters(): boolean {
    return !!(this.searchTerm || this.actionFilter || this.fromDate || this.toDate);
  }
}
