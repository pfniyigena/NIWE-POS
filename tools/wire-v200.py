#!/usr/bin/env python3
"""Idempotent: v1.20.0 wiring (Help Desk: Overview and Saved replies pages). Usage: wire-v200.py <frontend src/app dir>"""
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
    ("HelpDeskOverviewComponent", "import { HelpDeskReportListComponent } from './admin/helpdesk-report-list.component';\n",
     "import { HelpDeskOverviewComponent } from './admin/helpdesk-overview.component';\nimport { HelpDeskCannedListComponent } from './admin/helpdesk-canned-list.component';\n", False),
    ("path: 'admin/helpdesk/overview'", "  { path: 'admin/helpdesk/settings', component: HelpDeskSettingsComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/helpdesk/overview', component: HelpDeskOverviewComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/helpdesk/canned', component: HelpDeskCannedListComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/helpdesk/overview"', '            <a *ngIf="canSee(\'/admin/helpdesk/tickets\')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/tickets"',
     '            <a *ngIf="canSee(\'/admin/helpdesk/overview\')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/overview" routerLinkActive="active"><span class="sidenav-icon">📈</span> Overview</a>\n', True),
    ('routerLink="/admin/helpdesk/canned"', '            <a *ngIf="canSee(\'/admin/helpdesk/categories\')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/categories"',
     '            <a *ngIf="canSee(\'/admin/helpdesk/canned\')" class="sidenav-link sidenav-sublink" routerLink="/admin/helpdesk/canned" routerLinkActive="active"><span class="sidenav-icon">💬</span> Saved replies</a>\n', True),
])
patch('core/sidenav-state.service.ts', [
    ("group: 'helpdesk'", "  { group: 'subscriptions', prefixes: ['/admin/subscription'] },\n",
     "  { group: 'helpdesk', prefixes: ['/admin/helpdesk'] },\n", False),
])
