import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

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
  cancelFreeDays: number;
  cancelFeeNights: number;
  noShowFeeNights: number;
  earlyDepartureFeeNights: number;
  lateCheckoutFrom: string | null;
  lateCheckoutPercent: number;
  taxOnFees: boolean;
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
  dnd: boolean;
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
  vip: boolean;
  blacklisted: boolean;
  blacklistReason: string | null;
  preferences: string | null;
  dateOfBirth: string | null;
  profession: string | null;
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
  vip?: boolean;
  blacklisted?: boolean;
  blacklistReason?: string | null;
  preferences?: string | null;
  dateOfBirth?: string | null;
  profession?: string | null;
}

export interface GuestStats {
  stays: number;
  nights: number;
  lifetimeSpend: number;
  averageSpend: number;
  averageNightly: number;
  firstStay: string | null;
  lastStay: string | null;
  upcoming: number;
  inHouse: number;
  cancelled: number;
  noShows: number;
  favouriteRoomType: string | null;
  favouriteSource: string | null;
}

export interface GuestStay {
  bookingId: number;
  bookingNumber: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  status: string;
  roomNumber: string | null;
  roomTypeName: string;
  source: string | null;
  total: number;
}

export interface GuestDocument {
  id: number;
  guestId: number;
  docType: 'PASSPORT' | 'NATIONAL_ID' | 'DRIVING_LICENCE' | 'VISA' | 'OTHER';
  name: string;
  docNumber: string | null;
  expiryDate: string | null;
  expiryState: 'NONE' | 'OK' | 'SOON' | 'EXPIRED';
  fileName: string;
  contentType: string | null;
  sizeBytes: number;
  uploadedBy: string | null;
  createdAt: string;
}

export interface GuestProfile {
  guest: Guest;
  stats: GuestStats;
  stays: GuestStay[];
  documents: GuestDocument[];
}

export interface DuplicateGuest {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
  idNumber: string | null;
  stays: number;
  lifetimeSpend: number;
  vip: boolean;
  blacklisted: boolean;
}

export interface DuplicateCluster {
  matchedOn: string[];
  guests: DuplicateGuest[];
}

export interface MergeResult {
  guest: Guest;
  bookingsMoved: number;
  documentsMoved: number;
  groupsMoved: number;
  lostFoundMoved: number;
}

export interface RegisterRow {
  bookingId: number;
  bookingNumber: string;
  guestId: number;
  fullName: string;
  nationality: string | null;
  idType: string | null;
  idNumber: string | null;
  dateOfBirth: string | null;
  address: string | null;
  profession: string | null;
  phone: string | null;
  arrival: string;
  departure: string;
  roomNumber: string | null;
  adults: number;
  children: number;
  status: string;
}

export interface Booking {
  id: number;
  bookingNumber: string;
  guestId: number;
  guestName: string;
  guestPhone: string | null;
  guestVip?: boolean;
  guestBlacklisted?: boolean;
  guestPreferences?: string | null;
  confirmationCode?: string | null;
  online?: boolean;
  depositDue?: number | null;
  holdExpiresAt?: string | null;
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
  ratePlanId: number | null;
  ratePlanName: string | null;
  rateMode: 'AUTO' | 'FIXED';
  groupId: number | null;
  groupCode: string | null;
  groupName: string | null;
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
  ratePlanId?: number | null;
}

export interface FolioCharge {
  id: number;
  chargeType: 'ROOM' | 'RESTAURANT' | 'POS' | 'ROOM_SERVICE' | 'EXTRA' | 'FEE';
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
  billNo: number;
}

export interface FolioPayment {
  id: number;
  kind: 'DEPOSIT' | 'PAYMENT' | 'REFUND';
  method: string;
  amount: number;
  reference: string | null;
  note: string | null;
  createdAt: string;
  billNo: number;
}

export interface Bill {
  billNo: number;
  label: string | null;
  companyId: number | null;
  companyName: string | null;
  roomNights: boolean;
  charges: number;
  paid: number;
  balance: number;
  companyBillId: number | null;
  companyBillNumber: string | null;
  companyBillStatus: string | null;
  toGroup: boolean;
}

export interface FeeQuote {
  kind: 'CANCELLATION' | 'NO_SHOW' | 'EARLY_DEPARTURE' | 'LATE_CHECKOUT';
  description: string;
  nights: number;
  net: number;
  tax: number;
  gross: number;
}

export interface CancelPreview {
  fee: FeeQuote | null;
  depositHeld: number;
  feeFromDeposit: number;
  depositLeft: number;
  stillOwed: number;
}

