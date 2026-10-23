import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { ListFilter, pagedList } from './list-state';
import { PageResult } from './paged';

export type EmployeeStatus = 'ACTIVE' | 'INACTIVE' | 'TERMINATED';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CASUAL' | 'INTERN';
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type AttendanceStatus = 'PRESENT' | 'LATE' | 'ABSENT';
export type ContractType = 'PERMANENT' | 'FIXED_TERM' | 'PROBATION' | 'CASUAL' | 'INTERNSHIP';
export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'EXPIRED' | 'TERMINATED';
export type ComponentKind = 'EARNING' | 'DEDUCTION';
export type RuleKind = 'EMPLOYEE_DEDUCTION' | 'EMPLOYER_CONTRIBUTION';
export type RuleBase = 'BASIC' | 'GROSS' | 'NET';
export type PayableTarget = 'SOCIAL_SECURITY' | 'OTHER';
export type PayrollStatus = 'DRAFT' | 'APPROVED' | 'PAID';
export type ItemKind = 'EARNING' | 'DEDUCTION' | 'TAX' | 'EMPLOYER';

export interface Employee {
  id: number;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  dateOfBirth: string | null;
  nationalId: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  hireDate: string;
  terminationDate: string | null;
  terminationReason: string | null;
  status: EmployeeStatus;
  employmentType: EmploymentType;
  jobTitle: string | null;
  departmentId: number | null;
  departmentName: string | null;
  positionId: number | null;
  positionTitle: string | null;
  managerId: number | null;
  managerName: string | null;
  branchId: number | null;
  branchName: string | null;
  userId: number | null;
  userLabel: string | null;
  bankName: string | null;
  bankAccount: string | null;
  taxNumber: string | null;
  socialSecurityNumber: string | null;
  attendanceTracked: boolean;
  notes: string | null;
}

export interface EmployeePayload {
  employeeNumber: string | null;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  nationalId: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  hireDate: string;
  employmentType: EmploymentType;
  jobTitle: string | null;
  departmentId: number | null;
  positionId: number | null;
  managerId: number | null;
  branchId: number | null;
  userId: number | null;
  bankName: string | null;
  bankAccount: string | null;
  taxNumber: string | null;
  socialSecurityNumber: string | null;
  attendanceTracked: boolean;
  notes: string | null;
}

export interface StaffLogin {
  id: number;
  username: string;
  fullName: string | null;
}

export interface Department {
  id: number;
  name: string;
  code: string | null;
  parentId: number | null;
  parentName: string | null;
  managerId: number | null;
  managerName: string | null;
  active: boolean;
  employeeCount: number;
}

export interface DepartmentPayload {
  name: string;
  code: string | null;
  parentId: number | null;
  managerId: number | null;
  active: boolean;
}

export interface Position {
  id: number;
  title: string;
  departmentId: number | null;
  departmentName: string | null;
  description: string | null;
  plannedHeadcount: number;
  active: boolean;
  filled: number;
}

export interface PositionPayload {
  title: string;
  departmentId: number | null;
  description: string | null;
  plannedHeadcount: number;
  active: boolean;
}

export interface DeptNode {
  id: number;
  name: string;
  managerName: string | null;
  employeeCount: number;
  children: DeptNode[];
}

export interface PersonNode {
  id: number;
  name: string;
  jobTitle: string | null;
  departmentName: string | null;
  reports: PersonNode[];
}

export interface OrgChart {
  departments: DeptNode[];
  people: PersonNode[];
}

export type LeaveAccrual = 'UPFRONT' | 'MONTHLY';

export interface LeaveType {
  id: number;
  name: string;
  daysPerYear: number;
  paid: boolean;
  active: boolean;
  accrualMode: LeaveAccrual;
  carryOverMaxDays: number;
  payoutOnExit: boolean;
}

export interface LeaveTypePayload {
  name: string;
  daysPerYear: number;
  paid: boolean;
  active: boolean;
  accrualMode: LeaveAccrual;
  carryOverMaxDays: number;
  payoutOnExit: boolean;
}

export interface Holiday {
  id: number;
  date: string;
  name: string;
}

export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  leaveTypeId: number;
  leaveTypeName: string;
  paid: boolean;
  startDate: string;
  endDate: string;
  days: number;
  startHalf: boolean;
  endHalf: boolean;
  reason: string | null;
  status: LeaveStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface LeaveRequestPayload {
  employeeId: number | null;
  leaveTypeId: number;
  startDate: string;
  endDate: string;
  reason: string | null;
  startHalfDay?: boolean;
  endHalfDay?: boolean;
}

export interface LeaveCalendarDay {
  date: string;
  weekend: boolean;
  holiday: string | null;
}

export interface LeaveCalendarEntry {
  date: string;
  requestId: number;
  leaveTypeName: string;
  status: LeaveStatus;
  half: boolean;
}

export interface LeaveCalendarRow {
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  departmentName: string | null;
  entries: LeaveCalendarEntry[];
}

export interface LeaveCalendar {
  year: number;
  month: number;
  days: LeaveCalendarDay[];
  rows: LeaveCalendarRow[];
}

