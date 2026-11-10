import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelApiService, NightAuditReport } from '../core/api/hotel-api.service';
import { HotelExportComponent } from './hotel-export.component';
import { addDays, errMsg, today } from './hotel-util';

/** Hotel > Reports > Night audit: the day's totals, what the hotel posted to Accounting, and checks that the two agree. */
@Component({
  selector: 'app-rpt-audit',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, HotelExportComponent],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="flex gap-2 align-center mb-3" style="flex-wrap:wrap;">
      <label class="form-label" style="margin:0;">{{ 'Day' | translate }}</label>
      <input class="form-control au-date" style="width:auto;" type="date" name="date" [(ngModel)]="date" [max]="max" />
      <button class="btn btn-primary au-show" (click)="load()">{{ 'Show' | translate }}</button>
      <button class="btn btn-outline-secondary" (click)="go(0)">{{ 'Today' | translate }}</button>
      <button class="btn btn-outline-secondary" (click)="go(-1)">{{ 'Yesterday' | translate }}</button>
      <app-hotel-export *ngIf="r" style="margin-left:auto;" path="/hotel/reports/night-audit/export" name="night-audit" [filters]="exportFilters"></app-hotel-export>
    </div>
    <ng-container *ngIf="r">
      <div class="alert au-banner" [ngClass]="r.balanced ? 'alert-success' : 'alert-danger'">
        <strong *ngIf="r.balanced">{{ 'Hotel and Accounting agree.' | translate }}</strong>
        <strong *ngIf="!r.balanced">{{ 'Hotel and Accounting differ — see the checks below.' | translate }}</strong>
        <span style="margin-left:0.6rem;">
          {{ 'Night audit {p0}' | translate: { p0: (r.auditRanForDate ? 'has run for this day' : 'has not run for this day yet') } }}<span *ngIf="r.lastAuditRun"> {{ '(last run {p0})' | translate: { p0: (r.lastAuditRun) } }}</span>{{ '. Rooms are posted {p0}.' | translate: { p0: (r.postingMode === 'NIGHTLY' ? 'night by night' : 'at check-out') } }}
        </span>
      </div>
      <div class="stat-grid">
        <div class="stat-tile"><div class="stat-label">{{ 'In house' | translate }}</div><div class="stat-value">{{ r.inHouseStays }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Arrivals / departures' | translate }}</div><div class="stat-value">{{ r.arrivals }} / {{ r.departures }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Room nights charged' | translate }}</div><div class="stat-value">{{ r.roomNightsCharged }}</div><div class="text-muted" style="font-size:0.8rem;">{{ r.roomRevenue | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Charged (before tax)' | translate }}</div><div class="stat-value">{{ r.chargedTotal | number: '1.2-2' }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Money taken' | translate }}</div><div class="stat-value">{{ r.moneyTotal | number: '1.2-2' }}</div></div>
      </div>

      <h5>{{ 'Checks against Accounting' | translate }}</h5>
      <table class="au-checks">
        <thead><tr><th>{{ 'Check' | translate }}</th><th class="text-end">{{ 'Hotel' | translate }}</th><th class="text-end">{{ 'Accounting' | translate }}</th><th class="text-end">{{ 'Difference' | translate }}</th><th>{{ 'Result' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let c of r.checks" class="au-check" [attr.data-code]="c.code">
            <td><strong>{{ c.label }}</strong><div class="text-muted" style="font-size:0.78rem;">{{ c.note }}</div></td>
            <td class="text-end">{{ c.hotel | number: '1.2-2' }}</td><td class="text-end">{{ c.accounting | number: '1.2-2' }}</td><td class="text-end">{{ c.difference | number: '1.2-2' }}</td>
            <td><span class="badge" [ngClass]="c.ok ? 'badge-success' : 'badge-danger'" style="padding:0.15rem 0.5rem; border-radius:4px; color:#fff;" [style.background]="c.ok ? '#16a34a' : '#dc2626'">{{ c.ok ? ('OK' | translate) : ('Differs' | translate) }}</span></td>
          </tr>
        </tbody>
      </table>

      <div class="grid-2" style="display:grid; grid-template-columns:repeat(auto-fit,minmax(320px,1fr)); gap:1rem; margin-top:1rem;">
        <div>
          <h5>{{ 'Charged to guests' | translate }}</h5>
          <table class="au-charged">
            <thead><tr><th>{{ 'Type' | translate }}</th><th class="text-end">{{ 'Lines' | translate }}</th><th class="text-end">{{ 'Before tax' | translate }}</th><th class="text-end">{{ 'Tax' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of r.charged"><td>{{ l.type }}</td><td class="text-end">{{ l.lines }}</td><td class="text-end">{{ l.net | number: '1.2-2' }}</td><td class="text-end">{{ l.tax | number: '1.2-2' }}</td></tr>
              <tr *ngIf="r.charged.length === 0"><td colspan="4" class="text-muted">{{ 'Nothing was charged.' | translate }}</td></tr>
            </tbody>
          </table>
          <h5 class="mt-3">{{ 'Money taken' | translate }}</h5>
          <table class="au-money">
            <thead><tr><th>{{ 'Method' | translate }}</th><th class="text-end">{{ 'Deposits' | translate }}</th><th class="text-end">{{ 'Payments' | translate }}</th><th class="text-end">{{ 'Refunds' | translate }}</th><th class="text-end">{{ 'Net' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let m of r.money"><td>{{ m.method }}</td><td class="text-end">{{ m.deposits | number: '1.2-2' }}</td><td class="text-end">{{ m.payments | number: '1.2-2' }}</td><td class="text-end">{{ m.refunds | number: '1.2-2' }}</td><td class="text-end">{{ m.net | number: '1.2-2' }}</td></tr>
              <tr *ngIf="r.money.length === 0"><td colspan="5" class="text-muted">{{ 'No money taken.' | translate }}</td></tr>
            </tbody>
          </table>
        </div>
        <div>
          <h5>{{ 'Posted to Accounting that day' | translate }}</h5>
          <table class="au-postings">
            <thead><tr><th>{{ 'Account' | translate }}</th><th class="text-end">{{ 'Debit' | translate }}</th><th class="text-end">{{ 'Credit' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let p of r.postings"><td>{{ p.account }}</td><td class="text-end">{{ p.debit | number: '1.2-2' }}</td><td class="text-end">{{ p.credit | number: '1.2-2' }}</td></tr>
              <tr *ngIf="r.postings.length === 0"><td colspan="3" class="text-muted">{{ 'The hotel posted nothing on this day.' | translate }}</td></tr>
            </tbody>
          </table>
        </div>
      </div>
      <p class="text-muted" style="font-size:0.8rem; margin-top:0.6rem;">
        {{ 'The cash check covers this day only. The others compare the hotel\\'s records with Accounting up to now: revenue and tax posted, deposits held, and what companies and groups still owe. A difference means a posting is missing or was changed outside the hotel — look in Accounting for entries from the Hotel.' | translate }}
      </p>
    </ng-container>
  `,
})
export class RptAuditComponent implements OnInit {
  max = today();
  date = today();
  r: NightAuditReport | null = null;
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
    this.api.nightAuditReport(this.date).subscribe({
      next: (r) => {
        this.r = r;
        this.exportFilters = { date: r.date };
      },
      error: (e) => (this.error = errMsg(e, 'Failed to load the night audit report')),
    });
  }
}
