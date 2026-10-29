import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type WorkOrderType = 'CUSTOMER' | 'INTERNAL';
export type WorkOrderStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export type ApprovalStatus = 'NONE' | 'REQUESTED' | 'APPROVED' | 'REJECTED';
export type WorkOrderPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

export interface WorkOrderSummary {
  id: number; number: string; type: WorkOrderType; status: WorkOrderStatus; priority: WorkOrderPriority; title: string;
  customerId: number | null; customerName: string | null; assetId: number | null; assetName: string | null; packageName: string | null;
  assignedToUserId: number | null; assignedToName: string | null; scheduledDate: string | null; dueDate: string | null; overdue: boolean;
  checklistDone: number; checklistTotal: number; hours: number; invoiceId: number | null; invoiceNumber: string | null; readyToInvoice: boolean; createdAt: string;
  approvalRequired: boolean; approvalStatus: ApprovalStatus; scheduleId: number | null;
}

export interface WoLine { id: number; productId: number; productName: string; quantity: number; unitPrice: number; unitCost: number; included: boolean; lineTotal: number; }
export interface WoChecklist { id: number; seq: number; text: string; done: boolean; doneAt: string | null; doneByName: string | null; }
export interface WoCost { id: number; costDate: string; description: string; supplierName: string | null; amount: number; chargeAmount: number; expenseId: number | null; }
export interface WoMoney {
  partsCost: number; externalCost: number; totalCost: number; partsCharged: number; costsCharged: number; labourBillable: number;
  expectedRevenue: number; invoiceTotal: number | null;
}

export interface WorkOrderDetail {
  id: number; number: string; type: WorkOrderType; status: WorkOrderStatus; priority: WorkOrderPriority; title: string; description: string | null;
  itemDescription: string | null; customerId: number | null; customerName: string | null; assetId: number | null; assetName: string | null;
  packageId: number | null; packageName: string | null; fixedPrice: number; labourRate: number | null; assignedToUserId: number | null; assignedToName: string | null;
  requestedDate: string | null; scheduledDate: string | null; dueDate: string | null; startedAt: string | null; completedAt: string | null;
  timeProjectId: number | null; hours: number; billableHours: number; invoiceId: number | null; invoiceNumber: string | null;
  manufacturingOrderId: number | null; manufacturingOrderNumber: string | null; manufacturingOrderStatus: string | null; branchId: number | null;
  partsPosted: boolean; completionNote: string | null; createdByName: string | null; createdAt: string; overdue: boolean;
  canWork: boolean; canManage: boolean; readyToInvoice: boolean; lines: WoLine[]; checklist: WoChecklist[]; costs: WoCost[]; money: WoMoney;
  approvalRequired: boolean; approvalStatus: ApprovalStatus; estimatedHours: number | null; scheduleId: number | null; completionEmailedAt: string | null;
}

