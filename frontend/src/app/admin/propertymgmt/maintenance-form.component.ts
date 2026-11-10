import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PropertyMgmtApiService, TenantRow, UnitRow } from '../../core/api/propertymgmt-api.service';
import {
  COST_BEARERS, CostBearer, MAINTENANCE_CATEGORIES, MAINTENANCE_PRIORITIES, MaintenanceCategory, MaintenanceDto, MaintenancePayload, MaintenancePriority, PmMaintenanceApiService,
} from '../../core/api/pm-maintenance-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();
const today = (): string => new Date().toISOString().slice(0, 10);

/** Property Management > Maintenance > log a repair request, or change one that is still open. The unit cannot be changed once logged. */
@Component({
  selector: 'app-maintenance-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Change request ' + (m?.request?.code ?? '') : 'New maintenance request' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/propertymgmt/maintenance', id] : ['/admin/propertymgmt/maintenance']">{{ 'Cancel' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:16rem;"><label class="form-label">{{ 'Unit *' | translate }}</label>
            <select class="form-select" name="u" [(ngModel)]="f.unitId" [disabled]="!!id">
              <option [ngValue]="null">{{ '— choose —' | translate }}</option>
              <option *ngFor="let u of units" [ngValue]="u.id">{{ u.propertyName }} · {{ u.unitNo }}{{ u.tenantName ? ' (' + u.tenantName + ')' : '' }}</option>
            </select></div>
          <div class="form-group mb-2" style="flex:1; min-width:16rem;"><label class="form-label">{{ 'Tenant' | translate }}</label>
            <select class="form-select" name="t" [(ngModel)]="f.tenantId">
              <option [ngValue]="null">{{ id ? ('None' | translate) : ('The tenant now in the unit, if any' | translate) }}</option>
              <option *ngFor="let t of tenants" [ngValue]="t.id">{{ t.name }}{{ t.phone ? ' · ' + t.phone : '' }}</option>
            </select>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="!id && unitTenant()">{{ 'Will be recorded against {p0}.' | translate: { p0: (unitTenant()) } }}</div></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'What needs doing *' | translate }}</label><input class="form-control" name="ti" [(ngModel)]="f.title" maxlength="200" [placeholder]="'e.g. Leaking kitchen tap' | translate" /></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Kind of job' | translate }}</label>
            <select class="form-select" name="c" [(ngModel)]="f.category"><option *ngFor="let c of categories" [ngValue]="c.value">{{ c.label }}</option></select></div>
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Priority' | translate }}</label>
            <select class="form-select" name="p" [(ngModel)]="f.priority"><option *ngFor="let p of priorities" [ngValue]="p.value">{{ p.label }}</option></select></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Reported on' | translate }}</label><input class="form-control" type="date" name="rd" [(ngModel)]="f.reportedDate" [max]="todayStr" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Reported by' | translate }}</label><input class="form-control" name="rb" [(ngModel)]="f.reportedBy" maxlength="200" [placeholder]="'the tenant, a caretaker…' | translate" /></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Details' | translate }}</label><textarea class="form-control" rows="3" name="d" [(ngModel)]="f.description" maxlength="4000"></textarea></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Planned for' | translate }}</label><input class="form-control" type="date" name="sd" [(ngModel)]="f.scheduledDate" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Due by' | translate }}</label><input class="form-control" type="date" name="dd" [(ngModel)]="f.dueDate" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Contractor' | translate }}</label><input class="form-control" name="cn" [(ngModel)]="f.contractorName" maxlength="200" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Contractor phone' | translate }}</label><input class="form-control" name="cp" [(ngModel)]="f.contractorPhone" maxlength="40" /></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Estimated cost' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="ec" [(ngModel)]="f.estimatedCost" [placeholder]="id ? '0 clears' : ''" /></div>
          <div class="form-group mb-2" style="min-width:11rem;" *ngIf="id"><label class="form-label">{{ 'Actual cost' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="ac" [(ngModel)]="f.actualCost" [placeholder]="'0 clears' | translate" /></div>
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Who pays' | translate }}</label>
            <select class="form-select" name="cb" [(ngModel)]="f.costBearer"><option *ngFor="let b of bearers" [ngValue]="b.value">{{ b.label }}</option></select></div>
        </div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? ('Save changes' | translate) : ('Log the request' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class MaintenanceFormComponent implements OnInit {
  id: number | null = null;
  m: MaintenanceDto | null = null;
  units: UnitRow[] = [];
  tenants: TenantRow[] = [];
  categories = MAINTENANCE_CATEGORIES;
  priorities = MAINTENANCE_PRIORITIES;
  bearers = COST_BEARERS;
  todayStr = today();
  error: string | null = null;
  saving = false;
  f = {
    unitId: null as number | null, tenantId: null as number | null, title: '', category: 'OTHER' as MaintenanceCategory, priority: 'NORMAL' as MaintenancePriority, reportedDate: today(), reportedBy: '',
    description: '', scheduledDate: '', dueDate: '', contractorName: '', contractorPhone: '', estimatedCost: null as number | null, actualCost: null as number | null, costBearer: 'OWNER' as CostBearer,
  };

  constructor(private api: PmMaintenanceApiService, private pm: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const idParam = this.route.snapshot.paramMap.get('id');
    this.pm.tenants({ activeOnly: true }).subscribe({ next: (l) => (this.tenants = l), error: () => {} });
    this.pm.units({}).subscribe({
      next: (l) => {
        this.units = l;
        const u = Number(q.get('unitId'));
        if (!idParam && u && l.some((x) => x.id === u)) this.f.unitId = u;
      },
      error: () => {},
    });
    if (idParam) {
      this.id = Number(idParam);
      this.api.get(this.id).subscribe({
        next: (m) => {
          this.m = m;
          const r = m.request;
          if (r.status === 'COMPLETED' || r.status === 'CANCELLED') this.error = 'A finished request cannot be changed. Reopen it first.';
          this.f = {
            unitId: r.unitId, tenantId: r.tenantId, title: r.title, category: r.category, priority: r.priority, reportedDate: r.reportedDate, reportedBy: m.reportedBy ?? '', description: m.description ?? '',
            scheduledDate: r.scheduledDate ?? '', dueDate: r.dueDate ?? '', contractorName: r.contractorName ?? '', contractorPhone: m.contractorPhone ?? '', estimatedCost: r.estimatedCost,
            actualCost: r.actualCost, costBearer: r.costBearer,
          };
        },
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the request'),
      });
    }
  }

  unitTenant(): string | null {
    return this.units.find((x) => x.id === this.f.unitId)?.tenantName ?? null;
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!f.unitId) { this.error = 'Choose the unit.'; return; }
    if (!txt(f.title)) { this.error = 'Say what needs doing.'; return; }
    if (f.reportedDate && f.reportedDate > today()) { this.error = 'The report date cannot be in the future.'; return; }
    if (f.dueDate && f.reportedDate && f.dueDate < f.reportedDate) { this.error = 'It cannot be due before it was reported.'; return; }
    if (f.scheduledDate && f.reportedDate && f.scheduledDate < f.reportedDate) { this.error = 'It cannot be planned for before it was reported.'; return; }
    const p: MaintenancePayload = {
      title: txt(f.title), description: txt(f.description), category: f.category, priority: f.priority, reportedDate: f.reportedDate || null, reportedBy: txt(f.reportedBy),
      scheduledDate: f.scheduledDate || null, dueDate: f.dueDate || null, contractorName: txt(f.contractorName), contractorPhone: txt(f.contractorPhone), costBearer: f.costBearer,
    };
    if (this.id) {
      // On a change: 0 clears a cost, and the clear flags remove a tenant or a date.
      p.estimatedCost = num(f.estimatedCost) ?? 0;
      p.actualCost = num(f.actualCost) ?? 0;
      p.tenantId = f.tenantId;
      p.clearTenant = !f.tenantId;
      p.clearScheduled = !f.scheduledDate;
      p.clearDue = !f.dueDate;
    } else {
      p.unitId = f.unitId;
      p.tenantId = f.tenantId;
      p.estimatedCost = num(f.estimatedCost);
    }
    this.saving = true;
    const call = this.id ? this.api.update(this.id, p) : this.api.create(p);
    call.subscribe({
      next: (m) => { this.saving = false; this.router.navigate(['/admin/propertymgmt/maintenance', m.request.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the request'; },
    });
  }
}
