import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { REPORT_DEFS } from '../core/report-defs';

/** Subscriptions > Reports — a picker over the REPORT_DEFS entries with `section: 'Subscriptions'`. */
@Component({
  selector: 'app-subscription-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Subscription Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary" [routerLink]="['/admin/reports', r.key]">{{ r.title }}</a>
      </div>
    </app-shell>
  `,
})
export class SubscriptionReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Subscriptions');
}
