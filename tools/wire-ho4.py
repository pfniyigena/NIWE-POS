#!/usr/bin/env python3
"""Idempotent: routes + menu entry for Hotel > Groups. Usage: wire-ho4.py <frontend src/app dir>"""
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
    ("GroupListComponent }", "import { HotelReportComponent } from './hotel/hotel-report.component';\n",
     "import { GroupListComponent } from './hotel/group-list.component';\nimport { GroupDetailComponent } from './hotel/group-detail.component';\n", False),
    ("path: 'admin/hotel/groups'", "  { path: 'admin/hotel/reports', component: HotelReportComponent,",
     "  { path: 'admin/hotel/groups', component: GroupListComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/hotel/groups/:id', component: GroupDetailComponent, canActivate: [authGuard, adminGuard] },\n", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/hotel/groups"',
     """              <a *ngIf="canSee('/admin/hotel/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/reports\"""",
     """              <a *ngIf="canSee('/admin/hotel/groups')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/groups" routerLinkActive="active"><span class="sidenav-icon">👥</span> Groups</a>
""", True),
])
