import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { PropertyMgmtApiService, PropertyRow } from '../../core/api/propertymgmt-api.service';
import {
  MAINTENANCE_CATEGORIES, MAINTENANCE_PRIORITIES, MAINTENANCE_STATUS_LABEL, MaintenanceFilter, MaintenanceRow, PmMaintenanceApiService, maintLabel,
  maintenancePriorityBadge, maintenanceStatusBadge,
} from '../../core/api/pm-maintenance-api.service';

/** Property Management > Maintenance: repair requests for all units, by what needs doing (open, overdue, done), with their cost and who pays. */
@Component({
  selector: 'app-maintenance-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Maintenance' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-primary" routerLink="/admin/propertymgmt/maintenance/new" [queryParams]="unitId ? { unitId: unitId } : {}">{{ '+ Request' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/propertymgmt/reports">{{ 'Reports' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button *ngFor="let t of tabs" type="button" class="btn btn-sm" [ngClass]="tab === t.value ? 'btn-primary' : 'btn-outline-secondary'" (click)="pick(t.value)">{{ t.label }}</button>
        <select class="form-select" style="width:auto;" name="pr" [(ngModel)]="propertyId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any property' | translate }}</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="pri" [(ngModel)]="priority" (ngModelChange)="load()">
          <option value="">{{ 'Any priority' | translate }}</option><option *ngFor="let p of priorities" [value]="p.value">{{ p.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="cat" [(ngModel)]="category" (ngModelChange)="load()">
          <option value="">{{ 'Any kind of job' | translate }}</option><option *ngFor="let c of categories" [value]="c.value">{{ c.label }}</option>
        </select>
        <input class="form-control" style="width:14rem;" [placeholder]="'Search job, unit, tenant, contractor' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" (blur)="load()" />
      </div>
      <div class="text-muted mb-2" *ngIf="unitId || tenantId">{{ 'Showing one {p0}\\'s requests.' | translate: { p0: (unitId ? 'unit' : 'tenant') } }} <a routerLink="/admin/propertymgmt/maintenance">{{ 'Show all' | translate }}</a></div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Request' | translate }}</th><th>{{ 'Job' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'Tenant' | translate }}</th><th>{{ 'Priority' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Reported' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Cost' | translate }}</th><th>{{ 'Paid by' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of pageRows()">
            <td data-label="Request"><a [routerLink]="['/admin/propertymgmt/maintenance', r.id]"><strong>{{ r.code }}</strong></a></td>
            <td data-label="Job">{{ r.title }}<div class="text-muted" style="font-size:0.75rem;">{{ cat(r.category) }}<span *ngIf="r.contractorName"> · {{ r.contractorName }}</span></div></td>
            <td data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', r.unitId]">{{ r.propertyName }} · {{ r.unitNo }}</a></td>
            <td data-label="Tenant"><a *ngIf="r.tenantId" [routerLink]="['/admin/propertymgmt/tenants', r.tenantId]">{{ r.tenantName }}</a></td>
            <td data-label="Priority"><span class="badge" [ngClass]="pbadge(r.priority)">{{ plabel(r.priority) }}</span></td>
            <td data-label="Status"><span class="badge" [ngClass]="sbadge(r.status)">{{ slabel(r.status) }}</span></td>
            <td style="white-space:nowrap;" data-label="Reported">{{ r.reportedDate | date: 'd MMM y' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="r.daysOpen != null">{{ '{p0} day{p1}' | translate: { p0: (r.daysOpen), p1: (r.daysOpen === 1 ? '' : 's') } }}</div></td>
            <td style="white-space:nowrap;" [style.color]="r.overdue ? '#b91c1c' : ''" data-label="Due">{{ r.dueDate ? (r.dueDate | date: 'd MMM y') : '' }}<div style="font-size:0.75rem;" *ngIf="r.overdue">{{ 'overdue' | translate }}</div></td>
            <td class="text-end" data-label="Cost">{{ cost(r) != null ? (cost(r) | number: '1.0-0') : '' }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="r.actualCost == null && r.estimatedCost != null">{{ 'estimate' | translate }}</div></td>
            <td data-label="Paid by">{{ bearer(r.costBearer) }}</td>
            <td style="white-space:nowrap;" data-label=""><a *ngIf="r.workOrderNumber" class="badge badge-secondary" [routerLink]="['/admin/propertymgmt/maintenance', r.id]" [title]="'Work order' | translate">{{ r.workOrderNumber }}</a>
              <a *ngIf="r.invoiceNumber" class="badge badge-secondary" [routerLink]="['/admin/propertymgmt/maintenance', r.id]" [title]="'Invoice to the tenant' | translate">{{ r.invoiceNumber }}</a></td>
          </tr>
        </tbody>
        <tfoot *ngIf="list.length"><tr><td colspan="8" class="text-end"><strong>{{ '{p0} request{p1}' | translate: { p0: (list.length), p1: (list.length === 1 ? '' : 's') } }}</strong></td><td class="text-end" data-label="Cost"><strong>{{ total() | number: '1.0-0' }}</strong></td><td colspan="2"></td></tr></tfoot>
      </table>
      <app-pager [page]="page" [size]="size" [total]="list.length" (pageChange)="page = $event"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'Nothing here.' | translate }}</p>
    </app-shell>
  `,
})
export class MaintenanceListComponent implements OnInit {
  list: MaintenanceRow[] = [];
  page = 0;
  readonly size = PAGE_SIZE;
  properties: PropertyRow[] = [];
  tabs = [{ value: 'OPEN', label: 'Open' }, { value: 'OVERDUE', label: 'Overdue' }, { value: 'COMPLETED', label: 'Done' }, { value: 'CANCELLED', label: 'Cancelled' }, { value: '', label: 'All' }];
  priorities = MAINTENANCE_PRIORITIES;
  categories = MAINTENANCE_CATEGORIES;
  tab = 'OPEN';
  propertyId: number | null = null;
  unitId: number | null = null;
  tenantId: number | null = null;
  priority = '';
  category = '';
  q = '';
  loaded = false;
  error: string | null = null;

  constructor(private api: PmMaintenanceApiService, private pm: PropertyMgmtApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    if (qp.has('tab')) this.tab = qp.get('tab') ?? '';
    const n = (k: string): number | null => (qp.get(k) ? Number(qp.get(k)) : null);
    this.unitId = n('unitId');
    this.tenantId = n('tenantId');
    this.propertyId = n('propertyId');
    if (this.unitId || this.tenantId) this.tab = '';
    this.pm.properties({ activeOnly: true }).subscribe({ next: (p) => (this.properties = p), error: () => undefined });
    this.load();
  }

  sbadge(s: string): string { return maintenanceStatusBadge(s); }
  pbadge(s: string): string { return maintenancePriorityBadge(s); }
  slabel(s: string): string { return (MAINTENANCE_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  plabel(s: string): string { return maintLabel(MAINTENANCE_PRIORITIES, s); }
  cat(s: string): string { return maintLabel(MAINTENANCE_CATEGORIES, s); }
  bearer(s: string): string { return s === 'OWNER' ? 'Owner' : s === 'TENANT' ? 'Tenant' : s === 'AGENCY' ? 'Agency' : s; }
  cost(r: MaintenanceRow): number | null { return r.actualCost ?? r.estimatedCost; }
  pageRows(): MaintenanceRow[] { return this.list.slice(this.page * this.size, (this.page + 1) * this.size); }
  total(): number { return this.list.reduce((a, r) => a + (this.cost(r) ?? 0), 0); }

  pick(v: string): void {
    this.tab = v;
    this.load();
  }

  load(): void {
    this.page = 0;
    const f: MaintenanceFilter = { q: this.q.trim(), priority: this.priority, category: this.category, propertyId: this.propertyId, unitId: this.unitId, tenantId: this.tenantId };
    if (this.tab === 'OPEN') f.openOnly = true;
    else if (this.tab === 'OVERDUE') f.overdueOnly = true;
    else if (this.tab) f.status = this.tab;
    this.api.list(f).subscribe({
      next: (l) => { this.list = l; this.loaded = true; this.error = null; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the maintenance requests'; },
    });
  }
}
