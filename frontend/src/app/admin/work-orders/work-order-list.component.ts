import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { WorkOrderApiService, WorkOrderSummary, WorkOrderSummaryCounts } from '../../core/api/work-order-api.service';

/** Work Orders > Work orders: every job with its status, assignee, due date and what is waiting to be invoiced. */
@Component({
  selector: 'app-work-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Work orders<span class="text-muted" *ngIf="assetId" style="font-size:0.9rem;"> · for one asset</span></h3>
        <a class="btn btn-primary" routerLink="/admin/work-orders/new">+ New work order</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="stat-grid mb-3" *ngIf="s">
        <div class="stat-tile"><div class="stat-label">Open</div><div class="stat-value">{{ s.open }}</div></div>
        <div class="stat-tile"><div class="stat-label">In progress</div><div class="stat-value">{{ s.inProgress }}</div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="s.onHold">{{ s.onHold }} on hold</div></div>
        <div class="stat-tile" [class.warn]="s.overdue > 0"><div class="stat-label">Overdue</div><div class="stat-value">{{ s.overdue }}</div>
          <a href="javascript:void(0)" style="font-size:0.8rem;" *ngIf="s.overdue" (click)="overdueOnly = !overdueOnly; load()">{{ overdueOnly ? 'Show all' : 'Show only these' }}</a></div>
        <div class="stat-tile" [class.warn]="s.readyToInvoice > 0"><div class="stat-label">Ready to invoice</div><div class="stat-value">{{ s.readyToInvoice }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option><option value="IN_PROGRESS">In progress</option><option value="ON_HOLD">On hold</option>
          <option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option>
        </select>
        <select class="form-select" style="width:auto;" name="ty" [(ngModel)]="type" (ngModelChange)="load()">
          <option value="">Customer and internal</option>
          <option value="CUSTOMER">Customer jobs</option><option value="INTERNAL">Internal maintenance</option>
        </select>
        <input class="form-control" style="width:16rem;" placeholder="Search number, title, customer, asset" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <a class="btn btn-outline-secondary" routerLink="/admin/work-orders" *ngIf="assetId">Clear asset filter</a>
      </div>

      <table class="table-sm">
        <thead><tr><th>Number</th><th>Job</th><th>For</th><th>Status</th><th>Assigned to</th><th>Due</th><th>Checklist</th><th class="text-end">Hours</th><th>Invoice</th></tr></thead>
        <tbody>
          <tr *ngFor="let w of list">
            <td><a [routerLink]="['/admin/work-orders', w.id]">{{ w.number }}</a></td>
            <td>{{ w.title }}
              <span class="badge badge-danger" *ngIf="w.priority === 'URGENT'" style="margin-left:0.3rem;">Urgent</span>
              <span class="badge badge-warning" *ngIf="w.priority === 'HIGH'" style="margin-left:0.3rem;">High</span></td>
            <td>
              <span *ngIf="w.type === 'CUSTOMER'">{{ w.customerName || '—' }}</span>
              <span *ngIf="w.type === 'INTERNAL'" class="text-muted">Internal<span *ngIf="w.assetName"> · {{ w.assetName }}</span></span>
            </td>
            <td><span class="badge" [class.badge-success]="w.status === 'COMPLETED'" [class.badge-primary]="w.status === 'IN_PROGRESS'"
                      [class.badge-warning]="w.status === 'ON_HOLD'" [class.badge-secondary]="w.status === 'OPEN' || w.status === 'CANCELLED'">{{ label(w.status) }}</span></td>
            <td>{{ w.assignedToName || '—' }}</td>
            <td [style.color]="w.overdue ? '#c0392b' : ''">{{ w.dueDate || '—' }}<span *ngIf="w.overdue"> (late)</span></td>
            <td>{{ w.checklistTotal ? w.checklistDone + ' / ' + w.checklistTotal : '—' }}</td>
            <td class="text-end">{{ w.hours | number: '1.0-2' }}</td>
            <td><span *ngIf="w.invoiceNumber">{{ w.invoiceNumber }}</span><span *ngIf="!w.invoiceNumber && w.readyToInvoice" class="badge badge-warning">Ready</span>
              <span *ngIf="!w.invoiceNumber && !w.readyToInvoice" class="text-muted">—</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!list.length">No work orders match. Create one to start a job.</p>
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
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the work orders'),
    });
  }

  label(s: string): string {
    return s === 'IN_PROGRESS' ? 'In progress' : s === 'ON_HOLD' ? 'On hold' : s.charAt(0) + s.slice(1).toLowerCase();
  }
}
