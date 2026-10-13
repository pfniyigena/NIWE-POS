import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from '../constants';

export type PostingMode = 'NIGHTLY' | 'AT_CHECKOUT' | 'BOOKING_AND_CHECKOUT';
export type BookingStatus = 'BOOKED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'CANCELLED' | 'NO_SHOW';
export type HousekeepingStatus = 'CLEAN' | 'DIRTY' | 'CLEANING' | 'INSPECTED';

export interface HotelSettings {
  enabled: boolean;
  postingMode: PostingMode;
  taxPercent: number;
  checkInTime: string;
  checkOutTime: string;
  auditTime: string;
  auditLastRun: string | null;
  auditLastSummary: string | null;
}

export interface RoomType {
  id: number;
  name: string;
  description: string | null;
  baseRate: number;
  baseOccupancy: number;
  maxOccupancy: number;
  extraPersonRate: number;
  amenities: string | null;
  active: boolean;
  roomCount: number;
}

export interface Room {
  id: number;
  number: string;
  floor: string | null;
  roomTypeId: number;
  roomTypeName: string;
  rate: number;
  rateOverride: number | null;
  housekeepingStatus: HousekeepingStatus;
  housekeeper: string | null;
  housekeepingNote: string | null;
  outOfOrder: boolean;
  active: boolean;
  notes: string | null;
  occupied: boolean;
  occupantBookingId: number | null;
  occupantGuest: string | null;
  occupantCheckOut: string | null;
}

export interface Guest {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  nationality: string | null;
  idType: string | null;
  idNumber: string | null;
  address: string | null;
  notes: string | null;
  stays: number;
}

export interface GuestPayload {
  fullName: string;
  phone: string | null;
  email: string | null;
  nationality: string | null;
  idType: string | null;
  idNumber: string | null;
  address: string | null;
  notes: string | null;
}

export interface Booking {
  id: number;
  bookingNumber: string;
  guestId: number;
  guestName: string;
  guestPhone: string | null;
  roomTypeId: number;
  roomTypeName: string;
  roomId: number | null;
  roomNumber: string | null;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  actualCheckIn: string | null;
  actualCheckOut: string | null;
  adults: number;
  children: number;
  nightlyRate: number;
  extraPersonRate: number;
  nightlyTotal: number;
  status: BookingStatus;
  source: string | null;
  notes: string | null;
  cancelReason: string | null;
  chargesTotal: number;
  paidTotal: number;
  balance: number;
}

export interface BookingPayload {
  guestId: number | null;
  guest: GuestPayload | null;
  roomTypeId: number;
  roomId: number | null;
  checkInDate: string;
  checkOutDate: string;
  adults: number;
  children: number;
  nightlyRate: number | null;
  source: string | null;
  notes: string | null;
  depositAmount: number | null;
  depositMethod: string | null;
}

export interface FolioCharge {
  id: number;
  chargeType: 'ROOM' | 'RESTAURANT' | 'POS' | 'ROOM_SERVICE' | 'EXTRA';
  description: string;
  nightDate: string | null;
  net: number;
  tax: number;
  amount: number;
  sourceType: string | null;
  sourceId: number | null;
  voided: boolean;
  voidReason: string | null;
  createdAt: string;
}

export interface FolioPayment {
  id: number;
  kind: 'DEPOSIT' | 'PAYMENT' | 'REFUND';
  method: string;
  amount: number;
  reference: string | null;
  note: string | null;
  createdAt: string;
}

export interface Folio {
  booking: Booking;
  charges: FolioCharge[];
  payments: FolioPayment[];
  chargesTotal: number;
  taxTotal: number;
  deposits: number;
  paymentsTotal: number;
  refunds: number;
  balance: number;
}

export interface InHouseGuest {
  bookingId: number;
  bookingNumber: string;
  roomNumber: string;
  guestName: string;
}

export interface DayAvailability {
  date: string;
  total: number;
  booked: number;
  available: number;
}

export interface TypeAvailability {
  roomTypeId: number;
  roomTypeName: string;
  totalRooms: number;
  days: DayAvailability[];
}

export interface Availability {
  from: string;
  to: string;
  types: TypeAvailability[];
}

export interface GridBooking {
  bookingId: number;
  bookingNumber: string;
  guestName: string;
  checkInDate: string;
  checkOutDate: string;
  status: BookingStatus;
}

export interface GridRoom {
  roomId: number | null;
  roomNumber: string;
  roomTypeName: string;
  outOfOrder: boolean;
  bookings: GridBooking[];
}

