import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { REPORT_DEFS } from '../core/report-defs';

/**
 * Admin > Help Desk > Reports — Fourier: "Add Helpdesk reports(XLS,PDF)". Same small picker
 * pattern as InventoryReportListComponent/SalesReportListComponent/RestaurantReportListComponent:
 * every entry still links to the one shared `ReportViewerComponent` at `/admin/reports/:key`
 * (same `REPORT_DEFS` table those other pickers read), just reached from and scoped to the Help
 * Desk sidenav group instead of the general Admin > Reports picker.
 */
@Component({
  selector: 'app-helpdesk-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Help Desk Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a
          *ngFor="let r of reports"
          class="btn btn-outline-primary"
          [routerLink]="['/admin/reports', r.key]"
        >
          {{ r.title }}
        </a>
        <div class="text-muted" *ngIf="reports.length === 0">No Help Desk reports yet.</div>
      </div>
    </app-shell>
  `,
})
export class HelpDeskReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Help Desk');
}
