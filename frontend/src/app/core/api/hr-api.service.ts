import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

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

export interface LeaveType {
  id: number;
  name: string;
  daysPerYear: number;
  paid: boolean;
  active: boolean;
}

export interface LeaveTypePayload {
  name: string;
  daysPerYear: number;
  paid: boolean;
  active: boolean;
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

/** Employees, organisation, attendance & leaves, contracts and payroll (/api/hr). */
@Injectable({ providedIn: 'root' })
export class HrApiService {
  private base = `${API_BASE}/hr`;

  constructor(private http: HttpClient) {}

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
  leaveWorkingDays(from: string, to: string): Observable<number> {
    return this.http.get<number>(`${this.base}/leaves/working-days`, { params: { from, to } });
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
  deletePayroll(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/payroll/runs/${id}`);
  }
}
