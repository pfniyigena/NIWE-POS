import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { HotelApiService, HotelSettings, PostingMode } from '../core/api/hotel-api.service';
import { ModuleApiService } from '../core/api/module-api.service';
import { errMsg } from './hotel-util';

/** Hotel > Settings (admin): switch the module on, choose how room revenue reaches Accounting, tax, times, night audit. */
@Component({
  selector: 'app-hotel-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">Hotel settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <form *ngIf="loaded" (ngSubmit)="save()">
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="enabled" [(ngModel)]="s.enabled" [disabled]="activated" />
            {{ activated ? 'Hotel module is active' : 'Activate the Hotel module' }}
          </label>
          <div class="text-muted" style="font-size:0.8rem;">
            Shows the Hotel menu and adds the "Charge to room" payment method to the POS and the Restaurant. Set up room types and rooms first.
            <span *ngIf="activated">An activated module can't be deactivated.</span>
            <span *ngIf="!activated">Activation is permanent, and also activates the POS and the Restaurant.</span>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">How room revenue reaches Accounting</label>
          <label class="flex gap-2" style="align-items:flex-start; margin-bottom:0.4rem;">
            <input type="radio" name="mode" value="NIGHTLY" [(ngModel)]="s.postingMode" />
            <span><strong>Every night</strong> — each night's room charge is posted as revenue by the night audit. Best for most hotels.</span>
          </label>
          <label class="flex gap-2" style="align-items:flex-start; margin-bottom:0.4rem;">
            <input type="radio" name="mode" value="AT_CHECKOUT" [(ngModel)]="s.postingMode" />
            <span><strong>At check-out</strong> — nothing is posted for the room until the guest leaves, then the whole stay at once.</span>
          </label>
          <label class="flex gap-2" style="align-items:flex-start;">
            <input type="radio" name="mode" value="BOOKING_AND_CHECKOUT" [(ngModel)]="s.postingMode" />
            <span><strong>At booking and check-out</strong> — the booked value is recorded as unearned room revenue when the booking is made, and becomes revenue at check-out.</span>
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.35rem;">
            Deposits, payments and refunds are always posted when the money moves. The mode can only be changed when no booking is open (booked or in-house).
          </div>
        </div>

        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group">
            <label class="form-label">Tax on rooms &amp; services (%)</label>
            <input class="form-control" style="max-width:140px;" type="number" min="0" max="100" step="0.01" name="tax" [(ngModel)]="s.taxPercent" />
          </div>
          <div class="form-group">
            <label class="form-label">Check-in time</label>
            <input class="form-control" style="max-width:140px;" type="time" name="cin" [(ngModel)]="s.checkInTime" />
          </div>
          <div class="form-group">
            <label class="form-label">Check-out time</label>
            <input class="form-control" style="max-width:140px;" type="time" name="cout" [(ngModel)]="s.checkOutTime" />
          </div>
          <div class="form-group">
            <label class="form-label">Night audit runs at</label>
            <input class="form-control" style="max-width:140px;" type="time" name="audit" [(ngModel)]="s.auditTime" />
          </div>
        </div>
        <button class="btn btn-primary" type="submit" [disabled]="saving">Save</button>
      </form>

      <div class="card" style="padding:1rem; margin-top:1.5rem;" *ngIf="loaded && s.enabled">
        <h4 style="font-size:1rem; margin-bottom:0.4rem;">Night audit</h4>
        <p class="text-muted" style="font-size:0.85rem; margin-bottom:0.5rem;">
          Once a day (after the time above) the system charges every in-house guest for the nights that have ended and marks occupied rooms for cleaning.
          Running it again is safe — a night is never charged twice.
        </p>
        <p style="font-size:0.85rem;" *ngIf="s.auditLastRun">Last run {{ s.auditLastRun }} — {{ s.auditLastSummary }}</p>
        <p class="text-muted" style="font-size:0.85rem;" *ngIf="!s.auditLastRun">It has not run yet.</p>
        <button class="btn btn-outline-primary" type="button" (click)="runAudit()" [disabled]="auditing">Run night audit now</button>
      </div>
    </app-shell>
  `,
})
export class HotelSettingsComponent implements OnInit {
  s: HotelSettings = { enabled: false, postingMode: 'NIGHTLY', taxPercent: 0, checkInTime: '14:00', checkOutTime: '12:00', auditTime: '03:00', auditLastRun: null, auditLastSummary: null };
  loaded = false;
  /** Once Hotel is active it stays active — the checkbox is locked. */
  activated = false;
  saving = false;
  auditing = false;
  error: string | null = null;
  message: string | null = null;

  constructor(private api: HotelApiService, private modules: ModuleApiService) {}

  ngOnInit(): void {
    this.api.getSettings().subscribe({
      next: (s) => {
        this.s = s;
        this.activated = s.enabled;
        this.loaded = true;
      },
      error: (e) => (this.error = errMsg(e, 'Failed to load hotel settings')),
    });
  }

  save(): void {
    this.error = this.message = null;
    this.saving = true;
    this.api
      .saveSettings({
        enabled: this.s.enabled,
        postingMode: this.s.postingMode as PostingMode,
        taxPercent: this.s.taxPercent,
        checkInTime: this.s.checkInTime,
        checkOutTime: this.s.checkOutTime,
        auditTime: this.s.auditTime,
      })
      .subscribe({
        next: (s) => {
          this.s = s;
          this.activated = s.enabled;
          this.saving = false;
          this.message = 'Hotel settings saved.';
          // Activating Hotel also activates POS and Restaurant — refresh the module state used by the sidebar.
          this.modules.list().subscribe({ error: () => {} });
        },
        error: (e) => {
          this.saving = false;
          this.error = errMsg(e, 'Failed to save hotel settings');
          this.api.getSettings().subscribe((s) => { this.s = s; this.activated = s.enabled; });
        },
      });
  }

  runAudit(): void {
    this.error = this.message = null;
    this.auditing = true;
    this.api.runNightAudit().subscribe({
      next: (r) => {
        this.auditing = false;
        this.message = r.message;
        this.api.getSettings().subscribe((s) => { this.s = s; this.activated = s.enabled; });
      },
      error: (e) => {
        this.auditing = false;
        this.error = errMsg(e, 'Night audit failed');
      },
    });
  }
}
