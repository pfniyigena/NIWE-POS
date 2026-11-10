import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { Product } from '../../core/models';
import { TimeApiService, TimeSettings } from '../../core/api/time-api.service';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { addDays, today } from '../../core/time-util';

/** Time Tracking > Settings (admin): approval, timer rounding, default rate, daily limits and the product billed time is invoiced under. */
@Component({
  selector: 'app-time-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, DatePickerComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Time tracking settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>
      <form class="card" style="padding:1rem; max-width:36rem;" *ngIf="s" (ngSubmit)="save()">
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ra" [(ngModel)]="s.requireApproval" />
          {{ 'A manager must approve submitted time (switch off to approve everything automatically)' | translate }}</label></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Round stopped timers up to' | translate }}</label>
            <select class="form-select" name="rd" [(ngModel)]="s.timerRoundingMinutes">
              <option [ngValue]="0">{{ 'Exact minutes' | translate }}</option><option [ngValue]="5">{{ '5 minutes' | translate }}</option><option [ngValue]="6">{{ '6 minutes (tenth of an hour)' | translate }}</option>
              <option [ngValue]="10">{{ '10 minutes' | translate }}</option><option [ngValue]="15">{{ '15 minutes' | translate }}</option><option [ngValue]="30">{{ '30 minutes' | translate }}</option><option [ngValue]="60">{{ '1 hour' | translate }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Default hourly rate' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="dr" [(ngModel)]="s.defaultHourlyRate" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Most hours one person can log in a day' | translate }}</label><input class="form-control" type="number" min="1" max="24" name="mh" [(ngModel)]="s.maxHoursPerDay" /></div>
          <div class="form-group"><label class="form-label">{{ 'Working hours in a day (for utilisation)' | translate }}</label><input class="form-control" type="number" min="1" max="24" step="0.5" name="dc" [(ngModel)]="s.dailyCapacityHours" /></div>
        </div>
        <h5 class="mt-3">{{ 'Keeping timesheets tidy' | translate }}</h5>
        <div class="form-group"><label class="form-label">{{ 'Stop a timer that has been running for more than (hours)' | translate }}</label>
          <input class="form-control" style="max-width:10rem;" type="number" min="0" max="24" name="as" [(ngModel)]="s.timerAutoStopHours" />
          <div class="text-muted" style="font-size:0.8rem;">{{ 'A forgotten timer is stopped automatically at this many hours and kept as a draft for the person to check. 0 = never stop it.' | translate }}</div>
          <button class="btn btn-outline-secondary btn-sm mt-1" type="button" (click)="stopNow()">{{ 'Stop forgotten timers now' | translate }}</button></div>
        <div class="form-group"><label class="form-label">{{ 'Lock time up to and including' | translate }}</label>
          <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
            <app-date-picker name="lk" [(ngModel)]="lockDate" [max]="todayIso"></app-date-picker>
            <button class="btn btn-outline-secondary btn-sm" type="button" (click)="lockLastSunday()">{{ 'Last Sunday' | translate }}</button>
            <button class="btn btn-outline-secondary btn-sm" type="button" *ngIf="lockDate" (click)="lockDate = null">{{ 'Remove the lock' | translate }}</button>
          </div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Nobody can add, change or delete time on or before this date, so approved and invoiced weeks stay as they are.' | translate }}</div></div>
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="re" [(ngModel)]="s.reminderEnabled" />
          {{ 'Every week, e-mail people who have not handed in last week\\'s time' | translate }}</label>
          <div class="flex gap-2 align-center mt-1" *ngIf="s.reminderEnabled">
            <span>{{ 'Send on' | translate }}</span>
            <select class="form-select" style="width:auto;" name="rdy" [(ngModel)]="s.reminderDay">
              <option *ngFor="let d of days; let i = index" [ngValue]="i + 1">{{ d }}</option>
            </select></div>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Needs e-mail set up in Settings, and an e-mail address on each person.' | translate }}</div></div>
        <div class="form-group"><label class="form-label">{{ 'Warn when a project has used (% of its budget)' | translate }}</label>
          <input class="form-control" style="max-width:10rem;" type="number" min="0" max="100" name="bw" [(ngModel)]="s.budgetWarnPercent" />
          <div class="text-muted" style="font-size:0.8rem;">{{ '0 = no warning. The warning and the moment a project goes over budget are recorded in the audit log.' | translate }}</div></div>
        <div class="form-group"><label class="form-label">{{ 'Service product billed time is invoiced under' | translate }}</label>
          <select class="form-select" name="bp" [(ngModel)]="s.billingProductId">
            <option [ngValue]="null">{{ 'Create "Professional services (time)" automatically' | translate }}</option>
            <option *ngFor="let p of services" [ngValue]="p.id">{{ p.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Its tax rate and accounting follow the product. The price on each invoice line comes from the hours and rates.' | translate }}</div></div>
        <div><button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button></div>
      </form>
    </app-shell>
  `,
})
export class TimeSettingsComponent implements OnInit {
  s: TimeSettings | null = null;
  services: Product[] = [];
  error: string | null = null;
  saved = false;
  info: string | null = null;
  lockDate: string | null = null;
  todayIso = today();
  days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  constructor(private api: TimeApiService, private catalog: CatalogApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => { this.s = s; this.lockDate = s.lockedThrough; }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
    this.catalog.listAllProducts(null, 0, 500).subscribe({ next: (p) => (this.services = p.content.filter((x) => x.service && x.active)), error: () => {} });
  }

  lockLastSunday(): void {
    const t = today();
    const d = new Date(t + 'T00:00:00');
    const back = d.getDay() === 0 ? 7 : d.getDay();
    this.lockDate = addDays(t, -back);
  }

  stopNow(): void {
    this.error = null;
    this.api.autoStopNow().subscribe({
      next: (r) => { this.saved = false; this.error = null; this.info = r.stopped ? `${r.stopped} forgotten timer${r.stopped === 1 ? '' : 's'} stopped.` : 'No timer has been running too long.'; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not run it'),
    });
  }

  save(): void {
    if (!this.s) return;
    this.saved = false;
    const body: any = { ...this.s, lockedThrough: this.lockDate, clearLock: !this.lockDate };
    this.api.saveSettings(body).subscribe({
      next: (s) => {
        this.s = s;
        this.lockDate = s.lockedThrough;
        this.error = null;
        this.saved = true;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save'),
    });
  }
}
