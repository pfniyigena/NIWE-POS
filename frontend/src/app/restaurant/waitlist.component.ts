import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { WaitlistApiService } from '../core/api/waitlist-api.service';
import { DiningTableApiService } from '../core/api/dining-table-api.service';
import { DiningTable, WaitlistEntry } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

import { T } from '../core/i18n/t';
/**
 * Restaurant > Waiting list: the line at the door. Add a walk-in party with a quoted wait, tell
 * them when their table is ready, and seat them (which opens their order). The list refreshes by
 * itself so a host stand screen stays current.
 */
@Component({
  selector: 'app-waitlist',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Waiting list' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/reservations">{{ 'Reservations' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">{{ '← Floor plan' | translate }}</a>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Add a party' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="flex:1; min-width:160px;"><label class="form-label">{{ 'Name' | translate }}</label>
            <input class="form-control wl-name" [(ngModel)]="guestName" name="wlName" /></div>
          <div class="form-group" style="min-width:140px;"><label class="form-label">{{ 'Phone' | translate }}</label>
            <input class="form-control wl-phone" [(ngModel)]="guestPhone" name="wlPhone" /></div>
          <div class="form-group" style="width:90px;"><label class="form-label">{{ 'Party' | translate }}</label>
            <input class="form-control wl-party" type="number" min="1" [(ngModel)]="partySize" name="wlParty" /></div>
          <div class="form-group" style="width:150px;"><label class="form-label">{{ 'Wait (min, blank = estimate)' | translate }}</label>
            <input class="form-control wl-quote" type="number" min="0" [(ngModel)]="quoted" name="wlQuote" /></div>
          <div class="form-group" style="flex:1; min-width:160px;"><label class="form-label">{{ 'Note' | translate }}</label>
            <input class="form-control wl-notes" [(ngModel)]="notes" name="wlNotes" [placeholder]="'high chair, outside…' | translate" /></div>
          <button class="btn btn-primary wl-add" [disabled]="busy" (click)="add()">{{ 'Add to the list' | translate }}</button>
        </div>
      </div>

      <app-list-filter [placeholder]="'Search name, phone, note' | translate" [statuses]="statuses" statusAll="Still waiting" [dates]="false" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr><th>#</th><th>{{ 'Guest' | translate }}</th><th>{{ 'Party' | translate }}</th><th>{{ 'Quoted' | translate }}</th><th>{{ 'Waiting' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let w of list.rows">
            <tr class="wl-row">
              <td>{{ w.position || '—' }}</td>
              <td>{{ w.guestName }}<span class="text-muted" *ngIf="w.guestPhone"> — {{ w.guestPhone }}</span>
                <div class="text-muted" style="font-size:0.8rem;" *ngIf="w.notes">{{ w.notes }}</div></td>
              <td>{{ w.partySize }}</td>
              <td>{{ '{p0} min' | translate: { p0: (w.quotedMinutes) } }}</td>
              <td [style.color]="late(w) ? '#b91c1c' : ''">{{ '{p0} min' | translate: { p0: (w.waitedMinutes) } }}</td>
              <td>{{ pretty(w.status) }}<div class="wl-ready" style="font-size:0.8rem; color:#047857;" *ngIf="w.tableReady">{{ 'Table {p0} is free' | translate: { p0: (w.tableReady) } }}</div></td>
              <td class="flex gap-2" style="flex-wrap:wrap;">
                <ng-container *ngIf="w.status === 'WAITING' || w.status === 'NOTIFIED'">
                  <button class="btn btn-sm btn-outline-secondary wl-notify" *ngIf="w.status === 'WAITING'" (click)="notify(w)">{{ 'Table ready' | translate }}</button>
                  <button class="btn btn-sm btn-primary wl-seat" (click)="openSeat(w)">{{ 'Seat' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger wl-leave" (click)="leave(w)">{{ 'Left' | translate }}</button>
                </ng-container>
                <a *ngIf="w.status === 'SEATED' && w.orderId" [routerLink]="['/admin/restaurant/orders', w.orderId]">{{ 'Open the order' | translate }}</a>
              </td>
            </tr>
            <tr *ngIf="seatingId === w.id">
              <td colspan="7">
                <div class="card" style="padding:0.75rem;">
                  <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                    <div class="form-group"><label class="form-label">{{ 'Seat {p0} at' | translate: { p0: (w.guestName) } }}</label>
                      <select class="form-select wl-table" [(ngModel)]="seatTableId" name="wlTable">
                        <option [ngValue]="null">{{ '— Choose a table —' | translate }}</option>
                        <option *ngFor="let t of freeTables(w)" [ngValue]="t.id">{{ '{p0} (seats {p1})' | translate: { p0: (t.name), p1: (t.capacity) } }}</option>
                      </select></div>
                    <button class="btn btn-primary wl-seat-go" [disabled]="!seatTableId" (click)="seat(w)">{{ 'Seat them' | translate }}</button>
                    <button class="btn btn-link" (click)="seatingId = null">{{ 'Close' | translate }}</button>
                  </div>
                  <div class="text-muted" *ngIf="freeTables(w).length === 0">{{ 'No free table fits this party right now.' | translate }}</div>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered ? ('Nobody matches these filters.' | translate) : ('Nobody is waiting.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class WaitlistComponent implements OnInit, OnDestroy {
  statuses = [{ value: 'ALL', label: 'Everyone' }, { value: 'SEATED', label: 'Seated' }, { value: 'LEFT', label: 'Left' }];
  list = new ListState<WaitlistEntry>((f, p, sz) => this.api.page(f, p, sz), 'Failed to load the waiting list');
  tables: DiningTable[] = [];
  error: string | null = null;
  busy = false;
  private poll: ReturnType<typeof setInterval> | null = null;

  guestName = '';
  guestPhone = '';
  partySize = 2;
  quoted: number | null = null;
  notes = '';

  seatingId: number | null = null;
  seatTableId: number | null = null;
  private freeCache = new Map<number, DiningTable[]>();

  constructor(private api: WaitlistApiService, private tableApi: DiningTableApiService, private router: Router) {}

  ngOnInit(): void {
    this.list.load();
    this.loadTables();
    this.poll = setInterval(() => {
      if (this.seatingId === null) {
        this.list.load();
        this.loadTables();
      }
    }, 15000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  private loadTables(): void {
    this.tableApi.listAll().subscribe({
      next: (t) => {
        this.tables = t.filter((x) => x.active && x.status === 'AVAILABLE');
        this.freeCache.clear();
      },
      error: () => {},
    });
  }

  pretty(s: string): string {
    return s.charAt(0) + s.slice(1).toLowerCase();
  }

  late(w: WaitlistEntry): boolean {
    return (w.status === 'WAITING' || w.status === 'NOTIFIED') && w.waitedMinutes > w.quotedMinutes;
  }

  /** Cached per entry so the select does not rebuild on every change-detection pass. */
  freeTables(w: WaitlistEntry): DiningTable[] {
    let v = this.freeCache.get(w.id);
    if (!v) {
      v = this.tables.filter((t) => t.capacity >= w.partySize).sort((a, b) => a.capacity - b.capacity || a.name.localeCompare(b.name));
      this.freeCache.set(w.id, v);
    }
    return v;
  }

  add(): void {
    if (!this.guestName.trim()) {
      this.error = T('Enter the guest name');
      return;
    }
    this.error = null;
    this.busy = true;
    this.api
      .add({ guestName: this.guestName.trim(), guestPhone: this.guestPhone.trim() || null, partySize: this.partySize, notes: this.notes.trim() || null, quotedMinutes: this.quoted === null || (this.quoted as unknown) === '' ? null : this.quoted })
      .subscribe({
        next: () => {
          this.busy = false;
          this.guestName = '';
          this.guestPhone = '';
          this.partySize = 2;
          this.quoted = null;
          this.notes = '';
          this.list.load();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? T('Could not add them to the list');
        },
      });
  }

  notify(w: WaitlistEntry): void {
    this.error = null;
    this.api.notify(w.id).subscribe({ next: () => this.list.load(), error: (err) => (this.error = err?.error?.message ?? T('Failed')) });
  }

  openSeat(w: WaitlistEntry): void {
    this.seatingId = this.seatingId === w.id ? null : w.id;
    this.seatTableId = null;
    this.freeCache.delete(w.id);
  }

  seat(w: WaitlistEntry): void {
    if (!this.seatTableId) return;
    this.error = null;
    this.api.seat(w.id, this.seatTableId).subscribe({
      next: (order) => this.router.navigate(['/admin/restaurant/orders', order.id]),
      error: (err) => (this.error = err?.error?.message ?? T('Could not seat them')),
    });
  }

  leave(w: WaitlistEntry): void {
    this.error = null;
    this.api.leave(w.id).subscribe({ next: () => this.list.load(), error: (err) => (this.error = err?.error?.message ?? T('Failed')) });
  }
}
