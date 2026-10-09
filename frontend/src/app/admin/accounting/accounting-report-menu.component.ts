import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';

/**
 * Admin > Accounting > Report Menu — the single picker screen for all four report groups from
 * Fourier's own list (Accounting Reports, Journals Reports, Expenses Reports, Incomes Reports —
 * 25 menu items in total, after "General Ledger" was added to Journals Reports), grouped by
 * section exactly the way {@code ReportListComponent} groups
 * the POS Reports picker by section (Fourier: "Do the same as we did for POS Report"). Originally
 * split into two sidenav entries — a separate "Accounting Report" picker
 * (AccountingReportListComponent) for the first eight, plus this "Report Menu" for the other
 * 23 — Fourier then asked to fold that one in too ("Move also Accounting Report to the report
 * Menu"), so AccountingReportListComponent and its sidenav link were retired and its eight
 * entries became the "Accounting Reports" section below. Replaces the earlier four separate
 * sidenav accordion groups, which put every one of these links directly in the sidebar instead
 * of behind one screen like this.
 */
@Component({
  selector: 'app-accounting-report-menu',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Reports</h3>
      <div *ngFor="let section of sections" class="mb-4">
        <h5 class="mb-2">{{ section.title }}</h5>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a *ngFor="let r of section.reports" class="btn btn-outline-primary" [routerLink]="r.route">{{ r.title }}</a>
        </div>
      </div>
    </app-shell>
  `,
})
export class AccountingReportMenuComponent {
  sections = [
    {
      title: 'Accounting Reports',
      reports: [
        { title: 'Tax Report', route: '/admin/accounting/reports/tax-report' },
        { title: 'Tax Declaration', route: '/admin/accounting/reports/tax-declaration' },
        { title: 'Income Statement', route: '/admin/accounting/reports/income-statement' },
        { title: 'Balance Sheet', route: '/admin/accounting/reports/balance-sheet' },
        { title: 'Profit & Loss', route: '/admin/accounting/reports/profit-and-loss' },
        { title: 'Financial Transactions', route: '/admin/accounting/reports/financial-transactions' },
        { title: 'Cash Flow Report', route: '/admin/accounting/reports/cash-flow' },
        { title: 'Assets', route: '/admin/accounting/reports/assets' },
      ],
    },
    {
      title: 'Journals Reports',
      reports: [
        { title: 'General Ledger', route: '/admin/accounting/reports/general-ledger' },
        { title: 'Trial Balance & Nets', route: '/admin/accounting/reports/trial-balance-and-nets' },
        { title: 'Trial Balance', route: '/admin/accounting/reports/trial-balance' },
        { title: 'Trial Nets', route: '/admin/accounting/reports/trial-nets' },
        { title: 'Ledger', route: '/admin/accounting/accounts' },
        { title: 'Cost Centers', route: '/admin/accounting/reports/cost-centers' },
        { title: 'Journals Report', route: '/admin/accounting/reports/journals' },
        { title: 'Chart of Accounts Directory', route: '/admin/accounting/accounts' },
      ],
    },
    {
      title: 'Expenses Reports',
      reports: [
        { title: 'Expenses by Category', route: '/admin/accounting/reports/expenses-by-category' },
        { title: 'Expenses by Vendor', route: '/admin/accounting/reports/expenses-by-vendor' },
        { title: 'Expenses by Staff', route: '/admin/accounting/reports/expenses-by-staff' },
        { title: 'Expenses by Client', route: '/admin/accounting/reports/expenses-by-client' },
        { title: 'Daily Expenses', route: '/admin/accounting/reports/expenses-daily' },
        { title: 'Weekly Expenses', route: '/admin/accounting/reports/expenses-weekly' },
        { title: 'Monthly Expenses', route: '/admin/accounting/reports/expenses-monthly' },
        { title: 'Yearly Expenses', route: '/admin/accounting/reports/expenses-yearly' },
      ],
    },
    {
      title: 'Incomes Reports',
      reports: [
        { title: 'Incomes by Category', route: '/admin/accounting/reports/incomes-by-category' },
        { title: 'Incomes by Vendor', route: '/admin/accounting/reports/incomes-by-vendor' },
        { title: 'Incomes by Staff', route: '/admin/accounting/reports/incomes-by-staff' },
        { title: 'Incomes by Client', route: '/admin/accounting/reports/incomes-by-client' },
        { title: 'Daily Incomes', route: '/admin/accounting/reports/incomes-daily' },
        { title: 'Weekly Incomes', route: '/admin/accounting/reports/incomes-weekly' },
        { title: 'Monthly Incomes', route: '/admin/accounting/reports/incomes-monthly' },
        { title: 'Yearly Incomes', route: '/admin/accounting/reports/incomes-yearly' },
      ],
    },
  ];
}
