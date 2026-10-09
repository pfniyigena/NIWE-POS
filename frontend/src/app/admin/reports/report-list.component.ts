import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/**
 * Admin > Reports (Task: "Add POS Report where it will show the list of the following reports
 * and by click on each report it will open report will different filters and export as
 * EXCEL,PDF"). Just a picker grouped exactly the way the task listed them — POS Sales-Transactions,
 * then POS Profit — each item linking to its own report at /admin/reports/:key
 * (ReportViewerComponent), which is where the filters/table/export actually live.
 */
@Component({
  selector: 'app-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">POS Reports</h3>
      <div *ngFor="let section of sections" class="mb-4">
        <h5 class="mb-2">{{ section }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a
            *ngFor="let r of bySection(section)"
            class="btn btn-outline-primary"
            [routerLink]="['/admin/reports', r.key]"
          >
            {{ r.title }}
          </a>
        </div>
      </div>
    </app-shell>
  `,
})
export class ReportListComponent {
  reports = REPORT_DEFS;
  // Task 1 added the 'Inventory' section, but Task 2: "Move Inventory Report as submenu to
  // Inventory Menu" moved it off this general picker entirely — it's only reached from Admin >
  // Inventory > Reports now (InventoryReportListComponent), not listed here too. The section
  // stays in report-defs.ts itself (ReportViewerComponent still looks reports up there by key
  // regardless of which picker links to them); this array just controls what renders on *this* page.
  sections = ['POS Sales-Transactions', 'POS Profit', 'POS Credit'] as const;

  bySection(section: string) {
    return this.reports.filter((r) => r.section === section);
  }
}
