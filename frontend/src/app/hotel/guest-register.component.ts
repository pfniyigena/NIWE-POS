import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, RegisterRow } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

/** Hotel > Guest register: who was in the house on the nights of a period — the book the police or immigration office asks to see. */
@Component({
  selector: 'app-guest-register',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-2">
        <h3>{{ 'Guest register' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/hotel/guests">{{ '← Guests' | translate }}</a>
      </div>
      <p class="text-muted register-help">{{ 'Everyone who slept in the hotel on any night of the period, with the details the authorities ask for. Without dates it shows who is in the house today. Download it as Excel or PDF (it has a column to sign).' | translate }}</p>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex gap-2 align-center mb-2">
        <label class="text-muted" style="font-size:0.85rem;">{{ 'Nationality' | translate }} <input class="form-control reg-nat" style="width:160px; display:inline-block;" name="regnat" [(ngModel)]="nationality" (ngModelChange)="natChanged()" /></label>
      </div>
      <app-list-filter exportPath="/hotel/guest-register/export" [exportExtra]="extra" [extraActive]="nationality.trim() !== ''" [placeholder]="'Search name, ID number, room, booking' | translate" [statuses]="[]" (filterChange)="list.apply($event)" (cleared)="nationality = ''; extra = {}"></app-list-filter>
      <table class="table-sm register-table">
        <thead><tr><th>#</th><th>{{ 'Guest' | translate }}</th><th>{{ 'Nationality' | translate }}</th><th>{{ 'ID' | translate }}</th><th>{{ 'Born' | translate }}</th><th>{{ 'Address' | translate }}</th><th>{{ 'Profession' | translate }}</th><th>{{ 'Arrived' | translate }}</th><th>{{ 'Left' | translate }}</th><th>{{ 'Room' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let r of list.rows; let i = index" class="register-row">
            <td>{{ list.page * list.size + i + 1 }}</td>
            <td><a [routerLink]="['/admin/hotel/guests', r.guestId]"><strong>{{ r.fullName }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ r.phone }}</div></td>
            <td>{{ r.nationality || '—' }}</td>
            <td>{{ r.idType }} {{ r.idNumber }}<span *ngIf="!r.idType && !r.idNumber" class="text-danger">{{ 'missing' | translate }}</span></td>
            <td>{{ r.dateOfBirth || '—' }}</td>
            <td>{{ r.address || '—' }}</td>
            <td>{{ r.profession || '—' }}</td>
            <td>{{ r.arrival }}</td>
            <td>{{ r.departure }}<span class="text-muted" *ngIf="r.status === 'CHECKED_IN'"> {{ '(expected)' | translate }}</span></td>
            <td>{{ r.roomNumber || '—' }}<div class="text-muted" style="font-size:0.8rem;"><a [routerLink]="['/admin/hotel/bookings', r.bookingId]">{{ r.bookingNumber }}</a></div></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ 'Nobody was in the house in this period.' | translate }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class GuestRegisterComponent implements OnInit {
  nationality = '';
  list = new ListState<RegisterRow>((f, p, sz) => this.api.page<RegisterRow>('guest-register', f, p, sz, { nationality: this.nationality.trim() || undefined }), 'Failed to load the guest register');

  constructor(private api: HotelApiService) {}

  extra: Record<string, string> = {};

  ngOnInit(): void {
    this.list.load();
  }

  private timer: ReturnType<typeof setTimeout> | null = null;

  natChanged(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.extra = this.nationality.trim() ? { nationality: this.nationality.trim() } : {};
      this.list.apply(this.list.filter);
    }, 300);
  }
}
