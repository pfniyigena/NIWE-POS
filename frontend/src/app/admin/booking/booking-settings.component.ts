import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { BookingApiService, BookingSettings } from '../../core/api/booking-api.service';

const DAYS = [
  { key: 'MON', label: 'Mon' }, { key: 'TUE', label: 'Tue' }, { key: 'WED', label: 'Wed' }, { key: 'THU', label: 'Thu' },
  { key: 'FRI', label: 'Fri' }, { key: 'SAT', label: 'Sat' }, { key: 'SUN', label: 'Sun' },
];

/** Booking > Settings (admin): opening hours, slot rules, payment hold, and the public page wording. */
@Component({
  selector: 'app-booking-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Booking settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form class="card" style="padding:1rem; max-width:44rem;" *ngIf="s" (ngSubmit)="save()">
        <strong>Opening hours</strong>
        <div class="flex gap-2 mt-2">
          <div class="form-group mb-2"><label class="form-label">Opens</label><input class="form-control" type="time" name="ds" [(ngModel)]="s.dayStart" required /></div>
          <div class="form-group mb-2"><label class="form-label">Closes</label><input class="form-control" type="time" name="de" [(ngModel)]="s.dayEnd" required /></div>
          <div class="form-group mb-2"><label class="form-label">Time between start times (min)</label><input class="form-control" type="number" min="5" max="240" name="st" [(ngModel)]="s.slotStepMinutes" /></div>
        </div>
        <div class="form-group mb-3"><label class="form-label">Working days</label>
          <div class="flex gap-2"><label *ngFor="let d of days" style="display:flex; gap:0.3rem; align-items:center;">
            <input type="checkbox" [checked]="has(d.key)" (change)="toggle(d.key)" [name]="'wd' + d.key" /> {{ d.label }}</label></div></div>

        <strong>Booking rules</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">Earliest booking (minutes ahead)</label><input class="form-control" type="number" min="0" name="mn" [(ngModel)]="s.minNoticeMinutes" /></div>
          <div class="form-group mb-2"><label class="form-label">Furthest ahead (days)</label><input class="form-control" type="number" min="1" name="ma" [(ngModel)]="s.maxAdvanceDays" /></div>
          <div class="form-group mb-2"><label class="form-label">Hold for payment (minutes)</label><input class="form-control" type="number" min="5" name="hm" [(ngModel)]="s.holdMinutes" /></div>
          <div class="form-group mb-2"><label class="form-label">Online cancel until (hours before)</label><input class="form-control" type="number" min="0" name="cn" [(ngModel)]="s.cancelNoticeHours" /></div>
        </div>

        <strong class="mt-2">Public website</strong>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mt-2 mb-2"><input type="checkbox" class="bk-public" name="pe" [(ngModel)]="s.publicEnabled" /> Allow public website</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">Guests can then pick a service and a time themselves at <strong>/book</strong> on this site, without signing in. Switch it off to close the page and take its tile off the home page; staff still book appointments as usual.</div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="ec" [(ngModel)]="s.emailConfirmations" /> Email a confirmation when the guest gives an email address</label>
        <div class="form-group mb-2"><label class="form-label">Bookings per phone number per day</label><input class="form-control" style="width:8rem;" type="number" min="1" max="50" name="pp" [(ngModel)]="s.maxPublicPerPhonePerDay" /></div>
        <div class="form-group mb-2"><label class="form-label">Page title</label><input class="form-control" name="pt" [(ngModel)]="title" /></div>
        <div class="form-group mb-2"><label class="form-label">Intro text</label><textarea class="form-control" rows="2" name="pi" [(ngModel)]="intro"></textarea></div>
        <div class="form-group mb-3"><label class="form-label">How to pay <span class="text-muted">(shown to guests who owe a deposit — for example mobile money number or bank details)</span></label>
          <textarea class="form-control" rows="3" name="pay" [(ngModel)]="pay"></textarea></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : 'Save settings' }}</button></div>
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
    this.api.settings().subscribe({ next: (s) => this.apply(s), error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
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
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the settings'; },
    });
  }
}
