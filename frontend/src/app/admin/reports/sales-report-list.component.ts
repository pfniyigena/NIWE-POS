import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/**
 * Admin > Sales > Reports — mirrors InventoryReportListComponent's own "second, smaller picker"
 * pattern (same REPORT_DEFS data, same shared ReportViewerComponent at `/admin/reports/:key`),
 * just scoped to the new Sales sidenav group instead. Holds every `section: 'Sales'` report in report-defs.ts: Sales
 * Orders, and the ones built by SalesReportService (by customer / product / category, invoices, credit notes, sale
 * order returns, customer payments, receivables aging). Customer Statement has its own dedicated screen.
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
