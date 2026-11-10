import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { AssetApiService, FixedAsset } from '../../core/api/asset-api.service';
import { ModuleApiService } from '../../core/api/module-api.service';
import { StaffOption, TimeApiService } from '../../core/api/time-api.service';
import { Customer } from '../../core/models';
import { MaintenanceSchedule, PackageSummary, SchedulePayload, WorkOrderApiService } from '../../core/api/work-order-api.service';
import { WorkOrderTabsComponent } from './work-order-tabs.component';

/**
 * Work Orders > Planned maintenance: jobs that repeat (service the generator every 3 months, a monthly visit for a contract customer).
 * A daily check creates each job when it is due; "Run now" makes one straight away.
 */
@Component({
  selector: 'app-maintenance-schedule-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, WorkOrderTabsComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-2">
        <h3>{{ 'Work orders' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary" type="button" id="run-due" [disabled]="busy" (click)="runDue()">{{ 'Create the jobs that are due' | translate }}</button>
          <button class="btn btn-primary" type="button" id="new-schedule" (click)="startNew()">{{ '+ New schedule' | translate }}</button>
        </div>
      </div>
      <app-work-order-tabs></app-work-order-tabs>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <form class="card mb-3" style="padding:1rem; max-width:60rem;" *ngIf="f" (ngSubmit)="save()">
        <strong class="mb-2">{{ editId ? ('Edit schedule' | translate) : ('New schedule' | translate) }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:2; min-width:16rem;"><label class="form-label">{{ 'Title (becomes the job title)' | translate }}</label><input class="form-control" name="ti" [(ngModel)]="f.title" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Type' | translate }}</label>
            <select class="form-select" name="ty" [(ngModel)]="f.type">
              <option value="INTERNAL">{{ 'Internal maintenance' | translate }}</option><option value="CUSTOMER">{{ 'Customer job (billed)' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Priority' | translate }}</label>
            <select class="form-select" name="pr" [(ngModel)]="f.priority">
              <option value="LOW">{{ 'Low' | translate }}</option><option value="NORMAL">{{ 'Normal' | translate }}</option><option value="HIGH">{{ 'High' | translate }}</option><option value="URGENT">{{ 'Urgent' | translate }}</option>
            </select></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="f.type === 'CUSTOMER'"><label class="form-label">{{ 'Customer' | translate }}</label>
            <select class="form-select" name="cu" [(ngModel)]="f.customerId">
              <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select></div>
          <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="assets.length"><label class="form-label">{{ 'Asset (optional)' | translate }}</label>
            <select class="form-select" name="as" [(ngModel)]="f.assetId">
              <option [ngValue]="null">{{ 'None' | translate }}</option>
              <option *ngFor="let a of assets" [ngValue]="a.id">{{ a.code }} — {{ a.name }}</option>
            </select></div>
          <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="packages.length"><label class="form-label">{{ 'Service package (checklist and parts)' | translate }}</label>
            <select class="form-select" name="pk" [(ngModel)]="f.packageId">
              <option [ngValue]="null">{{ 'None' | translate }}</option>
              <option *ngFor="let p of packages" [ngValue]="p.id">{{ p.name }}</option>
            </select></div>
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Assigned to' | translate }}</label>
            <select class="form-select" name="au" [(ngModel)]="f.assignedToUserId">
              <option [ngValue]="null">{{ 'Nobody yet' | translate }}</option>
              <option *ngFor="let s of staff" [ngValue]="s.id">{{ s.fullName }}</option>
            </select></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;" *ngIf="f.type === 'CUSTOMER'">
          <div class="form-group"><label class="form-label">{{ 'Fixed price (optional)' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="fp" [(ngModel)]="f.fixedPrice" /></div>
          <div class="form-group"><label class="form-label">{{ 'Hourly rate (optional)' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="lr" [(ngModel)]="f.labourRate" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'Description' | translate }}</label><textarea class="form-control" rows="2" name="de" [(ngModel)]="f.description"></textarea></div>
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">{{ 'What is being worked on (optional)' | translate }}</label><input class="form-control" name="it" [(ngModel)]="f.itemDescription" /></div>
        </div>
        <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Repeat every' | translate }}</label>
            <div class="flex gap-2">
              <input class="form-control" type="number" min="1" max="120" name="ic" style="width:5rem;" [(ngModel)]="f.intervalCount" />
              <select class="form-select" name="iu" [(ngModel)]="f.intervalUnit">
                <option value="DAYS">{{ 'day(s)' | translate }}</option><option value="WEEKS">{{ 'week(s)' | translate }}</option><option value="MONTHS">{{ 'month(s)' | translate }}</option>
              </select>
            </div></div>
          <div class="form-group"><label class="form-label">{{ 'Next one is due' | translate }}</label><app-date-picker name="nd" [(ngModel)]="f.nextDueDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Create the job this many days early' | translate }}</label><input class="form-control" type="number" min="0" max="365" name="ld" style="width:6rem;" [(ngModel)]="f.leadDays" /></div>
          <div class="form-group"><label class="form-label">{{ 'Job is due after (days)' | translate }}</label><input class="form-control" type="number" min="0" max="365" name="da" style="width:6rem;" [(ngModel)]="f.dueAfterDays" /></div>
        </div>
        <label style="display:flex; gap:0.4rem; align-items:center;" class="mb-1"><input type="checkbox" name="so" [(ngModel)]="f.skipIfOpen" /> {{ 'Do not create a new job while the last one is still open' | translate }}</label>
        <label style="display:flex; gap:0.4rem; align-items:center;" class="mb-2"><input type="checkbox" name="ac" [(ngModel)]="f.active" /> {{ 'Active' | translate }}</label>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Save schedule' | translate }}</button>
          <button class="btn btn-outline-secondary" type="button" (click)="f = null">{{ 'Cancel' | translate }}</button>
        </div>
      </form>

      <table class="table-sm" *ngIf="list.length">
        <thead><tr><th>{{ 'Schedule' | translate }}</th><th>{{ 'For' | translate }}</th><th>{{ 'Repeats' | translate }}</th><th>{{ 'Next due' | translate }}</th><th>{{ 'Assigned to' | translate }}</th><th>{{ 'Last job' | translate }}</th><th class="text-end">{{ 'Made' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let s of list" [attr.data-id]="s.id">
            <td>{{ s.title }}</td>
            <td>{{ s.type === 'CUSTOMER' ? (s.customerName || '—') : (('Internal' | translate) + (s.assetName ? ' · ' + s.assetName : '')) }}</td>
            <td>{{ s.every }}</td>
            <td>{{ s.nextDueDate }} <span class="badge badge-warning" *ngIf="s.dueNow && s.active">{{ 'Due now' | translate }}</span></td>
            <td>{{ s.assignedToName || '—' }}</td>
            <td><a *ngIf="s.lastWorkOrderId" [routerLink]="['/admin/work-orders', s.lastWorkOrderId]">{{ s.lastWorkOrderNumber }}</a><span *ngIf="!s.lastWorkOrderId" class="text-muted">—</span>
              <span class="text-muted" *ngIf="s.lastWorkOrderStatus"> ({{ s.lastWorkOrderStatus.replace('_', ' ').toLowerCase() }})</span></td>
            <td class="text-end">{{ s.timesGenerated }}</td>
            <td><span class="badge" [class.badge-success]="s.active" [class.badge-secondary]="!s.active">{{ s.active ? ('Active' | translate) : ('Paused' | translate) }}</span></td>
            <td style="white-space:nowrap;">
              <button class="btn btn-sm btn-outline-primary" type="button" [disabled]="busy" (click)="run(s)">{{ 'Run now' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" [disabled]="busy" (click)="toggle(s)" style="margin-left:0.25rem;">{{ s.active ? ('Pause' | translate) : ('Resume' | translate) }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" (click)="startEdit(s)" style="margin-left:0.25rem;">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" type="button" [disabled]="busy" (click)="remove(s)" style="margin-left:0.25rem;">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="loaded && !list.length">{{ 'No planned maintenance yet. Add a schedule and the jobs are created for you when they fall due (a check runs every morning).' | translate }}</p>
    </app-shell>
  `,
})
export class MaintenanceScheduleListComponent implements OnInit {
  list: MaintenanceSchedule[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;
  notice: string | null = null;
  f: SchedulePayload | null = null;
  editId: number | null = null;

  customers: Customer[] = [];
  staff: StaffOption[] = [];
  assets: FixedAsset[] = [];
  packages: PackageSummary[] = [];

  constructor(
    private api: WorkOrderApiService, private customerApi: CustomerApiService, private assetApi: AssetApiService, private timeApi: TimeApiService,
    private modules: ModuleApiService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !x.walkIn)), error: () => {} });
    this.timeApi.staff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    this.api.packages(true).subscribe({ next: (p) => (this.packages = p), error: () => {} });
    if (this.modules.on('ASSETS')) this.assetApi.list().subscribe({ next: (a) => (this.assets = a), error: () => {} });
  }

  load(): void {
    this.api.schedules().subscribe({
      next: (l) => { this.list = l; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the schedules'; },
    });
  }

  private fail(e: any, fallback: string): void {
    this.busy = false;
    this.notice = null;
    this.error = e?.error?.message ?? fallback;
  }

  startNew(): void {
    this.editId = null;
    this.error = null;
    this.f = {
      title: '', description: '', itemDescription: '', type: 'INTERNAL', priority: 'NORMAL', customerId: null, assetId: null, packageId: null, assignedToUserId: null,
      fixedPrice: null, labourRate: null, intervalUnit: 'MONTHS', intervalCount: 1, nextDueDate: null, leadDays: 0, dueAfterDays: 7, skipIfOpen: true, active: true,
    };
  }

  startEdit(s: MaintenanceSchedule): void {
    this.editId = s.id;
    this.error = null;
    this.f = {
      title: s.title, description: s.description, itemDescription: s.itemDescription, type: s.type, priority: s.priority, customerId: s.customerId, assetId: s.assetId,
      packageId: s.packageId, assignedToUserId: s.assignedToUserId, fixedPrice: s.fixedPrice, labourRate: s.labourRate, branchId: s.branchId,
      intervalUnit: s.intervalUnit, intervalCount: s.intervalCount, nextDueDate: s.nextDueDate, leadDays: s.leadDays, dueAfterDays: s.dueAfterDays,
      skipIfOpen: s.skipIfOpen, active: s.active,
    };
  }

  save(): void {
    if (!this.f) return;
    this.busy = true;
    this.error = null;
    const p: SchedulePayload = { ...this.f, intervalCount: Number(this.f.intervalCount), leadDays: Number(this.f.leadDays) || 0, dueAfterDays: Number(this.f.dueAfterDays) || 0 };
    const call = this.editId ? this.api.updateSchedule(this.editId, p) : this.api.createSchedule(p);
    call.subscribe({
      next: () => { this.busy = false; this.f = null; this.notice = 'Schedule saved.'; this.load(); },
      error: (e) => this.fail(e, 'Could not save the schedule'),
    });
  }

  toggle(s: MaintenanceSchedule): void {
    this.busy = true;
    (s.active ? this.api.pauseSchedule(s.id) : this.api.resumeSchedule(s.id)).subscribe({
      next: () => { this.busy = false; this.error = null; this.load(); },
      error: (e) => this.fail(e, 'Could not change the schedule'),
    });
  }

  remove(s: MaintenanceSchedule): void {
    if (!confirm('Delete the schedule "' + s.title + '"? Jobs it already made are kept.')) return;
    this.busy = true;
    this.api.deleteSchedule(s.id).subscribe({
      next: () => { this.busy = false; this.error = null; this.notice = 'Schedule deleted.'; this.load(); },
      error: (e) => this.fail(e, 'Could not delete the schedule'),
    });
  }

  run(s: MaintenanceSchedule): void {
    this.busy = true;
    this.api.runSchedule(s.id).subscribe({
      next: (r) => { this.busy = false; this.error = null; this.notice = this.describe(r); this.load(); },
      error: (e) => this.fail(e, 'Could not create the job'),
    });
  }

  runDue(): void {
    this.busy = true;
    this.api.runDueSchedules().subscribe({
      next: (r) => { this.busy = false; this.error = null; this.notice = this.describe(r); this.load(); },
      error: (e) => this.fail(e, 'Could not run the schedules'),
    });
  }

  private describe(r: { created: number; skipped: number; numbers: string[]; notes: string[] }): string {
    const parts: string[] = [];
    parts.push(r.created ? 'Created ' + r.numbers.join(', ') + '.' : 'No new jobs were due.');
    if (r.skipped) parts.push(r.skipped + ' skipped (' + r.notes.join('; ') + ').');
    return parts.join(' ');
  }
}
