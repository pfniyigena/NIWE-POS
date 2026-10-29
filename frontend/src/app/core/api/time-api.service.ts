import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export type TimeEntryStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'INVOICED';
export type ProjectStatus = 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';

export interface StaffOption { id: number; fullName: string; }

export interface TimeSettings {
  requireApproval: boolean;
  timerRoundingMinutes: number;
  defaultHourlyRate: number;
  maxHoursPerDay: number;
  dailyCapacityHours: number;
  billingProductId: number | null;
  billingProductName: string | null;
  timerAutoStopHours: number;
  lockedThrough: string | null;
  reminderEnabled: boolean;
  reminderDay: number;
  budgetWarnPercent: number;
}
export type SettingsSave = Partial<TimeSettings> & { clearLock?: boolean };

export interface TimeTask { id: number; projectId: number; name: string; estimatedHours: number | null; active: boolean; hoursLogged: number; }
export interface TimeRate { userId: number; userName: string; hourlyRate: number; }

export interface ProjectSummary {
  id: number; name: string; code: string | null; customerId: number | null; customerName: string | null; status: ProjectStatus; billable: boolean;
  defaultRate: number | null; budgetHours: number | null; hoursLogged: number; billableHours: number; unbilledAmount: number; percentOfBudget: number | null;
  sourceType: string; sourceId: number | null; startDate: string | null; endDate: string | null;
  restrictToTeam: boolean; blockOverBudget: boolean;
}

export interface TeamMember { userId: number; userName: string; }
export interface ProjectDetail { summary: ProjectSummary; description: string | null; branchId: number | null; tasks: TimeTask[]; rates: TimeRate[]; team: TeamMember[]; }

export interface ProjectChoice { id: number; name: string; code: string | null; customerName: string | null; billable: boolean; tasks: TimeTask[]; budgetHours: number | null; hoursLogged: number | null; blockOverBudget: boolean; }

export interface ProjectPayload {
  name: string; code?: string | null; customerId?: number | null; description?: string | null; status?: ProjectStatus; billable?: boolean;
  defaultRate?: number | null; budgetHours?: number | null; startDate?: string | null; endDate?: string | null; branchId?: number | null;
  restrictToTeam?: boolean; blockOverBudget?: boolean;
}

export interface TimeEntry {
  id: number; userId: number; userName: string; projectId: number; projectName: string; customerName: string | null; taskId: number | null; taskName: string | null;
  workDate: string; minutes: number; hours: number; description: string | null; billable: boolean; hourlyRate: number; amount: number;
  status: TimeEntryStatus; origin: string; submittedAt: string | null; decidedByName: string | null; decidedAt: string | null; rejectReason: string | null;
  invoiceId: number | null; invoiceNumber: string | null;
}

export interface EntryPayload { projectId: number; taskId?: number | null; workDate: string; hours?: number | null; minutes?: number | null; description?: string | null; billable?: boolean; }

export interface Timer {
  running: boolean; projectId: number | null; projectName: string | null; taskId: number | null; taskName: string | null; description: string | null;
  billable: boolean; startedAt: string | null; elapsedSeconds: number; autoStopHours: number;
}

export interface Week {
  from: string; to: string; entries: TimeEntry[]; totalHours: number; billableHours: number; draftCount: number; submittedCount: number;
  approvedCount: number; rejectedCount: number; invoicedCount: number;
}

export interface MySummary { hoursToday: number; hoursThisWeek: number; billableHoursThisWeek: number; hoursThisMonth: number; toSubmit: number; rejected: number; }
export interface TimeSummary { pendingApprovals: number; hoursThisMonth: number; billableHoursThisMonth: number; unbilledAmount: number; activeProjects: number; overBudgetProjects: number; }

export interface MissingRow { userId: number; userName: string; hasEmail: boolean; status: 'NOTHING' | 'DRAFTS' | 'SENT_BACK'; hoursLogged: number; hoursNotSubmitted: number; hoursSubmitted: number; }
export interface MissingList { weekStart: string; weekEnd: string; rows: MissingRow[]; }
export interface ReminderResult { sent: number; noEmail: number; failed: number; notes: string[]; }
export interface CopyResult { copied: number; skipped: number; notes: string[]; }

export interface InvoiceLinePreview { userId: number; userName: string; hourlyRate: number; hours: number; amount: number; description: string; entries: number; }
export interface BillingPreview {
  projectId: number; projectName: string; customerId: number | null; customerName: string | null; entryIds: number[]; lines: InvoiceLinePreview[]; totalHours: number; totalAmount: number;
}
export interface BillingRequest { projectId: number; customerId?: number | null; from?: string | null; to?: string | null; entryIds?: number[] | null; dueDate?: string | null; notes?: string | null; }
export interface BillingResult { invoiceId: number; invoiceNumber: string; entries: number; totalAmount: number; }

@Injectable({ providedIn: 'root' })
export class TimeApiService {
  private base = `${API_BASE}/time`;

  constructor(private http: HttpClient) {}

  staff(): Observable<StaffOption[]> { return this.http.get<StaffOption[]>(`${this.base}/staff`); }
  projectChoices(): Observable<ProjectChoice[]> { return this.http.get<ProjectChoice[]>(`${this.base}/project-choices`); }

