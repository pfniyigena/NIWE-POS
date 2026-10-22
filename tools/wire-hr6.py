#!/usr/bin/env python3
"""Idempotent: adds the My HR page (route, menu entry, sidebar group). Usage: wire-hr6.py <frontend src/app dir>"""
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
            print('ANCHOR MISSING', rel, anchor[:70]); sys.exit(1)
        s = s.replace(anchor, (add + anchor) if before else (anchor + add), 1)
        changed = True
    if changed:
        open(p, 'w', encoding='utf-8').write(s)
        print('patched', rel)
    else:
        print('already', rel)

patch('app.routes.ts', [
    ("MyHrComponent }", "import { LeaveSettingsComponent } from './admin/hr/leave-settings.component';\n",
     "import { MyHrComponent } from './my/my-hr.component';\n", False),
    ("path: 'my/hr'", "  { path: 'profile', component: MyProfileComponent, canActivate: [authGuard] },\n",
     "  { path: 'my/hr', component: MyHrComponent, canActivate: [authGuard] },\n", False),
])

patch('shared/shell.component.ts', [
    ('routerLink="/my/hr"',
     """        <!-- Sidebar groups are now an accordion""",
     """        <!-- My HR (employee self-service): every signed-in user, shown when the Employees module is on. -->
        <a *ngIf="modules.on('EMPLOYEES')" class="sidenav-link sidenav-link-row" routerLink="/my/hr" routerLinkActive="active"
          ><span class="sidenav-icon">🧑‍💼</span><span>My HR</span></a
        >
""", True),
])
