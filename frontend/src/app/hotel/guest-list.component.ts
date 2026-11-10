import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { Guest, HotelApiService } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';
import { errMsg } from './hotel-util';
import { GuestFormComponent } from './guest-form.component';

/** Hotel > Guests: everyone who has booked or stayed, searchable by name, phone, email or ID number. */
@Component({
  selector: 'app-guest-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent, GuestFormComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Guests' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/hotel/guests/duplicates">{{ 'Find duplicates' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/hotel/guest-register">{{ 'Guest register' | translate }}</a>
          <button class="btn btn-primary new-guest" (click)="startNew()" *ngIf="!editing">{{ '+ New guest' | translate }}</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <app-guest-form *ngIf="editing" [guest]="editing.guest" (saved)="editing = null; load()" (cancelled)="editing = null"></app-guest-form>

      <app-list-filter exportPath="/hotel/guests/export" [placeholder]="'Search name, phone, email, ID number' | translate" [statuses]="[]" [dates]="false" (filterChange)="list.apply($event)"></app-list-filter>
      <table class="table-sm">
        <thead><tr><th>{{ 'Guest' | translate }}</th><th>{{ 'Contact' | translate }}</th><th>{{ 'ID' | translate }}</th><th>{{ 'Stays' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let g of list.rows">
            <td><a class="guest-link" [routerLink]="['/admin/hotel/guests', g.id]"><strong>{{ g.fullName }}</strong></a>
              <span class="badge badge-warning vip-badge" style="margin-left:0.3rem;" *ngIf="g.vip">{{ 'VIP' | translate }}</span>
              <span class="badge badge-danger black-badge" style="margin-left:0.3rem;" *ngIf="g.blacklisted" [title]="g.blacklistReason || ''">{{ 'Blacklisted' | translate }}</span>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="g.nationality">{{ g.nationality }}</div></td>
            <td>{{ g.phone }}<div class="text-muted" style="font-size:0.8rem;">{{ g.email }}</div></td>
            <td>{{ g.idType }} {{ g.idNumber }}</td>
            <td>{{ g.stays }}</td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary edit-guest" (click)="edit(g)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(g)" [disabled]="g.stays > 0">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No guests match this search.' | translate) : ('No guests yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class GuestListComponent implements OnInit {
  list = new ListState<Guest>((f, p, sz) => this.api.page<Guest>('guests', f, p, sz), 'Failed to load guests');
  editing: { guest: Guest | null } | null = null;
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.list.load();
  }

  startNew(): void {
    this.editing = { guest: null };
  }

  edit(g: Guest): void {
    this.editing = { guest: g };
  }

  remove(g: Guest): void {
    if (!confirm(`Delete ${g.fullName}?`)) return;
    this.api.deleteGuest(g.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
