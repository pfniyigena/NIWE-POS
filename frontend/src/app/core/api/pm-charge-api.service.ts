import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type ChargeKind = 'FIXED' | 'METERED';
export type ChargeSource = 'FIXED' | 'METER' | 'ONE_OFF';
export type ExtraStatus = 'PENDING' | 'BILLED' | 'CANCELLED';

export const CHARGE_KIND_LABEL: Record<ChargeKind, string> = { FIXED: 'Fixed every month', METERED: 'Metered (units used)' };
export const CHARGE_SOURCE_LABEL: Record<ChargeSource, string> = { FIXED: 'Monthly', METER: 'Reading', ONE_OFF: 'One-off' };

export interface ChargeType { id: number; name: string; kind: ChargeKind; unitLabel: string | null; defaultAmount: number | null; active: boolean; leaseCharges: number; inUse: boolean; }
export interface LeaseCharge {
  id: number; chargeTypeId: number; typeName: string; kind: ChargeKind; unitLabel: string | null; amount: number; startDate: string; endDate: string | null; openingReading: number | null;
  lastReading: number | null; lastReadingOn: string | null; inForce: boolean; canDelete: boolean; note: string | null;
}
export interface Extra {
  id: number; kind: ChargeSource; leaseChargeId: number | null; chargeTypeId: number | null; typeName: string | null; date: string; description: string; quantity: number | null; unitLabel: string | null;
  rate: number | null; amount: number; readingFrom: number | null; readingTo: number | null; status: ExtraStatus; invoiceNumber: string | null; note: string | null; createdByName: string | null; canCancel: boolean;
}
export interface BilledCharge { id: number; rentChargeId: number; periodStart: string; periodEnd: string; invoiceNumber: string | null; name: string; description: string; source: ChargeSource; amount: number; }
export interface PreviewLine { source: ChargeSource; name: string; description: string; amount: number; }
export interface InvoicePreview { rentChargeId: number; periodStart: string; periodEnd: string; dueDate: string; rent: number; lines: PreviewLine[]; extras: number; total: number; }
export interface LeaseChargesView {
  leaseId: number; leaseCode: string; leaseStatus: string; tenantName: string | null; unitLabel: string; canManage: boolean; canAddReadings: boolean; charges: LeaseCharge[]; pending: Extra[]; history: Extra[];
  billed: BilledCharge[]; next: InvoicePreview | null; types: ChargeType[];
}

/** Utilities and service charges on rent invoices. */
@Injectable({ providedIn: 'root' })
export class PmChargeApiService {
  private base = `${API_BASE}/propertymgmt`;

  constructor(private http: HttpClient) {}

  types(activeOnly = false): Observable<ChargeType[]> {
    let p = new HttpParams();
    if (activeOnly) p = p.set('activeOnly', 'true');
    return this.http.get<ChargeType[]>(`${this.base}/charge-types`, { params: p });
  }
  createType(b: { name: string; kind: ChargeKind; unitLabel?: string; defaultAmount?: number | null }): Observable<ChargeType> { return this.http.post<ChargeType>(`${this.base}/charge-types`, b); }
  updateType(id: number, b: { name?: string; kind?: ChargeKind; unitLabel?: string; defaultAmount?: number | null; clearDefaultAmount?: boolean; active?: boolean }): Observable<ChargeType> {
    return this.http.put<ChargeType>(`${this.base}/charge-types/${id}`, b);
  }
  deleteType(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/charge-types/${id}`); }

  forLease(leaseId: number): Observable<LeaseChargesView> { return this.http.get<LeaseChargesView>(`${this.base}/leases/${leaseId}/charges`); }
  addCharge(leaseId: number, b: { chargeTypeId: number; amount?: number | null; startDate?: string | null; endDate?: string | null; openingReading?: number | null; note?: string }): Observable<LeaseChargesView> {
    return this.http.post<LeaseChargesView>(`${this.base}/leases/${leaseId}/charges`, b);
  }
  updateCharge(id: number, b: { amount?: number | null; startDate?: string | null; endDate?: string | null; clearEndDate?: boolean; note?: string }): Observable<LeaseChargesView> {
    return this.http.put<LeaseChargesView>(`${this.base}/lease-charges/${id}`, b);
  }
  removeCharge(id: number): Observable<LeaseChargesView> { return this.http.delete<LeaseChargesView>(`${this.base}/lease-charges/${id}`); }
  addReading(id: number, b: { reading: number; date?: string | null; note?: string }): Observable<LeaseChargesView> { return this.http.post<LeaseChargesView>(`${this.base}/lease-charges/${id}/readings`, b); }
  addExtra(leaseId: number, b: { description: string; amount: number; chargeTypeId?: number | null; date?: string | null; note?: string }): Observable<LeaseChargesView> {
    return this.http.post<LeaseChargesView>(`${this.base}/leases/${leaseId}/extras`, b);
  }
  cancelExtra(id: number): Observable<LeaseChargesView> { return this.http.delete<LeaseChargesView>(`${this.base}/extras/${id}`); }
}
