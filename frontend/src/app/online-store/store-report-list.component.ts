import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { REPORT_DEFS } from '../core/report-defs';

/** Online Store > Sales reports: a picker over the REPORT_DEFS entries with `section: 'Online Store'`. A sale counts on the day its order was completed. */
@Component({
  selector: 'app-store-report-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-2">{{ 'Online sales reports' | translate }}</h3>
      <p class="text-muted mb-3">{{ 'An order counts as a sale on the day it was completed, and the money is what its invoice says. Orders that were not completed are listed apart.' | translate }}</p>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary" [routerLink]="['/admin/reports', r.key]">{{ r.title | translate }}</a>
      </div>
    </app-shell>
  `,
})
export class StoreReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Online Store');
}
