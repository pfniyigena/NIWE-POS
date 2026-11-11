import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, HousekeepingStatus, Room } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';
import { HkBoardComponent } from './hk-board.component';
import { HkLostFoundComponent } from './hk-lost-found.component';
import { HkSuppliesComponent } from './hk-supplies.component';
import { HkSetupComponent } from './hk-setup.component';

import { T } from '../core/i18n/t';
/** Hotel > Housekeeping: every room by cleaning state. One click moves a room along: dirty → cleaning → clean → inspected. */
@Component({
  selector: 'app-housekeeping',
  standalone: true,
  imports: [TranslatePipe, CommonModule, ShellComponent, HkBoardComponent, HkLostFoundComponent, HkSuppliesComponent, HkSetupComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Housekeeping' | translate }}</h3>
      <div class="flex gap-2 mb-3 hk-tabs" style="flex-wrap:wrap;">
        <button class="btn btn-sm" *ngFor="let t of tabs" [ngClass]="tab === t.code ? 'btn-primary' : 'btn-outline-secondary'" [attr.data-tab]="t.code" (click)="tab = t.code">{{ t.label }}</button>
      </div>
      <app-hk-board *ngIf="tab === 'board'"></app-hk-board>
      <app-hk-lost-found *ngIf="tab === 'found'"></app-hk-lost-found>
      <app-hk-supplies *ngIf="tab === 'supplies'"></app-hk-supplies>
      <app-hk-setup *ngIf="tab === 'setup'"></app-hk-setup>
      <ng-container *ngIf="tab === 'rooms'">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <button class="btn btn-sm" *ngFor="let f of filters" [ngClass]="filter === f.code ? 'btn-primary' : 'btn-outline-secondary'" (click)="filter = f.code">{{ f.label }} ({{ count(f.code) }})</button>
      </div>
      <div class="stat-grid">
        <div class="stat-tile" *ngFor="let r of shown()" [class.warn]="r.housekeepingStatus === 'DIRTY'">
          <div class="flex justify-between align-center">
            <strong style="font-size:1.2rem;">{{ r.number }}</strong>
            <span class="badge" [ngClass]="badge(r.housekeepingStatus)">{{ r.housekeepingStatus | titlecase }}</span>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ r.roomTypeName }}</div>
          <div style="font-size:0.8rem; margin:0.3rem 0;">
            <span *ngIf="r.outOfOrder" class="text-danger">{{ 'Out of order' | translate }}</span>
            <span *ngIf="!r.outOfOrder && r.occupied">{{ 'Occupied · {p0}' | translate: { p0: (r.occupantGuest) } }}</span>
            <span *ngIf="r.dnd" class="badge badge-secondary dnd-badge" style="margin-left:0.25rem;">{{ 'DND' | translate }}</span>
            <span *ngIf="!r.outOfOrder && !r.occupied" class="text-muted">{{ 'Vacant' | translate }}</span>
          </div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="r.housekeeper">{{ 'By {p0}' | translate: { p0: (r.housekeeper) } }}</div>
          <div class="flex gap-1" style="flex-wrap:wrap; margin-top:0.4rem;">
            <button class="btn btn-sm btn-outline-secondary" *ngIf="r.housekeepingStatus !== 'DIRTY'" (click)="set(r, 'DIRTY')">{{ 'Dirty' | translate }}</button>
            <button class="btn btn-sm btn-outline-primary" *ngIf="r.housekeepingStatus !== 'CLEANING'" (click)="set(r, 'CLEANING')">{{ 'Start cleaning' | translate }}</button>
            <button class="btn btn-sm btn-success" *ngIf="r.housekeepingStatus !== 'CLEAN' && r.housekeepingStatus !== 'INSPECTED'" (click)="set(r, 'CLEAN')">{{ 'Clean' | translate }}</button>
            <button class="btn btn-sm btn-outline-primary" *ngIf="r.housekeepingStatus === 'CLEAN'" (click)="set(r, 'INSPECTED')">{{ 'Inspected' | translate }}</button>
          </div>
        </div>
      </div>
      <p class="text-muted" *ngIf="shown().length === 0">{{ 'No rooms in this view.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class HousekeepingComponent implements OnInit {
  rooms: Room[] = [];
  filter = 'ALL';
  tab = 'board';
  tabs = [{ code: 'board', label: T('Today\'s board') }, { code: 'rooms', label: T('Rooms') }, { code: 'found', label: T('Lost & found') }, { code: 'supplies', label: T('Minibar & linen') }, { code: 'setup', label: T('Setup') }];
  error: string | null = null;
  filters = [
    { code: 'ALL', label: T('All') },
    { code: 'DIRTY', label: T('Dirty') },
    { code: 'CLEANING', label: T('Cleaning') },
    { code: 'CLEAN', label: T('Clean') },
    { code: 'INSPECTED', label: T('Inspected') },
  ];

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.rooms().subscribe({ next: (r) => (this.rooms = r.filter((x) => x.active)), error: (e) => (this.error = errMsg(e, 'Failed to load rooms')) });
  }

  shown(): Room[] {
    return this.filter === 'ALL' ? this.rooms : this.rooms.filter((r) => r.housekeepingStatus === this.filter);
  }

  count(code: string): number {
    return code === 'ALL' ? this.rooms.length : this.rooms.filter((r) => r.housekeepingStatus === code).length;
  }

  badge(s: HousekeepingStatus): string {
    return s === 'DIRTY' ? 'badge-danger' : s === 'CLEANING' ? 'badge-warning' : 'badge-success';
  }

  set(room: Room, status: HousekeepingStatus): void {
    let who: string | null = null;
    if (status === 'CLEANING') {
      who = prompt(`Who is cleaning room ${room.number}? (optional)`, room.housekeeper ?? '');
      if (who === null) return;
    }
    this.error = null;
    this.api.setHousekeeping(room.id, status, who || undefined).subscribe({
      next: (r) => (this.rooms = this.rooms.map((x) => (x.id === r.id ? r : x))),
      error: (e) => (this.error = errMsg(e, 'Failed to update the room')),
    });
  }
}
