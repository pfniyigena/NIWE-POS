import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/**
 * Admin > Inventory > Reports (Task 2: "Move Inventory Report as submenu to Inventory Menu").
 * The two Inventory reports (Task 1: stock valuation + stock movement history — later joined by
 * Task 4's Purchase Order report and Task 5's Supplier Statement, all still `section: 'Inventory'`
 * in REPORT_DEFS) used to only be reachable via the general Admin > Reports picker, mixed in
 * with the sales/profit/credit reports that have nothing to do with inventory. This is a second,
 * smaller picker — same REPORT_DEFS data, same target route (`/admin/reports/:key`, handled by
 * the one shared `ReportViewerComponent` — no new viewer needed), just reached from and scoped to
 * the Inventory sidenav group instead. `ReportListComponent`'s own section list no longer
 * includes `'Inventory'` (see its own comment), so these reports show up here only, not on both
 * pickers.
 */
@Component({
  selector: 'app-inventory-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Inventory Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a
          *ngFor="let r of reports"
          class="btn btn-outline-primary"
          [routerLink]="['/admin/reports', r.key]"
        >
          {{ r.title }}
        </a>
        <div class="text-muted" *ngIf="reports.length === 0">No inventory reports yet.</div>
      </div>
    </app-shell>
  `,
})
export class InventoryReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Inventory');
}
