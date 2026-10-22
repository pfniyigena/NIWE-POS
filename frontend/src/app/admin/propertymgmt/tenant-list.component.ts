import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { PmExportComponent } from './pm-export.component';
import { PropertyMgmtApiService, TenantKind, TenantPayload, TenantRow } from '../../core/api/propertymgmt-api.service';

const txt = (v: string | null | undefined): string | undefined => { const t = (v ?? '').trim(); return t ? t : undefined; };

/** Property Management > Tenants: people and companies that rent or want to rent. */
@Component({
  selector: 'app-tenant-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, PmExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Tenants</h3>
        <button class="btn btn-primary" type="button" (click)="adding = !adding">{{ adding ? 'Close' : '+ Add a tenant' }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="adding" class="card mb-3" style="padding:1rem; max-width:46rem;" (ngSubmit)="create()">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">Who</label>
            <select class="form-select" name="k" [(ngModel)]="f.kind"><option value="INDIVIDUAL">A person</option><option value="COMPANY">A company</option></select></div>
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">Name *</label><input class="form-control" name="n" [(ngModel)]="f.name" maxlength="200" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">Phone</label><input class="form-control" name="p" [(ngModel)]="f.phone" maxlength="40" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">E-mail</label><input class="form-control" type="email" name="e" [(ngModel)]="f.email" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">ID / registration no.</label><input class="form-control" name="id" [(ngModel)]="f.idNumber" maxlength="60" /></div>
        </div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : 'Save tenant' }}</button>
          <span class="text-muted" style="margin-left:0.75rem; font-size:0.85rem;">Employer and emergency contact can be added on the tenant's page.</span></div>
      </form>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <input class="form-control" style="width:16rem;" placeholder="Search name, code, phone, ID" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="wl" [(ngModel)]="withLease" (ngModelChange)="load()" /> With a lease in force</label>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ao" [(ngModel)]="activeOnly" (ngModelChange)="load()" /> Active only</label>
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <app-pm-export kind="tenants" [filters]="{ q: q.trim(), activeOnly: activeOnly, withLeaseOnly: withLease }"></app-pm-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Tenant</th><th>Contact</th><th>Renting</th><th class="text-end">Leases in force</th></tr></thead>
        <tbody>
          <tr *ngFor="let t of list">
            <td data-label="Tenant"><a [routerLink]="['/admin/propertymgmt/tenants', t.id]"><strong>{{ t.name }}</strong></a> <span class="text-muted" style="font-size:0.8rem;">{{ t.code }}</span>
              <span class="badge badge-secondary" *ngIf="t.kind === 'COMPANY'" style="margin-left:0.3rem;">Company</span>
              <span class="badge badge-secondary" *ngIf="!t.active" style="margin-left:0.3rem;">Inactive</span></td>
            <td data-label="Contact">{{ t.phone || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ t.email }}</div></td>
            <td data-label="Renting">{{ t.currentUnits || '—' }}</td>
            <td class="text-end" data-label="Leases in force">{{ t.currentLeases }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">No tenants match.</p>
    </app-shell>
  `,
})
export class TenantListComponent implements OnInit {
  list: TenantRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  loaded = false;
  q = '';
  withLease = false;
  activeOnly = false;
  adding = false;
  saving = false;
  error: string | null = null;
  f = { kind: 'INDIVIDUAL' as TenantKind, name: '', phone: '', email: '', idNumber: '' };

  constructor(private api: PropertyMgmtApiService, private router: Router) {}

  ngOnInit(): void { this.load(); }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.api.tenantsPage({ q: this.q.trim(), activeOnly: this.activeOnly, withLeaseOnly: this.withLease, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the tenants'; },
    });
  }

  create(): void {
    this.error = null;
    if (!txt(this.f.name)) { this.error = 'Give the tenant\'s name.'; return; }
    const p: TenantPayload = { kind: this.f.kind, name: txt(this.f.name), phone: txt(this.f.phone), email: txt(this.f.email), idNumber: txt(this.f.idNumber) };
    this.saving = true;
    this.api.createTenant(p).subscribe({
      next: (t) => { this.saving = false; this.router.navigate(['/admin/propertymgmt/tenants', t.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the tenant'; },
    });
  }
}
