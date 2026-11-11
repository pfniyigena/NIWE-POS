import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { HotelApiService, Maintenance, Room } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';
import { errMsg } from './hotel-util';

import { T } from '../core/i18n/t';
/** Hotel > Maintenance: faults and repairs. A request that "takes the room out of service" blocks bookings and check-in until it is done. */
@Component({
  selector: 'app-maintenance',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Maintenance' | translate }}</h3>
        <button class="btn btn-primary" (click)="adding = !adding" *ngIf="auth.canManage('HOTEL')">{{ adding ? ('Close' | translate) : ('+ Report a problem' | translate) }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-warning conflicts-warning" *ngIf="conflicts.length">
        <strong>{{ 'Some bookings fall inside the blocked dates:' | translate }}</strong>
        <div *ngFor="let c of conflicts">{{ c }}</div>
        <div style="font-size:0.85rem;">{{ 'These guests keep their reservations; move them to another room or change the dates.' | translate }}</div>
      </div>

      <form *ngIf="adding" class="card" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="add()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Room' | translate }}</label>
            <select class="form-control" name="room" [(ngModel)]="form.roomId" required><option *ngFor="let r of rooms" [ngValue]="r.id">{{ r.number }}</option></select></div>
          <div class="form-group"><label class="form-label">{{ 'Problem' | translate }}</label><input class="form-control" name="title" [(ngModel)]="form.title" required [placeholder]="'Air conditioner not cooling' | translate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Priority' | translate }}</label>
            <select class="form-control" name="prio" [(ngModel)]="form.priority"><option>{{ 'LOW' | translate }}</option><option>{{ 'NORMAL' | translate }}</option><option>{{ 'HIGH' | translate }}</option><option>{{ 'URGENT' | translate }}</option></select></div>
        </div>
        <div class="form-group"><label class="form-label">{{ 'Details (optional)' | translate }}</label><input class="form-control" name="desc" [(ngModel)]="form.description" /></div>
        <label class="mb-2" style="display:block;"><input type="checkbox" name="blocks" [(ngModel)]="form.blocksRoom" /> {{ 'Take the room out of service until fixed' | translate }}</label>
        <div class="flex gap-3 block-dates" style="flex-wrap:wrap;" *ngIf="form.blocksRoom">
          <div class="form-group"><label class="form-label">{{ 'Out of service from (optional)' | translate }}</label><input class="form-control" type="date" name="bf" [(ngModel)]="form.blockFrom" /></div>
          <div class="form-group"><label class="form-label">{{ 'Back in service on (optional)' | translate }}</label><input class="form-control" type="date" name="bu" [(ngModel)]="form.blockUntil" /></div>
          <div class="text-muted" style="font-size:0.8rem; align-self:center;">{{ 'Leave both empty to block the room right away until the request is done. With dates, only those nights are blocked, so earlier bookings are not affected.' | translate }}</div>
        </div>
        <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
      </form>

      <app-list-filter exportPath="/hotel/maintenance/export" [exportExtra]="extra" [extraActive]="priority !== ''" [placeholder]="'Search room, problem, reporter' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="false" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select prio-filter" style="width:auto;" [(ngModel)]="priority" (ngModelChange)="priorityChanged()" name="pr" [title]="'Priority' | translate">
          <option value="">{{ 'All priorities' | translate }}</option><option value="URGENT">{{ 'Urgent' | translate }}</option><option value="HIGH">{{ 'High' | translate }}</option><option value="NORMAL">{{ 'Normal' | translate }}</option><option value="LOW">{{ 'Low' | translate }}</option>
        </select>
      </div>
      <table class="table-sm">
        <thead><tr><th>{{ 'Room' | translate }}</th><th>{{ 'Problem' | translate }}</th><th>{{ 'Priority' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let m of list.rows">
            <td><strong>{{ m.roomNumber }}</strong><div class="badge badge-danger" *ngIf="m.blocksRoom && m.status !== 'DONE'">{{ 'Out of service' | translate }}</div><div class="text-muted block-window" style="font-size:0.75rem;" *ngIf="m.blocksRoom && m.status !== 'DONE' && (m.blockFrom || m.blockUntil)">{{ m.blockFrom || 'now' }} → {{ m.blockUntil || 'until fixed' }}</div></td>
            <td>{{ m.title }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="m.description">{{ m.description }}</div><div class="text-muted" style="font-size:0.75rem;">{{ 'Reported {p0}{p1}' | translate: { p0: (m.createdAt | date: 'yyyy-MM-dd'), p1: (m.reportedBy ? ' by ' + m.reportedBy : '') } }}</div></td>
            <td>{{ m.priority | titlecase }}</td>
            <td><span class="badge" [ngClass]="m.status === 'DONE' ? 'badge-success' : m.status === 'IN_PROGRESS' ? 'badge-warning' : 'badge-danger'">{{ m.status === 'IN_PROGRESS' ? ('In progress' | translate) : ((m.status | titlecase) | translate) }}</span></td>
            <td class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
              <button class="btn btn-sm btn-outline-primary" *ngIf="m.status === 'OPEN'" (click)="setStatus(m, 'IN_PROGRESS')">{{ 'Start' | translate }}</button>
              <button class="btn btn-sm btn-success" *ngIf="m.status !== 'DONE'" (click)="setStatus(m, 'DONE')">{{ 'Done' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" *ngIf="m.status === 'DONE'" (click)="setStatus(m, 'OPEN')">{{ 'Reopen' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(m)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || priority ? ('Nothing matches these filters.' | translate) : ('Nothing reported.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class MaintenanceComponent implements OnInit {
  rooms: Room[] = [];
  priority = '';
  extra: Record<string, string | number | null | undefined> = {};
  statuses = [{ value: 'OPEN', label: T('Open') }, { value: 'IN_PROGRESS', label: T('In progress') }, { value: 'DONE', label: T('Done') }];
  list = new ListState<Maintenance>((f, p, sz) => this.api.page<Maintenance>('maintenance', f, p, sz, { priority: this.priority }), 'Failed to load maintenance');
  adding = false;
  form: any = { roomId: null, title: '', description: '', priority: 'NORMAL', blocksRoom: false, blockFrom: '', blockUntil: '' };
  error: string | null = null;
  conflicts: string[] = [];

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.api.rooms().subscribe((r) => (this.rooms = r));
    this.load();
  }

  load(): void {
    this.list.load();
  }

  priorityChanged(): void {
    this.extra = { priority: this.priority };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.priority = '';
    this.extra = {};
  }

  add(): void {
    this.error = null;
    this.conflicts = [];
    const f = this.form;
    this.api.createMaintenance({ ...f, description: f.description || null, reportedBy: this.auth.currentUser()?.username ?? null, blockFrom: f.blocksRoom && f.blockFrom ? f.blockFrom : null, blockUntil: f.blocksRoom && f.blockUntil ? f.blockUntil : null }).subscribe({
      next: (m) => {
        this.conflicts = m.conflicts ?? [];
        this.adding = false;
        this.form = { roomId: null, title: '', description: '', priority: 'NORMAL', blocksRoom: false, blockFrom: '', blockUntil: '' };
        this.load();
      },
      error: (e) => (this.error = errMsg(e, 'Failed to save')),
    });
  }

  setStatus(m: Maintenance, status: string): void {
    this.api.updateMaintenance(m.id, { status }).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to update')) });
  }

  remove(m: Maintenance): void {
    if (!confirm(T('Delete this request?'))) return;
    this.api.deleteMaintenance(m.id).subscribe({ next: () => this.load(), error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }
}
