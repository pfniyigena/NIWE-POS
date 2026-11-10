import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ShellComponent } from '../shared/shell.component';
import { LoginEvent, LoginLogApiService, LoginLogSummary } from '../core/api/login-log-api.service';

/**
 * v1.14.0: Admin > Sign-in log — who signed in, and who tried and failed (wrong password, switched-off or locked account, unknown name),
 * with the address and device each attempt came from. Passwords are never recorded.
 */
@Component({
  selector: 'app-login-log',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Sign-in log' | translate }}</h3>
        <button class="btn btn-sm btn-outline-secondary" (click)="reload()">{{ 'Refresh' | translate }}</button>
      </div>

      <div class="flex gap-3 mb-3" style="flex-wrap:wrap;" *ngIf="summary">
        <div class="card" style="padding:0.75rem 1.25rem;" data-testid="tile-ok">
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Signed in, last 24 hours' | translate }}</div>
          <div style="font-size:1.5rem; font-weight:600;">{{ summary.okLast24h | number }}</div>
        </div>
        <div class="card" style="padding:0.75rem 1.25rem;" data-testid="tile-failed">
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Failed attempts, last 24 hours' | translate }}</div>
          <div style="font-size:1.5rem; font-weight:600;" [style.color]="summary.failedLast24h > 0 ? '#b91c1c' : 'inherit'">{{ summary.failedLast24h | number }}</div>
        </div>
        <div class="card" style="padding:0.75rem 1.25rem;" data-testid="tile-addresses">
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Addresses with failures' | translate }}</div>
          <div style="font-size:1.5rem; font-weight:600;">{{ summary.failingAddressesLast24h | number }}</div>
        </div>
      </div>

      <div class="flex gap-2 mb-3" style="align-items:center; flex-wrap:wrap;">
        <input class="form-control" style="max-width:260px;" [placeholder]="'Search by user name' | translate" [(ngModel)]="searchTerm" (input)="onFilterChange()" />
        <select class="form-select" style="max-width:180px;" [(ngModel)]="outcome" (ngModelChange)="onFilterChange()">
          <option value="">{{ 'All attempts' | translate }}</option>
          <option value="OK">{{ 'Signed in' | translate }}</option>
          <option value="FAILED">{{ 'Failed' | translate }}</option>
        </select>
        <div class="form-group" style="margin:0;">
          <label class="form-label" style="font-size:0.75rem;">{{ 'From' | translate }}</label>
          <app-date-picker [(ngModel)]="fromDate" (ngModelChange)="onFilterChange()"></app-date-picker>
        </div>
        <div class="form-group" style="margin:0;">
          <label class="form-label" style="font-size:0.75rem;">{{ 'To' | translate }}</label>
          <app-date-picker [(ngModel)]="toDate" (ngModelChange)="onFilterChange()"></app-date-picker>
        </div>
        <select class="form-select" style="max-width:140px;" [(ngModel)]="pageSize" (ngModelChange)="onPageSizeChange()">
          <option [ngValue]="25">{{ '25 / page' | translate }}</option>
          <option [ngValue]="50">{{ '50 / page' | translate }}</option>
          <option [ngValue]="100">{{ '100 / page' | translate }}</option>
        </select>
        <span class="text-muted" style="font-size:0.85rem;" *ngIf="!loading">{{ '{p0} attempt{p1}' | translate: { p0: (totalElements | number), p1: (totalElements === 1 ? '' : 's') } }}</span>
      </div>

      <table>
        <thead>
          <tr><th>{{ 'When' | translate }}</th><th>{{ 'User name' | translate }}</th><th>{{ 'Result' | translate }}</th><th>{{ 'Why' | translate }}</th><th>{{ 'Address' | translate }}</th><th>{{ 'Device' | translate }}</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let e of events">
            <td style="white-space:nowrap;">{{ e.at | date: 'yyyy-MM-dd HH:mm:ss' }}</td>
            <td>{{ e.username }}</td>
            <td>
              <span class="badge" [ngClass]="e.success ? 'badge-success' : 'badge-danger'">{{ e.success ? ('Signed in' | translate) : ('Failed' | translate) }}</span>
            </td>
            <td>{{ e.reason }}</td>
            <td>{{ e.ip }}</td>
            <td [title]="e.userAgent || ''" style="font-family:monospace; font-size:0.8rem;">{{ shortDevice(e.deviceId) }}</td>
          </tr>
          <tr *ngIf="!loading && events.length === 0">
            <td colspan="6" class="text-muted">{{ 'No sign-in attempts {p0}.' | translate: { p0: (hasFilters() ? 'match your filters' : 'yet') } }}</td>
          </tr>
          <tr *ngIf="loading"><td colspan="6" class="text-muted">{{ 'Loading…' | translate }}</td></tr>
        </tbody>
      </table>

      <div class="flex gap-2 mt-3" style="align-items:center;" *ngIf="totalPages > 1">
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page === 0" (click)="goToPage(page - 1)">&laquo; Prev</button>
        <span class="text-muted" style="font-size:0.85rem;">{{ 'Page {p0} of {p1}' | translate: { p0: (page + 1), p1: (totalPages) } }}</span>
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page >= totalPages - 1" (click)="goToPage(page + 1)">Next &raquo;</button>
      </div>
    </app-shell>
  `,
})
export class LoginLogComponent implements OnInit {
  events: LoginEvent[] = [];
  summary: LoginLogSummary | null = null;
  error: string | null = null;
  loading = false;

  searchTerm = '';
  outcome = '';
  fromDate: string | null = null;
  toDate: string | null = null;
  page = 0;
  pageSize = 50;
  totalElements = 0;
  totalPages = 0;

  private filterDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(private api: LoginLogApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.summary().subscribe({ next: (s) => (this.summary = s), error: () => (this.summary = null) });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.api.search(this.searchTerm || null, this.outcome, this.fromDate, this.toDate, this.page, this.pageSize).subscribe({
      next: (r) => {
        this.events = r.content;
        this.totalElements = r.totalElements;
        this.totalPages = r.totalPages;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load the sign-in log';
        this.loading = false;
      },
    });
  }

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
    return !!(this.searchTerm || this.outcome || this.fromDate || this.toDate);
  }

  shortDevice(id: string | null): string {
    return id ? (id.length > 14 ? id.slice(0, 14) + '…' : id) : '';
  }
}
