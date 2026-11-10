import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { HotelApiService, LinenBalance, LinenEntry, MinibarItem, Room } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/** Housekeeping > Minibar & linen: charge minibar use to the room's guest; count linen sent to and back from the laundry. */
@Component({
  selector: 'app-hk-supplies',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="message">{{ message }}</div>

    <h5>{{ 'Minibar' | translate }}</h5>
    <div class="card mb-3 minibar-use">
      <div class="card-body">
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <label class="form-label" style="margin:0;">{{ 'Room' | translate }}</label>
          <select class="form-control mb-room" style="width:auto;" name="mbroom" [(ngModel)]="mbRoomId"><option [ngValue]="null">{{ '— occupied room —' | translate }}</option><option *ngFor="let r of occupied" [ngValue]="r.id">{{ r.number }} · {{ r.occupantGuest }}</option></select>
        </div>
        <table class="table-sm mt-2" *ngIf="activeItems.length">
          <thead><tr><th>{{ 'Item' | translate }}</th><th class="text-end">{{ 'Price' | translate }}</th><th style="width:110px;">{{ 'Used' | translate }}</th></tr></thead>
          <tbody><tr *ngFor="let i of activeItems"><td>{{ i.name }}</td><td class="text-end">{{ i.price | number: '1.2-2' }}</td>
            <td><input class="form-control mb-qty" type="number" min="0" name="mq{{ i.id }}" [(ngModel)]="qty[i.id]" /></td></tr></tbody>
        </table>
        <p class="text-muted" *ngIf="!activeItems.length">{{ 'No minibar items set up yet.' | translate }}</p>
        <button class="btn btn-primary mb-post" type="button" [disabled]="busy || !mbRoomId || !hasQty" (click)="postMinibar()">{{ 'Charge to the room' | translate }}</button>
      </div>
    </div>

    <div *ngIf="canManage" class="mb-4">
      <h6>{{ 'Minibar items' | translate }}</h6>
      <table class="table-sm item-table">
        <thead><tr><th>{{ 'Item' | translate }}</th><th class="text-end">{{ 'Price' | translate }}</th><th>{{ 'Taxed' | translate }}</th><th>{{ 'Active' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let i of items" class="item-row"><td>{{ i.name }}</td><td class="text-end">{{ i.price | number: '1.2-2' }}</td><td>{{ i.taxable ? ('Yes' | translate) : ('No' | translate) }}</td><td>{{ i.active ? ('Yes' | translate) : ('No' | translate) }}</td>
            <td class="flex gap-1"><button class="btn btn-sm btn-outline-secondary edit-item" type="button" (click)="editItem(i)">{{ 'Edit' | translate }}</button><button class="btn btn-sm btn-outline-danger delete-item" type="button" (click)="removeItem(i)">{{ 'Delete' | translate }}</button></td></tr>
        </tbody>
      </table>
      <form class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;" (ngSubmit)="saveItem()">
        <input class="form-control item-name" style="width:180px;" name="iname" [(ngModel)]="itemForm.name" [placeholder]="'Item' | translate" required />
        <input class="form-control item-price" style="width:110px;" type="number" min="0" step="0.01" name="iprice" [(ngModel)]="itemForm.price" [placeholder]="'Price' | translate" required />
        <label><input type="checkbox" name="itax" [(ngModel)]="itemForm.taxable" /> {{ 'Taxed' | translate }}</label>
        <label><input type="checkbox" name="iact" [(ngModel)]="itemForm.active" /> {{ 'Active' | translate }}</label>
        <button class="btn btn-primary save-item" type="submit" [disabled]="busy">{{ itemId ? ('Update item' | translate) : ('Add item' | translate) }}</button>
        <button class="btn btn-link" type="button" *ngIf="itemId" (click)="resetItem()">{{ 'Cancel' | translate }}</button>
      </form>
    </div>

    <h5>{{ 'Linen' | translate }}</h5>
    <form class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;" (ngSubmit)="saveLinen()">
      <input class="form-control linen-item" style="width:160px;" name="litem" [(ngModel)]="linenForm.item" [placeholder]="'Item (bed sheet…)' | translate" required />
      <input class="form-control linen-sent" style="width:90px;" type="number" min="0" name="lsent" [(ngModel)]="linenForm.sent" [placeholder]="'Sent' | translate" />
      <input class="form-control linen-returned" style="width:90px;" type="number" min="0" name="lret" [(ngModel)]="linenForm.returned" [placeholder]="'Back' | translate" />
      <input class="form-control linen-damaged" style="width:90px;" type="number" min="0" name="ldam" [(ngModel)]="linenForm.damaged" [placeholder]="'Damaged' | translate" />
      <input class="form-control linen-notes" style="width:180px;" name="lnotes" [(ngModel)]="linenForm.notes" [placeholder]="'Notes' | translate" />
      <button class="btn btn-primary save-linen" type="submit" [disabled]="busy">{{ 'Record' | translate }}</button>
    </form>
    <table class="table-sm balance-table mb-3" *ngIf="balance.length">
      <thead><tr><th>{{ 'Item' | translate }}</th><th class="text-end">{{ 'Sent' | translate }}</th><th class="text-end">{{ 'Back' | translate }}</th><th class="text-end">{{ 'Damaged' | translate }}</th><th class="text-end">{{ 'At the laundry' | translate }}</th></tr></thead>
      <tbody><tr *ngFor="let b of balance" class="balance-row"><td>{{ b.item }}</td><td class="text-end">{{ b.sent }}</td><td class="text-end">{{ b.returned }}</td><td class="text-end">{{ b.damaged }}</td><td class="text-end"><strong [class.text-danger]="b.atLaundry < 0">{{ b.atLaundry }}</strong></td></tr></tbody>
    </table>
    <table class="table-sm linen-table" *ngIf="log.length">
      <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Item' | translate }}</th><th class="text-end">{{ 'Sent' | translate }}</th><th class="text-end">{{ 'Back' | translate }}</th><th class="text-end">{{ 'Damaged' | translate }}</th><th>{{ 'Notes' | translate }}</th><th></th></tr></thead>
      <tbody><tr *ngFor="let l of log" class="linen-row"><td>{{ l.logDate }}</td><td>{{ l.item }}</td><td class="text-end">{{ l.sent }}</td><td class="text-end">{{ l.returned }}</td><td class="text-end">{{ l.damaged }}</td><td>{{ l.notes }}</td>
        <td><button class="btn btn-sm btn-outline-danger delete-linen" type="button" *ngIf="canManage" (click)="removeLinen(l)">{{ 'Delete' | translate }}</button></td></tr></tbody>
    </table>
    <p class="text-muted" *ngIf="!log.length">{{ 'No linen movements recorded yet.' | translate }}</p>
  `,
})
export class HkSuppliesComponent implements OnInit {
  items: MinibarItem[] = [];
  rooms: Room[] = [];
  balance: LinenBalance[] = [];
  log: LinenEntry[] = [];
  qty: Record<number, number | null> = {};
  mbRoomId: number | null = null;
  itemId: number | null = null;
  itemForm = { name: '', price: 0, taxable: true, active: true };
  linenForm: { item: string; sent: number | null; returned: number | null; damaged: number | null; notes: string | null } = { item: '', sent: null, returned: null, damaged: null, notes: null };
  error: string | null = null;
  message: string | null = null;
  busy = false;

  constructor(private api: HotelApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  get activeItems(): MinibarItem[] {
    return this.items.filter((i) => i.active);
  }

  get occupied(): Room[] {
    return this.rooms.filter((r) => r.active && r.occupied);
  }

  get hasQty(): boolean {
    return Object.values(this.qty).some((q) => (q ?? 0) > 0);
  }

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.api.minibarItems(this.canManage).subscribe((i) => (this.items = i));
    this.api.rooms().subscribe((r) => (this.rooms = r));
    this.loadLinen();
  }

  loadLinen(): void {
    this.api.linenLog().subscribe((l) => (this.log = l));
    this.api.linenBalance().subscribe((b) => (this.balance = b));
  }

  private fail(e: unknown, fallback: string): void {
    this.busy = false;
    this.message = null;
    this.error = errMsg(e, fallback);
  }

  postMinibar(): void {
    if (!this.mbRoomId) return;
    const lines = Object.entries(this.qty).filter(([, q]) => (q ?? 0) > 0).map(([id, q]) => ({ itemId: Number(id), qty: Number(q) }));
    this.busy = true;
    this.error = this.message = null;
    this.api.useMinibar(this.mbRoomId, lines).subscribe({
      next: (r) => { this.busy = false; this.qty = {}; this.message = `Charged ${r.total.toFixed(2)} to room ${r.roomNumber} (${r.guestName}, ${r.bookingNumber}).`; },
      error: (e) => this.fail(e, 'Could not charge the minibar'),
    });
  }

  editItem(i: MinibarItem): void {
    this.itemId = i.id;
    this.itemForm = { name: i.name, price: i.price, taxable: i.taxable, active: i.active };
  }

  resetItem(): void {
    this.itemId = null;
    this.itemForm = { name: '', price: 0, taxable: true, active: true };
  }

  saveItem(): void {
    this.busy = true;
    this.error = this.message = null;
    this.api.saveMinibarItem(this.itemId, this.itemForm).subscribe({
      next: () => { this.busy = false; this.resetItem(); this.api.minibarItems(true).subscribe((i) => (this.items = i)); },
      error: (e) => this.fail(e, 'Could not save the item'),
    });
  }

  removeItem(i: MinibarItem): void {
    if (!confirm(`Delete ${i.name}?`)) return;
    this.api.deleteMinibarItem(i.id).subscribe({ next: () => this.api.minibarItems(true).subscribe((x) => (this.items = x)), error: (e) => this.fail(e, 'Could not delete the item') });
  }

  saveLinen(): void {
    this.busy = true;
    this.error = this.message = null;
    this.api.addLinen(this.linenForm).subscribe({
      next: () => { this.busy = false; this.linenForm = { item: '', sent: null, returned: null, damaged: null, notes: null }; this.loadLinen(); },
      error: (e) => this.fail(e, 'Could not record the linen'),
    });
  }

  removeLinen(l: LinenEntry): void {
    if (!confirm('Delete this linen entry?')) return;
    this.api.deleteLinen(l.id).subscribe({ next: () => this.loadLinen(), error: (e) => this.fail(e, 'Could not delete the entry') });
  }
}
