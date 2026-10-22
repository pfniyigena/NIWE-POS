import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { API_BASE } from '../constants';
import { downloadBlob } from '../download-file';
import { PageParams, PageResult, getPaged } from './paged';

export type DepositKind = 'RECEIVED' | 'TRANSFER_IN' | 'DEDUCTION' | 'REFUND' | 'TRANSFER_OUT';
export type DepositReason = 'UNPAID_RENT' | 'DAMAGE' | 'CLEANING' | 'UTILITIES' | 'OTHER';
export type DepositState = 'NONE' | 'DUE' | 'PART_PAID' | 'HELD' | 'REFUND_DUE' | 'SETTLED';
export type InspectionKind = 'MOVE_IN' | 'MOVE_OUT' | 'PERIODIC';
export type InspectionStatus = 'DRAFT' | 'SIGNED_OFF';
export type ItemCondition = 'GOOD' | 'FAIR' | 'POOR' | 'DAMAGED' | 'MISSING' | 'NA';

export const DEPOSIT_STATE_LABEL: Record<DepositState, string> = { NONE: 'No deposit', DUE: 'To collect', PART_PAID: 'Part paid', HELD: 'Held', REFUND_DUE: 'Refund due', SETTLED: 'Settled' };
export const DEPOSIT_KIND_LABEL: Record<DepositKind, string> = { RECEIVED: 'Received', TRANSFER_IN: 'Moved in', DEDUCTION: 'Kept', REFUND: 'Refunded', TRANSFER_OUT: 'Moved out' };
export const DEPOSIT_REASONS: { value: DepositReason; label: string }[] = [
  { value: 'UNPAID_RENT', label: 'Unpaid rent' }, { value: 'DAMAGE', label: 'Damage' }, { value: 'CLEANING', label: 'Cleaning' }, { value: 'UTILITIES', label: 'Utilities' }, { value: 'OTHER', label: 'Other' },
];
export const DEPOSIT_METHODS: { value: string; label: string }[] = [
  { value: 'CASH', label: 'Cash' }, { value: 'BANK', label: 'Bank' }, { value: 'MOBILE_MONEY', label: 'Mobile money' }, { value: 'CHEQUE', label: 'Cheque' }, { value: 'CARD', label: 'Card' }, { value: 'OTHER', label: 'Other' },
];
export const INSPECTION_KIND_LABEL: Record<InspectionKind, string> = { MOVE_IN: 'Move-in', MOVE_OUT: 'Move-out', PERIODIC: 'Visit' };
export const CONDITIONS: { value: ItemCondition; label: string }[] = [
  { value: 'GOOD', label: 'Good' }, { value: 'FAIR', label: 'Fair' }, { value: 'POOR', label: 'Poor' }, { value: 'DAMAGED', label: 'Damaged' }, { value: 'MISSING', label: 'Missing' }, { value: 'NA', label: 'Not applicable' },
];
export function conditionLabel(c: string | null | undefined): string { return c ? (CONDITIONS.find((x) => x.value === c)?.label ?? c) : '—'; }
export function depositBadge(s: string): string {
  return s === 'REFUND_DUE' ? 'badge-danger' : s === 'DUE' || s === 'PART_PAID' ? 'badge-warning' : s === 'HELD' ? 'badge-success' : 'badge-secondary';
}
export function conditionBadge(c: string | null | undefined): string {
  return c === 'GOOD' ? 'badge-success' : c === 'FAIR' ? 'badge-secondary' : c === 'POOR' ? 'badge-warning' : c === 'DAMAGED' || c === 'MISSING' ? 'badge-danger' : 'badge-secondary';
}

export interface DepositTxn {
  id: number; kind: DepositKind; amount: number; date: string; method: string | null; reason: DepositReason | null; note: string | null; invoiceId: number | null; invoiceNumber: string | null;
  receiptNumber: string | null; otherLeaseId: number | null; otherLeaseCode: string | null; voided: boolean; voidReason: string | null; voidedAt: string | null; recordedByName: string | null; canVoid: boolean;
}
export interface OpenInvoice { invoiceId: number; invoiceNumber: string; dueDate: string; outstanding: number; }
export interface TransferTarget { leaseId: number; leaseCode: string; status: string; agreed: number; paidIn: number; room: number; }
export interface DepositView {
  leaseId: number; leaseCode: string; leaseStatus: string; tenantId: number; tenantName: string; propertyName: string; unitNo: string; agreed: number; received: number; transferredIn: number;
  deducted: number; refunded: number; transferredOut: number; paidIn: number; held: number; stillToPay: number; state: DepositState; canReceive: boolean; canSettle: boolean; canTransfer: boolean;
  canManage: boolean; suggestedDamages: number; moveOutInspectionId: number | null; txns: DepositTxn[]; openInvoices: OpenInvoice[]; transferTargets: TransferTarget[];
}
export interface DepositRow {
  leaseId: number; leaseCode: string; leaseStatus: string; leaseState: string; tenantId: number; tenantName: string; propertyId: number; propertyName: string; unitNo: string; ownerId: number;
  ownerName: string; endDate: string; daysToEnd: number | null; agreed: number; paidIn: number; deducted: number; refunded: number; held: number; state: DepositState;
}
export interface DepositTotals { held: number; toCollect: number; refundDue: number; refundDueCount: number; heldCount: number; }
export interface DepositFilter extends PageParams { q?: string; state?: string; propertyId?: number | null; tenantId?: number | null; }

