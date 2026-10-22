import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type AppointmentStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type PaymentMode = 'NONE' | 'DEPOSIT' | 'FULL';
export type BlockTarget = 'ALL' | 'STAFF' | 'RESOURCE';

export interface Option { id: number; name: string; }

export interface BookingSettings {
  dayStart: string; dayEnd: string; workDays: string; slotStepMinutes: number; minNoticeMinutes: number; maxAdvanceDays: number; holdMinutes: number;
  cancelNoticeHours: number; maxPublicPerPhonePerDay: number; publicEnabled: boolean; emailConfirmations: boolean; publicTitle: string | null;
  publicIntro: string | null; paymentInstructions: string | null;
}

export interface BookableService {
  id: number; name: string; description: string | null; durationMinutes: number; bufferMinutes: number; price: number; paymentMode: PaymentMode;
  depositPercent: number; depositAmount: number; resourceKind: string | null; color: string | null; active: boolean; publicVisible: boolean;
  staff: Option[]; appointments: number;
}

export interface ServicePayload {
  name: string; description?: string | null; durationMinutes: number; bufferMinutes: number; price: number; paymentMode: PaymentMode; depositPercent: number;
  resourceKind?: string | null; color?: string | null; active: boolean; publicVisible: boolean; staffIds: number[];
}

export interface BookableResource { id: number; name: string; kind: string; capacity: number | null; notes: string | null; active: boolean; branchId: number | null; appointments: number; }
export interface ResourcePayload { name: string; kind: string; capacity?: number | null; notes?: string | null; active: boolean; }

export interface BookingBlock { id: number; targetType: BlockTarget; targetId: number | null; targetName: string; startAt: string; endAt: string; reason: string | null; createdByName: string | null; }
export interface BlockPayload { targetType: BlockTarget; targetId: number | null; startAt: string; endAt: string; reason?: string | null; }

export interface Appointment {
  id: number; number: string; confirmationCode: string; status: AppointmentStatus; source: 'STAFF' | 'PUBLIC'; serviceId: number; serviceName: string;
  serviceColor: string | null; startAt: string; endAt: string; durationMinutes: number; staffUserId: number | null; staffName: string | null;
  resourceId: number | null; resourceName: string | null; customerId: number | null; guestName: string; guestPhone: string | null; guestEmail: string | null;
  notes: string | null; price: number; paymentMode: PaymentMode; amountDue: number; amountPaid: number; invoiceId: number | null; invoiceNumber: string | null;
  holdExpiresAt: string | null; cancelReason: string | null; createdAt: string; createdByName: string | null; canWork: boolean; canManage: boolean;
}

export interface Slot { time: string; staff: Option[]; resources: Option[]; }

export interface DayView {
  date: string; appointments: Appointment[]; blocks: BookingBlock[]; staff: Option[]; resources: Option[]; dayStart: string; dayEnd: string; workDay: boolean;
}

export interface BookingSummary { today: number; upcomingWeek: number; awaitingPayment: number; noShowsThisMonth: number; }

