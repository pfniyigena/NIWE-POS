#!/usr/bin/env python3
"""Idempotent: v1.21.0 wiring (Expenses: Overview, Budgets, Settings and Reports pages). Usage: wire-v210.py <frontend src/app dir>"""
import sys, os
base = sys.argv[1] if len(sys.argv) > 1 else 'src/app'
def patch(rel, pairs):
    p = os.path.join(base, rel); s = open(p, encoding='utf-8').read(); changed = False
    for marker, anchor, add, before in pairs:
        if marker in s: continue
        if anchor not in s: print('ANCHOR MISSING', rel, anchor[:70]); sys.exit(1)
        s = s.replace(anchor, (add + anchor) if before else (anchor + add), 1); changed = True
    if changed: open(p, 'w', encoding='utf-8').write(s); print('patched', rel)

patch('app.routes.ts', [
    ("ExpenseOverviewComponent", "import { ExpenseDetailComponent } from './admin/accounting/expense-detail.component';\n",
     "import { ExpenseOverviewComponent } from './admin/accounting/expense-overview.component';\nimport { ExpenseBudgetsComponent } from './admin/accounting/expense-budgets.component';\nimport { ExpenseSettingsComponent } from './admin/accounting/expense-settings.component';\nimport { ExpenseReportListComponent } from './admin/accounting/expense-report-list.component';\n", False),
    ("path: 'admin/accounting/expenses/overview'", "  { path: 'admin/accounting/expenses/new', component: ExpenseFormComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/accounting/expenses/overview', component: ExpenseOverviewComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/accounting/expenses/budgets', component: ExpenseBudgetsComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/accounting/expenses/settings', component: ExpenseSettingsComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/accounting/expenses/reports', component: ExpenseReportListComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/accounting/expenses/overview"', '            <a *ngIf="canSee(\'/admin/accounting/expenses\')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses" routerLinkActive="active"\n',
     '            <a *ngIf="canSee(\'/admin/accounting/expenses/overview\')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses/overview" routerLinkActive="active"><span class="sidenav-icon">📈</span> Overview</a>\n', True),
    ('routerLink="/admin/accounting/expenses/budgets"', '            <a *ngIf="canSee(\'/admin/accounting/recurring-expenses\')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/recurring-expenses" routerLinkActive="active"\n',
     '            <a *ngIf="canSee(\'/admin/accounting/expenses/budgets\')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses/budgets" routerLinkActive="active"><span class="sidenav-icon">🎯</span> Budgets</a>\n', True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/accounting/expenses/reports"', '          <!-- HR modules (Settings > Modules): Employees,',
     '          <ng-container *ngIf="isOpen(\'expenses\') && showGroup(\'expenses\')">\n            <a *ngIf="canSee(\'/admin/accounting/expenses/reports\')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>\n            <a *ngIf="canSee(\'/admin/accounting/expenses/settings\')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/expenses/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>\n          </ng-container>\n', True),
])

# The plain Expenses link must only light up on its own page, not on Overview/Budgets/...
p = os.path.join(base, 'shared/shell.component.ts'); s = open(p, encoding='utf-8').read()
old = 'routerLink="/admin/accounting/expenses" routerLinkActive="active"\n'
new = 'routerLink="/admin/accounting/expenses" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"\n'
if old in s and new not in s:
    s = s.replace(old, new, 1); open(p, 'w', encoding='utf-8').write(s); print('patched shell exact')
