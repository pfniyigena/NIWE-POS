import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { HotelApiService, InHouseGuest } from '../core/api/hotel-api.service';

/**
 * Admin/staff > Restaurant > New order (Fourier: "Delivery & takeaway") — the off-premise
 * counterpart to tapping a table on the floor plan: there's no table to tap for a walk-in
 * takeaway order or a phoned-in delivery, so this screen collects a customer name (and, for
 * delivery, an address) and opens the order directly, landing on the same order-taking screen
 * (TableOrderComponent) the floor plan uses — same menu, same "send to kitchen," same checkout.
 */
@Component({
  selector: 'app-order-intake',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>New order</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/restaurant/tables/floor-plan">← Floor plan</a>
      </div>

      <div class="card" style="padding:1rem; max-width:480px;">
        <div class="flex gap-2 mb-3">
          <button class="btn" [class.btn-primary]="type === 'TAKEAWAY'" [class.btn-outline-secondary]="type !== 'TAKEAWAY'" (click)="type = 'TAKEAWAY'">
            🥡 Takeaway
          </button>
          <button class="btn" [class.btn-primary]="type === 'DELIVERY'" [class.btn-outline-secondary]="type !== 'DELIVERY'" (click)="type = 'DELIVERY'">
            🛵 Delivery
          </button>
          <button class="btn" *ngIf="hotelApi.enabled()" [class.btn-primary]="type === 'ROOM_SERVICE'" [class.btn-outline-secondary]="type !== 'ROOM_SERVICE'" (click)="chooseRoomService()">
            🏨 Room service
          </button>
        </div>

        <ng-container *ngIf="type === 'ROOM_SERVICE'">
          <label class="form-label">Guest's room</label>
          <select class="form-control mb-2" [(ngModel)]="bookingId" name="bookingId">
            <option [ngValue]="null">Choose the guest…</option>
            <option *ngFor="let g of inHouse" [ngValue]="g.bookingId">Room {{ g.roomNumber }} — {{ g.guestName }}</option>
          </select>
          <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="inHouse.length === 0">No guest is checked in right now.</div>
        </ng-container>

        <ng-container *ngIf="type !== 'ROOM_SERVICE'">
        <label class="form-label">Customer name</label>
        <input class="form-control mb-2" [(ngModel)]="customerName" name="customerName" placeholder="Who's this for?" />

        <label class="form-label">Phone</label>
        <input class="form-control mb-2" [(ngModel)]="customerPhone" name="customerPhone" placeholder="Optional" />
        </ng-container>

        <ng-container *ngIf="type === 'DELIVERY'">
          <label class="form-label">Delivery address</label>
          <textarea class="form-control mb-2" [(ngModel)]="customerAddress" name="customerAddress" rows="2" placeholder="Required for delivery"></textarea>
        </ng-container>

        <label class="form-label">Notes</label>
        <input class="form-control mb-3" [(ngModel)]="notes" name="notes" placeholder="Optional" />

        <button class="btn btn-primary" (click)="openOrder()" [disabled]="submitting">Open order</button>
      </div>
    </app-shell>
  `,
})
export class OrderIntakeComponent {
  type: 'TAKEAWAY' | 'DELIVERY' | 'ROOM_SERVICE' = 'TAKEAWAY';
  /** Hotel module: in-house guests for room service. */
  inHouse: InHouseGuest[] = [];
  bookingId: number | null = null;
  customerName = '';
  customerPhone = '';
  customerAddress = '';
  notes = '';
  error: string | null = null;
  submitting = false;

  constructor(
    private orderApi: RestaurantOrderApiService,
    private router: Router,
    public hotelApi: HotelApiService
  ) {
    this.hotelApi.ensureStateLoaded();
  }

  chooseRoomService(): void {
    this.type = 'ROOM_SERVICE';
    this.hotelApi.inHouseGuests().subscribe({ next: (g) => (this.inHouse = g), error: () => (this.inHouse = []) });
  }

  openOrder(): void {
    if (this.type === 'ROOM_SERVICE') {
      if (!this.bookingId) {
        this.error = "Choose the guest's room";
        return;
      }
      this.error = null;
      this.submitting = true;
      this.orderApi.openRoomServiceOrder({ bookingId: this.bookingId, notes: this.notes.trim() || null }).subscribe({
        next: (order) => this.router.navigate(['/admin/restaurant/orders', order.id]),
        error: (err) => {
          this.submitting = false;
          this.error = err?.error?.message ?? 'Failed to open order';
        },
      });
      return;
    }
    if (!this.customerName.trim()) {
      this.error = 'Customer name is required';
      return;
    }
    if (this.type === 'DELIVERY' && !this.customerAddress.trim()) {
      this.error = 'A delivery address is required';
      return;
    }
    this.error = null;
    this.submitting = true;
    const payload = {
      customerName: this.customerName.trim(),
      customerPhone: this.customerPhone.trim() || null,
      customerAddress: this.type === 'DELIVERY' ? this.customerAddress.trim() : null,
      guestCount: 1,
      notes: this.notes.trim() || null,
    };
    const request$ = this.type === 'DELIVERY' ? this.orderApi.openDeliveryOrder(payload) : this.orderApi.openTakeawayOrder(payload);
    request$.subscribe({
      next: (order) => this.router.navigate(['/admin/restaurant/orders', order.id]),
      error: (err) => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Failed to open order';
      },
    });
  }
}
