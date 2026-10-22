import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { DashboardSummary } from '../models';

export interface AttentionItem {
  key: string;
  label: string;
  count: number;
  amount: number | null;
}
export interface DayPoint {
  date: string;
  posCount: number;
  pos: number;
  invoiceCount: number;
  invoiced: number;
}
export interface SalesComparison {
  today: number;
  sameDayLastWeek: number;
  last7: number;
  previous7: number;
  todayCount: number;
  sameDayLastWeekCount: number;
}
export interface OwingRow {
  customerId: number;
  name: string;
  outstanding: number;
  overdue: number;
  oldestDaysOverdue: number;
}
export interface OwingBlock {
  totalOutstanding: number;
  totalOverdue: number;
  overdueItems: number;
  customersOwing: number;
  customersOverdue: number;
  top: OwingRow[];
}
export interface UnpaidBillRow {
  billId: number;
  billNumber: string;
  supplierName: string;
  outstanding: number;
  ageDays: number;
}
export interface BillsBlock {
  totalUnpaid: number;
  unpaidBills: number;
  oldest: UnpaidBillRow[];
}
export interface TopSeller {
  productId: number;
  name: string;
  sku: string;
  quantity: number;
  revenue: number;
}
export interface DashboardOverview {
  today: string;
  attention: AttentionItem[];
  days: DayPoint[];
  comparison: SalesComparison;
  owing: OwingBlock;
  bills: BillsBlock;
  topSellers: TopSeller[];
  topSellersDays: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  constructor(private http: HttpClient) {}

  /** `branchId` (null/omitted = every branch) narrows today's sales figures to one branch; stock, customers and sessions stay business-wide. */
  summary(branchId: number | null = null): Observable<DashboardSummary> {
    const params: Record<string, string> = branchId != null ? { branchId: String(branchId) } : {};
    return this.http.get<DashboardSummary>(`${API_BASE}/dashboard/summary`, { params });
  }

  /** Things to act on, sales against last week, who owes, bills to pay, top sellers. A branch narrows till sales and collections only. */
  overview(branchId: number | null = null): Observable<DashboardOverview> {
    const params: Record<string, string> = branchId != null ? { branchId: String(branchId) } : {};
    return this.http.get<DashboardOverview>(`${API_BASE}/dashboard/overview`, { params });
  }
}
