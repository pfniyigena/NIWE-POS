import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { MaintenanceRow } from './pm-maintenance-api.service';
import { PageParams, PageResult, getPaged } from './paged';

export type PmPropertyKind = 'HOUSE' | 'APARTMENT_BLOCK' | 'COMMERCIAL' | 'MIXED_USE' | 'LAND' | 'OTHER';
export type PmUnitKind = 'APARTMENT' | 'ROOM' | 'HOUSE' | 'SHOP' | 'OFFICE' | 'WAREHOUSE' | 'STORE' | 'PARKING' | 'OTHER';
export type TenantKind = 'INDIVIDUAL' | 'COMPANY';
export type LeaseStatus = 'DRAFT' | 'ACTIVE' | 'ENDED' | 'TERMINATED';
export type RentFrequency = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'ANNUAL';
export type LeaseState = 'DRAFT' | 'UPCOMING' | 'CURRENT' | 'EXPIRING' | 'EXPIRED' | 'RENEWED' | 'ENDED' | 'TERMINATED';
export type Occupancy = 'OCCUPIED' | 'UPCOMING' | 'VACANT' | 'OFF_MARKET';

export const PM_PROPERTY_KINDS: { value: PmPropertyKind; label: string }[] = [
  { value: 'APARTMENT_BLOCK', label: 'Apartment block' }, { value: 'HOUSE', label: 'House' }, { value: 'COMMERCIAL', label: 'Commercial building' },
  { value: 'MIXED_USE', label: 'Mixed use' }, { value: 'LAND', label: 'Land' }, { value: 'OTHER', label: 'Other' },
];
export const PM_UNIT_KINDS: { value: PmUnitKind; label: string }[] = [
  { value: 'APARTMENT', label: 'Apartment' }, { value: 'ROOM', label: 'Room' }, { value: 'HOUSE', label: 'House' }, { value: 'SHOP', label: 'Shop' }, { value: 'OFFICE', label: 'Office' },
  { value: 'WAREHOUSE', label: 'Warehouse' }, { value: 'STORE', label: 'Store' }, { value: 'PARKING', label: 'Parking' }, { value: 'OTHER', label: 'Other' },
];
export const RENT_FREQUENCIES: { value: RentFrequency; label: string }[] = [
  { value: 'MONTHLY', label: 'Monthly' }, { value: 'QUARTERLY', label: 'Every 3 months' }, { value: 'HALF_YEARLY', label: 'Every 6 months' }, { value: 'ANNUAL', label: 'Yearly' },
];
export const LEASE_STATE_LABEL: Record<LeaseState, string> = {
  DRAFT: 'Draft', UPCOMING: 'Signed, not started', CURRENT: 'In force', EXPIRING: 'Ending soon', EXPIRED: 'Past its end date', RENEWED: 'Renewed', ENDED: 'Ended', TERMINATED: 'Terminated early',
};
export const OCCUPANCY_LABEL: Record<Occupancy, string> = { OCCUPIED: 'Occupied', UPCOMING: 'Let from a date', VACANT: 'Vacant', OFF_MARKET: 'Off the market' };

export function pmLabel(list: { value: string; label: string }[], v: string | null | undefined): string { return list.find((x) => x.value === v)?.label ?? (v ?? ''); }
/** The badge colour class for a lease state. */
export function leaseBadge(s: string): string {
  return s === 'CURRENT' ? 'badge-success' : s === 'EXPIRING' || s === 'UPCOMING' ? 'badge-warning' : s === 'EXPIRED' || s === 'TERMINATED' ? 'badge-danger' : 'badge-secondary';
}
export function occupancyBadge(s: string): string {
  return s === 'OCCUPIED' ? 'badge-success' : s === 'VACANT' ? 'badge-danger' : s === 'UPCOMING' ? 'badge-warning' : 'badge-secondary';
}

export interface PmSettings { areas: string[]; expiryWarningDays: number; defaultDueDay: number; defaultFeePercent: number | null; invoiceAheadDays: number; autoInvoice: boolean; feeVatPercent: number | null; autoStatements: boolean; autoStatementsDay: number; autoSendStatements: boolean; autoStatementsThrough: string | null; }
export interface PmSettingsPayload { areas?: string; expiryWarningDays?: number; defaultDueDay?: number; defaultFeePercent?: number | null; clearFee?: boolean; invoiceAheadDays?: number; autoInvoice?: boolean; feeVatPercent?: number | null; clearFeeVat?: boolean; autoStatements?: boolean; autoStatementsDay?: number; autoSendStatements?: boolean; }

