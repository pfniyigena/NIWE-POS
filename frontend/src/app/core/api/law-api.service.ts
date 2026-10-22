import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { TimeEntry } from './time-api.service';
import { PageParams, PageResult, getPaged } from './paged';

export type MatterStatus = 'OPEN' | 'ON_HOLD' | 'CLOSED';
export type BillingType = 'HOURLY' | 'FIXED' | 'RETAINER' | 'CONTINGENCY' | 'PRO_BONO';
export type TeamRole = 'LEAD' | 'ASSOCIATE' | 'PARALEGAL' | 'SUPPORT';
export type PartyRole = 'OPPOSING_PARTY' | 'OPPOSING_COUNSEL' | 'CO_CLIENT' | 'WITNESS' | 'EXPERT' | 'JUDGE_OR_OFFICIAL' | 'RELATED' | 'OTHER';
export type ContactType = 'PERSON' | 'COMPANY' | 'GOVERNMENT';
export type DeadlineType = 'COURT_DATE' | 'HEARING' | 'FILING' | 'LIMITATION' | 'MEETING' | 'OTHER';
export type DeadlineStatus = 'OPEN' | 'DONE' | 'CANCELLED';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type NoteKind = 'NOTE' | 'CALL' | 'MEETING' | 'EMAIL' | 'COURT';

export interface Option { id: number; name: string; }

export interface LawSettings { practiceAreas: string[]; jurisdictions: string[]; reminderDays: number; digestEnabled: boolean; restrictedByDefault: boolean; defaultBillingType: BillingType;
  trustEnabled: boolean; trustBankName: string | null; trustAccountNumber: string | null; portalEnabled: boolean; escalationEnabled: boolean; escalationDays: number;
  collectionsEnabled: boolean; collectionsFirstDays: number; collectionsRepeatDays: number; collectionsMax: number; defaultTermDays: number; }
export interface LawSettingsPayload { practiceAreas: string; jurisdictions: string; reminderDays: number; digestEnabled: boolean; restrictedByDefault: boolean; defaultBillingType: BillingType;
  trustEnabled?: boolean; trustBankName?: string | null; trustAccountNumber?: string | null; portalEnabled?: boolean; escalationEnabled?: boolean; escalationDays?: number;
  collectionsEnabled?: boolean; collectionsFirstDays?: number; collectionsRepeatDays?: number; collectionsMax?: number; defaultTermDays?: number; }

// ---- client portal (the firm's side)
export type DocReview = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export interface PortalAccount { id: number; name: string; email: string; active: boolean; accepted: boolean; inviteExpiresAt: string | null; lastLoginAt: string | null; locked: boolean; }
export interface PortalAdminDeadline { id: number; title: string; type: string; dueDate: string; status: string; visibleToClient: boolean; }
export interface PortalAdminMessage { id: number; fromClient: boolean; senderName: string; body: string; createdAt: string; read: boolean; }
export interface PortalAdmin {
  matterId: number; number: string; title: string; clientName: string; firmPortalEnabled: boolean; enabled: boolean; showDeadlines: boolean; showDocuments: boolean; showInvoices: boolean;
  showTrust: boolean; allowUpload: boolean; allowMessages: boolean; clientUpdate: string | null; clientUpdateAt: string | null; clientUpdateBy: string | null; canManage: boolean; canWork: boolean;
  accounts: PortalAccount[]; deadlines: PortalAdminDeadline[]; messages: PortalAdminMessage[]; unread: number; pendingUploads: number;
}
export interface PortalInviteResult { account: PortalAccount; link: string; emailSent: boolean; emailNote: string; }
export interface PortalSettingsPayload { enabled?: boolean; showDeadlines?: boolean; showDocuments?: boolean; showInvoices?: boolean; showTrust?: boolean; allowUpload?: boolean; allowMessages?: boolean; }

// ---- documents
export type DocCategory = 'PLEADING' | 'CONTRACT' | 'CORRESPONDENCE' | 'EVIDENCE' | 'COURT_ORDER' | 'CLIENT_ID' | 'RESEARCH' | 'FINANCIAL' | 'OTHER';
export type TemplateKind = 'TEXT' | 'DOCX';
export interface DocItem {
  id: number; name: string; category: DocCategory; description: string | null; confidential: boolean; sharedWithClient: boolean; archived: boolean; currentVersion: number;
  fileName: string; contentType: string | null; sizeBytes: number; checkedOutByUserId: number | null; checkedOutByName: string | null; checkedOutAt: string | null; checkedOutByMe: boolean;
  createdByName: string; createdAt: string; updatedAt: string; templateId: number | null; canEdit: boolean; canLock: boolean; canRelease: boolean; canManage: boolean;
  fromClient: boolean; reviewStatus: DocReview | null; reviewNote: string | null;
}
export interface DocList { matterId: number; number: string; title: string; documents: DocItem[]; totalBytes: number; canUpload: boolean; canManage: boolean; }
export interface DocVersion { id: number; versionNo: number; fileName: string; contentType: string | null; sizeBytes: number; sha256: string | null; note: string | null; uploadedByName: string; createdAt: string; }
export interface DocVersions { document: DocItem; versions: DocVersion[]; }
export interface DocTemplate {
  id: number; name: string; category: DocCategory; description: string | null; kind: TemplateKind; body: string | null; fileName: string | null; active: boolean; createdByName: string | null;
  updatedAt: string; uses: number;
}
export interface DocPlaceholder { key: string; description: string; }
export interface DocGenerateResult { document: DocItem; unresolved: string[]; }
export interface DocTemplatePayload { name: string; category: DocCategory; description: string | null; kind: TemplateKind; body: string | null; active: boolean; }

