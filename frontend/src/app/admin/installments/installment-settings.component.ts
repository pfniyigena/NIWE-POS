import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { InstallmentApiService, InstallmentSettings } from '../../core/api/installment-api.service';

/** Installments > Settings: reminder timing and the starting values offered on a new plan. */
@Component({
  selector: 'app-installment-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Installment settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form class="card" style="padding:1rem; max-width:34rem;" *ngIf="s" (ngSubmit)="save()">
        <strong class="mb-2">Email reminders</strong>
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="re" [(ngModel)]="s.remindersEnabled" /> Send reminders to customers (needs email set up under Settings > Email)</label></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Remind this many days before a due date</label><input class="form-control" type="number" min="0" max="60" name="db" [(ngModel)]="s.reminderDaysBefore" /></div>
          <div class="form-group"><label class="form-label">Chase overdue instalments every (days)</label><input class="form-control" type="number" min="1" max="90" name="oe" [(ngModel)]="s.overdueReminderEveryDays" /></div>
        </div>
        <strong class="mb-2 mt-2">Starting values for a new plan</strong>
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="wu" [(ngModel)]="s.defaultWholeUnits" /> Whole amounts (no cents) — right for currencies without cents such as RWF and XAF</label></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Yearly interest %</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="ir" [(ngModel)]="s.defaultInterestRate" /></div>
          <div class="form-group"><label class="form-label">Grace days</label><input class="form-control" type="number" min="0" max="90" name="gd" [(ngModel)]="s.defaultGraceDays" /></div>
          <div class="form-group"><label class="form-label">Late fee (fixed)</label><input class="form-control" type="number" min="0" step="0.01" name="lf" [(ngModel)]="s.defaultLateFeeFixed" /></div>
          <div class="form-group"><label class="form-label">Late fee (% of unpaid)</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="lp" [(ngModel)]="s.defaultLateFeePercent" /></div>
        </div>
        <div><button class="btn btn-primary" type="submit">Save</button></div>
      </form>
    </app-shell>
  `,
})
export class InstallmentSettingsComponent implements OnInit {
  s: InstallmentSettings | null = null;
  error: string | null = null;
  saved = false;

  constructor(private api: InstallmentApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
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