export interface OwnerRow { id: number; code: string; name: string; phone: string | null; email: string | null; active: boolean; properties: number; units: number; occupied: number; monthlyRent: number; }
export interface OwnerDto {
  id: number; code: string; name: string; phone: string | null; email: string | null; idNumber: string | null; taxNumber: string | null; address: string | null; bankName: string | null;
  bankAccount: string | null; feePercent: number | null; active: boolean; notes: string | null; properties: PropertyRow[]; units: number; occupied: number; monthlyRent: number;
  canManage: boolean; createdAt: string;
}
export interface OwnerPayload {
  name?: string; phone?: string; email?: string; idNumber?: string; taxNumber?: string; address?: string; bankName?: string; bankAccount?: string; feePercent?: number | null;
  clearFee?: boolean; active?: boolean; notes?: string;
}

export interface PropertyRow {
  id: number; code: string; name: string; kind: PmPropertyKind; ownerId: number; ownerName: string | null; area: string | null; active: boolean; units: number; occupied: number; vacant: number;
  monthlyRent: number; effectiveFeePercent: number | null; ownerCount: number; ownersLabel: string | null; sharePercent: number | null;
}
export interface PropertyDto {
  id: number; code: string; name: string; kind: PmPropertyKind; ownerId: number; ownerName: string | null; area: string | null; address: string | null; titleReference: string | null;
  feePercent: number | null; effectiveFeePercent: number | null; managedFrom: string | null; active: boolean; notes: string | null; unitCount: number; occupied: number; upcoming: number;
  vacant: number; offMarket: number; monthlyRent: number; vacantMarketRent: number; units: UnitRow[]; canManage: boolean; createdAt: string; ownerCount: number; ownersLabel: string | null;
}
export interface PropertyPayload {
  name?: string; kind?: PmPropertyKind; ownerId?: number | null; area?: string; address?: string; titleReference?: string; feePercent?: number | null; clearFee?: boolean;
  managedFrom?: string | null; clearManagedFrom?: boolean; active?: boolean; notes?: string;
}

export interface UnitRow {
  id: number; propertyId: number; propertyCode: string | null; propertyName: string | null; unitNo: string; kind: PmUnitKind; floor: string | null; bedrooms: number | null; bathrooms: number | null;
  sizeSqm: number | null; marketRent: number | null; defaultDeposit: number | null; offMarket: boolean; offMarketNote: string | null; occupancy: Occupancy; leaseId: number | null;
  leaseCode: string | null; tenantId: number | null; tenantName: string | null; rent: number | null; frequency: RentFrequency | null; monthlyRent: number | null; leaseStart: string | null;
  leaseEnd: string | null; daysToEnd: number | null; holdingOver: boolean; vacantSince: string | null; notes: string | null;
}
export interface UnitDto { unit: UnitRow; ownerName: string | null; ownerId: number | null; leases: LeaseRow[]; canManage: boolean; }
export interface UnitPayload {
  unitNo?: string; kind?: PmUnitKind; floor?: string; bedrooms?: number | null; bathrooms?: number | null; sizeSqm?: number | null; marketRent?: number | null; defaultDeposit?: number | null;
  offMarket?: boolean; offMarketNote?: string; notes?: string;
}
export interface BulkUnitsPayload {
  prefix: string; from: number; to: number; pad: number; kind?: PmUnitKind; floor?: string; bedrooms?: number | null; bathrooms?: number | null; sizeSqm?: number | null;
  marketRent?: number | null; defaultDeposit?: number | null;
}

export interface TenantRow { id: number; code: string; kind: TenantKind; name: string; phone: string | null; email: string | null; active: boolean; currentLeases: number; currentUnits: string | null; customerId: number | null; }
export interface TenantDto {
  id: number; code: string; kind: TenantKind; name: string; contactPerson: string | null; phone: string | null; email: string | null; idNumber: string | null; address: string | null;
  employer: string | null; emergencyName: string | null; emergencyPhone: string | null; customerId: number | null; active: boolean; notes: string | null; leases: LeaseRow[]; canManage: boolean; createdAt: string;
}
export interface TenantPayload {
  kind?: TenantKind; name?: string; contactPerson?: string; phone?: string; email?: string; idNumber?: string; address?: string; employer?: string; emergencyName?: string;
  emergencyPhone?: string; active?: boolean; notes?: string;
}

