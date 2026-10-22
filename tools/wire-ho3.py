#!/usr/bin/env python3
"""Idempotent: route + menu entry for Hotel > Company accounts. Usage: wire-ho3.py <frontend src/app dir>"""
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
patch('app.routes.ts', [
    ("CompanyAccountsComponent }", "import { HotelReportComponent } from './hotel/hotel-report.component';\n",
     "import { CompanyAccountsComponent } from './hotel/company-accounts.component';\n", False),
    ("path: 'admin/hotel/companies'", "  { path: 'admin/hotel/reports', component: HotelReportComponent,",
     "  { path: 'admin/hotel/companies', component: CompanyAccountsComponent, canActivate: [authGuard, adminGuard] },\n", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/hotel/companies"',
     """              <a *ngIf="canSee('/admin/hotel/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/reports\"""",
     """              <a *ngIf="canSee('/admin/hotel/companies')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/companies" routerLinkActive="active"><span class="sidenav-icon">🏢</span> Company accounts</a>
""", True),
])