// ---- client money (trust account)
export type TrustType = 'DEPOSIT' | 'PAYOUT' | 'FEE_TRANSFER' | 'DEPOSIT_REVERSAL';
export type TrustStatus = 'PENDING' | 'POSTED' | 'REJECTED' | 'CANCELLED';
export interface TrustTx {
  id: number; number: string; type: TrustType; status: TrustStatus; matterId: number; matterNumber: string; clientCustomerId: number; clientName: string; amount: number; txDate: string;
  method: string | null; reference: string | null; partyName: string | null; purpose: string | null; requestedByName: string; createdAt: string; decidedByName: string | null; decidedAt: string | null;
  decisionNote: string | null; invoiceId: number | null; invoiceNumber: string | null; reversalOfId: number | null; reversed: boolean; balanceAfter: number | null;
  canApprove: boolean; canCancel: boolean; canReverse: boolean;
}
export interface TrustInvoice { invoiceId: number; invoiceNumber: string; total: number; outstanding: number; waiting: number; }
export interface MatterTrust {
  matterId: number; number: string; title: string; clientCustomerId: number; clientName: string; matterStatus: MatterStatus; enabled: boolean; bankName: string | null; accountNumber: string | null;
  balance: number; pending: number; available: number; transactions: TrustTx[]; invoices: TrustInvoice[]; canRecord: boolean; canDeposit: boolean;
}
export interface TrustBalanceRow { matterId: number; matterNumber: string; matterTitle: string; matterStatus: MatterStatus; clientCustomerId: number; clientName: string; balance: number; pending: number; available: number; lastActivity: string | null; }
export interface TrustOverview { enabled: boolean; bankName: string | null; accountNumber: string | null; totalHeld: number; totalPending: number; clients: number; rows: TrustBalanceRow[]; approvals: TrustTx[]; canReconcile: boolean; }
export interface TrustReconciliation {
  id: number; asOfDate: string; statementBalance: number; depositsInTransit: number; outstandingPayments: number; adjustedBank: number; ledgerTotal: number; glBank: number; glLiability: number;
  difference: number; balanced: boolean; note: string | null; preparedByName: string; createdAt: string;
}
export interface TrustReconciliationView { ledgerTotal: number; pendingOut: number; glBank: number; glLiability: number; history: TrustReconciliation[]; }
export interface TrustDepositPayload { amount: number | null; date: string | null; method: string; reference: string | null; payerName: string | null; purpose: string | null; }
export interface TrustPayoutPayload { amount: number | null; date: string | null; method: string; reference: string | null; payeeName: string; purpose: string; }
export interface TrustReconcilePayload { asOfDate: string | null; statementBalance: number | null; depositsInTransit: number | null; outstandingPayments: number | null; note: string | null; }


export interface Contact { id: number; name: string; type: ContactType; phone: string | null; email: string | null; idNumber: string | null; address: string | null; notes: string | null; matters: number; }
export interface ContactPayload { name: string; type: ContactType; phone?: string | null; email?: string | null; idNumber?: string | null; address?: string | null; notes?: string | null; }

export interface ClientRow { id: number; name: string; phone: string | null; email: string | null; matters: number; openMatters: number; }
export interface TeamMember { id: number; userId: number; userName: string; role: TeamRole; }
export interface Party { id: number; contactId: number; name: string; contactType: ContactType; phone: string | null; email: string | null; idNumber: string | null; role: PartyRole; note: string | null; }

export interface Deadline {
  id: number; matterId: number; matterNumber: string | null; matterTitle: string | null; title: string; type: DeadlineType; dueDate: string; dueTime: string | null;
  status: DeadlineStatus; assignedToUserId: number | null; assignedToName: string | null; notes: string | null; overdue: boolean; daysLeft: number; completedByName: string | null;
}
export interface Task {
  id: number; matterId: number; matterNumber: string | null; matterTitle: string | null; title: string; description: string | null; dueDate: string | null; assignedToUserId: number | null;
  assignedToName: string | null; status: TaskStatus; priority: TaskPriority; overdue: boolean; completedAt: string | null;
}
export interface DiaryNote { id: number; kind: NoteKind; text: string; createdByName: string | null; createdAt: string; canDelete: boolean; }

export interface MatterRow {
  id: number; number: string; title: string; status: MatterStatus; practiceArea: string | null; jurisdiction: string | null; clientId: number; clientName: string; leadUserId: number | null;
  leadName: string | null; openedDate: string | null; closedDate: string | null; billingType: BillingType; restricted: boolean; conflictCleared: boolean; openTasks: number;
  overdueTasks: number; nextDeadline: string | null; overdueDeadlines: number;
}

export interface Matter {
  id: number; number: string; title: string; description: string | null; status: MatterStatus; practiceArea: string | null; jurisdiction: string | null; courtOrForum: string | null;
  courtCaseNumber: string | null; branchId: number | null; clientId: number; clientName: string; clientPhone: string | null; clientEmail: string | null; billingType: BillingType;
  billingNote: string | null; openedDate: string | null; closedDate: string | null; outcome: string | null; restricted: boolean; conflictCleared: boolean; conflictNote: string | null;
  conflictClearedBy: string | null; conflictClearedAt: string | null; leadUserId: number | null; leadName: string | null; createdByName: string | null; team: TeamMember[]; parties: Party[];
  deadlines: Deadline[]; tasks: Task[]; notes: DiaryNote[]; canWork: boolean; canLead: boolean;
}