export interface LeaseRow {
  id: number; code: string; status: LeaseStatus; state: LeaseState; unitId: number; unitNo: string | null; propertyId: number | null; propertyCode: string | null; propertyName: string | null;
  ownerId: number | null; ownerName: string | null; tenantId: number; tenantName: string | null; startDate: string; endDate: string; rent: number; frequency: RentFrequency; monthlyRent: number;
  dueDay: number; deposit: number | null; daysToEnd: number | null; renewedFromId: number | null; renewedFromCode: string | null; renewalId: number | null; renewalCode: string | null;
}
export interface LeaseDto extends LeaseRow {
  customerId: number | null; escalationPercent: number | null; noticeDays: number; signedDate: string | null; terminatedDate: string | null; terminationReason: string | null; terms: string | null;
  suggestedRenewalRent: number | null; createdByName: string | null; canManage: boolean; createdAt: string;
}
export interface LeasePayload {
  unitId?: number | null; tenantId?: number | null; startDate?: string | null; endDate?: string | null; rent?: number | null; frequency?: RentFrequency; dueDay?: number | null; deposit?: number | null;
  escalationPercent?: number | null; noticeDays?: number | null; signedDate?: string | null; clearSignedDate?: boolean; terms?: string;
}
export interface RenewPayload { startDate?: string | null; endDate?: string | null; rent?: number | null; deposit?: number | null; terms?: string | null; }

export type RentStatus = 'NOT_INVOICED' | 'WAIVED' | 'UNPAID' | 'PARTLY_PAID' | 'PAID' | 'OVERDUE';
export const RENT_STATUS_LABEL: Record<RentStatus, string> = { NOT_INVOICED: 'Not invoiced', WAIVED: 'Waived', UNPAID: 'Invoiced', PARTLY_PAID: 'Part paid', PAID: 'Paid', OVERDUE: 'Overdue' };
export function rentBadge(s: string): string {
  return s === 'PAID' ? 'badge-success' : s === 'OVERDUE' ? 'badge-danger' : s === 'UNPAID' || s === 'PARTLY_PAID' ? 'badge-warning' : 'badge-secondary';
}

export interface RentChargeRow {
  id: number; leaseId: number; leaseCode: string | null; unitId: number | null; unitNo: string | null; propertyId: number | null; propertyName: string | null; ownerId: number | null;
  ownerName: string | null; tenantId: number | null; tenantName: string | null; periodStart: string; periodEnd: string; dueDate: string; amount: number; prorated: boolean; status: RentStatus;
  invoiceId: number | null; invoiceNumber: string | null; billed: number | null; paid: number | null; outstanding: number | null; daysOverdue: number | null; waivedReason: string | null;
  readyToInvoice: boolean; afterLeaving: boolean; reminders: number; lastReminderOn: string | null; lateFee: number | null; lateFeeInvoice: string | null; extras: number;
}
export interface RentSchedule {
  leaseId: number; leaseCode: string; charges: RentChargeRow[]; billed: number; paid: number; outstanding: number; overdue: number; notYetInvoiced: number; afterLeaving: number; canManage: boolean;
}
export interface InvoiceRun { invoiced: number; failed: number; amount: number; problems: string[]; }
export interface ArrearsRow {
  tenantId: number; tenantCode: string | null; tenantName: string | null; phone: string | null; charges: number; outstanding: number; days30: number; days60: number; days90: number; days90Plus: number;
  oldestDue: string; oldestDays: number; items: RentChargeRow[];
}
export interface Arrears { total: number; days30: number; days60: number; days90: number; days90Plus: number; tenants: number; rows: ArrearsRow[]; }

export interface PmOverview {
  owners: number; properties: number; units: number; occupied: number; upcoming: number; vacant: number; offMarket: number; occupancyPercent: number; monthlyRentRoll: number; vacantMarketRent: number;
  leasesExpiring: number; leasesExpired: number; draftLeases: number; tenants: number; expiryWarningDays: number; expiringLeases: LeaseRow[]; vacantUnits: UnitRow[];
  rentToInvoice: number; rentToInvoiceAmount: number; arrears: number; tenantsInArrears: number;
  maintenanceOpen: number; maintenanceUrgent: number; maintenanceOverdue: number; maintenanceAttention: MaintenanceRow[];
  statementsDraft: number; statementsToPay: number; statementsToPayAmount: number; documentsExpiring: number; documentsExpired: number; depositsHeld: number; depositsRefundDue: number; depositsRefundDueAmount: number;
}

