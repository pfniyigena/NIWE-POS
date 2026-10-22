import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { HotelApiService, OnlineRoomType, OnlineSettings } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

/**
 * Hotel > Online booking: whether the public booking page is open, what it asks of the guest (deposit, how long a room is held), the guest
 * e-mails (confirmation, pre-arrival reminder, thank-you), which room types are offered, and the calendar (iCal) feed of each room type.
 */
@Component({
  selector: 'app-online-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Online booking</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <button *ngFor="let t of tabs" class="btn on-tab" [attr.data-tab]="t.key" [ngClass]="tab === t.key ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = t.key">{{ t.label }}</button>
      </div>

      <ng-container *ngIf="s && tab === 'settings'">
        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <label class="flex gap-2 align-center"><input type="checkbox" class="on-enabled" [(ngModel)]="s.enabled" [disabled]="!canManage" name="en" /> <strong>Guests can book online</strong></label>
          <div class="text-muted" style="font-size:0.85rem;">The public page is at <a class="on-link" [href]="pageUrl" target="_blank" rel="noopener">{{ pageUrl }}</a>. It appears on your home page while this is on.</div>
          <div class="form-group mt-2"><label class="form-label">Welcome text <span class="text-muted">(shown at the top of the page)</span></label>
            <textarea class="form-control on-intro" rows="2" maxlength="1000" [(ngModel)]="s.intro" [disabled]="!canManage" name="in"></textarea></div>
        </div>

        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <h5>Deposit and limits</h5>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">Deposit asked (% of the stay)</label>
              <input class="form-control on-deposit" type="number" min="0" max="100" step="1" [(ngModel)]="s.depositPercent" [disabled]="!canManage" name="dp" style="width:140px;" />
              <div class="text-muted" style="font-size:0.8rem;">0 = the booking is confirmed straight away.</div></div>
            <div class="form-group"><label class="form-label">Hold the room for (hours)</label>
              <input class="form-control on-hold" type="number" min="1" max="720" [(ngModel)]="s.holdHours" [disabled]="!canManage" name="hh" style="width:140px;" />
              <div class="text-muted" style="font-size:0.8rem;">Released if no deposit is recorded in time.</div></div>
            <div class="form-group"><label class="form-label">Longest online stay (nights)</label>
              <input class="form-control on-maxnights" type="number" min="1" max="90" [(ngModel)]="s.maxNights" [disabled]="!canManage" name="mn" style="width:140px;" /></div>
            <div class="form-group"><label class="form-label">Book up to (days ahead)</label>
              <input class="form-control on-days" type="number" min="1" max="730" [(ngModel)]="s.maxDaysAhead" [disabled]="!canManage" name="md" style="width:140px;" /></div>
            <div class="form-group"><label class="form-label">Bookings per phone, per day</label>
              <input class="form-control on-perphone" type="number" min="1" max="20" [(ngModel)]="s.maxPerPhonePerDay" [disabled]="!canManage" name="pp" style="width:140px;" /></div>
          </div>
          <div class="form-group"><label class="form-label">How guests pay the deposit</label>
            <textarea class="form-control on-instructions" rows="2" maxlength="1000" [(ngModel)]="s.paymentInstructions" [disabled]="!canManage" name="pi" placeholder="For example: send the deposit to Mobile Money 0788 000 000 and quote your booking code."></textarea>
            <div class="text-muted" style="font-size:0.8rem;">Record the deposit on the booking (+ Deposit) when it arrives — that ends the hold.</div></div>
        </div>

        <div class="card" style="padding:1rem; margin-bottom:1rem;">
          <h5>E-mails to guests</h5>
          <div class="alert alert-warning" *ngIf="!s.mailReady">E-mail is not set up yet (Settings &gt; E-mail). Nothing can be sent until it is.</div>
          <label class="flex gap-2 align-center"><input type="checkbox" class="on-mail-confirm" [(ngModel)]="s.mailConfirmation" [disabled]="!canManage" name="mc" /> Confirmation for bookings made at the front desk <span class="text-muted">(online bookings are always confirmed at once)</span></label>
          <label class="flex gap-2 align-center mt-1"><input type="checkbox" class="on-mail-pre" [(ngModel)]="s.mailPrearrival" [disabled]="!canManage" name="mp" /> Reminder before arrival, </label>
          <div class="flex gap-2 align-center" style="margin-left:1.6rem;"><input class="form-control on-pre-days" type="number" min="1" max="14" [(ngModel)]="s.mailPrearrivalDays" [disabled]="!canManage" name="pd" style="width:80px;" /> days ahead</div>
          <label class="flex gap-2 align-center mt-1"><input type="checkbox" class="on-mail-thanks" [(ngModel)]="s.mailThanks" [disabled]="!canManage" name="mt" /> Thank-you after check-out</label>
          <div class="form-group mt-2"><label class="form-label">Thank-you message <span class="text-muted">(optional)</span></label>
            <textarea class="form-control on-thanks" rows="2" maxlength="1000" [(ngModel)]="s.thanksMessage" [disabled]="!canManage" name="tm"></textarea></div>
          <div class="form-group"><label class="form-label">Review link <span class="text-muted">(optional, shown in the thank-you)</span></label>
            <input class="form-control on-review" [(ngModel)]="s.reviewUrl" [disabled]="!canManage" name="rv" maxlength="300" placeholder="https://" /></div>
        </div>

        <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="canManage">
          <button class="btn btn-primary on-save" (click)="save()">Save</button>
          <button class="btn btn-outline-secondary on-run-mails" (click)="runMails()" title="Send the confirmations, reminders and thank-yous that are due, now">Send due e-mails now</button>
          <button class="btn btn-outline-secondary on-release" (click)="release()" title="Release online bookings whose deposit did not arrive in time, now">Release unpaid bookings now</button>
        </div>
      </ng-container>

      <ng-container *ngIf="tab === 'types'">
        <p class="text-muted">Choose which room types the public page offers, describe them for guests, and make a calendar address (iCal) for each so a channel manager or another booking site can see when the room type is taken.</p>
        <div class="card on-type-row" style="padding:1rem; margin-bottom:0.8rem;" *ngFor="let t of types" [attr.data-type]="t.name">
          <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
            <strong>{{ t.name }}</strong>
            <label class="flex gap-2 align-center"><input type="checkbox" class="on-visible" [(ngModel)]="t.onlineVisible" [disabled]="!canManage || !t.active" [name]="'v' + t.id" /> Offered online<span class="text-muted" *ngIf="!t.active"> (type not in use)</span></label>
          </div>
          <div class="form-group mt-1"><label class="form-label">Description for guests</label>
            <textarea class="form-control on-desc" rows="2" maxlength="2000" [(ngModel)]="t.description" [disabled]="!canManage" [name]="'d' + t.id"></textarea></div>
          <button class="btn btn-outline-primary btn-sm on-save-type" *ngIf="canManage" (click)="saveType(t)">Save</button>
          <div class="mt-2">
            <div class="text-muted" style="font-size:0.85rem;">Calendar (iCal) address — shows "Reserved" for every booked night, no guest details. Keep it private: anyone with the address can read it.</div>
            <div class="flex gap-2 align-center mt-1" style="flex-wrap:wrap;">
              <input class="form-control on-feed-url" style="flex:1; min-width:240px;" readonly *ngIf="t.icalPath" [value]="origin + t.icalPath" />
              <button class="btn btn-outline-secondary btn-sm on-feed-copy" *ngIf="t.icalPath" (click)="copy(origin + t.icalPath)">Copy</button>
              <button class="btn btn-outline-primary btn-sm on-feed-new" *ngIf="canManage" (click)="newFeed(t)">{{ t.icalPath ? 'Make a new address' : 'Make calendar address' }}</button>
              <button class="btn btn-outline-danger btn-sm on-feed-off" *ngIf="canManage && t.icalPath" (click)="removeFeed(t)">Switch off</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.icalPath">A new address stops the old one working.</div>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class OnlineSettingsComponent implements OnInit {
  tabs = [{ key: 'settings', label: 'Settings' }, { key: 'types', label: 'Room types and calendars' }];
  tab = 'settings';
  s: OnlineSettings | null = null;
  types: OnlineRoomType[] = [];
  error: string | null = null;
  message: string | null = null;
  origin = typeof window !== 'undefined' ? window.location.origin : '';
  pageUrl = this.origin + '/stay';

  constructor(private api: HotelApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.load();
  }

  private flash(msg: string): void {
    this.message = msg;
    this.error = null;
  }

  private fail(e: unknown, fallback: string): void {
    this.message = null;
    this.error = errMsg(e, fallback);
  }

  load(): void {
    this.api.onlineSettings().subscribe({ next: (s) => (this.s = s), error: (e) => this.fail(e, 'Failed to load the settings') });
    this.api.onlineRoomTypes().subscribe({ next: (t) => (this.types = t), error: (e) => this.fail(e, 'Failed to load the room types') });
  }

  save(): void {
    if (!this.s) return;
    const { mailReady, ...payload } = this.s;
    this.api.saveOnlineSettings(payload).subscribe({
      next: (s) => { this.s = s; this.flash('Saved.'); },
      error: (e) => this.fail(e, 'Could not save'),
    });
  }

  runMails(): void {
    this.api.sendDueMails().subscribe({
      next: (r) => this.flash(r.sent === 0 ? 'No e-mails were due.' : r.sent + ' e-mail' + (r.sent === 1 ? ' is' : 's are') + ' on the way.'),
      error: (e) => this.fail(e, 'Could not send'),
    });
  }

  release(): void {
    this.api.releaseUnpaid().subscribe({
      next: (r) => this.flash(r.released === 0 ? 'No online booking was waiting past its deadline.' : r.released + ' unpaid booking' + (r.released === 1 ? ' was' : 's were') + ' released.'),
      error: (e) => this.fail(e, 'Could not release'),
    });
  }

  private replace(t: OnlineRoomType): void {
    this.types = this.types.map((x) => (x.id === t.id ? t : x));
  }

  saveType(t: OnlineRoomType): void {
    this.api.updateOnlineRoomType(t.id, { onlineVisible: t.onlineVisible, description: t.description ?? '' }).subscribe({
      next: (x) => { this.replace(x); this.flash(t.name + ' saved.'); },
      error: (e) => this.fail(e, 'Could not save'),
    });
  }

  newFeed(t: OnlineRoomType): void {
    this.api.newFeed(t.id).subscribe({
      next: (x) => { this.replace(x); this.flash('Calendar address ready for ' + t.name + '.'); },
      error: (e) => this.fail(e, 'Could not make the address'),
    });
  }

  removeFeed(t: OnlineRoomType): void {
    if (!confirm('Switch off the calendar address of ' + t.name + '? Anything using it will stop seeing the calendar.')) return;
    this.api.removeFeed(t.id).subscribe({
      next: (x) => { this.replace(x); this.flash('Calendar address switched off.'); },
      error: (e) => this.fail(e, 'Could not switch off'),
    });
  }

  copy(text: string): void {
    try {
      navigator.clipboard.writeText(text).then(() => this.flash('Copied.'), () => this.flash('Select the address and copy it.'));
    } catch {
      this.flash('Select the address and copy it.');
    }
  }
}
