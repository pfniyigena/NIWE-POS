import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { RestaurantOrderApiService } from '../core/api/restaurant-order-api.service';
import { RestaurantSettings } from '../core/models';

import { T } from '../core/i18n/t';
/** Admin > Restaurant > Settings: the service charge, the discount limit, tips and bill rounding. */
@Component({
  selector: 'app-restaurant-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Restaurant settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved. New orders use these rules; tabs that are already open keep the rules they started with.' | translate }}</div>
      <div *ngIf="s" class="card" style="padding:1rem; max-width:720px;">
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Service charge' | translate }}</h4>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group">
            <label class="form-label">{{ 'Percent of the bill' | translate }}</label>
            <input class="form-control rs-sc-percent" type="number" min="0" max="50" step="0.5" style="width:120px;" [(ngModel)]="s.serviceChargePercent" name="scp" />
          </div>
          <div class="form-group">
            <label class="form-label">{{ 'VAT on the service charge (%)' | translate }}</label>
            <input class="form-control rs-sc-vat" type="number" min="0" max="100" step="0.5" style="width:120px;" [(ngModel)]="s.serviceChargeVatPercent" name="scv" />
          </div>
        </div>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <label><input class="rs-sc-dine" type="checkbox" [(ngModel)]="s.serviceChargeDineIn" name="scd" /> {{ 'Dine-in orders' | translate }}</label>
          <label><input class="rs-sc-take" type="checkbox" [(ngModel)]="s.serviceChargeTakeaway" name="sct" /> {{ 'Takeaway orders' | translate }}</label>
          <label><input class="rs-sc-deliv" type="checkbox" [(ngModel)]="s.serviceChargeDelivery" name="scl" /> {{ 'Delivery orders' | translate }}</label>
        </div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Discounts' | translate }}</h4>
        <div class="form-group mb-3">
          <label class="form-label">{{ 'The most a waiter may give without a manager (percent of the bill)' | translate }}</label>
          <input class="form-control rs-limit" type="number" min="0" max="100" step="0.5" style="width:120px;" [(ngModel)]="s.discountLimitPercent" name="dl" />
          <div class="text-muted" style="font-size:0.85rem;">{{ 'A bigger discount, or waiving the service charge, needs an administrator or someone with Operations manage rights.' | translate }}</div>
        </div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Tips' | translate }}</h4>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap; align-items:flex-end;">
          <label><input class="rs-tips" type="checkbox" [(ngModel)]="s.tipsEnabled" name="te" /> {{ 'Accept tips at checkout' | translate }}</label>
          <div class="form-group">
            <label class="form-label">{{ 'Quick choices (percent, separated by commas)' | translate }}</label>
            <input class="form-control rs-presets" style="width:180px;" [(ngModel)]="s.tipPresets" name="tp" />
          </div>
        </div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Kitchen notes' | translate }}</h4>
        <div class="form-group mb-3">
          <label class="form-label">{{ 'Quick notes the waiter can tap (separated by commas)' | translate }}</label>
          <input class="form-control rs-notes" style="max-width:640px;" [(ngModel)]="s.notePresets" name="np" />
          <div class="text-muted" style="font-size:0.85rem;">{{ 'They appear as buttons next to the note box on the order screen, for example "No onions" or "Well done".' | translate }}</div>
        </div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Kitchen timing' | translate }}</h4>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Usual preparation time (minutes)' | translate }}</label>
            <input class="form-control rs-prep" type="number" min="1" max="240" style="width:130px;" [(ngModel)]="s.defaultPrepMinutes" name="dpm" /></div>
          <div class="form-group"><label class="form-label">{{ 'Turn amber after (percent of that time)' | translate }}</label>
            <input class="form-control rs-warn" type="number" min="10" max="100" style="width:130px;" [(ngModel)]="s.kitchenWarnPercent" name="kwp" /></div>
        </div>
        <div class="text-muted mb-3" style="font-size:0.85rem;">{{ 'Kitchen tickets turn amber when this share of the time has passed and red when it is up. A menu item can have its own time on the Menu screen.' | translate }}</div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Rounding' | translate }}</h4>
        <div class="form-group mb-3">
          <label class="form-label">{{ 'Round the bill to the nearest (0 = no rounding)' | translate }}</label>
          <input class="form-control rs-rounding" type="number" min="0" step="1" style="width:120px;" [(ngModel)]="s.roundingUnit" name="ru" />
          <div class="text-muted" style="font-size:0.85rem;">{{ 'For example 50 or 100 where small coins are not used. The difference is booked to a rounding account.' | translate }}</div>
        </div>
        <h4 style="font-size:1rem;" class="mb-2">{{ 'Table bookings' | translate }}</h4>
        <label class="flex gap-1 mb-1" style="align-items:center;"><input type="checkbox" class="rs-public" [(ngModel)]="s.publicEnabled" name="pubr" /> {{ 'Allow public website' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Guests can then reserve a table themselves at' | translate }} <strong>{{ '/reserve' | translate }}</strong> {{ 'on this site, without signing in. Switch it off to close the page and take its tile off the home page; staff can still take bookings on the Reservations screen.' | translate }}</div>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Opens' | translate }}</label>
            <input class="form-control rs-open" type="time" style="width:130px;" [(ngModel)]="s.openTime" name="otm" /></div>
          <div class="form-group"><label class="form-label">{{ 'Closes' | translate }}</label>
            <input class="form-control rs-close" type="time" style="width:130px;" [(ngModel)]="s.closeTime" name="ctm" /></div>
          <div class="form-group"><label class="form-label">{{ 'Bookable every (minutes)' | translate }}</label>
            <input class="form-control rs-slot" type="number" min="5" max="240" step="5" style="width:120px;" [(ngModel)]="s.slotMinutes" name="slm" /></div>
          <div class="form-group"><label class="form-label">{{ 'A party keeps its table (minutes)' | translate }}</label>
            <input class="form-control rs-turn" type="number" min="15" max="480" step="15" style="width:120px;" [(ngModel)]="s.turnMinutes" name="trn" /></div>
          <div class="form-group"><label class="form-label">{{ 'Guests can book up to (days ahead)' | translate }}</label>
            <input class="form-control rs-ahead" type="number" min="1" max="365" style="width:120px;" [(ngModel)]="s.bookingDaysAhead" name="bda" /></div>
        </div>
        <div class="text-muted mb-3" style="font-size:0.85rem;">{{ 'The online booking page only offers times inside these hours. Staff can still book any time.' | translate }}</div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Deposits, no-shows and reminders' | translate }}</h4>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Deposit per guest (0 = none)' | translate }}</label>
            <input class="form-control rs-dep" type="number" min="0" step="any" style="width:140px;" [(ngModel)]="s.depositPerGuest" name="dpg" /></div>
          <div class="form-group"><label class="form-label">{{ 'Refund if cancelled this many hours ahead' | translate }}</label>
            <input class="form-control rs-refund" type="number" min="0" style="width:120px;" [(ngModel)]="s.depositRefundHours" name="drh" /></div>
          <div class="form-group"><label class="form-label">{{ 'Remind guests (hours ahead, 0 = off)' | translate }}</label>
            <input class="form-control rs-remind" type="number" min="0" style="width:120px;" [(ngModel)]="s.reminderHours" name="rmh" /></div>
          <div class="form-group"><label class="form-label">{{ 'No-show after (minutes late)' | translate }}</label>
            <input class="form-control rs-grace" type="number" min="0" style="width:120px;" [(ngModel)]="s.graceMinutes" name="grm" /></div>
          <div class="form-group"><label class="form-label">{{ 'Stop online booking after (no-shows, 0 = never)' | translate }}</label>
            <input class="form-control rs-block" type="number" min="0" max="20" style="width:120px;" [(ngModel)]="s.noShowBlockCount" name="nsb" /></div>
        </div>
        <div class="form-group mb-3">
          <label class="form-label">{{ 'Reminder message' | translate }}</label>
          <textarea class="form-control rs-template" rows="3" style="max-width:640px;" [(ngModel)]="s.reminderTemplate" name="rtp"></textarea>
          <div class="text-muted" style="font-size:0.85rem;">{{ 'You can use {p0}name{p1}, {p2}party{p3}, {p4}restaurant{p5}, {p6}date{p7}, {p8}time{p9} and {p10}code{p11}. Guests with an e-mail address get it automatically when e-mail is set up.' | translate: { p0: ('{'), p1: ('}'), p2: ('{'), p3: ('}'), p4: ('{'), p5: ('}'), p6: ('{'), p7: ('}'), p8: ('{'), p9: ('}'), p10: ('{'), p11: ('}') } }}</div>
        </div>
        <h4 style="font-size:1rem;" class="mb-2">{{ 'QR menu and online ordering' | translate }}</h4>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="rs-qr" [(ngModel)]="s.qrEnabled" name="qre" /> {{ 'Guests can order from the QR code on their table' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="rs-online" [(ngModel)]="s.onlineEnabled" name="one" /> {{ 'Online ordering page (takeaway and delivery)' | translate }}</label>
        </div>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap;">
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="rs-o-takeaway" [(ngModel)]="s.onlineTakeaway" name="ota" /> {{ 'Takeaway' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="rs-o-delivery" [(ngModel)]="s.onlineDelivery" name="ode" /> {{ 'Delivery' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="rs-o-auto" [(ngModel)]="s.onlineAutoAcceptTables" name="oat" /> {{ 'Accept table orders automatically' | translate }}</label>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="rs-o-paused" [(ngModel)]="s.onlinePaused" name="opa" /> {{ 'Pause all online orders for now' | translate }}</label>
        </div>
        <div class="flex gap-3 mb-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Usual ready time (minutes)' | translate }}</label>
            <input class="form-control rs-o-prep" type="number" min="5" max="480" style="width:120px;" [(ngModel)]="s.onlinePrepMinutes" name="opm" /></div>
          <div class="form-group"><label class="form-label">{{ 'Minimum order (0 = none)' | translate }}</label>
            <input class="form-control rs-o-min" type="number" min="0" style="width:130px;" [(ngModel)]="s.onlineMinOrder" name="omo" /></div>
          <div class="form-group"><label class="form-label">{{ 'Waiting orders per phone number' | translate }}</label>
            <input class="form-control rs-o-maxp" type="number" min="1" max="20" style="width:120px;" [(ngModel)]="s.onlineMaxPending" name="omp" /></div>
          <div class="form-group"><label class="form-label">{{ 'Lapse if unanswered (minutes)' | translate }}</label>
            <input class="form-control rs-o-expire" type="number" min="5" max="240" style="width:120px;" [(ngModel)]="s.onlineExpireMinutes" name="oem" /></div>
        </div>
        <div class="form-group mb-2">
          <label class="form-label">{{ 'Message shown at the top of the menu' | translate }}</label>
          <input class="form-control rs-o-msg" style="max-width:640px;" maxlength="200" [(ngModel)]="s.onlineMessage" name="omg" [placeholder]="'For example: Kitchen closes at 22:00' | translate" />
        </div>
        <div class="text-muted mb-3" style="font-size:0.85rem;">{{ 'Takeaway and delivery follow the opening hours above. Orders from a table QR code are always allowed while the restaurant is switched on. Delivery areas, fees and the QR codes are on the Online orders screen.' | translate }}</div>
        <div><button class="btn btn-primary rs-save" [disabled]="busy" (click)="save()">{{ busy ? ('Saving…' | translate) : ('Save' | translate) }}</button></div>
      </div>
    </app-shell>
  `,
})
export class RestaurantSettingsComponent implements OnInit {
  s: RestaurantSettings | null = null;
  error: string | null = null;
  saved = false;
  busy = false;

  constructor(private api: RestaurantOrderApiService) {}

  ngOnInit(): void {
    this.api.getSettings().subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? T('Failed to load the settings')) });
  }

  save(): void {
    if (!this.s) return;
    this.busy = true;
    this.error = null;
    this.saved = false;
    this.api.saveSettings(this.s).subscribe({
      next: (s) => {
        this.s = s;
        this.busy = false;
        this.saved = true;
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not save the settings');
      },
    });
  }
}