export interface EstimateLine { kind: 'PRICE' | 'LABOUR' | 'PART' | 'PART_INCLUDED' | 'COST'; description: string; quantity: number | null; unitPrice: number | null; amount: number; }
export interface Estimate {
  workOrderId: number; number: string; approvalRequired: boolean; status: ApprovalStatus; estimatedHours: number | null; labourRate: number | null; fixedPrice: number | null;
  lines: EstimateLine[]; total: number; totalShownToCustomer: number | null; requestedAt: string | null; decidedAt: string | null; decidedByName: string | null;
  note: string | null; recordedBy: string | null; changedSinceRequest: boolean; canStart: boolean; emailSent: boolean; emailNote: string | null;
}
export interface WoNote { id: number; authorUserId: number | null; authorName: string; text: string; createdAt: string; canDelete: boolean; }
export interface WoAttachment { id: number; fileName: string; contentType: string; sizeBytes: number; caption: string | null; uploadedByName: string | null; createdAt: string; image: boolean; canDelete: boolean; }
export interface WoHistory { at: string; who: string | null; action: string; description: string | null; }
export interface WoSettings { requireApprovalDefault: boolean; emailCustomerOnComplete: boolean; }
export type IntervalUnit = 'DAYS' | 'WEEKS' | 'MONTHS';
export interface MaintenanceSchedule {
  id: number; title: string; description: string | null; itemDescription: string | null; type: WorkOrderType; priority: WorkOrderPriority;
  customerId: number | null; customerName: string | null; assetId: number | null; assetName: string | null; packageId: number | null; packageName: string | null;
  assignedToUserId: number | null; assignedToName: string | null; fixedPrice: number | null; labourRate: number | null; branchId: number | null;
  intervalUnit: IntervalUnit; intervalCount: number; nextDueDate: string; leadDays: number; dueAfterDays: number; skipIfOpen: boolean; active: boolean;
  lastGeneratedAt: string | null; lastWorkOrderId: number | null; lastWorkOrderNumber: string | null; lastWorkOrderStatus: string | null;
  timesGenerated: number; dueNow: boolean; every: string;
}
export interface SchedulePayload {
  title: string; description?: string | null; itemDescription?: string | null; type: WorkOrderType; priority: WorkOrderPriority; customerId?: number | null;
  assetId?: number | null; packageId?: number | null; assignedToUserId?: number | null; fixedPrice?: number | null; labourRate?: number | null; branchId?: number | null;
  intervalUnit: IntervalUnit; intervalCount: number; nextDueDate: string | null; leadDays: number; dueAfterDays: number; skipIfOpen: boolean; active: boolean;
}
export interface ScheduleRunResult { created: number; skipped: number; numbers: string[]; notes: string[]; }
export type WoDocKind = 'jobcard' | 'estimate' | 'completion';

export interface WorkOrderSummaryCounts { open: number; inProgress: number; onHold: number; overdue: number; readyToInvoice: number; mine: number; }

export interface WorkOrderPayload {
  type: WorkOrderType; priority: WorkOrderPriority; title: string; description?: string | null; itemDescription?: string | null; customerId?: number | null;
  assetId?: number | null; packageId?: number | null; fixedPrice?: number | null; labourRate?: number | null; assignedToUserId?: number | null;
  requestedDate?: string | null; scheduledDate?: string | null; dueDate?: string | null; branchId?: number | null;
}

export interface WoLinePayload { productId: number; quantity: number; unitPrice: number | null; included: boolean; }
export interface WoCostPayload { costDate: string | null; description: string; supplierName?: string | null; amount: number; chargeAmount?: number | null; expenseAccountId?: number | null; }

export interface PackageSummary { id: number; code: string | null; name: string; fixedPrice: number; estimatedHours: number | null; active: boolean; steps: number; parts: number; timesUsed: number; }
export interface PackageDetail {
  id: number; code: string | null; name: string; description: string | null; fixedPrice: number; estimatedHours: number | null; active: boolean;
  items: { id: number; text: string }[]; parts: { id: number; productId: number; productName: string; quantity: number; included: boolean }[];
}
export interface PackagePayload {
  code?: string | null; name: string; description?: string | null; fixedPrice: number | null; estimatedHours?: number | null; active: boolean; items: string[];
  parts: { productId: number; quantity: number; included: boolean }[];
}

export interface InvoiceLinePreview { kind: 'FIXED' | 'PART' | 'COST' | 'LABOUR'; description: string; quantity: number; unitPrice: number; amount: number; }
export interface InvoicePreview { workOrderId: number; number: string; customerId: number | null; customerName: string | null; lines: InvoiceLinePreview[]; total: number; timeEntries: number; warning: string | null; }
export interface InvoiceResult { invoiceId: number; invoiceNumber: string; total: number; }

export interface WorkOrderFilters { status?: string; type?: string; assetId?: number | null; assigneeId?: number | null; customerId?: number | null; overdue?: boolean; q?: string; }

@Injectable({ providedIn: 'root' })
export class WorkOrderApiService {
  private base = `${API_BASE}/work-orders`;

  constructor(private http: HttpClient) {}

