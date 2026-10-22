#!/usr/bin/env python3
"""Idempotent: routes, menu entries, sidebar prefixes and the 'import' write segment for employee documents, checklists and import. Usage: wire-hr7.py <frontend src/app dir>"""
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
    ("HrDocumentsComponent }", "import { LeaveSettingsComponent } from './admin/hr/leave-settings.component';\n",
     "import { HrDocumentsComponent } from './admin/hr/hr-documents.component';\nimport { HrChecklistsComponent } from './admin/hr/hr-checklists.component';\nimport { EmployeeImportComponent } from './admin/hr/employee-import.component';\n", False),
    ("path: 'admin/hr/employees/import'", "  { path: 'admin/hr/employees/:id', component: EmployeeDetailComponent,",
     "  { path: 'admin/hr/employees/import', component: EmployeeImportComponent, canActivate: [authGuard, adminGuard] },\n"
     "  { path: 'admin/hr/documents', component: HrDocumentsComponent, canActivate: [authGuard, adminGuard] },\n"
     "  { path: 'admin/hr/checklists', component: HrChecklistsComponent, canActivate: [authGuard, adminGuard] },\n", True),
])

patch('shared/shell.component.ts', [
    ('routerLink="/admin/hr/documents"',
     """            <a *ngIf="canSee('/admin/hr/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/reports" routerLinkActive="active\"""",
     """            <a *ngIf="canSee('/admin/hr/documents')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/documents" routerLinkActive="active"
              ><span class="sidenav-icon">🗂️</span> Documents</a
            >
            <a *ngIf="canSee('/admin/hr/checklists')" class="sidenav-link sidenav-sublink" routerLink="/admin/hr/checklists" routerLinkActive="active"
              ><span class="sidenav-icon">✅</span> Onboarding</a
            >
""", True),
])

patch('core/sidenav-state.service.ts', [
    ("'/admin/hr/documents'", "'/admin/hr/employees', '/admin/hr/reports'", ", '/admin/hr/documents', '/admin/hr/checklists'", False),
])

patch('core/access.ts', [
    ("'import'", "'walk-in', 'from-purchase'", ", 'import'", False),
])
