#!/usr/bin/env python3
"""Idempotent: v1.15.0 wiring (Work Orders: board, calendar, planned maintenance, settings). Usage: wire-v150.py <frontend src/app dir>"""
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
    ("WorkOrderBoardComponent", "import { MyJobsComponent } from './work-orders/my-jobs.component';\n",
     "import { WorkOrderBoardComponent } from './admin/work-orders/work-order-board.component';\n"
     "import { WorkOrderCalendarComponent } from './admin/work-orders/work-order-calendar.component';\n"
     "import { WorkOrderSettingsComponent } from './admin/work-orders/work-order-settings.component';\n"
     "import { MaintenanceScheduleListComponent } from './admin/work-orders/maintenance-schedule-list.component';\n", False),
    ("path: 'admin/work-orders/board'", "  { path: 'admin/work-orders/:id', component: WorkOrderDetailComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/work-orders/board', component: WorkOrderBoardComponent, canActivate: [authGuard, adminGuard] },\n"
     "  { path: 'admin/work-orders/calendar', component: WorkOrderCalendarComponent, canActivate: [authGuard, adminGuard] },\n"
     "  { path: 'admin/work-orders/schedules', component: MaintenanceScheduleListComponent, canActivate: [authGuard, adminGuard] },\n"
     "  { path: 'admin/work-orders/settings', component: WorkOrderSettingsComponent, canActivate: [authGuard, adminGuard] },\n", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/work-orders/board"', '          <a *ngIf="canSee(\'/admin/work-orders/packages\')" class="sidenav-link sidenav-sublink" routerLink="/admin/work-orders/packages"',
     '          <a *ngIf="canSee(\'/admin/work-orders/board\')" class="sidenav-link sidenav-sublink" routerLink="/admin/work-orders/board" routerLinkActive="active"><span class="sidenav-icon">🗂️</span> Board</a>\n'
     '          <a *ngIf="canSee(\'/admin/work-orders/calendar\')" class="sidenav-link sidenav-sublink" routerLink="/admin/work-orders/calendar" routerLinkActive="active"><span class="sidenav-icon">📆</span> Calendar</a>\n'
     '          <a *ngIf="canSee(\'/admin/work-orders/schedules\')" class="sidenav-link sidenav-sublink" routerLink="/admin/work-orders/schedules" routerLinkActive="active"><span class="sidenav-icon">🔁</span> Planned maintenance</a>\n', True),
])
