import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import {
  AssetsReport,
  BalanceSheet,
  CashFlow,
  CostCenterActivity,
  ExpenseIncomeGroupBy,
  FinancialTransaction,
  GeneralLedgerAccount,
  GroupedAmount,
  JournalEntry,
  ProfitAndLoss,
  TaxSummary,
  TrialBalanceRow,
} from '../models';

/** Admin > Accounting > Reports — the original four standard ledger reports, plus the later Accounting/Journals/Expenses/Incomes report batch. Admin-only. */
@Injectable({ providedIn: 'root' })
export class AccountingReportApiService {
  constructor(private http: HttpClient) {}

  /** Every account's own ledger, back to back — the Reports menu's "General Ledger" (Fourier: "General ledger ... add them"). */
  generalLedger(from: string | null, to: string | null): Observable<GeneralLedgerAccount[]> {
    return this.http.get<GeneralLedgerAccount[]>(`${API_BASE}/accounting/reports/general-ledger`, { params: this.range(from, to) });
  }

  /** Backs Trial Balance, Trial Nets and Trial Balance & Nets alike — same data, different columns shown. */
  trialBalance(from: string | null, to: string | null): Observable<TrialBalanceRow[]> {
    return this.http.get<TrialBalanceRow[]>(`${API_BASE}/accounting/reports/trial-balance`, { params: this.range(from, to) });
  }

  /** `branchId` (null/omitted = every branch) gives that branch's own Profit & Loss. */
  profitAndLoss(from: string | null, to: string | null, branchId: number | null = null): Observable<ProfitAndLoss> {
    const params = this.range(from, to);
    if (branchId != null) params['branchId'] = String(branchId);
    return this.http.get<ProfitAndLoss>(`${API_BASE}/accounting/reports/profit-and-loss`, { params });
  }

  /** A snapshot as of one date — defaults (server-side) to today when omitted. */
  balanceSheet(asOf: string | null): Observable<BalanceSheet> {
    const params: Record<string, string> = {};
    if (asOf) params['asOf'] = asOf;
    return this.http.get<BalanceSheet>(`${API_BASE}/accounting/reports/balance-sheet`, { params });
  }

  financialTransactions(from: string | null, to: string | null): Observable<FinancialTransaction[]> {
    return this.http.get<FinancialTransaction[]>(`${API_BASE}/accounting/reports/financial-transactions`, { params: this.range(from, to) });
  }

  journals(from: string | null, to: string | null): Observable<JournalEntry[]> {
    return this.http.get<JournalEntry[]>(`${API_BASE}/accounting/reports/journals`, { params: this.range(from, to) });
  }

  costCenterActivity(from: string | null, to: string | null): Observable<CostCenterActivity[]> {
    return this.http.get<CostCenterActivity[]>(`${API_BASE}/accounting/reports/cost-centers`, { params: this.range(from, to) });
  }

  cashFlow(from: string | null, to: string | null): Observable<CashFlow> {
    return this.http.get<CashFlow>(`${API_BASE}/accounting/reports/cash-flow`, { params: this.range(from, to) });
  }

  /** Backs both the Tax Report and Tax Declaration menu items. */
  taxSummary(from: string | null, to: string | null): Observable<TaxSummary> {
    return this.http.get<TaxSummary>(`${API_BASE}/accounting/reports/tax-summary`, { params: this.range(from, to) });
  }

  assets(asOf: string | null): Observable<AssetsReport> {
    const params: Record<string, string> = {};
    if (asOf) params['asOf'] = asOf;
    return this.http.get<AssetsReport>(`${API_BASE}/accounting/reports/assets`, { params });
  }

  /** Backs all eight "Expenses ..." segmented/periodic report menu items — see the backend AccountingReportService.expensesGrouped javadoc. */
  expensesGrouped(from: string | null, to: string | null, groupBy: ExpenseIncomeGroupBy): Observable<GroupedAmount[]> {
    return this.http.get<GroupedAmount[]>(`${API_BASE}/accounting/reports/expenses`, { params: { ...this.range(from, to), groupBy } });
  }

  /** Backs all eight "Incomes ..." segmented/periodic report menu items. */
  incomesGrouped(from: string | null, to: string | null, groupBy: ExpenseIncomeGroupBy): Observable<GroupedAmount[]> {
    return this.http.get<GroupedAmount[]>(`${API_BASE}/accounting/reports/incomes`, { params: { ...this.range(from, to), groupBy } });
  }

  private range(from: string | null, to: string | null): Record<string, string> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return params;
  }

  /**
   * Same filters as any of the GET methods above, but as a downloadable file instead of JSON
   * (Fourier: "Export to Excel and Pdf") — one generic method for every accounting report rather
   * than one download method per endpoint, mirroring ReportApiService.download's own shape for
   * the POS Reports package. See the backend AccountingReportController's own {@code format}
   * handling for what each endpoint puts in the file.
   */
  download(endpoint: string, params: Record<string, string>, format: 'xlsx' | 'pdf'): Observable<Blob> {
    return this.http.get(`${API_BASE}/accounting/reports/${endpoint}`, { params: { ...params, format }, responseType: 'blob' });
  }
}
