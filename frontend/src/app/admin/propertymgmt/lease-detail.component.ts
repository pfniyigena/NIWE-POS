import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { RentScheduleComponent } from './rent-schedule.component';
import { PmPaymentsPanelComponent } from './pm-payments-panel.component';
import { LEASE_STATE_LABEL, LeaseDto, PropertyMgmtApiService, RENT_FREQUENCIES, RenewPayload, leaseBadge, pmLabel } from '../../core/api/propertymgmt-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';
import { PmDepositPanelComponent } from './pm-deposit-panel.component';
import { PmLeaseChargesPanelComponent } from './pm-lease-charges-panel.component';
import { PmInspectionsPanelComponent } from './pm-inspections-panel.component';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();
const today = (): string => new Date().toISOString().slice(0, 10);

/** Property Management > a lease: what was agreed, and the steps in its life: sign, renew, end, or terminate early. */
@Component({
  selector: 'app-lease-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, RentScheduleComponent, PmPaymentsPanelComponent, PmDocumentsPanelComponent, PmDepositPanelComponent, PmInspectionsPanelComponent, PmLeaseChargesPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!l && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="l">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/propertymgmt/leases" style="font-size:0.85rem;">← Leases</a>
            <h3>Lease {{ l.code }}
              <span class="badge" [ngClass]="badge(l.state)" style="font-size:0.8rem; vertical-align:middle;">{{ stateName(l.state) }}</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="l.canManage">
            <button class="btn btn-primary" type="button" *ngIf="l.status === 'DRAFT'" (click)="open('sign')">Sign the lease</button>
            <button class="btn btn-primary" type="button" *ngIf="canRenew()" (click)="openRenew()">Renew</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="l.status === 'ACTIVE' && pastEnd()" (click)="end(false)" [disabled]="busy">Mark as ended</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="l.status === 'ACTIVE' && !pastEnd() && isAdmin" (click)="end(true)" [disabled]="busy" title="Administrator: close the lease before its end date without a termination record">End now</button>
            <button class="btn btn-outline-danger" type="button" *ngIf="l.status === 'ACTIVE'" (click)="open('terminate')">Terminate early</button>
            <a class="btn btn-outline-secondary" *ngIf="l.status === 'DRAFT' || l.status === 'ACTIVE'" [routerLink]="['/admin/propertymgmt/leases', l.id, 'edit']">Edit</a>
            <button class="btn btn-outline-danger" type="button" *ngIf="l.status === 'DRAFT'" (click)="remove()" [disabled]="busy">Delete</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'sign'">
          <h4 class="mb-2">Sign the lease</h4>
          <div class="text-muted mb-2" style="font-size:0.9rem;">The unit is let to {{ l.tenantName }} from {{ l.startDate | date: 'd MMM y' }}, The tenant becomes a customer, and the rent for every period of the lease is laid out so it can be invoiced.</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="max-width:12rem;"><label class="form-label">Signed on</label><input class="form-control" type="date" name="sd" [(ngModel)]="signedOn" /></div>
            <div class="form-group mb-2" style="max-width:16rem;"><label class="form-label">Bill rent from <span class="text-muted">(optional)</span></label><input class="form-control" type="date" name="bf" [(ngModel)]="billFrom" />
              <div class="text-muted" style="font-size:0.8rem;">Leave empty to bill from the start. Set it when the earlier rent was settled outside.</div></div>
          </div>
          <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="sign()" [disabled]="busy">Sign</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">Cancel</button></div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'renew'">
          <h4 class="mb-2">Renew the lease</h4>
          <div class="text-muted mb-2" style="font-size:0.9rem;">Makes a draft for the next term. This lease keeps running until its end date. The rent starts at {{ l.suggestedRenewalRent | number: '1.0-0' }}<span *ngIf="l.escalationPercent"> (the rent plus the yearly increase of {{ l.escalationPercent }}%)</span>.</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Starts</label><input class="form-control" type="date" name="rs" [(ngModel)]="rn.startDate" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Ends <span class="text-muted">(same length if empty)</span></label><input class="form-control" type="date" name="re" [(ngModel)]="rn.endDate" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Rent</label><input class="form-control" type="number" min="0" step="0.01" name="rr" [(ngModel)]="rn.rent" /></div>
          </div>
          <div class="flex gap-2"><button class="btn btn-primary" type="button" (click)="renew()" [disabled]="busy">Make the renewal</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">Cancel</button></div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;" *ngIf="panel === 'terminate'">
          <h4 class="mb-2">Terminate early</h4>
          <div class="text-muted mb-2" style="font-size:0.9rem;">Record it on the day the tenant leaves. The unit becomes vacant from that date.</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Tenant left on *</label><input class="form-control" type="date" name="td" [(ngModel)]="tDate" [max]="todayStr" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:16rem;"><label class="form-label">Why *</label><input class="form-control" name="tr" [(ngModel)]="tReason" maxlength="1000" /></div>
          </div>
          <div class="flex gap-2"><button class="btn btn-danger" type="button" (click)="terminate()" [disabled]="busy">Terminate</button><button class="btn btn-outline-secondary" type="button" (click)="panel = null">Cancel</button></div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">The lease</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">Unit</td><td><a [routerLink]="['/admin/propertymgmt/units', l.unitId]">{{ l.propertyName }} · {{ l.unitNo }}</a></td></tr>
              <tr><td class="text-muted">Owner</td><td><a *ngIf="l.ownerId" [routerLink]="['/admin/propertymgmt/owners', l.ownerId]">{{ l.ownerName }}</a></td></tr>
              <tr><td class="text-muted">Tenant</td><td><a [routerLink]="['/admin/propertymgmt/tenants', l.tenantId]">{{ l.tenantName }}</a><span *ngIf="l.customerId"> · <a [routerLink]="['/admin/customers', l.customerId]">customer record</a></span></td></tr>
              <tr><td class="text-muted">Term</td><td>{{ l.startDate | date: 'd MMM y' }} to {{ l.endDate | date: 'd MMM y' }}
                <span class="text-muted" *ngIf="l.daysToEnd != null"> · {{ l.daysToEnd < 0 ? (-l.daysToEnd) + ' days past the end' : l.daysToEnd + ' days left' }}</span></td></tr>
              <tr><td class="text-muted">Signed</td><td>{{ l.signedDate ? (l.signedDate | date: 'd MMM y') : 'not yet' }}</td></tr>
              <tr><td class="text-muted">Notice</td><td>{{ l.noticeDays }} days</td></tr>
              <tr *ngIf="l.renewedFromId"><td class="text-muted">Renews</td><td><a [routerLink]="['/admin/propertymgmt/leases', l.renewedFromId]">{{ l.renewedFromCode }}</a></td></tr>
              <tr *ngIf="l.renewalId"><td class="text-muted">Renewed by</td><td><a [routerLink]="['/admin/propertymgmt/leases', l.renewalId]">{{ l.renewalCode }}</a></td></tr>
              <tr *ngIf="l.status === 'TERMINATED'"><td class="text-muted">Terminated</td><td>{{ l.terminatedDate | date: 'd MMM y' }} · {{ l.terminationReason }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Rent</h4>
            <div style="font-size:1.6rem;">{{ l.rent | number: '1.0-0' }} <span class="text-muted" style="font-size:0.9rem;">{{ freq(l.frequency).toLowerCase() }}</span></div>
            <div class="text-muted">{{ l.monthlyRent | number: '1.0-0' }} a month · due on day {{ l.dueDay }}</div>
            <table class="table-sm mt-2"><tbody>
              <tr><td class="text-muted" style="width:9rem;">Deposit</td><td>{{ l.deposit != null ? (l.deposit | number: '1.0-0') : 'none' }}</td></tr>
              <tr><td class="text-muted">Yearly increase</td><td>{{ l.escalationPercent != null ? (l.escalationPercent | number: '1.0-2') + '%' : 'none agreed' }}</td></tr>
            </tbody></table>
          </div>
        </div>
        <div class="card mb-3" style="padding:1rem;" *ngIf="l.status !== 'DRAFT'">
          <app-rent-schedule [leaseId]="l.id"></app-rent-schedule>
        </div>
        <app-pm-payments-panel *ngIf="l.status !== 'DRAFT'" [leaseId]="l.id" [canManage]="l.canManage"></app-pm-payments-panel>
        <app-pm-lease-charges-panel [leaseId]="l.id" [status]="l.status"></app-pm-lease-charges-panel>
        <app-pm-deposit-panel [leaseId]="l.id" [status]="l.status"></app-pm-deposit-panel>
        <app-pm-inspections-panel [leaseId]="l.id" [status]="l.status" [canManage]="l.canManage"></app-pm-inspections-panel>
        <app-pm-documents-panel subjectType="LEASE" [subjectId]="l.id" [canManage]="l.canManage" defaultCategory="LEASE_AGREEMENT"></app-pm-documents-panel>
        <div class="card" style="padding:1rem; max-width:60rem;" *ngIf="l.terms">
          <h4 class="mb-2">Terms and notes</h4>
          <div style="white-space:pre-wrap;">{{ l.terms }}</div>
        </div>
        <div class="text-muted mt-2" style="font-size:0.8rem;">Drafted by {{ l.createdByName || '—' }} on {{ l.createdAt | date: 'd MMM y' }}.</div>
      </ng-container>
    </app-shell>
  `,
})
export class LeaseDetailComponent implements OnInit {
  l: LeaseDto | null = null;
  error: string | null = null;
  busy = false;
  isAdmin = false;
  panel: 'sign' | 'renew' | 'terminate' | null = null;
  todayStr = today();
  signedOn = today();
  billFrom = '';
  tDate = today();
  tReason = '';
  rn = { startDate: '', endDate: '', rent: null as number | null };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router, auth: AuthService) {
    this.isAdmin = auth.isAdmin();
  }

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.lease(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (l) => (this.l = l), error: (e) => (this.error = e?.error?.message ?? 'Could not load the lease') });
  }

  badge(s: string): string { return leaseBadge(s); }
  stateName(s: string): string { return (LEASE_STATE_LABEL as Record<string, string>)[s] ?? s; }
  freq(f: string): string { return pmLabel(RENT_FREQUENCIES, f); }
  pastEnd(): boolean { return this.l != null && this.l.endDate < today(); }
  canRenew(): boolean { return this.l != null && this.l.status === 'ACTIVE' && this.l.renewalId == null && this.l.state !== 'UPCOMING'; }

  open(p: 'sign' | 'terminate'): void {
    this.error = null;
    this.panel = this.panel === p ? null : p;
  }

  openRenew(): void {
    this.error = null;
    this.rn = { startDate: '', endDate: '', rent: this.l!.suggestedRenewalRent };
    this.panel = this.panel === 'renew' ? null : 'renew';
  }

  private done(l: LeaseDto): void {
    this.busy = false;
    this.panel = null;
    this.l = l;
  }

  private fail(e: any, what: string): void {
    this.busy = false;
    this.error = e?.error?.message ?? what;
  }

  sign(): void {
    this.busy = true;
    this.error = null;
    this.api.activateLease(this.l!.id, this.signedOn || null, this.billFrom || null).subscribe({ next: (l) => this.done(l), error: (e) => this.fail(e, 'Could not sign the lease') });
  }

  renew(): void {
    this.busy = true;
    this.error = null;
    const p: RenewPayload = {};
    if (this.rn.startDate) p.startDate = this.rn.startDate;
    if (this.rn.endDate) p.endDate = this.rn.endDate;
    if (num(this.rn.rent)) p.rent = num(this.rn.rent);
    this.api.renewLease(this.l!.id, p).subscribe({
      next: (n) => { this.busy = false; this.panel = null; this.router.navigate(['/admin/propertymgmt/leases', n.id]); },
      error: (e) => this.fail(e, 'Could not renew the lease'),
    });
  }

  terminate(): void {
    this.error = null;
    if (!txt(this.tReason)) { this.error = 'Say why the lease is ending early.'; return; }
    this.busy = true;
    this.api.terminateLease(this.l!.id, this.tDate || null, txt(this.tReason)).subscribe({ next: (l) => this.done(l), error: (e) => this.fail(e, 'Could not terminate the lease') });
  }

  end(force: boolean): void {
    if (force && !confirm('End this lease now, before its end date? Use "Terminate early" instead if the tenant is leaving.')) return;
    this.busy = true;
    this.error = null;
    this.api.endLease(this.l!.id, force).subscribe({ next: (l) => this.done(l), error: (e) => this.fail(e, 'Could not end the lease') });
  }

  remove(): void {
    if (!confirm(`Delete draft lease ${this.l!.code}?`)) return;
    this.busy = true;
    this.error = null;
    this.api.deleteLease(this.l!.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/leases']),
      error: (e) => this.fail(e, 'Could not delete the lease'),
    });
  }
}
