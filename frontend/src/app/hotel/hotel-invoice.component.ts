import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Folio, HotelApiService } from '../core/api/hotel-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { StoreSettings } from '../core/models';

/** The guest's printable bill (folio) — also what is handed over at check-out. */
@Component({
  selector: 'app-hotel-invoice',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="invoice" *ngIf="folio as f">
      <div style="display:flex; justify-content:space-between; align-items:flex-start; gap:16px;">
        <div *ngIf="settings as s">
          <img *ngIf="s.logoDataUrl" [src]="s.logoDataUrl" alt="" style="max-width:140px; max-height:80px; margin-bottom:6px;" />
          <div style="font-weight:700; font-size:1.2rem;">{{ s.businessName }}</div>
          <div style="font-size:0.85rem; color:#444;">{{ s.address }}</div>
          <div style="font-size:0.85rem; color:#444;"><span *ngIf="s.phone">{{ s.phone }}</span><span *ngIf="s.phone && s.email"> · </span><span *ngIf="s.email">{{ s.email }}</span></div>
          <div style="font-size:0.85rem; color:#444;" *ngIf="s.taxId">Tax ID: {{ s.taxId }}</div>
        </div>
        <div style="text-align:right;">
          <h2 style="margin:0;">Guest invoice</h2>
          <div>{{ f.booking.bookingNumber }}</div>
          <div style="font-size:0.85rem; color:#444;">{{ f.booking.status === 'CHECKED_OUT' ? 'Settled' : 'Provisional — stay not finished' }}</div>
        </div>
      </div>
      <hr />
      <div style="display:flex; gap:32px; flex-wrap:wrap; font-size:0.9rem;">
        <div><div class="k">Guest</div>{{ f.booking.guestName }}<div *ngIf="f.booking.guestPhone">{{ f.booking.guestPhone }}</div></div>
        <div><div class="k">Room</div>{{ f.booking.roomNumber || '—' }} · {{ f.booking.roomTypeName }}</div>
        <div><div class="k">Stay</div>{{ f.booking.checkInDate }} → {{ f.booking.checkOutDate }} ({{ f.booking.nights }} night{{ f.booking.nights === 1 ? '' : 's' }})</div>
      </div>
      <table style="width:100%; margin-top:16px; border-collapse:collapse; font-size:0.9rem;">
        <thead><tr><th style="text-align:left;">Description</th><th class="r">Net</th><th class="r">Tax</th><th class="r">Amount</th></tr></thead>
        <tbody>
          <ng-container *ngFor="let c of f.charges">
            <tr *ngIf="!c.voided"><td>{{ c.description }}</td><td class="r">{{ c.net | number: '1.2-2' }}</td><td class="r">{{ c.tax | number: '1.2-2' }}</td><td class="r">{{ c.amount | number: '1.2-2' }}</td></tr>
          </ng-container>
        </tbody>
        <tfoot>
          <tr><td colspan="3" class="r"><strong>Total</strong></td><td class="r"><strong>{{ f.chargesTotal | number: '1.2-2' }}</strong></td></tr>
          <tr><td colspan="3" class="r">of which tax</td><td class="r">{{ f.taxTotal | number: '1.2-2' }}</td></tr>
        </tfoot>
      </table>
      <h4 style="margin:18px 0 6px;">Payments</h4>
      <table style="width:100%; border-collapse:collapse; font-size:0.9rem;">
        <tbody>
          <tr *ngFor="let p of f.payments"><td>{{ p.createdAt | date: 'yyyy-MM-dd HH:mm' }} · {{ p.kind | titlecase }} · {{ p.method }}</td><td class="r">{{ p.kind === 'REFUND' ? '−' : '' }}{{ p.amount | number: '1.2-2' }}</td></tr>
          <tr *ngIf="f.payments.length === 0"><td>No payments recorded.</td><td></td></tr>
        </tbody>
        <tfoot><tr><td class="r"><strong>Balance</strong></td><td class="r"><strong>{{ f.balance | number: '1.2-2' }}</strong></td></tr></tfoot>
      </table>
      <p style="text-align:center; margin-top:28px; color:#666;">Thank you for staying with us.</p>
      <div class="no-print" style="display:flex; gap:8px; margin-top:20px;">
        <button (click)="print()" style="flex:1; padding:10px; cursor:pointer;">Print</button>
        <a [routerLink]="['/admin/hotel/bookings', f.booking.id]" style="flex:1; text-align:center; padding:10px; border:1px solid #ccc; border-radius:4px; color:#111;">Back to booking</a>
      </div>
    </div>
    <p *ngIf="!folio && error" class="no-print" style="text-align:center; margin-top:40px; color:#b00;">{{ error }}</p>
  `,
  styles: [
    `
      :host { display: block; background: #fff; color: #111; min-height: 100vh; font-family: 'Segoe UI', system-ui, sans-serif; }
      .invoice { max-width: 760px; margin: 0 auto; padding: 28px 20px; }
      hr { border: none; border-top: 1px solid #ccc; margin: 14px 0; }
      th, td { padding: 5px 4px; border-bottom: 1px solid #eee; }
      .r { text-align: right; }
      .k { font-size: 0.7rem; text-transform: uppercase; color: #777; }
      @media print { .no-print { display: none !important; } }
    `,
  ],
})
export class HotelInvoiceComponent implements OnInit {
  folio: Folio | null = null;
  settings: StoreSettings | null = null;
  error: string | null = null;

  constructor(private route: ActivatedRoute, private api: HotelApiService, private storeApi: StoreSettingsApiService) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.storeApi.get().subscribe({ next: (s) => (this.settings = s), error: () => (this.settings = null) });
    this.api.folio(id).subscribe({ next: (f) => (this.folio = f), error: (e) => (this.error = e?.error?.message ?? 'Failed to load the invoice') });
  }

  print(): void {
    window.print();
  }
}