export interface RoomGrid {
  from: string;
  to: string;
  rooms: GridRoom[];
}

export interface Maintenance {
  id: number;
  roomId: number;
  roomNumber: string;
  title: string;
  description: string | null;
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE';
  blocksRoom: boolean;
  reportedBy: string | null;
  createdAt: string;
  resolvedAt: string | null;
  resolutionNote: string | null;
}

export interface HotelDashboard {
  totalRooms: number;
  occupied: number;
  availableToday: number;
  outOfOrder: number;
  arrivalsToday: number;
  departuresToday: number;
  dirtyRooms: number;
  occupancyPercent: number;
}

export interface OccupancyReport {
  from: string;
  to: string;
  roomCount: number;
  roomNightsAvailable: number;
  roomNightsSold: number;
  occupancyPercent: number;
  roomRevenue: number;
  adr: number;
  revPar: number;
  byType: { roomTypeName: string; roomNights: number; revenue: number }[];
}

export interface AuditResult {
  staysChecked: number;
  nightsCharged: number;
  message: string;
}

export interface PaymentLine {
  method: string;
  amount: number;
  reference?: string | null;
}

/** Everything under /api/hotel. The server enforces who may do what; the screens only hide what a role cannot use. */
@Injectable({ providedIn: 'root' })
export class HotelApiService {
  /** Whether the Hotel module is switched on — drives the sidebar group. Loaded once, refreshed when settings are saved. */
  readonly enabled = signal(false);
  private loaded = false;

  private base = `${API_BASE}/hotel`;

  constructor(private http: HttpClient) {}

  /** Loads the on/off flag once for the sidebar; safe to call from every page. */
  ensureStateLoaded(): void {
    if (this.loaded) return;
    this.loaded = true;
    this.getSettings().subscribe({ next: () => {}, error: () => (this.loaded = false) });
  }

  // settings
  getSettings(): Observable<HotelSettings> {
    return this.http.get<HotelSettings>(`${this.base}/settings`).pipe(tap((s) => this.enabled.set(s.enabled)));
  }
  saveSettings(payload: Partial<Omit<HotelSettings, 'auditLastRun' | 'auditLastSummary'>>): Observable<HotelSettings> {
    return this.http.put<HotelSettings>(`${this.base}/settings`, payload).pipe(tap((s) => this.enabled.set(s.enabled)));
  }
  runNightAudit(): Observable<AuditResult> {
    return this.http.post<AuditResult>(`${this.base}/night-audit/run`, {});
  }
  inHouseGuests(): Observable<InHouseGuest[]> {
    return this.http.get<InHouseGuest[]>(`${this.base}/in-house`);
  }

  // dashboard + reports
  dashboard(): Observable<HotelDashboard> {
    return this.http.get<HotelDashboard>(`${this.base}/dashboard`);
  }
  occupancy(from: string, to: string): Observable<OccupancyReport> {
    return this.http.get<OccupancyReport>(`${this.base}/reports/occupancy`, { params: new HttpParams().set('from', from).set('to', to) });
  }

