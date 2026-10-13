import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { ReportOption, ReportSchedule, ReportScheduleApiService } from '../core/api/report-schedule-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { Branch, Category, Warehouse } from '../core/models';

/**
 * Admin > Settings > Scheduled reports — pick a report, a format and a schedule, and it is emailed
 * automatically. Date-range reports cover the period that just ended (yesterday / the previous 7 days /
 * the previous calendar month). With no recipients it goes to the notifications email in Email settings.
 */
@Component({
  selector: 'app-report-schedule-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <h3 class="mb-3">Scheduled reports</h3>
      <p class="text-muted" style="max-width:760px;">
        Emails a report as an Excel or PDF attachment on a schedule. Email must be set up under Settings &gt; Email.
        Reports that cover a date range send the period that just ended: yesterday for daily, the previous 7 days
        for weekly, the previous calendar month for monthly. Snapshot reports (stock valuation, aging, ...) are
        taken at send time.
      </p>

      <div class="card mb-3" style="padding:0.75rem;">
        <strong>{{ editingId ? 'Edit schedule' : 'New schedule' }}</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div style="min-width:200px;">
            <label class="form-label">Name</label>
            <input class="form-control" [(ngModel)]="name" name="name" placeholder="e.g. Monday stock valuation" />
          </div>
          <div style="min-width:260px;">
            <label class="form-label">Report</label>
            <select class="form-select" [(ngModel)]="reportKey" name="reportKey" (ngModelChange)="onReportChange()">
              <option [ngValue]="null" disabled>Select</option>
              <optgroup *ngFor="let g of groups" [label]="g">
                <option *ngFor="let r of reportsIn(g)" [ngValue]="r.key">{{ r.label }}</option>
              </optgroup>
            </select>
          </div>
          <div>
            <label class="form-label">Format</label>
            <select class="form-select" [(ngModel)]="format" name="format">
              <option value="XLSX">Excel (.xlsx)</option>
              <option value="PDF">PDF</option>
            </select>
          </div>
          <div>
            <label class="form-label">Repeat</label>
            <select class="form-select" [(ngModel)]="frequency" name="frequency">
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </div>
          <div *ngIf="frequency === 'WEEKLY'">
            <label class="form-label">On</label>
            <select class="form-select" [(ngModel)]="dayOfWeek" name="dayOfWeek">
              <option *ngFor="let d of weekdays" [ngValue]="d.value">{{ d.label }}</option>
            </select>
          </div>
          <div *ngIf="frequency === 'MONTHLY'">
            <label class="form-label">Day of month</label>
            <select class="form-select" [(ngModel)]="dayOfMonth" name="dayOfMonth">
              <option *ngFor="let d of monthDays" [ngValue]="d">{{ d }}</option>
            </select>
          </div>
          <div>
            <label class="form-label">From time</label>
            <input class="form-control" type="time" [(ngModel)]="runTime" name="runTime" />
          </div>
        </div>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div style="min-width:160px;" *ngIf="selected?.categoryFilter">
            <label class="form-label">Category</label>
            <select class="form-select" [(ngModel)]="categoryId" name="categoryId">
              <option [ngValue]="null">All</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div style="min-width:160px;" *ngIf="selected?.warehouseFilter">
            <label class="form-label">Warehouse</label>
            <select class="form-select" [(ngModel)]="warehouseId" name="warehouseId">
              <option [ngValue]="null">All</option>
              <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.name }}</option>
            </select>
          </div>
          <div style="min-width:160px;" *ngIf="selected?.branchFilter">
            <label class="form-label">Branch</label>
            <select class="form-select" [(ngModel)]="branchId" name="branchId">
              <option [ngValue]="null">All</option>
              <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
            </select>
          </div>
          <div style="width:130px;" *ngIf="selected?.daysFilter">
            <label class="form-label">Days ({{ selected?.defaultDays }} default)</label>
            <input class="form-control" type="number" min="1" [(ngModel)]="dayWindow" name="dayWindow" />
          </div>
          <div style="min-width:280px; flex:1;">
            <label class="form-label">Send to (comma-separated; blank = notifications email)</label>
            <input class="form-control" [(ngModel)]="recipients" name="recipients" placeholder="owner@example.com, manager@example.com" />
          </div>
          <label class="form-label" style="margin-bottom:0.5rem;">
            <input type="checkbox" [(ngModel)]="active" name="active" /> Active
          </label>
          <button class="btn btn-primary" type="button" [disabled]="busy" (click)="save()">{{ editingId ? 'Save' : 'Add schedule' }}</button>
          <button class="btn btn-link" type="button" *ngIf="editingId" (click)="resetForm()">Cancel</button>
        </div>
      </div>

      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Report</th>
            <th>When</th>
            <th>Send to</th>
            <th>Last run</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let s of schedules">
            <td>
              {{ s.name }}
              <span class="text-muted" *ngIf="!s.active"> (paused)</span>
            </td>
            <td>{{ s.reportLabel }} <span class="text-muted">· {{ s.format }}</span></td>
            <td>{{ describeWhen(s) }}</td>
            <td>{{ s.recipients || 'Notifications email' }}</td>
            <td>
              <span *ngIf="s.lastStatus">
                <strong [style.color]="s.lastStatus === 'OK' ? 'var(--success, #198754)' : s.lastStatus === 'FAILED' ? 'var(--danger, #dc3545)' : 'inherit'">{{ s.lastStatus }}</strong>
                <span class="text-muted"> {{ s.lastRunAt | date: 'medium' }}</span>
                <div class="text-muted" style="font-size:0.8rem;">{{ s.lastMessage }}</div>
              </span>
              <span class="text-muted" *ngIf="!s.lastStatus">Never</span>
            </td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy" (click)="runNow(s)">Send now</button>
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="edit(s)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" type="button" (click)="remove(s)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="schedules.length === 0">
            <td colspan="6" class="text-muted">No scheduled reports yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ReportScheduleListComponent implements OnInit {
  schedules: ReportSchedule[] = [];
  reports: ReportOption[] = [];
  groups: string[] = [];
  categories: Category[] = [];
  warehouses: Warehouse[] = [];
  branches: Branch[] = [];
  error: string | null = null;
  notice: string | null = null;
  busy = false;

  readonly weekdays = [
    { value: 1, label: 'Monday' },
    { value: 2, label: 'Tuesday' },
    { value: 3, label: 'Wednesday' },
    { value: 4, label: 'Thursday' },
    { value: 5, label: 'Friday' },
    { value: 6, label: 'Saturday' },
    { value: 7, label: 'Sunday' },
  ];
  readonly monthDays = Array.from({ length: 28 }, (_, i) => i + 1);

  editingId: number | null = null;
  name = '';
  reportKey: string | null = null;
  format = 'XLSX';
  frequency = 'DAILY';
  dayOfWeek = 1;
  dayOfMonth = 1;
  runTime = '07:00';
  recipients = '';
  categoryId: number | null = null;
  warehouseId: number | null = null;
  branchId: number | null = null;
  dayWindow: number | null = null;
  active = true;

  constructor(
    private api: ReportScheduleApiService,
    private catalogApi: CatalogApiService,
    private warehouseApi: WarehouseApiService,
    private branchApi: BranchApiService,
  ) {}

  get selected(): ReportOption | null {
    return this.reports.find((r) => r.key === this.reportKey) ?? null;
  }

  ngOnInit(): void {
    this.api.reports().subscribe({
      next: (r) => {
        this.reports = r;
        this.groups = Array.from(new Set(r.map((x) => x.section)));
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the report list'),
    });
    this.catalogApi.listCategories().subscribe({ next: (c) => (this.categories = c), error: () => (this.categories = []) });
    this.warehouseApi.listAll().subscribe({ next: (w) => (this.warehouses = w), error: () => (this.warehouses = []) });
    this.branchApi.listAll().subscribe({ next: (b) => (this.branches = b), error: () => (this.branches = []) });
    this.load();
  }

  reportsIn(group: string): ReportOption[] {
    return this.reports.filter((r) => r.section === group);
  }

  load(): void {
    this.api.list().subscribe({
      next: (s) => (this.schedules = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load schedules'),
    });
  }

  onReportChange(): void {
    const r = this.selected;
    if (r && !this.name.trim()) this.name = r.label;
    if (!r?.categoryFilter) this.categoryId = null;
    if (!r?.warehouseFilter) this.warehouseId = null;
    if (!r?.branchFilter) this.branchId = null;
    if (!r?.daysFilter) this.dayWindow = null;
  }

  describeWhen(s: ReportSchedule): string {
    if (s.frequency === 'WEEKLY') {
      const d = this.weekdays.find((w) => w.value === s.dayOfWeek)?.label ?? '';
      return `Weekly, ${d} from ${s.runTime}`;
    }
    if (s.frequency === 'MONTHLY') return `Monthly, day ${s.dayOfMonth} from ${s.runTime}`;
    return `Daily from ${s.runTime}`;
  }

  edit(s: ReportSchedule): void {
    this.editingId = s.id;
    this.name = s.name;
    this.reportKey = s.reportKey;
    this.format = s.format;
    this.frequency = s.frequency;
    this.dayOfWeek = s.dayOfWeek ?? 1;
    this.dayOfMonth = s.dayOfMonth ?? 1;
    this.runTime = s.runTime;
    this.recipients = s.recipients ?? '';
    this.categoryId = s.categoryId;
    this.warehouseId = s.warehouseId;
    this.branchId = s.branchId;
    this.dayWindow = s.dayWindow;
    this.active = s.active;
    this.error = null;
    this.notice = null;
  }

  resetForm(): void {
    this.editingId = null;
    this.name = '';
    this.reportKey = null;
    this.format = 'XLSX';
    this.frequency = 'DAILY';
    this.dayOfWeek = 1;
    this.dayOfMonth = 1;
    this.runTime = '07:00';
    this.recipients = '';
    this.categoryId = null;
    this.warehouseId = null;
    this.branchId = null;
    this.dayWindow = null;
    this.active = true;
  }

  save(): void {
    if (!this.name.trim() || !this.reportKey) {
      this.error = 'Give the schedule a name and pick a report';
      return;
    }
    const payload = {
      name: this.name.trim(),
      reportKey: this.reportKey,
      format: this.format,
      frequency: this.frequency,
      runTime: this.runTime || '07:00',
      dayOfWeek: this.frequency === 'WEEKLY' ? this.dayOfWeek : null,
      dayOfMonth: this.frequency === 'MONTHLY' ? this.dayOfMonth : null,
      recipients: this.recipients.trim() || null,
      categoryId: this.categoryId,
      warehouseId: this.warehouseId,
      branchId: this.branchId,
      dayWindow: this.dayWindow ? Number(this.dayWindow) : null,
      active: this.active,
    };
    this.error = null;
    this.notice = null;
    this.busy = true;
    const call = this.editingId ? this.api.update(this.editingId, payload) : this.api.create(payload);
    call.subscribe({
      next: () => {
        this.busy = false;
        this.notice = this.editingId ? 'Schedule saved.' : 'Schedule added.';
        this.resetForm();
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to save the schedule';
      },
    });
  }

  runNow(s: ReportSchedule): void {
    this.error = null;
    this.notice = null;
    this.busy = true;
    this.api.run(s.id).subscribe({
      next: (r) => {
        this.busy = false;
        if (r.lastStatus === 'OK') this.notice = `${r.name}: ${r.lastMessage}`;
        else this.error = `${r.name}: ${r.lastStatus} — ${r.lastMessage}`;
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to send the report';
      },
    });
  }

  remove(s: ReportSchedule): void {
    if (!confirm(`Delete the schedule "${s.name}"?`)) return;
    this.api.delete(s.id).subscribe({
      next: () => {
        if (this.editingId === s.id) this.resetForm();
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to delete the schedule'),
    });
  }
}
