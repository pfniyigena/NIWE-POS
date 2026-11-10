import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { MaintenancePanelComponent } from './maintenance-panel.component';
import { TenantAccountComponent } from './tenant-account.component';
import { PmPaymentsPanelComponent } from './pm-payments-panel.component';
import { LEASE_STATE_LABEL, PropertyMgmtApiService, TenantDto, TenantKind, TenantPayload, leaseBadge, Arrears } from '../../core/api/propertymgmt-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';

const txt = (v: string | null | undefined): string => (v ?? '').trim();

/** Property Management > a tenant: contact and emergency details, the customer record, and every lease. */
@Component({
  selector: 'app-tenant-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, MaintenancePanelComponent, TenantAccountComponent, PmPaymentsPanelComponent, PmDocumentsPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!t && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="t">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/propertymgmt/tenants" style="font-size:0.85rem;">{{ '← Tenants' | translate }}</a>
            <h3>{{ t.name }} <span class="text-muted" style="font-size:0.9rem;">{{ t.code }}</span>
              <span class="badge badge-secondary" *ngIf="t.kind === 'COMPANY'" style="font-size:0.8rem;">{{ 'Company' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!t.active" style="font-size:0.8rem;">{{ 'Inactive' | translate }}</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="t.canManage">
            <a class="btn btn-primary" routerLink="/admin/propertymgmt/leases/new" [queryParams]="{ tenantId: t.id }" *ngIf="t.active">{{ '+ Lease' | translate }}</a>
            <button class="btn btn-outline-secondary" type="button" (click)="editing ? editing = false : startEdit()">{{ editing ? ('Cancel' | translate) : ('Edit' | translate) }}</button>
            <button class="btn btn-outline-secondary" type="button" (click)="toggleActive()" [disabled]="busy">{{ t.active ? ('Mark inactive' | translate) : ('Mark active' | translate) }}</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()" [disabled]="busy">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <form *ngIf="editing" class="card mb-3" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">{{ 'Who' | translate }}</label>
              <select class="form-select" name="k" [(ngModel)]="f.kind"><option value="INDIVIDUAL">{{ 'A person' | translate }}</option><option value="COMPANY">{{ 'A company' | translate }}</option></select></div>
            <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Name *' | translate }}</label><input class="form-control" name="n" [(ngModel)]="f.name" maxlength="200" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:12rem;" *ngIf="f.kind === 'COMPANY'"><label class="form-label">{{ 'Contact person' | translate }}</label><input class="form-control" name="cp" [(ngModel)]="f.contactPerson" maxlength="200" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="p" [(ngModel)]="f.phone" maxlength="40" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="e" [(ngModel)]="f.email" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'ID / registration no.' | translate }}</label><input class="form-control" name="id" [(ngModel)]="f.idNumber" maxlength="60" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Address' | translate }}</label><input class="form-control" name="ad" [(ngModel)]="f.address" maxlength="500" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Employer' | translate }}</label><input class="form-control" name="em" [(ngModel)]="f.employer" maxlength="200" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Emergency contact' | translate }}</label><input class="form-control" name="en" [(ngModel)]="f.emergencyName" maxlength="200" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Emergency phone' | translate }}</label><input class="form-control" name="ep" [(ngModel)]="f.emergencyPhone" maxlength="40" /></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Notes' | translate }}</label><textarea class="form-control" rows="2" name="nt" [(ngModel)]="f.notes" maxlength="2000"></textarea></div>
          <div><button class="btn btn-primary" type="submit" [disabled]="busy">{{ busy ? ('Saving…' | translate) : ('Save changes' | translate) }}</button></div>
        </form>

        <div class="card mb-3" style="padding:1rem; max-width:46rem;">
          <h4 class="mb-2">{{ 'Details' | translate }}</h4>
          <table class="table-sm"><tbody>
            <tr *ngIf="t.contactPerson"><td class="text-muted" style="width:10rem;">{{ 'Contact person' | translate }}</td><td>{{ t.contactPerson }}</td></tr>
            <tr><td class="text-muted" style="width:10rem;">{{ 'Phone' | translate }}</td><td>{{ t.phone || '—' }}</td></tr>
            <tr><td class="text-muted">{{ 'E-mail' | translate }}</td><td>{{ t.email || '—' }}</td></tr>
            <tr><td class="text-muted">{{ 'ID / registration' | translate }}</td><td>{{ t.idNumber || '—' }}</td></tr>
            <tr><td class="text-muted">{{ 'Address' | translate }}</td><td>{{ t.address || '—' }}</td></tr>
            <tr><td class="text-muted">{{ 'Employer' | translate }}</td><td>{{ t.employer || '—' }}</td></tr>
            <tr><td class="text-muted">{{ 'Emergency contact' | translate }}</td><td>{{ t.emergencyName || '—' }}{{ t.emergencyPhone ? ' · ' + t.emergencyPhone : '' }}</td></tr>
            <tr><td class="text-muted">{{ 'Customer record' | translate }}</td><td><a *ngIf="t.customerId" [routerLink]="['/admin/customers', t.customerId]">{{ 'Open the customer' | translate }}</a><span *ngIf="!t.customerId" class="text-muted">{{ 'Made when the first lease is signed. Rent is invoiced to this customer.' | translate }}</span></td></tr>
            <tr *ngIf="t.notes"><td class="text-muted">{{ 'Notes' | translate }}</td><td style="white-space:pre-wrap;">{{ t.notes }}</td></tr>
          </tbody></table>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="ar && ar.rows.length">
          <div class="flex justify-between align-center mb-2"><h4>{{ 'Rent owed past due' | translate }}</h4><strong style="color:#b91c1c;">{{ ar.total | number: '1.0-0' }}</strong></div>
          <table class="stack table-sm">
            <thead><tr><th>{{ 'Unit' | translate }}</th><th>{{ 'Period' | translate }}</th><th>{{ 'Due' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Owed' | translate }}</th><th>{{ 'Late' | translate }}</th><th></th></tr></thead>
            <tbody><tr *ngFor="let c of ar.rows[0].items">
              <td data-label="Unit">{{ c.propertyName }} · {{ c.unitNo }}</td>
              <td style="white-space:nowrap;" data-label="Period">{{ c.periodStart | date: 'd MMM y' }} – {{ c.periodEnd | date: 'd MMM y' }}</td>
              <td style="white-space:nowrap;" data-label="Due">{{ c.dueDate | date: 'd MMM y' }}</td>
              <td data-label="Invoice"><a *ngIf="c.invoiceId" [routerLink]="['/admin/invoices', c.invoiceId]">{{ c.invoiceNumber }}</a></td>
              <td class="text-end" data-label="Owed">{{ c.outstanding | number: '1.0-0' }}</td><td data-label="Late">{{ '{p0} days' | translate: { p0: (c.daysOverdue) } }}</td>
              <td style="text-align:right;" data-label=""><a class="btn btn-sm btn-outline-primary" *ngIf="c.invoiceId" [routerLink]="['/admin/invoices', c.invoiceId, 'record-payment']">{{ 'Record payment' | translate }}</a></td>
            </tr></tbody>
          </table>
        </div>

        <div class="card" style="padding:1rem;">
          <h4 class="mb-2">{{ 'Leases' | translate }}</h4>
          <table class="stack table-sm" *ngIf="t.leases.length; else nol">
            <thead><tr><th>{{ 'Lease' | translate }}</th><th>{{ 'Unit' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th class="text-end">{{ 'Rent' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let l of t.leases">
              <td data-label="Lease"><a [routerLink]="['/admin/propertymgmt/leases', l.id]"><strong>{{ l.code }}</strong></a></td>
              <td data-label="Unit"><a [routerLink]="['/admin/propertymgmt/units', l.unitId]">{{ l.propertyName }} · {{ l.unitNo }}</a></td>
              <td data-label="From">{{ l.startDate | date: 'd MMM y' }}</td><td data-label="To">{{ l.endDate | date: 'd MMM y' }}</td><td class="text-end" data-label="Rent">{{ l.rent | number: '1.0-0' }}</td>
              <td data-label="Status"><span class="badge" [ngClass]="lbadge(l.state)">{{ stateName(l.state) }}</span></td>
            </tr></tbody>
          </table>
          <ng-template #nol><div class="text-muted">{{ 'No leases yet.' | translate }}</div></ng-template>
        </div>
        <app-tenant-account [tenantId]="t.id" [email]="t.email" [canManage]="t.canManage"></app-tenant-account>
        <app-pm-payments-panel [tenantId]="t.id" [canManage]="t.canManage"></app-pm-payments-panel>
        <app-maintenance-panel [tenantId]="t.id" [canManage]="t.canManage"></app-maintenance-panel>
        <app-pm-documents-panel subjectType="TENANT" [subjectId]="t.id" [canManage]="t.canManage" defaultCategory="ID_DOCUMENT"></app-pm-documents-panel>
      </ng-container>
    </app-shell>
  `,
})
export class TenantDetailComponent implements OnInit {
  t: TenantDto | null = null;
  ar: Arrears | null = null;
  error: string | null = null;
  editing = false;
  busy = false;
  f = { kind: 'INDIVIDUAL' as TenantKind, name: '', contactPerson: '', phone: '', email: '', idNumber: '', address: '', employer: '', emergencyName: '', emergencyPhone: '', notes: '' };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.tenant(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (t) => { this.t = t; this.api.arrears({ tenantId: t.id }).subscribe({ next: (a) => (this.ar = a), error: () => (this.ar = null) }); }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the tenant') });
  }

  lbadge(s: string): string { return leaseBadge(s); }
  stateName(s: string): string { return (LEASE_STATE_LABEL as Record<string, string>)[s] ?? s; }

  startEdit(): void {
    const t = this.t!;
    this.f = { kind: t.kind, name: t.name, contactPerson: t.contactPerson ?? '', phone: t.phone ?? '', email: t.email ?? '', idNumber: t.idNumber ?? '', address: t.address ?? '', employer: t.employer ?? '',
      emergencyName: t.emergencyName ?? '', emergencyPhone: t.emergencyPhone ?? '', notes: t.notes ?? '' };
    this.editing = true;
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!txt(f.name)) { this.error = 'Give the tenant\'s name.'; return; }
    const p: TenantPayload = { kind: f.kind, name: txt(f.name), contactPerson: txt(f.contactPerson), phone: txt(f.phone), email: txt(f.email), idNumber: txt(f.idNumber), address: txt(f.address),
      employer: txt(f.employer), emergencyName: txt(f.emergencyName), emergencyPhone: txt(f.emergencyPhone), notes: txt(f.notes) };
    this.busy = true;
    this.api.updateTenant(this.t!.id, p).subscribe({
      next: (t) => { this.busy = false; this.t = t; this.editing = false; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not save the tenant'; },
    });
  }

  toggleActive(): void {
    this.busy = true;
    this.error = null;
    this.api.updateTenant(this.t!.id, { active: !this.t!.active }).subscribe({
      next: (t) => { this.busy = false; this.t = t; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not change the tenant'; },
    });
  }

  remove(): void {
    if (!confirm(`Delete ${this.t!.name}?`)) return;
    this.busy = true;
    this.error = null;
    this.api.deleteTenant(this.t!.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/tenants']),
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not delete the tenant'; },
    });
  }
}
