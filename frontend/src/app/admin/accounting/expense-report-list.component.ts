import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/** Expenses > Reports — the register, unpaid bills by age, budget vs actual and payment accounts, plus the older category/vendor/period reports under Accounting. */
@Component({
  selector: 'app-expense-report-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Expenses Reports' | translate }}</h3>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary" [routerLink]="['/admin/reports', r.key]">{{ r.title }}</a>
      </div>
      <p class="text-muted">Expenses by category, vendor, staff and period are under Accounting &gt; Reports.</p>
      <a class="btn btn-outline-secondary" routerLink="/admin/accounting/reports/menu">{{ 'Accounting reports' | translate }}</a>
    </app-shell>
  `,
})
export class ExpenseReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Expenses');
}
