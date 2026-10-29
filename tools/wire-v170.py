#!/usr/bin/env python3
"""Idempotent: v1.17.0 wiring (Booking: working hours and leave page). Usage: wire-v170.py <frontend src/app dir>"""
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
    ("BookingStaffSchedulesComponent", "import { BookingResourcesComponent } from './admin/booking/resources.component';\n",
     "import { BookingStaffSchedulesComponent } from './admin/booking/staff-schedules.component';\n", False),
    ("path: 'admin/booking/schedules'", "  { path: 'admin/booking/resources', component: BookingResourcesComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/booking/schedules', component: BookingStaffSchedulesComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/booking/schedules"', '          <a *ngIf="canSee(\'/admin/booking/reports\')" class="sidenav-link sidenav-sublink" routerLink="/admin/booking/reports"',
     '          <a *ngIf="canSee(\'/admin/booking/schedules\')" class="sidenav-link sidenav-sublink" routerLink="/admin/booking/schedules" routerLinkActive="active"><span class="sidenav-icon">🕘</span> Working hours</a>\n', True),
])
