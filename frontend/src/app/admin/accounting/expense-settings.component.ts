import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ExpenseApiService } from '../../core/api/expense-api.service';
import { ExpenseSettings } from '../../core/models';

import { T } from '../../core/i18n/t';
/** Expenses > Settings — who may approve what, and when bills fall due. Administrators only. */
@Component({
  selector: 'app-expense-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Expenses settings' | translate }}</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/accounting/expenses">{{ 'All expenses' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form *ngIf="s" (ngSubmit)="save()">
        <h4 class="mb-2">{{ 'Approval' | translate }}</h4>
        <div class="form-group">
          <label class="form-label">{{ 'Approval limit' | translate }}</label>
          <input class="form-control" type="number" min="0" step="0.01" name="approvalLimit" [(ngModel)]="s.approvalLimit" />
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Expenses above this amount can only be approved by an administrator or someone given "Approve large expenses" on their user. 0 means no limit.' | translate }}</div>
        </div>
        <div class="form-group">
          <label class="form-label"><input type="checkbox" name="blockSelf" [(ngModel)]="s.blockSelfApproval" /> {{ 'Nobody approves, rejects or voids an expense they entered themselves' | translate }}</label>
        </div>
        <div class="form-group">
          <label class="form-label"><input type="checkbox" name="blockBudget" [(ngModel)]="s.blockOverBudget" /> {{ 'Block approval when it would go over the monthly budget' | translate }}</label>
          <div class="text-muted" style="font-size:0.8rem;">Budgets are set under Expenses &gt; Budgets. An administrator can still approve with an override, which is written to the expense history.</div>
        </div>
        <h4 class="mb-2 mt-3">{{ 'Bills' | translate }}</h4>
        <div class="form-group">
          <label class="form-label">{{ 'Days until a bill is due' | translate }}</label>
          <input class="form-control" type="number" min="0" max="365" name="defaultDueDays" [(ngModel)]="s.defaultDueDays" />
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Used when an unpaid bill is entered without a due date.' | translate }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Warn this many days before the due date' | translate }}</label>
          <input class="form-control" type="number" min="0" max="90" name="dueSoonDays" [(ngModel)]="s.dueSoonDays" />
        </div>
        <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
      </form>
    </app-shell>
  `,
})
export class ExpenseSettingsComponent implements OnInit {
  s: ExpenseSettings | null = null;
  error: string | null = null;
  saved = false;

  constructor(private api: ExpenseApiService) {}

  ngOnInit(): void {
    this.api.getSettings().subscribe({ next: (s) => (this.s = s), error: (err) => (this.error = err?.error?.message ?? T('Could not load the settings')) });
  }

  save(): void {
    if (!this.s) return;
    this.error = null;
    this.saved = false;
    this.api.saveSettings(this.s).subscribe({
      next: (s) => {
        this.s = s;
        this.saved = true;
      },
      error: (err) => (this.error = err?.error?.message ?? T('Could not save')),
    });
  }
}
