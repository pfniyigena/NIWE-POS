import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ListingPurpose, Option, PROPERTY_TYPES, PropertyPayload, PropertyType, RealEstateApiService } from '../../core/api/realestate-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const txt = (v: string | null | undefined): string | null => { const t = (v ?? '').trim(); return t ? t : null; };

/** Real Estate > Properties > Add / edit. Photos are added on the property's own page once it has been saved. */
@Component({
  selector: 'app-property-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Edit property' : 'Add property' }}</h3>
        <a class="btn btn-outline-secondary" [routerLink]="id ? ['/admin/realestate/properties', id] : ['/admin/realestate/properties']">Cancel</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem; max-width:50rem;" (ngSubmit)="save()">
        <h4 class="mb-2">The listing</h4>
        <div class="form-group mb-2"><label class="form-label">Title *</label><input class="form-control" name="title" [(ngModel)]="f.title" maxlength="200" placeholder="e.g. 3-bedroom house with garden" /></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Type *</label>
            <select class="form-select" name="type" [(ngModel)]="f.type"><option *ngFor="let t of types" [value]="t.value">{{ t.label }}</option></select></div>
          <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Offered for *</label>
            <select class="form-select" name="purpose" [(ngModel)]="f.purpose"><option value="SALE">Sale</option><option value="RENT">Rent</option><option value="BOTH">Sale or rent</option></select></div>
          <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Agent</label>
            <select class="form-select" name="agent" [(ngModel)]="f.agentUserId"><option [ngValue]="null">Me</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option></select></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="min-width:14rem;" *ngIf="f.purpose !== 'RENT'"><label class="form-label">Sale price</label><input class="form-control" type="number" min="0" name="sale" [(ngModel)]="f.salePrice" /></div>
          <div class="form-group mb-2" style="min-width:14rem;" *ngIf="f.purpose !== 'SALE'"><label class="form-label">Rent per month</label><input class="form-control" type="number" min="0" name="rent" [(ngModel)]="f.rentPrice" /></div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="neg" [(ngModel)]="f.negotiable" /> The price is negotiable</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="feat" [(ngModel)]="f.featured" /> Feature this property (shown first)</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="pub" [(ngModel)]="f.publicListing" /> Show on the public website</label>
        <div class="text-muted mb-3" style="font-size:0.85rem;">Visitors see it only while it is Available and the website is switched on in the settings. They never see the owner, the street address or the map position.</div>

        <h4 class="mb-2">Where it is</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">Area</label>
            <input class="form-control" name="area" [(ngModel)]="f.area" list="re-areas" maxlength="120" />
            <datalist id="re-areas"><option *ngFor="let a of areas" [value]="a"></option></datalist></div>
          <div class="form-group mb-2" style="flex:2; min-width:16rem;"><label class="form-label">Address or directions</label><input class="form-control" name="addr" [(ngModel)]="f.address" maxlength="300" /></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">Title / plot reference</label><input class="form-control" name="tref" [(ngModel)]="f.titleReference" maxlength="120" placeholder="e.g. Block 12 Plot 345" /></div>
          <div class="form-group mb-2" style="width:9rem;"><label class="form-label">Latitude</label><input class="form-control" type="number" step="any" name="lat" [(ngModel)]="f.latitude" /></div>
          <div class="form-group mb-2" style="width:9rem;"><label class="form-label">Longitude</label><input class="form-control" type="number" step="any" name="lng" [(ngModel)]="f.longitude" /></div>
        </div>

        <h4 class="mb-2 mt-2">The building</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="width:7rem;"><label class="form-label">Bedrooms</label><input class="form-control" type="number" min="0" name="bed" [(ngModel)]="f.bedrooms" /></div>
          <div class="form-group mb-2" style="width:7rem;"><label class="form-label">Bathrooms</label><input class="form-control" type="number" min="0" name="bath" [(ngModel)]="f.bathrooms" /></div>
          <div class="form-group mb-2" style="width:7rem;"><label class="form-label">Parking</label><input class="form-control" type="number" min="0" name="park" [(ngModel)]="f.parkingSpaces" /></div>
          <div class="form-group mb-2" style="width:9rem;"><label class="form-label">Built area m²</label><input class="form-control" type="number" min="0" name="size" [(ngModel)]="f.sizeSqm" /></div>
          <div class="form-group mb-2" style="width:9rem;"><label class="form-label">Plot m²</label><input class="form-control" type="number" min="0" name="plot" [(ngModel)]="f.plotSqm" /></div>
          <div class="form-group mb-2" style="width:8rem;"><label class="form-label">Year built</label><input class="form-control" type="number" min="1800" max="2100" name="year" [(ngModel)]="f.yearBuilt" /></div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="furn" [(ngModel)]="f.furnished" /> Furnished</label>
        <div class="form-group mb-2"><label class="form-label">Features <span class="text-muted">(separate with commas)</span></label><input class="form-control" name="feat2" [(ngModel)]="f.features" maxlength="1000" placeholder="Garden, Borehole, Security wall, Solar water heating" /></div>
        <div class="form-group mb-3"><label class="form-label">Description</label><textarea class="form-control" rows="5" name="desc" [(ngModel)]="f.description" maxlength="4000"></textarea></div>

        <h4 class="mb-2">The owner <span class="text-muted" style="font-size:0.85rem;">(only staff see this)</span></h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">Name</label><input class="form-control" name="on" [(ngModel)]="f.ownerName" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">Phone</label><input class="form-control" name="op" [(ngModel)]="f.ownerPhone" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">E-mail</label><input class="form-control" type="email" name="oe" [(ngModel)]="f.ownerEmail" /></div>
        </div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : id ? 'Save changes' : 'Save and add photos' }}</button></div>
      </form>
    </app-shell>
  `,
})
export class PropertyFormComponent implements OnInit {
  id: number | null = null;
  types = PROPERTY_TYPES;
  agents: Option[] = [];
  areas: string[] = [];
  saving = false;
  error: string | null = null;
  f = {
    title: '', type: 'HOUSE' as PropertyType, purpose: 'SALE' as ListingPurpose, agentUserId: null as number | null, salePrice: null as number | null, rentPrice: null as number | null,
    negotiable: false, featured: false, publicListing: false, area: '', address: '', titleReference: '', latitude: null as number | null, longitude: null as number | null,
    bedrooms: null as number | null, bathrooms: null as number | null, parkingSpaces: null as number | null, sizeSqm: null as number | null, plotSqm: null as number | null,
    yearBuilt: null as number | null, furnished: false, features: '', description: '', ownerName: '', ownerPhone: '', ownerEmail: '',
  };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.api.settings().subscribe({ next: (s) => (this.areas = s.areas), error: () => {} });
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.id = Number(id);
      this.api.property(this.id).subscribe({
        next: (p) => {
          Object.assign(this.f, {
            title: p.title, type: p.type, purpose: p.purpose, agentUserId: p.agentUserId, salePrice: p.salePrice, rentPrice: p.rentPrice, negotiable: p.negotiable, featured: p.featured, publicListing: p.publicListing,
            area: p.area ?? '', address: p.address ?? '', titleReference: p.titleReference ?? '', latitude: p.latitude, longitude: p.longitude, bedrooms: p.bedrooms,
            bathrooms: p.bathrooms, parkingSpaces: p.parkingSpaces, sizeSqm: p.sizeSqm, plotSqm: p.plotSqm, yearBuilt: p.yearBuilt, furnished: p.furnished, features: p.features ?? '',
            description: p.description ?? '', ownerName: p.ownerName ?? '', ownerPhone: p.ownerPhone ?? '', ownerEmail: p.ownerEmail ?? '',
          });
        },
        error: (e) => (this.error = e?.error?.message ?? 'Could not load the property'),
      });
    }
  }

  save(): void {
    this.error = null;
    if (!this.f.title.trim()) { this.error = 'Enter a title for the listing.'; return; }
    const f = this.f;
    const body: PropertyPayload = {
      title: f.title.trim(), type: f.type, purpose: f.purpose, description: txt(f.description), area: txt(f.area), address: txt(f.address), titleReference: txt(f.titleReference),
      latitude: num(f.latitude), longitude: num(f.longitude), bedrooms: num(f.bedrooms), bathrooms: num(f.bathrooms), parkingSpaces: num(f.parkingSpaces), sizeSqm: num(f.sizeSqm),
      plotSqm: num(f.plotSqm), yearBuilt: num(f.yearBuilt), furnished: f.furnished, features: txt(f.features), salePrice: f.purpose === 'RENT' ? null : num(f.salePrice),
      rentPrice: f.purpose === 'SALE' ? null : num(f.rentPrice), negotiable: f.negotiable, featured: f.featured, publicListing: f.publicListing, ownerName: txt(f.ownerName), ownerPhone: txt(f.ownerPhone),
      ownerEmail: txt(f.ownerEmail), agentUserId: f.agentUserId,
    };
    this.saving = true;
    const call = this.id ? this.api.updateProperty(this.id, body) : this.api.createProperty(body);
    call.subscribe({
      next: (p) => this.router.navigate(['/admin/realestate/properties', p.id]),
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the property'; },
    });
  }
}