export interface LeaveBalance {
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  leaveTypeId: number;
  leaveTypeName: string;
  unlimited: boolean;
  entitled: number;
  used: number;
  pending: number;
  remaining: number;
  accrued: number;
  carriedOver: number;
  accrualMode: LeaveAccrual;
}

export interface Attendance {
  id: number;
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  workDate: string;
  clockIn: string | null;
  clockOut: string | null;
  status: AttendanceStatus;
  hoursWorked: number;
  overtimeHours: number;
  note: string | null;
}

export interface AttendancePayload {
  employeeId: number;
  workDate: string;
  clockIn: string | null;
  clockOut: string | null;
  status: AttendanceStatus | null;
  note: string | null;
}

export interface AttendanceSummary {
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  departmentName: string | null;
  expectedDays: number;
  presentDays: number;
  lateDays: number;
  absentDays: number;
  leaveDays: number;
  hoursWorked: number;
  overtimeHours: number;
}

export interface TodayStatus {
  linked: boolean;
  employeeId: number | null;
  employeeName: string | null;
  record: Attendance | null;
}

export interface MyProfile {
  id: number;
  employeeNumber: string;
  fullName: string;
  jobTitle: string | null;
  departmentName: string | null;
  managerName: string | null;
  hireDate: string;
  status: EmployeeStatus;
  phone: string | null;
  email: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
}

export interface MyOverview {
  linked: boolean;
  profile: MyProfile | null;
  year: number;
  balances: LeaveBalance[];
  pendingRequests: number;
  payslipCount: number;
  today: TodayStatus;
}

export interface MyPayslipRow {
  runId: number;
  year: number;
  month: number;
  status: PayrollStatus;
  paidDate: string | null;
  grossPay: number;
  payeTax: number;
  employeeContributions: number;
  otherDeductions: number;
  netPay: number;
}

export interface MyPayslip {
  runId: number;
  year: number;
  month: number;
  status: PayrollStatus;
  paidDate: string | null;
  line: PayrollLine;
  items: PayrollItem[];
}

export interface MyContactPayload {
  phone: string | null;
  email: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
}

export interface HrSettings {
  workStart: string;
  graceMinutes: number;
  standardDailyHours: number;
  workingDays: string;
  overtimeMultiplier: number;
  taxBandsAnnual: boolean;
  payrollCountry: string;
}

export interface HrSettingsPayload {
  workStart: string;
  graceMinutes: number;
  standardDailyHours: number;
  workingDays: string;
  overtimeMultiplier: number;
}

export interface ContractComponent {
  id?: number;
  name: string;
  kind: ComponentKind;
  amount: number;
  taxable: boolean;
}

export interface Contract {
  id: number;
  contractNumber: string;
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  type: ContractType;
  status: ContractStatus;
  startDate: string;
  endDate: string | null;
  jobTitle: string | null;
  baseSalary: number;
  terminationDate: string | null;
  terminationReason: string | null;
  renewsContractNumber: string | null;
  notes: string | null;
  daysToExpiry: number | null;
  components: ContractComponent[];
}

export interface ContractPayload {
  employeeId: number;
  type: ContractType;
  startDate: string;
  endDate: string | null;
  jobTitle: string | null;
  baseSalary: number;
  notes: string | null;
  components: ContractComponent[];
}

export interface PayrollRule {
  id: number;
  code: string;
  name: string;
  kind: RuleKind;
  base: RuleBase;
  ratePercent: number;
  capAmount: number | null;
  deductibleFromTax: boolean;
  target: PayableTarget;
  active: boolean;
  sortOrder: number;
}

export interface PayrollRulePayload {
  code: string | null;
  name: string;
  kind: RuleKind;
  base: RuleBase;
  ratePercent: number;
  capAmount: number | null;
  deductibleFromTax: boolean;
  target: PayableTarget;
  active: boolean;
  sortOrder: number | null;
}

export interface TaxBand {
  id?: number;
  fromAmount: number;
  toAmount: number | null;
  ratePercent: number;
}

export interface PayrollRun {
  id: number;
  periodYear: number;
  periodMonth: number;
  status: PayrollStatus;
  employeeCount: number;
  totalGross: number;
  totalPaye: number;
  totalEmployeeContributions: number;
  totalOtherDeductions: number;
  totalNet: number;
  totalEmployerContributions: number;
  createdByName: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  paidDate: string | null;
  note: string | null;
}

export interface PayrollLine {
  id: number;
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  departmentName: string | null;
  contractNumber: string | null;
  branchId: number | null;
  bankName: string | null;
  bankAccount: string | null;
  baseSalary: number;
  daysInMonth: number;
  payableDays: number;
  unpaidLeaveDays: number;
  absentDays: number;
  overtimeHours: number;
  grossPay: number;
  taxablePay: number;
  payeTax: number;
  employeeContributions: number;
  otherDeductions: number;
  netPay: number;
  employerContributions: number;
}

export interface PayrollRunDetail {
  run: PayrollRun;
  lines: PayrollLine[];
  warnings: string[];
}

export interface PayrollItem {
  kind: ItemKind;
  name: string;
  amount: number;
  taxable: boolean;
}

export interface Payslip {
  run: PayrollRun;
  line: PayrollLine;
  bankName: string | null;
  bankAccount: string | null;
  items: PayrollItem[];
}

