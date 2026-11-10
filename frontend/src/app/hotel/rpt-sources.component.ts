import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelApiService, SourceReport } from '../core/api/hotel-api.service';
import { HotelExportComponent } from './hotel-export.component';
import { addDays, errMsg, iso, today } from './hotel-util';

/** Hotel > Reports > Sources: bookings and revenue by where the booking came from, for stays arriving in a period. */
@Component({
  selector: 'app-rpt-sources',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, HotelExportComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
      <label class="form-label" style="margin:0;">{{ 'Arriving from' | translate }}</label>
      <input class="form-control src-from" style="width:auto;" type="date" name="from" [(ngModel)]="from" />
      <label class="form-label" style="margin:0;">{{ 'to' | translate }}</label>
      <input class="form-control src-to" style="width:auto;" type="date" name="to" [(ngModel)]="to" />
      <button class="btn btn-primary src-show" (click)="load()">{{ 'Show' | translate }}</button>
      <button class="btn btn-outline-secondary" (click)="preset(30)">{{ 'Last 30 days' | translate }}</button>
      <button class="btn btn-outline-secondary" (click)="preset(90)">{{ 'Last 90 days' | translate }}</button>
      <button class="btn btn-outline-secondary" (click)="thisYear()">{{ 'This year' | translate }}</button>
      <app-hotel-export *ngIf="r" style="margin-left:auto;" path="/hotel/reports/sources/export" name="revenue-by-source" [filters]="exportFilters"></app-hotel-export>
    </div>
    <ng-container *ngIf="r">
      <div style="overflow-x:auto;">
        <table class="src-table">
          <thead>
            <tr><th>{{ 'Source' | translate }}</th><th class="text-end">{{ 'Bookings' | translate }}</th><th class="text-end">{{ 'Stays' | translate }}</th><th class="text-end">{{ 'Cancelled' | translate }}</th><th class="text-end">{{ 'No-shows' | translate }}</th><th class="text-end">{{ 'Lost' | translate }}</th>
              <th class="text-end">{{ 'Room nights' | translate }}</th><th class="text-end">{{ 'Room revenue' | translate }}</th><th class="text-end">{{ 'Other revenue' | translate }}</th><th class="text-end">{{ 'ADR' | translate }}</th><th class="text-end">{{ 'Avg stay' | translate }}</th><th class="text-end">{{ 'Avg lead (days)' | translate }}</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let x of r.rows" class="src-row">
              <td><strong>{{ x.source }}</strong></td><td class="text-end">{{ x.bookings }}</td><td class="text-end">{{ x.stays }}</td><td class="text-end">{{ x.cancelled }}</td><td class="text-end">{{ x.noShows }}</td>
              <td class="text-end">{{ x.cancelPercent }}%</td><td class="text-end">{{ x.roomNights }}</td><td class="text-end">{{ x.roomRevenue | number: '1.2-2' }}</td><td class="text-end">{{ x.otherRevenue | number: '1.2-2' }}</td>
              <td class="text-end">{{ x.adr | number: '1.2-2' }}</td><td class="text-end">{{ x.averageStay | number: '1.1-1' }}</td><td class="text-end">{{ x.averageLeadDays | number: '1.1-1' }}</td>
            </tr>
            <tr *ngIf="r.rows.length === 0"><td colspan="12" class="text-muted">{{ 'No bookings arrive in this period.' | translate }}</td></tr>
            <tr *ngIf="r.rows.length > 0" class="src-total">
              <td><strong>{{ 'Total' | translate }}</strong></td><td class="text-end">{{ r.total.bookings }}</td><td class="text-end">{{ r.total.stays }}</td><td class="text-end">{{ r.total.cancelled }}</td><td class="text-end">{{ r.total.noShows }}</td>
              <td class="text-end">{{ r.total.cancelPercent }}%</td><td class="text-end">{{ r.total.roomNights }}</td><td class="text-end"><strong>{{ r.total.roomRevenue | number: '1.2-2' }}</strong></td><td class="text-end">{{ r.total.otherRevenue | number: '1.2-2' }}</td>
              <td class="text-end">{{ r.total.adr | number: '1.2-2' }}</td><td class="text-end">{{ r.total.averageStay | number: '1.1-1' }}</td><td class="text-end">{{ r.total.averageLeadDays | number: '1.1-1' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-muted" style="font-size:0.8rem; margin-top:0.6rem;">
        {{ 'Every booking arriving in the period, grouped by the source on the booking (blank ones show as Unspecified). Revenue is what the stays have been charged so far, before tax. Lost = cancelled plus no-show, as a share of bookings. Lead = days between booking and arrival.' | translate }}
      </p>
    </ng-container>
  `,
})
export class RptSourcesComponent implements OnInit {
  from = '';
  to = today();
  r: SourceReport | null = null;
  exportFilters: Record<string, string> = {};
  error: string | null = null;

  constructor(private api: HotelApiService) {}

  ngOnInit(): void {
    this.preset(30);
  }

  preset(days: number): void {
    this.to = today();
    this.from = addDays(this.to, -(days - 1));
    this.load();
  }

  thisYear(): void {
    this.from = iso(new Date(new Date().getFullYear(), 0, 1));
    this.to = today();
    this.load();
  }

  load(): void {
    this.error = null;
    this.api.sourceReport(this.from, this.to).subscribe({
      next: (r) => {
        this.r = r;
        this.exportFilters = { from: r.from, to: r.to };
      },
      error: (e) => (this.error = errMsg(e, 'Failed to load the report')),
    });
  }
}