export interface CheckoutPreview {
  fees: FeeQuote[];
  guestDue: number;
  companyDue: number;
  toPay: number;
}

export interface CompanyReceipt {
  id: number;
  method: string;
  amount: number;
  reference: string | null;
  receivedOn: string;
}

export interface CompanyBill {
  id: number;
  billNumber: string;
  companyId: number;
  companyName: string;
  bookingId: number;
  bookingNumber: string;
  guestName: string;
  billNo: number;
  description: string;
  amount: number;
  paid: number;
  outstanding: number;
  status: 'OPEN' | 'PAID';
  issuedOn: string;
  dueDate: string | null;
  overdue: boolean;
  receipts: CompanyReceipt[];
}

export interface CompanyOption {
  id: number;
  name: string;
  taxId: string | null;
  creditLimit: number;
  owing: number;
  termDays: number | null;
}

export interface CompanySummary {
  companyId: number;
  companyName: string;
  billed: number;
  paid: number;
  outstanding: number;
  overdue: number;
  openBills: number;
}

export type GroupStatus = 'TENTATIVE' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
export type GroupBilling = 'INDIVIDUAL' | 'MASTER_ROOMS' | 'MASTER_ALL';

export interface GroupSummary {
  id: number;
  groupCode: string;
  name: string;
  status: GroupStatus;
  billing: GroupBilling;
  arrivalDate: string;
  departureDate: string;
  companyName: string | null;
  contactName: string | null;
  roomsBlocked: number;
  roomsBooked: number;
  checkedIn: number;
  owed: number;
  held: number;
}

export interface GroupBlock {
  id: number;
  roomTypeId: number;
  roomTypeName: string;
  rooms: number;
  rate: number | null;
  startDate: string;
  endDate: string;
  releaseDate: string | null;
  released: boolean;
  holding: boolean;
  booked: number;
  unbooked: number;
}

export interface GroupRoom {
  bookingId: number;
  bookingNumber: string;
  guestId: number;
  guestName: string;
  guestPhone: string | null;
  unnamed: boolean;
  roomTypeId: number;
  roomTypeName: string;
  roomId: number | null;
  roomNumber: string | null;
  checkInDate: string;
  checkOutDate: string;
  adults: number;
  children: number;
  status: BookingStatus;
  charges: number;
  paid: number;
  billTo: string;
}

export interface GroupEntry {
  id: number;
  kind: 'BILL' | 'DEPOSIT' | 'APPLIED' | 'PAYMENT' | 'REFUND';
  bookingId: number | null;
  bookingNumber: string | null;
  billNo: number | null;
  description: string | null;
  amount: number;
  method: string | null;
  reference: string | null;
  entryDate: string;
}

export interface GroupTotals {
  billed: number;
  deposits: number;
  applied: number;
  paid: number;
  refunded: number;
  owed: number;
  held: number;
}

export interface GroupDetail {
  id: number;
  groupCode: string;
  name: string;
  contactName: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  companyId: number | null;
  companyName: string | null;
  arrivalDate: string;
  departureDate: string;
  status: GroupStatus;
  billing: GroupBilling;
  notes: string | null;
  roomsBlocked: number;
  roomsBooked: number;
  checkedIn: number;
  blocks: GroupBlock[];
  rooming: GroupRoom[];
  entries: GroupEntry[];
  totals: GroupTotals;
}

export interface GroupPayload {
  name: string;
  contactName: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  companyId: number | null;
  arrivalDate: string;
  departureDate: string;
  billing: GroupBilling;
  notes: string | null;
}

export interface BlockPayload {
  roomTypeId: number;
  rooms: number;
  rate: number | null;
  startDate: string | null;
  endDate: string | null;
  releaseDate: string | null;
}

export interface AddRoomsPayload {
  roomTypeId: number;
  count: number | null;
  names: string[] | null;
  adults: number | null;
  children: number | null;
  checkInDate: string | null;
  checkOutDate: string | null;
  nightlyRate: number | null;
}

export interface GroupMoneyPayload {
  method: string;
  amount: number;
  reference: string | null;
  note: string | null;
}

export interface HkStaff {
  id: number;
  name: string;
  active: boolean;
  tasks: number;
}

export interface ChecklistItem {
  id: number | null;
  label: string;
  sortOrder: number;
  active: boolean;
}

export interface HkCheck {
  label: string;
  passed: boolean;
}

