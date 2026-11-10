import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ShellComponent } from '../../shared/shell.component';
import { DealDto, DealPayload, LeadRow, Option, PropertyRow, RealEstateApiService } from '../../core/api/realestate-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string | null => { const t = (v ?? '').trim(); return t ? t : null; };

/** Real Estate > Deals > record an offer (a new deal) or change one that is still going on. */
@Component({
  selector: 'app-deal-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Change deal ' + (deal?.code ?? '') : 'Record an offer' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/realestate/deals', id] : ['/admin/realestate/deals']">{{ 'Cancel' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
        <h4 class="mb-2">{{ 'The property' | translate }}</h4>
        <div class="form-group mb-3" *ngIf="!id">
          <label class="form-label">{{ 'Property for sale *' | translate }}</label>
          <select class="form-select" name="prop" [(ngModel)]="f.propertyId" (ngModelChange)="propertyPicked()">
            <option [ngValue]="null">{{ '— choose —' | translate }}</option>
            <option *ngFor="let p of properties" [ngValue]="p.id">{{ p.code }} · {{ p.title }}{{ p.area ? ' · ' + p.area : '' }}{{ p.status === 'RESERVED' ? ' (reserved)' : '' }}</option>
          </select>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="picked()">{{ 'Asking {p0}. A property that is already reserved can still take back-up offers.' | translate: { p0: (picked()!.salePrice != null ? (picked()!.salePrice | number: '1.0-0') : '—') } }}</div>
        </div>
        <div class="mb-3" *ngIf="id && deal"><a [routerLink]="['/admin/realestate/properties', deal.propertyId]">{{ deal.propertyCode }} · {{ deal.propertyTitle }}</a></div>

        <h4 class="mb-2">{{ 'The buyer' | translate }}</h4>
        <div class="form-group mb-2" *ngIf="!id">
          <label class="form-label">{{ 'From a lead' | translate }} <span class="text-muted">{{ '(optional: fills in the details below)' | translate }}</span></label>
          <select class="form-select" name="lead" [(ngModel)]="f.leadId" (ngModelChange)="leadPicked()">
            <option [ngValue]="null">{{ '— a new buyer —' | translate }}</option>
            <option *ngFor="let l of leads" [ngValue]="l.id">{{ l.name }}{{ l.phone ? ' · ' + l.phone : '' }}</option>
          </select>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Name *' | translate }}</label><input class="form-control" name="bn" [(ngModel)]="f.buyerName" maxlength="200" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="bp" [(ngModel)]="f.buyerPhone" maxlength="40" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="be" [(ngModel)]="f.buyerEmail" /></div>
        </div>

        <h4 class="mb-2 mt-2">{{ 'The offer' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:13rem;" *ngIf="!id || deal?.stage === 'OFFER'"><label class="form-label">{{ 'Amount offered *' | translate }}</label><input class="form-control" type="number" min="0" name="oa" [(ngModel)]="f.offerAmount" /></div>
          <div class="form-group mb-2" style="min-width:13rem;" *ngIf="id && deal?.stage !== 'OFFER'"><label class="form-label">{{ 'Agreed price' | translate }}</label><input class="form-control" type="number" min="0" name="ap" [(ngModel)]="f.agreedPrice" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Offer date' | translate }}</label><input class="form-control" type="date" name="od" [(ngModel)]="f.offerDate" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Offer valid until' | translate }}</label><input class="form-control" type="date" name="ov" [(ngModel)]="f.offerValidUntil" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Expected completion' | translate }}</label><input class="form-control" type="date" name="ec" [(ngModel)]="f.expectedCompletion" /></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Agent' | translate }}</label>
            <select class="form-select" name="agent" [(ngModel)]="f.agentUserId"><option [ngValue]="null">{{ id ? 'Unchanged' : "The property's agent" }}</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option></select></div>
          <div class="form-group mb-2" style="width:11rem;"><label class="form-label">{{ 'Commission %' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="cp" [(ngModel)]="f.commissionPercent" [placeholder]="'usual' | translate" /></div>
          <div class="form-group mb-2" style="width:13rem;" *ngIf="id"><label class="form-label">{{ 'or a fixed commission' | translate }}</label><input class="form-control" type="number" min="0" name="cf" [(ngModel)]="f.commissionFixed" [placeholder]="'none' | translate" /></div>
        </div>
        <div class="form-group mb-3"><label class="form-label">{{ 'Notes' | translate }}</label><textarea class="form-control" rows="3" name="nt" [(ngModel)]="f.notes" maxlength="2000"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? ('Save changes' | translate) : ('Save offer' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class DealFormComponent implements OnInit {
  id: number | null = null;
  deal: DealDto | null = null;
  properties: PropertyRow[] = [];
  leads: LeadRow[] = [];
  agents: Option[] = [];
  error: string | null = null;
  saving = false;
  f = {
    propertyId: null as number | null, leadId: null as number | null, buyerName: '', buyerPhone: '', buyerEmail: '', offerAmount: null as number | null, agreedPrice: null as number | null,
    offerDate: '', offerValidUntil: '', expectedCompletion: '', agentUserId: null as number | null, commissionPercent: null as number | null, commissionFixed: null as number | null, notes: '',
  };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.api.deal(this.id).subscribe({
        next: (d) => { this.deal = d; this.fill(d); },
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the deal'),
      });
      return;
    }
    this.f.offerDate = new Date().toISOString().slice(0, 10);
    const q = this.route.snapshot.queryParamMap;
    forkJoin([this.api.properties({ status: 'AVAILABLE' }), this.api.properties({ status: 'RESERVED' }), this.api.leads({ openOnly: true })]).subscribe({
      next: ([a, r, l]) => {
        this.properties = [...a, ...r].filter((p) => p.purpose !== 'RENT');
        this.leads = l.filter((x) => x.interest !== 'RENT');
        const pid = Number(q.get('propertyId'));
        const lid = Number(q.get('leadId'));
        if (pid && this.properties.some((p) => p.id === pid)) { this.f.propertyId = pid; this.propertyPicked(); }
        if (lid && this.leads.some((x) => x.id === lid)) { this.f.leadId = lid; this.leadPicked(); }
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the properties'),
    });
  }

  private fill(d: DealDto): void {
    this.f = {
      propertyId: d.propertyId, leadId: d.leadId, buyerName: d.buyerName, buyerPhone: d.buyerPhone ?? '', buyerEmail: d.buyerEmail ?? '', offerAmount: d.offerAmount, agreedPrice: d.agreedPrice,
      offerDate: d.offerDate ?? '', offerValidUntil: d.offerValidUntil ?? '', expectedCompletion: d.expectedCompletion ?? '', agentUserId: null, commissionPercent: d.commissionPercent,
      commissionFixed: d.commissionFixed, notes: d.notes ?? '',
    };
  }

  picked(): PropertyRow | undefined { return this.properties.find((p) => p.id === this.f.propertyId); }

  propertyPicked(): void {
    const p = this.picked();
    if (p && p.salePrice != null && !this.f.offerAmount) this.f.offerAmount = p.salePrice;
  }

  leadPicked(): void {
    const l = this.leads.find((x) => x.id === this.f.leadId);
    if (!l) return;
    this.f.buyerName = l.name;
    this.f.buyerPhone = l.phone ?? '';
    this.f.buyerEmail = l.email ?? '';
    if (!this.f.propertyId && l.propertyId && this.properties.some((p) => p.id === l.propertyId)) { this.f.propertyId = l.propertyId; this.propertyPicked(); }
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!this.id && !f.propertyId) { this.error = 'Choose the property.'; return; }
    if (!txt(f.buyerName)) { this.error = 'Give the buyer\'s name.'; return; }
    const payload: DealPayload = {
      buyerName: txt(f.buyerName), buyerPhone: txt(f.buyerPhone) ?? '', buyerEmail: txt(f.buyerEmail) ?? '', offerDate: f.offerDate || null, offerValidUntil: f.offerValidUntil || null,
      expectedCompletion: f.expectedCompletion || null, agentUserId: f.agentUserId, commissionPercent: num(f.commissionPercent), notes: f.notes,
    };
    if (!this.id) {
      if (!num(f.offerAmount)) { this.error = 'Enter the amount offered.'; return; }
      payload.propertyId = f.propertyId;
      payload.leadId = f.leadId;
      payload.offerAmount = num(f.offerAmount);
      payload.notes = txt(f.notes);
      payload.buyerPhone = txt(f.buyerPhone);
      payload.buyerEmail = txt(f.buyerEmail);
    } else {
      if (this.deal?.stage === 'OFFER') payload.offerAmount = num(f.offerAmount);
      else payload.agreedPrice = num(f.agreedPrice);
      // 0 clears the commission %, so the fixed amount (or nothing) applies.
      payload.commissionPercent = num(f.commissionPercent) ?? 0;
      payload.commissionFixed = num(f.commissionFixed) ?? 0;
    }
    this.saving = true;
    const call = this.id ? this.api.updateDeal(this.id, payload) : this.api.createDeal(payload);
    call.subscribe({
      next: (d) => { this.saving = false; this.router.navigate(['/admin/realestate/deals', d.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the deal'; },
    });
  }
}
