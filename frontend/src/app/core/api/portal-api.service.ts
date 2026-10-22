import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DocCategory, DocReview, MatterStatus } from './law-api.service';

// ---- what the client portal receives (never any internal note, rate or other client's data)
export interface PortalSession { token: string; expiresInMinutes: number; name: string; email: string; }
export interface PortalInviteInfo { name: string; email: string; firmName: string | null; }
export interface PortalMatterRow { id: number; number: string; title: string; status: MatterStatus; practiceArea: string | null; leadName: string | null; openedDate: string | null; unreadMessages: number; nextDeadline: string | null; }
export interface PortalMe { name: string; email: string; clientName: string; firmName: string | null; firmPhone: string | null; firmEmail: string | null; matters: PortalMatterRow[]; }
export interface PortalFeatures { deadlines: boolean; documents: boolean; invoices: boolean; trust: boolean; upload: boolean; messages: boolean; }
export interface PortalDeadline { title: string; type: string; dueDate: string; dueTime: string | null; status: string; }
export interface PortalDocument { id: number; name: string; category: DocCategory; fileName: string; sizeBytes: number; version: number; updatedAt: string; }
export interface PortalUpload { id: number; name: string; fileName: string; sizeBytes: number; reviewStatus: DocReview | null; reviewNote: string | null; createdAt: string; }
export interface PortalInvoice { number: string; date: string; dueDate: string | null; total: number; paid: number; outstanding: number; }
export interface PortalTrustEntry { date: string; kind: string; amount: number; reference: string | null; purpose: string | null; balanceAfter: number; }
export interface PortalTrust { balance: number; entries: PortalTrustEntry[]; }
export interface PortalMessage { id: number; fromClient: boolean; senderName: string; body: string; createdAt: string; }
export interface PortalMatter {
  id: number; number: string; title: string; status: MatterStatus; practiceArea: string | null; jurisdiction: string | null; court: string | null; courtCaseNumber: string | null; leadName: string | null;
  openedDate: string | null; closedDate: string | null; clientUpdate: string | null; clientUpdateAt: string | null; features: PortalFeatures; deadlines: PortalDeadline[]; documents: PortalDocument[];
  uploads: PortalUpload[]; invoices: PortalInvoice[]; trust: PortalTrust | null; messages: PortalMessage[];
}

const TOKEN_KEY = 'niwe.portal.token';

/**
 * The client portal's own HTTP client. It keeps its own token, apart from the firm's staff login, and sends it itself: the staff interceptor
 * ignores every /api/portal/ request, so a staff session and a client session can never be mixed up.
 */
@Injectable({ providedIn: 'root' })
export class PortalApiService {
  private readonly base = `${API_BASE}/portal`;
  private memoryToken: string | null = null;

  constructor(private http: HttpClient) {}

  // ---- session
  getToken(): string | null {
    if (this.memoryToken) return this.memoryToken;
    try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
  }
  private setToken(t: string | null): void {
    this.memoryToken = t;
    try { if (t) sessionStorage.setItem(TOKEN_KEY, t); else sessionStorage.removeItem(TOKEN_KEY); } catch { /* storage unavailable: the memory copy still works */ }
  }
  signedIn(): boolean { return !!this.getToken(); }
  signOut(): void { this.setToken(null); }

  private auth(): { headers: HttpHeaders } { return { headers: new HttpHeaders({ Authorization: `Bearer ${this.getToken() ?? ''}` }) }; }

  login(email: string, password: string): Observable<PortalSession> {
    return new Observable((o) => this.http.post<PortalSession>(`${this.base}/auth/login`, { email, password }).subscribe({
      next: (s) => { this.setToken(s.token); o.next(s); o.complete(); }, error: (e) => o.error(e) }));
  }
  inviteInfo(token: string): Observable<PortalInviteInfo> { return this.http.get<PortalInviteInfo>(`${this.base}/auth/invite`, { params: { token } }); }
  accept(token: string, password: string): Observable<PortalSession> {
    return new Observable((o) => this.http.post<PortalSession>(`${this.base}/auth/accept`, { token, password }).subscribe({
      next: (s) => { this.setToken(s.token); o.next(s); o.complete(); }, error: (e) => o.error(e) }));
  }
  changePassword(currentPassword: string, newPassword: string): Observable<void> { return this.http.post<void>(`${this.base}/password`, { currentPassword, newPassword }, this.auth()); }

  // ---- data
  me(): Observable<PortalMe> { return this.http.get<PortalMe>(`${this.base}/me`, this.auth()); }
  matter(id: number): Observable<PortalMatter> { return this.http.get<PortalMatter>(`${this.base}/matters/${id}`, this.auth()); }
  download(documentId: number): Observable<Blob> { return this.http.get(`${this.base}/documents/${documentId}/download`, { ...this.auth(), responseType: 'blob' }); }
  upload(matterId: number, form: FormData): Observable<PortalUpload> { return this.http.post<PortalUpload>(`${this.base}/matters/${matterId}/uploads`, form, this.auth()); }
  post(matterId: number, body: string): Observable<PortalMessage[]> { return this.http.post<PortalMessage[]>(`${this.base}/matters/${matterId}/messages`, { body }, this.auth()); }
  markRead(matterId: number): Observable<void> { return this.http.post<void>(`${this.base}/matters/${matterId}/messages/read`, {}, this.auth()); }
}
