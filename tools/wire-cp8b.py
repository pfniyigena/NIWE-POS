#!/usr/bin/env python3
"""Adds the Sales > Promotions route, menu entry, access rule and sidenav group prefix. Safe to run twice. Usage: python3 wire-cp8b.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "'./admin/promotions/promotions.component'" in s: return s
    imp = "import { CustomerGroupsComponent } from './admin/pricing/customer-groups.component';"
    assert imp in s, "customer groups import not found (run wire-cp8.py first)"
    s = s.replace(imp, imp + "\nimport { PromotionsComponent } from './admin/promotions/promotions.component';", 1)
    route = "  { path: 'admin/pricing/groups', component: CustomerGroupsComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "customer groups route not found"
    return s.replace(route, route + "\n  { path: 'admin/promotions', component: PromotionsComponent, canActivate: [authGuard, adminGuard] },", 1)
def shell(s):
    if "/admin/promotions" in s: return s
    key = '''<a *ngIf="canSee('/admin/pricing/groups')"'''
    assert key in s, "customer groups menu link not found (run wire-cp8.py first)"
    i = s.index(key); j = s.index("</a", i); j = s.index(">", j) + 1
    return s[:j] + '''
          <a *ngIf="canSee('/admin/promotions')" class="sidenav-link sidenav-sublink" routerLink="/admin/promotions" routerLinkActive="active"><span class="sidenav-icon">🎁</span> Promotions</a>''' + s[j:]
def access(s):
    if "admin/promotions" in s: return s
    k = "  { prefix: 'admin/pricing', areas: ['SALES'] },"
    assert k in s, "pricing access rule not found (run wire-cp8.py first)"
    return s.replace(k, k + "\n  { prefix: 'admin/promotions', areas: ['SALES'] },", 1)
def sidenav(s):
    if "'/admin/promotions'" in s: return s
    k = "'/admin/pricing']"
    assert k in s, "sales sidenav prefixes not found (run wire-cp8.py first)"
    return s.replace(k, "'/admin/pricing', '/admin/promotions']", 1)
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell); edit("core/access.ts", access); edit("core/sidenav-state.service.ts", sidenav)
