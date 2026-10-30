#!/usr/bin/env python3
"""Idempotent: v1.18.0 wiring (Installments: Collections page). Usage: wire-v180.py <frontend src/app dir>"""
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
    ("InstallmentCollectionsComponent", "import { InstallmentPlanListComponent } from './admin/installments/plan-list.component';\n",
     "import { InstallmentCollectionsComponent } from './admin/installments/collections.component';\n", False),
    ("path: 'admin/installments/collections'", "  { path: 'admin/installments/new', component: InstallmentPlanFormComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/installments/collections', component: InstallmentCollectionsComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/installments/collections"', '            <a *ngIf="canSee(\'/admin/installments/new\')" class="sidenav-link sidenav-sublink" routerLink="/admin/installments/new" routerLinkActive="active"\n',
     '            <a *ngIf="canSee(\'/admin/installments/collections\')" class="sidenav-link sidenav-sublink" routerLink="/admin/installments/collections" routerLinkActive="active"\n              ><span class="sidenav-icon">📞</span> Collections</a\n            >\n', True),
])
