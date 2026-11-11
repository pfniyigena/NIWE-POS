import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, OccupancyReport } from '../core/api/hotel-api.service';
import { HotelExportComponent } from './hotel-export.component';
import { addDays, errMsg, iso, today } from './hotel-util';
import { RptDailyComponent } from './rpt-daily.component';
import { RptSourcesComponent } from './rpt-sources.component';
import { RptForecastComponent } from './rpt-forecast.component';
import { RptAuditComponent } from './rpt-audit.component';

import { T } from '../core/i18n/t';
/** Hotel > Reports: occupancy, average daily rate (ADR) and revenue per available room (RevPAR) for any date range. */
@Component({
  selector: 'app-hotel-report',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, HotelExportComponent, RptDailyComponent, RptSourcesComponent, RptForecastComponent, RptAuditComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Hotel reports' | translate }}</h3>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <button *ngFor="let t of tabs" class="btn rpt-tab" [attr.data-tab]="t.key" [ngClass]="tab === t.key ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = t.key">{{ t.label }}</button>
      </div>
      <app-rpt-daily *ngIf="tab === 'daily'"></app-rpt-daily>
      <app-rpt-sources *ngIf="tab === 'sources'"></app-rpt-sources>
      <app-rpt-forecast *ngIf="tab === 'forecast'"></app-rpt-forecast>
      <app-rpt-audit *ngIf="tab === 'audit'"></app-rpt-audit>
      <ng-container *ngIf="tab === 'occupancy'">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
        <label class="form-label" style="margin:0;">{{ 'From' | translate }}</label>
        <input class="form-control" style="width:auto;" type="date" name="from" [(ngModel)]="from" />
        <label class="form-label" style="margin:0;">{{ 'To' | translate }}</label>
        <input class="form-control" style="width:auto;" type="date" name="to" [(ngModel)]="to" />
        <button class="btn btn-primary" (click)="load()">{{ 'Show' | translate }}</button>
        <button class="btn btn-outline-secondary" (click)="preset(7)">{{ 'Last 7 days' | translate }}</button>
        <button class="btn btn-outline-secondary" (click)="preset(30)">{{ 'Last 30 days' | translate }}</button>
        <button class="btn btn-outline-secondary" (click)="thisMonth()">{{ 'This month' | translate }}</button>
        <app-hotel-export *ngIf="r" style="margin-left:auto;" path="/hotel/reports/occupancy/export" name="occupancy" [filters]="{ from: r.from, to: r.to }"></app-hotel-export>
      </div>
      <ng-container *ngIf="r">
        <div class="stat-grid">
          <div class="stat-tile"><div class="stat-label">{{ 'Occupancy' | translate }}</div><div class="stat-value">{{ r.occupancyPercent }}%</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Room nights sold' | translate }}</div><div class="stat-value">{{ r.roomNightsSold }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Room nights available' | translate }}</div><div class="stat-value">{{ r.roomNightsAvailable }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'Room revenue' | translate }}</div><div class="stat-value">{{ r.roomRevenue | number: '1.2-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'ADR' | translate }}</div><div class="stat-value">{{ r.adr | number: '1.2-2' }}</div></div>
          <div class="stat-tile"><div class="stat-label">{{ 'RevPAR' | translate }}</div><div class="stat-value">{{ r.revPar | number: '1.2-2' }}</div></div>
        </div>
        <table>
          <thead><tr><th>{{ 'Room type' | translate }}</th><th class="text-end">{{ 'Room nights' | translate }}</th><th class="text-end">{{ 'Revenue (before tax)' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let t of r.byType"><td>{{ t.roomTypeName }}</td><td class="text-end">{{ t.roomNights }}</td><td class="text-end">{{ t.revenue | number: '1.2-2' }}</td></tr>
            <tr *ngIf="r.byType.length === 0"><td colspan="3" class="text-muted">{{ 'No room nights charged in this period.' | translate }}</td></tr>
          </tbody>
        </table>
        <p class="text-muted" style="font-size:0.8rem; margin-top:0.6rem;">
          {{ 'Based on the room charges on guests\\' bills ({p0} rooms in use). ADR = room revenue ÷ nights sold; RevPAR = room revenue ÷ nights available.' | translate: { p0: (r.roomCount) } }}
        </p>
      </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class HotelReportComponent implements OnInit {
  tabs = [
    { key: 'daily', label: T('Day report') },
    { key: 'occupancy', label: T('Occupancy') },
    { key: 'sources', label: T('By source') },
    { key: 'forecast', label: T('Forecast') },
    { key: 'audit', label: T('Night audit') },
  ];
  tab = 'daily';
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