export interface HkTask {
  id: number;
  roomId: number;
  roomNumber: string;
  floor: string | null;
  roomTypeName: string;
  roomStatus: HousekeepingStatus;
  kind: 'DEPARTURE' | 'STAYOVER';
  status: 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED';
  staffId: number | null;
  staffName: string | null;
  dnd: boolean;
  occupied: boolean;
  guestName: string | null;
  departs: string | null;
  arrivalToday: boolean;
  notes: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  minutes: number | null;
  inspectionResult: 'PASS' | 'FAIL' | null;
  inspectionNote: string | null;
  attempts: number;
  checks: HkCheck[];
}

export interface HkBoard {
  date: string;
  tasks: HkTask[];
  summary: { total: number; open: number; inProgress: number; done: number; skipped: number; departures: number; stayovers: number; inspected: number; failed: number };
  load: { staffId: number; staffName: string; tasks: number; done: number; credits: number }[];
}

export interface Productivity {
  staffId: number;
  staffName: string;
  tasks: number;
  departures: number;
  stayovers: number;
  avgDepartureMinutes: number | null;
  avgStayoverMinutes: number | null;
  inspections: number;
  failedInspections: number;
}

export interface LostFound {
  id: number;
  itemNo: string;
  description: string;
  foundOn: string;
  roomId: number | null;
  roomNumber: string | null;
  location: string | null;
  foundBy: string | null;
  status: 'FOUND' | 'CLAIMED' | 'DISPOSED';
  guestId: number | null;
  guestName: string | null;
  guestPhone: string | null;
  claimedBy: string | null;
  claimedOn: string | null;
  notes: string | null;
}

export interface LostFoundPayload {
  description: string;
  foundOn: string | null;
  roomId: number | null;
  location: string | null;
  foundBy: string | null;
  guestId: number | null;
  notes: string | null;
}

export interface MinibarItem {
  id: number;
  name: string;
  price: number;
  taxable: boolean;
  active: boolean;
}

export interface LinenEntry {
  id: number;
  logDate: string;
  item: string;
  sent: number;
  returned: number;
  damaged: number;
  notes: string | null;
}

export interface LinenBalance {
  item: string;
  sent: number;
  returned: number;
  damaged: number;
  atLaundry: number;
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
  bills: Bill[];
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
  held: number;
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
  blockFrom: string | null;
  blockUntil: string | null;
  conflicts: string[];
}

export interface RatePlan {
  id: number;
  name: string;
  description: string | null;
  adjustPercent: number;
  adjustAmount: number;
  minNights: number;
  active: boolean;
  bookings: number;
}

export type RateRuleMode = 'FIXED' | 'PERCENT' | 'AMOUNT';

export interface RateRule {
  id: number;
  name: string;
  roomTypeId: number | null;
  roomTypeName: string | null;
  startDate: string;
  endDate: string | null;
  daysOfWeek: string | null;
  mode: RateRuleMode;
  value: number;
  minNights: number;
  priority: number;
  active: boolean;
}

export interface RateRulePayload {
  name: string;
  roomTypeId: number | null;
  startDate: string;
  endDate: string | null;
  daysOfWeek: string | null;
  mode: RateRuleMode;
  value: number;
  minNights: number;
  priority: number;
  active: boolean;
}

export interface QuoteNight {
  date: string;
  rate: number;
  extra: number;
  net: number;
  tax: number;
  gross: number;
  ruleName: string | null;
}

export interface Quote {
  roomTypeId: number;
  roomTypeName: string;
  from: string;
  to: string;
  nights: number;
  ratePlanId: number | null;
  ratePlanName: string | null;
  items: QuoteNight[];
  net: number;
  tax: number;
  gross: number;
  averageRate: number;
}

export interface RateCalendarDay {
  date: string;
  rate: number;
  ruleName: string | null;
  available: number;
}

