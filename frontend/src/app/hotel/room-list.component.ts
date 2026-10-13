import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, Room, RoomType } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Rooms: the physical rooms, each with a type (rate comes from the type unless the room has its own). */
@Component({
  selector: 'app-room-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Rooms</h3>
        <button class="btn btn-primary" (click)="startNew()" *ngIf="!editing" [disabled]="types.length === 0">+ New room</button>
      </div>
      <div class="alert alert-warning" *ngIf="types.length === 0">Add a <a routerLink="/admin/hotel/room-types">room type</a> before adding rooms.</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="editing" class="card" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="save()">
        <h4 style="font-size:1rem;" class="mb-2">{{ editing.id ? 'Edit room' : 'New room' }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Room number</label>
            <input class="form-control" style="max-width:130px;" name="number" [(ngModel)]="form.number" required /></div>
          <div class="form-group"><label class="form-label">Floor (optional)</label>
            <input class="form-control" style="max-width:110px;" name="floor" [(ngModel)]="form.floor" /></div>
          <div class="form-group"><label class="form-label">Room type</label>
            <select class="form-control" name="type" [(ngModel)]="form.roomTypeId" required>
              <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }} ({{ t.baseRate | number: '1.0-2' }})</option>
            </select></div>
          <div class="form-group"><label class="form-label">Own rate (optional)</label>
            <input class="form-control" style="max-width:140px;" type="number" min="0" step="0.01" name="override" [(ngModel)]="form.rateOverride" placeholder="Type's rate" /></div>
        </div>
        <div class="form-group"><label class="form-label">Notes (optional)</label>
          <input class="form-control" name="notes" [(ngModel)]="form.notes" placeholder="Sea view, connecting door..." /></div>
        <div class="form-group"><label class="form-label"><input type="checkbox" name="active" [(ngModel)]="form.active" /> In use</label></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <button class="btn btn-link" type="button" (click)="editing = null">Cancel</button>
        </div>
      </form>

      <table>
        <thead><tr><th>Room</th><th>Type</th><th>Rate</th><th>State</th><th>Cleaning</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of rooms">
            <td><strong>{{ r.number }}</strong><span class="text-muted" *ngIf="r.floor"> · floor {{ r.floor }}</span></td>
            <td>{{ r.roomTypeName }}</td>
            <td>{{ r.rate | number: '1.2-2' }}<span class="text-muted" *ngIf="r.rateOverride !== null"> (own)</span></td>
            <td>
              <span class="badge badge-secondary" *ngIf="!r.active">Not in use</span>
              <span class="badge badge-danger" *ngIf="r.active && r.outOfOrder">Out of order</span>
              <span class="badge badge-warning" *ngIf="r.active && !r.outOfOrder && r.occupied">Occupied · {{ r.occupantGuest }}</span>
              <span class="badge badge-success" *ngIf="r.active && !r.outOfOrder && !r.occupied">Vacant</span>
            </td>
            <td>{{ r.housekeepingStatus | titlecase }}</td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" (click)="edit(r)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(r)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="rooms.length === 0"><td colspan="6" class="text-muted">No rooms yet.</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RoomListComponent implements OnInit {
  rooms: Room[] = [];
  types: RoomType[] = [];
  editing: { id: number | null } | null = null;
  form: any = {};
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.api.roomTypes().subscribe((t) => (this.types = t.filter((x) => x.active)));
    this.load();
  }

  load(): void {
    this.api.rooms().subscribe({ next: (r) => (this.rooms = r), error: (e) => (this.error = errMsg(e, 'Failed to load rooms')) });
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
    if (!confirm(`Delete room ${r.number}?`)) return;
    this.error = null;
    this.api.deleteRoom(r.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
