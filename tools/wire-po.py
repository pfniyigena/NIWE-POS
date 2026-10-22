#!/usr/bin/env python3
"""Idempotent: POS screens added by PO-2.. (routes, module gating, side menu, access). Usage: wire-po.py <frontend src/app dir>"""
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

IMPORT_ANCHOR = "import { ShiftListComponent } from './admin/shift-list.component';\n"
ROUTE_ANCHOR = "  { path: 'admin/shifts', component: ShiftListComponent, canActivate: [authGuard, adminGuard] },\n"
MODULE_ANCHOR = "  { prefix: 'admin/shifts', key: 'POS' },\n"

# PO-2: cash control
patch('app.routes.ts', [
    ("CashControlComponent }", IMPORT_ANCHOR, "import { CashControlComponent } from './registers/cash-control.component';\nimport { CashMovementListComponent } from './admin/cash-movement-list.component';\nimport { PosSettingsComponent } from './admin/pos-settings.component';\n", False),
    ("path: 'cash-control/:id'", ROUTE_ANCHOR, "  { path: 'cash-control/:id', component: CashControlComponent, canActivate: [authGuard] },\n  { path: 'admin/cash-movements', component: CashMovementListComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/pos-settings', component: PosSettingsComponent, canActivate: [authGuard, adminGuard] },\n", False),
    ("prefix: 'cash-control'", MODULE_ANCHOR, "  { prefix: 'cash-control', key: 'POS' },\n  { prefix: 'admin/cash-movements', key: 'POS' },\n  { prefix: 'admin/pos-settings', key: 'POS' },\n", False),
])
patch('core/access.ts', [
    ("prefix: 'admin/cash-movements'", "  { prefix: 'admin/shifts', areas: ['SALES'] },\n", "  { prefix: 'admin/cash-movements', areas: ['SALES'] },\n  { prefix: 'admin/pos-settings', areas: ['SALES'] },\n", False),
])
patch('core/sidenav-state.service.ts', [
    ("'/admin/cash-movements'", "'/admin/shifts', '/admin/reports'", "'/admin/cash-movements', '/admin/pos-settings', '/cash-control', ", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/cash-movements"',
     """          <!-- POS Reports (Task: "Add POS Report..."): a picker of Sales-Transactions/Profit reports""",
     """          <a *ngIf="canSee('/admin/cash-movements')" class="sidenav-link" routerLink="/admin/cash-movements" routerLinkActive="active"
            ><span class="sidenav-icon">💵</span> Cash movements</a
          >
          <a *ngIf="canSee('/admin/pos-settings')" class="sidenav-link" routerLink="/admin/pos-settings" routerLinkActive="active"
            ><span class="sidenav-icon">⚙️</span> POS settings</a
          >
""", True),
])

# PO-3: X and Z reports
patch('app.routes.ts', [
    ("ShiftXzReportComponent }", IMPORT_ANCHOR, "import { ShiftXzReportComponent } from './registers/shift-xz-report.component';\n", False),
    ("path: 'sessions/:id/x-report'", "  { path: 'sessions/:id/report', component: SessionReportComponent, canActivate: [authGuard] },\n",
     "  { path: 'sessions/:id/x-report', component: ShiftXzReportComponent, data: { kind: 'x' }, canActivate: [authGuard] },\n  { path: 'sessions/:id/z-report', component: ShiftXzReportComponent, data: { kind: 'z' }, canActivate: [authGuard] },\n", False),
])

# PO-4: voids and returns log
patch('app.routes.ts', [
    ("PosLogComponent }", IMPORT_ANCHOR, "import { PosLogComponent } from './admin/pos-log.component';\n", False),
    ("path: 'admin/pos-log'", ROUTE_ANCHOR, "  { path: 'admin/pos-log', component: PosLogComponent, canActivate: [authGuard, adminGuard] },\n", False),
    ("prefix: 'admin/pos-log'", MODULE_ANCHOR, "  { prefix: 'admin/pos-log', key: 'POS' },\n", False),
])
patch('core/access.ts', [
    ("prefix: 'admin/pos-log'", "  { prefix: 'admin/cash-movements', areas: ['SALES'] },\n", "  { prefix: 'admin/pos-log', areas: ['SALES'] },\n", False),
])
patch('core/sidenav-state.service.ts', [
    ("'/admin/pos-log'", "'/admin/cash-movements'", "'/admin/pos-log', ", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/pos-log"',
     """          <a *ngIf="canSee('/admin/cash-movements')" class="sidenav-link" routerLink="/admin/cash-movements\"""",
     """          <a *ngIf="canSee('/admin/pos-log')" class="sidenav-link" routerLink="/admin/pos-log" routerLinkActive="active"
            ><span class="sidenav-icon">↩️</span> Voids &amp; returns</a
          >
""", True),
])
