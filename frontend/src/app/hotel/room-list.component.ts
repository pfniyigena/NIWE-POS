import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, Room, RoomType } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';
import { errMsg } from './hotel-util';

import { T } from '../core/i18n/t';
/** Hotel > Rooms: the physical rooms, each with a type (rate comes from the type unless the room has its own). */
@Component({
  selector: 'app-room-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Rooms' | translate }}</h3>
        <button class="btn btn-primary" (click)="startNew()" *ngIf="!editing" [disabled]="types.length === 0">{{ '+ New room' | translate }}</button>
      </div>
      <div class="alert alert-warning" *ngIf="types.length === 0">{{ 'Add a' | translate }} <a routerLink="/admin/hotel/room-types">{{ 'room type' | translate }}</a> {{ 'before adding rooms.' | translate }}</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="editing" class="card" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="save()">
        <h4 style="font-size:1rem;" class="mb-2">{{ editing.id ? ('Edit room' | translate) : ('New room' | translate) }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Room number' | translate }}</label>
            <input class="form-control" style="max-width:130px;" name="number" [(ngModel)]="form.number" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Floor (optional)' | translate }}</label>
            <input class="form-control" style="max-width:110px;" name="floor" [(ngModel)]="form.floor" /></div>
          <div class="form-group"><label class="form-label">{{ 'Room type' | translate }}</label>
            <select class="form-control" name="type" [(ngModel)]="form.roomTypeId" required>
              <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }} ({{ t.baseRate | number: '1.0-2' }})</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Own rate (optional)' | translate }}</label>
            <input class="form-control" style="max-width:140px;" type="number" min="0" step="0.01" name="override" [(ngModel)]="form.rateOverride" [placeholder]="'Type\\'s rate' | translate" /></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Notes (optional)' | translate }}</label>
          <input class="form-control" name="notes" [(ngModel)]="form.notes" [placeholder]="'Sea view, connecting door...' | translate" /></div>
        <div class="form-group"><label class="form-label"><input type="checkbox" name="active" [(ngModel)]="form.active" /> {{ 'In use' | translate }}</label></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
          <button class="btn btn-link" type="button" (click)="editing = null">{{ 'Cancel' | translate }}</button>
        </div>
      </form>

      <app-list-filter exportPath="/hotel/rooms/export" [exportExtra]="extra" [extraActive]="roomTypeId !== null" [placeholder]="'Search room, floor, type, guest' | translate" [statuses]="statuses" statusAll="All rooms" [dates]="false" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center" *ngIf="types.length > 1">
        <select class="form-select type-filter" style="width:auto;" [(ngModel)]="roomTypeId" (ngModelChange)="typeChanged()" name="rt" [title]="'Room type' | translate">
          <option [ngValue]="null">{{ 'All room types' | translate }}</option>
          <option *ngFor="let t of allTypes" [ngValue]="t.id">{{ t.name }}</option>
        </select>
      </div>
      <table class="table-sm">
        <thead><tr><th>{{ 'Room' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'Rate' | translate }}</th><th>{{ 'State' | translate }}</th><th>{{ 'Cleaning' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of list.rows">
            <td><strong>{{ r.number }}</strong><span class="text-muted" *ngIf="r.floor"> {{ '· floor {p0}' | translate: { p0: (r.floor) } }}</span></td>
            <td>{{ r.roomTypeName }}</td>
            <td>{{ r.rate | number: '1.2-2' }}<span class="text-muted" *ngIf="r.rateOverride !== null"> {{ '(own)' | translate }}</span></td>
            <td>
              <span class="badge badge-secondary" *ngIf="!r.active">{{ 'Not in use' | translate }}</span>
              <span class="badge badge-danger" *ngIf="r.active && r.outOfOrder">{{ 'Out of order' | translate }}</span>
              <span class="badge badge-warning" *ngIf="r.active && !r.outOfOrder && r.occupied">{{ 'Occupied · {p0}' | translate: { p0: (r.occupantGuest) } }}</span>
              <span class="badge badge-success" *ngIf="r.active && !r.outOfOrder && !r.occupied">{{ 'Vacant' | translate }}</span>
            </td>
            <td>{{ r.housekeepingStatus | titlecase }}</td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" (click)="edit(r)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(r)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || roomTypeId ? ('No rooms match these filters.' | translate) : ('No rooms yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class RoomListComponent implements OnInit {
  types: RoomType[] = [];
  allTypes: RoomType[] = [];
  roomTypeId: number | null = null;
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [
    { value: 'OCCUPIED', label: T('Occupied') }, { value: 'VACANT', label: T('Vacant') }, { value: 'OUT_OF_ORDER', label: T('Out of order') }, { value: 'INACTIVE', label: T('Not in use') },
    { value: 'DIRTY', label: T('Dirty') }, { value: 'CLEANING', label: T('Being cleaned') }, { value: 'CLEAN', label: T('Clean') }, { value: 'INSPECTED', label: T('Inspected') },
  ];
  list = new ListState<Room>((f, p, sz) => this.api.page<Room>('rooms', f, p, sz, { roomTypeId: this.roomTypeId }), 'Failed to load rooms');
  editing: { id: number | null } | null = null;
  form: any = {};
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.api.roomTypes().subscribe((t) => { this.allTypes = t; this.types = t.filter((x) => x.active); });
    this.load();
  }

  load(): void {
    this.list.load();
  }

  typeChanged(): void {
    this.extra = { roomTypeId: this.roomTypeId };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.roomTypeId = null;
    this.extra = {};
  }

  startNew(): void {
    this.editing = { id: null };
    this.form = { number: '', floor: '', roomTypeId: this.types[0]?.id ?? null, rateOverride: null, notes: '', active: true };
  }

  edit(r: Room): void {
    this.editing = { id: r.id };
    this.form = { number: r.number, floor: r.floor, roomTypeId: r.roomTypeId, rateOverride: r.rateOverride, notes: r.notes, active: r.active };
  }

  save(): void {
    this.error = null;
    this.api.saveRoom(this.editing!.id, this.form).subscribe({
      next: () => {
        this.editing = null;
        this.load();
      },
      error: (e) => (this.error = errMsg(e, 'Failed to save room')),
    });
  }

  remove(r: Room): void {
    if (!confirm(T('Delete room {p0}?', { p0: r.number }))) return;
    this.error = null;
    this.api.deleteRoom(r.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
