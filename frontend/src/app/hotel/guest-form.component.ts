import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Guest, GuestPayload, HotelApiService } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

import { T } from '../core/i18n/t';
/** Add or edit a guest: contact and ID details, date of birth, preferences, and the VIP / blacklist flags. */
@Component({
  selector: 'app-guest-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <form class="card guest-form" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="save()">
      <h4 style="font-size:1rem;" class="mb-2">{{ guest ? ('Edit guest' | translate) : ('New guest' | translate) }}</h4>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-3" style="flex-wrap:wrap;">
        <div class="form-group"><label class="form-label">{{ 'Full name' | translate }}</label><input class="form-control g-name" name="fullName" [(ngModel)]="form.fullName" required /></div>
        <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control g-phone" name="phone" [(ngModel)]="form.phone" /></div>
        <div class="form-group"><label class="form-label">{{ 'Email' | translate }}</label><input class="form-control g-email" type="email" name="email" [(ngModel)]="form.email" /></div>
        <div class="form-group"><label class="form-label">{{ 'Nationality' | translate }}</label><input class="form-control g-nat" name="nat" [(ngModel)]="form.nationality" /></div>
        <div class="form-group"><label class="form-label">{{ 'ID type' | translate }}</label>
          <select class="form-control g-idtype" name="idType" [(ngModel)]="form.idType">
            <option [ngValue]="null">—</option><option>{{ 'National ID' | translate }}</option><option>{{ 'Passport' | translate }}</option><option>{{ 'Driving licence' | translate }}</option><option>{{ 'Other' | translate }}</option>
          </select></div>
        <div class="form-group"><label class="form-label">{{ 'ID number' | translate }}</label><input class="form-control g-idnumber" name="idNumber" [(ngModel)]="form.idNumber" /></div>
        <div class="form-group"><label class="form-label">{{ 'Date of birth' | translate }}</label><input class="form-control g-dob" type="date" name="dob" [(ngModel)]="form.dateOfBirth" /></div>
        <div class="form-group"><label class="form-label">{{ 'Profession' | translate }}</label><input class="form-control g-profession" name="profession" [(ngModel)]="form.profession" /></div>
      </div>
      <div class="form-group"><label class="form-label">{{ 'Address' | translate }}</label><input class="form-control g-address" name="address" [(ngModel)]="form.address" /></div>
      <div class="form-group"><label class="form-label">{{ 'Preferences' | translate }}</label><input class="form-control g-prefs" name="prefs" [(ngModel)]="form.preferences" [placeholder]="'High floor, extra pillow, late arrival, vegetarian...' | translate" /></div>
      <div class="form-group"><label class="form-label">{{ 'Notes' | translate }}</label><input class="form-control g-notes" name="notes" [(ngModel)]="form.notes" [placeholder]="'Anything else the team should know' | translate" /></div>
      <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
        <label><input type="checkbox" class="g-vip" name="vip" [(ngModel)]="vip" /> {{ 'VIP guest' | translate }}</label>
        <label><input type="checkbox" class="g-black" name="black" [(ngModel)]="blacklisted" /> {{ 'Blacklisted (no bookings or check-in)' | translate }}</label>
        <input class="form-control g-reason" *ngIf="blacklisted" style="max-width:360px;" name="reason" [(ngModel)]="reason" [placeholder]="'Why? (required)' | translate" />
      </div>
      <div class="flex gap-2 mt-2">
        <button class="btn btn-primary save-guest" type="submit" [disabled]="busy">{{ 'Save' | translate }}</button>
        <button class="btn btn-link cancel-guest" type="button" (click)="cancelled.emit()">{{ 'Cancel' | translate }}</button>
      </div>
    </form>
  `,
})
export class GuestFormComponent implements OnChanges {
  @Input() guest: Guest | null = null;
  @Output() saved = new EventEmitter<Guest>();
  @Output() cancelled = new EventEmitter<void>();
  form: GuestPayload = this.blank();
  vip = false;
  blacklisted = false;
  reason = '';
  error: string | null = null;
  busy = false;

  constructor(private api: HotelApiService) {}

  private blank(): GuestPayload {
    return { fullName: '', phone: null, email: null, nationality: null, idType: null, idNumber: null, address: null, notes: null, preferences: null, dateOfBirth: null, profession: null };
  }

  ngOnChanges(): void {
    const g = this.guest;
    this.error = null;
    if (!g) {
      this.form = this.blank();
      this.vip = false;
      this.blacklisted = false;
      this.reason = '';
    } else {
      this.form = { fullName: g.fullName, phone: g.phone, email: g.email, nationality: g.nationality, idType: g.idType, idNumber: g.idNumber, address: g.address, notes: g.notes, preferences: g.preferences, dateOfBirth: g.dateOfBirth, profession: g.profession };
      this.vip = g.vip;
      this.blacklisted = g.blacklisted;
      this.reason = g.blacklistReason ?? '';
    }
  }

  save(): void {
    this.error = null;
    if (this.blacklisted && !this.reason.trim()) {
      this.error = T('Say why this guest is being blacklisted.');
      return;
    }
    this.busy = true;
    const payload: GuestPayload = { ...this.form, dateOfBirth: this.form.dateOfBirth || null, vip: this.vip, blacklisted: this.blacklisted, blacklistReason: this.blacklisted ? this.reason.trim() : null };
    this.api.saveGuest(this.guest ? this.guest.id : null, payload).subscribe({
      next: (g) => { this.busy = false; this.saved.emit(g); },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Failed to save guest'); },
    });
  }
}
