import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { OwnerRow, PM_PROPERTY_KINDS, PmPropertyKind, PropertyDto, PropertyMgmtApiService, PropertyPayload } from '../../core/api/propertymgmt-api.service';

const txt = (v: string | null | undefined): string => (v ?? '').trim();

/** Property Management > Properties > add or change a managed property. */
@Component({
  selector: 'app-pm-property-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Change ' + (property?.name ?? 'property') : 'Add a property' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/propertymgmt/properties', id] : ['/admin/propertymgmt/properties']">{{ 'Cancel' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem; max-width:46rem;" (ngSubmit)="save()">
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Name *' | translate }}</label><input class="form-control" name="n" [(ngModel)]="f.name" maxlength="200" [placeholder]="'e.g. Kololo Heights' | translate" /></div>
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Type' | translate }}</label>
            <select class="form-select" name="k" [(ngModel)]="f.kind"><option *ngFor="let k of kinds" [ngValue]="k.value">{{ k.label }}</option></select></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Owner *' | translate }}</label>
          <select class="form-select" name="ow" [(ngModel)]="f.ownerId" [disabled]="!!id"><option [ngValue]="null">{{ '— choose —' | translate }}</option><option *ngFor="let o of owners" [ngValue]="o.id">{{ o.name }}{{ o.active ? '' : ' (inactive)' }}</option></select>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="!id">{{ 'Not in the list?' | translate }} <a routerLink="/admin/propertymgmt/owners">{{ 'Add the owner first' | translate }}</a>{{ '. Co-owners are added on the property page after you save.' | translate }}</div>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="id">{{ 'Owners and their shares are changed on the property page, in the Owners panel.' | translate }}</div></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Area' | translate }}</label><input class="form-control" name="a" [(ngModel)]="f.area" list="areas" maxlength="120" />
            <datalist id="areas"><option *ngFor="let a of areas" [value]="a"></option></datalist></div>
          <div class="form-group mb-2" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Address' | translate }}</label><input class="form-control" name="ad" [(ngModel)]="f.address" maxlength="500" /></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">{{ 'Title / plot reference' | translate }}</label><input class="form-control" name="t" [(ngModel)]="f.titleReference" maxlength="120" /></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">{{ 'Managed since' | translate }}</label><input class="form-control" type="date" name="m" [(ngModel)]="f.managedFrom" /></div>
          <div class="form-group mb-2" style="width:12rem;"><label class="form-label">{{ 'Management fee %' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="fee" [(ngModel)]="f.fee" [placeholder]="'the owner\\'s / usual' | translate" /></div>
        </div>
        <div class="form-group mb-3"><label class="form-label">{{ 'Notes' | translate }}</label><textarea class="form-control" rows="3" name="nt" [(ngModel)]="f.notes" maxlength="2000"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? ('Save changes' | translate) : ('Save property' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class PmPropertyFormComponent implements OnInit {
  id: number | null = null;
  property: PropertyDto | null = null;
  owners: OwnerRow[] = [];
  areas: string[] = [];
  kinds = PM_PROPERTY_KINDS;
  error: string | null = null;
  saving = false;
  f = { name: '', kind: 'APARTMENT_BLOCK' as PmPropertyKind, ownerId: null as number | null, area: '', address: '', titleReference: '', managedFrom: '', fee: null as number | null, notes: '' };

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.areas = s.areas), error: () => {} });
    this.api.owners({}).subscribe({
      next: (l) => {
        this.owners = l;
        const q = Number(this.route.snapshot.queryParamMap.get('ownerId'));
        if (!this.id && q && l.some((o) => o.id === q)) this.f.ownerId = q;
      },
      error: () => {},
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.api.property(this.id).subscribe({
        next: (p) => {
          this.property = p;
          this.f = { name: p.name, kind: p.kind, ownerId: p.ownerId, area: p.area ?? '', address: p.address ?? '', titleReference: p.titleReference ?? '', managedFrom: p.managedFrom ?? '', fee: p.feePercent, notes: p.notes ?? '' };
        },
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the property'),
      });
    }
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!txt(f.name)) { this.error = 'Give the property a name.'; return; }
    if (!f.ownerId) { this.error = 'Choose the owner.'; return; }
    const hasFee = f.fee != null && String(f.fee) !== '';
    const p: PropertyPayload = { name: txt(f.name), kind: f.kind, ownerId: f.ownerId, area: txt(f.area), address: txt(f.address), titleReference: txt(f.titleReference), notes: txt(f.notes) };
    if (f.managedFrom) p.managedFrom = f.managedFrom;
    else if (this.property?.managedFrom) p.clearManagedFrom = true;
    if (hasFee) p.feePercent = Number(f.fee);
    else if (this.property?.feePercent != null) p.clearFee = true;
    this.saving = true;
    const call = this.id ? this.api.updateProperty(this.id, p) : this.api.createProperty(p);
    call.subscribe({
      next: (r) => { this.saving = false; this.router.navigate(['/admin/propertymgmt/properties', r.id]); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the property'; },
    });
  }
}
