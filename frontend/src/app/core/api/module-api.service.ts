import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { API_BASE } from '../constants';
import { HotelApiService } from './hotel-api.service';
import { SalesFeatures } from '../models';

export type ModuleKey = 'SALES' | 'PURCHASES' | 'ACCOUNTING' | 'POS' | 'HOTEL' | 'RESTAURANT' | 'MANUFACTURING' | 'SUBSCRIPTIONS' | 'HELPDESK' | 'EXPENSES' | 'ASSETS' | 'INSTALLMENTS' | 'TIME_TRACKING' | 'WORK_ORDERS' | 'BOOKING' | 'LAW_FIRM' | 'REAL_ESTATE' | 'PROPERTY_MANAGEMENT' | 'EMPLOYEES' | 'ORG_STRUCTURE' | 'ATTENDANCE' | 'CONTRACTS' | 'PAYROLL';

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
  /** Is the module part of the installed license? Core modules always are. */
  licensed: boolean;
  /**
   * CORE, ACTIVE (licensed and switched on), AVAILABLE (licensed, not yet activated), LOCKED (not in the license),
   * REQUESTED (locked, a request to NIWE is waiting) or EXPIRED (in a license that has run out).
   */
  state: ModuleState;
  /** When the waiting request was made (REQUESTED only). */
  requestedAt: string | null;
}

export type ModuleState = 'CORE' | 'ACTIVE' | 'AVAILABLE' | 'LOCKED' | 'REQUESTED' | 'EXPIRED';

/**
 * Settings > Modules: which optional modules are switched on. The sidebar and route guard read this. Every module counts as
 * off until the server says it is on (also if the state cannot be loaded), so a menu never shows for a module that is off.
 * Hotel's switch lives in HotelApiService.enabled, shared with Hotel > Settings.
 */
@Injectable({ providedIn: 'root' })
export class ModuleApiService {
  private enabledKeys = signal<ReadonlySet<string>>(new Set());
  /** Promotions, Gift cards and Loyalty points (Sales > Settings). Off until the server says on. */
  private salesFeatures = signal<SalesFeatures>({ promotions: false, giftCards: false, loyalty: false });
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
    return this.http.get<ModuleInfo[]>(this.base).pipe(
      tap((m) => this.apply(m)),
      switchMap((m) => this.refreshFeatures().pipe(map(() => m)))
    );
  }

  /** Is the sales feature switched on in Sales > Settings? Reads a signal, so templates re-render when it changes. */
  feature(key: 'promotions' | 'giftCards' | 'loyalty'): boolean {
    return this.salesFeatures()[key];
  }

  /** Reads the three switches again (after they are changed in Sales > Settings). Never errors: a failure leaves them off. */
  refreshFeatures(): Observable<SalesFeatures | null> {
    return this.http.get<SalesFeatures>(`${API_BASE}/sales/features`).pipe(
      tap((f) => this.salesFeatures.set(f)),
      catchError(() => of(null))
    );
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