export interface MatterPayload {
  title: string; description?: string | null; clientCustomerId: number | null; practiceArea?: string | null; jurisdiction?: string | null; courtOrForum?: string | null;
  courtCaseNumber?: string | null; branchId?: number | null; billingType: BillingType; billingNote?: string | null; restricted: boolean; leadUserId?: number | null; openedDate?: string | null;
}

export interface LawSummary { openMatters: number; onHold: number; deadlinesThisWeek: number; overdueDeadlines: number; overdueTasks: number; conflictsNotCleared: number; }
export interface Mine { matters: MatterRow[]; tasks: Task[]; deadlines: Deadline[]; }

export interface MatterRef { id: number | null; number: string | null; title: string | null; status: MatterStatus; role: string; hidden: boolean; }
export interface ConflictHit { kind: 'CLIENT' | 'CONTACT'; id: number; name: string; phone: string | null; email: string | null; idNumber: string | null; reasons: string[]; severity: 'HIGH' | 'REVIEW'; matters: MatterRef[]; }
export interface ConflictSubject { label: string; role: string | null; hits: ConflictHit[]; }
export interface ConflictResult { logId: number | null; hits: number; subjects: ConflictSubject[]; }
export interface ConflictLog { id: number; query: string; matterId: number | null; matterNumber: string | null; hits: number; checkedBy: string | null; at: string; }

export interface TaskPayload { title: string; description?: string | null; dueDate?: string | null; assignedToUserId?: number | null; priority: TaskPriority; }
export interface DeadlinePayload { title: string; type: DeadlineType; dueDate: string; dueTime?: string | null; assignedToUserId?: number | null; notes?: string | null; }

export interface FeeTerms { billingType: BillingType; billingNote: string | null; hourlyRate: number | null; fixedFee: number | null; retainerAmount: number | null; contingencyPercent: number | null; }
export type FeeTermsPayload = FeeTerms;
export interface FeeStage { id: number; description: string; amount: number; dueDate: string | null; status: 'PLANNED' | 'BILLED'; billId: number | null; invoiceNumber: string | null; }
export interface MatterCost {
  id: number; expenseDate: string; description: string; supplierName: string | null; amount: number; chargeAmount: number; billed: boolean; billId: number | null;
  invoiceNumber: string | null; expenseId: number | null; createdByName: string | null; canEdit: boolean;
}
export interface MatterBill {
  id: number; kind: 'INVOICE' | 'RETAINER'; status: 'ACTIVE' | 'RELEASED'; invoiceId: number | null; invoiceNumber: string | null; invoiceStatus: string | null; amount: number;
  feesAmount: number; expensesAmount: number; retainerApplied: number; recoveredAmount: number | null; summary: string | null; createdByName: string | null; createdAt: string;
  amountPaid: number | null; amountOutstanding: number | null; stale: boolean;
}
export interface BillingSummary {
  hoursLogged: number; billableHours: number; timeWaitingApproval: number; unbilledTime: number; unbilledCosts: number; stagesPlanned: number; billed: number; paid: number;
  outstanding: number; retainerInvoiced: number; retainerApplied: number; retainerBalance: number;
}
export interface MatterBilling {
  matterId: number; number: string; title: string; clientName: string; timeProjectId: number; terms: FeeTerms; summary: BillingSummary;
  rates: { userId: number; userName: string; hourlyRate: number }[]; stages: FeeStage[]; costs: MatterCost[]; bills: MatterBill[]; time: TimeEntry[];
  canTerms: boolean; canBill: boolean; canWork: boolean; canApprove: boolean;
}
export interface BillRequestPayload {
  includeTime: boolean; timeTo: string | null; stageIds: number[]; costIds: number[]; recoveredAmount: number | null; applyRetainer: boolean; dueDate: string | null; notes: string | null;
}
export interface BillPreview {
  matterId: number; number: string; customerId: number; customerName: string; lines: { kind: string; description: string; amount: number }[]; fees: number; costs: number;
  retainerBalance: number; retainerApplied: number; total: number; timeEntries: number; warning: string | null;
}
export interface BillResult { billId: number; invoiceId: number | null; invoiceNumber: string | null; total: number; retainerApplied: number; }
export interface CostPayload { expenseDate: string | null; description: string; supplierName: string | null; amount: number | null; chargeAmount: number | null; expenseAccountId?: number | null; }
export interface BillingOverviewRow {
  matterId: number; number: string; title: string; clientName: string; billingType: BillingType; status: string; hoursLogged: number; unbilledTime: number; unbilledCosts: number;
  stagesPlanned: number; billed: number; paid: number; outstanding: number; retainerBalance: number; restricted: boolean;
}

@Injectable({ providedIn: 'root' })
export class LawApiService {
  private base = `${API_BASE}/law`;

  constructor(private http: HttpClient) {}

  settings(): Observable<LawSettings> { return this.http.get<LawSettings>(`${this.base}/settings`); }
  updateSettings(p: LawSettingsPayload): Observable<LawSettings> { return this.http.put<LawSettings>(`${this.base}/settings`, p); }
  staff(): Observable<Option[]> { return this.http.get<Option[]>(`${this.base}/staff`); }

