#!/usr/bin/env python3
"""Idempotent: Manufacturing screens added by MF-3.. (routes). Usage: wire-mf.py <frontend src/app dir>"""
import sys, os
base = sys.argv[1] if len(sys.argv) > 1 else 'src/app'
def patch(rel, pairs):
    p = os.path.join(base, rel); s = open(p, encoding='utf-8').read(); changed = False
    for marker, anchor, add, before in pairs:
        if marker in s: continue
        if anchor not in s: print('ANCHOR MISSING', rel, anchor[:70]); sys.exit(1)
        s = s.replace(anchor, (add + anchor) if before else (anchor + add), 1); changed = True
    if changed: open(p, 'w', encoding='utf-8').write(s); print('patched', rel)
    else: print('already', rel)

ORDER_NEW = "  { path: 'admin/manufacturing/orders/new', component: ManufacturingOrderFormComponent, canActivate: [authGuard, adminGuard] },\n"
patch('app.routes.ts', [
    ("path: 'admin/manufacturing/orders/:id/edit'", ORDER_NEW,
     "  { path: 'admin/manufacturing/orders/:id/edit', component: ManufacturingOrderFormComponent, canActivate: [authGuard, adminGuard] },\n", False),
])

# MF-5: Manufacturing > Reports
patch('app.routes.ts', [
    ("ManufacturingReportListComponent }", "import { ManufacturingOrderListComponent } from './admin/manufacturing/manufacturing-order-list.component';\n",
     "import { ManufacturingReportListComponent } from './admin/manufacturing/manufacturing-report-list.component';\n", False),
    ("path: 'admin/manufacturing/reports'", ORDER_NEW,
     "  { path: 'admin/manufacturing/reports', component: ManufacturingReportListComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('core/access.ts', [
    ("prefix: 'admin/manufacturing/reports'", "  { prefix: 'admin/manufacturing', areas: ['OPERATIONS'] },\n",
     "  { prefix: 'admin/manufacturing/reports', areas: ['REPORTS', 'OPERATIONS'] },\n", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/manufacturing/reports"',
     """            <a *ngIf="canSee('/admin/manufacturing/overhead-costs')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/overhead-costs" routerLinkActive="active"
              ><span class="sidenav-icon">💰</span> Indirect costs</a
            >
""",
     """            <a *ngIf="canSee('/admin/manufacturing/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/manufacturing/reports" routerLinkActive="active"
              ><span class="sidenav-icon">📊</span> Reports</a
            >
""", False),
])
