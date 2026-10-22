import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ForecastReport, HotelApiService } from '../core/api/hotel-api.service';
import { HotelExportComponent } from './hotel-export.component';
import { errMsg, today } from './hotel-util';

/** Hotel > Reports > Forecast: what is booked for the coming days, and how much of it was booked recently (pick-up). */
@Component({
  selector: 'app-rpt-forecast',
  standalone: true,
  imports: [CommonModule, FormsModule, HotelExportComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
      <label class="form-label" style="margin:0;">From</label>
      <input class="form-control fc-from" style="width:auto;" type="date" name="from" [(ngModel)]="from" />
      <label class="form-label" style="margin:0;">Days</label>
      <select class="form-control fc-days" style="width:auto;" name="days" [(ngModel)]="days">
        <option [ngValue]="7">7</option><option [ngValue]="14">14</option><option [ngValue]="30">30</option><option [ngValue]="60">60</option><option [ngValue]="90">90</option>
      </select>
      <label class="form-label" style="margin:0;">Pick-up window</label>
      <select class="form-control fc-pickup" style="width:auto;" name="pickupDays" [(ngModel)]="pickupDays">
        <option [ngValue]="1">last day</option><option [ngValue]="7">last 7 days</option><option [ngValue]="14">last 14 days</option><option [ngValue]="30">last 30 days</option>
      </select>
      <button class="btn btn-primary fc-show" (click)="load()">Show</button>
      <app-hotel-export *ngIf="r" style="margin-left:auto;" path="/hotel/reports/forecast/export" name="forecast" [filters]="exportFilters"></app-hotel-export>
    </div>
    <ng-container *ngIf="r">
      <div class="stat-grid">
        <div class="stat-tile"><div class="stat-label">Occupancy on the books</div><div class="stat-value fc-occ">{{ r.totals.occupancyPercent }}%</div><div class="text-muted" style="font-size:0.8rem;">{{ r.totals.roomNightsBooked }} of {{ r.totals.capacityNights }} nights</div></div>
        <div class="stat-tile"><div class="stat-label">Revenue on the books</div><div class="stat-value fc-rev">{{ r.totals.revenue | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">ADR</div><div class="stat-value">{{ r.totals.adr | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">Picked up in the last {{ r.pickupDays }} day{{ r.pickupDays === 1 ? '' : 's' }}</div><div class="stat-value fc-pickup-total">{{ r.totals.pickup }} nights</div><div class="text-muted" style="font-size:0.8rem;">{{ r.totals.pickupRevenue | number: '1.2-2' }}</div></div>
      </div>
      <div style="overflow-x:auto;">
        <table class="fc-table">
          <thead><tr><th>Date</th><th>Day</th><th class="text-end">Rooms</th><th class="text-end">Booked</th><th class="text-end">Held</th><th style="min-width:140px;">Occupancy</th><th class="text-end">Revenue</th><th class="text-end">In</th><th class="text-end">Out</th><th class="text-end">Pick-up</th><th class="text-end">Pick-up revenue</th></tr></thead>
          <tbody>
            <tr *ngFor="let d of r.days" class="fc-row">
              <td>{{ d.date }}</td><td>{{ d.weekday }}</td><td class="text-end">{{ d.capacity }}</td><td class="text-end">{{ d.booked }}</td><td class="text-end">{{ d.held }}</td>
              <td><div style="display:flex; align-items:center; gap:0.4rem;"><div style="flex:1; background:#e5e7eb; border-radius:3px; height:8px;"><div [style.width.%]="d.occupancyPercent" style="background:#2563eb; height:8px; border-radius:3px;"></div></div><span style="font-size:0.8rem; min-width:3rem; text-align:right;">{{ d.occupancyPercent }}%</span></div></td>
              <td class="text-end">{{ d.revenue | number: '1.2-2' }}</td><td class="text-end">{{ d.arrivals }}</td><td class="text-end">{{ d.departures }}</td>
              <td class="text-end">{{ d.pickup }}</td><td class="text-end">{{ d.pickupRevenue | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-muted" style="font-size:0.8rem; margin-top:0.6rem;">
        Booked = rooms with a confirmed booking or a guest in the house that night; Held = rooms kept back for groups. Revenue uses each night's rate (seasons, rate plans and extra guests included), before tax.
        Pick-up = nights on bookings made within the pick-up window.
      </p>
    </ng-container>
  `,
})
export class RptForecastComponent implements OnInit {
  from = today();
  days = 30;
  pickupDays = 7;
  r: ForecastReport | null = null;
  exportFilters: Record<string, string | number> = {};
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.error = null;
    this.api.forecastReport(this.from, this.days, this.pickupDays).subscribe({
      next: (r) => {
        this.r = r;
        this.exportFilters = { from: r.from, days: this.days, pickupDays: this.pickupDays };
      },
      error: (e) => (this.error = errMsg(e, 'Failed to load the forecast')),
    });
  }
}
