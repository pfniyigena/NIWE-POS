import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/**
 * Admin > Sales > Reports — mirrors InventoryReportListComponent's own "second, smaller picker"
 * pattern (same REPORT_DEFS data, same shared ReportViewerComponent at `/admin/reports/:key`),
 * just scoped to the new Sales sidenav group instead. Today this only holds the Sales Orders
 * report (`section: 'Sales'` in report-defs.ts); Customer Statement/Customer Payments have their
 * own dedicated screens reached directly from the Sales group, same as they always have.
 */
@Component({
  selector: 'app-sales-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Sales Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a
          *ngFor="let r of reports"
          class="btn btn-outline-primary"
          [routerLink]="['/admin/reports', r.key]"
        >
          {{ r.title }}
        </a>
        <div class="text-muted" *ngIf="reports.length === 0">No sales reports yet.</div>
      </div>
    </app-shell>
  `,
})
export class SalesReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Sales');
}
