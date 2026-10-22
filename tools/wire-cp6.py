#!/usr/bin/env python3
"""Adds the Accounting > Controls route, menu entry and sidenav group prefix. Safe to run twice. Usage: python3 wire-cp6.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "'./admin/accounting/accounting-controls.component'" in s: return s
    imp = "import { JournalEntryListComponent } from './admin/accounting/journal-entry-list.component';"
    assert imp in s, "journal entry list import not found"
    s = s.replace(imp, imp + "\nimport { AccountingControlsComponent } from './admin/accounting/accounting-controls.component';", 1)
    route = "  { path: 'admin/accounting/journal-entries', component: JournalEntryListComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "journal entries route not found"
    return s.replace(route, route + "\n  { path: 'admin/accounting/controls', component: AccountingControlsComponent, canActivate: [authGuard, adminGuard] },", 1)
def shell(s):
    if "/admin/accounting/controls" in s: return s
    key = '''<a *ngIf="canSee('/admin/accounting/journal-entries')"'''
    i = s.index(key); j = s.index("</a", i); j = s.index(">", j) + 1
    return s[:j] + '''
            <a *ngIf="canSee('/admin/accounting/controls')" class="sidenav-link sidenav-sublink" routerLink="/admin/accounting/controls" routerLinkActive="active"
              ><span class="sidenav-icon">🔒</span> Controls</a
            >''' + s[j:]
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell)
