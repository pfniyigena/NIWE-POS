import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Appointment, BookingApiService, Slot } from '../../core/api/booking-api.service';

/** One appointment: who, when, payment, and the actions that move it along. Serves /admin/booking/appointments/:id and /booking/appointments/:id (any login). */
@Component({
  selector: 'app-appointment-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Appointment <span *ngIf="a">{{ a.number }}</span></h3>
        <a class="btn btn-outline-secondary" [routerLink]="admin ? '/admin/booking' : '/booking/mine'">Back</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>

      <ng-container *ngIf="a">
        <div class="card mb-3" style="padding:1rem;">
          <div class="flex justify-between" style="flex-wrap:wrap; gap:1rem;">
            <div>
              <div style="font-size:1.15rem; font-weight:600;">{{ a.serviceName }}</div>
              <div>{{ a.startAt | date: 'EEE d MMM y, HH:mm' }} – {{ a.endAt | date: 'HH:mm' }} <span class="text-muted">({{ a.durationMinutes }} min)</span></div>
              <div class="text-muted">
                <span *ngIf="a.staffName">With {{ a.staffName }}</span><span *ngIf="a.staffName && a.resourceName"> · </span><span *ngIf="a.resourceName">{{ a.resourceName }}</span>
              </div>
            </div>
            <div style="text-align:right;">
              <span class="badge" [class.badge-success]="a.status === 'CONFIRMED' || a.status === 'COMPLETED'" [class.badge-warning]="a.status === 'PENDING_PAYMENT'"
                    [class.badge-danger]="a.status === 'NO_SHOW'" [class.badge-secondary]="a.status === 'CANCELLED'">{{ label(a.status) }}</span>
              <div class="text-muted" style="font-size:0.8rem;">Code {{ a.confirmationCode }} · {{ a.source === 'PUBLIC' ? 'Booked online' : 'Booked by staff' }}</div>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="a.holdExpiresAt && a.status === 'PENDING_PAYMENT'">Held until {{ a.holdExpiresAt | date: 'd MMM, HH:mm' }}</div>
            </div>
          </div>
          <hr />
          <div><strong>{{ a.guestName }}</strong>
            <span *ngIf="a.guestPhone"> · {{ a.guestPhone }}</span><span *ngIf="a.guestEmail"> · {{ a.guestEmail }}</span></div>
          <div *ngIf="a.cancelReason" class="text-muted">Cancelled: {{ a.cancelReason }}</div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="a.price > 0">
          <strong>Payment</strong>
          <div>Price {{ a.price | number: '1.0-2' }}<span *ngIf="a.paymentMode === 'DEPOSIT'"> · deposit to confirm {{ a.amountDue | number: '1.0-2' }}</span>
            <span *ngIf="a.paymentMode === 'FULL'"> · paid in full to confirm</span><span *ngIf="a.paymentMode === 'NONE'"> · pay at the visit</span></div>
          <div *ngIf="a.invoiceId">Invoice <a [routerLink]="['/admin/invoices', a.invoiceId]" *ngIf="admin">{{ a.invoiceNumber }}</a><span *ngIf="!admin">{{ a.invoiceNumber }}</span>
            · paid {{ a.amountPaid | number: '1.0-2' }} of {{ a.amountDue | number: '1.0-2' }}
            <a class="btn btn-sm btn-outline-primary" style="margin-left:0.5rem;" *ngIf="admin && a.canManage && a.status === 'PENDING_PAYMENT'"
               [routerLink]="['/admin/invoices', a.invoiceId, 'record-payment']">Record payment</a></div>
          <div *ngIf="!a.invoiceId && a.canManage && a.status !== 'CANCELLED'">
            <span class="text-muted">No invoice yet.</span>
            <button class="btn btn-sm btn-outline-primary" style="margin-left:0.5rem;" (click)="invoice()" [disabled]="busy">Create invoice</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <strong>Notes</strong>
          <textarea class="form-control mt-1" rows="3" [(ngModel)]="notes" name="notes" [readonly]="!a.canWork"></textarea>
          <button class="btn btn-sm btn-outline-secondary mt-2" *ngIf="a.canWork" (click)="saveNotes()" [disabled]="busy">Save notes</button>
        </div>

        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="a.status === 'PENDING_PAYMENT' || a.status === 'CONFIRMED'">
          <button class="btn btn-primary" *ngIf="a.canManage && a.status === 'PENDING_PAYMENT'" (click)="act('confirm')" [disabled]="busy">Confirm without payment</button>
          <button class="btn btn-primary" *ngIf="a.canWork && a.status === 'CONFIRMED'" (click)="act('complete')" [disabled]="busy">Mark done</button>
          <button class="btn btn-outline-secondary" *ngIf="a.canWork && a.status === 'CONFIRMED'" (click)="act('noShow')" [disabled]="busy">No-show</button>
          <button class="btn btn-outline-secondary" *ngIf="a.canManage" (click)="startReschedule()" [disabled]="busy">Move to another time</button>
          <button class="btn btn-outline-danger" *ngIf="a.canManage" (click)="cancelOpen = !cancelOpen">Cancel appointment</button>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="cancelOpen">
          <label class="form-label">Reason <span class="text-muted">(optional)</span></label>
          <input class="form-control" name="cr" [(ngModel)]="cancelReason" />
          <div class="mt-2"><button class="btn btn-danger" (click)="cancelNow()" [disabled]="busy">Yes, cancel it</button></div>
          <p class="text-muted mt-2" style="font-size:0.8rem;">An unpaid invoice for this booking is voided. Money already received stays on the invoice for you to refund or keep.</p>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="moving">
          <strong>Move to another time</strong>
          <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
            <app-date-picker name="rd" [(ngModel)]="rdate" (ngModelChange)="loadSlots()"></app-date-picker>
          </div>
          <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
            <button class="btn" *ngFor="let s of slots" [class.btn-primary]="rtime === s.time" [class.btn-outline-secondary]="rtime !== s.time" (click)="rtime = s.time">{{ s.time }}</button>
          </div>
          <p class="text-muted mt-2" *ngIf="slotsLoaded && !slots.length">Nothing free on that day.</p>
          <div class="mt-2"><button class="btn btn-primary" [disabled]="!rtime || busy" (click)="reschedule()">Move it</button></div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class AppointmentDetailComponent implements OnInit {
  a: Appointment | null = null;
  admin = false;
  notes = '';
  error: string | null = null;
  info: string | null = null;
  busy = false;
  cancelOpen = false;
  cancelReason = '';
  moving = false;
  rdate = '';
  rtime: string | null = null;
  slots: Slot[] = [];
  slotsLoaded = false;

  constructor(private api: BookingApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.admin = this.router.url.startsWith('/admin/');
    this.reload();
  }

  private reload(): void {
    this.api.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (a) => { this.a = a; this.notes = a.notes ?? ''; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the appointment'),
    });
  }

  label(s: string): string {
    return s === 'PENDING_PAYMENT' ? 'Waiting for payment' : s === 'NO_SHOW' ? 'No-show' : s.charAt(0) + s.slice(1).toLowerCase();
  }

  private run(call: any, message: string): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (a: Appointment) => { this.a = a; this.notes = a.notes ?? ''; this.busy = false; this.info = message; this.cancelOpen = false; this.moving = false; },
      error: (e: any) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; },
    });
  }

  act(kind: 'confirm' | 'complete' | 'noShow'): void {
    if (!this.a) return;
    const id = this.a.id;
    if (kind === 'confirm') this.run(this.api.confirm(id), 'Confirmed.');
    else if (kind === 'complete') this.run(this.api.complete(id), 'Marked as done.');
    else this.run(this.api.noShow(id), 'Marked as a no-show.');
  }

  cancelNow(): void { if (this.a) this.run(this.api.cancel(this.a.id, this.cancelReason.trim() || null), 'Cancelled.'); }
  invoice(): void { if (this.a) this.run(this.api.invoice(this.a.id), 'Invoice created.'); }
  saveNotes(): void { if (this.a) this.run(this.api.saveNotes(this.a.id, this.notes.trim() || null), 'Notes saved.'); }

  startReschedule(): void {
    this.moving = !this.moving;
    if (this.moving && this.a) {
      this.rdate = this.a.startAt.slice(0, 10);
      this.loadSlots();
    }
  }

  loadSlots(): void {
    if (!this.a || !this.rdate) return;
    this.rtime = null;
    this.slotsLoaded = false;
    this.api.slots(this.a.serviceId, this.rdate, null, this.a.id).subscribe({
      next: (l) => { this.slots = l; this.slotsLoaded = true; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not check availability'),
    });
  }

  reschedule(): void {
    if (!this.a || !this.rtime) return;
    this.run(this.api.reschedule(this.a.id, `${this.rdate}T${this.rtime}`, this.a.staffUserId, this.a.resourceId), 'Moved.');
  }
}
