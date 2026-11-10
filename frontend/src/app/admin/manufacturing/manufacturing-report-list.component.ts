import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { REPORT_DEFS } from '../../core/report-defs';

/** Manufacturing > Reports — a picker over the REPORT_DEFS entries with `section: 'Manufacturing'`. */
@Component({
  selector: 'app-manufacturing-report-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Manufacturing Reports' | translate }}</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary mf-report-link" [routerLink]="['/admin/reports', r.key]">{{ r.title }}</a>
      </div>
    </app-shell>
  `,
})
export class ManufacturingReportListComponent {
  reports = REPORT_DEFS.filter((r) => r.section === 'Manufacturing');
}