export interface PayslipEmailResult {
  sent: number;
  noEmail: string[];
  failed: string[];
}


export type AdjustmentKind = 'EARNING' | 'DEDUCTION';
export interface PayrollAdjustment {
  id: number;
  employeeId: number;
  employeeNumber: string | null;
  employeeName: string;
  kind: AdjustmentKind;
  name: string;
  amount: number;
  taxable: boolean;
  year: number;
  month: number;
  note: string | null;
  createdByName: string | null;
  status: 'PENDING' | 'APPLIED';
  draftRunId: number | null;
}
export interface AdjustmentPayload {
  employeeId: number | null;
  kind: AdjustmentKind;
  name: string;
  amount: number | null;
  taxable: boolean;
  year: number;
  month: number;
  note: string | null;
}

export type LoanType = 'ADVANCE' | 'LOAN';
export type LoanStatus = 'ACTIVE' | 'PAID_OFF' | 'CANCELLED';
export interface StaffLoan {
  id: number;
  loanNumber: string;
  employeeId: number;
  employeeNumber: string | null;
  employeeName: string;
  loanType: LoanType;
  principal: number;
  installment: number;
  issueDate: string;
  firstYear: number;
  firstMonth: number;
  status: LoanStatus;
  repaid: number;
  balance: number;
  installmentsLeft: number | null;
  note: string | null;
  createdByName: string | null;
}
export interface LoanRepayment {
  id: number;
  amount: number;
  repaidOn: string;
  source: 'PAYROLL' | 'CASH' | 'WRITE_OFF';
  runId: number | null;
  note: string | null;
  createdByName: string | null;
}
export interface StaffLoanDetail {
  loan: StaffLoan;
  repayments: LoanRepayment[];
}
export interface LoanPayload {
  employeeId: number | null;
  loanType: LoanType;
  principal: number | null;
  installment: number | null;
  issueDate: string | null;
  firstYear: number | null;
  firstMonth: number | null;
  note: string | null;
}


export type RemittanceKind = 'PAYE' | 'SOCIAL_SECURITY' | 'OTHER';
export interface RemittanceRow {
  year: number;
  month: number;
  runId: number;
  runStatus: PayrollStatus;
  kind: RemittanceKind;
  kindLabel: string;
  owed: number;
  paid: number;
  outstanding: number;
  dueDate: string;
  overdue: boolean;
}
export interface RemittanceKindTotal {
  kind: RemittanceKind;
  kindLabel: string;
  owed: number;
  paid: number;
  outstanding: number;
  overdue: number;
}
export interface RemittanceSummary {
  totals: RemittanceKindTotal[];
  rows: RemittanceRow[];
}
export interface Remittance {
  id: number;
  kind: RemittanceKind;
  kindLabel: string;
  year: number;
  month: number;
  amount: number;
  paidOn: string;
  reference: string | null;
  paidTo: string | null;
  note: string | null;
  status: 'ACTIVE' | 'VOIDED';
  voidNote: string | null;
}
export interface RemittancePayload {
  kind: RemittanceKind;
  year: number;
  month: number;
  amount: number | null;
  paidOn: string | null;
  reference: string | null;
  paidTo: string | null;
  note: string | null;
}


// ---- payroll corrections and final pay (HR-8) ----
export interface PayrollEvent { kind: 'PAYMENT_CANCELLED' | 'REOPENED'; note: string | null; byName: string | null; at: string; }
export interface FinalPayLine { name: string; kind: 'EARNING' | 'DEDUCTION'; days: number | null; amount: number; note: string | null; }
export interface FinalPay {
  employeeId: number; employeeNumber: string; employeeName: string; lastDay: string | null; year: number; month: number; dailyRate: number; baseSalary: number;
  workingDays: number; leaveDays: number; suggestedLeaveDays: number; leave: { leaveTypeName: string; remaining: number }[];
  noticePayDays: number; noticeRecoverDays: number; settleLoans: boolean; loans: { loanId: number; loanNumber: string; balance: number }[]; loansBalance: number;
  lines: FinalPayLine[]; earnings: number; deductions: number; applied: boolean; runStatus: 'DRAFT' | 'APPROVED' | 'PAID' | null; blocked: string | null; note: string | null;
}
export interface FinalPayPayload { leaveDays: number | null; noticePayDays: number | null; noticeRecoverDays: number | null; settleLoans: boolean; note: string | null; }

