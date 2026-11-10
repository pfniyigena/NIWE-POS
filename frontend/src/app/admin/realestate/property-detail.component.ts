import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReDocumentsComponent } from './re-documents.component';
import { RePrintComponent } from './re-print.component';
import { ReValuationsComponent } from './re-valuations.component';
import { RePhotoComponent } from './re-photo.component';
import { ViewingFormComponent } from './viewing-form.component';
import {
  DEAL_STAGE_LABEL, DealRow, INTEREST_LABEL, LEAD_STATUS_LABEL, PROPERTY_STATUS_LABEL, PropertyDto, PropertyStatus, PURPOSE_LABEL, RealEstateApiService, typeLabel, ViewingDto, VIEWING_STATUS_LABEL, ViewingStatus,
} from '../../core/api/realestate-api.service';

/** Real Estate > Properties > one property: photos, details, the owner, the status, who has asked about it and the viewings. */
@Component({
  selector: 'app-property-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, ReDocumentsComponent, RePrintComponent, ReValuationsComponent, RePhotoComponent, ViewingFormComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!p && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="p">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/realestate/properties" style="font-size:0.85rem;">{{ '← Properties' | translate }}</a>
            <h3>{{ p.code }} · {{ p.title }}
              <span class="badge" [class.badge-success]="p.status === 'AVAILABLE'" [class.badge-warning]="p.status === 'RESERVED'" [class.badge-secondary]="p.status !== 'AVAILABLE' && p.status !== 'RESERVED'" style="font-size:0.8rem; vertical-align:middle;">{{ statusName(p.status) }}</span></h3>
          </div>
          <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="p.canManage">
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/realestate/properties', p.id, 'edit']">{{ 'Edit' | translate }}</a>
            <a class="btn btn-outline-primary" routerLink="/admin/realestate/leads/new" [queryParams]="{ propertyId: p.id }">{{ '+ Lead for this property' | translate }}</a>
            <button class="btn btn-outline-primary" type="button" *ngIf="p.status === 'AVAILABLE'" (click)="showViewing = !showViewing">{{ 'Book viewing' | translate }}</button>
            <a class="btn btn-outline-primary" routerLink="/admin/realestate/deals/new" [queryParams]="{ propertyId: p.id }" *ngIf="p.purpose !== 'RENT' && (p.status === 'AVAILABLE' || p.status === 'RESERVED')">{{ 'Record an offer' | translate }}</a>
            <button class="btn btn-outline-danger" type="button" (click)="remove()">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <app-re-print kind="property" [id]="p.id"></app-re-print>
        <app-viewing-form *ngIf="showViewing" [fixedPropertyId]="p.id" (saved)="viewingSaved()" (closed)="showViewing = false"></app-viewing-form>
        <app-viewing-form *ngIf="moving" [existing]="moving" (saved)="viewingSaved()" (closed)="moving = null"></app-viewing-form>

        <div class="card mb-3" style="padding:1rem;" *ngIf="p.canManage">
          <h4 class="mb-2">{{ 'Status' | translate }}</h4>
          <div class="alert alert-danger" *ngIf="statusError">{{ statusError }}</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button *ngFor="let s of statusButtons()" class="btn btn-sm" [class.btn-primary]="s === 'AVAILABLE'" [class.btn-outline-secondary]="s !== 'AVAILABLE'" type="button" (click)="setStatus(s)" [disabled]="busy">{{ statusVerb(s) }}</button>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(24rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <div class="flex justify-between align-center mb-2"><h4>{{ 'Photos' | translate }} <span class="text-muted" style="font-size:0.85rem;">{{ '({p0} of 12)' | translate: { p0: (p.photos.length) } }}</span></h4>
              <label class="btn btn-sm btn-outline-primary" *ngIf="p.canManage && p.photos.length < 12" style="cursor:pointer; margin:0;">
                {{ uploading ? ('Uploading…' | translate) : ('+ Add photos' | translate) }}
                <input type="file" accept="image/jpeg,image/png,image/webp" multiple style="display:none;" (change)="upload($event)" [disabled]="uploading" />
              </label></div>
            <div class="alert alert-danger" *ngIf="photoError">{{ photoError }}</div>
            <div *ngIf="p.photos.length; else nophotos">
              <app-re-photo [propertyId]="p.id" [photoId]="current ?? p.photos[0].id" width="100%" height="18rem"></app-re-photo>
              <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
                <div *ngFor="let ph of p.photos" style="position:relative; cursor:pointer;" (click)="current = ph.id" [style.outline]="(current ?? p.photos[0].id) === ph.id ? '2px solid #2563eb' : 'none'">
                  <app-re-photo [propertyId]="p.id" [photoId]="ph.id" width="5rem" height="4rem"></app-re-photo>
                  <span class="badge badge-warning" *ngIf="ph.cover" style="position:absolute; left:2px; top:2px; font-size:0.6rem;">{{ 'Cover' | translate }}</span>
                </div>
              </div>
              <div class="flex gap-2 mt-2" *ngIf="p.canManage && selected() as sel">
                <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="!sel.cover" (click)="makeCover(sel.id)">{{ 'Use as cover' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger" type="button" (click)="removePhoto(sel.id)">{{ 'Delete photo' | translate }}</button>
              </div>
            </div>
            <ng-template #nophotos><div class="text-muted">{{ 'No photos yet. Listings with photos get far more enquiries.' | translate }}</div></ng-template>
          </div>

          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Details' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:10rem;">{{ 'Type' | translate }}</td><td>{{ typeName(p.type) }} · {{ purposeName(p.purpose) }}</td></tr>
              <tr *ngIf="p.purpose !== 'RENT'"><td class="text-muted">{{ 'Sale price' | translate }}</td><td>{{ p.salePrice != null ? (p.salePrice | number: '1.0-0') : '—' }}<span class="text-muted" *ngIf="p.negotiable"> {{ '· negotiable' | translate }}</span></td></tr>
              <tr *ngIf="p.purpose !== 'SALE'"><td class="text-muted">{{ 'Rent per month' | translate }}</td><td>{{ p.rentPrice != null ? (p.rentPrice | number: '1.0-0') : '—' }}<span class="text-muted" *ngIf="p.negotiable"> {{ '· negotiable' | translate }}</span></td></tr>
              <tr><td class="text-muted">{{ 'Area' | translate }}</td><td>{{ p.area || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Address' | translate }}</td><td>{{ p.address || '—' }}</td></tr>
              <tr *ngIf="p.titleReference"><td class="text-muted">{{ 'Title / plot ref.' | translate }}</td><td>{{ p.titleReference }}</td></tr>
              <tr *ngIf="p.latitude != null && p.longitude != null"><td class="text-muted">{{ 'Map' | translate }}</td><td><a [href]="mapLink()" target="_blank" rel="noopener noreferrer">{{ p.latitude }}, {{ p.longitude }}</a></td></tr>
              <tr><td class="text-muted">{{ 'Bedrooms / baths' | translate }}</td><td>{{ p.bedrooms ?? '—' }} / {{ p.bathrooms ?? '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Parking' | translate }}</td><td>{{ p.parkingSpaces ?? '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Built / plot m²' | translate }}</td><td>{{ p.sizeSqm != null ? (p.sizeSqm | number: '1.0-0') : '—' }} / {{ p.plotSqm != null ? (p.plotSqm | number: '1.0-0') : '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Year built' | translate }}</td><td>{{ p.yearBuilt ?? '—' }}<span *ngIf="p.furnished"> {{ '· furnished' | translate }}</span></td></tr>
              <tr *ngIf="p.features"><td class="text-muted">{{ 'Features' | translate }}</td><td>{{ p.features }}</td></tr>
              <tr><td class="text-muted">{{ 'Agent' | translate }}</td><td>{{ p.agentName || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Public website' | translate }}</td><td>{{ p.publicListing ? (p.status === 'AVAILABLE' ? 'Shown' : 'Ticked, but hidden until it is Available') : 'Not shown' }}</td></tr>
              <tr><td class="text-muted">{{ 'Listed' | translate }}</td><td>{{ p.listedDate ? (p.listedDate | date: 'd MMM y') : 'Not listed yet' }}</td></tr>
              <tr><td class="text-muted">{{ 'Added' | translate }}</td><td>{{ '{p0} by {p1}' | translate: { p0: (p.createdAt | date: 'd MMM y'), p1: (p.createdByName || '—') } }}</td></tr>
            </tbody></table>
            <div *ngIf="p.description" class="mt-2" style="white-space:pre-wrap;">{{ p.description }}</div>
            <h4 class="mt-3 mb-1" *ngIf="p.ownerName || p.ownerPhone || p.ownerEmail">{{ 'Owner' | translate }}</h4>
            <div *ngIf="p.ownerName || p.ownerPhone || p.ownerEmail">{{ p.ownerName || '—' }}<span *ngIf="p.ownerPhone"> · {{ p.ownerPhone }}</span><span *ngIf="p.ownerEmail"> · {{ p.ownerEmail }}</span></div>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(24rem, 1fr)); gap:0.75rem;">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Viewings' | translate }}</h4>
            <table class="table-sm" *ngIf="p.viewings.length; else noviews"><tbody>
              <tr *ngFor="let v of p.viewings">
                <td style="white-space:nowrap;">{{ v.startAt | date: 'EEE d MMM, HH:mm' }}</td>
                <td><a [routerLink]="['/admin/realestate/leads', v.leadId]">{{ v.leadName }}</a><div class="text-muted" style="font-size:0.8rem;">{{ 'with {p0}' | translate: { p0: (v.agentName || '—') } }}<span *ngIf="v.feedback"> · “{{ v.feedback }}”</span></div></td>
                <td style="white-space:nowrap; text-align:right;">
                  <span class="badge" [class.badge-success]="v.status === 'DONE'" [class.badge-warning]="v.status === 'SCHEDULED'" [class.badge-secondary]="v.status === 'CANCELLED' || v.status === 'NO_SHOW'">{{ viewingName(v.status) }}</span>
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="v.status === 'SCHEDULED' && v.canManage" style="margin-left:0.3rem;" (click)="moving = v">{{ 'Move' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="v.status === 'SCHEDULED' && v.canManage" style="margin-left:0.2rem;" (click)="outcome = v; outcomeStatus = 'DONE'; outcomeFeedback = ''; outcomeError = null">{{ 'Outcome' | translate }}</button>
                </td>
              </tr>
            </tbody></table>
            <ng-template #noviews><div class="text-muted">{{ 'No viewings yet.' | translate }}</div></ng-template>
            <div class="mt-2" *ngIf="outcome">
              <div class="alert alert-danger" *ngIf="outcomeError">{{ outcomeError }}</div>
              <strong>{{ 'Outcome of the viewing on {p0}' | translate: { p0: (outcome.startAt | date: 'd MMM, HH:mm') } }}</strong>
              <div class="flex gap-2 mt-1" style="flex-wrap:wrap;">
                <select class="form-select" style="width:auto;" [value]="outcomeStatus" (change)="outcomeStatus = $any($event.target).value"><option value="DONE">{{ 'Happened' | translate }}</option><option value="NO_SHOW">{{ 'No-show' | translate }}</option><option value="CANCELLED">{{ 'Cancelled' | translate }}</option></select>
                <input class="form-control" style="flex:1; min-width:12rem;" [value]="outcomeFeedback" (input)="outcomeFeedback = $any($event.target).value" maxlength="2000" [placeholder]="'Feedback (optional)' | translate" />
                <button class="btn btn-sm btn-primary" type="button" (click)="saveOutcome()" [disabled]="busy">{{ 'Save' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" type="button" (click)="outcome = null">{{ 'Cancel' | translate }}</button>
              </div>
            </div>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Leads asking about this property' | translate }}</h4>
            <table class="table-sm" *ngIf="p.leads.length; else noleads"><tbody>
              <tr *ngFor="let l of p.leads">
                <td><a [routerLink]="['/admin/realestate/leads', l.id]"><strong>{{ l.name }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ l.phone || l.email || '—' }} · {{ interestName(l.interest) }}</div></td>
                <td style="text-align:right;"><span class="badge badge-secondary">{{ leadStage(l.status) }}</span></td>
              </tr>
            </tbody></table>
            <ng-template #noleads><div class="text-muted">{{ 'Nobody has asked about it yet.' | translate }}</div></ng-template>
          </div>
          <div class="card" style="padding:1rem;" *ngIf="deals.length">
            <h4 class="mb-2">{{ 'Sales deals' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr *ngFor="let d of deals">
                <td><a [routerLink]="['/admin/realestate/deals', d.id]"><strong>{{ d.code }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ d.buyerName }} · {{ (d.agreedPrice ?? d.offerAmount) | number: '1.0-0' }}</div></td>
                <td style="text-align:right;"><span class="badge" [class.badge-success]="d.stage === 'COMPLETED'" [class.badge-danger]="d.stage === 'CANCELLED'" [class.badge-warning]="d.stage === 'RESERVED' || d.stage === 'AGREEMENT'" [class.badge-secondary]="d.stage === 'OFFER'">{{ dealStage(d.stage) }}</span></td>
              </tr>
            </tbody></table>
          </div>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-valuations [propertyId]="p.id" [canManage]="p.canManage"></app-re-valuations>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-documents subjectType="PROPERTY" [subjectId]="p.id" [canManage]="p.canManage" defaultCategory="TITLE_DEED"></app-re-documents>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class PropertyDetailComponent implements OnInit {
  p: PropertyDto | null = null;
  deals: DealRow[] = [];
  error: string | null = null;
  statusError: string | null = null;
  photoError: string | null = null;
  busy = false;
  uploading = false;
  current: number | null = null;
  showViewing = false;
  moving: ViewingDto | null = null;
  outcome: ViewingDto | null = null;
  outcomeStatus: ViewingStatus = 'DONE';
  outcomeFeedback = '';
  outcomeError: string | null = null;

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.api.property(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (p) => this.show(p),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the property'),
    });
  }

  private show(p: PropertyDto): void {
    this.p = p;
    this.api.deals({ propertyId: p.id }).subscribe({ next: (d) => (this.deals = d), error: () => (this.deals = []) });
    if (this.current != null && !p.photos.some((x) => x.id === this.current)) this.current = null;
  }

  selected() { return this.p?.photos.find((x) => x.id === (this.current ?? this.p!.photos[0]?.id)) ?? null; }
  statusName(s: string): string { return (PROPERTY_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  purposeName(s: string): string { return (PURPOSE_LABEL as Record<string, string>)[s] ?? s; }
  typeName(t: string): string { return typeLabel(t); }
  viewingName(s: string): string { return (VIEWING_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  dealStage(s: string): string { return (DEAL_STAGE_LABEL as Record<string, string>)[s] ?? s; }
  leadStage(s: string): string { return (LEAD_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  interestName(s: string): string { return (INTEREST_LABEL as Record<string, string>)[s] ?? s; }
  mapLink(): string { return `https://www.openstreetmap.org/?mlat=${this.p!.latitude}&mlon=${this.p!.longitude}#map=17/${this.p!.latitude}/${this.p!.longitude}`; }

  statusButtons(): PropertyStatus[] {
    const p = this.p!;
    const all: PropertyStatus[] = ['AVAILABLE', 'RESERVED', 'SOLD', 'LET', 'WITHDRAWN'];
    return all.filter((s) => s !== p.status && !(s === 'SOLD' && p.purpose === 'RENT') && !(s === 'LET' && p.purpose === 'SALE'));
  }

  statusVerb(s: PropertyStatus): string {
    return { AVAILABLE: this.p!.status === 'DRAFT' ? 'List it (available)' : 'Make available', RESERVED: 'Reserve', SOLD: 'Mark sold', LET: 'Mark let', WITHDRAWN: 'Withdraw', DRAFT: 'Draft' }[s];
  }

  setStatus(s: PropertyStatus): void {
    this.busy = true;
    this.statusError = null;
    this.api.setPropertyStatus(this.p!.id, s).subscribe({
      next: (p) => { this.busy = false; this.show(p); },
      error: (e) => { this.busy = false; this.statusError = e?.error?.message ?? 'Could not change the status'; },
    });
  }

  upload(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) return;
    this.photoError = null;
    this.uploading = true;
    const next = (i: number): void => {
      if (i >= files.length) { this.uploading = false; return; }
      this.api.addPhoto(this.p!.id, files[i]).subscribe({
        next: (p) => { this.show(p); next(i + 1); },
        error: (e) => { this.photoError = `${files[i].name}: ${e?.error?.message ?? 'could not be added'}`; this.uploading = false; },
      });
    };
    next(0);
  }

  makeCover(photoId: number): void {
    this.api.setCover(this.p!.id, photoId).subscribe({ next: (p) => this.show(p), error: (e) => (this.photoError = e?.error?.message ?? 'Could not change the cover') });
  }

  removePhoto(photoId: number): void {
    if (!confirm('Delete this photo?')) return;
    this.api.deletePhoto(this.p!.id, photoId).subscribe({ next: (p) => this.show(p), error: (e) => (this.photoError = e?.error?.message ?? 'Could not delete the photo') });
  }

  viewingSaved(): void {
    this.showViewing = false;
    this.moving = null;
    this.reload();
  }

  saveOutcome(): void {
    if (!this.outcome) return;
    this.busy = true;
    this.api.setViewingStatus(this.outcome.id, this.outcomeStatus, this.outcomeFeedback.trim() || null).subscribe({
      next: () => { this.busy = false; this.outcome = null; this.reload(); },
      error: (e) => { this.busy = false; this.outcomeError = e?.error?.message ?? 'Could not save the outcome'; },
    });
  }

  remove(): void {
    if (!this.p || !confirm(`Delete ${this.p.code}? This cannot be undone.`)) return;
    this.api.deleteProperty(this.p.id).subscribe({
      next: () => this.router.navigate(['/admin/realestate/properties']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the property'),
    });
  }
}
