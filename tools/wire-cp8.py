#!/usr/bin/env python3
"""Adds the Sales > Price lists and Customer groups routes, menu entries, access rule and sidenav group prefix. Safe to run twice. Usage: python3 wire-cp8.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "'./admin/pricing/price-lists.component'" in s: return s
    imp = "import { CustomerStatementComponent } from './admin/customer-statement.component';"
    assert imp in s, "customer statement import not found"
    s = s.replace(imp, imp + "\nimport { PriceListsComponent } from './admin/pricing/price-lists.component';\nimport { PriceListDetailComponent } from './admin/pricing/price-list-detail.component';\nimport { CustomerGroupsComponent } from './admin/pricing/customer-groups.component';", 1)
    route = "  { path: 'admin/collections', component: CollectionsComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "collections route not found"
    return s.replace(route, route + """
  { path: 'admin/pricing/price-lists', component: PriceListsComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/pricing/price-lists/:id', component: PriceListDetailComponent, canActivate: [authGuard, adminGuard] },
  { path: 'admin/pricing/groups', component: CustomerGroupsComponent, canActivate: [authGuard, adminGuard] },""", 1)
def shell(s):
    if "/admin/pricing/price-lists" in s: return s
    key = '''<a *ngIf="canSee('/admin/sales-orders')"'''
    i = s.index(key); j = s.index("</a", i); j = s.index(">", j) + 1
    return s[:j] + '''
          <a *ngIf="canSee('/admin/pricing/price-lists')" class="sidenav-link sidenav-sublink" routerLink="/admin/pricing/price-lists" routerLinkActive="active"><span class="sidenav-icon">🏷️</span> Price lists</a>
          <a *ngIf="canSee('/admin/pricing/groups')" class="sidenav-link sidenav-sublink" routerLink="/admin/pricing/groups" routerLinkActive="active"><span class="sidenav-icon">👥</span> Customer groups</a>''' + s[j:]
def access(s):
    if "admin/pricing" in s: return s
    k = "  { prefix: 'admin/sales-orders', areas: ['SALES'] },"
    assert k in s, "sales-orders access rule not found"
    return s.replace(k, k + "\n  { prefix: 'admin/pricing', areas: ['SALES'] },", 1)
def sidenav(s):
    if "'/admin/pricing'" in s: return s
    k = "'/admin/collections', '/admin/sales']"
    assert k in s, "sales sidenav prefixes not found"
    return s.replace(k, "'/admin/collections', '/admin/sales', '/admin/pricing']", 1)
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell); edit("core/access.ts", access); edit("core/sidenav-state.service.ts", sidenav)
