import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { REPORT_DEFS } from '../core/report-defs';

/**
 * Admin > Restaurant > Reports (Fourier: "Add all reports related to Restaurants Including all
 * type of orders etc") — mirrors SalesReportListComponent/InventoryReportListComponent's own
 * "second, smaller picker" pattern: same REPORT_DEFS data (`section: 'Restaurant'`), same shared
 * target route (`/admin/reports/:key`, handled by the one `ReportViewerComponent` — no new viewer
 * needed), just reached from and scoped to the Restaurant sidenav group instead. Every report
 * here covers every order type (dine-in, takeaway, delivery) together — none of these are
 * dine-in-only — split into four groups purely for readability on this picker screen.
 */
@Component({
  selector: 'app-restaurant-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Restaurant Reports</h3>
      <div *ngFor="let group of groups" class="mb-4">
        <h5 class="mb-2">{{ group.title }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a
            *ngFor="let r of group.reports"
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
export class RestaurantReportListComponent {
  private reports = REPORT_DEFS.filter((r) => r.section === 'Restaurant');

  groups = [
    {
      title: 'Sales & Transactions',
      reports: this.reports.filter(
        (r) => !r.key.includes('profit') && r.key !== 'restaurant-cancelled-orders' && r.key !== 'restaurant-wastage'
      ),
    },
    {
      title: 'Profit',
      reports: this.reports.filter((r) => r.key.includes('profit')),
    },
    {
      title: 'Cancelled Orders',
      reports: this.reports.filter((r) => r.key === 'restaurant-cancelled-orders'),
    },
    {
      title: 'Wastage',
      reports: this.reports.filter((r) => r.key === 'restaurant-wastage'),
    },
  ];
}
