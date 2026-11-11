import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { BookingApiService, BookingSettings } from '../../core/api/booking-api.service';

import { T } from '../../core/i18n/t';
const DAYS = [
  { key: 'MON', get label() { return T('Mon'); } }, { key: 'TUE', get label() { return T('Tue'); } }, { key: 'WED', get label() { return T('Wed'); } }, { key: 'THU', get label() { return T('Thu'); } },
  { key: 'FRI', get label() { return T('Fri'); } }, { key: 'SAT', get label() { return T('Sat'); } }, { key: 'SUN', get label() { return T('Sun'); } },
];

/** Booking > Settings (admin): opening hours, slot rules, payment hold, and the public page wording. */
@Component({
  selector: 'app-booking-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Booking settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form class="card" style="padding:1rem; max-width:44rem;" *ngIf="s" (ngSubmit)="save()">
        <strong>{{ 'Opening hours' | translate }}</strong>
        <div class="flex gap-2 mt-2">
          <div class="form-group mb-2"><label class="form-label">{{ 'Opens' | translate }}</label><input class="form-control" type="time" name="ds" [(ngModel)]="s.dayStart" required /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Closes' | translate }}</label><input class="form-control" type="time" name="de" [(ngModel)]="s.dayEnd" required /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Time between start times (min)' | translate }}</label><input class="form-control" type="number" min="5" max="240" name="st" [(ngModel)]="s.slotStepMinutes" /></div>
        </div>
        <div class="form-group mb-3"><label class="form-label">{{ 'Working days' | translate }}</label>
          <div class="flex gap-2"><label *ngFor="let d of days" style="display:flex; gap:0.3rem; align-items:center;">
            <input type="checkbox" [checked]="has(d.key)" (change)="toggle(d.key)" [name]="'wd' + d.key" /> {{ d.label }}</label></div></div>

        <strong>{{ 'Booking rules' | translate }}</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">{{ 'Earliest booking (minutes ahead)' | translate }}</label><input class="form-control" type="number" min="0" name="mn" [(ngModel)]="s.minNoticeMinutes" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Furthest ahead (days)' | translate }}</label><input class="form-control" type="number" min="1" name="ma" [(ngModel)]="s.maxAdvanceDays" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Hold for payment (minutes)' | translate }}</label><input class="form-control" type="number" min="5" name="hm" [(ngModel)]="s.holdMinutes" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Online cancel until (hours before)' | translate }}</label><input class="form-control" type="number" min="0" name="cn" [(ngModel)]="s.cancelNoticeHours" /></div>
        </div>

        <strong class="mt-2">{{ 'Reminders and moving a booking' | translate }}</strong>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mt-2 mb-2"><input type="checkbox" id="bk-reminders" name="re" [(ngModel)]="s.reminderEnabled" />
          {{ 'E-mail the guest a reminder before the visit (with a calendar file)' | translate }}</label>
        <div class="flex gap-2 align-center mb-1" *ngIf="s.reminderEnabled">
          <span>{{ 'Send it about' | translate }}</span><input class="form-control" style="width:6rem;" type="number" min="1" max="168" name="rh" [(ngModel)]="s.reminderHours" /><span>{{ 'hours before' | translate }}</span>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Needs e-mail set up in Settings and an e-mail address on the booking. A visit booked less than this many hours ahead is not reminded; you can still send one by hand from the appointment.' | translate }}</div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" id="bk-guest-move" name="gr" [(ngModel)]="s.allowGuestReschedule" /> {{ 'Guests can move their own booking on the public page' | translate }}</label>
        <div class="flex gap-2 align-center mb-3" *ngIf="s.allowGuestReschedule">
          <span>{{ 'At most' | translate }}</span><input class="form-control" style="width:5rem;" type="number" min="0" max="10" name="gm" [(ngModel)]="s.maxGuestReschedules" /><span>{{ 'times, and only until the cancellation notice above' | translate }}</span>
        </div>

        <strong class="mt-2">{{ 'Public website' | translate }}</strong>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mt-2 mb-2"><input type="checkbox" class="bk-public" name="pe" [(ngModel)]="s.publicEnabled" /> {{ 'Allow public website' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Guests can then pick a service and a time themselves at' | translate }} <strong>{{ '/book' | translate }}</strong> {{ 'on this site, without signing in. Switch it off to close the page and take its tile off the home page; staff still book appointments as usual.' | translate }}</div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="ec" [(ngModel)]="s.emailConfirmations" /> {{ 'Email a confirmation when the guest gives an email address' | translate }}</label>
        <div class="form-group mb-2"><label class="form-label">{{ 'Bookings per phone number per day' | translate }}</label><input class="form-control" style="width:8rem;" type="number" min="1" max="50" name="pp" [(ngModel)]="s.maxPublicPerPhonePerDay" /></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Page title' | translate }}</label><input class="form-control" name="pt" [(ngModel)]="title" /></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Intro text' | translate }}</label><textarea class="form-control" rows="2" name="pi" [(ngModel)]="intro"></textarea></div>
        <div class="form-group mb-3"><label class="form-label">{{ 'How to pay' | translate }} <span class="text-muted">{{ '(shown to guests who owe a deposit — for example mobile money number or bank details)' | translate }}</span></label>
          <textarea class="form-control" rows="3" name="pay" [(ngModel)]="pay"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? ('Saving…' | translate) : ('Save settings' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class BookingSettingsComponent implements OnInit {
  days = DAYS;
  s: BookingSettings | null = null;
  title = '';
  intro = '';
  pay = '';
  saving = false;
  saved = false;
  error: string | null = null;

  constructor(private api: BookingApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => this.apply(s), error: (e) => (this.error = e?.error?.message ?? T('Could not load the settings')) });
  }

  private apply(s: BookingSettings): void {
    this.s = { ...s, dayStart: s.dayStart.slice(0, 5), dayEnd: s.dayEnd.slice(0, 5) };
    this.title = s.publicTitle ?? '';
    this.intro = s.publicIntro ?? '';
    this.pay = s.paymentInstructions ?? '';
  }

  private set(): Set<string> {
    return new Set((this.s?.workDays ?? '').split(',').map((x) => x.trim().toUpperCase().slice(0, 3)).filter((x) => x));
  }

  has(k: string): boolean { return this.set().has(k); }

  toggle(k: string): void {
    if (!this.s) return;
    const cur = this.set();
    if (cur.has(k)) cur.delete(k); else cur.add(k);
    this.s.workDays = DAYS.map((d) => d.key).filter((d) => cur.has(d)).join(',');
  }

  save(): void {
    if (!this.s) return;
    this.saving = true;
    this.saved = false;
    this.error = null;
    this.api.updateSettings({ ...this.s, publicTitle: this.title.trim() || null, publicIntro: this.intro.trim() || null, paymentInstructions: this.pay.trim() || null }).subscribe({
      next: (s) => { this.apply(s); this.saving = false; this.saved = true; },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? T('Could not save the settings'); },
    });
  }
}
