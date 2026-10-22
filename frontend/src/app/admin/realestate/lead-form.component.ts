import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { LeadInterest, LeadPayload, LeadSource, Option, PROPERTY_TYPES, PropertyRow, PropertyType, RealEstateApiService, SOURCE_LABEL } from '../../core/api/realestate-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string | null => { const t = (v ?? '').trim(); return t ? t : null; };

/** Real Estate > Leads > Add / edit. A phone number already used by an open lead is refused, with the choice to save anyway. */
@Component({
  selector: 'app-lead-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Edit lead' : 'Add lead' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/realestate/leads', id] : ['/admin/realestate/leads']">Cancel</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-warning" *ngIf="duplicate">
        {{ duplicate }}
        <div class="mt-2"><button class="btn btn-sm btn-outline-secondary" type="button" (click)="save(true)" [disabled]="saving">Save anyway</button></div>
      </div>
      <form class="card" style="padding:1rem; max-width:48rem;" (ngSubmit)="save(false)">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">Name *</label><input class="form-control" name="name" [(ngModel)]="f.name" maxlength="200" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:11rem;"><label class="form-label">Phone</label><input class="form-control" name="phone" [(ngModel)]="f.phone" maxlength="40" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:13rem;"><label class="form-label">E-mail</label><input class="form-control" type="email" name="email" [(ngModel)]="f.email" /></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Looking to</label>
            <select class="form-select" name="int" [(ngModel)]="f.interest"><option value="BUY">Buy</option><option value="RENT">Rent</option><option value="EITHER">Buy or rent</option></select></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Came through</label>
            <select class="form-select" name="src" [(ngModel)]="f.source"><option *ngFor="let s of sources" [value]="s.value">{{ s.label }}</option></select></div>
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Looked after by</label>
            <select class="form-select" name="ag" [(ngModel)]="f.assignedUserId"><option [ngValue]="null">Me</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option></select></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Next follow-up</label><input class="form-control" type="date" name="fu" [(ngModel)]="f.nextFollowUp" /></div>
        </div>
        <h4 class="mb-2 mt-2">What they want</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Budget from</label><input class="form-control" type="number" min="0" name="bmin" [(ngModel)]="f.budgetMin" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Budget up to</label><input class="form-control" type="number" min="0" name="bmax" [(ngModel)]="f.budgetMax" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Area</label><input class="form-control" name="area" [(ngModel)]="f.preferredArea" list="lead-areas" maxlength="120" />
            <datalist id="lead-areas"><option *ngFor="let a of areas" [value]="a"></option></datalist></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Type</label>
            <select class="form-select" name="pt" [(ngModel)]="f.preferredType"><option [ngValue]="null">Any</option><option *ngFor="let t of types" [value]="t.value">{{ t.label }}</option></select></div>
          <div class="form-group mb-2" style="width:8rem;"><label class="form-label">Bedrooms (min)</label><input class="form-control" type="number" min="0" name="bed" [(ngModel)]="f.minBedrooms" /></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">Asked about a property</label>
          <select class="form-select" name="prop" [(ngModel)]="f.propertyId"><option [ngValue]="null">None in particular</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.code }} · {{ p.title }}</option></select></div>
        <div class="form-group mb-3"><label class="form-label">Other requirements</label><textarea class="form-control" rows="3" name="req" [(ngModel)]="f.requirements" maxlength="1000" placeholder="e.g. needs a garden, near a school, cash buyer"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? 'Save changes' : 'Add lead' }}</button></div>
      </form>
    </app-shell>
  `,
})
export class LeadFormComponent implements OnInit {
  id: number | null = null;
  agents: Option[] = [];
  areas: string[] = [];
  properties: PropertyRow[] = [];
  types = PROPERTY_TYPES;
  sources = (Object.keys(SOURCE_LABEL) as LeadSource[]).map((k) => ({ value: k, label: SOURCE_LABEL[k] }));
  saving = false;
  error: string | null = null;
  duplicate: string | null = null;
  f = {
    name: '', phone: '', email: '', interest: 'BUY' as LeadInterest, source: 'PHONE' as LeadSource, assignedUserId: null as number | null, nextFollowUp: '',
    budgetMin: null as number | null, budgetMax: null as number | null, preferredArea: '', preferredType: null as PropertyType | null, minBedrooms: null as number | null,
    propertyId: null as number | null, requirements: '',
  };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.api.settings().subscribe({ next: (s) => (this.areas = s.areas), error: () => {} });
    this.api.properties({}).subscribe({ next: (l) => (this.properties = l), error: () => {} });
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.id = Number(id);
      this.api.lead(this.id).subscribe({
        next: (l) => Object.assign(this.f, {
          name: l.name, phone: l.phone ?? '', email: l.email ?? '', interest: l.interest, source: l.source, assignedUserId: l.assignedUserId, nextFollowUp: l.nextFollowUp ?? '',
          budgetMin: l.budgetMin, budgetMax: l.budgetMax, preferredArea: l.preferredArea ?? '', preferredType: l.preferredType, minBedrooms: l.minBedrooms,
          propertyId: l.propertyId, requirements: l.requirements ?? '',
        }),
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the lead'),
      });
    } else {
      const pid = this.route.snapshot.queryParamMap.get('propertyId');
      if (pid) this.f.propertyId = Number(pid);
    }
  }

  save(force: boolean): void {
    this.error = null;
    if (!force) this.duplicate = null;
    const f = this.f;
    if (!f.name.trim()) { this.error = 'Enter the lead\'s name.'; return; }
    if (!f.phone.trim() && !f.email.trim()) { this.error = 'Give a phone number or an e-mail so the lead can be reached.'; return; }
    const body: LeadPayload = {
      name: f.name.trim(), phone: txt(f.phone), email: txt(f.email), interest: f.interest, source: f.source, budgetMin: num(f.budgetMin), budgetMax: num(f.budgetMax),
      preferredArea: txt(f.preferredArea), preferredType: f.preferredType, minBedrooms: num(f.minBedrooms), requirements: txt(f.requirements), propertyId: f.propertyId,
      assignedUserId: f.assignedUserId, nextFollowUp: f.nextFollowUp || null, force,
    };
    this.saving = true;
    const call = this.id ? this.api.updateLead(this.id, body) : this.api.createLead(body);
    call.subscribe({
      next: (l) => this.router.navigate(['/admin/realestate/leads', l.id]),
      error: (e) => {
        this.saving = false;
        if (e?.status === 409) { this.duplicate = e?.error?.message ?? 'There is already a lead with this phone number.'; this.error = null; }
        else { this.duplicate = null; this.error = e?.error?.message ?? 'Could not save the lead'; }
      },
    });
  }
}
