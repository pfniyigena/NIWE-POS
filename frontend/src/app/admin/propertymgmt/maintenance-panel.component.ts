import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  MAINTENANCE_PRIORITIES, MAINTENANCE_STATUS_LABEL, MaintenanceRow, PmMaintenanceApiService, maintLabel, maintenanceActive, maintenancePriorityBadge, maintenanceStatusBadge,
} from '../../core/api/pm-maintenance-api.service';

/** The repair requests of one unit, property or tenant, newest open ones first, with a link to log another. */
@Component({
  selector: 'app-maintenance-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'Maintenance' | translate }} <span class="text-muted" style="font-size:0.85rem;" *ngIf="openCount()">{{ '· {p0} open' | translate: { p0: (openCount()) } }}</span></h4>
        <div class="flex gap-2">
          <a class="btn btn-sm btn-outline-primary" *ngIf="canManage && unitId" routerLink="/admin/propertymgmt/maintenance/new" [queryParams]="{ unitId: unitId }">{{ '+ Request' | translate }}</a>
          <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/propertymgmt/maintenance" [queryParams]="filterParams()">{{ 'All' | translate }}</a>
        </div>
      </div>
      <table class="table-sm stack" *ngIf="rows.length; else none">
        <thead><tr><th>{{ 'Request' | translate }}</th><th>{{ 'Job' | translate }}</th><th *ngIf="!unitId">{{ 'Unit' | translate }}</th><th>{{ 'Priority' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Reported' | translate }}</th><th class="text-end">{{ 'Cost' | translate }}</th></tr></thead>
        <tbody><tr *ngFor="let r of rows">
          <td data-label="Request"><a [routerLink]="['/admin/propertymgmt/maintenance', r.id]"><strong>{{ r.code }}</strong></a></td>
          <td data-label="Job">{{ r.title }}</td>
          <td *ngIf="!unitId" data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', r.unitId]">{{ r.propertyName }} · {{ r.unitNo }}</a></td>
          <td data-label="Priority"><span class="badge" [ngClass]="pbadge(r.priority)">{{ plabel(r.priority) }}</span></td>
          <td data-label="Status"><span class="badge" [ngClass]="sbadge(r.status)">{{ slabel(r.status) }}</span><span class="badge badge-danger" *ngIf="r.overdue" style="margin-left:0.3rem;">{{ 'Overdue' | translate }}</span></td>
          <td style="white-space:nowrap;" data-label="Reported">{{ r.reportedDate | date: 'd MMM y' }}</td>
          <td class="text-end" data-label="Cost">{{ (r.actualCost ?? r.estimatedCost) != null ? ((r.actualCost ?? r.estimatedCost) | number: '1.0-0') : '' }}</td>
        </tr></tbody>
      </table>
      <ng-template #none><div class="text-muted">{{ loaded ? ('No maintenance requests.' | translate) : ('Loading…' | translate) }}</div></ng-template>
      <div class="text-muted mt-1" style="font-size:0.8rem;" *ngIf="more > 0">{{ '{p0} older request{p1} not shown.' | translate: { p0: (more), p1: (more === 1 ? '' : 's') } }}</div>
    </div>
  `,
})
export class MaintenancePanelComponent implements OnChanges {
  @Input() unitId: number | null = null;
  @Input() propertyId: number | null = null;
  @Input() tenantId: number | null = null;
  @Input() canManage = false;
  rows: MaintenanceRow[] = [];
  all: MaintenanceRow[] = [];
  more = 0;
  loaded = false;

  constructor(private api: PmMaintenanceApiService) {}

  ngOnChanges(): void {
    this.api.list({ unitId: this.unitId, propertyId: this.propertyId, tenantId: this.tenantId }).subscribe({
      next: (l) => { this.all = l; this.rows = l.slice(0, 10); this.more = Math.max(0, l.length - 10); this.loaded = true; },
      error: () => { this.loaded = true; },
    });
  }

  filterParams(): Record<string, number> {
    return this.unitId ? { unitId: this.unitId } : this.tenantId ? { tenantId: this.tenantId } : this.propertyId ? { propertyId: this.propertyId } : {};
  }

  openCount(): number { return this.all.filter((r) => maintenanceActive(r.status)).length; }
  sbadge(s: string): string { return maintenanceStatusBadge(s); }
  pbadge(s: string): string { return maintenancePriorityBadge(s); }
  slabel(s: string): string { return (MAINTENANCE_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  plabel(s: string): string { return maintLabel(MAINTENANCE_PRIORITIES, s); }
}
