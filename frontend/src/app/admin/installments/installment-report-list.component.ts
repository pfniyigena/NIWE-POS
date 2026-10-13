import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/** Installments > Reports — a picker over the REPORT_DEFS entries with `section: 'Installments'`. */
@Component({
  selector: 'app-installment-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Installment Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary" [routerLink]="['/admin/reports', r.key]">{{ r.title }}</a>
      </div>
    </app-shell>
  `,
})
export class InstallmentReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Installments');
}
