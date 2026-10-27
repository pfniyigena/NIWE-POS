#!/usr/bin/env python3
"""Idempotent: v1.12.0 wiring (Devices page: thermal printers and COM customer display). Usage: wire-v120.py <frontend src/app dir>"""
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
    ("DevicesComponent", "import { PosSettingsComponent } from './admin/pos-settings.component';\n",
     "import { DevicesComponent } from './pos/devices.component';\n", False),
    ("path: 'devices'", "  { path: '**', redirectTo: 'registers' },",
     "  { path: 'devices', component: DevicesComponent, canActivate: [authGuard] },\n", True),
])
patch('core/access.ts', [
    ("prefix: 'devices'", "  { prefix: 'registers', areas: ['SALES'] },\n",
     "  { prefix: 'devices', areas: ['SALES', 'OPERATIONS'] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/devices"', "          <!-- POS Reports (Task: \"Add POS Report...\")",
     "          <a *ngIf=\"canSee('/devices')\" class=\"sidenav-link\" routerLink=\"/devices\" routerLinkActive=\"active\"\n            ><span class=\"sidenav-icon\">🖨️</span> Devices</a\n          >\n", True),
])
