import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { HotelApiService, Maintenance, Room } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Maintenance: faults and repairs. A request that "takes the room out of service" blocks bookings and check-in until it is done. */
@Component({
  selector: 'app-maintenance',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Maintenance</h3>
        <button class="btn btn-primary" (click)="adding = !adding" *ngIf="auth.canManage('HOTEL')">{{ adding ? 'Close' : '+ Report a problem' }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="adding" class="card" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="add()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Room</label>
            <select class="form-control" name="room" [(ngModel)]="form.roomId" required><option *ngFor="let r of rooms" [ngValue]="r.id">{{ r.number }}</option></select></div>
          <div class="form-group"><label class="form-label">Problem</label><input class="form-control" name="title" [(ngModel)]="form.title" required placeholder="Air conditioner not cooling" /></div>
          <div class="form-group"><label class="form-label">Priority</label>
            <select class="form-control" name="prio" [(ngModel)]="form.priority"><option>LOW</option><option>NORMAL</option><option>HIGH</option><option>URGENT</option></select></div>
        </div>
        <div class="form-group"><label class="form-label">Details (optional)</label><input class="form-control" name="desc" [(ngModel)]="form.description" /></div>
        <label class="mb-2" style="display:block;"><input type="checkbox" name="blocks" [(ngModel)]="form.blocksRoom" /> Take the room out of service until fixed</label>
        <button class="btn btn-primary" type="submit">Save</button>
      </form>

      <table>
        <thead><tr><th>Room</th><th>Problem</th><th>Priority</th><th>Status</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let m of items">
            <td><strong>{{ m.roomNumber }}</strong><div class="badge badge-danger" *ngIf="m.blocksRoom && m.status !== 'DONE'">Out of service</div></td>
            <td>{{ m.title }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="m.description">{{ m.description }}</div><div class="text-muted" style="font-size:0.75rem;">Reported {{ m.createdAt | date: 'yyyy-MM-dd' }}{{ m.reportedBy ? ' by ' + m.reportedBy : '' }}</div></td>
            <td>{{ m.priority | titlecase }}</td>
            <td><span class="badge" [ngClass]="m.status === 'DONE' ? 'badge-success' : m.status === 'IN_PROGRESS' ? 'badge-warning' : 'badge-danger'">{{ m.status === 'IN_PROGRESS' ? 'In progress' : (m.status | titlecase) }}</span></td>
            <td class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
              <button class="btn btn-sm btn-outline-primary" *ngIf="m.status === 'OPEN'" (click)="setStatus(m, 'IN_PROGRESS')">Start</button>
              <button class="btn btn-sm btn-success" *ngIf="m.status !== 'DONE'" (click)="setStatus(m, 'DONE')">Done</button>
              <button class="btn btn-sm btn-outline-secondary" *ngIf="m.status === 'DONE'" (click)="setStatus(m, 'OPEN')">Reopen</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(m)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="items.length === 0"><td colspan="5" class="text-muted">Nothing reported.</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class MaintenanceComponent implements OnInit {
  items: Maintenance[] = [];
  rooms: Room[] = [];
  adding = false;
  form: any = { roomId: null, title: '', description: '', priority: 'NORMAL', blocksRoom: false };
  error: string | null = null;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.api.rooms().subscribe((r) => (this.rooms = r));
    this.load();
  }

  load(): void {
    this.api.maintenance().subscribe({ next: (m) => (this.items = m), error: (e) => (this.error = errMsg(e, 'Failed to load maintenance')) });
  }

  add(): void {
    this.error = null;
    this.api.createMaintenance({ ...this.form, description: this.form.description || null, reportedBy: this.auth.currentUser()?.username ?? null }).subscribe({
      next: () => {
        this.adding = false;
        this.form = { roomId: null, title: '', description: '', priority: 'NORMAL', blocksRoom: false };
        this.load();
      },
      error: (e) => (this.error = errMsg(e, 'Failed to save')),
    });
  }

  setStatus(m: Maintenance, status: string): void {
    this.api.updateMaintenance(m.id, { status }).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to update')) });
  }

  remove(m: Maintenance): void {
    if (!confirm('Delete this request?')) return;
    this.api.deleteMaintenance(m.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
