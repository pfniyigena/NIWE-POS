import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { LawExportComponent } from './law-export.component';
import { LawImportComponent } from './law-import.component';
import { AuthService } from '../../core/auth.service';
import { PAGE_SIZE } from '../../core/api/paged';
import { BILLING_LABEL, LawApiService, LawSummary, MatterRow, Option, STATUS_LABEL } from '../../core/api/law-api.service';

/** Law Firm > Matters: every matter the person can see, with what is overdue and what still needs a conflict check. */
@Component({
  selector: 'app-law-matter-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, LawExportComponent, LawImportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Matters' | translate }}<span class="text-muted" *ngIf="clientId" style="font-size:0.9rem;"> {{ '· one client' | translate }}</span></h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <button class="btn btn-outline-secondary imp-open-clients" type="button" *ngIf="canManage" (click)="openImport('clients')">{{ 'Import clients' | translate }}</button>
          <button class="btn btn-outline-secondary imp-open-matters" type="button" *ngIf="canManage" (click)="openImport('matters')">{{ 'Import matters' | translate }}</button>
          <a class="btn btn-primary" routerLink="/admin/law/matters/new" [queryParams]="clientId ? { clientId: clientId } : {}">{{ '+ Open matter' | translate }}</a>
        </div>
      </div>
      <app-law-import *ngIf="importKind" [kind]="importKind" (imported)="load()" (closed)="importKind = null"></app-law-import>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid mb-3" *ngIf="s">
        <div class="stat-tile"><div class="stat-label">{{ 'Open matters' | translate }}</div><div class="stat-value">{{ s.openMatters }}</div><div class="text-muted" style="font-size:0.75rem;" *ngIf="s.onHold">{{ '{p0} on hold' | translate: { p0: (s.onHold) } }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Deadlines this week' | translate }}</div><div class="stat-value">{{ s.deadlinesThisWeek }}</div></div>
        <div class="stat-tile" [class.warn]="s.overdueDeadlines > 0"><div class="stat-label">{{ 'Overdue deadlines' | translate }}</div><div class="stat-value">{{ s.overdueDeadlines }}</div></div>
        <div class="stat-tile" [class.warn]="s.overdueTasks > 0"><div class="stat-label">{{ 'Overdue tasks' | translate }}</div><div class="stat-value">{{ s.overdueTasks }}</div></div>
        <div class="stat-tile" [class.warn]="s.conflictsNotCleared > 0"><div class="stat-label">{{ 'Conflicts not cleared' | translate }}</div><div class="stat-value">{{ s.conflictsNotCleared }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">{{ 'All statuses' | translate }}</option><option value="OPEN">{{ 'Open' | translate }}</option><option value="ON_HOLD">{{ 'On hold' | translate }}</option><option value="CLOSED">{{ 'Closed' | translate }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="pa" [(ngModel)]="practiceArea" (ngModelChange)="load()">
          <option value="">{{ 'All practice areas' | translate }}</option><option *ngFor="let p of areas" [value]="p">{{ p }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ld" [(ngModel)]="leadId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any lead lawyer' | translate }}</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.name }}</option>
        </select>
        <input class="form-control" style="width:16rem;" [placeholder]="'Search number, title, client, court case no.' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
        <a class="btn btn-outline-secondary" routerLink="/admin/law/matters" *ngIf="clientId">{{ 'Clear client filter' | translate }}</a>
        <app-law-export kind="matters" [filters]="{ status: status, q: q.trim(), practiceArea: practiceArea, leadId: leadId, clientId: clientId }"></app-law-export>
      </div>

      <table class="stack table-sm">
        <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'Matter' | translate }}</th><th>{{ 'Client' | translate }}</th><th>{{ 'Practice area' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Lead' | translate }}</th><th>{{ 'Billing' | translate }}</th><th>{{ 'Next deadline' | translate }}</th><th>{{ 'Tasks' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let m of list">
            <td data-label="Number"><a [routerLink]="['/law/matters', m.id]">{{ m.number }}</a></td>
            <td data-label="Matter">{{ m.title }}
              <span class="badge badge-secondary" *ngIf="m.restricted" style="margin-left:0.3rem;" [title]="'Only the team and admins can open it' | translate">{{ 'Restricted' | translate }}</span>
              <span class="badge badge-danger" *ngIf="!m.conflictCleared && m.status !== 'CLOSED'" style="margin-left:0.3rem;">{{ 'Conflicts not cleared' | translate }}</span></td>
            <td data-label="Client">{{ m.clientName }}</td><td data-label="Practice area">{{ m.practiceArea || '—' }}</td>
            <td data-label="Status"><span class="badge" [class.badge-success]="m.status === 'OPEN'" [class.badge-warning]="m.status === 'ON_HOLD'" [class.badge-secondary]="m.status === 'CLOSED'">{{ statusLabel(m.status) }}</span></td>
            <td data-label="Lead">{{ m.leadName || '—' }}</td><td data-label="Billing">{{ billing(m.billingType) }}</td>
            <td [style.color]="m.overdueDeadlines ? '#c0392b' : ''" data-label="Next deadline">{{ m.nextDeadline || '—' }}<span *ngIf="m.overdueDeadlines"> {{ '({p0} overdue)' | translate: { p0: (m.overdueDeadlines) } }}</span></td>
            <td data-label="Tasks">{{ m.openTasks }}<span *ngIf="m.overdueTasks" style="color:#c0392b;"> {{ '({p0} late)' | translate: { p0: (m.overdueTasks) } }}</span></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length">{{ 'No matters match. Open one to start.' | translate }}</p>
    </app-shell>
  `,
})
export class LawMatterListComponent implements OnInit {
  list: MatterRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  s: LawSummary | null = null;
  staff: Option[] = [];
  areas: string[] = [];
  status = 'OPEN';
  practiceArea = '';
  leadId: number | null = null;
  q = '';
  clientId: number | null = null;
  error: string | null = null;

  canManage: boolean;
  importKind: 'clients' | 'matters' | null = null;

  constructor(private api: LawApiService, private route: ActivatedRoute, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  openImport(k: 'clients' | 'matters'): void { this.importKind = this.importKind === k ? null : k; }

  ngOnInit(): void {
    const c = this.route.snapshot.queryParamMap.get('clientId');
    this.clientId = c ? Number(c) : null;
    if (this.clientId) this.status = '';
    this.api.summary().subscribe({ next: (s) => (this.s = s), error: () => {} });
    this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
    this.api.settings().subscribe({ next: (s) => (this.areas = s.practiceAreas), error: () => {} });
    this.load();
  }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.mattersPage({ status: this.status, q: this.q.trim(), practiceArea: this.practiceArea, leadId: this.leadId, clientId: this.clientId, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the matters'),
    });
  }

  statusLabel(s: string): string { return (STATUS_LABEL as Record<string, string>)[s] ?? s; }
  billing(b: string): string { return (BILLING_LABEL as Record<string, string>)[b] ?? b; }
}