  // my time
  myWeek(from?: string, to?: string): Observable<Week> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Week>(`${this.base}/my/entries`, { params });
  }
  mySummary(): Observable<MySummary> { return this.http.get<MySummary>(`${this.base}/my/summary`); }
  createEntry(p: EntryPayload): Observable<TimeEntry> { return this.http.post<TimeEntry>(`${this.base}/my/entries`, p); }
  updateEntry(id: number, p: EntryPayload): Observable<TimeEntry> { return this.http.put<TimeEntry>(`${this.base}/my/entries/${id}`, p); }
  deleteEntry(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/my/entries/${id}`); }
  submit(ids: number[] | null, from?: string, to?: string): Observable<{ submitted: number }> {
    return this.http.post<{ submitted: number }>(`${this.base}/my/submit`, { ids, from, to });
  }
  timer(): Observable<Timer> { return this.http.get<Timer>(`${this.base}/my/timer`); }
  startTimer(p: { projectId: number; taskId?: number | null; description?: string | null; billable?: boolean }): Observable<Timer> {
    return this.http.post<Timer>(`${this.base}/my/timer/start`, p);
  }
  stopTimer(p?: { description?: string | null; minutes?: number | null }): Observable<TimeEntry> { return this.http.post<TimeEntry>(`${this.base}/my/timer/stop`, p ?? {}); }
  discardTimer(): Observable<void> { return this.http.delete<void>(`${this.base}/my/timer`); }

  // projects
  projects(status?: string, includeArchived = false): Observable<ProjectSummary[]> {
    let params = new HttpParams().set('includeArchived', includeArchived);
    if (status) params = params.set('status', status);
    return this.http.get<ProjectSummary[]>(`${this.base}/projects`, { params });
  }
  project(id: number): Observable<ProjectDetail> { return this.http.get<ProjectDetail>(`${this.base}/projects/${id}`); }
  createProject(p: ProjectPayload): Observable<ProjectDetail> { return this.http.post<ProjectDetail>(`${this.base}/projects`, p); }
  updateProject(id: number, p: ProjectPayload): Observable<ProjectDetail> { return this.http.put<ProjectDetail>(`${this.base}/projects/${id}`, p); }
  deleteProject(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/projects/${id}`); }
  addTask(projectId: number, name: string, estimatedHours: number | null): Observable<TimeTask> {
    return this.http.post<TimeTask>(`${this.base}/projects/${projectId}/tasks`, { name, estimatedHours });
  }
  updateTask(id: number, p: { name: string; estimatedHours: number | null; active: boolean }): Observable<TimeTask> { return this.http.put<TimeTask>(`${this.base}/tasks/${id}`, p); }
  deleteTask(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/tasks/${id}`); }
  setRate(projectId: number, userId: number, hourlyRate: number): Observable<TimeRate[]> {
    return this.http.put<TimeRate[]>(`${this.base}/projects/${projectId}/rates`, { userId, hourlyRate });
  }
  removeRate(projectId: number, userId: number): Observable<TimeRate[]> { return this.http.delete<TimeRate[]>(`${this.base}/projects/${projectId}/rates/${userId}`); }

  // everyone's time
  entries(f: { status?: string; userId?: number | null; projectId?: number | null; from?: string; to?: string }): Observable<TimeEntry[]> {
    let params = new HttpParams();
    if (f.status) params = params.set('status', f.status);
    if (f.userId) params = params.set('userId', f.userId);
    if (f.projectId) params = params.set('projectId', f.projectId);
    if (f.from) params = params.set('from', f.from);
    if (f.to) params = params.set('to', f.to);
    return this.http.get<TimeEntry[]>(`${this.base}/entries`, { params });
  }
  approvals(): Observable<TimeEntry[]> { return this.http.get<TimeEntry[]>(`${this.base}/approvals`); }
  summary(): Observable<TimeSummary> { return this.http.get<TimeSummary>(`${this.base}/summary`); }
  approve(ids: number[]): Observable<{ approved: number }> { return this.http.post<{ approved: number }>(`${this.base}/approve`, { ids }); }
  reject(ids: number[], reason: string): Observable<{ rejected: number }> { return this.http.post<{ rejected: number }>(`${this.base}/reject`, { ids, reason }); }
  correct(id: number, p: EntryPayload): Observable<TimeEntry> { return this.http.put<TimeEntry>(`${this.base}/entries/${id}`, p); }

  // settings
  settings(): Observable<TimeSettings> { return this.http.get<TimeSettings>(`${this.base}/settings`); }
  saveSettings(s: SettingsSave): Observable<TimeSettings> { return this.http.put<TimeSettings>(`${this.base}/settings`, s); }
  autoStopNow(): Observable<{ stopped: number }> { return this.http.post<{ stopped: number }>(`${this.base}/timers/auto-stop`, {}); }

  // discipline
  copyWeek(week?: string): Observable<CopyResult> { return this.http.post<CopyResult>(`${this.base}/my/copy-week`, week ? { week } : {}); }
  missing(week?: string): Observable<MissingList> {
    let params = new HttpParams();
    if (week) params = params.set('week', week);
    return this.http.get<MissingList>(`${this.base}/missing`, { params });
  }
  remind(week: string, userIds: number[] | null): Observable<ReminderResult> { return this.http.post<ReminderResult>(`${this.base}/reminders`, { week, userIds }); }
  setTeam(projectId: number, userIds: number[], restrictToTeam: boolean): Observable<ProjectDetail> {
    return this.http.put<ProjectDetail>(`${this.base}/projects/${projectId}/team`, { userIds, restrictToTeam });
  }

  // billing
  ready(): Observable<ProjectSummary[]> { return this.http.get<ProjectSummary[]>(`${this.base}/billing/ready`); }
  preview(r: BillingRequest): Observable<BillingPreview> { return this.http.post<BillingPreview>(`${this.base}/billing/preview`, r); }
  invoice(r: BillingRequest): Observable<BillingResult> { return this.http.post<BillingResult>(`${this.base}/billing/invoice`, r); }
  release(invoiceId: number): Observable<{ released: number }> { return this.http.post<{ released: number }>(`${this.base}/billing/release/${invoiceId}`, {}); }
}
