import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Guest, GuestDocument, GuestProfile, HotelApiService } from '../core/api/hotel-api.service';
import { downloadBlob } from '../core/download-file';
import { GuestFormComponent } from './guest-form.component';
import { errMsg } from './hotel-util';

import { T } from '../core/i18n/t';
/** Hotel > Guests > one guest: stays and lifetime spend, preferences, ID documents, and merging a duplicate into this guest. */
@Component({
  selector: 'app-guest-profile',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, GuestFormComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <ng-container *ngIf="p as pr">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <h3 class="profile-name">
            {{ pr.guest.fullName }}
            <span class="badge badge-warning vip-badge" style="font-size:0.75rem;" *ngIf="pr.guest.vip">{{ 'VIP' | translate }}</span>
            <span class="badge badge-danger black-badge" style="font-size:0.75rem;" *ngIf="pr.guest.blacklisted">{{ 'Blacklisted' | translate }}</span>
          </h3>
          <div class="flex gap-2">
            <a class="btn btn-outline-secondary" routerLink="/admin/hotel/guests">{{ '← Guests' | translate }}</a>
            <button class="btn btn-outline-primary edit-profile" type="button" *ngIf="canManage && !editing" (click)="editing = true">{{ 'Edit' | translate }}</button>
          </div>
        </div>
        <div class="alert alert-danger black-reason" *ngIf="pr.guest.blacklisted">{{ 'Blacklisted: {p0}' | translate: { p0: (pr.guest.blacklistReason) } }}</div>
        <app-guest-form *ngIf="editing" [guest]="pr.guest" (saved)="editing = false; message = 'Guest saved.'; load()" (cancelled)="editing = false"></app-guest-form>

        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'CONTACT' | translate }}</div>{{ pr.guest.phone || '—' }}<div style="font-size:0.85rem;">{{ pr.guest.email }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'NATIONALITY' | translate }}</div>{{ pr.guest.nationality || '—' }}</div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'ID' | translate }}</div>{{ pr.guest.idType }} {{ pr.guest.idNumber }}<span *ngIf="!pr.guest.idType && !pr.guest.idNumber">—</span></div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'BORN' | translate }}</div>{{ pr.guest.dateOfBirth || '—' }}</div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'PROFESSION' | translate }}</div>{{ pr.guest.profession || '—' }}</div>
            <div><div class="text-muted" style="font-size:0.75rem;">{{ 'ADDRESS' | translate }}</div>{{ pr.guest.address || '—' }}</div>
          </div>
          <div style="margin-top:0.6rem;" *ngIf="pr.guest.preferences"><span class="text-muted" style="font-size:0.75rem;">{{ 'PREFERENCES' | translate }}</span> <span class="prefs">{{ pr.guest.preferences }}</span></div>
          <div style="margin-top:0.3rem;" *ngIf="pr.guest.notes"><span class="text-muted" style="font-size:0.75rem;">{{ 'NOTES' | translate }}</span> {{ pr.guest.notes }}</div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">{{ 'Stays' | translate }}</div><div class="stat-value st-stays">{{ pr.stats.stays }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} night{p1}' | translate: { p0: (pr.stats.nights), p1: (pr.stats.nights === 1 ? '' : 's') } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Lifetime spend' | translate }}</div><div class="stat-value st-spend">{{ pr.stats.lifetimeSpend | number: '1.2-2' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ 'tax included' | translate }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Average per stay' | translate }}</div><div class="stat-value st-avg">{{ pr.stats.averageSpend | number: '1.2-2' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ '{p0} a night' | translate: { p0: (pr.stats.averageNightly | number: '1.2-2') } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Last stay' | translate }}</div><div class="stat-value st-last" style="font-size:1.1rem;">{{ pr.stats.lastStay || '—' }}</div><div class="text-muted" style="font-size:0.8rem;" *ngIf="pr.stats.firstStay">{{ 'first {p0}' | translate: { p0: (pr.stats.firstStay) } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Usually books' | translate }}</div><div class="stat-value st-fav" style="font-size:1.1rem;">{{ pr.stats.favouriteRoomType || '—' }}</div><div class="text-muted" style="font-size:0.8rem;" *ngIf="pr.stats.favouriteSource">{{ 'via {p0}' | translate: { p0: (pr.stats.favouriteSource) } }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Upcoming / cancelled' | translate }}</div><div class="stat-value st-up" style="font-size:1.1rem;">{{ pr.stats.upcoming }} / {{ pr.stats.cancelled + pr.stats.noShows }}</div></div>
        </div>

        <h5>{{ 'Stays' | translate }}</h5>
        <table class="table-sm stays-table">
          <thead><tr><th>{{ 'Booking' | translate }}</th><th>{{ 'Dates' | translate }}</th><th>{{ 'Room' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Source' | translate }}</th><th class="text-end">{{ 'Charged' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let s of pr.stays" class="stay-row">
              <td><a [routerLink]="['/admin/hotel/bookings', s.bookingId]">{{ s.bookingNumber }}</a></td>
              <td>{{ s.checkInDate }} → {{ s.checkOutDate }} <span class="text-muted">{{ '({p0}n)' | translate: { p0: (s.nights) } }}</span></td>
              <td>{{ s.roomNumber || '—' }} <span class="text-muted">{{ s.roomTypeName }}</span></td>
              <td>{{ statusLabel(s.status) }}</td>
              <td>{{ s.source || '—' }}</td>
              <td class="text-end">{{ s.total | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!pr.stays.length">{{ 'No bookings yet.' | translate }}</p>

        <h5 style="margin-top:1.5rem;">{{ 'ID documents' | translate }}</h5>
        <table class="table-sm docs-table" *ngIf="pr.documents.length">
          <thead><tr><th>{{ 'Document' | translate }}</th><th>{{ 'Number' | translate }}</th><th>{{ 'Expires' | translate }}</th><th>{{ 'File' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let d of pr.documents" class="doc-row">
              <td><strong>{{ d.name }}</strong> <span class="text-muted">{{ typeLabel(d.docType) }}</span></td>
              <td>{{ d.docNumber || '—' }}</td>
              <td>{{ d.expiryDate || '—' }} <span class="badge" [ngClass]="d.expiryState === 'EXPIRED' ? 'badge-danger' : 'badge-warning'" *ngIf="d.expiryState === 'EXPIRED' || d.expiryState === 'SOON'">{{ d.expiryState === 'EXPIRED' ? ('Expired' | translate) : ('Expires soon' | translate) }}</span></td>
              <td>{{ d.fileName }} <span class="text-muted">{{ '{p0} KB' | translate: { p0: ((d.sizeBytes / 1024) | number: '1.0-0') } }}</span></td>
              <td class="flex gap-1">
                <button class="btn btn-sm btn-outline-secondary download-doc" type="button" (click)="download(d)">{{ 'Download' | translate }}</button>
                <button class="btn btn-sm btn-outline-danger delete-doc" type="button" *ngIf="canManage" (click)="removeDoc(d)">{{ 'Delete' | translate }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!pr.documents.length">{{ 'No ID documents on file.' | translate }}</p>
        <form class="card doc-form" style="padding:0.8rem; margin-bottom:1rem;" *ngIf="canManage" (ngSubmit)="upload()">
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <select class="form-control doc-type" style="width:auto;" name="dtype" [(ngModel)]="docType"><option value="PASSPORT">{{ 'Passport' | translate }}</option><option value="NATIONAL_ID">{{ 'National ID' | translate }}</option><option value="DRIVING_LICENCE">{{ 'Driving licence' | translate }}</option><option value="VISA">{{ 'Visa' | translate }}</option><option value="OTHER">{{ 'Other' | translate }}</option></select>
            <input class="form-control doc-name" style="width:170px;" name="dname" [(ngModel)]="docName" [placeholder]="'Name (optional)' | translate" />
            <input class="form-control doc-number" style="width:150px;" name="dnum" [(ngModel)]="docNumber" [placeholder]="'Document number' | translate" />
            <label class="text-muted" style="font-size:0.85rem;">{{ 'Expires' | translate }} <input class="form-control doc-expiry" type="date" style="width:auto; display:inline-block;" name="dexp" [(ngModel)]="docExpiry" /></label>
            <input class="doc-file" type="file" accept="image/png,image/jpeg,image/webp,application/pdf" (change)="pick($event)" />
            <button class="btn btn-primary upload-doc" type="submit" [disabled]="busy || !file">{{ 'Upload' | translate }}</button>
          </div>
        </form>

        <div *ngIf="canManage" class="card merge-card" style="padding:0.8rem;">
          <h5 style="margin-bottom:0.4rem;">{{ 'Merge a duplicate into {p0}' | translate: { p0: (pr.guest.fullName) } }}</h5>
          <p class="text-muted" style="font-size:0.85rem;">All bookings, groups, lost &amp; found items and ID documents of the other guest move here, missing details are filled in, and the other record is removed. This cannot be undone.</p>
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <input class="form-control merge-search" style="max-width:260px;" name="msearch" [(ngModel)]="mergeQuery" (ngModelChange)="searchMerge()" [placeholder]="'Search the other guest' | translate" />
            <select class="form-control merge-pick" style="width:auto; max-width:360px;" name="mpick" [(ngModel)]="mergeId" *ngIf="mergeOptions.length">
              <option [ngValue]="null">{{ '— choose —' | translate }}</option>
              <option *ngFor="let o of mergeOptions" [ngValue]="o.id">{{ '{p0} · {p1} · {p2} stay{p3}' | translate: { p0: (o.fullName), p1: (o.phone || o.email || 'no contact'), p2: (o.stays), p3: (o.stays === 1 ? '' : 's') } }}</option>
            </select>
            <button class="btn btn-danger do-merge" type="button" [disabled]="busy || !mergeId" (click)="merge()">{{ 'Merge into this guest' | translate }}</button>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class GuestProfileComponent implements OnInit {
  p: GuestProfile | null = null;
  id = 0;
  editing = false;
  error: string | null = null;
  message: string | null = null;
  busy = false;
  docType = 'PASSPORT';
  docName = '';
  docNumber = '';
  docExpiry = '';
  file: File | null = null;
  mergeQuery = '';
  mergeOptions: Guest[] = [];
  mergeId: number | null = null;

  constructor(private api: HotelApiService, private route: ActivatedRoute, private router: Router, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((m) => {
      this.id = Number(m.get('id'));
      this.load();
    });
  }

  load(): void {
    this.api.guestProfile(this.id).subscribe({ next: (p) => (this.p = p), error: (e) => (this.error = errMsg(e, 'Failed to load the guest')) });
  }

  statusLabel(s: string): string {
    return s === 'CHECKED_IN' ? 'In house' : s === 'CHECKED_OUT' ? 'Stayed' : s === 'NO_SHOW' ? 'No-show' : s.charAt(0) + s.slice(1).toLowerCase();
  }

  typeLabel(t: string): string {
    return t === 'NATIONAL_ID' ? 'National ID' : t === 'DRIVING_LICENCE' ? 'Driving licence' : t.charAt(0) + t.slice(1).toLowerCase();
  }

  pick(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.file = input.files && input.files.length ? input.files[0] : null;
  }

  upload(): void {
    if (!this.file) return;
    this.busy = true;
    this.error = this.message = null;
    this.api.uploadGuestDocument(this.id, this.file, this.docType, this.docName, this.docNumber, this.docExpiry).subscribe({
      next: (d) => {
        this.busy = false;
        this.message = T('{p0} added.', { p0: d.name });
        this.file = null;
        this.docName = this.docNumber = this.docExpiry = '';
        const input = document.querySelector('.doc-file') as HTMLInputElement | null;
        if (input) input.value = '';
        this.load();
      },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not add the document'); },
    });
  }

  download(d: GuestDocument): void {
    this.api.guestDocumentFile(d.id).subscribe({ next: (b) => downloadBlob(b, d.fileName), error: () => (this.error = T('Could not download the file.')) });
  }

  removeDoc(d: GuestDocument): void {
    if (!confirm(T('Delete {p0}?', { p0: d.name }))) return;
    this.api.deleteGuestDocument(d.id).subscribe({ next: () => { this.message = T('Document deleted.'); this.load(); }, error: (e) => (this.error = errMsg(e, 'Could not delete the document')) });
  }

  searchMerge(): void {
    this.mergeId = null;
    if (this.mergeQuery.trim().length < 2) { this.mergeOptions = []; return; }
    this.api.guests(this.mergeQuery.trim()).subscribe((g) => (this.mergeOptions = g.filter((x) => x.id !== this.id)));
  }

  merge(): void {
    const other = this.mergeOptions.find((o) => o.id === this.mergeId);
    if (!other || !this.p) return;
    if (!confirm(T('Merge {p0} into {p1}? This cannot be undone.', { p0: other.fullName, p1: this.p.guest.fullName }))) return;
    this.busy = true;
    this.error = this.message = null;
    this.api.mergeGuests(this.id, other.id).subscribe({
      next: (r) => {
        this.busy = false;
        this.message = T('Merged {p0}: {p1} booking{p2} and {p3} document{p4} moved.', { p0: other.fullName, p1: r.bookingsMoved, p2: r.bookingsMoved === 1 ? '' : 's', p3: r.documentsMoved, p4: r.documentsMoved === 1 ? '' : 's' });
        this.mergeQuery = '';
        this.mergeOptions = [];
        this.mergeId = null;
        this.load();
      },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not merge the guests'); },
    });
  }
}
