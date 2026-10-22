import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { StatementsPanelComponent } from './statements-panel.component';
import { OwnerDto, OwnerPayload, PM_PROPERTY_KINDS, PropertyMgmtApiService, pmLabel } from '../../core/api/propertymgmt-api.service';
import { PmDocumentsPanelComponent } from './pm-documents-panel.component';

const txt = (v: string | null | undefined): string => (v ?? '').trim();

/** Property Management > an owner: contact and payout details, the fee, and their properties. */
@Component({
  selector: 'app-owner-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, StatementsPanelComponent, PmDocumentsPanelComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!o && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="o">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/propertymgmt/owners" style="font-size:0.85rem;">← Owners</a>
            <h3>{{ o.name }} <span class="text-muted" style="font-size:0.9rem;">{{ o.code }}</span>
              <span class="badge badge-secondary" *ngIf="!o.active" style="font-size:0.8rem;">Inactive</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="o.canManage">
            <a class="btn btn-primary" routerLink="/admin/propertymgmt/properties/new" [queryParams]="{ ownerId: o.id }">+ Property</a>
            <button class="btn btn-outline-secondary" type="button" (click)="editing ? editing = false : startEdit()">{{ editing ? 'Cancel' : 'Edit' }}</button>
            <button class="btn btn-outline-secondary" type="button" (click)="toggleActive()" [disabled]="busy">{{ o.active ? 'Mark inactive' : 'Mark active' }}</button>
            <button class="btn btn-outline-danger" type="button" (click)="remove()" [disabled]="busy">Delete</button>
          </div>
        </div>

        <form *ngIf="editing" class="card mb-3" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">Name *</label><input class="form-control" name="n" [(ngModel)]="f.name" maxlength="200" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">Phone</label><input class="form-control" name="p" [(ngModel)]="f.phone" maxlength="40" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">E-mail</label><input class="form-control" type="email" name="e" [(ngModel)]="f.email" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">ID / registration no.</label><input class="form-control" name="id" [(ngModel)]="f.idNumber" maxlength="60" /></div>
            <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Tax number</label><input class="form-control" name="tx" [(ngModel)]="f.taxNumber" maxlength="60" /></div>
            <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">Address</label><input class="form-control" name="ad" [(ngModel)]="f.address" maxlength="500" /></div>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Bank</label><input class="form-control" name="bk" [(ngModel)]="f.bankName" maxlength="120" /></div>
            <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Account for payouts</label><input class="form-control" name="ba" [(ngModel)]="f.bankAccount" maxlength="60" /></div>
            <div class="form-group mb-2" style="width:11rem;"><label class="form-label">Management fee %</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="fee" [(ngModel)]="f.fee" placeholder="usual" /></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">Notes</label><textarea class="form-control" rows="2" name="nt" [(ngModel)]="f.notes" maxlength="2000"></textarea></div>
          <div><button class="btn btn-primary" type="submit" [disabled]="busy">{{ busy ? 'Saving…' : 'Save changes' }}</button></div>
        </form>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Details</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">Phone</td><td>{{ o.phone || '—' }}</td></tr>
              <tr><td class="text-muted">E-mail</td><td>{{ o.email || '—' }}</td></tr>
              <tr><td class="text-muted">ID / registration</td><td>{{ o.idNumber || '—' }}</td></tr>
              <tr><td class="text-muted">Tax number</td><td>{{ o.taxNumber || '—' }}</td></tr>
              <tr><td class="text-muted">Address</td><td>{{ o.address || '—' }}</td></tr>
              <tr><td class="text-muted">Bank</td><td>{{ o.bankName || '—' }}{{ o.bankAccount ? ' · ' + o.bankAccount : '' }}</td></tr>
              <tr><td class="text-muted">Management fee</td><td>{{ o.feePercent != null ? (o.feePercent | number: '1.0-2') + '%' : 'the usual fee' }}</td></tr>
              <tr *ngIf="o.notes"><td class="text-muted">Notes</td><td style="white-space:pre-wrap;">{{ o.notes }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Portfolio</h4>
            <div style="font-size:1.6rem;">{{ o.monthlyRent | number: '1.0-0' }} <span class="text-muted" style="font-size:0.9rem;">rent per month</span></div>
            <div class="text-muted">{{ o.properties.length }} propert{{ o.properties.length === 1 ? 'y' : 'ies' }} · {{ o.units }} units · {{ o.occupied }} occupied</div>
          </div>
        </div>

        <div class="card" style="padding:1rem;">
          <h4 class="mb-2">Properties</h4>
          <table class="stack table-sm" *ngIf="o.properties.length; else none">
            <thead><tr><th>Property</th><th>Type</th><th>Area</th><th class="text-end">Units</th><th class="text-end">Occupied</th><th class="text-end">Vacant</th><th class="text-end">Rent / month</th><th class="text-end">Share</th><th class="text-end">Fee</th></tr></thead>
            <tbody><tr *ngFor="let p of o.properties">
              <td data-label="Property"><a [routerLink]="['/admin/propertymgmt/properties', p.id]"><strong>{{ p.name }}</strong></a> <span class="text-muted" style="font-size:0.8rem;">{{ p.code }}</span><span class="badge badge-secondary" *ngIf="!p.active" style="margin-left:0.3rem;">Inactive</span></td>
              <td data-label="Type">{{ kind(p.kind) }}</td><td data-label="Area">{{ p.area || '—' }}</td><td class="text-end" data-label="Units">{{ p.units }}</td><td class="text-end" data-label="Occupied">{{ p.occupied }}</td><td class="text-end" data-label="Vacant">{{ p.vacant }}</td>
              <td class="text-end" data-label="Rent / month">{{ p.monthlyRent | number: '1.0-0' }}</td><td class="text-end" data-label="Share">{{ p.sharePercent != null ? (p.sharePercent | number: '1.0-2') + '%' : '—' }}</td><td class="text-end" data-label="Fee">{{ p.effectiveFeePercent != null ? (p.effectiveFeePercent | number: '1.0-2') + '%' : '—' }}</td>
            </tr></tbody>
          </table>
          <ng-template #none><div class="text-muted">No properties yet.</div></ng-template>
        </div>
        <app-statements-panel [ownerId]="o.id" [canManage]="o.canManage"></app-statements-panel>
        <app-pm-documents-panel subjectType="OWNER" [subjectId]="o.id" [canManage]="o.canManage" defaultCategory="ID_DOCUMENT"></app-pm-documents-panel>
      </ng-container>
    </app-shell>
  `,
})
export class OwnerDetailComponent implements OnInit {
  o: OwnerDto | null = null;
  error: string | null = null;
  editing = false;
  busy = false;
  f = { name: '', phone: '', email: '', idNumber: '', taxNumber: '', address: '', bankName: '', bankAccount: '', fee: null as number | null, notes: '' };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.owner(Number(this.route.snapshot.paramMap.get('id'))).subscribe({ next: (o) => (this.o = o), error: (e) => (this.error = e?.error?.message ?? 'Could not load the owner') });
  }

  kind(k: string): string { return pmLabel(PM_PROPERTY_KINDS, k); }

  startEdit(): void {
    const o = this.o!;
    this.f = { name: o.name, phone: o.phone ?? '', email: o.email ?? '', idNumber: o.idNumber ?? '', taxNumber: o.taxNumber ?? '', address: o.address ?? '', bankName: o.bankName ?? '',
      bankAccount: o.bankAccount ?? '', fee: o.feePercent, notes: o.notes ?? '' };
    this.editing = true;
  }

  save(): void {
    this.error = null;
    if (!txt(this.f.name)) { this.error = 'Give the owner\'s name.'; return; }
    const hadFee = this.o!.feePercent != null;
    const hasFee = this.f.fee != null && String(this.f.fee) !== '';
    const p: OwnerPayload = { name: txt(this.f.name), phone: txt(this.f.phone), email: txt(this.f.email), idNumber: txt(this.f.idNumber), taxNumber: txt(this.f.taxNumber), address: txt(this.f.address),
      bankName: txt(this.f.bankName), bankAccount: txt(this.f.bankAccount), notes: txt(this.f.notes) };
    if (hasFee) p.feePercent = Number(this.f.fee);
    else if (hadFee) p.clearFee = true;
    this.busy = true;
    this.api.updateOwner(this.o!.id, p).subscribe({
      next: (o) => { this.busy = false; this.o = o; this.editing = false; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not save the owner'; },
    });
  }

  toggleActive(): void {
    this.busy = true;
    this.error = null;
    this.api.updateOwner(this.o!.id, { active: !this.o!.active }).subscribe({
      next: (o) => { this.busy = false; this.o = o; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not change the owner'; },
    });
  }

  remove(): void {
    if (!confirm(`Delete ${this.o!.name}?`)) return;
    this.busy = true;
    this.error = null;
    this.api.deleteOwner(this.o!.id).subscribe({
      next: () => this.router.navigate(['/admin/propertymgmt/owners']),
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not delete the owner'; },
    });
  }
}
