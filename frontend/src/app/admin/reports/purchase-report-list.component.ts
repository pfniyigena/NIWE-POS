import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/**
 * Purchases > Reports — the purchasing counterpart of SalesReportListComponent: a picker over the REPORT_DEFS entries
 * with `section: 'Purchases'`, each opening the shared ReportViewerComponent (filters, Excel / PDF export).
 */
@Component({
  selector: 'app-purchase-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Purchases Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary" [routerLink]="['/admin/reports', r.key]">{{ r.title }}</a>
        <div class="text-muted" *ngIf="reports.length === 0">No purchases reports yet.</div>
      </div>
    </app-shell>
  `,
})
export class PurchaseReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Purchases');
}