// ---- documents, checklists, import (HR-7) ----
export type DocCategory = 'CONTRACT' | 'ID_DOCUMENT' | 'QUALIFICATION' | 'PERMIT' | 'PAYROLL' | 'DISCIPLINARY' | 'OTHER';
export const DOC_CATEGORIES: { value: DocCategory; label: string }[] = [
  { value: 'CONTRACT', label: 'Contract' },
  { value: 'ID_DOCUMENT', label: 'ID document' },
  { value: 'QUALIFICATION', label: 'Qualification' },
  { value: 'PERMIT', label: 'Permit' },
  { value: 'PAYROLL', label: 'Payroll' },
  { value: 'DISCIPLINARY', label: 'Disciplinary' },
  { value: 'OTHER', label: 'Other' },
];
export function docCategoryLabel(c: string): string {
  return DOC_CATEGORIES.find((x) => x.value === c)?.label ?? c;
}
export interface EmployeeDocument {
  id: number;
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  name: string;
  category: DocCategory;
  description: string | null;
  expiryDate: string | null;
  expiryState: 'NONE' | 'OK' | 'SOON' | 'EXPIRED';
  daysToExpiry: number | null;
  sharedWithEmployee: boolean;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string | null;
  createdAt: string;
}
export interface DocumentSummary { total: number; expired: number; expiringSoon: number; }
export interface DocumentUploadPayload { name: string; category: DocCategory; description: string; expiryDate: string; shared: boolean; }
export interface DocumentUpdatePayload { name: string; category: DocCategory; description: string | null; expiryDate: string | null; clearExpiry: boolean; sharedWithEmployee: boolean; }

export type ChecklistKind = 'ONBOARDING' | 'OFFBOARDING';
export interface ChecklistTemplateItem { id?: number; sortOrder?: number; title: string; responsible: string; dueOffsetDays: number; }
export interface ChecklistTemplate { id: number; name: string; kind: ChecklistKind; description: string | null; active: boolean; items: ChecklistTemplateItem[]; }
export interface ChecklistTemplatePayload { name: string; kind: ChecklistKind; description: string | null; active: boolean; items: { title: string; responsible: string; dueOffsetDays: number }[]; }
export interface ChecklistItem { id: number; sortOrder: number; title: string; responsible: string | null; dueDate: string | null; done: boolean; doneAt: string | null; doneByName: string | null; note: string | null; overdue: boolean; }
export interface EmployeeChecklist {
  id: number; employeeId: number; employeeNumber: string; employeeName: string; name: string; kind: ChecklistKind; startDate: string;
  total: number; done: number; complete: boolean; createdByName: string | null; items: ChecklistItem[];
}
export interface OpenTask {
  itemId: number; checklistId: number; employeeId: number; employeeNumber: string; employeeName: string; checklistName: string; kind: ChecklistKind;
  title: string; responsible: string | null; dueDate: string | null; overdue: boolean; note: string | null;
}
export interface EmployeeImportRow { row: number; label: string; message: string; }
export interface EmployeeImportResult {
  dryRun: boolean; fileName: string; rows: number; ok: number; skipped: number; problems: EmployeeImportRow[]; notes: EmployeeImportRow[];
  sample: string[]; columns: string[]; ignoredColumns: string[]; message: string;
}

/** Employees, organisation, attendance & leaves, contracts and payroll (/api/hr). */
/** A login just given to an employee; the password is shown only this once. */
export interface EmployeeLoginCreated {
  employeeId: number;
  employeeNumber: string;
  employeeName: string;
  username: string;
  password: string;
}

export interface EmployeeLoginBulk {
  created: EmployeeLoginCreated[];
  skipped: string[];
}

@Injectable({ providedIn: 'root' })
export class HrApiService {
  private base = `${API_BASE}/hr`;

  constructor(private http: HttpClient) {}

  /** One page of an HR list (employees, leaves, attendance, contracts, payroll/runs) with the screen's search, status, dates and branch. */
  page<T>(path: string, f: ListFilter, page: number, size: number, extra: Record<string, string | number | boolean | null | undefined> = {}): Observable<PageResult<T>> {
    return pagedList<T>(this.http, `${this.base}/${path}`, f, page, size, extra);
  }

