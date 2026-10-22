import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { BlockTarget, BookableResource, BookingApiService, BookingBlock, Option } from '../../core/api/booking-api.service';
import { today } from '../../core/time-util';

/** Booking > Rooms & equipment, plus blocked time (holidays, maintenance, someone away). */
@Component({
  selector: 'app-booking-resources',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Rooms &amp; equipment</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveResource()">
        <strong>{{ editing ? 'Edit' : 'Add' }} a room or piece of equipment</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:end;">
          <div class="form-group"><label class="form-label">Name</label><input class="form-control" name="rn" [(ngModel)]="r.name" required /></div>
          <div class="form-group"><label class="form-label">Kind</label><input class="form-control" name="rk" [(ngModel)]="r.kind" placeholder="Room, Chair, Court…" required /></div>
          <div class="form-group"><label class="form-label">Capacity</label><input class="form-control" style="width:6rem;" type="number" min="1" name="rc" [(ngModel)]="r.capacity" /></div>
          <div class="form-group"><label class="form-label">Notes</label><input class="form-control" name="rt" [(ngModel)]="r.notes" /></div>
          <label style="display:flex; gap:0.3rem; align-items:center;"><input type="checkbox" name="ra" [(ngModel)]="r.active" /> In use</label>
          <button class="btn btn-primary" type="submit">{{ editing ? 'Save' : 'Add' }}</button>
          <button class="btn btn-outline-secondary" type="button" *ngIf="editing" (click)="resetResource()">Cancel</button>
        </div>
      </form>

      <table class="table-sm mb-4">
        <thead><tr><th>Name</th><th>Kind</th><th>Capacity</th><th>Notes</th><th>Bookings</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let x of resources" [style.opacity]="x.active ? 1 : 0.55">
            <td>{{ x.name }}<span class="badge badge-secondary" *ngIf="!x.active" style="margin-left:0.4rem;">Off</span></td>
            <td>{{ x.kind }}</td><td>{{ x.capacity ?? '—' }}</td><td>{{ x.notes || '—' }}</td><td>{{ x.appointments }}</td>
            <td><button class="btn btn-sm btn-outline-secondary" (click)="edit(x)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" style="margin-left:0.3rem;" *ngIf="!x.appointments" (click)="removeResource(x)">Delete</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!resources.length">No rooms or equipment yet. Add them only if your services need one.</p>

      <h3 class="mb-2">Blocked time</h3>
      <p class="text-muted">Nothing can be booked in a blocked period. Block everything (a public holiday), one person (leave) or one room (repairs).</p>
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="addBlock()">
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:end;">
          <div class="form-group"><label class="form-label">Applies to</label>
            <select class="form-select" name="bt" [(ngModel)]="b.targetType" (ngModelChange)="b.targetId = null">
              <option value="ALL">Everything</option><option value="STAFF">One person</option><option value="RESOURCE">One room / equipment</option>
            </select></div>
          <div class="form-group" *ngIf="b.targetType !== 'ALL'"><label class="form-label">Which</label>
            <select class="form-select" name="bw" [(ngModel)]="b.targetId" required>
              <option [ngValue]="null">Choose…</option>
              <option *ngFor="let o of targets()" [ngValue]="o.id">{{ o.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">From</label><input class="form-control" type="datetime-local" name="bs" [(ngModel)]="b.startAt" required /></div>
          <div class="form-group"><label class="form-label">Until</label><input class="form-control" type="datetime-local" name="be" [(ngModel)]="b.endAt" required /></div>
          <div class="form-group"><label class="form-label">Reason</label><input class="form-control" name="br" [(ngModel)]="b.reason" /></div>
          <button class="btn btn-primary" type="submit">Block</button>
        </div>
      </form>
      <table class="table-sm">
        <thead><tr><th>Applies to</th><th>From</th><th>Until</th><th>Reason</th><th>By</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let k of blocks">
            <td>{{ k.targetName }}</td><td>{{ k.startAt | date: 'EEE d MMM y, HH:mm' }}</td><td>{{ k.endAt | date: 'EEE d MMM y, HH:mm' }}</td>
            <td>{{ k.reason || '—' }}</td><td>{{ k.createdByName || '—' }}</td>
            <td><button class="btn btn-sm btn-outline-danger" (click)="removeBlock(k)">Remove</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!blocks.length">No blocked time coming up.</p>
    </app-shell>
  `,
})
export class BookingResourcesComponent implements OnInit {
  resources: BookableResource[] = [];
  blocks: BookingBlock[] = [];
  staff: Option[] = [];
  editing: number | null = null;
  r = { name: '', kind: '', capacity: null as number | null, notes: '', active: true };
  b = { targetType: 'ALL' as BlockTarget, targetId: null as number | null, startAt: '', endAt: '', reason: '' };
  error: string | null = null;

  constructor(private api: BookingApiService) {}

  ngOnInit(): void {
    this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
    this.loadResources();
    this.loadBlocks();
  }

  targets(): Option[] {
    return this.b.targetType === 'STAFF' ? this.staff : this.resources.filter((x) => x.active).map((x) => ({ id: x.id, name: x.name }));
  }

  loadResources(): void {
    this.api.resources().subscribe({ next: (l) => (this.resources = l), error: (e) => (this.error = e?.error?.message ?? 'Could not load the rooms') });
  }

  loadBlocks(): void {
    this.api.blocks(today()).subscribe({ next: (l) => (this.blocks = l), error: (e) => (this.error = e?.error?.message ?? 'Could not load the blocked time') });
  }

  edit(x: BookableResource): void {
    this.editing = x.id;
    this.r = { name: x.name, kind: x.kind, capacity: x.capacity, notes: x.notes ?? '', active: x.active };
  }

  resetResource(): void {
    this.editing = null;
    this.r = { name: '', kind: '', capacity: null, notes: '', active: true };
  }

  saveResource(): void {
    this.error = null;
    const p = { name: this.r.name.trim(), kind: this.r.kind.trim(), capacity: this.r.capacity, notes: this.r.notes.trim() || null, active: this.r.active };
    const call = this.editing ? this.api.updateResource(this.editing, p) : this.api.createResource(p);
    call.subscribe({ next: () => { this.resetResource(); this.loadResources(); }, error: (e) => (this.error = e?.error?.message ?? 'Could not save') });
  }

  removeResource(x: BookableResource): void {
    if (!confirm(`Delete "${x.name}"?`)) return;
    this.api.deleteResource(x.id).subscribe({ next: () => this.loadResources(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete') });
  }

  addBlock(): void {
    this.error = null;
    this.api.addBlock({ targetType: this.b.targetType, targetId: this.b.targetType === 'ALL' ? null : this.b.targetId, startAt: this.b.startAt, endAt: this.b.endAt, reason: this.b.reason.trim() || null }).subscribe({
      next: () => { this.b = { targetType: 'ALL', targetId: null, startAt: '', endAt: '', reason: '' }; this.loadBlocks(); },
      error: (e) => (this.error = e?.error?.message ?? 'Could not block this time'),
    });
  }

  removeBlock(k: BookingBlock): void {
    this.api.deleteBlock(k.id).subscribe({ next: () => this.loadBlocks(), error: (e) => (this.error = e?.error?.message ?? 'Could not remove') });
  }
}
