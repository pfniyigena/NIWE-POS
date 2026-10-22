import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Branch } from '../core/models';
import { ListFilter, emptyFilter, isFiltered } from '../core/api/list-state';
import { API_BASE } from '../core/constants';
import { downloadBlob } from '../core/download-file';

export interface FilterOption { value: string; label: string; }

/**
 * The search bar above a core list: a search box, an optional status choice, an optional date range
 * and an optional branch choice. Typing waits a moment before it asks the server; the other controls
 * ask at once. Emits a fresh {@link ListFilter} each time.
 */
@Component({
  selector: 'app-list-filter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="list-filter flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
      <input class="form-control lf-q" type="search" style="max-width:260px;" [placeholder]="placeholder" [(ngModel)]="f.q" (ngModelChange)="typed()" aria-label="Search" />
      <select class="form-select lf-status" style="width:auto;" *ngIf="statuses.length" [(ngModel)]="f.status" (ngModelChange)="now()" title="Status">
        <option value="">{{ statusAll }}</option>
        <option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
      </select>
      <ng-container *ngIf="dates">
        <label class="text-muted lf-date" style="font-size:0.85rem;">From <input class="form-control lf-from" type="date" style="width:auto;display:inline-block;" [(ngModel)]="f.from" (ngModelChange)="now()" /></label>
        <label class="text-muted lf-date" style="font-size:0.85rem;">To <input class="form-control lf-to" type="date" style="width:auto;display:inline-block;" [(ngModel)]="f.to" (ngModelChange)="now()" /></label>
      </ng-container>
      <select class="form-select lf-branch" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="f.branchId" (ngModelChange)="now()" title="Branch">
        <option [ngValue]="null">All branches</option>
        <option *ngFor="let br of branches" [ngValue]="br.id">{{ br.name }}</option>
      </select>
      <button class="btn btn-sm btn-outline-secondary lf-clear" type="button" *ngIf="active" (click)="clear()">Clear filters</button>
      <span class="lf-export flex gap-2 align-center" *ngIf="exportKind" style="margin-left:auto;">
        <span class="text-muted" style="font-size:0.85rem;">Export{{ active ? ' (filtered)' : '' }}</span>
        <button class="btn btn-sm btn-outline-secondary lf-x-xlsx" type="button" [disabled]="exporting" (click)="download('xlsx')">Excel</button>
        <button class="btn btn-sm btn-outline-secondary lf-x-pdf" type="button" [disabled]="exporting" (click)="download('pdf')">PDF</button>
        <button class="btn btn-sm btn-outline-secondary lf-x-csv" type="button" [disabled]="exporting" (click)="download('csv')">CSV</button>
      </span>
      <span class="text-danger lf-export-error" *ngIf="exportError" style="font-size:0.85rem;">{{ exportError }}</span>
    </div>
  `,
})
export class ListFilterComponent implements OnDestroy {
  @Input() placeholder = 'Search…';
  @Input() statuses: FilterOption[] = [];
  @Input() statusAll = 'All statuses';
  @Input() dates = true;
  @Input() branches: Branch[] = [];
  @Output() filterChange = new EventEmitter<ListFilter>();
  /** The list to export (the part after /api/export/), or empty for no Export buttons. */
  @Input() exportKind = '';
  /** Which query parameter the "status" choice travels in (payments send it as `method`, suppliers as `active`). */
  @Input() statusParam = 'status';
  /** Fixed extra parameters of the screen (a customer id when the list is one customer's). */
  @Input() exportExtra: Record<string, string | number | null | undefined> = {};
  exporting = false;
  exportError: string | null = null;

  constructor(private http: HttpClient) {}

  f: ListFilter = emptyFilter();
  private timer: ReturnType<typeof setTimeout> | null = null;

  get active(): boolean {
    return isFiltered(this.f);
  }

  typed(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.now(), 300);
  }

  /** What was last sent to the list — exports follow this, so file and screen agree even while a search is still being typed. */
  applied: ListFilter = emptyFilter();

  now(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.applied = { ...this.f };
    this.filterChange.emit({ ...this.f });
  }

  /** Downloads the whole list that the current search describes — not just the page on screen. */
  download(format: 'xlsx' | 'pdf' | 'csv'): void {
    if (this.timer) this.now();
    const f = this.applied;
    let p = new HttpParams().set('format', format);
    if (f.q.trim()) p = p.set('q', f.q.trim());
    if (f.status) p = p.set(this.statusParam, f.status);
    if (f.from) p = p.set('from', f.from);
    if (f.to) p = p.set('to', f.to);
    if (f.branchId != null) p = p.set('branchId', String(f.branchId));
    for (const [k, v] of Object.entries(this.exportExtra)) if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    this.exporting = true;
    this.exportError = null;
    this.http.get(`${API_BASE}/export/${this.exportKind}`, { params: p, responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, `${this.exportKind}.${format}`);
      },
      error: () => {
        this.exporting = false;
        this.exportError = 'The export failed.';
      },
    });
  }

  clear(): void {
    this.f = emptyFilter();
    this.now();
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
  }
}
