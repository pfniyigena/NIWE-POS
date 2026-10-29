#!/usr/bin/env python3
"""Idempotent: v1.14.0 wiring (Admin > Sign-in log and Admin > Server logs). Usage: wire-v140.py <frontend src/app dir>"""
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
    ("LoginLogComponent", "import { AuditLogListComponent } from './admin/audit-log-list.component';\n",
     "import { LoginLogComponent } from './admin/login-log.component';\nimport { ServerLogsComponent } from './admin/server-logs.component';\n", False),
    ("path: 'admin/login-log'", "  { path: 'admin/audit-log', component: AuditLogListComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/login-log', component: LoginLogComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/server-logs', component: ServerLogsComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/login-log"', "            <!-- Fourier: \"no backup/restore or bulk import-export anywhere in the backend\"",
     "            <!-- v1.14.0: sign-in log and server log viewer -->\n"
     "            <a *ngIf=\"canSee('/admin/login-log')\" class=\"sidenav-link sidenav-sublink\" routerLink=\"/admin/login-log\" routerLinkActive=\"active\"\n"
     "              ><span class=\"sidenav-icon\">🔑</span> Sign-in log</a\n            >\n"
     "            <a *ngIf=\"canSee('/admin/server-logs')\" class=\"sidenav-link sidenav-sublink\" routerLink=\"/admin/server-logs\" routerLinkActive=\"active\"\n"
     "              ><span class=\"sidenav-icon\">📜</span> Server logs</a\n            >\n", True),
])
