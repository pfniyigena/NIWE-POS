import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { AppUser } from '../models';

/** Self-service "My Profile" (Task: "Add my profile, change my password") — every endpoint here
 * acts on the caller's own account, resolved server-side from the JWT, never by an id. */
@Injectable({ providedIn: 'root' })
export class MeApiService {
  constructor(private http: HttpClient) {}

  getProfile(): Observable<AppUser> {
    return this.http.get<AppUser>(`${API_BASE}/me`);
  }

  updateProfile(fullName: string, email?: string | null): Observable<AppUser> {
    return this.http.put<AppUser>(`${API_BASE}/me`, { fullName, email });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/me/change-password`, { currentPassword, newPassword });
  }

  /** Self-service "change my PIN" (Task: "Add change PIN inside my profile") — no current-PIN check; see the backend MeController javadoc. */
  changePin(pin: string): Observable<AppUser> {
    return this.http.post<AppUser>(`${API_BASE}/me/pin`, { pin });
  }
}
