import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DailyReport, HotelApiService } from '../core/api/hotel-api.service';
import { HotelExportComponent } from './hotel-export.component';
import { addDays, errMsg, today } from './hotel-util';

/** Hotel > Reports > Day report: how the hotel did on one day, with the month and year so far. */
@Component({
  selector: 'app-rpt-daily',
  standalone: true,
  imports: [CommonModule, FormsModule, HotelExportComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
      <label class="form-label" style="margin:0;">Day</label>
      <input class="form-control rpt-date" style="width:auto;" type="date" name="date" [(ngModel)]="date" [max]="max" />
      <button class="btn btn-primary rpt-show" (click)="load()">Show</button>
      <button class="btn btn-outline-secondary" (click)="go(0)">Today</button>
      <button class="btn btn-outline-secondary" (click)="go(-1)">Yesterday</button>
      <app-hotel-export *ngIf="r" style="margin-left:auto;" path="/hotel/reports/daily/export" name="day-report" [filters]="exportFilters"></app-hotel-export>
    </div>
    <ng-container *ngIf="r">
      <div class="stat-grid">
        <div class="stat-tile"><div class="stat-label">Occupancy</div><div class="stat-value daily-occ">{{ r.occupancyPercent }}%</div><div class="text-muted" style="font-size:0.8rem;">{{ r.roomsOccupied }} of {{ r.sellable }} rooms</div></div>
        <div class="stat-tile"><div class="stat-label">Room revenue</div><div class="stat-value daily-rev">{{ r.roomRevenue | number: '1.2-2' }}</div><div class="text-muted" style="font-size:0.8rem;" *ngIf="r.expectedRoomRevenue !== null">expected {{ r.expectedRoomRevenue | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">ADR</div><div class="stat-value">{{ r.adr | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">RevPAR</div><div class="stat-value">{{ r.revPar | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">Arrivals / departures</div><div class="stat-value daily-moves">{{ r.arrivals }} / {{ r.departures }}</div><div class="text-muted" style="font-size:0.8rem;">{{ r.stayOvers }} stay-overs</div></div>
        <div class="stat-tile"><div class="stat-label">Tomorrow</div><div class="stat-value">{{ r.arrivalsTomorrow }} in / {{ r.departuresTomorrow }} out</div></div>
        <div class="stat-tile"><div class="stat-label">In house now</div><div class="stat-value daily-inhouse">{{ r.inHouseNow }}</div><div class="text-muted" style="font-size:0.8rem;">balance {{ r.inHouseBalance | number: '1.2-2' }}</div></div>
        <div class="stat-tile" *ngIf="r.outOfOrder > 0"><div class="stat-label">Out of order</div><div class="stat-value">{{ r.outOfOrder }}</div></div>
      </div>

      <div class="grid-2" style="display:grid; grid-template-columns:repeat(auto-fit,minmax(320px,1fr)); gap:1rem;">
        <div>
          <h5>Revenue earned</h5>
          <table class="daily-revenue">
            <thead><tr><th>Type</th><th class="text-end">Lines</th><th class="text-end">Before tax</th><th class="text-end">Tax</th><th class="text-end">Total</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of r.revenue"><td>{{ l.type }}</td><td class="text-end">{{ l.lines }}</td><td class="text-end">{{ l.net | number: '1.2-2' }}</td><td class="text-end">{{ l.tax | number: '1.2-2' }}</td><td class="text-end">{{ l.gross | number: '1.2-2' }}</td></tr>
              <tr *ngIf="r.revenue.length === 0"><td colspan="5" class="text-muted">Nothing charged on this day.</td></tr>
              <tr *ngIf="r.revenue.length > 0"><td colspan="2"><strong>Total</strong></td><td class="text-end"><strong>{{ r.revenueTotal | number: '1.2-2' }}</strong></td><td colspan="2"></td></tr>
            </tbody>
          </table>
        </div>
        <div>
          <h5>Money taken</h5>
          <table class="daily-money">
            <thead><tr><th>Method</th><th class="text-end">Deposits</th><th class="text-end">Payments</th><th class="text-end">Refunds</th><th class="text-end">Net</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of r.money"><td>{{ m.method }}</td><td class="text-end">{{ m.deposits | number: '1.2-2' }}</td><td class="text-end">{{ m.payments | number: '1.2-2' }}</td><td class="text-end">{{ m.refunds | number: '1.2-2' }}</td><td class="text-end">{{ m.net | number: '1.2-2' }}</td></tr>
              <tr *ngIf="r.money.length === 0"><td colspan="5" class="text-muted">No money taken on this day.</td></tr>
              <tr *ngIf="r.money.length > 0"><td colspan="4"><strong>Total</strong></td><td class="text-end"><strong>{{ r.moneyTotal | number: '1.2-2' }}</strong></td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <h5 class="mt-3">So far this month and year</h5>
      <table class="daily-periods">
        <thead><tr><th>Period</th><th class="text-end">Nights sold</th><th class="text-end">Nights available</th><th class="text-end">Occupancy</th><th class="text-end">Room revenue</th><th class="text-end">ADR</th><th class="text-end">RevPAR</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of periods">
            <td>{{ p.label }} <span class="text-muted" style="font-size:0.8rem;">({{ p.from }} to {{ p.to }})</span></td>
            <td class="text-end">{{ p.roomNightsSold }}</td><td class="text-end">{{ p.roomNightsAvailable }}</td><td class="text-end">{{ p.occupancyPercent }}%</td>
            <td class="text-end">{{ p.roomRevenue | number: '1.2-2' }}</td><td class="text-end">{{ p.adr | number: '1.2-2' }}</td><td class="text-end">{{ p.revPar | number: '1.2-2' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="font-size:0.8rem; margin-top:0.6rem;">
        Revenue is what was charged to guests that day, before tax. Occupancy counts the rooms holding a guest that night, out of the rooms that could be sold.
      </p>
    </ng-container>
  `,
})
export class RptDailyComponent implements OnInit {
  max = addDays(today(), 1);
  date = today();
  r: DailyReport | null = null;
  periods: DailyReport['monthToDate'][] = [];
  exportFilters: Record<string, string> = {};
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.load();
  }

  go(offset: number): void {
    this.date = addDays(today(), offset);
    this.load();
  }

  load(): void {
    this.error = null;
    this.api.dailyReport(this.date).subscribe({
      next: (r) => {
        this.r = r;
        this.periods = [r.monthToDate, r.yearToDate];
        this.exportFilters = { date: r.date };
      },
      error: (e) => (this.error = errMsg(e, 'Failed to load the day report')),
    });
  }
}