  // employees
  employees(filter: { status?: string; departmentId?: number | null; branchId?: number | null; q?: string } = {}): Observable<Employee[]> {
    let params = new HttpParams();
    if (filter.status) params = params.set('status', filter.status);
    if (filter.departmentId) params = params.set('departmentId', filter.departmentId);
    if (filter.branchId) params = params.set('branchId', filter.branchId);
    if (filter.q) params = params.set('q', filter.q);
    return this.http.get<Employee[]>(`${this.base}/employees`, { params });
  }
  employee(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.base}/employees/${id}`);
  }
  createEmployee(p: EmployeePayload): Observable<Employee> {
    return this.http.post<Employee>(`${this.base}/employees`, p);
  }
  updateEmployee(id: number, p: EmployeePayload): Observable<Employee> {
    return this.http.put<Employee>(`${this.base}/employees/${id}`, p);
  }
  terminateEmployee(id: number, date: string, reason: string | null): Observable<Employee> {
    return this.http.post<Employee>(`${this.base}/employees/${id}/terminate`, { date, reason });
  }
  setEmployeeStatus(id: number, status: EmployeeStatus): Observable<Employee> {
    return this.http.post<Employee>(`${this.base}/employees/${id}/status`, null, { params: { status } });
  }
  deleteEmployee(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/employees/${id}`);
  }
  staffLogins(): Observable<StaffLogin[]> {
    return this.http.get<StaffLogin[]>(`${this.base}/staff-logins`);
  }

  // organisation
  departments(): Observable<Department[]> {
    return this.http.get<Department[]>(`${this.base}/departments`);
  }
  createDepartment(p: DepartmentPayload): Observable<Department> {
    return this.http.post<Department>(`${this.base}/departments`, p);
  }
  updateDepartment(id: number, p: DepartmentPayload): Observable<Department> {
    return this.http.put<Department>(`${this.base}/departments/${id}`, p);
  }
  deleteDepartment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/departments/${id}`);
  }
  positions(): Observable<Position[]> {
    return this.http.get<Position[]>(`${this.base}/positions`);
  }
  createPosition(p: PositionPayload): Observable<Position> {
    return this.http.post<Position>(`${this.base}/positions`, p);
  }
  updatePosition(id: number, p: PositionPayload): Observable<Position> {
    return this.http.put<Position>(`${this.base}/positions/${id}`, p);
  }
  deletePosition(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/positions/${id}`);
  }
  orgChart(): Observable<OrgChart> {
    return this.http.get<OrgChart>(`${this.base}/org-chart`);
  }

  // leave
  leaveTypes(): Observable<LeaveType[]> {
    return this.http.get<LeaveType[]>(`${this.base}/leave-types`);
  }
  createLeaveType(p: LeaveTypePayload): Observable<LeaveType> {
    return this.http.post<LeaveType>(`${this.base}/leave-types`, p);
  }
  updateLeaveType(id: number, p: LeaveTypePayload): Observable<LeaveType> {
    return this.http.put<LeaveType>(`${this.base}/leave-types/${id}`, p);
  }
  deleteLeaveType(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/leave-types/${id}`);
  }
  holidays(): Observable<Holiday[]> {
    return this.http.get<Holiday[]>(`${this.base}/holidays`);
  }
  createHoliday(date: string, name: string): Observable<Holiday> {
    return this.http.post<Holiday>(`${this.base}/holidays`, { date, name });
  }
  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/holidays/${id}`);
  }
  leaves(filter: { status?: string; employeeId?: number | null; from?: string; to?: string } = {}): Observable<LeaveRequest[]> {
    let params = new HttpParams();
    if (filter.status) params = params.set('status', filter.status);
    if (filter.employeeId) params = params.set('employeeId', filter.employeeId);
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    return this.http.get<LeaveRequest[]>(`${this.base}/leaves`, { params });
  }
  leaveWorkingDays(from: string, to: string, startHalf = false, endHalf = false): Observable<number> {
    return this.http.get<number>(`${this.base}/leaves/working-days`, { params: { from, to, startHalf, endHalf } });
  }
  leaveCalendar(year: number, month: number, departmentId?: number | null, branchId?: number | null): Observable<LeaveCalendar> {
    let params = new HttpParams().set('year', year).set('month', month);
    if (departmentId) params = params.set('departmentId', departmentId);
    if (branchId) params = params.set('branchId', branchId);
    return this.http.get<LeaveCalendar>(`${this.base}/leaves/calendar`, { params });
  }
  requestLeave(p: LeaveRequestPayload): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/leaves`, p);
  }
  decideLeave(id: number, action: 'approve' | 'reject' | 'cancel', note: string | null): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/leaves/${id}/${action}`, { note });
  }
  leaveBalances(year: number, employeeId?: number | null): Observable<LeaveBalance[]> {
    let params = new HttpParams().set('year', year);
    if (employeeId) params = params.set('employeeId', employeeId);
    return this.http.get<LeaveBalance[]>(`${this.base}/leave-balances`, { params });
  }

  // attendance
  attendance(from: string, to: string, employeeId?: number | null, departmentId?: number | null): Observable<Attendance[]> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (employeeId) params = params.set('employeeId', employeeId);
    if (departmentId) params = params.set('departmentId', departmentId);
    return this.http.get<Attendance[]>(`${this.base}/attendance`, { params });
  }
  attendanceSummary(from: string, to: string, employeeId?: number | null, departmentId?: number | null): Observable<AttendanceSummary[]> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (employeeId) params = params.set('employeeId', employeeId);
    if (departmentId) params = params.set('departmentId', departmentId);
    return this.http.get<AttendanceSummary[]>(`${this.base}/attendance/summary`, { params });
  }
  today(): Observable<TodayStatus> {
    return this.http.get<TodayStatus>(`${this.base}/attendance/today`);
  }
  // employee self-service ("My HR"): always the signed-in user's own record
  myOverview(): Observable<MyOverview> {
    return this.http.get<MyOverview>(`${this.base}/me`);
  }
  myUpdateContact(p: MyContactPayload): Observable<MyProfile> {
    return this.http.put<MyProfile>(`${this.base}/me/contact`, p);
  }
  myPayslips(year?: number | null): Observable<MyPayslipRow[]> {
    let params = new HttpParams();
    if (year) params = params.set('year', year);
    return this.http.get<MyPayslipRow[]>(`${this.base}/me/payslips`, { params });
  }
  myPayslip(runId: number): Observable<MyPayslip> {
    return this.http.get<MyPayslip>(`${this.base}/me/payslips/${runId}`);
  }
  myLeaveTypes(): Observable<LeaveType[]> {
    return this.http.get<LeaveType[]>(`${this.base}/me/leave-types`);
  }
  myLeaveBalances(year: number): Observable<LeaveBalance[]> {
    return this.http.get<LeaveBalance[]>(`${this.base}/me/leave-balances`, { params: { year } });
  }
  myLeaves(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(`${this.base}/me/leaves`);
  }
  myWorkingDays(from: string, to: string, startHalf = false, endHalf = false): Observable<number> {
    return this.http.get<number>(`${this.base}/me/working-days`, { params: { from, to, startHalf, endHalf } });
  }
  myRequestLeave(p: LeaveRequestPayload): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/me/leaves`, p);
  }
  myCancelLeave(id: number, note: string | null): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/me/leaves/${id}/cancel`, { note });
  }
  myAttendance(from: string, to: string): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.base}/me/attendance`, { params: { from, to } });
  }
  myClockIn(): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.base}/me/attendance/clock-in`, {});
  }
  myClockOut(): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.base}/me/attendance/clock-out`, {});
  }
  clockIn(): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.base}/attendance/clock-in`, null);
  }
  clockOut(): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.base}/attendance/clock-out`, null);
  }
  saveAttendance(p: AttendancePayload): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.base}/attendance`, p);
  }
  deleteAttendance(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/attendance/${id}`);
  }

  // settings
  settings(): Observable<HrSettings> {
    return this.http.get<HrSettings>(`${this.base}/settings`);
  }
  updateSettings(p: HrSettingsPayload): Observable<HrSettings> {
    return this.http.put<HrSettings>(`${this.base}/settings`, p);
  }

  // contracts
  contracts(filter: { status?: string; employeeId?: number | null } = {}): Observable<Contract[]> {
    let params = new HttpParams();
    if (filter.status) params = params.set('status', filter.status);
    if (filter.employeeId) params = params.set('employeeId', filter.employeeId);
    return this.http.get<Contract[]>(`${this.base}/contracts`, { params });
  }
  expiringContracts(days = 60): Observable<Contract[]> {
    return this.http.get<Contract[]>(`${this.base}/contracts/expiring`, { params: { days } });
  }
  contract(id: number): Observable<Contract> {
    return this.http.get<Contract>(`${this.base}/contracts/${id}`);
  }
  createContract(p: ContractPayload): Observable<Contract> {
    return this.http.post<Contract>(`${this.base}/contracts`, p);
  }
  updateContract(id: number, p: ContractPayload): Observable<Contract> {
    return this.http.put<Contract>(`${this.base}/contracts/${id}`, p);
  }
  activateContract(id: number): Observable<Contract> {
    return this.http.post<Contract>(`${this.base}/contracts/${id}/activate`, null);
  }
  terminateContract(id: number, date: string, reason: string | null): Observable<Contract> {
    return this.http.post<Contract>(`${this.base}/contracts/${id}/terminate`, { date, reason });
  }
  renewContract(id: number, startDate: string, endDate: string | null, baseSalary: number | null): Observable<Contract> {
    return this.http.post<Contract>(`${this.base}/contracts/${id}/renew`, { startDate, endDate, baseSalary });
  }
  deleteContract(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/contracts/${id}`);
  }

  // payroll
  payrollRules(): Observable<PayrollRule[]> {
    return this.http.get<PayrollRule[]>(`${this.base}/payroll/rules`);
  }
  createPayrollRule(p: PayrollRulePayload): Observable<PayrollRule> {
    return this.http.post<PayrollRule>(`${this.base}/payroll/rules`, p);
  }
  updatePayrollRule(id: number, p: PayrollRulePayload): Observable<PayrollRule> {
    return this.http.put<PayrollRule>(`${this.base}/payroll/rules/${id}`, p);
  }
  deletePayrollRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/payroll/rules/${id}`);
  }
  loadPayrollDefaults(): Observable<PayrollRule[]> {
    return this.http.post<PayrollRule[]>(`${this.base}/payroll/rules/load-defaults`, null);
  }
  taxBands(): Observable<TaxBand[]> {
    return this.http.get<TaxBand[]>(`${this.base}/payroll/bands`);
  }
  saveTaxBands(bands: TaxBand[]): Observable<TaxBand[]> {
    return this.http.put<TaxBand[]>(`${this.base}/payroll/bands`, { bands });
  }
  payrollSettings(): Observable<HrSettings> {
    return this.http.get<HrSettings>(`${this.base}/payroll/settings`);
  }
  setTaxMode(annual: boolean): Observable<HrSettings> {
    return this.http.put<HrSettings>(`${this.base}/payroll/tax-mode`, null, { params: { annual } });
  }
  payrollRuns(): Observable<PayrollRun[]> {
    return this.http.get<PayrollRun[]>(`${this.base}/payroll/runs`);
  }
  payrollRun(id: number): Observable<PayrollRunDetail> {
    return this.http.get<PayrollRunDetail>(`${this.base}/payroll/runs/${id}`);
  }
  payslip(runId: number, lineId: number): Observable<Payslip> {
    return this.http.get<Payslip>(`${this.base}/payroll/runs/${runId}/lines/${lineId}/payslip`);
  }
  generatePayroll(year: number, month: number): Observable<PayrollRunDetail> {
    return this.http.post<PayrollRunDetail>(`${this.base}/payroll/runs`, { year, month });
  }
  approvePayroll(id: number): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/payroll/runs/${id}/approve`, null);
  }
  payPayroll(id: number, paidDate: string | null): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/payroll/runs/${id}/pay`, { paidDate });
  }
  bankFile(id: number, format: 'csv' | 'xlsx'): Observable<Blob> {
    return this.http.get(`${this.base}/payroll/runs/${id}/bank-file`, { params: { format }, responseType: 'blob' });
  }
  emailPayslips(id: number, lineId?: number): Observable<PayslipEmailResult> {
    let params = new HttpParams();
    if (lineId) params = params.set('lineId', lineId);
    return this.http.post<PayslipEmailResult>(`${this.base}/payroll/runs/${id}/email-payslips`, null, { params });
  }
  /** The payroll register of a month (Excel, PDF or CSV). */
  payrollRegister(id: number, format: 'xlsx' | 'pdf' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/payroll/runs/${id}/register`, { params: { format }, responseType: 'blob' });
  }
  /** One row per employee for a year (approved and paid payrolls). */
  payrollAnnualSummary(year: number, format: 'xlsx' | 'pdf' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/payroll/annual-summary`, { params: { year, format }, responseType: 'blob' });
  }
  deletePayroll(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/payroll/runs/${id}`);
  }

  // one-off adjustments
  createAdjustment(p: AdjustmentPayload): Observable<PayrollAdjustment> {
    return this.http.post<PayrollAdjustment>(`${this.base}/payroll/adjustments`, p);
  }
  updateAdjustment(id: number, p: AdjustmentPayload): Observable<PayrollAdjustment> {
    return this.http.put<PayrollAdjustment>(`${this.base}/payroll/adjustments/${id}`, p);
  }
  deleteAdjustment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/payroll/adjustments/${id}`);
  }

  // advances and loans
  createLoan(p: LoanPayload): Observable<StaffLoan> {
    return this.http.post<StaffLoan>(`${this.base}/payroll/loans`, p);
  }
  loan(id: number): Observable<StaffLoanDetail> {
    return this.http.get<StaffLoanDetail>(`${this.base}/payroll/loans/${id}`);
  }
  repayLoan(id: number, amount: number, date: string | null, note: string | null): Observable<StaffLoanDetail> {
    return this.http.post<StaffLoanDetail>(`${this.base}/payroll/loans/${id}/repay`, { amount, date, note });
  }
  writeOffLoan(id: number, note: string): Observable<StaffLoanDetail> {
    return this.http.post<StaffLoanDetail>(`${this.base}/payroll/loans/${id}/write-off`, { note });
  }
  cancelLoan(id: number): Observable<StaffLoan> {
    return this.http.post<StaffLoan>(`${this.base}/payroll/loans/${id}/cancel`, {});
  }

  // tax and social-security payments, monthly returns
  remittanceSummary(year: number | null): Observable<RemittanceSummary> {
    return this.http.get<RemittanceSummary>(`${this.base}/payroll/remittances/summary`, { params: year ? { year } : {} });
  }
  payRemittance(p: RemittancePayload): Observable<Remittance> {
    return this.http.post<Remittance>(`${this.base}/payroll/remittances`, p);
  }
  voidRemittance(id: number, note: string): Observable<Remittance> {
    return this.http.post<Remittance>(`${this.base}/payroll/remittances/${id}/void`, { note });
  }
  /** The income-tax or social-security return of an approved payroll month. */
  payrollReturn(runId: number, kind: 'paye' | 'social-security', format: 'xlsx' | 'pdf' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/payroll/runs/${runId}/returns/${kind}`, { params: { format }, responseType: 'blob' });
  }
  // employee documents
  allDocuments(f: { q?: string; category?: string; state?: string; employeeId?: number | null } = {}): Observable<EmployeeDocument[]> {
    let params = new HttpParams();
    if (f.q) params = params.set('q', f.q);
    if (f.category) params = params.set('category', f.category);
    if (f.state) params = params.set('state', f.state);
    if (f.employeeId) params = params.set('employeeId', f.employeeId);
    return this.http.get<EmployeeDocument[]>(`${this.base}/employees/documents`, { params });
  }
  documentSummary(): Observable<DocumentSummary> {
    return this.http.get<DocumentSummary>(`${this.base}/employees/documents/summary`);
  }
  employeeDocuments(employeeId: number): Observable<EmployeeDocument[]> {
    return this.http.get<EmployeeDocument[]>(`${this.base}/employees/${employeeId}/documents`);
  }
  uploadDocument(employeeId: number, file: File, p: DocumentUploadPayload): Observable<EmployeeDocument> {
    const body = new FormData();
    body.append('file', file, file.name);
    body.append('name', p.name);
    body.append('category', p.category);
    if (p.description) body.append('description', p.description);
    if (p.expiryDate) body.append('expiryDate', p.expiryDate);
    body.append('shared', p.shared ? 'true' : 'false');
    return this.http.post<EmployeeDocument>(`${this.base}/employees/${employeeId}/documents`, body);
  }
  updateDocument(docId: number, p: DocumentUpdatePayload): Observable<EmployeeDocument> {
    return this.http.put<EmployeeDocument>(`${this.base}/employees/documents/${docId}`, p);
  }
  deleteDocument(docId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/employees/documents/${docId}`);
  }
  documentFile(docId: number): Observable<Blob> {
    return this.http.get(`${this.base}/employees/documents/${docId}/file`, { responseType: 'blob' });
  }
  myDocuments(): Observable<EmployeeDocument[]> {
    return this.http.get<EmployeeDocument[]>(`${this.base}/me/documents`);
  }
  myDocumentFile(docId: number): Observable<Blob> {
    return this.http.get(`${this.base}/me/documents/${docId}/file`, { responseType: 'blob' });
  }

  // onboarding / offboarding checklists
  checklistTemplates(): Observable<ChecklistTemplate[]> {
    return this.http.get<ChecklistTemplate[]>(`${this.base}/employees/checklist-templates`);
  }
  createChecklistTemplate(p: ChecklistTemplatePayload): Observable<ChecklistTemplate> {
    return this.http.post<ChecklistTemplate>(`${this.base}/employees/checklist-templates`, p);
  }
  updateChecklistTemplate(id: number, p: ChecklistTemplatePayload): Observable<ChecklistTemplate> {
    return this.http.put<ChecklistTemplate>(`${this.base}/employees/checklist-templates/${id}`, p);
  }
  deleteChecklistTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/employees/checklist-templates/${id}`);
  }
  openTasks(f: { responsible?: string; overdueOnly?: boolean; kind?: string; employeeId?: number | null } = {}): Observable<OpenTask[]> {
    let params = new HttpParams();
    if (f.responsible) params = params.set('responsible', f.responsible);
    if (f.overdueOnly) params = params.set('overdueOnly', true);
    if (f.kind) params = params.set('kind', f.kind);
    if (f.employeeId) params = params.set('employeeId', f.employeeId);
    return this.http.get<OpenTask[]>(`${this.base}/employees/checklists/open`, { params });
  }
  employeeChecklists(employeeId: number): Observable<EmployeeChecklist[]> {
    return this.http.get<EmployeeChecklist[]>(`${this.base}/employees/${employeeId}/checklists`);
  }
  startChecklist(employeeId: number, templateId: number, startDate: string | null): Observable<EmployeeChecklist> {
    return this.http.post<EmployeeChecklist>(`${this.base}/employees/${employeeId}/checklists`, { templateId, startDate });
  }
  deleteChecklist(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/employees/checklists/${id}`);
  }
  addChecklistTask(checklistId: number, title: string, responsible: string | null, dueDate: string | null): Observable<EmployeeChecklist> {
    return this.http.post<EmployeeChecklist>(`${this.base}/employees/checklists/${checklistId}/tasks`, { title, responsible, dueDate });
  }
  setTaskDone(taskId: number, done: boolean, note: string | null): Observable<EmployeeChecklist> {
    return this.http.post<EmployeeChecklist>(`${this.base}/employees/checklist-tasks/${taskId}/done`, { done, note });
  }
  deleteChecklistTask(taskId: number): Observable<EmployeeChecklist> {
    return this.http.delete<EmployeeChecklist>(`${this.base}/employees/checklist-tasks/${taskId}`);
  }

  // logins for employees (so each can open My HR)
  createEmployeeLogin(id: number, body: { username?: string; password?: string } = {}): Observable<EmployeeLoginCreated> {
    return this.http.post<EmployeeLoginCreated>(`${this.base}/employees/${id}/login`, body);
  }
  createEmployeeLogins(): Observable<EmployeeLoginBulk> {
    return this.http.post<EmployeeLoginBulk>(`${this.base}/employees/logins`, {});
  }
  resetEmployeeLoginPassword(id: number): Observable<EmployeeLoginCreated> {
    return this.http.post<EmployeeLoginCreated>(`${this.base}/employees/${id}/login/reset-password`, {});
  }

  // importing employees from Excel or CSV
  importEmployeesTemplate(format: 'xlsx' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/employees/import/template`, { params: { format }, responseType: 'blob' });
  }
  importEmployees(file: File, dryRun: boolean, createDepartments: boolean): Observable<EmployeeImportResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<EmployeeImportResult>(`${this.base}/employees/import`, body, { params: { dryRun, createDepartments } });
  }

  // corrections: cancel the payment, take an approved payroll back to draft
  cancelPayrollPayment(id: number, note: string): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/payroll/runs/${id}/cancel-payment`, { note });
  }
  reopenPayroll(id: number, note: string): Observable<PayrollRun> {
    return this.http.post<PayrollRun>(`${this.base}/payroll/runs/${id}/reopen`, { note });
  }
  payrollHistory(id: number): Observable<PayrollEvent[]> {
    return this.http.get<PayrollEvent[]>(`${this.base}/payroll/runs/${id}/history`);
  }

  // final pay of an employee who left
  finalPay(employeeId: number): Observable<FinalPay> {
    return this.http.get<FinalPay>(`${this.base}/payroll/final-pay/${employeeId}`);
  }
  saveFinalPay(employeeId: number, p: FinalPayPayload): Observable<FinalPay> {
    return this.http.post<FinalPay>(`${this.base}/payroll/final-pay/${employeeId}`, p);
  }
  removeFinalPay(employeeId: number): Observable<FinalPay> {
    return this.http.delete<FinalPay>(`${this.base}/payroll/final-pay/${employeeId}`);
  }
}
