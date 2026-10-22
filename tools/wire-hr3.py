#!/usr/bin/env python3
"""HR-3: adds the Payroll > Adjustments and Payroll > Advances & loans routes and menu entries. Safe to run twice. Usage: python3 wire-hr3.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "PayrollAdjustmentsComponent" in s: return s
    imp = "import { PayrollRunComponent } from './admin/hr/payroll-run.component';"
    assert imp in s, "payroll run import not found"
    s = s.replace(imp, imp + "\nimport { PayrollAdjustmentsComponent } from './admin/hr/payroll-adjustments.component';\nimport { PayrollLoansComponent } from './admin/hr/payroll-loans.component';\nimport { StaffLoanComponent } from './admin/hr/staff-loan.component';", 1)
    route = "  { path: 'admin/hr/payroll/runs', component: PayrollRunsComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "payroll runs route not found"
    return s.replace(route, route + "\n  { path: 'admin/hr/payroll/adjustments', component: PayrollAdjustmentsComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/hr/payroll/loans', component: PayrollLoansComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/hr/payroll/loans/:id', component: StaffLoanComponent, canActivate: [authGuard, adminGuard] },", 1)
def shell(s):
    if "/admin/hr/payroll/adjustments" in s: return s
    key = '''<a *ngIf="canSee('/admin/hr/payroll/runs')"'''
    assert key in s, "payroll runs menu link not found"
    i = s.index(key); j = s.index("</a", i); j = s.index(">", j) + 1
    return s[:j] + '''
            <a *ngIf="canSee('/admin/hr/payroll/adjustments')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/payroll/adjustments" routerLinkActive="active"
              ><span class="sidenav-icon">✏️</span> Adjustments</a
            >
            <a *ngIf="canSee('/admin/hr/payroll/loans')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/payroll/loans" routerLinkActive="active"
              ><span class="sidenav-icon">💳</span> Advances & loans</a
            >''' + s[j:]
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell)
