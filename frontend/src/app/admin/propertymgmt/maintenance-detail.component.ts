import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ShellComponent } from '../../shared/shell.component';
import {
  COST_BEARERS, CostBearer, MAINTENANCE_CATEGORIES, MAINTENANCE_PRIORITIES, MAINTENANCE_STATUS_LABEL, MaintenanceDto, PmMaintenanceApiService, maintLabel, maintenanceActive,
  maintenancePriorityBadge, maintenanceStatusBadge,
} from '../../core/api/pm-maintenance-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();
const today = (): string => new Date().toISOString().slice(0, 10);

/** Property Management > a maintenance request: what is wrong, the steps of the job (start, hold, finish, cancel), its work order, its cost and who pays. */
@Component({
  selector: 'app-maintenance-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PmDocumentsPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="note">{{ note }}</div>
      <div *ngIf="!m && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="m">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/propertymgmt/maintenance" style="font-size:0.85rem;">← Maintenance</a>
            <h3>{{ m.request.code }} · {{ m.request.title }}
              <span class="badge" [ngClass]="sbadge(m.request.status)" style="font-size:0.8rem; vertical-align:middle;">{{ slabel(m.request.status) }}</span>
              <span class="badge" [ngClass]="pbadge(m.request.priority)" style="font-size:0.8rem; vertical-align:middle;">{{ plabel(m.request.priority) }}</span>
              <span class="badge badge-danger" *ngIf="m.request.overdue" style="font-size:0.8rem; vertical-align:middle;">Overdue</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="m.canManage">
            <button class="btn btn-primary" type="button" *ngIf="status === 'OPEN' || status === 'ON_HOLD'" (click)="start()" [disabled]="busy">{{ status === 'ON_HOLD' ? 'Resume' : 'Start the job' }}</button>
            <button class="btn btn-primary" type="button" *ngIf="active()" (click)="openComplete()">Mark as done</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="status === 'OPEN' || status === 'IN_PROGRESS'" (click)="hold()" [disabled]="busy">Put on hold</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="!active()" (click)="reopen()" [disabled]="busy">Reopen</button>
            <a class="btn btn-outline-secondary" *ngIf="active()" [routerLink]="['/admin/propertymgmt/maintenance', m.request.id, 'edit']">Edit</a>
            <button class="btn btn-outline-danger" type="button" *ngIf="active()" (click)="panel = 'cancel'">Cancel the request</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="canDelete()" (click)="remove()" [disabled]="busy">Delete</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'complete'">
          <h4 class="mb-2">Mark the job as done</h4>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Finished on</label><input class="form-control" type="date" name="cd" [(ngModel)]="c.completedDate" [max]="todayStr" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">What it cost</label><input class="form-control" type="number" min="0" step="0.01" name="cc" [(ngModel)]="c.actualCost" /></div>
            <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Who pays</label>
              <select class="form-select" name="cb" [(ngModel)]="c.costBearer"><option *ngFor="let b of bearers" [ngValue]="b.value">{{ b.label }}</option></select></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">What was done</label><textarea class="form-control" rows="2" name="cr" [(ngModel)]="c.resolution" maxlength="2000"></textarea></div>
          <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="c.costBearer === 'TENANT'">The tenant pays: once it is done you can invoice the tenant from this page.</div>
          <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="complete()" [disabled]="busy">Save as done</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">Cancel</button></div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'cancel'">
          <h4 class="mb-2">Cancel the request</h4>
          <div class="form-group mb-2"><label class="form-label">Why *</label><input class="form-control" name="cx" [(ngModel)]="reason" maxlength="2000" /></div>
          <div class="flex gap-2"><button class="btn btn-danger" type="button" (click)="cancel()" [disabled]="busy">Cancel the request</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">Keep it</button></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">The request</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">Unit</td><td><a [routerLink]="['/admin/propertymgmt/units', m.request.unitId]">{{ m.request.propertyName }} · {{ m.request.unitNo }}</a></td></tr>
              <tr><td class="text-muted">Owner</td><td><a *ngIf="m.request.ownerId" [routerLink]="['/admin/propertymgmt/owners', m.request.ownerId]">{{ m.request.ownerName }}</a></td></tr>
              <tr><td class="text-muted">Tenant</td><td><a *ngIf="m.request.tenantId" [routerLink]="['/admin/propertymgmt/tenants', m.request.tenantId]">{{ m.request.tenantName }}</a><span *ngIf="m.leaseId"> · lease <a [routerLink]="['/admin/propertymgmt/leases', m.leaseId]">{{ m.leaseCode }}</a></span></td></tr>
              <tr><td class="text-muted">Kind of job</td><td>{{ cat(m.request.category) }}</td></tr>
              <tr><td class="text-muted">Reported</td><td>{{ m.request.reportedDate | date: 'd MMM y' }}<span *ngIf="m.reportedBy"> by {{ m.reportedBy }}</span>
                <span class="text-muted" *ngIf="m.request.daysOpen != null"> · {{ m.request.daysOpen }} day{{ m.request.daysOpen === 1 ? '' : 's' }}{{ active() ? ' open' : '' }}</span></td></tr>
              <tr *ngIf="m.request.scheduledDate"><td class="text-muted">Planned for</td><td>{{ m.request.scheduledDate | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="m.request.dueDate"><td class="text-muted">Due by</td><td [style.color]="m.request.overdue ? '#b91c1c' : ''">{{ m.request.dueDate | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="m.startedDate"><td class="text-muted">Started</td><td>{{ m.startedDate | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="m.request.completedDate"><td class="text-muted">Finished</td><td>{{ m.request.completedDate | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="m.request.contractorName || m.contractorPhone"><td class="text-muted">Contractor</td><td>{{ m.request.contractorName }}{{ m.contractorPhone ? ' · ' + m.contractorPhone : '' }}</td></tr>
            </tbody></table>
            <div class="mt-2" *ngIf="m.description" style="white-space:pre-wrap;">{{ m.description }}</div>
            <div class="mt-2" *ngIf="m.resolution"><strong>{{ status === 'CANCELLED' ? 'Cancelled because:' : 'What was done:' }}</strong>
              <div style="white-space:pre-wrap;">{{ m.resolution }}</div></div>
          </div>

          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Cost</h4>
            <div style="font-size:1.6rem;" *ngIf="m.request.actualCost != null">{{ m.request.actualCost | number: '1.0-0' }}</div>
            <div class="text-muted" *ngIf="m.request.estimatedCost != null">Estimated {{ m.request.estimatedCost | number: '1.0-0' }}</div>
            <div class="text-muted" *ngIf="m.request.actualCost == null && m.request.estimatedCost == null">No cost recorded yet.</div>
            <div class="mt-1">Paid by <strong>{{ bearer(m.request.costBearer) }}</strong></div>
            <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="m.request.costBearer === 'OWNER'">Charged to the owner.</div>

            <ng-container *ngIf="m.request.costBearer === 'TENANT'">
              <hr />
              <h4 class="mb-2">Invoice to the tenant</h4>
              <ng-container *ngIf="m.invoiceId; else noInv">
                <div><a [routerLink]="['/admin/invoices', m.invoiceId]"><strong>{{ m.request.invoiceNumber }}</strong></a> · {{ m.invoiceStatus }}</div>
                <div>{{ m.invoiceTotal | number: '1.0-0' }} invoiced · {{ m.invoicePaid | number: '1.0-0' }} paid · <strong>{{ m.invoiceOutstanding | number: '1.0-0' }} owed</strong></div>
                <a class="btn btn-sm btn-outline-primary mt-1" *ngIf="m.invoiceOutstanding" [routerLink]="['/admin/invoices', m.invoiceId, 'record-payment']">Record payment</a>
              </ng-container>
              <ng-template #noInv>
                <button class="btn btn-sm btn-primary" type="button" *ngIf="m.canInvoiceTenant && m.canManage" (click)="invoiceTenant()" [disabled]="busy">Invoice the tenant</button>
                <div class="text-muted" style="font-size:0.85rem;" *ngIf="!m.canInvoiceTenant">Once the job is done with a cost and a tenant, you can invoice the tenant here.</div>
              </ng-template>
            </ng-container>
          </div>

          <div class="card" style="padding:1rem;" *ngIf="m.workOrdersEnabled || m.workOrderId">
            <h4 class="mb-2">Work order</h4>
            <ng-container *ngIf="m.workOrderId; else noWo">
              <div><a [routerLink]="['/admin/work-orders', m.workOrderId]"><strong>{{ m.request.workOrderNumber }}</strong></a> · {{ m.workOrderStatus }}</div>
              <button class="btn btn-sm btn-outline-secondary mt-1" type="button" *ngIf="m.canManage" (click)="unlink()" [disabled]="busy">Unlink</button>
            </ng-container>
            <ng-template #noWo>
              <div class="text-muted mb-1" style="font-size:0.9rem;">Raise a work order to assign the job, time it and track materials in Work Orders.</div>
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="m.canManage && active()" (click)="raise()" [disabled]="busy">Raise a work order</button>
            </ng-template>
          </div>
        </div>
        <app-pm-documents-panel subjectType="MAINTENANCE" [subjectId]="m.request.id" [canManage]="m.canManage" defaultCategory="PHOTO"></app-pm-documents-panel>
        <div class="text-muted mt-2" style="font-size:0.8rem;">Logged by {{ m.createdByName || '—' }} on {{ m.createdAt | date: 'd MMM y' }}.</div>
      </ng-container>
    </app-shell>
  `,
})
export class MaintenanceDetailComponent implements OnInit {
  m: MaintenanceDto | null = null;
  error: string | null = null;
  note: string | null = null;
  busy = false;
  panel: 'complete' | 'cancel' | null = null;
  reason = '';
  bearers = COST_BEARERS;
  todayStr = today();
  c = { completedDate: today(), actualCost: null as number | null, resolution: '', costBearer: 'OWNER' as CostBearer };

  constructor(private api: PmMaintenanceApiService, private route: ActivatedRoute, private router: Router) {}

  get status(): string { return this.m?.request.status ?? ''; }

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (m) => (this.m = m), error: (e) => (this.error = e?.error?.message ?? 'Could not load the request') });
  }

  active(): boolean { return maintenanceActive(this.status); }
  canDelete(): boolean { return !!this.m && (this.status === 'OPEN' || this.status === 'CANCELLED') && !this.m.workOrderId && !this.m.invoiceId; }
  sbadge(s: string): string { return maintenanceStatusBadge(s); }
  pbadge(s: string): string { return maintenancePriorityBadge(s); }
  slabel(s: string): string { return (MAINTENANCE_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  plabel(s: string): string { return maintLabel(MAINTENANCE_PRIORITIES, s); }
  cat(s: string): string { return maintLabel(MAINTENANCE_CATEGORIES, s); }
  bearer(s: string): string { return maintLabel(COST_BEARERS, s); }

  /** Run a step, show the new state of the request, or the server's reason it could not be done. */
  private step(call: Observable<MaintenanceDto>, done?: string): void {
    this.busy = true;
    this.error = null;
    this.note = null;
    call.subscribe({
      next: (m) => { this.busy = false; this.m = m; this.panel = null; if (done) this.note = done; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; },
    });
  }

  start(): void { if (this.m) this.step(this.api.start(this.m.request.id)); }
  hold(): void { if (this.m) this.step(this.api.hold(this.m.request.id)); }
  reopen(): void { if (this.m) this.step(this.api.reopen(this.m.request.id)); }
  raise(): void { if (this.m) this.step(this.api.raiseWorkOrder(this.m.request.id), 'Work order raised.'); }
  unlink(): void { if (this.m) this.step(this.api.unlinkWorkOrder(this.m.request.id), 'Work order unlinked.'); }
  invoiceTenant(): void { if (this.m) this.step(this.api.invoiceTenant(this.m.request.id), 'The tenant has been invoiced.'); }

  openComplete(): void {
    if (!this.m) return;
    this.c = { completedDate: today(), actualCost: this.m.request.actualCost ?? this.m.request.estimatedCost, resolution: this.m.resolution ?? '', costBearer: this.m.request.costBearer };
    this.panel = 'complete';
  }

  complete(): void {
    if (!this.m) return;
    const c = this.c;
    if (!c.completedDate) { this.error = 'Give the date it was finished.'; return; }
    this.step(this.api.complete(this.m.request.id, { completedDate: c.completedDate, actualCost: num(c.actualCost), resolution: txt(c.resolution), costBearer: c.costBearer }), 'Marked as done.');
  }

  cancel(): void {
    if (!this.m) return;
    if (!txt(this.reason)) { this.error = 'Say why it is cancelled.'; return; }
    this.step(this.api.cancel(this.m.request.id, txt(this.reason)));
    this.reason = '';
  }

  remove(): void {
    if (!this.m || !confirm('Delete this request? This cannot be undone.')) return;
    this.busy = true;
    this.api.remove(this.m.request.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/maintenance']),
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not delete the request'; },
    });
  }
}
