import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Contract, HrApiService } from '../../core/api/hr-api.service';

/** Contracts > Contract list: every contract with the actions to activate, renew, terminate and delete. */
@Component({
  selector: 'app-contract-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Contracts</h3>
        <a class="btn btn-primary" routerLink="/admin/hr/contracts/new">+ New contract</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="alert alert-warning" *ngIf="expiring.length">
        <strong>{{ expiring.length }} contract(s) end within 60 days:</strong>
        <span *ngFor="let c of expiring; let last = last"> {{ c.employeeName }} ({{ c.endDate }}){{ last ? '' : ',' }}</span>
      </div>

      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All contracts</option>
          <option value="DRAFT">Draft</option><option value="ACTIVE">Active</option><option value="EXPIRED">Expired</option><option value="TERMINATED">Terminated</option>
        </select>
      </div>

      <form class="card mb-3" style="padding:1rem;" *ngIf="action" (ngSubmit)="confirmAction()">
        <strong class="mb-2">{{ action.kind === 'terminate' ? 'Terminate' : 'Renew' }} contract {{ action.contract.contractNumber }} — {{ action.contract.employeeName }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <ng-container *ngIf="action.kind === 'terminate'">
            <div class="form-group"><label class="form-label">Last day</label><app-date-picker name="td" [(ngModel)]="action.date"></app-date-picker></div>
            <div class="form-group" style="min-width:16rem;"><label class="form-label">Reason</label><input class="form-control" name="tr" [(ngModel)]="action.reason" /></div>
          </ng-container>
          <ng-container *ngIf="action.kind === 'renew'">
            <div class="form-group"><label class="form-label">New start</label><app-date-picker name="rs" [(ngModel)]="action.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">New end (empty = open)</label><app-date-picker name="re" [(ngModel)]="action.endDate"></app-date-picker></div>
            <div class="form-group"><label class="form-label">Salary (empty = same)</label><input class="form-control" type="number" min="0" style="width:10rem;" name="rsal" [(ngModel)]="action.salary" /></div>
          </ng-container>
          <button class="btn btn-primary" type="submit">Confirm</button>
          <button class="btn btn-link" type="button" (click)="action = null">Cancel</button>
        </div>
        <div class="text-muted" style="font-size:0.8rem;" *ngIf="action.kind === 'renew'">Renewing creates a new draft contract with the same allowances. Activate it when it's agreed.</div>
      </form>

      <table class="table-sm">
        <thead><tr><th>No.</th><th>Employee</th><th>Type</th><th>From</th><th>To</th><th>Salary</th><th>Status</th><th></th></tr></thead>
        <tbody><tr *ngFor="let c of contracts">
          <td>{{ c.contractNumber }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="c.renewsContractNumber">renews {{ c.renewsContractNumber }}</div></td>
          <td><a [routerLink]="['/admin/hr/employees', c.employeeId]">{{ c.employeeName }}</a></td>
          <td>{{ typeLabel(c.type) }}</td><td>{{ c.startDate }}</td>
          <td>{{ c.endDate || 'Open' }}<span class="badge badge-warning" *ngIf="c.daysToExpiry !== null && c.daysToExpiry <= 60" style="margin-left:0.3rem;">{{ c.daysToExpiry < 0 ? 'ended' : c.daysToExpiry + ' days left' }}</span></td>
          <td>{{ c.baseSalary | number: '1.0-2' }}<span class="text-muted" *ngIf="c.components.length"> + {{ c.components.length }} item(s)</span></td>
          <td><span class="badge" [class.badge-success]="c.status === 'ACTIVE'" [class.badge-warning]="c.status === 'DRAFT'" [class.badge-secondary]="c.status === 'EXPIRED' || c.status === 'TERMINATED'">{{ c.status }}</span></td>
          <td class="flex gap-1" style="flex-wrap:wrap;">
            <a class="btn btn-sm btn-outline-primary" *ngIf="c.status === 'DRAFT' || c.status === 'ACTIVE'" [routerLink]="['/admin/hr/contracts', c.id, 'edit']">Edit</a>
            <button class="btn btn-sm btn-success" type="button" *ngIf="c.status === 'DRAFT'" (click)="activate(c)">Activate</button>
            <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="c.status === 'ACTIVE' || c.status === 'EXPIRED'" (click)="startAction('renew', c)">Renew</button>
            <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="c.status === 'ACTIVE'" (click)="startAction('terminate', c)">Terminate</button>
            <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="c.status === 'DRAFT'" (click)="remove(c)">Delete</button>
          </td>
        </tr></tbody>
      </table>
      <p class="text-muted" *ngIf="!contracts.length">No contracts found.</p>
    </app-shell>
  `,
})
export class ContractListComponent implements OnInit {
  contracts: Contract[] = [];
  expiring: Contract[] = [];
  status = '';
  error: string | null = null;
  action: { kind: 'terminate' | 'renew'; contract: Contract; date: string; endDate: string | null; salary: number | null; reason: string } | null = null;

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.contracts({ status: this.status }).subscribe({
      next: (c) => {
        this.contracts = c;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load contracts'),
    });
    this.api.expiringContracts(60).subscribe({ next: (c) => (this.expiring = c), error: () => {} });
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
