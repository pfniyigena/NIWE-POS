import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { PAGE_SIZE, PageResult, getPaged } from './paged';

/** What a list screen's search bar can narrow by. Empty strings / null mean "not set". */
export interface ListFilter {
  q: string;
  status: string;
  from: string;
  to: string;
  branchId: number | null;
}

export function emptyFilter(): ListFilter {
  return { q: '', status: '', from: '', to: '', branchId: null };
}

export function isFiltered(f: ListFilter): boolean {
  return !!(f.q.trim() || f.status || f.from || f.to || f.branchId != null);
}

/** Query parameters for a core list: the filter, the page, and any list-specific extras (customerId, method, ...). */
export function listParams(f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): HttpParams {
  let p = new HttpParams().set('page', String(page)).set('size', String(size));
  if (f.q.trim()) p = p.set('q', f.q.trim());
  if (f.status) p = p.set('status', f.status);
  if (f.from) p = p.set('from', f.from);
  if (f.to) p = p.set('to', f.to);
  if (f.branchId != null) p = p.set('branchId', String(f.branchId));
  for (const [k, v] of Object.entries(extra)) {
    if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
  }
  return p;
}

export function pagedList<T>(http: HttpClient, url: string, f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): Observable<PageResult<T>> {
  return getPaged<T>(http, url, listParams(f, page, size, extra));
}

/**
 * Everything a paged, filterable list screen needs: the current rows, the total that matched,
 * the page and the filter. A newer request cancels the one still in flight.
 */
export class ListState<T> {
  rows: T[] = [];
  total = 0;
  page = 0;
  size = PAGE_SIZE;
  filter: ListFilter = emptyFilter();
  error: string | null = null;
  loading = false;
  loaded = false;
  private sub?: Subscription;

  constructor(private fetch: (f: ListFilter, page: number, size: number) => Observable<PageResult<T>>, private failMessage: string) {}

  get filtered(): boolean {
    return isFiltered(this.filter);
  }

  load(): void {
    this.sub?.unsubscribe();
    this.loading = true;
    this.sub = this.fetch(this.filter, this.page, this.size).subscribe({
      next: (r) => {
        this.error = null;
        this.loading = false;
        this.loaded = true;
        if (r.items.length === 0 && r.total > 0 && this.page > 0) {
          this.page = Math.max(0, Math.ceil(r.total / this.size) - 1);
          this.load();
          return;
        }
        this.rows = r.items;
        this.total = r.total;
      },
      error: (err) => {
        this.error = err?.error?.message ?? this.failMessage;
        this.loading = false;
        this.loaded = true;
      },
    });
  }

  apply(f: ListFilter): void {
    this.filter = f;
    this.page = 0;
    this.load();
  }

  goto(p: number): void {
    this.page = p;
    this.load();
  }
}

/**
 * Paging and filtering done in the browser, for the few lists that still come from an endpoint with
 * no server-side paging (a customer's own payments). `text` gives the strings the search box looks in,
 * `when` the row's date (as yyyy-mm-dd or an ISO instant), `choice` the value the status choice compares.
 */
export function localPage<T>(
  items: T[],
  f: ListFilter,
  page: number,
  size: number,
  text: (x: T) => (string | null | undefined)[],
  when?: (x: T) => string | null | undefined,
  choice?: (x: T) => string | null | undefined,
): PageResult<T> {
  const q = f.q.trim().toLowerCase();
  const rows = items.filter((x) => {
    if (q && !text(x).some((s) => (s ?? '').toLowerCase().includes(q))) return false;
    if (f.status && (choice?.(x) ?? '').toLowerCase() !== f.status.toLowerCase()) return false;
    if (f.from || f.to) {
      const d = (when?.(x) ?? '').slice(0, 10);
      if (!d) return false;
      if (f.from && d < f.from) return false;
      if (f.to && d > f.to) return false;
    }
    return true;
  });
  return { items: rows.slice(page * size, page * size + size), total: rows.length };
}
