import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { InstallmentApiService, InstallmentSettings } from '../../core/api/installment-api.service';

import { T } from '../../core/i18n/t';
/** Installments > Settings: reminder timing and the starting values offered on a new plan. */
@Component({
  selector: 'app-installment-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Installment settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form class="card" style="padding:1rem; max-width:44rem;" *ngIf="s" (ngSubmit)="save()">
        <strong class="mb-2">{{ 'Email reminders' | translate }}</strong>
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="re" [(ngModel)]="s.remindersEnabled" /> {{ 'Send reminders to customers (needs email set up under Settings > Email)' | translate }}</label></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Remind this many days before a due date' | translate }}</label><input class="form-control" type="number" min="0" max="60" name="db" [(ngModel)]="s.reminderDaysBefore" /></div>
          <div class="form-group"><label class="form-label">{{ 'Chase overdue instalments every (days)' | translate }}</label><input class="form-control" type="number" min="1" max="90" name="oe" [(ngModel)]="s.overdueReminderEveryDays" /></div>
        </div>
        <strong class="mb-2 mt-2">{{ 'Starting values for a new plan' | translate }}</strong>
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="wu" [(ngModel)]="s.defaultWholeUnits" /> {{ 'Whole amounts (no cents) — right for currencies without cents such as RWF and XAF' | translate }}</label></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Yearly interest %' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="ir" [(ngModel)]="s.defaultInterestRate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Grace days' | translate }}</label><input class="form-control" type="number" min="0" max="90" name="gd" [(ngModel)]="s.defaultGraceDays" /></div>
          <div class="form-group"><label class="form-label">{{ 'Late fee (fixed)' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="lf" [(ngModel)]="s.defaultLateFeeFixed" /></div>
          <div class="form-group"><label class="form-label">{{ 'Late fee (% of unpaid)' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="0.01" name="lp" [(ngModel)]="s.defaultLateFeePercent" /></div>
        </div>
        <strong class="mb-2 mt-2">{{ 'Early payoff and default' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">{{ 'Interest waived when a plan is paid off early (%)' | translate }}</label><input class="form-control" type="number" min="0" max="100" step="1" name="pr" [(ngModel)]="s.payoffRebatePercent" />
            <div class="text-muted" style="font-size:0.75rem;">{{ 'Share of the interest on instalments not yet due that is forgiven. It can be changed for each payoff.' | translate }}</div></div>
          <div class="form-group"><label class="form-label">{{ 'Mark a plan defaulted when an instalment is this many days overdue' | translate }}</label><input class="form-control" type="number" min="0" max="730" name="da" [(ngModel)]="s.defaultAfterDays" />
            <div class="text-muted" style="font-size:0.75rem;">{{ '0 = never automatically; you can still mark a plan defaulted by hand.' | translate }}</div></div>
        </div>
        <strong class="mb-2 mt-2">{{ 'Agreement wording' | translate }}</strong>
        <div class="form-group"><textarea class="form-control" rows="8" name="at" [(ngModel)]="s.agreementTerms" placeholder="One term per line. Leave empty to use the standard wording."></textarea>
          <div class="text-muted" style="font-size:0.75rem;">{{ 'Printed under "Terms" on the agreement PDF. The standard wording is plain and general; have your own text checked by a lawyer if the agreement matters legally.' | translate }}</div></div>
        <div><button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button></div>
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
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? T('Could not load the settings')) });
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
      error: (e) => (this.error = e?.error?.message ?? T('Could not save')),
    });
  }
}
