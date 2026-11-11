import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { HotelApiService, LostFound, LostFoundPayload, Room } from '../core/api/hotel-api.service';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';
import { errMsg, today } from './hotel-util';

import { T } from '../core/i18n/t';
/** Housekeeping > Lost & found: what was left behind, where, and who it went back to. */
@Component({
  selector: 'app-hk-lost-found',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ListFilterComponent, PagerComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
    <div class="alert alert-success" *ngIf="message">{{ message }}</div>
    <div class="flex justify-between align-center mb-3">
      <span class="text-muted">{{ 'Items found in rooms and public areas.' | translate }}</span>
      <button class="btn btn-primary new-found" type="button" (click)="openForm(null)">{{ '+ Log an item' | translate }}</button>
    </div>

    <form class="card mb-3 found-form" *ngIf="form" (ngSubmit)="save()">
      <div class="card-body">
        <h5>{{ editingId ? ('Edit item' | translate) : ('Log an item' | translate) }}</h5>
        <div class="form-grid">
          <div><label class="form-label">{{ 'What was found *' | translate }}</label><input class="form-control f-desc" name="fdesc" [(ngModel)]="form.description" required /></div>
          <div><label class="form-label">{{ 'Found on' | translate }}</label><input class="form-control f-date" type="date" name="fdate" [(ngModel)]="form.foundOn" /></div>
          <div><label class="form-label">{{ 'Room' | translate }}</label>
            <select class="form-control f-room" name="froom" [(ngModel)]="form.roomId"><option [ngValue]="null">{{ '— none / public area —' | translate }}</option><option *ngFor="let r of rooms" [ngValue]="r.id">{{ r.number }}</option></select></div>
          <div><label class="form-label">{{ 'Place (if not a room)' | translate }}</label><input class="form-control f-place" name="fplace" [(ngModel)]="form.location" [placeholder]="'Lobby, restaurant, pool…' | translate" /></div>
          <div><label class="form-label">{{ 'Found by' | translate }}</label><input class="form-control f-by" name="fby" [(ngModel)]="form.foundBy" /></div>
          <div><label class="form-label">{{ 'Notes' | translate }}</label><input class="form-control f-notes" name="fnotes" [(ngModel)]="form.notes" /></div>
        </div>
        <p class="text-muted" style="font-size:0.85rem;" *ngIf="form.roomId">{{ 'The guest who last stayed in that room on the day is linked automatically, so you know whom to contact.' | translate }}</p>
        <div class="flex gap-2">
          <button class="btn btn-primary save-found" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button>
          <button class="btn btn-secondary" type="button" (click)="form = null">{{ 'Cancel' | translate }}</button>
        </div>
      </div>
    </form>

    <form class="card mb-3 claim-form" *ngIf="claiming" (ngSubmit)="claim()">
      <div class="card-body">
        <h5>{{ 'Hand over {p0} — {p1}' | translate: { p0: (claiming.itemNo), p1: (claiming.description) } }}</h5>
        <div class="form-grid">
          <div><label class="form-label">{{ 'Collected by *' | translate }}</label><input class="form-control c-by" name="cby" [(ngModel)]="claimBy" required /></div>
          <div><label class="form-label">{{ 'Notes (ID checked, courier…)' | translate }}</label><input class="form-control c-notes" name="cnotes" [(ngModel)]="claimNotes" /></div>
        </div>
        <div class="flex gap-2"><button class="btn btn-success confirm-claim" type="submit" [disabled]="busy">{{ 'Hand over' | translate }}</button><button class="btn btn-secondary" type="button" (click)="claiming = null">{{ 'Cancel' | translate }}</button></div>
      </div>
    </form>

    <app-list-filter exportPath="/hotel/lost-found/export" [placeholder]="'Search item, room, guest, place' | translate" [statuses]="statuses" statusAll="All items" [dates]="false" (filterChange)="list.apply($event)"></app-list-filter>
    <table class="table-sm found-table">
      <thead><tr><th>{{ 'No.' | translate }}</th><th>{{ 'Item' | translate }}</th><th>{{ 'Found' | translate }}</th><th>{{ 'Where' | translate }}</th><th>{{ 'Guest to contact' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
      <tbody>
        <tr *ngFor="let i of list.rows" class="found-row">
          <td><strong>{{ i.itemNo }}</strong></td>
          <td>{{ i.description }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="i.notes">{{ i.notes }}</div></td>
          <td>{{ i.foundOn }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="i.foundBy">{{ 'by {p0}' | translate: { p0: (i.foundBy) } }}</div></td>
          <td>{{ i.roomNumber ? ('Room {p0}' | translate: { p0: i.roomNumber }) : (i.location || '—') }}</td>
          <td><span *ngIf="i.guestName">{{ i.guestName }}<span class="text-muted" *ngIf="i.guestPhone"> · {{ i.guestPhone }}</span></span><span *ngIf="!i.guestName" class="text-muted">—</span></td>
          <td><span class="badge" [ngClass]="i.status === 'FOUND' ? 'badge-warning' : i.status === 'CLAIMED' ? 'badge-success' : 'badge-secondary'">{{ i.status | titlecase }}</span>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="i.claimedBy">{{ i.claimedBy }} · {{ i.claimedOn }}</div></td>
          <td class="flex gap-1" style="flex-wrap:wrap;">
            <button class="btn btn-sm btn-outline-secondary edit-found" type="button" *ngIf="i.status === 'FOUND'" (click)="openForm(i)">{{ 'Edit' | translate }}</button>
            <ng-container *ngIf="canManage && i.status === 'FOUND'">
              <button class="btn btn-sm btn-success claim-found" type="button" (click)="startClaim(i)">{{ 'Hand over' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary dispose-found" type="button" (click)="dispose(i)">{{ 'Dispose' | translate }}</button>
            </ng-container>
            <button class="btn btn-sm btn-outline-danger delete-found" type="button" *ngIf="canManage" (click)="remove(i)">{{ 'Delete' | translate }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('No item matches these filters.' | translate) : ('Nothing logged yet.' | translate) }}</p>
    <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
  `,
})
export class HkLostFoundComponent implements OnInit {
  statuses = [{ value: 'FOUND', label: T('Waiting for owner') }, { value: 'CLAIMED', label: T('Handed over') }, { value: 'DISPOSED', label: T('Disposed') }];
  list = new ListState<LostFound>((f, p, sz) => this.api.page<LostFound>('lost-found', f, p, sz), 'Failed to load lost & found');
  rooms: Room[] = [];
  form: LostFoundPayload | null = null;
  editingId: number | null = null;
  claiming: LostFound | null = null;
  claimBy = '';
  claimNotes = '';
  error: string | null = null;
  message: string | null = null;
  busy = false;

  constructor(private api: HotelApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.api.rooms().subscribe((r) => (this.rooms = r.filter((x) => x.active)));
    this.list.load();
  }

  openForm(i: LostFound | null): void {
    this.error = this.message = null;
    this.claiming = null;
    this.editingId = i ? i.id : null;
    this.form = i
      ? { description: i.description, foundOn: i.foundOn, roomId: i.roomId, location: i.location, foundBy: i.foundBy, guestId: i.guestId, notes: i.notes }
      : { description: '', foundOn: today(), roomId: null, location: null, foundBy: null, guestId: null, notes: null };
  }

  save(): void {
    if (!this.form) return;
    this.busy = true;
    this.error = null;
    const call = this.editingId ? this.api.updateLostFound(this.editingId, this.form) : this.api.createLostFound(this.form);
    call.subscribe({
      next: (i) => { this.busy = false; this.form = null; this.message = T('{p0} saved.', { p0: i.itemNo }); this.list.load(); },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not save the item'); },
    });
  }

  startClaim(i: LostFound): void {
    this.error = this.message = null;
    this.form = null;
    this.claiming = i;
    this.claimBy = i.guestName || '';
    this.claimNotes = '';
  }

  claim(): void {
    if (!this.claiming) return;
    this.busy = true;
    this.api.claimLostFound(this.claiming.id, this.claimBy, null, this.claimNotes || null).subscribe({
      next: (i) => { this.busy = false; this.claiming = null; this.message = T('{p0} handed over.', { p0: i.itemNo }); this.list.load(); },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Could not hand the item over'); },
    });
  }

  dispose(i: LostFound): void {
    const why = prompt(`Dispose of ${i.itemNo}? Note (optional):`, '');
    if (why === null) return;
    this.api.disposeLostFound(i.id, why || null).subscribe({ next: () => { this.message = T('{p0} disposed.', { p0: i.itemNo }); this.list.load(); }, error: (e) => (this.error = errMsg(e, 'Could not dispose of the item')) });
  }

  remove(i: LostFound): void {
    if (!confirm(T('Delete {p0}?', { p0: i.itemNo }))) return;
    this.api.deleteLostFound(i.id).subscribe({ next: () => { this.message = T('{p0} deleted.', { p0: i.itemNo }); this.list.load(); }, error: (e) => (this.error = errMsg(e, 'Could not delete the item')) });
  }
}
