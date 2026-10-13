import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, RoomType } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Room types: one nightly rate per type (plus an optional charge for each guest above the included number). */
@Component({
  selector: 'app-room-type-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Room types</h3>
        <button class="btn btn-primary" (click)="startNew()" *ngIf="!editing">+ New room type</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="editing" class="card" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="save()">
        <h4 style="font-size:1rem;" class="mb-2">{{ editing.id ? 'Edit room type' : 'New room type' }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Name</label>
            <input class="form-control" name="name" [(ngModel)]="form.name" required placeholder="e.g. Deluxe double" /></div>
          <div class="form-group"><label class="form-label">Nightly rate (before tax)</label>
            <input class="form-control" style="max-width:150px;" type="number" min="0" step="0.01" name="rate" [(ngModel)]="form.baseRate" required /></div>
          <div class="form-group"><label class="form-label">Guests included</label>
            <input class="form-control" style="max-width:110px;" type="number" min="1" name="baseOcc" [(ngModel)]="form.baseOccupancy" /></div>
          <div class="form-group"><label class="form-label">Sleeps at most</label>
            <input class="form-control" style="max-width:110px;" type="number" min="1" name="maxOcc" [(ngModel)]="form.maxOccupancy" /></div>
          <div class="form-group"><label class="form-label">Extra guest, per night</label>
            <input class="form-control" style="max-width:150px;" type="number" min="0" step="0.01" name="extra" [(ngModel)]="form.extraPersonRate" /></div>
        </div>
        <div class="form-group"><label class="form-label">Amenities (optional)</label>
          <input class="form-control" name="amenities" [(ngModel)]="form.amenities" placeholder="Wi-Fi, air conditioning, balcony" /></div>
        <div class="form-group"><label class="form-label">Description (optional)</label>
          <textarea class="form-control" rows="2" name="desc" [(ngModel)]="form.description"></textarea></div>
        <div class="form-group"><label class="form-label"><input type="checkbox" name="active" [(ngModel)]="form.active" /> Being sold</label></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <button class="btn btn-link" type="button" (click)="editing = null">Cancel</button>
        </div>
      </form>

      <table>
        <thead><tr><th>Type</th><th>Rate / night</th><th>Guests</th><th>Extra guest</th><th>Rooms</th><th>Status</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let t of types">
            <td><strong>{{ t.name }}</strong><div class="text-muted" style="font-size:0.8rem;" *ngIf="t.amenities">{{ t.amenities }}</div></td>
            <td>{{ t.baseRate | number: '1.2-2' }}</td>
            <td>{{ t.baseOccupancy }} (max {{ t.maxOccupancy }})</td>
            <td>{{ t.extraPersonRate | number: '1.2-2' }}</td>
            <td>{{ t.roomCount }}</td>
            <td><span class="badge" [ngClass]="t.active ? 'badge-success' : 'badge-secondary'">{{ t.active ? 'Active' : 'Inactive' }}</span></td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" (click)="edit(t)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(t)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="types.length === 0"><td colspan="7" class="text-muted">No room types yet — add one, then add rooms.</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class RoomTypeListComponent implements OnInit {
  types: RoomType[] = [];
  editing: { id: number | null } | null = null;
  form: any = {};
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.roomTypes().subscribe({ next: (t) => (this.types = t), error: (e) => (this.error = errMsg(e, 'Failed to load room types')) });
  }

  startNew(): void {
    this.editing = { id: null };
    this.form = { name: '', baseRate: null, baseOccupancy: 2, maxOccupancy: 2, extraPersonRate: 0, amenities: '', description: '', active: true };
  }

  edit(t: RoomType): void {
    this.editing = { id: t.id };
    this.form = { ...t };
  }

  save(): void {
    this.error = null;
    this.api.saveRoomType(this.editing!.id, this.form).subscribe({
      next: () => {
        this.editing = null;
        this.load();
      },
      error: (e) => (this.error = errMsg(e, 'Failed to save room type')),
    });
  }

  remove(t: RoomType): void {
    if (!confirm(`Delete "${t.name}"?`)) return;
    this.error = null;
    this.api.deleteRoomType(t.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
