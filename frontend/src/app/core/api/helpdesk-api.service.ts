import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import {
  HelpDeskCategory,
  HelpDeskCategoryPayload,
  HelpDeskComment,
  HelpDeskPriority,
  HelpDeskSettings,
  HelpDeskSettingsPayload,
  HelpDeskStatus,
  HelpDeskTicket,
  HelpDeskTicketPayload,
  HelpDeskTicketType,
} from '../models';

/** Admin > Help Desk > Categories — ADMIN-only on the backend, same shape as CatalogApiService's own category calls. */
@Injectable({ providedIn: 'root' })
export class HelpDeskCategoryApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<HelpDeskCategory[]> {
    return this.http.get<HelpDeskCategory[]>(`${API_BASE}/helpdesk/categories`);
  }

  create(payload: HelpDeskCategoryPayload): Observable<HelpDeskCategory> {
    return this.http.post<HelpDeskCategory>(`${API_BASE}/helpdesk/categories`, payload);
  }

  update(id: number, payload: HelpDeskCategoryPayload): Observable<HelpDeskCategory> {
    return this.http.put<HelpDeskCategory>(`${API_BASE}/helpdesk/categories/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/helpdesk/categories/${id}`);
  }
}

export interface HelpDeskTicketFilter {
  type?: HelpDeskTicketType | null;
  status?: HelpDeskStatus | null;
  priority?: HelpDeskPriority | null;
  categoryId?: number | null;
  assignedToUserId?: number | null;
  q?: string | null;
}

/** Admin > Help Desk > Tickets — ADMIN-only on the backend; status moves through named actions (start/resolve/close/reopen), same convention as SubscriptionApiService's pause/resume/cancel. */
@Injectable({ providedIn: 'root' })
export class HelpDeskTicketApiService {
  constructor(private http: HttpClient) {}

  search(filter: HelpDeskTicketFilter = {}): Observable<HelpDeskTicket[]> {
    const params: Record<string, string> = {};
    if (filter.type) params['type'] = filter.type;
    if (filter.status) params['status'] = filter.status;
    if (filter.priority) params['priority'] = filter.priority;
    if (filter.categoryId != null) params['categoryId'] = String(filter.categoryId);
    if (filter.assignedToUserId != null) params['assignedToUserId'] = String(filter.assignedToUserId);
    if (filter.q) params['q'] = filter.q;
    return this.http.get<HelpDeskTicket[]>(`${API_BASE}/helpdesk/tickets`, { params });
  }

  getById(id: number): Observable<HelpDeskTicket> {
    return this.http.get<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}`);
  }

  create(payload: HelpDeskTicketPayload): Observable<HelpDeskTicket> {
    return this.http.post<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets`, payload);
  }

  update(id: number, payload: HelpDeskTicketPayload): Observable<HelpDeskTicket> {
    return this.http.put<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}`, payload);
  }

  assign(id: number, assignedToUserId: number | null): Observable<HelpDeskTicket> {
    return this.http.post<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}/assign`, { assignedToUserId });
  }

  start(id: number): Observable<HelpDeskTicket> {
    return this.http.post<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}/start`, {});
  }

  resolve(id: number, resolutionNotes: string | null): Observable<HelpDeskTicket> {
    return this.http.post<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}/resolve`, { resolutionNotes });
  }

  close(id: number): Observable<HelpDeskTicket> {
    return this.http.post<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}/close`, {});
  }

  reopen(id: number): Observable<HelpDeskTicket> {
    return this.http.post<HelpDeskTicket>(`${API_BASE}/helpdesk/tickets/${id}/reopen`, {});
  }

  listComments(id: number): Observable<HelpDeskComment[]> {
    return this.http.get<HelpDeskComment[]>(`${API_BASE}/helpdesk/tickets/${id}/comments`);
  }

  addComment(id: number, body: string, internal: boolean): Observable<HelpDeskComment> {
    return this.http.post<HelpDeskComment>(`${API_BASE}/helpdesk/tickets/${id}/comments`, { body, internal });
  }

  /** Fourier: "print tick[et] on PDF" — opened in a new tab via openPdfForPrint, same convention as InvoiceApiService.pdf/SalesOrderApiService.pdf. */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE}/helpdesk/tickets/${id}/pdf`, { responseType: 'blob' });
  }
}

/** Admin > Help Desk > Settings — ADMIN-only on the backend; the notification email list that HelpDeskMailService sends new-ticket alerts to. */
@Injectable({ providedIn: 'root' })
export class HelpDeskSettingsApiService {
  constructor(private http: HttpClient) {}

  get(): Observable<HelpDeskSettings> {
    return this.http.get<HelpDeskSettings>(`${API_BASE}/helpdesk/settings`);
  }

  update(payload: HelpDeskSettingsPayload): Observable<HelpDeskSettings> {
    return this.http.put<HelpDeskSettings>(`${API_BASE}/helpdesk/settings`, payload);
  }
}
