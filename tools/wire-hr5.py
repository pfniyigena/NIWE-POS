#!/usr/bin/env python3
"""Idempotent: adds the team leave calendar route and menu entry. Usage: wire-hr5.py <frontend src/app dir>"""
import sys, os
base = sys.argv[1] if len(sys.argv) > 1 else 'src/app'

def patch(rel, pairs):
    p = os.path.join(base, rel)
    s = open(p, encoding='utf-8').read()
    changed = False
    for marker, anchor, add, before in pairs:
        if marker in s:
            continue
        if anchor not in s:
            print('ANCHOR MISSING', rel, anchor[:60]); sys.exit(1)
        s = s.replace(anchor, (add + anchor) if before else (anchor + add), 1)
        changed = True
    if changed:
        open(p, 'w', encoding='utf-8').write(s)
        print('patched', rel)
    else:
        print('already', rel)

patch('app.routes.ts', [
    ("LeaveCalendarComponent }", "import { LeaveSettingsComponent } from './admin/hr/leave-settings.component';\n",
     "import { LeaveCalendarComponent } from './admin/hr/leave-calendar.component';\n", False),
    ("admin/hr/leave-calendar'", "  { path: 'admin/hr/leave-settings', component: LeaveSettingsComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/hr/leave-calendar', component: LeaveCalendarComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/hr/leave-calendar"',
     """            <a *ngIf="canSee('/admin/hr/leave-settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/leave-settings" routerLinkActive="active\"""",
     """            <a *ngIf="canSee('/admin/hr/leave-calendar')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/leave-calendar" routerLinkActive="active"
              ><span class="sidenav-icon">🗓️</span> Team calendar</a
            >
""", True),
])