export interface InspectionItem { id: number; sortOrder: number; room: string; item: string; condition: ItemCondition | null; baseline: ItemCondition | null; worse: boolean; notes: string | null; repairCost: number | null; }
export interface InspectionRow {
  id: number; code: string; leaseId: number; leaseCode: string; tenantName: string; propertyName: string; unitNo: string; kind: InspectionKind; status: InspectionStatus; date: string;
  inspectorName: string | null; items: number; assessed: number; problems: number; repairCost: number; tenantAcknowledged: boolean;
}
export interface Inspection {
  id: number; code: string; leaseId: number; leaseCode: string; tenantId: number; tenantName: string; propertyId: number; propertyName: string; unitId: number; unitNo: string; kind: InspectionKind;
  status: InspectionStatus; date: string; inspectorName: string | null; electricityReading: string | null; waterReading: string | null; keysHanded: number | null; notes: string | null;
  tenantAcknowledged: boolean; tenantSignedName: string | null; signedOffOn: string | null; signedOffByName: string | null; createdByName: string | null; items: InspectionItem[]; assessed: number;
  problems: number; worse: number; repairCost: number; moveInInspectionId: number | null; canManage: boolean;
}
export interface InspectionFilter extends PageParams { leaseId?: number | null; kind?: string; status?: string; q?: string; tenantId?: number | null; propertyId?: number | null; }

function params(f: object): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(f)) if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
  return p;
}

/** Security deposits of leases, and the move-in / move-out inspections. */
@Injectable({ providedIn: 'root' })
export class PmDepositApiService {
  private base = `${API_BASE}/propertymgmt`;

  constructor(private http: HttpClient) {}

  deposit(leaseId: number): Observable<DepositView> { return this.http.get<DepositView>(`${this.base}/leases/${leaseId}/deposit`); }
  deposits(f: DepositFilter): Observable<PageResult<DepositRow>> { return getPaged<DepositRow>(this.http, `${this.base}/deposits`, params(f)); }
  totals(): Observable<DepositTotals> { return this.http.get<DepositTotals>(`${this.base}/deposits/totals`); }
  receive(leaseId: number, b: { amount: number; date?: string | null; method?: string; note?: string }): Observable<DepositView> { return this.http.post<DepositView>(`${this.base}/leases/${leaseId}/deposit/receive`, b); }
  deduct(leaseId: number, b: { amount: number; date?: string | null; reason: DepositReason; invoiceId?: number | null; note?: string }): Observable<DepositView> { return this.http.post<DepositView>(`${this.base}/leases/${leaseId}/deposit/deduct`, b); }
  refund(leaseId: number, b: { amount: number; date?: string | null; method?: string; note?: string }): Observable<DepositView> { return this.http.post<DepositView>(`${this.base}/leases/${leaseId}/deposit/refund`, b); }
  transfer(leaseId: number, b: { toLeaseId: number; amount: number; date?: string | null; note?: string }): Observable<DepositView> { return this.http.post<DepositView>(`${this.base}/leases/${leaseId}/deposit/transfer`, b); }
  voidEntry(id: number, reason: string): Observable<DepositView> { return this.http.post<DepositView>(`${this.base}/deposits/entries/${id}/void`, { reason }); }

  inspections(f: InspectionFilter): Observable<PageResult<InspectionRow>> { return getPaged<InspectionRow>(this.http, `${this.base}/inspections`, params(f)); }
  inspection(id: number): Observable<Inspection> { return this.http.get<Inspection>(`${this.base}/inspections/${id}`); }
  startInspection(leaseId: number, b: { kind: InspectionKind; date?: string | null; inspectorName?: string; copyFromMoveIn?: boolean; rooms?: string[] }): Observable<Inspection> {
    return this.http.post<Inspection>(`${this.base}/leases/${leaseId}/inspections`, b);
  }
  updateInspection(id: number, b: { date?: string | null; inspectorName?: string; electricityReading?: string; waterReading?: string; keysHanded?: number | null; notes?: string }): Observable<Inspection> {
    return this.http.put<Inspection>(`${this.base}/inspections/${id}`, b);
  }
  addItem(id: number, b: { room: string; item: string; condition?: ItemCondition | null }): Observable<Inspection> { return this.http.post<Inspection>(`${this.base}/inspections/${id}/items`, b); }
  updateItem(id: number, itemId: number, b: { room?: string; item?: string; condition?: ItemCondition; clearCondition?: boolean; notes?: string; repairCost?: number | null; clearCost?: boolean }): Observable<Inspection> {
    return this.http.put<Inspection>(`${this.base}/inspections/${id}/items/${itemId}`, b);
  }
  deleteItem(id: number, itemId: number): Observable<Inspection> { return this.http.delete<Inspection>(`${this.base}/inspections/${id}/items/${itemId}`); }
  signOff(id: number, b: { tenantAcknowledged: boolean; tenantSignedName?: string }): Observable<Inspection> { return this.http.post<Inspection>(`${this.base}/inspections/${id}/sign-off`, b); }
  reopen(id: number): Observable<Inspection> { return this.http.post<Inspection>(`${this.base}/inspections/${id}/reopen`, {}); }
  deleteInspection(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/inspections/${id}`); }
  print(id: number, code: string, format: 'pdf' | 'xlsx'): Observable<void> {
    return this.http.get(`${this.base}/inspections/${id}/print`, { params: { format }, responseType: 'blob' }).pipe(map((b) => downloadBlob(b, `inspection-${code}.${format}`)));
  }
}
