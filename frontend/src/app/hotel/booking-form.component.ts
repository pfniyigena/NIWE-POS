import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { Guest, GuestPayload, HotelApiService, Room, RoomType } from '../core/api/hotel-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../core/models';
import { addDays, errMsg, nightsBetween, today } from './hotel-util';

/**
 * Hotel > Bookings > New booking, and (with data.walkIn) Hotel > Walk-in: a guest with no booking who is
 * given a room and checked in straight away.
 */
@Component({
  selector: 'app-booking-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ walkIn ? 'Walk-in guest' : 'New booking' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <h4 style="font-size:1rem;" class="mb-2">Guest</h4>
        <div class="flex gap-3 mb-2">
          <label><input type="radio" name="gmode" [value]="false" [(ngModel)]="newGuest" /> Existing guest</label>
          <label><input type="radio" name="gmode" [value]="true" [(ngModel)]="newGuest" /> New guest</label>
        </div>
        <div *ngIf="!newGuest" class="form-group">
          <input class="form-control" placeholder="Search by name, phone or ID number" name="gq" [(ngModel)]="guestQuery" (ngModelChange)="searchGuests()" />
          <select class="form-control" style="margin-top:0.4rem;" name="guestId" [(ngModel)]="guestId" size="4" *ngIf="guestOptions.length">
            <option *ngFor="let g of guestOptions" [ngValue]="g.id">{{ g.fullName }} {{ g.phone ? '· ' + g.phone : '' }}</option>
          </select>
        </div>
        <div *ngIf="newGuest" class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Full name</label><input class="form-control" name="gname" [(ngModel)]="guest.fullName" /></div>
          <div class="form-group"><label class="form-label">Phone</label><input class="form-control" name="gphone" [(ngModel)]="guest.phone" /></div>
          <div class="form-group"><label class="form-label">Email</label><input class="form-control" name="gemail" [(ngModel)]="guest.email" /></div>
          <div class="form-group"><label class="form-label">ID number</label><input class="form-control" name="gid" [(ngModel)]="guest.idNumber" /></div>
        </div>

        <h4 style="font-size:1rem;" class="mb-2 mt-3">Stay</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" *ngIf="!walkIn"><label class="form-label">Arrival</label>
            <input class="form-control" type="date" name="from" [min]="todayStr" [(ngModel)]="checkIn" (ngModelChange)="datesChanged()" required /></div>
          <div class="form-group"><label class="form-label">Departure</label>
            <input class="form-control" type="date" name="to" [min]="minDeparture" [(ngModel)]="checkOut" (ngModelChange)="datesChanged()" required /></div>
          <div class="form-group" *ngIf="!walkIn"><label class="form-label">Room type</label>
            <select class="form-control" name="type" [(ngModel)]="roomTypeId" (ngModelChange)="datesChanged()" required>
              <option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }} ({{ t.baseRate | number: '1.0-2' }})</option>
            </select></div>
          <div class="form-group"><label class="form-label">Adults</label>
            <input class="form-control" style="max-width:90px;" type="number" min="1" name="adults" [(ngModel)]="adults" /></div>
          <div class="form-group"><label class="form-label">Children</label>
            <input class="form-control" style="max-width:90px;" type="number" min="0" name="children" [(ngModel)]="children" /></div>
        </div>
        <div class="form-group">
          <label class="form-label">Room {{ walkIn ? '' : '(optional — can be chosen at check-in)' }}</label>
          <select class="form-control" style="max-width:360px;" name="room" [(ngModel)]="roomId" (ngModelChange)="roomPicked()">
            <option [ngValue]="null" *ngIf="!walkIn">Assign later</option>
            <option *ngFor="let r of freeRooms" [ngValue]="r.id">{{ r.number }} · {{ r.roomTypeName }} · {{ r.rate | number: '1.0-2' }}{{ r.housekeepingStatus === 'DIRTY' ? ' (needs cleaning)' : '' }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="freeRooms.length === 0 && checkOut">No room is free for those dates.</div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Nightly rate (before tax)</label>
            <input class="form-control" style="max-width:160px;" type="number" min="0" step="0.01" name="rate" [(ngModel)]="rate" [placeholder]="defaultRate() + ''" /></div>
          <div class="form-group" *ngIf="!walkIn"><label class="form-label">Source</label>
            <input class="form-control" name="source" [(ngModel)]="source" placeholder="Phone, website, agent..." /></div>
        </div>
        <p class="text-muted" style="font-size:0.85rem;" *ngIf="nights() > 0">
          {{ nights() }} night{{ nights() === 1 ? '' : 's' }} × {{ nightly() | number: '1.2-2' }} = <strong>{{ nights() * nightly() | number: '1.2-2' }}</strong> before tax
        </p>
        <div class="form-group"><label class="form-label">Notes (optional)</label><input class="form-control" name="notes" [(ngModel)]="notes" /></div>

        <h4 style="font-size:1rem;" class="mb-2 mt-3">Deposit (optional)</h4>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Amount</label>
            <input class="form-control" style="max-width:160px;" type="number" min="0" step="0.01" name="dep" [(ngModel)]="deposit" /></div>
          <div class="form-group" *ngIf="deposit && deposit > 0"><label class="form-label">Paid by</label>
            <select class="form-control" name="depm" [(ngModel)]="depositMethod">
              <option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option>
            </select></div>
        </div>

        <div class="flex gap-2 mt-3">
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ walkIn ? 'Check in guest' : 'Create booking' }}</button>
          <a class="btn btn-link" routerLink="/admin/hotel/bookings">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class BookingFormComponent implements OnInit {
  walkIn = false;
  types: RoomType[] = [];
  methods: PaymentMethodOption[] = [];
  freeRooms: Room[] = [];
  guestOptions: Guest[] = [];

  newGuest = false;
  guestQuery = '';
  guestId: number | null = null;
  guest: GuestPayload = { fullName: '', phone: null, email: null, nationality: null, idType: null, idNumber: null, address: null, notes: null };

  todayStr = today();
  checkIn = today();
  checkOut = addDays(today(), 1);
  roomTypeId: number | null = null;
  roomId: number | null = null;
  adults = 1;
  children = 0;
  rate: number | null = null;
  source = '';
  notes = '';
  deposit: number | null = null;
  depositMethod = '';
  error: string | null = null;
  saving = false;
  private timer: any;

  constructor(private api: HotelApiService, private pay: PaymentMethodApiService, private route: ActivatedRoute, private router: Router) {}

  get minDeparture(): string {
    return addDays(this.checkIn, 1);
  }

  ngOnInit(): void {
    this.walkIn = !!this.route.snapshot.data['walkIn'];
    this.api.roomTypes().subscribe((t) => {
      this.types = t.filter((x) => x.active);
      this.roomTypeId = this.types[0]?.id ?? null;
      this.datesChanged();
    });
    this.pay.listActive().subscribe((m) => {
      this.methods = m.filter((x) => !x.credit && !x.roomCharge);
      this.depositMethod = (this.methods.find((x) => x.cash) ?? this.methods[0])?.code ?? '';
    });
    this.searchGuests();
  }

  searchGuests(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.api.guests(this.guestQuery.trim() || undefined).subscribe((g) => (this.guestOptions = g)), 200);
  }

  datesChanged(): void {
    if (this.walkIn) this.checkIn = today();
    if (this.checkOut <= this.checkIn) this.checkOut = addDays(this.checkIn, 1);
    this.api.freeRooms(this.walkIn ? null : this.roomTypeId, this.checkIn, this.checkOut).subscribe({
      next: (r) => {
        this.freeRooms = r;
        if (this.roomId && !r.some((x) => x.id === this.roomId)) this.roomId = null;
        if (this.walkIn && !this.roomId && r.length) this.roomId = r[0].id;
      },
      error: () => (this.freeRooms = []),
    });
  }

  roomPicked(): void {
    // nothing else to do: the default rate follows the chosen room
  }

  private chosenType(): RoomType | undefined {
    if (this.walkIn) {
      const room = this.freeRooms.find((r) => r.id === this.roomId);
      return this.types.find((t) => t.id === room?.roomTypeId);
    }
    return this.types.find((t) => t.id === this.roomTypeId);
  }

  defaultRate(): number {
    const room = this.freeRooms.find((r) => r.id === this.roomId);
    return room ? room.rate : this.chosenType()?.baseRate ?? 0;
  }

  nights(): number {
    return Math.max(0, nightsBetween(this.checkIn, this.checkOut));
  }

  nightly(): number {
    const t = this.chosenType();
    const extra = t ? Math.max(0, this.adults + this.children - t.baseOccupancy) * t.extraPersonRate : 0;
    return (this.rate ?? this.defaultRate()) + extra;
  }

  submit(): void {
    this.error = null;
    if (!this.newGuest && !this.guestId) return void (this.error = 'Choose a guest, or switch to "New guest".');
    if (this.newGuest && !this.guest.fullName.trim()) return void (this.error = "Enter the guest's name.");
    if (this.walkIn && !this.roomId) return void (this.error = 'Choose a room for the guest.');
    const guestId = this.newGuest ? null : this.guestId;
    const guest = this.newGuest ? this.guest : null;
    const depositAmount = this.deposit && this.deposit > 0 ? this.deposit : null;
    const depositMethod = depositAmount ? this.depositMethod : null;
    this.saving = true;
    const done = {
      next: (b: { id: number }) => this.router.navigate(['/admin/hotel/bookings', b.id]),
      error: (e: any) => {
        this.saving = false;
        this.error = errMsg(e, 'Could not save the booking');
      },
    };
    if (this.walkIn) {
      this.api
        .walkIn({ guestId, guest, roomId: this.roomId!, checkOutDate: this.checkOut, adults: this.adults, children: this.children, nightlyRate: this.rate, notes: this.notes || null, depositAmount, depositMethod })
        .subscribe(done);
    } else {
      this.api
        .createBooking({ guestId, guest, roomTypeId: this.roomTypeId!, roomId: this.roomId, checkInDate: this.checkIn, checkOutDate: this.checkOut, adults: this.adults, children: this.children, nightlyRate: this.rate, source: this.source || null, notes: this.notes || null, depositAmount, depositMethod })
        .subscribe(done);
    }
  }
}
