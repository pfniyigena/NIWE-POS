import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { Product } from '../../core/models';
import { TimeApiService, TimeSettings } from '../../core/api/time-api.service';

/** Time Tracking > Settings (admin): approval, timer rounding, default rate, daily limits and the product billed time is invoiced under. */
@Component({
  selector: 'app-time-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Time tracking settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form class="card" style="padding:1rem; max-width:36rem;" *ngIf="s" (ngSubmit)="save()">
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ra" [(ngModel)]="s.requireApproval" />
          A manager must approve submitted time (switch off to approve everything automatically)</label></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Round stopped timers up to</label>
            <select class="form-select" name="rd" [(ngModel)]="s.timerRoundingMinutes">
              <option [ngValue]="0">Exact minutes</option><option [ngValue]="5">5 minutes</option><option [ngValue]="6">6 minutes (tenth of an hour)</option>
              <option [ngValue]="10">10 minutes</option><option [ngValue]="15">15 minutes</option><option [ngValue]="30">30 minutes</option><option [ngValue]="60">1 hour</option>
            </select></div>
          <div class="form-group"><label class="form-label">Default hourly rate</label><input class="form-control" type="number" min="0" step="0.01" name="dr" [(ngModel)]="s.defaultHourlyRate" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Most hours one person can log in a day</label><input class="form-control" type="number" min="1" max="24" name="mh" [(ngModel)]="s.maxHoursPerDay" /></div>
          <div class="form-group"><label class="form-label">Working hours in a day (for utilisation)</label><input class="form-control" type="number" min="1" max="24" step="0.5" name="dc" [(ngModel)]="s.dailyCapacityHours" /></div>
        </div>
        <div class="form-group"><label class="form-label">Service product billed time is invoiced under</label>
          <select class="form-select" name="bp" [(ngModel)]="s.billingProductId">
            <option [ngValue]="null">Create "Professional services (time)" automatically</option>
            <option *ngFor="let p of services" [ngValue]="p.id">{{ p.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem;">Its tax rate and accounting follow the product. The price on each invoice line comes from the hours and rates.</div></div>
        <div><button class="btn btn-primary" type="submit">Save</button></div>
      </form>
    </app-shell>
  `,
})
export class TimeSettingsComponent implements OnInit {
  s: TimeSettings | null = null;
  services: Product[] = [];
  error: string | null = null;
  saved = false;

  constructor(private api: TimeApiService, private catalog: CatalogApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
    this.catalog.listAllProducts(null, 0, 500).subscribe({ next: (p) => (this.services = p.content.filter((x) => x.service && x.active)), error: () => {} });
  }

  save(): void {
    if (!this.s) return;
    this.saved = false;
    this.api.saveSettings(this.s).subscribe({
      next: (s) => {
        this.s = s;
        this.error = null;
        this.saved = true;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save'),
    });
  }
}
