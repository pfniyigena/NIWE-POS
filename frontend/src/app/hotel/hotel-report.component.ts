import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, OccupancyReport } from '../core/api/hotel-api.service';
import { addDays, errMsg, iso, today } from './hotel-util';

/** Hotel > Reports: occupancy, average daily rate (ADR) and revenue per available room (RevPAR) for any date range. */
@Component({
  selector: 'app-hotel-report',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Hotel report</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
        <label class="form-label" style="margin:0;">From</label>
        <input class="form-control" style="width:auto;" type="date" name="from" [(ngModel)]="from" />
        <label class="form-label" style="margin:0;">To</label>
        <input class="form-control" style="width:auto;" type="date" name="to" [(ngModel)]="to" />
        <button class="btn btn-primary" (click)="load()">Show</button>
        <button class="btn btn-outline-secondary" (click)="preset(7)">Last 7 days</button>
        <button class="btn btn-outline-secondary" (click)="preset(30)">Last 30 days</button>
        <button class="btn btn-outline-secondary" (click)="thisMonth()">This month</button>
      </div>
      <ng-container *ngIf="r">
        <div class="stat-grid">
          <div class="stat-tile"><div class="stat-label">Occupancy</div><div class="stat-value">{{ r.occupancyPercent }}%</div></div>
          <div class="stat-tile"><div class="stat-label">Room nights sold</div><div class="stat-value">{{ r.roomNightsSold }}</div></div>
          <div class="stat-tile"><div class="stat-label">Room nights available</div><div class="stat-value">{{ r.roomNightsAvailable }}</div></div>
          <div class="stat-tile"><div class="stat-label">Room revenue</div><div class="stat-value">{{ r.roomRevenue | number: '1.2-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">ADR</div><div class="stat-value">{{ r.adr | number: '1.2-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">RevPAR</div><div class="stat-value">{{ r.revPar | number: '1.2-2' }}</div></div>
        </div>
        <table>
          <thead><tr><th>Room type</th><th class="text-end">Room nights</th><th class="text-end">Revenue (before tax)</th></tr></thead>
          <tbody>
            <tr *ngFor="let t of r.byType"><td>{{ t.roomTypeName }}</td><td class="text-end">{{ t.roomNights }}</td><td class="text-end">{{ t.revenue | number: '1.2-2' }}</td></tr>
            <tr *ngIf="r.byType.length === 0"><td colspan="3" class="text-muted">No room nights charged in this period.</td></tr>
          </tbody>
        </table>
        <p class="text-muted" style="font-size:0.8rem; margin-top:0.6rem;">
          Based on the room charges on guests' bills ({{ r.roomCount }} rooms in use). ADR = room revenue ÷ nights sold; RevPAR = room revenue ÷ nights available.
        </p>
      </ng-container>
    </app-shell>
  `,
})
export class HotelReportComponent implements OnInit {
  from = '';
  to = today();
  r: OccupancyReport | null = null;
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.thisMonth();
  }

  thisMonth(): void {
    const d = new Date();
    this.from = iso(new Date(d.getFullYear(), d.getMonth(), 1));
    this.to = today();
    this.load();
  }

  preset(days: number): void {
    this.to = today();
    this.from = addDays(this.to, -(days - 1));
    this.load();
  }

  load(): void {
    this.error = null;
    this.api.occupancy(this.from, this.to).subscribe({ next: (r) => (this.r = r), error: (e) => (this.error = errMsg(e, 'Failed to load the report')) });
  }
}