export interface BookPayload {
  serviceId: number; start: string; staffUserId?: number | null; resourceId?: number | null; customerId?: number | null; guestName?: string | null;
  guestPhone?: string | null; guestEmail?: string | null; notes?: string | null; skipPayment?: boolean;
}

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private base = `${API_BASE}/booking`;

  constructor(private http: HttpClient) {}

  day(date: string): Observable<DayView> { return this.http.get<DayView>(`${this.base}/day`, { params: new HttpParams().set('date', date) }); }
  list(f: { from?: string; to?: string; staffId?: number | null; resourceId?: number | null; status?: string }): Observable<Appointment[]> {
    let p = new HttpParams();
    if (f.from) p = p.set('from', f.from);
    if (f.to) p = p.set('to', f.to);
    if (f.staffId) p = p.set('staffId', f.staffId);
    if (f.resourceId) p = p.set('resourceId', f.resourceId);
    if (f.status) p = p.set('status', f.status);
    return this.http.get<Appointment[]>(`${this.base}/appointments`, { params: p });
  }
  mine(): Observable<Appointment[]> { return this.http.get<Appointment[]>(`${this.base}/mine`); }
  summary(): Observable<BookingSummary> { return this.http.get<BookingSummary>(`${this.base}/summary`); }
  get(id: number): Observable<Appointment> { return this.http.get<Appointment>(`${this.base}/appointments/${id}`); }
  slots(serviceId: number, date: string, staffId?: number | null, ignoreAppointmentId?: number | null): Observable<Slot[]> {
    let p = new HttpParams().set('serviceId', serviceId).set('date', date);
    if (staffId) p = p.set('staffId', staffId);
    if (ignoreAppointmentId) p = p.set('ignoreAppointmentId', ignoreAppointmentId);
    return this.http.get<Slot[]>(`${this.base}/slots`, { params: p });
  }
  staff(): Observable<Option[]> { return this.http.get<Option[]>(`${this.base}/staff`); }

  book(p: BookPayload): Observable<Appointment> { return this.http.post<Appointment>(`${this.base}/appointments`, p); }
  reschedule(id: number, start: string, staffUserId: number | null, resourceId: number | null): Observable<Appointment> {
    return this.http.put<Appointment>(`${this.base}/appointments/${id}/reschedule`, { start, staffUserId, resourceId });
  }
  saveNotes(id: number, notes: string | null): Observable<Appointment> { return this.http.put<Appointment>(`${this.base}/appointments/${id}/notes`, { notes }); }
  complete(id: number): Observable<Appointment> { return this.http.post<Appointment>(`${this.base}/appointments/${id}/complete`, {}); }
  noShow(id: number): Observable<Appointment> { return this.http.post<Appointment>(`${this.base}/appointments/${id}/no-show`, {}); }
  confirm(id: number): Observable<Appointment> { return this.http.post<Appointment>(`${this.base}/appointments/${id}/confirm`, {}); }
  cancel(id: number, reason: string | null): Observable<Appointment> { return this.http.post<Appointment>(`${this.base}/appointments/${id}/cancel`, { reason }); }
  invoice(id: number): Observable<Appointment> { return this.http.post<Appointment>(`${this.base}/appointments/${id}/invoice`, {}); }

  services(activeOnly = false): Observable<BookableService[]> { return this.http.get<BookableService[]>(`${this.base}/services`, { params: new HttpParams().set('activeOnly', activeOnly) }); }
  service(id: number): Observable<BookableService> { return this.http.get<BookableService>(`${this.base}/services/${id}`); }
  createService(p: ServicePayload): Observable<BookableService> { return this.http.post<BookableService>(`${this.base}/services`, p); }
  updateService(id: number, p: ServicePayload): Observable<BookableService> { return this.http.put<BookableService>(`${this.base}/services/${id}`, p); }
  deleteService(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/services/${id}`); }

  resources(): Observable<BookableResource[]> { return this.http.get<BookableResource[]>(`${this.base}/resources`); }
  createResource(p: ResourcePayload): Observable<BookableResource> { return this.http.post<BookableResource>(`${this.base}/resources`, p); }
  updateResource(id: number, p: ResourcePayload): Observable<BookableResource> { return this.http.put<BookableResource>(`${this.base}/resources/${id}`, p); }
  deleteResource(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/resources/${id}`); }

  blocks(from?: string, to?: string): Observable<BookingBlock[]> {
    let p = new HttpParams();
    if (from) p = p.set('from', from);
    if (to) p = p.set('to', to);
    return this.http.get<BookingBlock[]>(`${this.base}/blocks`, { params: p });
  }
  addBlock(b: BlockPayload): Observable<BookingBlock> { return this.http.post<BookingBlock>(`${this.base}/blocks`, b); }
  deleteBlock(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/blocks/${id}`); }

  settings(): Observable<BookingSettings> { return this.http.get<BookingSettings>(`${this.base}/settings`); }
  updateSettings(s: Partial<BookingSettings>): Observable<BookingSettings> { return this.http.put<BookingSettings>(`${this.base}/settings`, s); }
}
