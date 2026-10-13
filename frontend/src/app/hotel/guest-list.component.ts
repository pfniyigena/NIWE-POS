import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { Guest, GuestPayload, HotelApiService } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Guests: everyone who has booked or stayed, searchable by name, phone, email or ID number. */
@Component({
  selector: 'app-guest-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Guests</h3>
        <button class="btn btn-primary" (click)="startNew()" *ngIf="!editing">+ New guest</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="editing" class="card" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="save()">
        <h4 style="font-size:1rem;" class="mb-2">{{ editing.id ? 'Edit guest' : 'New guest' }}</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Full name</label><input class="form-control" name="fullName" [(ngModel)]="form.fullName" required /></div>
          <div class="form-group"><label class="form-label">Phone</label><input class="form-control" name="phone" [(ngModel)]="form.phone" /></div>
          <div class="form-group"><label class="form-label">Email</label><input class="form-control" type="email" name="email" [(ngModel)]="form.email" /></div>
          <div class="form-group"><label class="form-label">Nationality</label><input class="form-control" name="nat" [(ngModel)]="form.nationality" /></div>
          <div class="form-group"><label class="form-label">ID type</label>
            <select class="form-control" name="idType" [(ngModel)]="form.idType">
              <option [ngValue]="null">—</option><option>National ID</option><option>Passport</option><option>Driving licence</option><option>Other</option>
            </select></div>
          <div class="form-group"><label class="form-label">ID number</label><input class="form-control" name="idNumber" [(ngModel)]="form.idNumber" /></div>
        </div>
        <div class="form-group"><label class="form-label">Address</label><input class="form-control" name="address" [(ngModel)]="form.address" /></div>
        <div class="form-group"><label class="form-label">Notes</label><input class="form-control" name="notes" [(ngModel)]="form.notes" placeholder="Preferences, allergies to note for the kitchen..." /></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <button class="btn btn-link" type="button" (click)="editing = null">Cancel</button>
        </div>
      </form>

      <div class="form-group"><input class="form-control" style="max-width:360px;" placeholder="Search name, phone, email or ID number" [(ngModel)]="q" (ngModelChange)="search()" name="q" /></div>
      <table>
        <thead><tr><th>Guest</th><th>Contact</th><th>ID</th><th>Stays</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let g of guests">
            <td><strong>{{ g.fullName }}</strong><div class="text-muted" style="font-size:0.8rem;" *ngIf="g.nationality">{{ g.nationality }}</div></td>
            <td>{{ g.phone }}<div class="text-muted" style="font-size:0.8rem;">{{ g.email }}</div></td>
            <td>{{ g.idType }} {{ g.idNumber }}</td>
            <td>{{ g.stays }}</td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" (click)="edit(g)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(g)" [disabled]="g.stays > 0">Delete</button>
            </td>
          </tr>
          <tr *ngIf="guests.length === 0"><td colspan="5" class="text-muted">No guests found.</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class GuestListComponent implements OnInit {
  guests: Guest[] = [];
  q = '';
  editing: { id: number | null } | null = null;
  form: GuestPayload = this.blank();
  error: string | null = null;
  private timer: any;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private blank(): GuestPayload {
    return { fullName: '', phone: null, email: null, nationality: null, idType: null, idNumber: null, address: null, notes: null };
  }

  load(): void {
    this.api.guests(this.q.trim() || undefined).subscribe({ next: (g) => (this.guests = g), error: (e) => (this.error = errMsg(e, 'Failed to load guests')) });
  }

  search(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.load(), 250);
  }

  startNew(): void {
    this.editing = { id: null };
    this.form = this.blank();
  }

  edit(g: Guest): void {
    this.editing = { id: g.id };
    this.form = { fullName: g.fullName, phone: g.phone, email: g.email, nationality: g.nationality, idType: g.idType, idNumber: g.idNumber, address: g.address, notes: g.notes };
  }

  save(): void {
    this.error = null;
    this.api.saveGuest(this.editing!.id, this.form).subscribe({
      next: () => {
        this.editing = null;
        this.load();
      },
      error: (e) => (this.error = errMsg(e, 'Failed to save guest')),
    });
  }

  remove(g: Guest): void {
    if (!confirm(`Delete ${g.fullName}?`)) return;
    this.api.deleteGuest(g.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
