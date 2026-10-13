import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from './constants';
import { LoginResponse, Permission } from './models';
import { AreaCode, landingFor, needsManage, requiredAreas } from './access';

/** Fourier: "remove this feature where by when I refresh the page you are asking a logged user to login again" — the persisted session key. */
const STORAGE_KEY = 'pos.auth';

/**
 * Fourier: "remove this feature where by when I refresh the page you are asking a logged user to
 * login again" — reverts the earlier "clear session on restart" change: currentUser is restored
 * from localStorage on boot, and every login/logout keeps that storage in sync, so a page refresh
 * (or a dev-server restart, or reopening the tab) no longer logs anyone out. A user is only sent
 * back to /login by an explicit logout, the JWT actually expiring (480 minutes, see
 * app.jwt.expiration-minutes), or a genuine 401 from the server (see authInterceptor).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = signal<LoginResponse | null>(this.readStoredUser());

  constructor(private http: HttpClient) {}

  private readStoredUser(): LoginResponse | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as LoginResponse;
    } catch {
      // Corrupt/unparsable leftover value — never let a bad stored blob break app boot.
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE}/auth/login`, { username, password }).pipe(
      tap((response) => {
        this.currentUser.set(response);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(response));
      })
    );
  }

  logout(): void {
    this.currentUser.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  /**
   * Fourier: "Add forget password feature where user will input the email and he/she can reset
   * the password" — hits the public POST /api/auth/forgot-password endpoint. Always succeeds
   * from the caller's point of view (see PasswordResetService.requestReset's javadoc on why it
   * never reveals whether the email is actually registered) unless SMTP itself isn't configured,
   * which surfaces as a normal error response.
   */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/forgot-password`, { email });
  }

  /** The second half of the flow above — the link in that email lands on /reset-password?token=... */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/reset-password`, { token, newPassword });
  }

  /** Keeps the topbar (and anything else reading currentUser()) in sync right after a successful
   * "My Profile" save, without requiring a re-login just to see the new name reflected. */
  updateFullName(fullName: string): void {
    const current = this.currentUser();
    if (!current) return;
    this.currentUser.set({ ...current, fullName });
  }

  getToken(): string | null {
    return this.currentUser()?.token ?? null;
  }

  isLoggedIn(): boolean {
    return this.currentUser() !== null;
  }

  /**
   * Fourier: "Build a full permissions system" — literal grant check only (does NOT treat ADMIN
   * as automatically holding every permission): most of this app's ADMIN-or-permission checks are
   * written out explicitly as `auth.isAdmin || auth.hasPermission(...)` at each call site, mirroring
   * the backend's own `hasRole('ADMIN') or hasAuthority(...)` — because for discount-cap override
   * specifically, a plain ADMIN who hasn't separately been granted OVERRIDE_DISCOUNT_CAP stays
   * capped at the store's configured maximum exactly like today (see OrderService.validateDiscount's
   * javadoc) — baking an automatic ADMIN-always-true into this helper would get that one case wrong.
   */
  hasPermission(permission: Permission): boolean {
    return this.currentUser()?.permissions.includes(permission) ?? false;
  }

  isAdmin(): boolean {
    return this.currentUser()?.role === 'ADMIN';
  }

  /** Custom roles: may this user open (read) the given access area? Admins always may. */
  canView(area: AreaCode): boolean {
    if (this.isAdmin()) return true;
    const areas = this.currentUser()?.areas ?? [];
    return areas.includes(area + '_VIEW') || areas.includes(area + '_MANAGE');
  }

  /** Custom roles: may this user create/change things in the given access area? Admins always may. */
  canManage(area: AreaCode): boolean {
    if (this.isAdmin()) return true;
    return (this.currentUser()?.areas ?? []).includes(area + '_MANAGE');
  }

  /** True when the user holds at least one custom-role grant (and is not an admin). */
  hasAnyArea(): boolean {
    return !this.isAdmin() && (this.currentUser()?.areas?.length ?? 0) > 0;
  }

  /** Can this user open the given app path (used by the route guard and the sidebar)? */
  canAccessPath(path: string): boolean {
    if (this.isAdmin()) return true;
    const required = requiredAreas(path);
    if (required === 'ADMIN') return false;
    const write = needsManage(path);
    return required.some((area) => (write ? this.canManage(area) : this.canView(area)));
  }

  /** Where to send the user right after login. */
  homePath(posOn = true): string {
    if (this.isAdmin()) return posOn ? '/registers' : '/admin/dashboard';
    return landingFor(this.currentUser()?.areas ?? [], posOn);
  }
}
