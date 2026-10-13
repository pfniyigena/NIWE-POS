import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { API_BASE } from '../constants';
import { HotelApiService } from './hotel-api.service';

export type ModuleKey = 'SALES' | 'PURCHASES' | 'ACCOUNTING' | 'POS' | 'HOTEL' | 'RESTAURANT' | 'MANUFACTURING' | 'SUBSCRIPTIONS' | 'HELPDESK' | 'EXPENSES' | 'ASSETS' | 'INSTALLMENTS' | 'EMPLOYEES' | 'ORG_STRUCTURE' | 'ATTENDANCE' | 'CONTRACTS' | 'PAYROLL';

export interface ModuleInfo {
  key: ModuleKey;
  name: string;
  icon: string;
  description: string;
  enabled: boolean;
  /** Core modules (Sales, Purchases, Accounting) are always on. */
  core: boolean;
  /** Modules this one works together with: switching it on switches them on too. */
  requires: ModuleKey[];
}

/**
 * Settings > Modules: which optional modules are switched on. The sidebar and route guard read this. Every module counts as
 * off until the server says it is on (also if the state cannot be loaded), so a menu never shows for a module that is off.
 * Hotel's switch lives in HotelApiService.enabled, shared with Hotel > Settings.
 */
@Injectable({ providedIn: 'root' })
export class ModuleApiService {
  private enabledKeys = signal<ReadonlySet<string>>(new Set());
  private loaded = false;
  private loading: Observable<void> | null = null;

  private base = `${API_BASE}/modules`;

  constructor(private http: HttpClient, private hotelApi: HotelApiService) {}

  /** Is the module switched on? Reads signals, so templates re-render when it changes. */
  on(key: ModuleKey): boolean {
    if (key === 'SALES' || key === 'PURCHASES' || key === 'ACCOUNTING') return true;
    if (key === 'HOTEL') return this.hotelApi.enabled();
    return this.enabledKeys().has(key);
  }

  list(): Observable<ModuleInfo[]> {
    return this.http.get<ModuleInfo[]>(this.base).pipe(tap((m) => this.apply(m)));
  }

  /** Switches a module; the answer is the whole list, because integrated modules may have changed too. */
  set(key: ModuleKey, enabled: boolean): Observable<ModuleInfo[]> {
    return this.http.put<ModuleInfo[]>(`${this.base}/${key}`, { enabled }).pipe(tap((m) => this.apply(m)));
  }

  /** Loads the state once; emits when it is known (immediately if already loaded). Never errors. */
  whenLoaded(): Observable<void> {
    if (this.loaded) return of(undefined);
    if (!this.loading) {
      this.loading = this.list().pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
        tap(() => (this.loading = null))
      );
    }
    return this.loading;
  }

  /** Fire-and-forget version for the sidebar. */
  ensureLoaded(): void {
    this.whenLoaded().subscribe();
  }

  private apply(modules: ModuleInfo[]): void {
    this.loaded = true;
    this.enabledKeys.set(new Set(modules.filter((m) => m.enabled && m.key !== 'HOTEL').map((m) => m.key)));
    const hotel = modules.find((m) => m.key === 'HOTEL');
    if (hotel) this.hotelApi.enabled.set(hotel.enabled);
  }
}
