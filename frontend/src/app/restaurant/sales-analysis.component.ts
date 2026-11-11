import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantReportApiService } from '../core/api/restaurant-report-api.service';
import { AnalysisHour, AnalysisItem, RestaurantAnalysis } from '../core/models';

import { T } from '../core/i18n/t';
type Tab = 'overview' | 'menu' | 'servers' | 'tables' | 'controls';

/** Admin > Restaurant > Sales analysis: when the money comes in, what sells and what earns, who sells it and how tables turn. */
@Component({
  selector: 'app-sales-analysis',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:8px;">
        <h3>{{ 'Sales analysis' | translate }}</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/restaurant/end-of-day">{{ 'End of day (cash-up)' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="card mb-3" style="padding:0.75rem;">
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <button *ngFor="let p of presets" class="btn btn-sm btn-outline-secondary an-preset" (click)="preset(p.key)">{{ p.label }}</button>
          <label class="ms-2">{{ 'From' | translate }} <input type="date" class="form-control an-from" style="display:inline-block; width:auto;" [(ngModel)]="from" name="anfrom" /></label>
          <label>{{ 'To' | translate }} <input type="date" class="form-control an-to" style="display:inline-block; width:auto;" [(ngModel)]="to" name="anto" /></label>
          <button class="btn btn-primary an-go" (click)="load()" [disabled]="loading">{{ loading ? ('Loading…' | translate) : ('Show' | translate) }}</button>
        </div>
      </div>

      <ng-container *ngIf="a">
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="an-card"><div class="an-n an-k-net">{{ a.totals.netSales | number: '1.2-2' }}</div><div class="text-muted">{{ 'net sales' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-billed">{{ a.totals.billed | number: '1.2-2' }}</div><div class="text-muted">{{ 'billed (with tax and service)' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-checks">{{ a.totals.checks }}</div><div class="text-muted">{{ 'checks' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-covers">{{ a.totals.covers }}</div><div class="text-muted">{{ 'covers' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-avg">{{ a.totals.averageCheck | number: '1.2-2' }}</div><div class="text-muted">{{ 'average check' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-cover">{{ a.totals.averagePerCover | number: '1.2-2' }}</div><div class="text-muted">{{ 'per cover (dine-in)' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-margin">{{ a.totals.marginPercent | number: '1.1-1' }}%</div><div class="text-muted">{{ 'margin ({p0})' | translate: { p0: (a.totals.profit | number: '1.2-2') } }}</div></div>
          <div class="an-card"><div class="an-n an-k-tips">{{ a.totals.tips | number: '1.2-2' }}</div><div class="text-muted">{{ 'tips' | translate }}</div></div>
          <div class="an-card"><div class="an-n an-k-minutes">{{ a.totals.averageMinutesAtTable }}<small> {{ 'min' | translate }}</small></div><div class="text-muted">{{ 'at the table' | translate }}</div></div>
        </div>

        <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
          <button *ngFor="let t of tabs" class="btn btn-sm an-tab" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="setTab(t.key)">{{ t.label }}</button>
          <span style="margin-left:auto;" class="flex gap-2 align-center">
            <select class="form-control an-export-what" style="width:auto;" [(ngModel)]="exportWhat" name="anexw">
              <option *ngFor="let e of exportChoices()" [ngValue]="e.key">{{ e.label }}</option>
            </select>
            <button class="btn btn-sm btn-outline-secondary an-export-xlsx" (click)="download('xlsx')" [disabled]="exporting">{{ 'Excel' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary an-export-pdf" (click)="download('pdf')" [disabled]="exporting">{{ 'PDF' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary an-export-csv" (click)="download('csv')" [disabled]="exporting">{{ 'CSV' | translate }}</button>
          </span>
        </div>
        <div class="alert alert-danger" *ngIf="exportError">{{ exportError }}</div>

        <!-- overview -->
        <div *ngIf="tab === 'overview'">
          <div class="card mb-3" style="padding:1rem;">
            <h4 style="font-size:1rem;" class="mb-2">{{ 'Sales by hour' | translate }} <small class="text-muted">{{ '(by the hour the bill was paid)' | translate }}</small></h4>
            <p class="text-muted" *ngIf="hourRows.length === 0">{{ 'No sales in this period.' | translate }}</p>
            <div *ngFor="let h of hourRows" class="an-hour-row flex align-center gap-2">
              <span class="an-hour-label">{{ h.hour < 10 ? '0' : '' }}{{ h.hour }}:00</span>
              <span class="an-bar-wrap"><span class="an-bar an-hour-bar" [style.width.%]="pctOf(h.netSales, maxHour)"></span></span>
              <span class="an-hour-amt">{{ h.netSales | number: '1.2-2' }}</span>
              <span class="text-muted">{{ '{p0} check{p1}, {p2} cover{p3}' | translate: { p0: (h.checks), p1: (h.checks === 1 ? '' : 's'), p2: (h.covers), p3: (h.covers === 1 ? '' : 's') } }}</span>
            </div>
          </div>

          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;">
            <div class="card" style="padding:1rem; flex:1; min-width:320px;">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'By weekday' | translate }}</h4>
              <table class="an-weekday-table">
                <thead><tr><th>{{ 'Day' | translate }}</th><th class="text-end">{{ 'Days' | translate }}</th><th class="text-end">{{ 'Checks' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Per day' | translate }}</th></tr></thead>
                <tbody>
                  <tr *ngFor="let w of a.weekdays" class="an-weekday-row" [class.text-muted]="w.days === 0">
                    <td>{{ w.name }}</td><td class="text-end">{{ w.days }}</td><td class="text-end">{{ w.checks }}</td>
                    <td class="text-end">{{ w.netSales | number: '1.2-2' }}</td><td class="text-end">{{ w.averageNetPerDay | number: '1.2-2' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="card" style="padding:1rem; flex:1; min-width:320px;">
              <h4 style="font-size:1rem;" class="mb-2">{{ 'By order type' | translate }}</h4>
              <table class="an-type-table">
                <thead><tr><th>{{ 'Type' | translate }}</th><th class="text-end">{{ 'Checks' | translate }}</th><th class="text-end">{{ 'Covers' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Average check' | translate }}</th></tr></thead>
                <tbody>
                  <tr *ngFor="let t of a.types" class="an-type-row">
                    <td>{{ pretty(t.type) }}</td><td class="text-end">{{ t.checks }}</td><td class="text-end">{{ t.covers }}</td>
                    <td class="text-end">{{ t.netSales | number: '1.2-2' }}</td><td class="text-end">{{ t.averageCheck | number: '1.2-2' }}</td>
                  </tr>
                </tbody>
              </table>
              <h4 style="font-size:1rem;" class="mb-2 mt-3">{{ 'How guests paid' | translate }}</h4>
              <table class="an-pay-table">
                <thead><tr><th>{{ 'Method' | translate }}</th><th class="text-end">{{ 'Checks' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th class="text-end">{{ 'Share' | translate }}</th></tr></thead>
                <tbody>
                  <tr *ngFor="let p of a.payments" class="an-pay-row">
                    <td>{{ p.method === 'DEPOSIT' ? ('Booking deposit' | translate) : p.method }}</td><td class="text-end">{{ p.checks }}</td>
                    <td class="text-end">{{ p.amount | number: '1.2-2' }}</td><td class="text-end">{{ p.percent | number: '1.1-1' }}%</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="card mt-3" style="padding:1rem;" *ngIf="a.days.length > 1">
            <h4 style="font-size:1rem;" class="mb-2">{{ 'By day' | translate }}</h4>
            <table class="an-day-table">
              <thead><tr><th>{{ 'Date' | translate }}</th><th class="text-end">{{ 'Checks' | translate }}</th><th class="text-end">{{ 'Covers' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Billed' | translate }}</th></tr></thead>
              <tbody>
                <tr *ngFor="let d of a.days" class="an-day-row" [class.text-muted]="d.checks === 0">
                  <td>{{ d.date }}</td><td class="text-end">{{ d.checks }}</td><td class="text-end">{{ d.covers }}</td>
                  <td class="text-end">{{ d.netSales | number: '1.2-2' }}</td><td class="text-end">{{ d.billed | number: '1.2-2' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- menu mix -->
        <div *ngIf="tab === 'menu'">
          <div class="card mb-3" style="padding:1rem;">
            <h4 style="font-size:1rem;" class="mb-2">{{ 'By category' | translate }}</h4>
            <table class="an-cat-table">
              <thead><tr><th>{{ 'Category' | translate }}</th><th class="text-end">{{ 'Sold' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Share' | translate }}</th><th class="text-end">{{ 'Cost' | translate }}</th><th class="text-end">{{ 'Profit' | translate }}</th><th class="text-end">{{ 'Margin' | translate }}</th></tr></thead>
              <tbody>
                <tr *ngFor="let c of a.categories" class="an-cat-row">
                  <td>{{ c.name }}</td><td class="text-end">{{ c.quantity }}</td><td class="text-end">{{ c.netSales | number: '1.2-2' }}</td>
                  <td class="text-end">{{ c.salesPercent | number: '1.1-1' }}%</td><td class="text-end">{{ c.cost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ c.profit | number: '1.2-2' }}</td><td class="text-end">{{ c.marginPercent | number: '1.1-1' }}%</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="card" style="padding:1rem;">
            <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:8px;">
              <h4 style="font-size:1rem;">{{ 'Menu mix' | translate }}</h4>
              <span class="flex gap-2">
                <button class="btn btn-sm an-class-filter" [class.btn-primary]="classFilter === ''" [class.btn-outline-secondary]="classFilter !== ''" (click)="setClass('')">{{ 'All' | translate }}</button>
                <button *ngFor="let c of classes" class="btn btn-sm an-class-filter" [class.btn-primary]="classFilter === c.key" [class.btn-outline-secondary]="classFilter !== c.key" (click)="setClass(c.key)">{{ c.label }}</button>
              </span>
            </div>
            <p class="text-muted" style="font-size:0.85rem;">
              <b>{{ 'Star' | translate }}</b>{{ ': sells well and earns well — protect it.' | translate }} <b>{{ 'Plowhorse' | translate }}</b>{{ ': sells well but earns little — try a small price rise or a cheaper recipe.' | translate }}
              <b>{{ 'Puzzle' | translate }}</b>{{ ': earns well but sells little — promote it.' | translate }} <b>{{ 'Dog' | translate }}</b>{{ ': sells little and earns little — rework or drop it. A dish sells well when it is at least 70% of an even share of everything sold; it earns well when its profit per plate is at or above the menu\\'s average.' | translate }}
            </p>
            <table class="an-item-table">
              <thead><tr><th>{{ 'Dish' | translate }}</th><th>{{ 'Category' | translate }}</th><th class="text-end">{{ 'Sold' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Cost' | translate }}</th><th class="text-end">{{ 'Profit' | translate }}</th><th class="text-end">{{ 'Margin' | translate }}</th><th class="text-end">{{ 'Profit each' | translate }}</th><th class="text-end">{{ 'Share of dishes' | translate }}</th><th>{{ 'Class' | translate }}</th></tr></thead>
              <tbody>
                <tr *ngFor="let i of shownItems" class="an-item-row">
                  <td>{{ i.name }}</td><td>{{ i.category }}</td><td class="text-end">{{ i.quantity }}</td>
                  <td class="text-end">{{ i.netSales | number: '1.2-2' }}</td><td class="text-end">{{ i.cost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ i.profit | number: '1.2-2' }}</td><td class="text-end">{{ i.marginPercent | number: '1.1-1' }}%</td>
                  <td class="text-end">{{ i.unitProfit | number: '1.2-2' }}</td><td class="text-end">{{ i.popularity | number: '1.1-1' }}%</td>
                  <td><span class="an-class" [ngClass]="'an-c-' + (i.classification || 'none').toLowerCase()">{{ className(i) }}</span></td>
                </tr>
              </tbody>
            </table>
            <p class="text-muted mt-2" *ngIf="shownItems.length === 0">{{ 'No dishes to show.' | translate }}</p>
          </div>
        </div>

        <!-- servers -->
        <div *ngIf="tab === 'servers'" class="card" style="padding:1rem;">
          <h4 style="font-size:1rem;" class="mb-2">{{ 'By server' | translate }}</h4>
          <table class="an-server-table">
            <thead><tr><th>{{ 'Server' | translate }}</th><th class="text-end">{{ 'Checks' | translate }}</th><th class="text-end">{{ 'Covers' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Average check' | translate }}</th><th class="text-end">{{ 'Service charge' | translate }}</th><th class="text-end">{{ 'Tips' | translate }}</th><th class="text-end">{{ 'Discounts' | translate }}</th><th class="text-end">{{ 'Voids' | translate }}</th><th class="text-end">{{ 'Comps' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let s of a.servers" class="an-server-row">
                <td>{{ s.name }}</td><td class="text-end">{{ s.checks }}</td><td class="text-end">{{ s.covers }}</td>
                <td class="text-end">{{ s.netSales | number: '1.2-2' }}</td><td class="text-end">{{ s.averageCheck | number: '1.2-2' }}</td>
                <td class="text-end">{{ s.serviceCharge | number: '1.2-2' }}</td><td class="text-end">{{ s.tips | number: '1.2-2' }}</td>
                <td class="text-end">{{ s.discounts | number: '1.2-2' }}</td>
                <td class="text-end">{{ s.voids }} <span class="text-muted" *ngIf="s.voids">({{ s.voidAmount | number: '1.2-2' }})</span></td>
                <td class="text-end">{{ s.comps }} <span class="text-muted" *ngIf="s.comps">({{ s.compAmount | number: '1.2-2' }})</span></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" style="font-size:0.85rem;">{{ 'Voids and comps are counted against the person who took the item off, which is a manager\\'s name in most cases.' | translate }}</p>
          <p class="text-muted" *ngIf="a.servers.length === 0">{{ 'No sales in this period.' | translate }}</p>
        </div>

        <!-- tables -->
        <div *ngIf="tab === 'tables'" class="card" style="padding:1rem;">
          <h4 style="font-size:1rem;" class="mb-2">{{ 'Table turns' | translate }}</h4>
          <table class="an-table-table">
            <thead><tr><th>{{ 'Table' | translate }}</th><th>{{ 'Section' | translate }}</th><th class="text-end">{{ 'Seats' | translate }}</th><th class="text-end">{{ 'Parties' | translate }}</th><th class="text-end">{{ 'Covers' | translate }}</th><th class="text-end">{{ 'Turns per day' | translate }}</th><th class="text-end">{{ 'Minutes per party' | translate }}</th><th class="text-end">{{ 'Net sales' | translate }}</th><th class="text-end">{{ 'Per cover' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let t of a.tables" class="an-table-row">
                <td>{{ t.name }}</td><td>{{ t.section }}</td><td class="text-end">{{ t.seats }}</td><td class="text-end">{{ t.parties }}</td><td class="text-end">{{ t.covers }}</td>
                <td class="text-end">{{ t.turnsPerDay | number: '1.2-2' }}</td><td class="text-end">{{ t.averageMinutes }}</td>
                <td class="text-end">{{ t.netSales | number: '1.2-2' }}</td><td class="text-end">{{ t.salesPerCover | number: '1.2-2' }}</td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" style="font-size:0.85rem;">{{ 'A party that split its bill counts once, from sitting down until the last check was paid.' | translate }}</p>
          <p class="text-muted" *ngIf="a.tables.length === 0">{{ 'No table sales in this period.' | translate }}</p>
        </div>

        <!-- controls -->
        <div *ngIf="tab === 'controls'" class="card" style="padding:1rem; max-width:620px;">
          <h4 style="font-size:1rem;" class="mb-2">{{ 'Voids, comps and cancelled orders' | translate }}</h4>
          <table class="an-control-table">
            <thead><tr><th>{{ 'Kind' | translate }}</th><th class="text-end">{{ 'Count' | translate }}</th><th class="text-end">{{ 'Value' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let c of a.controls" class="an-control-row"><td>{{ pretty(c.type) }}</td><td class="text-end">{{ c.count }}</td><td class="text-end">{{ c.type === 'REPRINT' ? '' : (c.amount | number: '1.2-2') }}</td></tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="a.controls.length === 0">{{ 'Nothing was voided, comped, cancelled or reprinted in this period.' | translate }}</p>
          <p class="text-muted mt-2" style="font-size:0.85rem;">{{ 'Discounts given on bills: {p0} (this includes comps). See' | translate: { p0: (a.totals.discounts | number: '1.2-2') } }} <a routerLink="/admin/restaurant/voids">{{ 'Voids and comps' | translate }}</a> {{ 'for who and why.' | translate }}</p>
        </div>
      </ng-container>
    </app-shell>
  `,
  styles: [
    `
      .an-card { min-width: 130px; border: 1px solid var(--border, #ddd); border-radius: 8px; padding: 0.5rem 0.9rem; text-align: center; }
      .an-n { font-size: 1.35rem; font-weight: 700; }
      table { width: 100%; border-collapse: collapse; }
      th, td { padding: 4px 8px; border-bottom: 1px solid var(--border, #eee); }
      .an-hour-row { margin-bottom: 4px; }
      .an-hour-label { width: 48px; font-variant-numeric: tabular-nums; }
      .an-bar-wrap { flex: 1; max-width: 520px; background: rgba(120, 120, 120, 0.12); border-radius: 4px; height: 16px; display: inline-block; }
      .an-bar { display: block; height: 100%; background: var(--primary, #2563eb); border-radius: 4px; min-width: 2px; }
      .an-hour-amt { width: 110px; text-align: right; font-variant-numeric: tabular-nums; }
      .an-class { padding: 1px 8px; border-radius: 10px; font-size: 0.8rem; font-weight: 600; }
      .an-c-star { background: rgba(47, 190, 114, 0.2); color: #15803d; }
      .an-c-plowhorse { background: rgba(245, 158, 11, 0.2); color: #b45309; }
      .an-c-puzzle { background: rgba(59, 130, 246, 0.2); color: #1d4ed8; }
      .an-c-dog { background: rgba(220, 38, 38, 0.15); color: #b91c1c; }
    `,
  ],
})
export class SalesAnalysisComponent implements OnInit {
  presets = [
    { key: 'today', label: 'Today' },
    { key: 'yesterday', label: 'Yesterday' },
    { key: 'week', label: 'Last 7 days' },
    { key: 'month', label: 'Last 30 days' },
    { key: 'thismonth', label: 'This month' },
  ];
  tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: 'Overview' },
    { key: 'menu', label: 'Menu mix' },
    { key: 'servers', label: 'Servers' },
    { key: 'tables', label: 'Tables' },
    { key: 'controls', label: 'Voids & comps' },
  ];
  classes = [
    { key: 'STAR', label: 'Stars' },
    { key: 'PLOWHORSE', label: 'Plowhorses' },
    { key: 'PUZZLE', label: 'Puzzles' },
    { key: 'DOG', label: 'Dogs' },
  ];
  from = iso(new Date());
  to = iso(new Date());
  a: RestaurantAnalysis | null = null;
  tab: Tab = 'overview';
  classFilter = '';
  shownItems: AnalysisItem[] = [];
  hourRows: AnalysisHour[] = [];
  maxHour = 0;
  loading = false;
  error: string | null = null;
  exportWhat = 'hours';
  exporting = false;
  exportError: string | null = null;

  constructor(private api: RestaurantReportApiService) {}

  ngOnInit(): void {
    this.load();
  }

  preset(key: string): void {
    const now = new Date();
    const day = (n: number) => iso(new Date(now.getFullYear(), now.getMonth(), now.getDate() + n));
    if (key === 'today') [this.from, this.to] = [day(0), day(0)];
    else if (key === 'yesterday') [this.from, this.to] = [day(-1), day(-1)];
    else if (key === 'week') [this.from, this.to] = [day(-6), day(0)];
    else if (key === 'month') [this.from, this.to] = [day(-29), day(0)];
    else [this.from, this.to] = [iso(new Date(now.getFullYear(), now.getMonth(), 1)), day(0)];
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.api.analysis(this.from, this.to).subscribe({
      next: (r) => {
        this.a = r;
        this.loading = false;
        this.prepare();
      },
      error: (e) => {
        this.loading = false;
        this.error = e?.error?.message || T('Could not load the analysis');
      },
    });
  }

  private prepare(): void {
    const a = this.a!;
    const active = a.hours.filter((h) => h.checks > 0);
    if (active.length === 0) {
      this.hourRows = [];
      this.maxHour = 0;
    } else {
      const lo = Math.min(...active.map((h) => h.hour));
      const hi = Math.max(...active.map((h) => h.hour));
      this.hourRows = a.hours.filter((h) => h.hour >= lo && h.hour <= hi);
      this.maxHour = Math.max(...this.hourRows.map((h) => h.netSales), 0);
    }
    this.applyClass();
    if (!this.exportChoices().some((e) => e.key === this.exportWhat)) this.exportWhat = this.exportChoices()[0].key;
  }

  setTab(t: Tab): void {
    this.tab = t;
    this.exportWhat = this.exportChoices()[0].key;
  }

  setClass(c: string): void {
    this.classFilter = c;
    this.applyClass();
  }

  private applyClass(): void {
    this.shownItems = (this.a?.items ?? []).filter((i) => !this.classFilter || i.classification === this.classFilter);
  }

  className(i: AnalysisItem): string {
    return i.classification ? this.classes.find((c) => c.key === i.classification)!.label.replace(/s$/, '') : '';
  }

  exportChoices(): { key: string; label: string }[] {
    switch (this.tab) {
      case 'menu': return [{ key: 'items', label: 'Menu mix' }, { key: 'categories', label: 'Categories' }];
      case 'servers': return [{ key: 'servers', label: 'Servers' }];
      case 'tables': return [{ key: 'tables', label: 'Table turns' }];
      case 'controls': return [{ key: 'controls', label: 'Voids & comps' }];
      default: return [{ key: 'hours', label: 'By hour' }, { key: 'weekdays', label: 'By weekday' }, { key: 'days', label: 'By day' }, { key: 'types', label: 'By order type' }, { key: 'payments', label: 'Payments' }];
    }
  }

  download(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.exporting = true;
    this.exportError = null;
    this.api.download(this.exportWhat, { from: this.from, to: this.to }, format, (err) => {
      this.exporting = false;
      if (err) this.exportError = 'The export failed.';
    });
  }

  pctOf(v: number, max: number): number {
    return max > 0 ? Math.max(1, Math.round((v / max) * 100)) : 0;
  }

  pretty(code: string): string {
    const t = (code || '').toLowerCase().replace(/_/g, ' ');
    return t.charAt(0).toUpperCase() + t.slice(1);
  }
}

function iso(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}
