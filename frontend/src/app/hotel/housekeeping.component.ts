import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, HousekeepingStatus, Room } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Housekeeping: every room by cleaning state. One click moves a room along: dirty → cleaning → clean → inspected. */
@Component({
  selector: 'app-housekeeping',
  standalone: true,
  imports: [CommonModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Housekeeping</h3>
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
            <span *ngIf="r.outOfOrder" class="text-danger">Out of order</span>
            <span *ngIf="!r.outOfOrder && r.occupied">Occupied · {{ r.occupantGuest }}</span>
            <span *ngIf="!r.outOfOrder && !r.occupied" class="text-muted">Vacant</span>
          </div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="r.housekeeper">By {{ r.housekeeper }}</div>
          <div class="flex gap-1" style="flex-wrap:wrap; margin-top:0.4rem;">
            <button class="btn btn-sm btn-outline-secondary" *ngIf="r.housekeepingStatus !== 'DIRTY'" (click)="set(r, 'DIRTY')">Dirty</button>
            <button class="btn btn-sm btn-outline-primary" *ngIf="r.housekeepingStatus !== 'CLEANING'" (click)="set(r, 'CLEANING')">Start cleaning</button>
            <button class="btn btn-sm btn-success" *ngIf="r.housekeepingStatus !== 'CLEAN' && r.housekeepingStatus !== 'INSPECTED'" (click)="set(r, 'CLEAN')">Clean</button>
            <button class="btn btn-sm btn-outline-primary" *ngIf="r.housekeepingStatus === 'CLEAN'" (click)="set(r, 'INSPECTED')">Inspected</button>
          </div>
        </div>
      </div>
      <p class="text-muted" *ngIf="shown().length === 0">No rooms in this view.</p>
    </app-shell>
  `,
})
export class HousekeepingComponent implements OnInit {
  rooms: Room[] = [];
  filter = 'ALL';
  error: string | null = null;
  filters = [
    { code: 'ALL', label: 'All' },
    { code: 'DIRTY', label: 'Dirty' },
    { code: 'CLEANING', label: 'Cleaning' },
    { code: 'CLEAN', label: 'Clean' },
    { code: 'INSPECTED', label: 'Inspected' },
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
