#!/usr/bin/env python3
"""Adds the Collections route, menu entry and access rule. Safe to run twice. Usage: python3 wire-cp3.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "'./admin/collections.component'" in s: return s
    imp = "import { CustomerPaymentListComponent } from './admin/customer-payment-list.component';"
    assert imp in s, "customer payment import not found"
    s = s.replace(imp, imp + "\nimport { CollectionsComponent } from './admin/collections.component';", 1)
    route = "  { path: 'admin/customer-payments', component: CustomerPaymentListComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "customer payments route not found"
    return s.replace(route, route + "\n  { path: 'admin/collections', component: CollectionsComponent, canActivate: [authGuard, adminGuard] },", 1)
def shell(s):
    if "/admin/collections" in s: return s
    key = '''<a *ngIf="canSee('/admin/customer-payments')"'''
    i = s.index(key); j = s.index("</a>", i) + 4
    return s[:j] + '''\n          <a *ngIf="canSee('/admin/collections')" class="sidenav-link sidenav-sublink" routerLink="/admin/collections" routerLinkActive="active"><span class="sidenav-icon">⏰</span> Collections</a>''' + s[j:]
def access(s):
    if "admin/collections" in s: return s
    line = "  { prefix: 'admin/customer-payments', areas: ['SALES', 'BILLING'] },"
    assert line in s, "access line not found"
    return s.replace(line, line + "\n  { prefix: 'admin/collections', areas: ['SALES', 'BILLING'] },", 1)
def sidenav(s):
    if "/admin/collections" in s: return s
    old = "prefixes: ['/admin/customers', '/admin/customer-payments', '/admin/sales'] }"
    assert old in s, "sidenav prefixes not found"
    return s.replace(old, "prefixes: ['/admin/customers', '/admin/customer-payments', '/admin/collections', '/admin/sales'] }", 1)
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell); edit("core/access.ts", access); edit("core/sidenav-state.service.ts", sidenav)
