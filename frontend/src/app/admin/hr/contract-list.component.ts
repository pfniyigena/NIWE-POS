import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { ListFilterComponent } from '../../shared/list-filter.component';
import { ListState } from '../../core/api/list-state';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Contract, HrApiService } from '../../core/api/hr-api.service';

/** Contracts > Contract list: every contract with the actions to activate, renew, terminate and delete. */
@Component({
  selector: 'app-contract-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Contracts' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/hr/contracts/new">{{ '+ New contract' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>

      <div class="alert alert-warning" *ngIf="expiring.length">
        <strong>{{ '{p0} contract(s) end within 60 days:' | translate: { p0: (expiring.length) } }}</strong>
        <span *ngFor="let c of expiring; let last = last"> {{ c.employeeName }} ({{ c.endDate }}){{ last ? '' : ',' }}</span>
      </div>

      <app-list-filter exportKind="contracts" exportPath="/hr/contracts/export" [placeholder]="'Search contract no., employee, job title…' | translate" [statuses]="statuses" statusAll="All contracts" [dates]="false" [branches]="branches" [exportExtra]="extra" (cleared)="type = ''; extra = {}" (filterChange)="list.apply($event)"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select type-filter" style="width:auto;" name="ty" [(ngModel)]="type" (ngModelChange)="typeChanged()">
          <option value="">{{ 'All types' | translate }}</option>
          <option *ngFor="let t of types" [value]="t.value">{{ t.label }}</option>
        </select>
      </div>

      <form class="card mb-3" style="padding:1rem;" *ngIf="action" (ngSubmit)="confirmAction()">
        <strong class="mb-2">{{ '{p0} contract {p1} — {p2}' | translate: { p0: (action.kind === 'terminate' ? 'Terminate' : 'Renew'), p1: (action.contract.contractNumber), p2: (action.contract.employeeName) } }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <ng-container *ngIf="action.kind === 'terminate'">
            <div class="form-group"><label class="form-label">{{ 'Last day' | translate }}</label><app-date-picker name="td" [(ngModel)]="action.date"></app-date-picker></div>
            <div class="form-group" style="min-width:16rem;"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="tr" [(ngModel)]="action.reason" /></div>
          </ng-container>
          <ng-container *ngIf="action.kind === 'renew'">
            <div class="form-group"><label class="form-label">{{ 'New start' | translate }}</label><app-date-picker name="rs" [(ngModel)]="action.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'New end (empty = open)' | translate }}</label><app-date-picker name="re" [(ngModel)]="action.endDate"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Salary (empty = same)' | translate }}</label><input class="form-control" type="number" min="0" style="width:10rem;" name="rsal" [(ngModel)]="action.salary" /></div>
          </ng-container>
          <button class="btn btn-primary" type="submit">{{ 'Confirm' | translate }}</button>
          <button class="btn btn-link" type="button" (click)="action = null">{{ 'Cancel' | translate }}</button>
        </div>
        <div class="text-muted" style="font-size:0.8rem;" *ngIf="action.kind === 'renew'">{{ 'Renewing creates a new draft contract with the same allowances. Activate it when it\\'s agreed.' | translate }}</div>
      </form>

      <table class="table-sm">
        <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Employee' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Salary' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody><tr *ngFor="let c of list.rows">
          <td>{{ c.contractNumber }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="c.renewsContractNumber">{{ 'renews {p0}' | translate: { p0: (c.renewsContractNumber) } }}</div></td>
          <td><a [routerLink]="['/admin/hr/employees', c.employeeId]">{{ c.employeeName }}</a></td>
          <td>{{ typeLabel(c.type) }}</td><td>{{ c.startDate }}</td>
          <td>{{ c.endDate || ('Open' | translate) }}<span class="badge badge-warning" *ngIf="c.daysToExpiry !== null && c.daysToExpiry <= 60" style="margin-left:0.3rem;">{{ c.daysToExpiry < 0 ? ('ended' | translate) : ('{p0} days left' | translate: { p0: c.daysToExpiry }) }}</span></td>
          <td>{{ c.baseSalary | number: '1.0-2' }}<span class="text-muted" *ngIf="c.components.length"> {{ '+ {p0} item(s)' | translate: { p0: (c.components.length) } }}</span></td>
          <td><span class="badge" [class.badge-success]="c.status === 'ACTIVE'" [class.badge-warning]="c.status === 'DRAFT'" [class.badge-secondary]="c.status === 'EXPIRED' || c.status === 'TERMINATED'">{{ c.status }}</span></td>
          <td class="flex gap-1" style="flex-wrap:wrap;">
            <a class="btn btn-sm btn-outline-primary" *ngIf="c.status === 'DRAFT' || c.status === 'ACTIVE'" [routerLink]="['/admin/hr/contracts', c.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-sm btn-success" type="button" *ngIf="c.status === 'DRAFT'" (click)="activate(c)">{{ 'Activate' | translate }}</button>
            <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="c.status === 'ACTIVE' || c.status === 'EXPIRED'" (click)="startAction('renew', c)">{{ 'Renew' | translate }}</button>
            <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="c.status === 'ACTIVE'" (click)="startAction('terminate', c)">{{ 'Terminate' | translate }}</button>
            <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="c.status === 'DRAFT'" (click)="remove(c)">{{ 'Delete' | translate }}</button>
          </td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || type ? ('Nothing matches these filters.' | translate) : ('No contracts found.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ContractListComponent implements OnInit {
  expiring: Contract[] = [];
  branches: Branch[] = [];
  type = '';
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'DRAFT', label: 'Draft' }, { value: 'ACTIVE', label: 'Active' }, { value: 'EXPIRED', label: 'Expired' }, { value: 'TERMINATED', label: 'Terminated' }];
  types = [{ value: 'PERMANENT', label: 'Permanent' }, { value: 'FIXED_TERM', label: 'Fixed term' }, { value: 'PROBATION', label: 'Probation' }, { value: 'CASUAL', label: 'Casual' }, { value: 'INTERNSHIP', label: 'Internship' }];
  error: string | null = null;
  list = new ListState<Contract>((f, p, sz) => this.api.page<Contract>('contracts', f, p, sz, { type: this.type }), 'Failed to load contracts');
  action: { kind: 'terminate' | 'renew'; contract: Contract; date: string; endDate: string | null; salary: number | null; reason: string } | null = null;

  constructor(private api: HrApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.load();
  }

  load(): void {
    this.list.load();
    this.api.expiringContracts(60).subscribe({ next: (c) => (this.expiring = c), error: () => {} });
  }

  typeChanged(): void {
    this.extra = { type: this.type };
    this.list.page = 0;
    this.list.load();
  }

  typeLabel(t: string): string {
    return t.replace('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
  }

  activate(c: Contract): void {
    this.api.activateContract(c.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not activate the contract') });
  }

  startAction(kind: 'terminate' | 'renew', c: Contract): void {
    const today = new Date().toISOString().slice(0, 10);
    this.action = { kind, contract: c, date: kind === 'renew' ? (c.endDate ? this.dayAfter(c.endDate) : today) : today, endDate: null, salary: null, reason: '' };
  }

  private dayAfter(iso: string): string {
    const d = new Date(iso + 'T00:00:00Z');
    d.setUTCDate(d.getUTCDate() + 1);
    return d.toISOString().slice(0, 10);
  }

  confirmAction(): void {
    if (!this.action) return;
    const a = this.action;
    const call = a.kind === 'terminate'
      ? this.api.terminateContract(a.contract.id, a.date, a.reason || null)
      : this.api.renewContract(a.contract.id, a.date, a.endDate, a.salary);
    call.subscribe({
      next: () => {
        this.action = null;
        this.load();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not complete that'),
    });
  }

  remove(c: Contract): void {
    if (!confirm(`Delete draft contract ${c.contractNumber}?`)) return;
    this.api.deleteContract(c.id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the contract') });
  }
}
