import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { LeaseDto, LeasePayload, PropertyMgmtApiService, RENT_FREQUENCIES, RentFrequency, TenantRow, UnitRow } from '../../core/api/propertymgmt-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string => (v ?? '').trim();
const MONTHS: Record<RentFrequency, number> = { MONTHLY: 1, QUARTERLY: 3, HALF_YEARLY: 6, ANNUAL: 12 };

/** Property Management > Leases > draft a lease, or change one: a draft freely, a lease in force only its due day, increase, notice period and terms. */
@Component({
  selector: 'app-lease-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Change lease ' + (lease?.code ?? '') : 'Draft a lease' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/propertymgmt/leases', id] : ['/admin/propertymgmt/leases']">{{ 'Cancel' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-warning" *ngIf="locked">{{ 'This lease is signed. The unit, tenant, dates, rent and deposit are fixed: renew it, or terminate it and draft a new one. You can still change the due day, yearly increase, notice period and terms.' | translate }}</div>
      <form class="card" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:16rem;"><label class="form-label">{{ 'Unit *' | translate }}</label>
            <select class="form-select" name="u" [(ngModel)]="f.unitId" (ngModelChange)="unitPicked()" [disabled]="locked">
              <option [ngValue]="null">{{ '— choose —' | translate }}</option>
              <option *ngFor="let u of units" [ngValue]="u.id">{{ u.propertyName }} · {{ u.unitNo }}{{ u.occupancy === 'OCCUPIED' ? ' (occupied)' : u.occupancy === 'OFF_MARKET' ? ' (off the market)' : '' }}</option>
            </select></div>
          <div class="form-group mb-2" style="flex:1; min-width:16rem;"><label class="form-label">{{ 'Tenant *' | translate }}</label>
            <select class="form-select" name="t" [(ngModel)]="f.tenantId" [disabled]="locked"><option [ngValue]="null">{{ '— choose —' | translate }}</option><option *ngFor="let t of tenants" [ngValue]="t.id">{{ t.name }}{{ t.phone ? ' · ' + t.phone : '' }}</option></select>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="!locked">{{ 'Not there?' | translate }} <a routerLink="/admin/propertymgmt/tenants">{{ 'Add the tenant first' | translate }}</a>.</div></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Starts *' | translate }}</label><input class="form-control" type="date" name="s" [(ngModel)]="f.startDate" [disabled]="locked" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Ends *' | translate }}</label><input class="form-control" type="date" name="e" [(ngModel)]="f.endDate" [disabled]="locked" /></div>
          <div class="form-group mb-2" style="align-self:flex-end;" *ngIf="!locked"><button class="btn btn-sm btn-outline-secondary" type="button" (click)="oneYear()" [title]="'End one year after the start date, less a day' | translate">{{ '1 year' | translate }}</button></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Rent for each payment *' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="r" [(ngModel)]="f.rent" [disabled]="locked" />
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="perMonth() != null">{{ '{p0} a month' | translate: { p0: (perMonth() | number: '1.0-0') } }}</div></div>
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Paid' | translate }}</label>
            <select class="form-select" name="fr" [(ngModel)]="f.frequency" [disabled]="locked"><option *ngFor="let q of freqs" [ngValue]="q.value">{{ q.label }}</option></select></div>
          <div class="form-group mb-2" style="width:9rem;"><label class="form-label">{{ 'Due on day' | translate }}</label><input class="form-control" type="number" min="1" max="28" name="dd" [(ngModel)]="f.dueDay" /></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Deposit' | translate }}</label><input class="form-control" type="number" min="0" name="dp" [(ngModel)]="f.deposit" [disabled]="locked" [placeholder]="'none' | translate" /></div>
          <div class="form-group mb-2" style="width:11rem;"><label class="form-label">{{ 'Yearly increase %' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="es" [(ngModel)]="f.escalation" [placeholder]="'none' | translate" /></div>
          <div class="form-group mb-2" style="width:11rem;"><label class="form-label">{{ 'Notice (days)' | translate }}</label><input class="form-control" type="number" min="0" max="365" name="nd" [(ngModel)]="f.noticeDays" /></div>
        </div>
        <div class="form-group mb-3"><label class="form-label">{{ 'Terms / notes' | translate }}</label><textarea class="form-control" rows="4" name="tm" [(ngModel)]="f.terms" maxlength="2000"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? ('Save changes' | translate) : ('Save draft' | translate) }}</button>
          <span class="text-muted" style="margin-left:0.75rem; font-size:0.85rem;" *ngIf="!id">{{ 'A draft does not let the unit. Sign it from the lease page.' | translate }}</span></div>
      </form>
    </app-shell>
  `,
})
export class LeaseFormComponent implements OnInit {
  id: number | null = null;
  lease: LeaseDto | null = null;
  units: UnitRow[] = [];
  tenants: TenantRow[] = [];
  freqs = RENT_FREQUENCIES;
  error: string | null = null;
  saving = false;
  locked = false;
  f = { unitId: null as number | null, tenantId: null as number | null, startDate: '', endDate: '', rent: null as number | null, frequency: 'MONTHLY' as RentFrequency, dueDay: 1 as number | null,
    deposit: null as number | null, escalation: null as number | null, noticeDays: 30 as number | null, terms: '' };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.api.tenants({ activeOnly: true }).subscribe({
      next: (l) => { this.tenants = l; const t = Number(q.get('tenantId')); if (!this.id && t && l.some((x) => x.id === t)) this.f.tenantId = t; },
      error: () => {},
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    this.api.units({}).subscribe({
      next: (l) => {
        this.units = l;
        const u = Number(q.get('unitId'));
        if (!idParam && u && l.some((x) => x.id === u)) { this.f.unitId = u; this.unitPicked(); }
      },
      error: () => {},
    });
    if (idParam) {
      this.id = Number(idParam);
      this.api.lease(this.id).subscribe({
        next: (l) => {
          this.lease = l;
          this.locked = l.status !== 'DRAFT';
          if (l.status !== 'DRAFT' && l.status !== 'ACTIVE') this.error = 'A lease that has ended cannot be changed.';
          this.f = { unitId: l.unitId, tenantId: l.tenantId, startDate: l.startDate, endDate: l.endDate, rent: l.rent, frequency: l.frequency, dueDay: l.dueDay, deposit: l.deposit,
            escalation: l.escalationPercent, noticeDays: l.noticeDays, terms: l.terms ?? '' };
        },
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the lease'),
      });
    } else {
      this.api.settings().subscribe({ next: (s) => (this.f.dueDay = s.defaultDueDay), error: () => {} });
      this.f.startDate = new Date().toISOString().slice(0, 10);
      this.oneYear();
    }
  }

  unitPicked(): void {
    const u = this.units.find((x) => x.id === this.f.unitId);
    if (!u || this.id) return;
    if (!this.f.rent && u.marketRent) this.f.rent = u.marketRent;
    if (!this.f.deposit && u.defaultDeposit) this.f.deposit = u.defaultDeposit;
  }

  oneYear(): void {
    if (!this.f.startDate) return;
    const d = new Date(this.f.startDate + 'T00:00:00');
    d.setFullYear(d.getFullYear() + 1);
    d.setDate(d.getDate() - 1);
    this.f.endDate = d.toISOString().slice(0, 10);
  }

  perMonth(): number | null {
    const r = num(this.f.rent);
    return r ? r / MONTHS[this.f.frequency] : null;
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!this.locked) {
      if (!f.unitId) { this.error = 'Choose the unit.'; return; }
      if (!f.tenantId) { this.error = 'Choose the tenant.'; return; }
      if (!f.startDate || !f.endDate) { this.error = 'Give the start and end dates.'; return; }
      if (f.endDate < f.startDate) { this.error = 'The lease cannot end before it starts.'; return; }
      if (!num(f.rent)) { this.error = 'Enter the rent.'; return; }
    }
    // 0 clears the deposit and the yearly increase on the server.
    const p: LeasePayload = { dueDay: num(f.dueDay), escalationPercent: num(f.escalation) ?? 0, noticeDays: num(f.noticeDays), terms: txt(f.terms) };
    if (!this.locked) {
      Object.assign(p, { unitId: f.unitId, tenantId: f.tenantId, startDate: f.startDate, endDate: f.endDate, rent: num(f.rent), frequency: f.frequency, deposit: num(f.deposit) ?? 0 });
    }
    this.saving = true;
    const call = this.id ? this.api.updateLease(this.id, p) : this.api.createLease(p);
    call.subscribe({
      next: (l) => { this.saving = false; this.router.navigate(['/admin/propertymgmt/leases', l.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the lease'; },
    });
  }
}
