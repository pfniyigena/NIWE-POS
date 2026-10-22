import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Guest, GuestDocument, GuestProfile, HotelApiService } from '../core/api/hotel-api.service';
import { downloadBlob } from '../core/download-file';
import { GuestFormComponent } from './guest-form.component';
import { errMsg } from './hotel-util';

/** Hotel > Guests > one guest: stays and lifetime spend, preferences, ID documents, and merging a duplicate into this guest. */
@Component({
  selector: 'app-guest-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, GuestFormComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <ng-container *ngIf="p as pr">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <h3 class="profile-name">
            {{ pr.guest.fullName }}
            <span class="badge badge-warning vip-badge" style="font-size:0.75rem;" *ngIf="pr.guest.vip">VIP</span>
            <span class="badge badge-danger black-badge" style="font-size:0.75rem;" *ngIf="pr.guest.blacklisted">Blacklisted</span>
          </h3>
          <div class="flex gap-2">
            <a class="btn btn-outline-secondary" routerLink="/admin/hotel/guests">← Guests</a>
            <button class="btn btn-outline-primary edit-profile" type="button" *ngIf="canManage && !editing" (click)="editing = true">Edit</button>
          </div>
        </div>
        <div class="alert alert-danger black-reason" *ngIf="pr.guest.blacklisted">Blacklisted: {{ pr.guest.blacklistReason }}</div>
        <app-guest-form *ngIf="editing" [guest]="pr.guest" (saved)="editing = false; message = 'Guest saved.'; load()" (cancelled)="editing = false"></app-guest-form>

        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><div class="text-muted" style="font-size:0.75rem;">CONTACT</div>{{ pr.guest.phone || '—' }}<div style="font-size:0.85rem;">{{ pr.guest.email }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">NATIONALITY</div>{{ pr.guest.nationality || '—' }}</div>
            <div><div class="text-muted" style="font-size:0.75rem;">ID</div>{{ pr.guest.idType }} {{ pr.guest.idNumber }}<span *ngIf="!pr.guest.idType && !pr.guest.idNumber">—</span></div>
            <div><div class="text-muted" style="font-size:0.75rem;">BORN</div>{{ pr.guest.dateOfBirth || '—' }}</div>
            <div><div class="text-muted" style="font-size:0.75rem;">PROFESSION</div>{{ pr.guest.profession || '—' }}</div>
            <div><div class="text-muted" style="font-size:0.75rem;">ADDRESS</div>{{ pr.guest.address || '—' }}</div>
          </div>
          <div style="margin-top:0.6rem;" *ngIf="pr.guest.preferences"><span class="text-muted" style="font-size:0.75rem;">PREFERENCES</span> <span class="prefs">{{ pr.guest.preferences }}</span></div>
          <div style="margin-top:0.3rem;" *ngIf="pr.guest.notes"><span class="text-muted" style="font-size:0.75rem;">NOTES</span> {{ pr.guest.notes }}</div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile"><div class="stat-label">Stays</div><div class="stat-value st-stays">{{ pr.stats.stays }}</div><div class="text-muted" style="font-size:0.8rem;">{{ pr.stats.nights }} night{{ pr.stats.nights === 1 ? '' : 's' }}</div></div>
          <div class="stat-tile"><div class="stat-label">Lifetime spend</div><div class="stat-value st-spend">{{ pr.stats.lifetimeSpend | number: '1.2-2' }}</div><div class="text-muted" style="font-size:0.8rem;">tax included</div></div>
          <div class="stat-tile"><div class="stat-label">Average per stay</div><div class="stat-value st-avg">{{ pr.stats.averageSpend | number: '1.2-2' }}</div><div class="text-muted" style="font-size:0.8rem;">{{ pr.stats.averageNightly | number: '1.2-2' }} a night</div></div>
          <div class="stat-tile"><div class="stat-label">Last stay</div><div class="stat-value st-last" style="font-size:1.1rem;">{{ pr.stats.lastStay || '—' }}</div><div class="text-muted" style="font-size:0.8rem;" *ngIf="pr.stats.firstStay">first {{ pr.stats.firstStay }}</div></div>
          <div class="stat-tile"><div class="stat-label">Usually books</div><div class="stat-value st-fav" style="font-size:1.1rem;">{{ pr.stats.favouriteRoomType || '—' }}</div><div class="text-muted" style="font-size:0.8rem;" *ngIf="pr.stats.favouriteSource">via {{ pr.stats.favouriteSource }}</div></div>
          <div class="stat-tile"><div class="stat-label">Upcoming / cancelled</div><div class="stat-value st-up" style="font-size:1.1rem;">{{ pr.stats.upcoming }} / {{ pr.stats.cancelled + pr.stats.noShows }}</div></div>
        </div>

        <h5>Stays</h5>
        <table class="table-sm stays-table">
          <thead><tr><th>Booking</th><th>Dates</th><th>Room</th><th>Status</th><th>Source</th><th class="text-end">Charged</th></tr></thead>
          <tbody>
            <tr *ngFor="let s of pr.stays" class="stay-row">
              <td><a [routerLink]="['/admin/hotel/bookings', s.bookingId]">{{ s.bookingNumber }}</a></td>
              <td>{{ s.checkInDate }} → {{ s.checkOutDate }} <span class="text-muted">({{ s.nights }}n)</span></td>
              <td>{{ s.roomNumber || '—' }} <span class="text-muted">{{ s.roomTypeName }}</span></td>
              <td>{{ statusLabel(s.status) }}</td>
              <td>{{ s.source || '—' }}</td>
              <td class="text-end">{{ s.total | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!pr.stays.length">No bookings yet.</p>

        <h5 style="margin-top:1.5rem;">ID documents</h5>
        <table class="table-sm docs-table" *ngIf="pr.documents.length">
          <thead><tr><th>Document</th><th>Number</th><th>Expires</th><th>File</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let d of pr.documents" class="doc-row">
              <td><strong>{{ d.name }}</strong> <span class="text-muted">{{ typeLabel(d.docType) }}</span></td>
              <td>{{ d.docNumber || '—' }}</td>
              <td>{{ d.expiryDate || '—' }} <span class="badge" [ngClass]="d.expiryState === 'EXPIRED' ? 'badge-danger' : 'badge-warning'" *ngIf="d.expiryState === 'EXPIRED' || d.expiryState === 'SOON'">{{ d.expiryState === 'EXPIRED' ? 'Expired' : 'Expires soon' }}</span></td>
              <td>{{ d.fileName }} <span class="text-muted">{{ (d.sizeBytes / 1024) | number: '1.0-0' }} KB</span></td>
              <td class="flex gap-1">
                <button class="btn btn-sm btn-outline-secondary download-doc" type="button" (click)="download(d)">Download</button>
                <button class="btn btn-sm btn-outline-danger delete-doc" type="button" *ngIf="canManage" (click)="removeDoc(d)">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!pr.documents.length">No ID documents on file.</p>
        <form class="card doc-form" style="padding:0.8rem; margin-bottom:1rem;" *ngIf="canManage" (ngSubmit)="upload()">
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <select class="form-control doc-type" style="width:auto;" name="dtype" [(ngModel)]="docType"><option value="PASSPORT">Passport</option><option value="NATIONAL_ID">National ID</option><option value="DRIVING_LICENCE">Driving licence</option><option value="VISA">Visa</option><option value="OTHER">Other</option></select>
            <input class="form-control doc-name" style="width:170px;" name="dname" [(ngModel)]="docName" placeholder="Name (optional)" />
            <input class="form-control doc-number" style="width:150px;" name="dnum" [(ngModel)]="docNumber" placeholder="Document number" />
            <label class="text-muted" style="font-size:0.85rem;">Expires <input class="form-control doc-expiry" type="date" style="width:auto; display:inline-block;" name="dexp" [(ngModel)]="docExpiry" /></label>
            <input class="doc-file" type="file" accept="image/png,image/jpeg,image/webp,application/pdf" (change)="pick($event)" />
            <button class="btn btn-primary upload-doc" type="submit" [disabled]="busy || !file">Upload</button>
          </div>
        </form>

        <div *ngIf="canManage" class="card merge-card" style="padding:0.8rem;">
          <h5 style="margin-bottom:0.4rem;">Merge a duplicate into {{ pr.guest.fullName }}</h5>
          <p class="text-muted" style="font-size:0.85rem;">All bookings, groups, lost &amp; found items and ID documents of the other guest move here, missing details are filled in, and the other record is removed. This cannot be undone.</p>
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <input class="form-control merge-search" style="max-width:260px;" name="msearch" [(ngModel)]="mergeQuery" (ngModelChange)="searchMerge()" placeholder="Search the other guest" />
            <select class="form-control merge-pick" style="width:auto; max-width:360px;" name="mpick" [(ngModel)]="mergeId" *ngIf="mergeOptions.length">
              <option [ngValue]="null">— choose —</option>
              <option *ngFor="let o of mergeOptions" [ngValue]="o.id">{{ o.fullName }} · {{ o.phone || o.email || 'no contact' }} · {{ o.stays }} stay{{ o.stays === 1 ? '' : 's' }}</option>
            </select>
            <button class="btn btn-danger do-merge" type="button" [disabled]="busy || !mergeId" (click)="merge()">Merge into this guest</button>
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
        this.message = `${d.name} added.`;
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
    this.api.guestDocumentFile(d.id).subscribe({ next: (b) => downloadBlob(b, d.fileName), error: () => (this.error = 'Could not download the file.') });
  }

  removeDoc(d: GuestDocument): void {
    if (!confirm(`Delete ${d.name}?`)) return;
    this.api.deleteGuestDocument(d.id).subscribe({ next: () => { this.message = 'Document deleted.'; this.load(); }, error: (e) => (this.error = errMsg(e, 'Could not delete the document')) });
  }

  searchMerge(): void {
    this.mergeId = null;
    if (this.mergeQuery.trim().length < 2) { this.mergeOptions = []; return; }
    this.api.guests(this.mergeQuery.trim()).subscribe((g) => (this.mergeOptions = g.filter((x) => x.id !== this.id)));
  }

  merge(): void {
    const other = this.mergeOptions.find((o) => o.id === this.mergeId);
    if (!other || !this.p) return;
    if (!confirm(`Merge ${other.fullName} into ${this.p.guest.fullName}? This cannot be undone.`)) return;
    this.busy = true;
    this.error = this.message = null;
    this.api.mergeGuests(this.id, other.id).subscribe({
      next: (r) => {
        this.busy = false;
        this.message = `Merged ${other.fullName}: ${r.bookingsMoved} booking${r.bookingsMoved === 1 ? '' : 's'} and ${r.documentsMoved} document${r.documentsMoved === 1 ? '' : 's'} moved.`;
        this.mergeQuery = '';
        this.mergeOptions = [];
        this.mergeId = null;
        this.load();
      },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not merge the guests'); },
    });
  }
}
