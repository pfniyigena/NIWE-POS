import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { Folio, HotelApiService, PaymentLine, Room } from '../core/api/hotel-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../core/models';
import { addDays, errMsg, statusBadge, statusLabel, today } from './hotel-util';

type Panel = 'checkin' | 'edit' | 'cancel' | 'noshow' | 'move' | 'extend' | 'charge' | 'payment' | 'checkout' | null;

/** A booking and its bill (folio): arrival, in-house charges, payments, check-out. */
@Component({
  selector: 'app-booking-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <ng-container *ngIf="folio as f">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <h3>
            {{ f.booking.bookingNumber }}
            <span class="badge" [ngClass]="badge(f.booking.status)" style="font-size:0.75rem;">{{ label(f.booking.status) }}</span>
          </h3>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/hotel/bookings', f.booking.id, 'invoice']">Print invoice</a>
            <ng-container *ngIf="canManage">
              <button class="btn btn-success" *ngIf="f.booking.status === 'BOOKED' && f.booking.checkInDate <= todayStr" (click)="open('checkin')">Check in</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN'" (click)="open('edit')">Edit</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('move')">Move room</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('extend')">Change departure</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('charge')">+ Charge</button>
              <button class="btn btn-outline-primary" *ngIf="f.booking.status === 'BOOKED' || f.booking.status === 'CHECKED_IN'" (click)="open('payment')">{{ f.booking.status === 'BOOKED' ? '+ Deposit' : '+ Payment' }}</button>
              <button class="btn btn-primary" *ngIf="f.booking.status === 'CHECKED_IN'" (click)="open('checkout')">Check out</button>
              <button class="btn btn-outline-danger" *ngIf="f.booking.status === 'BOOKED' && f.booking.checkInDate <= todayStr" (click)="open('noshow')">No-show</button>
              <button class="btn btn-outline-danger" *ngIf="f.booking.status === 'BOOKED'" (click)="open('cancel')">Cancel booking</button>
            </ng-container>
          </div>
        </div>

        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div><div class="text-muted" style="font-size:0.75rem;">GUEST</div><strong>{{ f.booking.guestName }}</strong><div style="font-size:0.85rem;">{{ f.booking.guestPhone }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">ROOM</div><strong>{{ f.booking.roomNumber || 'Not assigned' }}</strong><div style="font-size:0.85rem;">{{ f.booking.roomTypeName }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">STAY</div><strong>{{ f.booking.checkInDate }} → {{ f.booking.checkOutDate }}</strong><div style="font-size:0.85rem;">{{ f.booking.nights }} night{{ f.booking.nights === 1 ? '' : 's' }}</div></div>
            <div><div class="text-muted" style="font-size:0.75rem;">GUESTS</div><strong>{{ f.booking.adults }} adult{{ f.booking.adults === 1 ? '' : 's' }}<span *ngIf="f.booking.children"> + {{ f.booking.children }} child{{ f.booking.children === 1 ? '' : 'ren' }}</span></strong></div>
            <div><div class="text-muted" style="font-size:0.75rem;">RATE / NIGHT</div><strong>{{ f.booking.nightlyTotal | number: '1.2-2' }}</strong><div style="font-size:0.85rem;">before tax</div></div>
          </div>
          <div class="text-muted" style="font-size:0.85rem; margin-top:0.5rem; white-space:pre-line;" *ngIf="f.booking.notes">{{ f.booking.notes }}</div>
          <div class="text-muted" style="font-size:0.85rem; margin-top:0.5rem;" *ngIf="f.booking.cancelReason">Reason: {{ f.booking.cancelReason }}</div>
        </div>

        <!-- Action panels -->
        <div class="card" style="padding:1rem; margin-bottom:1rem;" *ngIf="panel">
          <ng-container [ngSwitch]="panel">
            <form *ngSwitchCase="'checkin'" (ngSubmit)="doCheckIn()">
              <h4 style="font-size:1rem;" class="mb-2">Check in</h4>
              <div class="form-group"><label class="form-label">Room</label>
                <select class="form-control" style="max-width:340px;" name="room" [(ngModel)]="roomId" required>
                  <option *ngFor="let r of freeRooms" [ngValue]="r.id">{{ r.number }} · {{ r.roomTypeName }} · {{ r.housekeepingStatus | titlecase }}</option>
                </select></div>
              <label class="mb-2" style="display:block;"><input type="checkbox" name="anyway" [(ngModel)]="allowNotReady" /> Check in even if the room is not marked clean</label>
              <button class="btn btn-success" type="submit" [disabled]="busy">Check in</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'edit'" (ngSubmit)="doEdit()">
              <h4 style="font-size:1rem;" class="mb-2">Edit booking</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group" *ngIf="f.booking.status === 'BOOKED'"><label class="form-label">Arrival</label><input class="form-control" type="date" name="ci" [(ngModel)]="edit.checkInDate" /></div>
                <div class="form-group"><label class="form-label">Departure</label><input class="form-control" type="date" name="co" [(ngModel)]="edit.checkOutDate" /></div>
                <div class="form-group"><label class="form-label">Adults</label><input class="form-control" style="max-width:90px;" type="number" min="1" name="ad" [(ngModel)]="edit.adults" /></div>
                <div class="form-group"><label class="form-label">Children</label><input class="form-control" style="max-width:90px;" type="number" min="0" name="ch" [(ngModel)]="edit.children" /></div>
                <div class="form-group"><label class="form-label">Nightly rate</label><input class="form-control" style="max-width:140px;" type="number" min="0" step="0.01" name="rt" [(ngModel)]="edit.nightlyRate" /></div>
              </div>
              <div class="text-muted" style="font-size:0.8rem; margin-bottom:0.5rem;" *ngIf="f.booking.status === 'CHECKED_IN'">A new rate applies to nights not yet charged.</div>
              <div class="form-group"><label class="form-label">Notes</label><input class="form-control" name="nt" [(ngModel)]="edit.notes" /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">Save</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'move'" (ngSubmit)="doMove()">
              <h4 style="font-size:1rem;" class="mb-2">Move to another room</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">New room</label>
                  <select class="form-control" name="mr" [(ngModel)]="roomId" required>
                    <option *ngFor="let r of freeRooms" [ngValue]="r.id">{{ r.number }} · {{ r.roomTypeName }} · {{ r.housekeepingStatus | titlecase }}</option>
                  </select></div>
                <div class="form-group"><label class="form-label">Reason</label><input class="form-control" name="why" [(ngModel)]="reason" /></div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">Move guest</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'extend'" (ngSubmit)="doExtend()">
              <h4 style="font-size:1rem;" class="mb-2">Change departure date</h4>
              <div class="form-group"><input class="form-control" style="max-width:200px;" type="date" name="co2" [(ngModel)]="newDeparture" required /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">Save</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'charge'" (ngSubmit)="doCharge()">
              <h4 style="font-size:1rem;" class="mb-2">Add a charge</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">Description</label><input class="form-control" name="cd" [(ngModel)]="chargeDesc" placeholder="Laundry, minibar, late check-out..." required /></div>
                <div class="form-group"><label class="form-label">Amount (before tax; negative = discount)</label><input class="form-control" style="max-width:170px;" type="number" step="0.01" name="ca" [(ngModel)]="chargeAmount" required /></div>
              </div>
              <label class="mb-2" style="display:block;"><input type="checkbox" name="tx" [(ngModel)]="chargeTaxable" /> Add tax</label>
              <button class="btn btn-primary" type="submit" [disabled]="busy">Add charge</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'payment'" (ngSubmit)="doPayment()">
              <h4 style="font-size:1rem;" class="mb-2">{{ f.booking.status === 'BOOKED' ? 'Take a deposit' : 'Take a payment' }}</h4>
              <div class="flex gap-3" style="flex-wrap:wrap;">
                <div class="form-group"><label class="form-label">Amount</label><input class="form-control" style="max-width:160px;" type="number" min="0.01" step="0.01" name="pa" [(ngModel)]="payAmount" required /></div>
                <div class="form-group"><label class="form-label">Method</label>
                  <select class="form-control" name="pm" [(ngModel)]="payMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
                <div class="form-group"><label class="form-label">Reference</label><input class="form-control" name="pr" [(ngModel)]="payRef" /></div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">Record</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'checkout'" (ngSubmit)="doCheckout()">
              <h4 style="font-size:1rem;" class="mb-2">Check out &amp; settle the bill</h4>
              <p style="font-size:0.9rem;">
                Charges to date <strong>{{ f.chargesTotal | number: '1.2-2' }}</strong> · deposits and payments
                <strong>{{ f.deposits + f.paymentsTotal - f.refunds | number: '1.2-2' }}</strong> ·
                <ng-container *ngIf="f.balance > 0">still to pay <strong>{{ f.balance | number: '1.2-2' }}</strong></ng-container>
                <ng-container *ngIf="f.balance <= 0">nothing more to pay<span *ngIf="f.balance < 0"> — {{ -f.balance | number: '1.2-2' }} will be refunded</span></ng-container>.
                Any remaining nights up to today are added when you confirm.
              </p>
              <div class="flex gap-2 mb-2" *ngFor="let l of payLines; let i = index" style="align-items:flex-end; flex-wrap:wrap;">
                <div class="form-group" style="margin:0;"><label class="form-label" *ngIf="i === 0">Pay with</label>
                  <select class="form-control" [name]="'m' + i" [(ngModel)]="l.method"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
                <div class="form-group" style="margin:0;"><label class="form-label" *ngIf="i === 0">Amount</label>
                  <input class="form-control" style="max-width:150px;" type="number" min="0" step="0.01" [name]="'a' + i" [(ngModel)]="l.amount" /></div>
                <button class="btn btn-sm btn-outline-secondary" type="button" (click)="payLines.splice(i, 1)" *ngIf="payLines.length > 1">Remove</button>
              </div>
              <button class="btn btn-sm btn-outline-secondary mb-2" type="button" (click)="addPayLine()">+ Split payment</button>
              <div class="form-group" *ngIf="f.balance < 0"><label class="form-label">Refund by</label>
                <select class="form-control" style="max-width:220px;" name="rm" [(ngModel)]="refundMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">Check out</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>

            <form *ngSwitchCase="'cancel'" (ngSubmit)="doCancel(false)">
              <h4 style="font-size:1rem;" class="mb-2">Cancel booking</h4>
              <div class="form-group"><label class="form-label">Reason</label><input class="form-control" name="cr" [(ngModel)]="reason" /></div>
              <ng-container *ngTemplateOutlet="depositChoice"></ng-container>
              <button class="btn btn-danger" type="submit" [disabled]="busy">Cancel booking</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>
            <form *ngSwitchCase="'noshow'" (ngSubmit)="doCancel(true)">
              <h4 style="font-size:1rem;" class="mb-2">Mark as no-show</h4>
              <div class="form-group"><label class="form-label">Note</label><input class="form-control" name="nr" [(ngModel)]="reason" /></div>
              <ng-container *ngTemplateOutlet="depositChoice"></ng-container>
              <button class="btn btn-danger" type="submit" [disabled]="busy">Mark no-show</button>
              <button class="btn btn-link" type="button" (click)="panel = null">Close</button>
            </form>
          </ng-container>
        </div>

        <ng-template #depositChoice>
          <div *ngIf="folio && folio.deposits > 0" class="form-group">
            <label class="form-label">Deposit of {{ folio.deposits | number: '1.2-2' }}</label>
            <label class="flex gap-2"><input type="radio" name="da" value="REFUND" [(ngModel)]="depositAction" /> Give it back</label>
            <label class="flex gap-2"><input type="radio" name="da" value="FORFEIT" [(ngModel)]="depositAction" /> Keep it (becomes income)</label>
            <select class="form-control" style="max-width:220px; margin-top:0.4rem;" name="drm" [(ngModel)]="refundMethod" *ngIf="depositAction === 'REFUND'">
              <option *ngFor="let m of methods" [value]="m.code">Refund by {{ m.name }}</option>
            </select>
          </div>
        </ng-template>

        <!-- Folio -->
        <h4 style="font-size:1rem;" class="mb-2">Charges</h4>
        <table class="mb-3">
          <thead><tr><th>Description</th><th>Type</th><th class="text-end">Net</th><th class="text-end">Tax</th><th class="text-end">Amount</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of f.charges" [style.opacity]="c.voided ? 0.5 : 1">
              <td [style.text-decoration]="c.voided ? 'line-through' : 'none'">{{ c.description }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="c.voided">Voided{{ c.voidReason ? ': ' + c.voidReason : '' }}</div></td>
              <td>{{ typeLabel(c.chargeType) }}</td>
              <td class="text-end">{{ c.net | number: '1.2-2' }}</td>
              <td class="text-end">{{ c.tax | number: '1.2-2' }}</td>
              <td class="text-end">{{ c.amount | number: '1.2-2' }}</td>
              <td>
                <button class="btn btn-sm btn-outline-danger" *ngIf="canManage && !c.voided && (c.chargeType === 'ROOM' || c.chargeType === 'EXTRA') && (f.booking.status === 'CHECKED_IN' || f.booking.status === 'BOOKED')" (click)="voidCharge(c.id)">Void</button>
              </td>
            </tr>
            <tr *ngIf="f.charges.length === 0"><td colspan="6" class="text-muted">{{ f.booking.status === 'BOOKED' ? 'Room charges start at check-in. Quote for the stay: ' + (f.booking.chargesTotal | number: '1.2-2') : 'No charges.' }}</td></tr>
          </tbody>
          <tfoot *ngIf="f.charges.length">
            <tr><th colspan="2">Total</th><th class="text-end"></th><th class="text-end">{{ f.taxTotal | number: '1.2-2' }}</th><th class="text-end">{{ f.chargesTotal | number: '1.2-2' }}</th><th></th></tr>
          </tfoot>
        </table>

        <h4 style="font-size:1rem;" class="mb-2">Deposits &amp; payments</h4>
        <table class="mb-3">
          <thead><tr><th>When</th><th>Kind</th><th>Method</th><th>Reference</th><th class="text-end">Amount</th></tr></thead>
          <tbody>
            <tr *ngFor="let p of f.payments">
              <td>{{ p.createdAt | date: 'yyyy-MM-dd HH:mm' }}</td>
              <td>{{ p.kind | titlecase }}</td>
              <td>{{ p.method }}</td>
              <td>{{ p.reference || p.note }}</td>
              <td class="text-end">{{ p.kind === 'REFUND' ? '−' : '' }}{{ p.amount | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="f.payments.length === 0"><td colspan="5" class="text-muted">Nothing received yet.</td></tr>
          </tbody>
        </table>
        <p style="font-size:1.05rem;">Balance: <strong [style.color]="f.balance > 0 ? 'var(--danger-text)' : 'inherit'">{{ f.balance | number: '1.2-2' }}</strong>
          <span class="text-muted" style="font-size:0.85rem;" *ngIf="f.booking.status === 'BOOKED'"> (quote {{ f.booking.chargesTotal | number: '1.2-2' }} less deposits {{ f.deposits | number: '1.2-2' }} = {{ f.booking.balance | number: '1.2-2' }})</span></p>
      </ng-container>
    </app-shell>
  `,
})
export class BookingDetailComponent implements OnInit {
  folio: Folio | null = null;
  methods: PaymentMethodOption[] = [];
  panel: Panel = null;
  busy = false;
  error: string | null = null;
  message: string | null = null;
  todayStr = today();

  freeRooms: Room[] = [];
  roomId: number | null = null;
  allowNotReady = false;
  edit: any = {};
  reason = '';
  newDeparture = '';
  chargeDesc = '';
  chargeAmount: number | null = null;
  chargeTaxable = true;
  payAmount: number | null = null;
  payMethod = '';
  payRef = '';
  payLines: { method: string; amount: number | null }[] = [];
  refundMethod = '';
  depositAction: 'REFUND' | 'FORFEIT' = 'REFUND';

  badge = statusBadge;
  label = statusLabel;
  private id = 0;

  constructor(private api: HotelApiService, private pay: PaymentMethodApiService, private route: ActivatedRoute, private router: Router, public auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.pay.listActive().subscribe((m) => {
      this.methods = m.filter((x) => !x.credit && !x.roomCharge);
      const preferred = (this.methods.find((x) => x.cash) ?? this.methods[0])?.code ?? '';
      this.payMethod = this.refundMethod = preferred;
    });
    this.load();
  }

  load(): void {
    this.api.folio(this.id).subscribe({ next: (f) => (this.folio = f), error: (e) => (this.error = errMsg(e, 'Failed to load the booking')) });
  }

  typeLabel(t: string): string {
    return { ROOM: 'Room', RESTAURANT: 'Restaurant', POS: 'Shop', ROOM_SERVICE: 'Room service', EXTRA: 'Extra' }[t] ?? t;
  }

  open(panel: Panel): void {
    this.error = this.message = null;
    this.panel = panel;
    const b = this.folio!.booking;
    if (panel === 'checkin') {
      this.roomId = b.roomId;
      this.allowNotReady = false;
      this.loadRooms(null, today(), b.checkOutDate > today() ? b.checkOutDate : addDays(today(), 1), b.id, b.roomId);
    } else if (panel === 'move') {
      this.roomId = null;
      this.reason = '';
      const from = today();
      this.loadRooms(null, from, b.checkOutDate > from ? b.checkOutDate : addDays(from, 1), b.id, null);
    } else if (panel === 'edit') {
      this.edit = { checkInDate: b.checkInDate, checkOutDate: b.checkOutDate, adults: b.adults, children: b.children, nightlyRate: b.nightlyRate, notes: b.notes };
    } else if (panel === 'extend') {
      this.newDeparture = b.checkOutDate;
    } else if (panel === 'charge') {
      this.chargeDesc = '';
      this.chargeAmount = null;
      this.chargeTaxable = true;
    } else if (panel === 'payment') {
      this.payAmount = null;
      this.payRef = '';
    } else if (panel === 'checkout') {
      const due = this.folio!.balance;
      this.payLines = [{ method: this.payMethod, amount: due > 0 ? due : 0 }];
    } else if (panel === 'cancel' || panel === 'noshow') {
      this.reason = '';
      this.depositAction = 'REFUND';
    }
  }

  private loadRooms(typeId: number | null, from: string, to: string, exclude: number, keep: number | null): void {
    this.api.freeRooms(typeId, from, to, exclude).subscribe((r) => {
      this.freeRooms = r.filter((x) => x.id !== this.folio?.booking.roomId || keep === x.id);
      if (keep && !this.freeRooms.some((x) => x.id === keep)) this.roomId = null;
    });
  }

  addPayLine(): void {
    this.payLines.push({ method: this.payMethod, amount: null });
  }

  private run(call: { subscribe: (o: any) => any }, ok: string | null, close = true): void {
    this.error = this.message = null;
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        if (close) this.panel = null;
        if (ok) this.message = ok;
        this.load();
      },
      error: (e: any) => {
        this.busy = false;
        this.error = errMsg(e, 'That did not work');
      },
    });
  }

  doCheckIn(): void {
    this.run(this.api.checkIn(this.id, this.roomId, this.allowNotReady), 'Guest checked in.');
  }

  doEdit(): void {
    const b = this.folio!.booking;
    const e = this.edit;
    const payload: any = { checkOutDate: e.checkOutDate, adults: e.adults, children: e.children, nightlyRate: e.nightlyRate, notes: e.notes ?? '' };
    if (b.status === 'BOOKED') payload.checkInDate = e.checkInDate;
    this.run(this.api.updateBooking(this.id, payload), 'Booking updated.');
  }

  doMove(): void {
    if (!this.roomId) return void (this.error = 'Choose the new room.');
    this.run(this.api.moveRoom(this.id, this.roomId, this.reason || null), 'Guest moved.');
  }

  doExtend(): void {
    this.run(this.api.extend(this.id, this.newDeparture), 'Departure date changed.');
  }

  doCharge(): void {
    if (this.chargeAmount === null) return;
    this.run(this.api.addCharge(this.id, this.chargeDesc, this.chargeAmount, this.chargeTaxable), 'Charge added.');
  }

  doPayment(): void {
    if (!this.payAmount) return;
    const booked = this.folio!.booking.status === 'BOOKED';
    this.run(this.api.addPayment(this.id, booked ? 'DEPOSIT' : 'PAYMENT', this.payMethod, this.payAmount, this.payRef || null), booked ? 'Deposit recorded.' : 'Payment recorded.');
  }

  voidCharge(chargeId: number): void {
    const reason = prompt('Why is this charge being voided? (optional)');
    if (reason === null) return;
    this.run(this.api.voidCharge(this.id, chargeId, reason || null), 'Charge voided.', false);
  }

  doCheckout(): void {
    const payments: PaymentLine[] = this.payLines.filter((l) => l.amount && l.amount > 0).map((l) => ({ method: l.method, amount: l.amount! }));
    this.error = this.message = null;
    this.busy = true;
    this.api.checkOut(this.id, payments, this.refundMethod || null).subscribe({
      next: () => {
        this.busy = false;
        this.router.navigate(['/admin/hotel/bookings', this.id, 'invoice']);
      },
      error: (e) => {
        this.busy = false;
        this.error = errMsg(e, 'Could not check out');
      },
    });
  }

  doCancel(noShow: boolean): void {
    const hasDeposit = (this.folio?.deposits ?? 0) > 0;
    const action = hasDeposit ? this.depositAction : 'REFUND';
    const call = noShow
      ? this.api.noShow(this.id, this.reason || null, action, this.refundMethod || null)
      : this.api.cancel(this.id, this.reason || null, action, this.refundMethod || null);
    this.run(call, noShow ? 'Marked as no-show.' : 'Booking cancelled.');
  }
}
