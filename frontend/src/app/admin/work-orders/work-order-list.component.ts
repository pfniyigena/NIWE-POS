import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { WorkOrderApiService, WorkOrderSummary, WorkOrderSummaryCounts } from '../../core/api/work-order-api.service';
import { WorkOrderTabsComponent } from './work-order-tabs.component';

import { T } from '../../core/i18n/t';
/** Work Orders > Work orders: every job with its status, assignee, due date and what is waiting to be invoiced. */
@Component({
  selector: 'app-work-order-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, WorkOrderTabsComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-2">
        <h3>{{ 'Work orders' | translate }}<span class="text-muted" *ngIf="assetId" style="font-size:0.9rem;"> {{ '· for one asset' | translate }}</span></h3>
        <a class="btn btn-primary" routerLink="/admin/work-orders/new">{{ '+ New work order' | translate }}</a>
      </div>
      <app-work-order-tabs></app-work-order-tabs>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid mb-3" *ngIf="s">
        <div class="stat-tile"><div class="stat-label">{{ 'Open' | translate }}</div><div class="stat-value">{{ s.open }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'In progress' | translate }}</div><div class="stat-value">{{ s.inProgress }}</div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="s.onHold">{{ '{p0} on hold' | translate: { p0: (s.onHold) } }}</div></div>
        <div class="stat-tile" [class.warn]="s.overdue > 0"><div class="stat-label">{{ 'Overdue' | translate }}</div><div class="stat-value">{{ s.overdue }}</div>
          <a href="javascript:void(0)" style="font-size:0.8rem;" *ngIf="s.overdue" (click)="overdueOnly = !overdueOnly; load()">{{ overdueOnly ? ('Show all' | translate) : ('Show only these' | translate) }}</a></div>
        <div class="stat-tile" [class.warn]="s.readyToInvoice > 0"><div class="stat-label">{{ 'Ready to invoice' | translate }}</div><div class="stat-value">{{ s.readyToInvoice }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">{{ 'All statuses' | translate }}</option>
          <option value="OPEN">{{ 'Open' | translate }}</option><option value="IN_PROGRESS">{{ 'In progress' | translate }}</option><option value="ON_HOLD">{{ 'On hold' | translate }}</option>
          <option value="COMPLETED">{{ 'Completed' | translate }}</option><option value="CANCELLED">{{ 'Cancelled' | translate }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ty" [(ngModel)]="type" (ngModelChange)="load()">
          <option value="">{{ 'Customer and internal' | translate }}</option>
          <option value="CUSTOMER">{{ 'Customer jobs' | translate }}</option><option value="INTERNAL">{{ 'Internal maintenance' | translate }}</option>
        </select>
        <input class="form-control" style="width:16rem;" [placeholder]="'Search number, title, customer, asset' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
        <a class="btn btn-outline-secondary" routerLink="/admin/work-orders" *ngIf="assetId">{{ 'Clear asset filter' | translate }}</a>
      </div>

      <table class="table-sm">
        <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'Job' | translate }}</th><th>{{ 'For' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Assigned to' | translate }}</th><th>{{ 'Due' | translate }}</th><th>{{ 'Checklist' | translate }}</th><th class="text-end">{{ 'Hours' | translate }}</th><th>{{ 'Invoice' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let w of list">
            <td><a [routerLink]="['/admin/work-orders', w.id]">{{ w.number }}</a></td>
            <td>{{ w.title }}
              <span class="badge badge-danger" *ngIf="w.priority === 'URGENT'" style="margin-left:0.3rem;">{{ 'Urgent' | translate }}</span>
              <span class="badge badge-warning" *ngIf="w.priority === 'HIGH'" style="margin-left:0.3rem;">{{ 'High' | translate }}</span>
              <span class="badge badge-warning" *ngIf="w.approvalRequired && (w.approvalStatus === 'NONE' || w.approvalStatus === 'REQUESTED')" style="margin-left:0.3rem;">{{ w.approvalStatus === 'REQUESTED' ? ('Waiting for customer' | translate) : ('Needs estimate' | translate) }}</span>
              <span class="badge badge-danger" *ngIf="w.approvalRequired && w.approvalStatus === 'REJECTED'" style="margin-left:0.3rem;">{{ 'Declined' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="w.scheduleId" style="margin-left:0.3rem;">{{ 'Planned' | translate }}</span></td>
            <td>
              <span *ngIf="w.type === 'CUSTOMER'">{{ w.customerName || '—' }}</span>
              <span *ngIf="w.type === 'INTERNAL'" class="text-muted">{{ 'Internal' | translate }}<span *ngIf="w.assetName"> · {{ w.assetName }}</span></span>
            </td>
            <td><span class="badge" [class.badge-success]="w.status === 'COMPLETED'" [class.badge-primary]="w.status === 'IN_PROGRESS'"
                      [class.badge-warning]="w.status === 'ON_HOLD'" [class.badge-secondary]="w.status === 'OPEN' || w.status === 'CANCELLED'">{{ label(w.status) }}</span></td>
            <td>{{ w.assignedToName || '—' }}</td>
            <td [style.color]="w.overdue ? '#c0392b' : ''">{{ w.dueDate || '—' }}<span *ngIf="w.overdue"> {{ '(late)' | translate }}</span></td>
            <td>{{ w.checklistTotal ? w.checklistDone + ' / ' + w.checklistTotal : '—' }}</td>
            <td class="text-end">{{ w.hours | number: '1.0-2' }}</td>
            <td><span *ngIf="w.invoiceNumber">{{ w.invoiceNumber }}</span><span *ngIf="!w.invoiceNumber && w.readyToInvoice" class="badge badge-warning">{{ 'Ready' | translate }}</span>
              <span *ngIf="!w.invoiceNumber && !w.readyToInvoice" class="text-muted">—</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length">{{ 'No work orders match. Create one to start a job.' | translate }}</p>
    </app-shell>
  `,
})
export class WorkOrderListComponent implements OnInit {
  list: WorkOrderSummary[] = [];
  s: WorkOrderSummaryCounts | null = null;
  status = '';
  type = '';
  q = '';
  overdueOnly = false;
  assetId: number | null = null;
  error: string | null = null;

  constructor(private api: WorkOrderApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const a = this.route.snapshot.queryParamMap.get('assetId');
    this.assetId = a ? Number(a) : null;
    this.load();
    this.api.summary().subscribe({ next: (s) => (this.s = s), error: () => {} });
  }

  load(): void {
    this.error = null;
    this.api.list({ status: this.status, type: this.type, assetId: this.assetId, overdue: this.overdueOnly, q: this.q.trim() }).subscribe({
      next: (l) => (this.list = l),
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the work orders')),
    });
  }

  label(s: string): string {
    return s === 'IN_PROGRESS' ? 'In progress' : s === 'ON_HOLD' ? 'On hold' : s.charAt(0) + s.slice(1).toLowerCase();
  }
}
