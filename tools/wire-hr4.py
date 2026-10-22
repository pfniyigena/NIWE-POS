#!/usr/bin/env python3
"""HR-4: adds the Payroll > Tax & social security route and menu entry. Safe to run twice. Usage: python3 wire-hr4.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "PayrollRemittancesComponent" in s: return s
    imp = "import { PayrollLoansComponent } from './admin/hr/payroll-loans.component';"
    assert imp in s, "run wire-hr3.py first"
    s = s.replace(imp, imp + "\nimport { PayrollRemittancesComponent } from './admin/hr/payroll-remittances.component';", 1)
    route = "  { path: 'admin/hr/payroll/loans', component: PayrollLoansComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "loans route not found"
    return s.replace(route, route + "\n  { path: 'admin/hr/payroll/remittances', component: PayrollRemittancesComponent, canActivate: [authGuard, adminGuard] },", 1)
def shell(s):
    if "/admin/hr/payroll/remittances" in s: return s
    key = '''<a *ngIf="canSee('/admin/hr/payroll/loans')"'''
    assert key in s, "run wire-hr3.py first"
    i = s.index(key); j = s.index("</a", i); j = s.index(">", j) + 1
    return s[:j] + '''
            <a *ngIf="canSee('/admin/hr/payroll/remittances')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/payroll/remittances" routerLinkActive="active"
              ><span class="sidenav-icon">🏛️</span> Tax & social security</a
            >''' + s[j:]
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell)
