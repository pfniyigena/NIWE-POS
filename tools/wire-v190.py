#!/usr/bin/env python3
"""Idempotent: v1.19.0 wiring (Subscriptions: Overview and Reports pages). Usage: wire-v190.py <frontend src/app dir>"""
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
    ("SubscriptionOverviewComponent", "import { InstallmentReportListComponent } from './admin/installments/installment-report-list.component';\n",
     "import { SubscriptionOverviewComponent } from './admin/subscription-overview.component';\nimport { SubscriptionReportListComponent } from './admin/subscription-report-list.component';\n", False),
    ("path: 'admin/subscriptions/overview'", "  { path: 'admin/subscriptions/job-settings', component: SubscriptionJobSettingsComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/subscriptions/overview', component: SubscriptionOverviewComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/subscriptions/reports', component: SubscriptionReportListComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/subscriptions/overview"', '            <a *ngIf="canSee(\'/admin/subscriptions/job-settings\')" class="sidenav-link sidenav-sublink" routerLink="/admin/subscriptions/job-settings"',
     '            <a *ngIf="canSee(\'/admin/subscriptions/overview\')" class="sidenav-link sidenav-sublink" routerLink="/admin/subscriptions/overview" routerLinkActive="active"><span class="sidenav-icon">📊</span> Overview</a>\n            <a *ngIf="canSee(\'/admin/subscriptions/reports\')" class="sidenav-link sidenav-sublink" routerLink="/admin/subscriptions/reports" routerLinkActive="active"><span class="sidenav-icon">📑</span> Reports</a>\n', True),
])