  // room types + rooms
  roomTypes(): Observable<RoomType[]> {
    return this.http.get<RoomType[]>(`${this.base}/room-types`);
  }
  saveRoomType(id: number | null, payload: Partial<RoomType>): Observable<RoomType> {
    return id ? this.http.put<RoomType>(`${this.base}/room-types/${id}`, payload) : this.http.post<RoomType>(`${this.base}/room-types`, payload);
  }
  deleteRoomType(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/room-types/${id}`);
  }
  rooms(): Observable<Room[]> {
    return this.http.get<Room[]>(`${this.base}/rooms`);
  }
  saveRoom(id: number | null, payload: { number: string; floor: string | null; roomTypeId: number | null; rateOverride: number | null; notes: string | null; active: boolean }): Observable<Room> {
    return id ? this.http.put<Room>(`${this.base}/rooms/${id}`, payload) : this.http.post<Room>(`${this.base}/rooms`, payload);
  }
  deleteRoom(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/rooms/${id}`);
  }
  setHousekeeping(id: number, status: HousekeepingStatus, housekeeper?: string | null, note?: string | null): Observable<Room> {
    return this.http.put<Room>(`${this.base}/rooms/${id}/housekeeping`, { status, housekeeper: housekeeper ?? null, note: note ?? null });
  }

  // guests
  guests(q?: string): Observable<Guest[]> {
    return this.http.get<Guest[]>(`${this.base}/guests`, { params: q ? new HttpParams().set('q', q) : undefined });
  }
  saveGuest(id: number | null, payload: GuestPayload): Observable<Guest> {
    return id ? this.http.put<Guest>(`${this.base}/guests/${id}`, payload) : this.http.post<Guest>(`${this.base}/guests`, payload);
  }
  deleteGuest(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/guests/${id}`);
  }

  // bookings + availability
  bookings(status?: string, q?: string): Observable<Booking[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (q) params = params.set('q', q);
    return this.http.get<Booking[]>(`${this.base}/bookings`, { params });
  }
  booking(id: number): Observable<Booking> {
    return this.http.get<Booking>(`${this.base}/bookings/${id}`);
  }
  createBooking(payload: BookingPayload): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings`, payload);
  }
  updateBooking(id: number, payload: Partial<{ roomTypeId: number; roomId: number | null; checkInDate: string; checkOutDate: string; adults: number; children: number; nightlyRate: number; source: string | null; notes: string | null }>): Observable<Booking> {
    return this.http.put<Booking>(`${this.base}/bookings/${id}`, payload);
  }
  extend(id: number, checkOutDate: string): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/extend`, { checkOutDate });
  }
  cancel(id: number, reason: string | null, depositAction: 'REFUND' | 'FORFEIT', refundMethod: string | null): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/cancel`, { reason, depositAction, refundMethod });
  }
  noShow(id: number, reason: string | null, depositAction: 'REFUND' | 'FORFEIT', refundMethod: string | null): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/no-show`, { reason, depositAction, refundMethod });
  }
  availability(from: string, to: string): Observable<Availability> {
    return this.http.get<Availability>(`${this.base}/availability`, { params: new HttpParams().set('from', from).set('to', to) });
  }
  freeRooms(roomTypeId: number | null, from: string, to: string, excludeBookingId?: number | null): Observable<Room[]> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (roomTypeId) params = params.set('roomTypeId', roomTypeId);
    if (excludeBookingId) params = params.set('excludeBookingId', excludeBookingId);
    return this.http.get<Room[]>(`${this.base}/free-rooms`, { params });
  }
  grid(from: string, to: string): Observable<RoomGrid> {
    return this.http.get<RoomGrid>(`${this.base}/grid`, { params: new HttpParams().set('from', from).set('to', to) });
  }

  // front desk
  arrivals(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.base}/front-desk/arrivals`);
  }
  departures(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.base}/front-desk/departures`);
  }
  inHouse(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.base}/front-desk/in-house`);
  }
  checkIn(id: number, roomId: number | null, allowNotReady: boolean, adults?: number, children?: number): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/check-in`, { roomId, allowNotReady, adults: adults ?? null, children: children ?? null });
  }
  walkIn(payload: { guestId: number | null; guest: GuestPayload | null; roomId: number; checkOutDate: string; adults: number; children: number; nightlyRate: number | null; notes: string | null; depositAmount: number | null; depositMethod: string | null }): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/walk-in`, payload);
  }
  checkOut(id: number, payments: PaymentLine[], refundMethod: string | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/check-out`, { payments, refundMethod });
  }
  moveRoom(id: number, roomId: number, reason: string | null): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/move`, { roomId, reason });
  }

  // folio
  folio(id: number): Observable<Folio> {
    return this.http.get<Folio>(`${this.base}/bookings/${id}/folio`);
  }
  addCharge(id: number, description: string, amount: number, taxable: boolean): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/charges`, { description, amount, taxable });
  }
  voidCharge(id: number, chargeId: number, reason: string | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/charges/${chargeId}/void`, { reason });
  }
  addPayment(id: number, kind: 'DEPOSIT' | 'PAYMENT', method: string, amount: number, reference: string | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/payments`, { kind, method, amount, reference, note: null });
  }

  // maintenance
  maintenance(): Observable<Maintenance[]> {
    return this.http.get<Maintenance[]>(`${this.base}/maintenance`);
  }
  createMaintenance(payload: { roomId: number; title: string; description: string | null; priority: string; blocksRoom: boolean; reportedBy: string | null }): Observable<Maintenance> {
    return this.http.post<Maintenance>(`${this.base}/maintenance`, payload);
  }
  updateMaintenance(id: number, payload: Partial<{ status: string; priority: string; blocksRoom: boolean; resolutionNote: string | null }>): Observable<Maintenance> {
    return this.http.put<Maintenance>(`${this.base}/maintenance/${id}`, payload);
  }
  deleteMaintenance(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/maintenance/${id}`);
  }
}
