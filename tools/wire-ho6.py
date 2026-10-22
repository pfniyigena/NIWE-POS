#!/usr/bin/env python3
"""Idempotent: routes + menu entry for Hotel > Guest register (guest profile, duplicates, register). Usage: wire-ho6.py <frontend src/app dir>"""
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
    ("GuestProfileComponent }", "import { HotelReportComponent } from './hotel/hotel-report.component';\n",
     "import { GuestProfileComponent } from './hotel/guest-profile.component';\nimport { GuestDuplicatesComponent } from './hotel/guest-duplicates.component';\nimport { GuestRegisterComponent } from './hotel/guest-register.component';\n", False),
    ("path: 'admin/hotel/guests/duplicates'", "  { path: 'admin/hotel/guests', component: GuestListComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/hotel/guests/duplicates', component: GuestDuplicatesComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/hotel/guests/:id', component: GuestProfileComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/hotel/guest-register', component: GuestRegisterComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/hotel/guest-register"',
     """              <a *ngIf="canSee('/admin/hotel/guests')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/guests" routerLinkActive="active"><span class="sidenav-icon">🧳</span> Guests</a>
""",
     """              <a *ngIf="canSee('/admin/hotel/guest-register')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/guest-register" routerLinkActive="active"><span class="sidenav-icon">📒</span> Guest register</a>
""", False),
])
