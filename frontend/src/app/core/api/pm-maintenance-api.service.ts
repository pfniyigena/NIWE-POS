import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type MaintenanceStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export type MaintenancePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type MaintenanceCategory = 'PLUMBING' | 'ELECTRICAL' | 'STRUCTURE' | 'PAINTING' | 'APPLIANCE' | 'SECURITY' | 'CLEANING' | 'GARDEN' | 'PEST' | 'OTHER';
export type CostBearer = 'OWNER' | 'TENANT' | 'AGENCY';

export const MAINTENANCE_STATUS_LABEL: Record<MaintenanceStatus, string> = { OPEN: 'Open', IN_PROGRESS: 'In progress', ON_HOLD: 'On hold', COMPLETED: 'Done', CANCELLED: 'Cancelled' };
export const MAINTENANCE_PRIORITIES: { value: MaintenancePriority; label: string }[] = [
  { value: 'LOW', label: 'Low' }, { value: 'NORMAL', label: 'Normal' }, { value: 'HIGH', label: 'High' }, { value: 'URGENT', label: 'Urgent' },
];
export const MAINTENANCE_CATEGORIES: { value: MaintenanceCategory; label: string }[] = [
  { value: 'PLUMBING', label: 'Plumbing' }, { value: 'ELECTRICAL', label: 'Electrical' }, { value: 'STRUCTURE', label: 'Structure / roof' }, { value: 'PAINTING', label: 'Painting' },
  { value: 'APPLIANCE', label: 'Appliance' }, { value: 'SECURITY', label: 'Security / locks' }, { value: 'CLEANING', label: 'Cleaning' }, { value: 'GARDEN', label: 'Garden' },
  { value: 'PEST', label: 'Pest control' }, { value: 'OTHER', label: 'Other' },
];
export const COST_BEARERS: { value: CostBearer; label: string }[] = [
  { value: 'OWNER', label: 'The owner' }, { value: 'TENANT', label: 'The tenant' }, { value: 'AGENCY', label: 'Us (the agency)' },
];

export function maintenanceActive(s: string): boolean { return s === 'OPEN' || s === 'IN_PROGRESS' || s === 'ON_HOLD'; }
export function maintenanceStatusBadge(s: string): string {
  return s === 'COMPLETED' ? 'badge-success' : s === 'IN_PROGRESS' ? 'badge-warning' : s === 'OPEN' ? 'badge-danger' : 'badge-secondary';
}
export function maintenancePriorityBadge(p: string): string {
  return p === 'URGENT' ? 'badge-danger' : p === 'HIGH' ? 'badge-warning' : 'badge-secondary';
}
export function maintLabel(list: { value: string; label: string }[], v: string | null | undefined): string { return list.find((x) => x.value === v)?.label ?? (v ?? ''); }

export interface MaintenanceRow {
  id: number; code: string; unitId: number; unitNo: string | null; propertyId: number | null; propertyName: string | null; ownerId: number | null; ownerName: string | null;
  tenantId: number | null; tenantName: string | null; title: string; category: MaintenanceCategory; priority: MaintenancePriority; status: MaintenanceStatus; reportedDate: string;
  scheduledDate: string | null; dueDate: string | null; completedDate: string | null; daysOpen: number | null; overdue: boolean; contractorName: string | null; estimatedCost: number | null;
  actualCost: number | null; costBearer: CostBearer; workOrderNumber: string | null; invoiceNumber: string | null;
}
export interface MaintenanceDto {
  request: MaintenanceRow; description: string | null; reportedBy: string | null; contractorPhone: string | null; startedDate: string | null; resolution: string | null; leaseId: number | null;
  leaseCode: string | null; workOrderId: number | null; workOrderStatus: string | null; invoiceId: number | null; invoiceStatus: string | null; invoiceTotal: number | null; invoicePaid: number | null;
  invoiceOutstanding: number | null; workOrdersEnabled: boolean; canInvoiceTenant: boolean; canManage: boolean; createdByName: string | null; createdAt: string;
}
export interface MaintenancePayload {
  unitId?: number | null; tenantId?: number | null; clearTenant?: boolean; title?: string; description?: string; category?: MaintenanceCategory; priority?: MaintenancePriority;
  reportedDate?: string | null; reportedBy?: string; scheduledDate?: string | null; clearScheduled?: boolean; dueDate?: string | null; clearDue?: boolean; contractorName?: string;
  contractorPhone?: string; estimatedCost?: number | null; actualCost?: number | null; costBearer?: CostBearer;
}
export interface CompleteMaintenancePayload { completedDate?: string | null; actualCost?: number | null; resolution?: string; costBearer?: CostBearer; }
export interface MaintenanceFilter {
  q?: string; status?: string; openOnly?: boolean; priority?: string; category?: string; unitId?: number | null; propertyId?: number | null; ownerId?: number | null; tenantId?: number | null; overdueOnly?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PmMaintenanceApiService {
  private base = `${API_BASE}/propertymgmt/maintenance`;

  constructor(private http: HttpClient) {}

  private params(o: Record<string, unknown>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(o)) if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
    return p;
  }

  list(f: MaintenanceFilter): Observable<MaintenanceRow[]> { return this.http.get<MaintenanceRow[]>(this.base, { params: this.params({ ...f }) }); }
  get(id: number): Observable<MaintenanceDto> { return this.http.get<MaintenanceDto>(`${this.base}/${id}`); }
  create(p: MaintenancePayload): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(this.base, p); }
  update(id: number, p: MaintenancePayload): Observable<MaintenanceDto> { return this.http.put<MaintenanceDto>(`${this.base}/${id}`, p); }
  remove(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}`); }
  start(id: number): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/start`, {}); }
  hold(id: number): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/hold`, {}); }
  complete(id: number, p: CompleteMaintenancePayload): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/complete`, p); }
  cancel(id: number, reason: string): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/cancel`, { reason }); }
  reopen(id: number): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/reopen`, {}); }
  raiseWorkOrder(id: number): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/work-order`, {}); }
  unlinkWorkOrder(id: number): Observable<MaintenanceDto> { return this.http.delete<MaintenanceDto>(`${this.base}/${id}/work-order`); }
  invoiceTenant(id: number): Observable<MaintenanceDto> { return this.http.post<MaintenanceDto>(`${this.base}/${id}/invoice-tenant`, {}); }
}
