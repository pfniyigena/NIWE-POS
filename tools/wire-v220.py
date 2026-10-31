#!/usr/bin/env python3
"""Idempotent: v1.22.0 wiring (Assets: Overview, Verification rounds, Settings and Print tags pages). Usage: wire-v220.py <frontend src/app dir>"""
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
    ("AssetOverviewComponent", "import { AssetDetailComponent } from './admin/assets/asset-detail.component';\n",
     "import { AssetOverviewComponent } from './admin/assets/asset-overview.component';\nimport { AssetSettingsComponent } from './admin/assets/asset-settings.component';\nimport { AssetLabelsComponent } from './admin/assets/asset-labels.component';\nimport { AssetVerificationListComponent } from './admin/assets/asset-verification-list.component';\nimport { AssetVerificationDetailComponent } from './admin/assets/asset-verification-detail.component';\n", False),
    ("path: 'admin/assets/overview'", "  { path: 'admin/assets/:id', component: AssetDetailComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/assets/overview', component: AssetOverviewComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/assets/settings', component: AssetSettingsComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/assets/labels', component: AssetLabelsComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/assets/verifications', component: AssetVerificationListComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/assets/verifications/:id', component: AssetVerificationDetailComponent, canActivate: [authGuard, adminGuard] },\n", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/assets/overview"', '            <a *ngIf="canSee(\'/admin/assets/new\')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/new" routerLinkActive="active"\n',
     '            <a *ngIf="canSee(\'/admin/assets/overview\')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/overview" routerLinkActive="active"\n              ><span class="sidenav-icon">📈</span> Overview</a\n            >\n', True),
    ('routerLink="/admin/assets/verifications"', '            <a *ngIf="canSee(\'/admin/assets/reports\')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/reports" routerLinkActive="active"\n',
     '            <a *ngIf="canSee(\'/admin/assets/verifications\')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/verifications" routerLinkActive="active"\n              ><span class="sidenav-icon">✅</span> Verification</a\n            >\n', True),
    ('routerLink="/admin/assets/settings"', '          <!-- Accounting group (Fourier:',
     '          <ng-container *ngIf="isOpen(\'assets\') && showGroup(\'assets\')">\n            <a *ngIf="canSee(\'/admin/assets/settings\')" class="sidenav-link sidenav-sublink" routerLink="/admin/assets/settings" routerLinkActive="active"\n              ><span class="sidenav-icon">⚙️</span> Settings</a\n            >\n          </ng-container>\n', True),
])
