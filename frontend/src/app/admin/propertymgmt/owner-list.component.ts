import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { PmExportComponent } from './pm-export.component';
import { OwnerPayload, OwnerRow, PropertyMgmtApiService } from '../../core/api/propertymgmt-api.service';

const txt = (v: string | null | undefined): string | undefined => { const t = (v ?? '').trim(); return t ? t : undefined; };

/** Property Management > Owners: the landlords whose properties we manage, with their portfolio. */
@Component({
  selector: 'app-owner-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, PmExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Owners</h3>
        <button class="btn btn-primary" type="button" (click)="adding = !adding">{{ adding ? 'Close' : '+ Add an owner' }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="adding" class="card mb-3" style="padding:1rem; max-width:46rem;" (ngSubmit)="create()">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">Name *</label><input class="form-control" name="n" [(ngModel)]="f.name" maxlength="200" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">Phone</label><input class="form-control" name="p" [(ngModel)]="f.phone" maxlength="40" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">E-mail</label><input class="form-control" type="email" name="e" [(ngModel)]="f.email" /></div>
          <div class="form-group mb-2" style="width:10rem;"><label class="form-label">Management fee %</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="fee" [(ngModel)]="f.fee" placeholder="usual" /></div>
        </div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : 'Save owner' }}</button>
          <span class="text-muted" style="margin-left:0.75rem; font-size:0.85rem;">More details (ID, bank account for payouts) can be added on the owner's page.</span></div>
      </form>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <input class="form-control" style="width:16rem;" placeholder="Search name, code, phone, e-mail" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ao" [(ngModel)]="activeOnly" (ngModelChange)="load()" /> Active only</label>
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <app-pm-export kind="owners" [filters]="{ q: q.trim(), activeOnly: activeOnly }"></app-pm-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Owner</th><th>Contact</th><th class="text-end">Properties</th><th class="text-end">Units</th><th class="text-end">Occupied</th><th class="text-end">Rent / month</th></tr></thead>
        <tbody>
          <tr *ngFor="let o of list">
            <td data-label="Owner"><a [routerLink]="['/admin/propertymgmt/owners', o.id]"><strong>{{ o.name }}</strong></a> <span class="text-muted" style="font-size:0.8rem;">{{ o.code }}</span>
              <span class="badge badge-secondary" *ngIf="!o.active" style="margin-left:0.3rem;">Inactive</span></td>
            <td data-label="Contact">{{ o.phone || '—' }}<div class="text-muted" style="font-size:0.8rem;">{{ o.email }}</div></td>
            <td class="text-end" data-label="Properties">{{ o.properties }}</td><td class="text-end" data-label="Units">{{ o.units }}</td><td class="text-end" data-label="Occupied">{{ o.occupied }}</td><td class="text-end" data-label="Rent / month">{{ o.monthlyRent | number: '1.0-0' }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">No owners yet.</p>
    </app-shell>
  `,
})
export class OwnerListComponent implements OnInit {
  list: OwnerRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  loaded = false;
  q = '';
  activeOnly = false;
  adding = false;
  saving = false;
  error: string | null = null;
  f = { name: '', phone: '', email: '', fee: null as number | null };

  constructor(private api: PropertyMgmtApiService, private router: Router) {}

  ngOnInit(): void { this.load(); }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.api.ownersPage({ q: this.q.trim(), activeOnly: this.activeOnly, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the owners'; },
    });
  }

  create(): void {
    this.error = null;
    if (!txt(this.f.name)) { this.error = 'Give the owner\'s name.'; return; }
    const p: OwnerPayload = { name: txt(this.f.name), phone: txt(this.f.phone), email: txt(this.f.email) };
    if (this.f.fee != null && String(this.f.fee) !== '') p.feePercent = Number(this.f.fee);
    this.saving = true;
    this.api.createOwner(p).subscribe({
      next: (o) => { this.saving = false; this.router.navigate(['/admin/propertymgmt/owners', o.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the owner'; },
    });
  }
}