  list(f: WorkOrderFilters = {}): Observable<WorkOrderSummary[]> {
    let params = new HttpParams();
    if (f.status) params = params.set('status', f.status);
    if (f.type) params = params.set('type', f.type);
    if (f.assetId) params = params.set('assetId', f.assetId);
    if (f.assigneeId) params = params.set('assigneeId', f.assigneeId);
    if (f.customerId) params = params.set('customerId', f.customerId);
    if (f.overdue) params = params.set('overdue', true);
    if (f.q) params = params.set('q', f.q);
    return this.http.get<WorkOrderSummary[]>(this.base, { params });
  }
  mine(): Observable<WorkOrderSummary[]> { return this.http.get<WorkOrderSummary[]>(`${this.base}/mine`); }
  summary(): Observable<WorkOrderSummaryCounts> { return this.http.get<WorkOrderSummaryCounts>(`${this.base}/summary`); }
  get(id: number): Observable<WorkOrderDetail> { return this.http.get<WorkOrderDetail>(`${this.base}/${id}`); }
  create(p: WorkOrderPayload): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(this.base, p); }
  update(id: number, p: WorkOrderPayload): Observable<WorkOrderDetail> { return this.http.put<WorkOrderDetail>(`${this.base}/${id}`, p); }
  delete(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}`); }

  saveLines(id: number, lines: WoLinePayload[]): Observable<WorkOrderDetail> { return this.http.put<WorkOrderDetail>(`${this.base}/${id}/lines`, { lines }); }
  saveChecklist(id: number, items: string[]): Observable<WorkOrderDetail> { return this.http.put<WorkOrderDetail>(`${this.base}/${id}/checklist`, { items }); }
  toggle(id: number, itemId: number): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/checklist/${itemId}/toggle`, {}); }
  addCost(id: number, c: WoCostPayload): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/costs`, c); }
  deleteCost(id: number, costId: number): Observable<WorkOrderDetail> { return this.http.delete<WorkOrderDetail>(`${this.base}/${id}/costs/${costId}`); }

  start(id: number): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/start`, {}); }
  hold(id: number): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/hold`, {}); }
  complete(id: number, note: string | null, force: boolean): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/complete`, { note, force }); }
  reopen(id: number): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/reopen`, {}); }
  cancel(id: number): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/cancel`, {}); }

  linkManufacturing(id: number, bomId: number, quantity: number, scheduledDate: string | null): Observable<WorkOrderDetail> {
    return this.http.post<WorkOrderDetail>(`${this.base}/${id}/manufacturing-order`, { bomId, quantity, scheduledDate });
  }
  unlinkManufacturing(id: number): Observable<WorkOrderDetail> { return this.http.delete<WorkOrderDetail>(`${this.base}/${id}/manufacturing-order`); }

  invoicePreview(id: number): Observable<InvoicePreview> { return this.http.get<InvoicePreview>(`${this.base}/${id}/invoice-preview`); }
  invoice(id: number, dueDate: string | null, notes: string | null): Observable<InvoiceResult> { return this.http.post<InvoiceResult>(`${this.base}/${id}/invoice`, { dueDate, notes }); }
  releaseInvoice(id: number): Observable<WorkOrderDetail> { return this.http.post<WorkOrderDetail>(`${this.base}/${id}/release-invoice`, {}); }

  packages(activeOnly = false): Observable<PackageSummary[]> { return this.http.get<PackageSummary[]>(`${this.base}/packages`, { params: new HttpParams().set('activeOnly', activeOnly) }); }
  getPackage(id: number): Observable<PackageDetail> { return this.http.get<PackageDetail>(`${this.base}/packages/${id}`); }
  createPackage(p: PackagePayload): Observable<PackageDetail> { return this.http.post<PackageDetail>(`${this.base}/packages`, p); }
  updatePackage(id: number, p: PackagePayload): Observable<PackageDetail> { return this.http.put<PackageDetail>(`${this.base}/packages/${id}`, p); }
  deletePackage(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/packages/${id}`); }

  // ---- v1.15.0: documents, estimate / approval, notes, files, history, e-mail, reschedule, settings, schedules
  pdf(id: number, kind: WoDocKind): Observable<Blob> { return this.http.get(`${this.base}/${id}/pdf`, { params: new HttpParams().set('kind', kind), responseType: 'blob' }); }
  estimate(id: number): Observable<Estimate> { return this.http.get<Estimate>(`${this.base}/${id}/estimate`); }
  saveEstimateSettings(id: number, approvalRequired: boolean, estimatedHours: number | null): Observable<Estimate> {
    return this.http.put<Estimate>(`${this.base}/${id}/estimate`, { approvalRequired, estimatedHours });
  }
  sendEstimate(id: number, email: boolean, note: string | null): Observable<Estimate> { return this.http.post<Estimate>(`${this.base}/${id}/estimate/send`, { email, note }); }
  approveEstimate(id: number, byName: string, note: string | null): Observable<Estimate> { return this.http.post<Estimate>(`${this.base}/${id}/estimate/approve`, { byName, note }); }
  rejectEstimate(id: number, byName: string | null, note: string | null): Observable<Estimate> { return this.http.post<Estimate>(`${this.base}/${id}/estimate/reject`, { byName, note }); }

  notes(id: number): Observable<WoNote[]> { return this.http.get<WoNote[]>(`${this.base}/${id}/notes`); }
  addNote(id: number, text: string): Observable<WoNote> { return this.http.post<WoNote>(`${this.base}/${id}/notes`, { text }); }
  deleteNote(id: number, noteId: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}/notes/${noteId}`); }

  attachments(id: number): Observable<WoAttachment[]> { return this.http.get<WoAttachment[]>(`${this.base}/${id}/attachments`); }
  addAttachment(id: number, file: File, caption: string | null): Observable<WoAttachment> {
    const fd = new FormData();
    fd.append('file', file, file.name);
    if (caption) fd.append('caption', caption);
    return this.http.post<WoAttachment>(`${this.base}/${id}/attachments`, fd);
  }
  attachmentBlob(id: number, attachmentId: number): Observable<Blob> { return this.http.get(`${this.base}/${id}/attachments/${attachmentId}`, { responseType: 'blob' }); }
  deleteAttachment(id: number, attachmentId: number): Observable<void> { return this.http.delete<void>(`${this.base}/${id}/attachments/${attachmentId}`); }

  history(id: number): Observable<WoHistory[]> { return this.http.get<WoHistory[]>(`${this.base}/${id}/history`); }
  emailCompletion(id: number): Observable<{ ok: boolean; message: string }> { return this.http.post<{ ok: boolean; message: string }>(`${this.base}/${id}/email-completion`, {}); }
  reschedule(id: number, scheduledDate: string | null, dueDate: string | null, assignedToUserId: number | null, changeAssignee: boolean): Observable<WorkOrderDetail> {
    return this.http.put<WorkOrderDetail>(`${this.base}/${id}/reschedule`, { scheduledDate, dueDate, assignedToUserId, changeAssignee });
  }

  settings(): Observable<WoSettings> { return this.http.get<WoSettings>(`${this.base}/settings`); }
  saveSettings(s: WoSettings): Observable<WoSettings> { return this.http.put<WoSettings>(`${this.base}/settings`, s); }

  schedules(): Observable<MaintenanceSchedule[]> { return this.http.get<MaintenanceSchedule[]>(`${this.base}/schedules`); }
  createSchedule(p: SchedulePayload): Observable<MaintenanceSchedule> { return this.http.post<MaintenanceSchedule>(`${this.base}/schedules`, p); }
  updateSchedule(id: number, p: SchedulePayload): Observable<MaintenanceSchedule> { return this.http.put<MaintenanceSchedule>(`${this.base}/schedules/${id}`, p); }
  deleteSchedule(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/schedules/${id}`); }
  pauseSchedule(id: number): Observable<MaintenanceSchedule> { return this.http.post<MaintenanceSchedule>(`${this.base}/schedules/${id}/pause`, {}); }
  resumeSchedule(id: number): Observable<MaintenanceSchedule> { return this.http.post<MaintenanceSchedule>(`${this.base}/schedules/${id}/resume`, {}); }
  runSchedule(id: number): Observable<ScheduleRunResult> { return this.http.post<ScheduleRunResult>(`${this.base}/schedules/${id}/run`, {}); }
  runDueSchedules(): Observable<ScheduleRunResult> { return this.http.post<ScheduleRunResult>(`${this.base}/schedules/run-due`, {}); }
}
