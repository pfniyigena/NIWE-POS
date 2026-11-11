import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { DuplicateCluster, DuplicateGuest, HotelApiService } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

import { T } from '../core/i18n/t';
/** Hotel > Guests > Find duplicates: guests who share an ID number, phone, e-mail or name, with one click to merge them. */
@Component({
  selector: 'app-guest-duplicates',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Possible duplicate guests' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/hotel/guests">{{ '← Guests' | translate }}</a>
      </div>
      <p class="text-muted">{{ 'Guests who share an ID number, a phone number, an e-mail address or a name. Choose the record to keep; the others are merged into it.' | translate }}</p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="card cluster" style="padding:0.8rem; margin-bottom:1rem;" *ngFor="let c of clusters">
        <div class="mb-2"><span class="badge badge-info" style="margin-right:0.3rem;" *ngFor="let m of c.matchedOn">{{ m }}</span></div>
        <table class="table-sm">
          <thead><tr><th>{{ 'Guest' | translate }}</th><th>{{ 'Contact' | translate }}</th><th>{{ 'ID number' | translate }}</th><th class="text-end">{{ 'Stays' | translate }}</th><th class="text-end">{{ 'Spent' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let g of c.guests" class="dup-row">
              <td><a [routerLink]="['/admin/hotel/guests', g.id]">{{ g.fullName }}</a>
                <span class="badge badge-warning" style="margin-left:0.3rem;" *ngIf="g.vip">{{ 'VIP' | translate }}</span>
                <span class="badge badge-danger" style="margin-left:0.3rem;" *ngIf="g.blacklisted">{{ 'Blacklisted' | translate }}</span></td>
              <td>{{ g.phone }}<div class="text-muted" style="font-size:0.8rem;">{{ g.email }}</div></td>
              <td>{{ g.idNumber || '—' }}</td>
              <td class="text-end">{{ g.stays }}</td>
              <td class="text-end">{{ g.lifetimeSpend | number: '1.2-2' }}</td>
              <td><button class="btn btn-sm btn-outline-primary keep-this" type="button" *ngIf="canManage" [disabled]="busy" (click)="mergeOthers(c, g)">{{ 'Keep this one, merge the rest' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-muted no-dups" *ngIf="loaded && !clusters.length">{{ 'No duplicates found.' | translate }}</p>
    </app-shell>
  `,
})
export class GuestDuplicatesComponent implements OnInit {
  clusters: DuplicateCluster[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;
  message: string | null = null;

  constructor(private api: HotelApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.guestDuplicates().subscribe({ next: (c) => { this.clusters = c; this.loaded = true; }, error: (e) => (this.error = errMsg(e, 'Failed to look for duplicates')) });
  }

  mergeOthers(c: DuplicateCluster, keeper: DuplicateGuest): void {
    const others = c.guests.filter((g) => g.id !== keeper.id);
    if (!confirm(T('Keep {p0} and merge {p1} into it? This cannot be undone.', { p0: keeper.fullName, p1: others.map((o) => o.fullName).join(', ') }))) return;
    this.busy = true;
    this.error = this.message = null;
    const run = (i: number, moved: number): void => {
      if (i >= others.length) {
        this.busy = false;
        this.message = T('Merged {p0} record{p1} into {p2} ({p3} booking{p4} moved).', { p0: others.length, p1: others.length === 1 ? '' : 's', p2: keeper.fullName, p3: moved, p4: moved === 1 ? '' : 's' });
        this.load();
        return;
      }
      this.api.mergeGuests(keeper.id, others[i].id).subscribe({
        next: (r) => run(i + 1, moved + r.bookingsMoved),
        error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not merge the guests'); this.load(); },
      });
    };
    run(0, 0);
  }
}