@Injectable({ providedIn: 'root' })
export class PropertyMgmtApiService {
  private base = `${API_BASE}/propertymgmt`;

  constructor(private http: HttpClient) {}

  private params(o: Record<string, unknown>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(o)) if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
    return p;
  }

  // Paged lists for the list screens (the plain methods above/below still return every row, for pickers and reports).
  ownersPage(f: { q?: string; activeOnly?: boolean } & PageParams): Observable<PageResult<OwnerRow>> { return getPaged<OwnerRow>(this.http, `${this.base}/owners`, this.params({ ...f })); }
  propertiesPage(f: { q?: string; ownerId?: number | null; activeOnly?: boolean } & PageParams): Observable<PageResult<PropertyRow>> { return getPaged<PropertyRow>(this.http, `${this.base}/properties`, this.params({ ...f })); }
  unitsPage(f: { q?: string; propertyId?: number | null; occupancy?: string; ownerId?: number | null } & PageParams): Observable<PageResult<UnitRow>> { return getPaged<UnitRow>(this.http, `${this.base}/units`, this.params({ ...f })); }
  tenantsPage(f: { q?: string; activeOnly?: boolean; withLeaseOnly?: boolean } & PageParams): Observable<PageResult<TenantRow>> { return getPaged<TenantRow>(this.http, `${this.base}/tenants`, this.params({ ...f })); }
  leasesPage(f: { q?: string; status?: string; state?: string; unitId?: number | null; tenantId?: number | null; propertyId?: number | null; ownerId?: number | null; expiringOnly?: boolean } & PageParams): Observable<PageResult<LeaseRow>> { return getPaged<LeaseRow>(this.http, `${this.base}/leases`, this.params({ ...f })); }

  settings(): Observable<PmSettings> { return this.http.get<PmSettings>(`${this.base}/settings`); }
  saveSettings(p: PmSettingsPayload): Observable<PmSettings> { return this.http.put<PmSettings>(`${this.base}/settings`, p); }
  overview(): Observable<PmOverview> { return this.http.get<PmOverview>(`${this.base}/overview`); }

  owners(f: { q?: string; activeOnly?: boolean }): Observable<OwnerRow[]> { return this.http.get<OwnerRow[]>(`${this.base}/owners`, { params: this.params({ ...f }) }); }
  owner(id: number): Observable<OwnerDto> { return this.http.get<OwnerDto>(`${this.base}/owners/${id}`); }
  createOwner(p: OwnerPayload): Observable<OwnerDto> { return this.http.post<OwnerDto>(`${this.base}/owners`, p); }
  updateOwner(id: number, p: OwnerPayload): Observable<OwnerDto> { return this.http.put<OwnerDto>(`${this.base}/owners/${id}`, p); }
  deleteOwner(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/owners/${id}`); }

  properties(f: { q?: string; ownerId?: number | null; activeOnly?: boolean }): Observable<PropertyRow[]> { return this.http.get<PropertyRow[]>(`${this.base}/properties`, { params: this.params({ ...f }) }); }
  property(id: number): Observable<PropertyDto> { return this.http.get<PropertyDto>(`${this.base}/properties/${id}`); }
  createProperty(p: PropertyPayload): Observable<PropertyDto> { return this.http.post<PropertyDto>(`${this.base}/properties`, p); }
  updateProperty(id: number, p: PropertyPayload): Observable<PropertyDto> { return this.http.put<PropertyDto>(`${this.base}/properties/${id}`, p); }
  deleteProperty(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/properties/${id}`); }
  addUnit(propertyId: number, p: UnitPayload): Observable<UnitRow> { return this.http.post<UnitRow>(`${this.base}/properties/${propertyId}/units`, p); }
  addUnitsBulk(propertyId: number, p: BulkUnitsPayload): Observable<UnitRow[]> { return this.http.post<UnitRow[]>(`${this.base}/properties/${propertyId}/units/bulk`, p); }

  units(f: { q?: string; propertyId?: number | null; occupancy?: string; ownerId?: number | null }): Observable<UnitRow[]> { return this.http.get<UnitRow[]>(`${this.base}/units`, { params: this.params({ ...f }) }); }
  unit(id: number): Observable<UnitDto> { return this.http.get<UnitDto>(`${this.base}/units/${id}`); }
  updateUnit(id: number, p: UnitPayload): Observable<UnitRow> { return this.http.put<UnitRow>(`${this.base}/units/${id}`, p); }
  deleteUnit(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/units/${id}`); }

  tenants(f: { q?: string; activeOnly?: boolean; withLeaseOnly?: boolean }): Observable<TenantRow[]> { return this.http.get<TenantRow[]>(`${this.base}/tenants`, { params: this.params({ ...f }) }); }
  tenant(id: number): Observable<TenantDto> { return this.http.get<TenantDto>(`${this.base}/tenants/${id}`); }
  createTenant(p: TenantPayload): Observable<TenantDto> { return this.http.post<TenantDto>(`${this.base}/tenants`, p); }
  updateTenant(id: number, p: TenantPayload): Observable<TenantDto> { return this.http.put<TenantDto>(`${this.base}/tenants/${id}`, p); }
  deleteTenant(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/tenants/${id}`); }

  leases(f: { q?: string; status?: string; state?: string; unitId?: number | null; tenantId?: number | null; propertyId?: number | null; ownerId?: number | null; expiringOnly?: boolean }): Observable<LeaseRow[]> {
    return this.http.get<LeaseRow[]>(`${this.base}/leases`, { params: this.params({ ...f }) });
  }
  lease(id: number): Observable<LeaseDto> { return this.http.get<LeaseDto>(`${this.base}/leases/${id}`); }
  createLease(p: LeasePayload): Observable<LeaseDto> { return this.http.post<LeaseDto>(`${this.base}/leases`, p); }
  updateLease(id: number, p: LeasePayload): Observable<LeaseDto> { return this.http.put<LeaseDto>(`${this.base}/leases/${id}`, p); }
  deleteLease(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/leases/${id}`); }
  activateLease(id: number, signedDate: string | null, billFrom?: string | null): Observable<LeaseDto> { return this.http.post<LeaseDto>(`${this.base}/leases/${id}/activate`, { signedDate, billFrom: billFrom || null }); }
  terminateLease(id: number, date: string | null, reason: string): Observable<LeaseDto> { return this.http.post<LeaseDto>(`${this.base}/leases/${id}/terminate`, { date, reason }); }
  endLease(id: number, force: boolean): Observable<LeaseDto> { return this.http.post<LeaseDto>(`${this.base}/leases/${id}/end`, { force }); }
  renewLease(id: number, p: RenewPayload): Observable<LeaseDto> { return this.http.post<LeaseDto>(`${this.base}/leases/${id}/renew`, p); }

  leaseRent(id: number): Observable<RentSchedule> { return this.http.get<RentSchedule>(`${this.base}/leases/${id}/rent`); }
  rent(f: { status?: string; leaseId?: number | null; tenantId?: number | null; propertyId?: number | null; ownerId?: number | null; from?: string; to?: string }): Observable<RentChargeRow[]> {
    return this.http.get<RentChargeRow[]>(`${this.base}/rent`, { params: this.params({ ...f }) });
  }
  invoiceRent(p: { leaseId?: number | null; through?: string | null }): Observable<InvoiceRun> { return this.http.post<InvoiceRun>(`${this.base}/rent/invoice`, p); }
  invoiceCharge(id: number): Observable<RentChargeRow> { return this.http.post<RentChargeRow>(`${this.base}/rent/${id}/invoice`, {}); }
  waiveCharge(id: number, reason: string): Observable<RentChargeRow> { return this.http.post<RentChargeRow>(`${this.base}/rent/${id}/waive`, { reason }); }
  restoreCharge(id: number): Observable<RentChargeRow> { return this.http.post<RentChargeRow>(`${this.base}/rent/${id}/restore`, {}); }
  arrears(f: { tenantId?: number | null; propertyId?: number | null; ownerId?: number | null }): Observable<Arrears> { return this.http.get<Arrears>(`${this.base}/arrears`, { params: this.params({ ...f }) }); }
}
