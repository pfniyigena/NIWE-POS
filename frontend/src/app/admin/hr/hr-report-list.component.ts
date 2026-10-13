import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { ModuleApiService, ModuleKey } from '../../core/api/module-api.service';
import { REPORT_DEFS, ReportDef } from '../../core/report-defs';

/** HR > Reports — a picker over the REPORT_DEFS entries with `section: 'HR'`, limited to the active HR modules and what the user may see. */
@Component({
  selector: 'app-hr-report-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">HR Reports</h3>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <a *ngFor="let r of reports" class="btn btn-outline-primary" [routerLink]="['/admin/reports', r.key]">{{ r.title }}</a>
      </div>
      <p class="text-muted" *ngIf="!reports.length">No reports available.</p>
    </app-shell>
  `,
})
export class HrReportListComponent implements OnInit {
  reports: ReportDef[] = [];

  /** Which module each HR report needs, and whether it is salary data. */
  private static readonly NEEDS: Record<string, { module: ModuleKey; payroll?: boolean }> = {
    'employee-directory': { module: 'EMPLOYEES' },
    'headcount-by-department': { module: 'EMPLOYEES' },
    'leave-balances': { module: 'ATTENDANCE' },
    'leave-requests': { module: 'ATTENDANCE' },
    'attendance-summary': { module: 'ATTENDANCE' },
    'contracts-expiring': { module: 'CONTRACTS', payroll: true },
    'payroll-register': { module: 'PAYROLL', payroll: true },
    'payroll-summary': { module: 'PAYROLL', payroll: true },
  };

  constructor(private modules: ModuleApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.modules.whenLoaded().subscribe(() => {
      this.reports = REPORT_DEFS.filter((r) => r.section === 'HR').filter((r) => {
        const need = HrReportListComponent.NEEDS[r.key];
        return !need || (this.modules.on(need.module) && (!need.payroll || this.auth.canView('PAYROLL')));
      });
    });
  }
}
