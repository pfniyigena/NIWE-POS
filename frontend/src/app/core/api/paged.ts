import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/** One page of a list, with the number of rows that matched in all (from the X-Total-Count header). */
export interface PageResult<T> { items: T[]; total: number; }

/** How many rows a list screen asks for at a time. */
export const PAGE_SIZE = 25;

/** The paging fields a list filter can carry (page counts from 0). */
export interface PageParams { page?: number; size?: number; }

export function getPaged<T>(http: HttpClient, url: string, params: HttpParams): Observable<PageResult<T>> {
  return http.get<T[]>(url, { params, observe: 'response' }).pipe(
    map((r) => ({ items: r.body ?? [], total: Number(r.headers.get('X-Total-Count') ?? (r.body ?? []).length) })),
  );
}
