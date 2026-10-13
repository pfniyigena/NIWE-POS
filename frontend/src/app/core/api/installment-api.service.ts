import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type InstallmentPlanStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type InterestMethod = 'NONE' | 'FLAT' | 'REDUCING';
export type InstallmentFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY';
export type InstallmentState = 'PAID' | 'PARTIAL' | 'PENDING' | 'OVERDUE';

export interface InstallmentPlanSummary {
  id: number;
  planNumber: string;
  invoiceId: number;
  invoiceNumber: string;
  customerId: number | null;
  customerName: string | null;
  branchId: number | null;
  status: InstallmentPlanStatus;
  financedAmount: number;
  totalInterest: number;
  totalPayable: number;
  paid: number;
  outstanding: number;
  overdueAmount: number;
  nextDueDate: string | null;
  nextDueAmount: number | null;
  numberOfInstallments: number;
  paidInstallments: number;
  overdueInstallments: number;
  createdAt: string;
}

export interface InstallmentRow {
  seq: number;
  dueDate: string;
  principalDue: number;
  interestDue: number;
  lateFee: number;
  lateFeeWaived: boolean;
  totalDue: number;
  paid: number;
  balance: number;
  state: InstallmentState;
  daysOverdue: number;
}

export interface InstallmentPaymentRow {
  id: number;
  paidAt: string;
  amount: number;
  principalPart: number;
  chargesPart: number;
  method: string | null;
  receiptNumber: string | null;
  note: string | null;
  recordedByName: string | null;
}

export interface InstallmentTerms {
  invoiceTotal: number;
  deposit: number;
  interestMethod: InterestMethod;
  interestRate: number;
  frequency: InstallmentFrequency;
  firstDueDate: string | null;
  graceDays: number;
  lateFeeFixed: number;
  lateFeePercent: number;
  remindersEnabled: boolean;
  wholeUnits: boolean;
  notes: string | null;
  adjustmentNote: string | null;
  createdByName: string | null;
  cancelledOn: string | null;
  cancelReason: string | null;
}

export interface InstallmentPlanDetail {
  plan: InstallmentPlanSummary;
  terms: InstallmentTerms;
  installments: InstallmentRow[];
  payments: InstallmentPaymentRow[];
}

export interface InstallmentPreview {
  invoiceTotal: number;
  alreadyPaid: number;
  outstandingBeforeDeposit: number;
  deposit: number;
  financedAmount: number;
  totalInterest: number;
  totalPayable: number;
  lines: { seq: number; dueDate: string; principalDue: number; interestDue: number; totalDue: number }[];
}

export interface InstallmentSummary {
  activeAgreements: number;
  outstandingBalance: number;
  overdueAmount: number;
  overdueInstallments: number;
  dueThisMonth: number;
  collectedThisMonth: number;
}

export interface InstallmentSettings {
  remindersEnabled: boolean;
  reminderDaysBefore: number;
  overdueReminderEveryDays: number;
  defaultWholeUnits: boolean;
  defaultInterestRate: number;
  defaultGraceDays: number;
  defaultLateFeeFixed: number;
  defaultLateFeePercent: number;
}

export interface InstallmentPlanRequest {
  invoiceId: number | null;
  deposit: number | null;
  depositMethod: string | null;
  numberOfInstallments: number | null;
  frequency: InstallmentFrequency;
  firstDueDate: string | null;
  interestMethod: InterestMethod;
  interestRate: number | null;
  graceDays: number | null;
  lateFeeFixed: number | null;
  lateFeePercent: number | null;
  remindersEnabled: boolean;
  wholeUnits: boolean | null;
  notes: string | null;
}

export interface RescheduleLine {
  dueDate: string;
  principalDue: number;
  interestDue: number;
}

/** Customer instalment plans on invoices (Installments module). */
@Injectable({ providedIn: 'root' })
export class InstallmentApiService {
  private base = `${API_BASE}/installments`;

  constructor(private http: HttpClient) {}

  plans(filter: { status?: string; customerId?: number | null } = {}): Observable<InstallmentPlanSummary[]> {
    let params = new HttpParams();
    if (filter.status) params = params.set('status', filter.status);
    if (filter.customerId) params = params.set('customerId', filter.customerId);
    return this.http.get<InstallmentPlanSummary[]>(`${this.base}/plans`, { params });
  }
  plan(id: number): Observable<InstallmentPlanDetail> {
    return this.http.get<InstallmentPlanDetail>(`${this.base}/plans/${id}`);
  }
  forInvoice(invoiceId: number): Observable<InstallmentPlanSummary[]> {
    return this.http.get<InstallmentPlanSummary[]>(`${this.base}/by-invoice/${invoiceId}`);
  }
  summary(): Observable<InstallmentSummary> {
    return this.http.get<InstallmentSummary>(`${this.base}/summary`);
  }
  settings(): Observable<InstallmentSettings> {
    return this.http.get<InstallmentSettings>(`${this.base}/settings`);
  }
  saveSettings(s: InstallmentSettings): Observable<InstallmentSettings> {
    return this.http.put<InstallmentSettings>(`${this.base}/settings`, s);
  }
  preview(r: InstallmentPlanRequest): Observable<InstallmentPreview> {
    return this.http.post<InstallmentPreview>(`${this.base}/preview`, r);
  }
  create(r: InstallmentPlanRequest): Observable<InstallmentPlanDetail> {
    return this.http.post<InstallmentPlanDetail>(`${this.base}/plans`, r);
  }
  pay(id: number, body: { amount: number; method: string; note: string | null }): Observable<InstallmentPlanDetail> {
    return this.http.post<InstallmentPlanDetail>(`${this.base}/plans/${id}/payments`, body);
  }
  cancel(id: number, reason: string | null): Observable<InstallmentPlanDetail> {
    return this.http.post<InstallmentPlanDetail>(`${this.base}/plans/${id}/cancel`, { reason });
  }
  reschedule(id: number, installments: RescheduleLine[]): Observable<InstallmentPlanDetail> {
    return this.http.put<InstallmentPlanDetail>(`${this.base}/plans/${id}/schedule`, { installments });
  }
  waiveFee(id: number, seq: number): Observable<InstallmentPlanDetail> {
    return this.http.post<InstallmentPlanDetail>(`${this.base}/plans/${id}/installments/${seq}/waive-fee`, null);
  }
  remind(id: number): Observable<{ sent: boolean; sentTo: string }> {
    return this.http.post<{ sent: boolean; sentTo: string }>(`${this.base}/plans/${id}/remind`, null);
  }
  schedulePdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/plans/${id}/schedule-pdf`, { responseType: 'blob' });
  }
}