export interface RateCalendar {
  roomTypeId: number;
  roomTypeName: string;
  baseRate: number;
  ratePlanId: number | null;
  ratePlanName: string | null;
  days: RateCalendarDay[];
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

export interface RevenueLine { type: string; lines: number; net: number; tax: number; gross: number }
export interface MoneyLine { method: string; deposits: number; payments: number; refunds: number; net: number }
export interface ReportPeriod { label: string; from: string; to: string; roomNightsSold: number; roomNightsAvailable: number; occupancyPercent: number; roomRevenue: number; adr: number; revPar: number }
export interface DailyReport {
  date: string; today: boolean; totalRooms: number; outOfOrder: number; sellable: number; roomsOccupied: number; occupancyPercent: number;
  roomRevenue: number; expectedRoomRevenue: number | null; adr: number; revPar: number;
  arrivals: number; departures: number; stayOvers: number; arrivalsTomorrow: number; departuresTomorrow: number; inHouseNow: number; inHouseBalance: number;
  revenue: RevenueLine[]; revenueTotal: number; money: MoneyLine[]; moneyTotal: number; monthToDate: ReportPeriod; yearToDate: ReportPeriod;
}
export interface SourceRow {
  source: string; bookings: number; stays: number; cancelled: number; noShows: number; roomNights: number; roomRevenue: number; otherRevenue: number;
  adr: number; averageStay: number; averageLeadDays: number; cancelPercent: number;
}
export interface SourceReport { from: string; to: string; rows: SourceRow[]; total: SourceRow }
export interface ForecastDay { date: string; weekday: string; capacity: number; booked: number; held: number; occupancyPercent: number; revenue: number; arrivals: number; departures: number; pickup: number; pickupRevenue: number }
export interface ForecastTotals { roomNightsBooked: number; capacityNights: number; occupancyPercent: number; revenue: number; adr: number; pickup: number; pickupRevenue: number }
export interface ForecastReport { from: string; to: string; pickupDays: number; days: ForecastDay[]; totals: ForecastTotals }
export interface AuditCheck { code: string; label: string; hotel: number; accounting: number; difference: number; ok: boolean; note: string }
export interface AccountMovement { account: string; debit: number; credit: number }
export interface NightAuditReport {
  date: string; lastAuditRun: string | null; lastAuditSummary: string | null; postingMode: string; auditRanForDate: boolean;
  inHouseStays: number; arrivals: number; departures: number; roomNightsCharged: number; roomRevenue: number;
  charged: RevenueLine[]; chargedTotal: number; money: MoneyLine[]; moneyTotal: number; postings: AccountMovement[]; checks: AuditCheck[]; balanced: boolean;
}

export interface OnlineSettings {
  enabled: boolean; intro: string | null; depositPercent: number; holdHours: number; maxNights: number; maxDaysAhead: number; maxPerPhonePerDay: number;
  paymentInstructions: string | null; mailConfirmation: boolean; mailPrearrival: boolean; mailPrearrivalDays: number; mailThanks: boolean;
  thanksMessage: string | null; reviewUrl: string | null; mailReady: boolean;
}
export type OnlineSettingsPayload = Partial<Omit<OnlineSettings, 'mailReady'>>;
export interface OnlineRoomType { id: number; name: string; description: string | null; active: boolean; onlineVisible: boolean; icalToken: string | null; icalPath: string | null }

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
  onlineSettings(): Observable<OnlineSettings> {
    return this.http.get<OnlineSettings>(`${this.base}/online/settings`);
  }
  saveOnlineSettings(payload: OnlineSettingsPayload): Observable<OnlineSettings> {
    return this.http.put<OnlineSettings>(`${this.base}/online/settings`, payload);
  }
  onlineRoomTypes(): Observable<OnlineRoomType[]> {
    return this.http.get<OnlineRoomType[]>(`${this.base}/online/room-types`);
  }
  updateOnlineRoomType(id: number, payload: { onlineVisible?: boolean; description?: string }): Observable<OnlineRoomType> {
    return this.http.put<OnlineRoomType>(`${this.base}/online/room-types/${id}`, payload);
  }
  newFeed(id: number): Observable<OnlineRoomType> {
    return this.http.post<OnlineRoomType>(`${this.base}/online/room-types/${id}/feed`, {});
  }
  removeFeed(id: number): Observable<OnlineRoomType> {
    return this.http.delete<OnlineRoomType>(`${this.base}/online/room-types/${id}/feed`);
  }
  sendConfirmationMail(bookingId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/bookings/${bookingId}/send-confirmation`, {});
  }
  sendDueMails(): Observable<{ sent: number }> {
    return this.http.post<{ sent: number }>(`${this.base}/online/send-due`, {});
  }
  releaseUnpaid(): Observable<{ released: number }> {
    return this.http.post<{ released: number }>(`${this.base}/online/release-unpaid`, {});
  }
  dailyReport(date?: string | null): Observable<DailyReport> {
    return this.http.get<DailyReport>(`${this.base}/reports/daily`, { params: date ? new HttpParams().set('date', date) : new HttpParams() });
  }
  sourceReport(from: string, to: string): Observable<SourceReport> {
    return this.http.get<SourceReport>(`${this.base}/reports/sources`, { params: new HttpParams().set('from', from).set('to', to) });
  }
  forecastReport(from: string, days: number, pickupDays: number): Observable<ForecastReport> {
    return this.http.get<ForecastReport>(`${this.base}/reports/forecast`, { params: new HttpParams().set('from', from).set('days', String(days)).set('pickupDays', String(pickupDays)) });
  }
  nightAuditReport(date?: string | null): Observable<NightAuditReport> {
    return this.http.get<NightAuditReport>(`${this.base}/reports/night-audit`, { params: date ? new HttpParams().set('date', date) : new HttpParams() });
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
  guestProfile(id: number): Observable<GuestProfile> {
    return this.http.get<GuestProfile>(`${this.base}/guests/${id}/profile`);
  }
  guestDuplicates(): Observable<DuplicateCluster[]> {
    return this.http.get<DuplicateCluster[]>(`${this.base}/guests/duplicates`);
  }
  mergeGuests(keeperId: number, duplicateId: number): Observable<MergeResult> {
    return this.http.post<MergeResult>(`${this.base}/guests/${keeperId}/merge`, { duplicateId });
  }
  uploadGuestDocument(guestId: number, file: File, docType: string, name: string, docNumber: string, expiryDate: string): Observable<GuestDocument> {
    const form = new FormData();
    form.append('file', file);
    form.append('docType', docType);
    if (name) form.append('name', name);
    if (docNumber) form.append('docNumber', docNumber);
    if (expiryDate) form.append('expiryDate', expiryDate);
    return this.http.post<GuestDocument>(`${this.base}/guests/${guestId}/documents`, form);
  }
  guestDocumentFile(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/guest-documents/${id}/file`, { responseType: 'blob' });
  }
  deleteGuestDocument(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/guest-documents/${id}`);
  }
  registrationCardPdf(bookingId: number): Observable<Blob> {
    return this.http.get(`${this.base}/bookings/${bookingId}/registration-card/pdf`, { responseType: 'blob' });
  }
  confirmationPdf(bookingId: number): Observable<Blob> {
    return this.http.get(`${this.base}/bookings/${bookingId}/confirmation/pdf`, { responseType: 'blob' });
  }

  /** One page of a hotel list (bookings, guests, rooms, maintenance) plus the number that matched in all. */
  page<T>(path: 'bookings' | 'guests' | 'rooms' | 'maintenance' | 'company-bills' | 'groups' | 'lost-found' | 'guest-register', f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): Observable<PageResult<T>> {
    return pagedList<T>(this.http, `${this.base}/${path}`, f, page, size, extra);
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
  updateBooking(id: number, payload: Partial<{ roomTypeId: number; roomId: number | null; checkInDate: string; checkOutDate: string; adults: number; children: number; nightlyRate: number; source: string | null; notes: string | null; ratePlanId: number | null; autoRate: boolean }>): Observable<Booking> {
    return this.http.put<Booking>(`${this.base}/bookings/${id}`, payload);
  }
  extend(id: number, checkOutDate: string): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/extend`, { checkOutDate });
  }
  cancel(id: number, reason: string | null, depositAction: 'REFUND' | 'FORFEIT', refundMethod: string | null, waiveFee = false): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/cancel`, { reason, depositAction, refundMethod, waiveFee });
  }
  noShow(id: number, reason: string | null, depositAction: 'REFUND' | 'FORFEIT', refundMethod: string | null, waiveFee = false): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/no-show`, { reason, depositAction, refundMethod, waiveFee });
  }
  cancelPreview(id: number, noShow: boolean): Observable<CancelPreview> {
    return this.http.get<CancelPreview>(`${this.base}/bookings/${id}/cancel-preview`, { params: new HttpParams().set('noShow', noShow) });
  }
  checkoutPreview(id: number): Observable<CheckoutPreview> {
    return this.http.get<CheckoutPreview>(`${this.base}/bookings/${id}/checkout-preview`);
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
  walkIn(payload: { guestId: number | null; guest: GuestPayload | null; roomId: number; checkOutDate: string; adults: number; children: number; nightlyRate: number | null; notes: string | null; depositAmount: number | null; depositMethod: string | null; ratePlanId?: number | null }): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/walk-in`, payload);
  }
  checkOut(id: number, payments: PaymentLine[], refundMethod: string | null, waiveFees = false): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/check-out`, { payments, refundMethod, waiveFees });
  }
  moveRoom(id: number, roomId: number, reason: string | null): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings/${id}/move`, { roomId, reason });
  }

  // folio
  folio(id: number): Observable<Folio> {
    return this.http.get<Folio>(`${this.base}/bookings/${id}/folio`);
  }
  addCharge(id: number, description: string, amount: number, taxable: boolean, billNo?: number | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/charges`, { description, amount, taxable, billNo: billNo ?? null });
  }
  voidCharge(id: number, chargeId: number, reason: string | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/charges/${chargeId}/void`, { reason });
  }
  addPayment(id: number, kind: 'DEPOSIT' | 'PAYMENT', method: string, amount: number, reference: string | null, billNo?: number | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/payments`, { kind, method, amount, reference, note: null, billNo: billNo ?? null });
  }

  // bills of a stay
  addBill(id: number, label: string | null, companyId: number | null, toGroup = false): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/bills`, { label, companyId, toGroup });
  }
  updateBill(id: number, billNo: number, payload: { label?: string | null; companyId?: number | null; clearCompany?: boolean; toGroup?: boolean }): Observable<Folio> {
    return this.http.put<Folio>(`${this.base}/bookings/${id}/bills/${billNo}`, payload);
  }
  deleteBill(id: number, billNo: number): Observable<Folio> {
    return this.http.delete<Folio>(`${this.base}/bookings/${id}/bills/${billNo}`);
  }
  setRoomBill(id: number, billNo: number): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/bills/${billNo}/room-nights`, {});
  }
  assignCharge(id: number, chargeId: number, billNo: number): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/charges/${chargeId}/assign`, { billNo });
  }
  moveCharge(id: number, chargeId: number, targetBookingId: number, reason: string | null): Observable<Folio> {
    return this.http.post<Folio>(`${this.base}/bookings/${id}/charges/${chargeId}/move`, { targetBookingId, reason });
  }
  billPdf(id: number, billNo: number): Observable<Blob> {
    return this.http.get(`${this.base}/bookings/${id}/bill/pdf`, { params: new HttpParams().set('bill', billNo), responseType: 'blob' });
  }
  receiptPdf(id: number, paymentId: number): Observable<Blob> {
    return this.http.get(`${this.base}/bookings/${id}/payments/${paymentId}/receipt/pdf`, { responseType: 'blob' });
  }
  companyInvoicePdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/company-bills/${id}/invoice/pdf`, { responseType: 'blob' });
  }

  // company accounts (city ledger)
  companyBill(id: number): Observable<CompanyBill> {
    return this.http.get<CompanyBill>(`${this.base}/company-bills/${id}`);
  }
  companies(): Observable<CompanyOption[]> {
    return this.http.get<CompanyOption[]>(`${this.base}/companies`);
  }
  // groups
  group(id: number): Observable<GroupDetail> {
    return this.http.get<GroupDetail>(`${this.base}/groups/${id}`);
  }
  createGroup(payload: GroupPayload): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups`, payload);
  }
  updateGroup(id: number, payload: GroupPayload): Observable<GroupDetail> {
    return this.http.put<GroupDetail>(`${this.base}/groups/${id}`, payload);
  }
  confirmGroup(id: number): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/confirm`, {});
  }
  cancelGroup(id: number, reason: string | null, waiveFees: boolean): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/cancel`, { reason, waiveFees });
  }
  completeGroup(id: number): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/complete`, {});
  }
  addGroupBlock(id: number, payload: BlockPayload): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/blocks`, payload);
  }
  updateGroupBlock(id: number, blockId: number, payload: BlockPayload): Observable<GroupDetail> {
    return this.http.put<GroupDetail>(`${this.base}/groups/${id}/blocks/${blockId}`, payload);
  }
  deleteGroupBlock(id: number, blockId: number): Observable<GroupDetail> {
    return this.http.delete<GroupDetail>(`${this.base}/groups/${id}/blocks/${blockId}`);
  }
  releaseGroupBlock(id: number, blockId: number): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/blocks/${blockId}/release`, {});
  }
  addGroupRooms(id: number, payload: AddRoomsPayload): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/rooms`, payload);
  }
  updateGroupRoom(id: number, bookingId: number, payload: { guest?: { fullName: string; phone?: string | null }; adults?: number; children?: number; roomId?: number | null }): Observable<GroupDetail> {
    return this.http.put<GroupDetail>(`${this.base}/groups/${id}/rooming/${bookingId}`, payload);
  }
  nameGroupRooms(id: number, names: string[]): Observable<{ assigned: number; leftover: number; group: GroupDetail }> {
    return this.http.post<{ assigned: number; leftover: number; group: GroupDetail }>(`${this.base}/groups/${id}/rooming/names`, { names });
  }
  roomingExport(id: number, format: 'xlsx' | 'pdf' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/groups/${id}/rooming/export`, { params: new HttpParams().set('format', format), responseType: 'blob' });
  }
  groupMoney(id: number, kind: 'deposits' | 'payments' | 'refunds', payload: GroupMoneyPayload): Observable<GroupDetail> {
    return this.http.post<GroupDetail>(`${this.base}/groups/${id}/${kind}`, payload);
  }
  groupStatementPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/groups/${id}/statement/pdf`, { responseType: 'blob' });
  }

  // housekeeping v2
  hkBoard(date?: string): Observable<HkBoard> {
    return this.http.get<HkBoard>(`${this.base}/housekeeping/board`, { params: date ? new HttpParams().set('date', date) : undefined });
  }
  hkGenerate(): Observable<HkBoard> {
    return this.http.post<HkBoard>(`${this.base}/housekeeping/board/generate`, {});
  }
  hkAutoAssign(): Observable<HkBoard> {
    return this.http.post<HkBoard>(`${this.base}/housekeeping/board/assign`, {});
  }
  hkBoardExport(date: string, format: 'xlsx' | 'pdf' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/housekeeping/board/export`, { params: new HttpParams().set('date', date).set('format', format), responseType: 'blob' });
  }
  hkAssign(taskId: number, staffId: number | null): Observable<HkTask> {
    return this.http.put<HkTask>(`${this.base}/housekeeping/tasks/${taskId}/assign`, { staffId });
  }
  hkAct(taskId: number, action: 'start' | 'finish' | 'skip' | 'reopen', body: Record<string, unknown> = {}): Observable<HkTask> {
    return this.http.post<HkTask>(`${this.base}/housekeeping/tasks/${taskId}/${action}`, body);
  }
  hkInspect(taskId: number, checks: HkCheck[], note: string | null, passed: boolean | null): Observable<HkTask> {
    return this.http.post<HkTask>(`${this.base}/housekeeping/tasks/${taskId}/inspect`, { checks, note, passed });
  }
  hkDnd(roomId: number, dnd: boolean): Observable<HkBoard> {
    return this.http.put<HkBoard>(`${this.base}/housekeeping/rooms/${roomId}/dnd`, { dnd });
  }
  hkProductivity(from: string, to: string): Observable<Productivity[]> {
    return this.http.get<Productivity[]>(`${this.base}/housekeeping/productivity`, { params: new HttpParams().set('from', from).set('to', to) });
  }
  hkStaff(): Observable<HkStaff[]> {
    return this.http.get<HkStaff[]>(`${this.base}/housekeeping/staff`);
  }
  hkSaveStaff(id: number | null, name: string, active: boolean): Observable<HkStaff> {
    return id ? this.http.put<HkStaff>(`${this.base}/housekeeping/staff/${id}`, { name, active }) : this.http.post<HkStaff>(`${this.base}/housekeeping/staff`, { name, active });
  }
  hkDeleteStaff(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/housekeeping/staff/${id}`);
  }
  hkChecklist(all: boolean): Observable<ChecklistItem[]> {
    return this.http.get<ChecklistItem[]>(`${this.base}/housekeeping/checklist`, { params: new HttpParams().set('all', all) });
  }
  hkSaveChecklist(items: { label: string; active: boolean }[]): Observable<ChecklistItem[]> {
    return this.http.put<ChecklistItem[]>(`${this.base}/housekeeping/checklist`, { items });
  }
  createLostFound(payload: LostFoundPayload): Observable<LostFound> {
    return this.http.post<LostFound>(`${this.base}/lost-found`, payload);
  }
  updateLostFound(id: number, payload: LostFoundPayload): Observable<LostFound> {
    return this.http.put<LostFound>(`${this.base}/lost-found/${id}`, payload);
  }
  claimLostFound(id: number, claimedBy: string | null, guestId: number | null, notes: string | null): Observable<LostFound> {
    return this.http.post<LostFound>(`${this.base}/lost-found/${id}/claim`, { claimedBy, guestId, notes });
  }
  disposeLostFound(id: number, notes: string | null): Observable<LostFound> {
    return this.http.post<LostFound>(`${this.base}/lost-found/${id}/dispose`, { notes });
  }
  deleteLostFound(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/lost-found/${id}`);
  }
  minibarItems(all: boolean): Observable<MinibarItem[]> {
    return this.http.get<MinibarItem[]>(`${this.base}/minibar/items`, { params: new HttpParams().set('all', all) });
  }
  saveMinibarItem(id: number | null, payload: { name: string; price: number; taxable: boolean; active: boolean }): Observable<MinibarItem> {
    return id ? this.http.put<MinibarItem>(`${this.base}/minibar/items/${id}`, payload) : this.http.post<MinibarItem>(`${this.base}/minibar/items`, payload);
  }
  deleteMinibarItem(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/minibar/items/${id}`);
  }
  useMinibar(roomId: number, lines: { itemId: number; qty: number }[]): Observable<{ bookingId: number; bookingNumber: string; roomNumber: string; guestName: string; lines: number; total: number }> {
    return this.http.post<{ bookingId: number; bookingNumber: string; roomNumber: string; guestName: string; lines: number; total: number }>(`${this.base}/minibar/use`, { roomId, lines });
  }
  linenLog(): Observable<LinenEntry[]> {
    return this.http.get<LinenEntry[]>(`${this.base}/linen`);
  }
  linenBalance(): Observable<LinenBalance[]> {
    return this.http.get<LinenBalance[]>(`${this.base}/linen/balance`);
  }
  addLinen(payload: { item: string; sent: number | null; returned: number | null; damaged: number | null; notes: string | null }): Observable<LinenEntry> {
    return this.http.post<LinenEntry>(`${this.base}/linen`, payload);
  }
  deleteLinen(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/linen/${id}`);
  }

  companySummary(): Observable<CompanySummary[]> {
    return this.http.get<CompanySummary[]>(`${this.base}/company-summary`);
  }
  receiveFromCompany(id: number, payload: { method: string; amount: number; reference: string | null; receivedOn: string | null }): Observable<CompanyBill> {
    return this.http.post<CompanyBill>(`${this.base}/company-bills/${id}/receipts`, payload);
  }

  // rates
  ratePlans(): Observable<RatePlan[]> {
    return this.http.get<RatePlan[]>(`${this.base}/rate-plans`);
  }
  saveRatePlan(id: number | null, payload: Partial<RatePlan>): Observable<RatePlan> {
    return id ? this.http.put<RatePlan>(`${this.base}/rate-plans/${id}`, payload) : this.http.post<RatePlan>(`${this.base}/rate-plans`, payload);
  }
  deleteRatePlan(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/rate-plans/${id}`);
  }
  rateRules(): Observable<RateRule[]> {
    return this.http.get<RateRule[]>(`${this.base}/rate-rules`);
  }
  saveRateRule(id: number | null, payload: RateRulePayload): Observable<RateRule> {
    return id ? this.http.put<RateRule>(`${this.base}/rate-rules/${id}`, payload) : this.http.post<RateRule>(`${this.base}/rate-rules`, payload);
  }
  deleteRateRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/rate-rules/${id}`);
  }
  quote(roomTypeId: number, from: string, to: string, opts: { roomId?: number | null; ratePlanId?: number | null; adults?: number; children?: number } = {}): Observable<Quote> {
    let params = new HttpParams().set('roomTypeId', String(roomTypeId)).set('from', from).set('to', to);
    if (opts.roomId) params = params.set('roomId', String(opts.roomId));
    if (opts.ratePlanId) params = params.set('ratePlanId', String(opts.ratePlanId));
    if (opts.adults) params = params.set('adults', String(opts.adults));
    if (opts.children) params = params.set('children', String(opts.children));
    return this.http.get<Quote>(`${this.base}/quote`, { params });
  }
  rateCalendar(roomTypeId: number, from: string, to: string, ratePlanId?: number | null): Observable<RateCalendar> {
    let params = new HttpParams().set('roomTypeId', String(roomTypeId)).set('from', from).set('to', to);
    if (ratePlanId) params = params.set('ratePlanId', String(ratePlanId));
    return this.http.get<RateCalendar>(`${this.base}/rate-calendar`, { params });
  }
  bookingNights(id: number): Observable<QuoteNight[]> {
    return this.http.get<QuoteNight[]>(`${this.base}/bookings/${id}/nights`);
  }

  // maintenance
  maintenance(): Observable<Maintenance[]> {
    return this.http.get<Maintenance[]>(`${this.base}/maintenance`);
  }
  createMaintenance(payload: { roomId: number; title: string; description: string | null; priority: string; blocksRoom: boolean; reportedBy: string | null; blockFrom?: string | null; blockUntil?: string | null }): Observable<Maintenance> {
    return this.http.post<Maintenance>(`${this.base}/maintenance`, payload);
  }
  updateMaintenance(id: number, payload: Partial<{ status: string; priority: string; blocksRoom: boolean; resolutionNote: string | null; blockFrom: string | null; blockUntil: string | null; clearBlockDates: boolean }>): Observable<Maintenance> {
    return this.http.put<Maintenance>(`${this.base}/maintenance/${id}`, payload);
  }
  deleteMaintenance(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/maintenance/${id}`);
  }
}