  summary(): Observable<LawSummary> { return this.http.get<LawSummary>(`${this.base}/summary`); }
  mine(): Observable<Mine> { return this.http.get<Mine>(`${this.base}/mine`); }
  matters(f: { status?: string; q?: string; practiceArea?: string; leadId?: number | null; clientId?: number | null }): Observable<MatterRow[]> {
    let p = new HttpParams();
    if (f.status) p = p.set('status', f.status);
    if (f.q) p = p.set('q', f.q);
    if (f.practiceArea) p = p.set('practiceArea', f.practiceArea);
    if (f.leadId) p = p.set('leadId', f.leadId);
    if (f.clientId) p = p.set('clientId', f.clientId);
    return this.http.get<MatterRow[]>(`${this.base}/matters`, { params: p });
  }
  mattersPage(f: { status?: string; q?: string; practiceArea?: string; leadId?: number | null; clientId?: number | null } & PageParams): Observable<PageResult<MatterRow>> {
    let p = new HttpParams();
    if (f.status) p = p.set('status', f.status);
    if (f.q) p = p.set('q', f.q);
    if (f.practiceArea) p = p.set('practiceArea', f.practiceArea);
    if (f.leadId) p = p.set('leadId', f.leadId);
    if (f.clientId) p = p.set('clientId', f.clientId);
    if (f.size) p = p.set('page', f.page ?? 0).set('size', f.size);
    return getPaged<MatterRow>(this.http, `${this.base}/matters`, p);
  }
  matter(id: number): Observable<Matter> { return this.http.get<Matter>(`${this.base}/matters/${id}`); }
  open(p: MatterPayload): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters`, p); }
  update(id: number, p: MatterPayload): Observable<Matter> { return this.http.put<Matter>(`${this.base}/matters/${id}`, p); }
  setStatus(id: number, status: MatterStatus, outcome: string | null, force: boolean): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters/${id}/status`, { status, outcome, force }); }
  deleteMatter(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/matters/${id}`); }
  setTeam(id: number, members: { userId: number; role: TeamRole }[]): Observable<Matter> { return this.http.put<Matter>(`${this.base}/matters/${id}/team`, { members }); }
  addParty(id: number, contactId: number, role: PartyRole, note: string | null): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters/${id}/parties`, { contactId, role, note }); }
  removeParty(id: number, partyId: number): Observable<Matter> { return this.http.delete<Matter>(`${this.base}/matters/${id}/parties/${partyId}`); }
  addNote(id: number, kind: NoteKind, text: string): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters/${id}/notes`, { kind, text }); }
  deleteNote(id: number, noteId: number): Observable<Matter> { return this.http.delete<Matter>(`${this.base}/matters/${id}/notes/${noteId}`); }

  addTask(id: number, p: TaskPayload): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters/${id}/tasks`, p); }
  updateTask(taskId: number, p: TaskPayload): Observable<Matter> { return this.http.put<Matter>(`${this.base}/tasks/${taskId}`, p); }
  taskStatus(taskId: number, status: TaskStatus): Observable<Matter> { return this.http.post<Matter>(`${this.base}/tasks/${taskId}/status`, { status }); }
  deleteTask(taskId: number): Observable<Matter> { return this.http.delete<Matter>(`${this.base}/tasks/${taskId}`); }
  tasks(f: { mine?: boolean; assigneeId?: number | null; includeDone?: boolean }): Observable<Task[]> {
    let p = new HttpParams().set('mine', !!f.mine).set('includeDone', !!f.includeDone);
    if (f.assigneeId) p = p.set('assigneeId', f.assigneeId);
    return this.http.get<Task[]>(`${this.base}/tasks`, { params: p });
  }

  tasksPage(f: { mine?: boolean; assigneeId?: number | null; includeDone?: boolean } & PageParams): Observable<PageResult<Task>> {
    let p = new HttpParams().set('mine', !!f.mine).set('includeDone', !!f.includeDone);
    if (f.assigneeId) p = p.set('assigneeId', f.assigneeId);
    if (f.size) p = p.set('page', f.page ?? 0).set('size', f.size);
    return getPaged<Task>(this.http, `${this.base}/tasks`, p);
  }

  addDeadline(id: number, p: DeadlinePayload): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters/${id}/deadlines`, p); }
  updateDeadline(deadlineId: number, p: DeadlinePayload): Observable<Matter> { return this.http.put<Matter>(`${this.base}/deadlines/${deadlineId}`, p); }
  deadlineDone(deadlineId: number): Observable<Matter> { return this.http.post<Matter>(`${this.base}/deadlines/${deadlineId}/done`, {}); }
  deadlineReopen(deadlineId: number): Observable<Matter> { return this.http.post<Matter>(`${this.base}/deadlines/${deadlineId}/reopen`, {}); }
  deadlineCancel(deadlineId: number): Observable<Matter> { return this.http.post<Matter>(`${this.base}/deadlines/${deadlineId}/cancel`, {}); }
  deleteDeadline(deadlineId: number): Observable<Matter> { return this.http.delete<Matter>(`${this.base}/deadlines/${deadlineId}`); }
  deadlines(f: { mine?: boolean; assigneeId?: number | null; from?: string; to?: string; includeDone?: boolean }): Observable<Deadline[]> {
    let p = new HttpParams().set('mine', !!f.mine).set('includeDone', !!f.includeDone);
    if (f.assigneeId) p = p.set('assigneeId', f.assigneeId);
    if (f.from) p = p.set('from', f.from);
    if (f.to) p = p.set('to', f.to);
    return this.http.get<Deadline[]>(`${this.base}/deadlines`, { params: p });
  }

  conflictSearch(p: { name?: string; phone?: string; idNumber?: string; email?: string; excludeMatterId?: number | null }): Observable<ConflictResult> {
    return this.http.post<ConflictResult>(`${this.base}/conflicts/search`, p);
  }
  conflictCheck(id: number): Observable<ConflictResult> { return this.http.post<ConflictResult>(`${this.base}/matters/${id}/conflicts/check`, {}); }
  conflictClear(id: number, note: string | null): Observable<Matter> { return this.http.post<Matter>(`${this.base}/matters/${id}/conflicts/clear`, { note }); }
  conflictLog(): Observable<ConflictLog[]> { return this.http.get<ConflictLog[]>(`${this.base}/conflicts/log`); }

  contacts(q?: string): Observable<Contact[]> {
    let p = new HttpParams();
    if (q) p = p.set('q', q);
    return this.http.get<Contact[]>(`${this.base}/contacts`, { params: p });
  }
  createContact(p: ContactPayload): Observable<Contact> { return this.http.post<Contact>(`${this.base}/contacts`, p); }
  updateContact(id: number, p: ContactPayload): Observable<Contact> { return this.http.put<Contact>(`${this.base}/contacts/${id}`, p); }
  deleteContact(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/contacts/${id}`); }
  calendar(f: { mine: boolean; from: string; to: string; includeDone: boolean; tasks: boolean }): Observable<CalendarEntry[]> {
    const p = new HttpParams().set('mine', f.mine).set('from', f.from).set('to', f.to).set('includeDone', f.includeDone).set('tasks', f.tasks);
    return this.http.get<CalendarEntry[]>(`${this.base}/calendar`, { params: p });
  }
  paperChecks(matterId: number): Observable<PaperChecks> { return this.http.get<PaperChecks>(`${this.base}/matters/${matterId}/papers/checks`); }
  paperPdf(matterId: number, kind: PaperKind, r: PaperRequest): Observable<Blob> { return this.http.post(`${this.base}/matters/${matterId}/papers/${kind}`, r, { responseType: 'blob' }); }
  paperSave(matterId: number, kind: PaperKind, r: PaperRequest): Observable<{ documentId: number; name: string; message: string }> {
    return this.http.post<{ documentId: number; name: string; message: string }>(`${this.base}/matters/${matterId}/papers/${kind}/save`, r);
  }
  paperEmail(matterId: number, kind: PaperKind, r: PaperRequest): Observable<{ sentTo: string; message: string }> {
    return this.http.post<{ sentTo: string; message: string }>(`${this.base}/matters/${matterId}/papers/${kind}/email`, r);
  }
  collections(f: { overdueOnly: boolean; q?: string; clientId?: number | null }): Observable<Collections> {
    let p = new HttpParams().set('overdueOnly', f.overdueOnly);
    if (f.q) p = p.set('q', f.q);
    if (f.clientId) p = p.set('clientId', f.clientId);
    return this.http.get<Collections>(`${this.base}/collections`, { params: p });
  }
  clientStatementPdf(clientId: number, from?: string, to?: string): Observable<Blob> {
    let p = new HttpParams();
    if (from) p = p.set('from', from);
    if (to) p = p.set('to', to);
    return this.http.get(`${this.base}/collections/clients/${clientId}/statement`, { params: p, responseType: 'blob' });
  }
  emailClientStatement(clientId: number): Observable<{ sentTo: string; message: string }> {
    return this.http.post<{ sentTo: string; message: string }>(`${this.base}/collections/clients/${clientId}/statement/email`, {});
  }
  remindClients(billIds: number[]): Observable<RemindResult> { return this.http.post<RemindResult>(`${this.base}/collections/remind`, { billIds }); }
  reminderPreview(): Observable<ReminderItem[]> { return this.http.get<ReminderItem[]>(`${this.base}/collections/reminders`); }
  runReminders(): Observable<RemindResult> { return this.http.post<RemindResult>(`${this.base}/collections/reminders/run`, {}); }
  importFile(kind: 'clients' | 'matters', file: File, dryRun: boolean, createClients: boolean, clearConflicts: boolean): Observable<ImportResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    let p = new HttpParams().set('dryRun', dryRun ? 'true' : 'false');
    if (createClients) p = p.set('createClients', 'true');
    if (clearConflicts) p = p.set('clearConflicts', 'true');
    return this.http.post<ImportResult>(`${this.base}/import/${kind}`, body, { params: p });
  }
  importTemplate(kind: 'clients' | 'matters', format: 'xlsx' | 'csv'): Observable<Blob> {
    return this.http.get(`${this.base}/import/${kind}/template`, { params: new HttpParams().set('format', format), responseType: 'blob' });
  }
  escalations(): Observable<EscalationItem[]> { return this.http.get<EscalationItem[]>(`${this.base}/escalations`); }
  sendEscalations(): Observable<EscalationResult> { return this.http.post<EscalationResult>(`${this.base}/escalations/send`, {}); }
  deadlinesPage(f: { mine?: boolean; assigneeId?: number | null; from?: string; to?: string; includeDone?: boolean } & PageParams): Observable<PageResult<Deadline>> {
    let p = new HttpParams().set('mine', !!f.mine).set('includeDone', !!f.includeDone);
    if (f.assigneeId) p = p.set('assigneeId', f.assigneeId);
    if (f.from) p = p.set('from', f.from);
    if (f.to) p = p.set('to', f.to);
    if (f.size) p = p.set('page', f.page ?? 0).set('size', f.size);
    return getPaged<Deadline>(this.http, `${this.base}/deadlines`, p);
  }
  contactsPage(q: string, f: PageParams): Observable<PageResult<Contact>> {
    let p = new HttpParams();
    if (q) p = p.set('q', q);
    if (f.size) p = p.set('page', f.page ?? 0).set('size', f.size);
    return getPaged<Contact>(this.http, `${this.base}/contacts`, p);
  }
  trustReceiptPdf(txId: number): Observable<Blob> { return this.http.get(`${this.base}/trust/${txId}/receipt`, { responseType: 'blob' }); }
  trustStatementPdf(matterId: number, from: string | null, to: string | null): Observable<Blob> {
    let p = new HttpParams();
    if (from) p = p.set('from', from);
    if (to) p = p.set('to', to);
    return this.http.get(`${this.base}/matters/${matterId}/trust/statement`, { params: p, responseType: 'blob' });
  }
  trustEmailStatement(matterId: number): Observable<{ sentTo: string; message: string }> { return this.http.post<{ sentTo: string; message: string }>(`${this.base}/matters/${matterId}/trust/statement/email`, {}); }
  reconciliationPdf(id: number): Observable<Blob> { return this.http.get(`${this.base}/trust/reconciliation/${id}/print`, { responseType: 'blob' }); }
  clientsPage(f: PageParams): Observable<PageResult<ClientRow>> {
    let p = new HttpParams();
    if (f.size) p = p.set('page', f.page ?? 0).set('size', f.size);
    return getPaged<ClientRow>(this.http, `${this.base}/clients`, p);
  }
  clients(): Observable<ClientRow[]> { return this.http.get<ClientRow[]>(`${this.base}/clients`); }

  // ---- billing
  billing(id: number): Observable<MatterBilling> { return this.http.get<MatterBilling>(`${this.base}/matters/${id}/billing`); }
  saveTerms(id: number, p: FeeTermsPayload): Observable<MatterBilling> { return this.http.put<MatterBilling>(`${this.base}/matters/${id}/fee-terms`, p); }
  setRate(id: number, userId: number, hourlyRate: number): Observable<MatterBilling> { return this.http.put<MatterBilling>(`${this.base}/matters/${id}/rates`, { userId, hourlyRate }); }
  removeRate(id: number, userId: number): Observable<MatterBilling> { return this.http.delete<MatterBilling>(`${this.base}/matters/${id}/rates/${userId}`); }
  approveTime(id: number, ids: number[]): Observable<MatterBilling> { return this.http.post<MatterBilling>(`${this.base}/matters/${id}/time/approve`, { ids }); }
  rejectTime(id: number, ids: number[], reason: string): Observable<MatterBilling> { return this.http.post<MatterBilling>(`${this.base}/matters/${id}/time/reject`, { ids, reason }); }
  addStage(id: number, p: { description: string; amount: number | null; dueDate: string | null }): Observable<MatterBilling> { return this.http.post<MatterBilling>(`${this.base}/matters/${id}/stages`, p); }
  deleteStage(id: number, stageId: number): Observable<MatterBilling> { return this.http.delete<MatterBilling>(`${this.base}/matters/${id}/stages/${stageId}`); }
  addCost(id: number, p: CostPayload): Observable<MatterBilling> { return this.http.post<MatterBilling>(`${this.base}/matters/${id}/costs`, p); }
  deleteCost(id: number, costId: number): Observable<MatterBilling> { return this.http.delete<MatterBilling>(`${this.base}/matters/${id}/costs/${costId}`); }
  previewBill(id: number, p: BillRequestPayload): Observable<BillPreview> { return this.http.post<BillPreview>(`${this.base}/matters/${id}/bill-preview`, p); }
  bill(id: number, p: BillRequestPayload): Observable<BillResult> { return this.http.post<BillResult>(`${this.base}/matters/${id}/bill`, p); }
  requestRetainer(id: number, p: { amount: number | null; dueDate: string | null; notes: string | null }): Observable<BillResult> { return this.http.post<BillResult>(`${this.base}/matters/${id}/retainer-request`, p); }
  releaseBill(id: number, billId: number): Observable<MatterBilling> { return this.http.post<MatterBilling>(`${this.base}/matters/${id}/bills/${billId}/release`, {}); }
  billingOverview(openOnly: boolean): Observable<BillingOverviewRow[]> { return this.http.get<BillingOverviewRow[]>(`${this.base}/billing/overview`, { params: new HttpParams().set('openOnly', openOnly) }); }

  // ---- client money (trust account)
  trustOverview(): Observable<TrustOverview> { return this.http.get<TrustOverview>(`${this.base}/trust/overview`); }
  matterTrust(id: number): Observable<MatterTrust> { return this.http.get<MatterTrust>(`${this.base}/matters/${id}/trust`); }
  trustDeposit(id: number, p: TrustDepositPayload): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/matters/${id}/trust/deposit`, p); }
  trustPayout(id: number, p: TrustPayoutPayload): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/matters/${id}/trust/payout`, p); }
  trustFeeTransfer(id: number, p: { invoiceId: number; amount: number | null; purpose: string | null }): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/matters/${id}/trust/fee-transfer`, p); }
  trustApprove(txId: number, note: string | null): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/trust/${txId}/approve`, { note }); }
  trustReject(txId: number, note: string): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/trust/${txId}/reject`, { note }); }
  trustCancel(txId: number): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/trust/${txId}/cancel`, {}); }
  trustReverse(txId: number, reason: string): Observable<MatterTrust> { return this.http.post<MatterTrust>(`${this.base}/trust/${txId}/reverse`, { reason }); }
  trustReconciliation(): Observable<TrustReconciliationView> { return this.http.get<TrustReconciliationView>(`${this.base}/trust/reconciliation`); }
  trustReconcile(p: TrustReconcilePayload): Observable<TrustReconciliationView> { return this.http.post<TrustReconciliationView>(`${this.base}/trust/reconciliation`, p); }
  // ---- documents
  matterDocuments(id: number, archived: boolean): Observable<DocList> { return this.http.get<DocList>(`${this.base}/matters/${id}/documents`, { params: new HttpParams().set('archived', archived) }); }
  uploadDocument(id: number, form: FormData): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/matters/${id}/documents`, form); }
  generateDocument(id: number, p: { templateId: number; name: string | null; confidential: boolean }): Observable<DocGenerateResult> { return this.http.post<DocGenerateResult>(`${this.base}/matters/${id}/documents/generate`, p); }
  documentVersions(id: number): Observable<DocVersions> { return this.http.get<DocVersions>(`${this.base}/documents/${id}/versions`); }
  newDocumentVersion(id: number, form: FormData): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/documents/${id}/versions`, form); }
  downloadDocument(id: number, version: number | null): Observable<Blob> {
    return this.http.get(`${this.base}/documents/${id}/download`, { params: version == null ? new HttpParams() : new HttpParams().set('version', version), responseType: 'blob' });
  }
  updateDocument(id: number, p: { name: string; category: DocCategory; description: string | null }): Observable<DocItem> { return this.http.put<DocItem>(`${this.base}/documents/${id}`, p); }
  setDocumentFlags(id: number, p: { confidential?: boolean; sharedWithClient?: boolean }): Observable<DocItem> { return this.http.put<DocItem>(`${this.base}/documents/${id}/flags`, p); }
  checkoutDocument(id: number): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/documents/${id}/checkout`, {}); }
  releaseDocument(id: number): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/documents/${id}/release`, {}); }
  archiveDocument(id: number): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/documents/${id}/archive`, {}); }
  restoreDocument(id: number): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/documents/${id}/restore`, {}); }
  docTemplates(activeOnly: boolean): Observable<DocTemplate[]> { return this.http.get<DocTemplate[]>(`${this.base}/doc-templates`, { params: new HttpParams().set('activeOnly', activeOnly) }); }
  docPlaceholders(): Observable<DocPlaceholder[]> { return this.http.get<DocPlaceholder[]>(`${this.base}/doc-templates/placeholders`); }
  saveDocTemplate(id: number | null, p: DocTemplatePayload): Observable<DocTemplate> {
    return id == null ? this.http.post<DocTemplate>(`${this.base}/doc-templates`, p) : this.http.put<DocTemplate>(`${this.base}/doc-templates/${id}`, p);
  }
  uploadTemplateFile(id: number, form: FormData): Observable<DocTemplate> { return this.http.post<DocTemplate>(`${this.base}/doc-templates/${id}/file`, form); }
  downloadTemplateFile(id: number): Observable<Blob> { return this.http.get(`${this.base}/doc-templates/${id}/file`, { responseType: 'blob' }); }
  deleteDocTemplate(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/doc-templates/${id}`); }
  // ---- client portal (the firm's side)
  matterPortal(id: number): Observable<PortalAdmin> { return this.http.get<PortalAdmin>(`${this.base}/matters/${id}/portal`); }
  saveMatterPortal(id: number, p: PortalSettingsPayload): Observable<PortalAdmin> { return this.http.put<PortalAdmin>(`${this.base}/matters/${id}/portal`, p); }
  postPortalUpdate(id: number, text: string): Observable<PortalAdmin> { return this.http.put<PortalAdmin>(`${this.base}/matters/${id}/portal/update`, { text }); }
  setDeadlinePortal(deadlineId: number, visible: boolean): Observable<PortalAdmin> { return this.http.put<PortalAdmin>(`${this.base}/deadlines/${deadlineId}/portal`, { visible }); }
  invitePortal(id: number, name: string, email: string): Observable<PortalInviteResult> { return this.http.post<PortalInviteResult>(`${this.base}/matters/${id}/portal/accounts`, { name, email }); }
  reinvitePortal(id: number, accountId: number): Observable<PortalInviteResult> { return this.http.post<PortalInviteResult>(`${this.base}/matters/${id}/portal/accounts/${accountId}/invite`, {}); }
  setPortalAccountActive(id: number, accountId: number, active: boolean): Observable<PortalAdmin> { return this.http.put<PortalAdmin>(`${this.base}/matters/${id}/portal/accounts/${accountId}/active`, { active }); }
  replyPortal(id: number, body: string): Observable<PortalAdmin> { return this.http.post<PortalAdmin>(`${this.base}/matters/${id}/portal/messages`, { body }); }
  readPortalMessages(id: number): Observable<PortalAdmin> { return this.http.post<PortalAdmin>(`${this.base}/matters/${id}/portal/messages/read`, {}); }
  reviewDocument(id: number, accept: boolean, note: string | null): Observable<DocItem> { return this.http.post<DocItem>(`${this.base}/documents/${id}/review`, { accept, note }); }
}

/** Shared labels so every screen words these the same way. */
export const PARTY_ROLE_LABEL: Record<PartyRole, string> = {
  OPPOSING_PARTY: 'Opposing party', OPPOSING_COUNSEL: 'Opposing counsel', CO_CLIENT: 'Co-client', WITNESS: 'Witness', EXPERT: 'Expert',
  JUDGE_OR_OFFICIAL: 'Judge / official', RELATED: 'Related party', OTHER: 'Other',
};
export interface CalendarEntry { kind: 'DEADLINE' | 'TASK'; id: number; matterId: number; matterNumber: string; matterTitle: string; title: string; type: string | null; date: string; time: string | null;
  status: string; assignedToName: string | null; overdue: boolean; done: boolean; }
export interface EscalationItem { deadlineId: number; matterId: number; matterNumber: string; matterTitle: string; title: string; dueDate: string; daysLeft: number; stage: number; stageLabel: string;
  recipients: string[]; problem: string | null; }
export interface EscalationResult { mailsSent: number; deadlines: number; skipped: number; items: EscalationItem[]; }
export interface PaperChecks { closed: boolean; status: string; openDeadlines: number; openTasks: number; unbilledTime: number; unbilledCosts: number; outstanding: number;
  trustHeld: number; trustEnabled: boolean; clientEmail: string | null; mailReady: boolean; feeTermsSet: boolean; canWork: boolean; }
export interface PaperRequest { scope?: string; terms?: string; notes?: string; letterDate?: string | null; }
export type PaperKind = 'cover' | 'engagement' | 'closing';
export const DEADLINE_TYPE_LABEL: Record<DeadlineType, string> = {
  COURT_DATE: 'Court date', HEARING: 'Hearing', FILING: 'Filing', LIMITATION: 'Limitation date', MEETING: 'Meeting', OTHER: 'Other',
};
export const BILLING_LABEL: Record<BillingType, string> = {
  HOURLY: 'Hourly', FIXED: 'Fixed fee', RETAINER: 'Retainer', CONTINGENCY: 'Contingency', PRO_BONO: 'Pro bono / legal aid',
};
export const DOC_CATEGORY_LABEL: Record<DocCategory, string> = {
  PLEADING: 'Pleading / court filing', CONTRACT: 'Contract / agreement', CORRESPONDENCE: 'Letter / correspondence', EVIDENCE: 'Evidence', COURT_ORDER: 'Court order / judgment',
  CLIENT_ID: 'Client identity / KYC', RESEARCH: 'Research / opinion', FINANCIAL: 'Financial', OTHER: 'Other',
};
export const TRUST_TYPE_LABEL: Record<TrustType, string> = { DEPOSIT: 'Money received', PAYOUT: 'Payout', FEE_TRANSFER: 'Fees paid from trust', DEPOSIT_REVERSAL: 'Deposit taken back' };
export const STATUS_LABEL: Record<MatterStatus, string> = { OPEN: 'Open', ON_HOLD: 'On hold', CLOSED: 'Closed' };
export const NOTE_KIND_LABEL: Record<NoteKind, string> = { NOTE: 'Note', CALL: 'Call', MEETING: 'Meeting', EMAIL: 'E-mail', COURT: 'Court' };
export const TEAM_ROLE_LABEL: Record<TeamRole, string> = { LEAD: 'Lead lawyer', ASSOCIATE: 'Associate', PARALEGAL: 'Paralegal', SUPPORT: 'Support' };

// ---- fee collections
export interface Aging { current: number; days1to30: number; days31to60: number; days61to90: number; over90: number; }
export interface CollectionRow { billId: number; invoiceId: number; invoiceNumber: string; matterId: number; matterNumber: string; matterTitle: string; clientId: number; clientName: string; clientEmail: string | null;
  invoiceDate: string; dueDate: string; dueAssumed: boolean; amount: number; paid: number; outstanding: number; daysOverdue: number; reminderCount: number; lastRemindedAt: string | null; }
export interface Collections { rows: CollectionRow[]; totalOutstanding: number; totalOverdue: number; overdueInvoices: number; clientsOwing: number; aging: Aging; canRemind: boolean; mailReady: boolean; defaultTermDays: number; }
export interface ReminderItem { clientId: number; clientName: string; email: string | null; invoices: number; outstanding: number; problem: string | null; }
export interface RemindResult { mails: number; invoices: number; skipped: number; items: ReminderItem[]; }

// ---- importing clients and matters
export interface ImportRow { row: number; label: string; message: string; }
export interface ImportResult { kind: string; dryRun: boolean; fileName: string; rows: number; ok: number; skipped: number; clientsAdded: number; problems: ImportRow[]; notes: ImportRow[]; sample: string[];
  columns: string[]; ignoredColumns: string[]; message: string; }
