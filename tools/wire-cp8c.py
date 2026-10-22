#!/usr/bin/env python3
"""Adds the Sales > Gift cards and Sales > Loyalty points routes, menu entries, access rules and sidenav group prefixes. Safe to run twice. Usage: python3 wire-cp8c.py <frontend/src/app folder>"""
import sys, os
root = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app"
def edit(rel, fn):
    p = os.path.join(root, rel); s = open(p, encoding="utf-8").read(); n = fn(s)
    if n != s: open(p, "w", encoding="utf-8").write(n); print("wired", rel)
    else: print("already wired", rel)
def routes(s):
    if "'./admin/giftcards/gift-cards.component'" in s: return s
    imp = "import { PromotionsComponent } from './admin/promotions/promotions.component';"
    assert imp in s, "promotions import not found (run wire-cp8b.py first)"
    s = s.replace(imp, imp + "\nimport { GiftCardsComponent } from './admin/giftcards/gift-cards.component';\nimport { LoyaltyComponent } from './admin/loyalty/loyalty.component';", 1)
    route = "  { path: 'admin/promotions', component: PromotionsComponent, canActivate: [authGuard, adminGuard] },"
    assert route in s, "promotions route not found"
    return s.replace(route, route + "\n  { path: 'admin/gift-cards', component: GiftCardsComponent, canActivate: [authGuard, adminGuard] },\n  { path: 'admin/loyalty', component: LoyaltyComponent, canActivate: [authGuard, adminGuard] },", 1)
def shell(s):
    if "/admin/gift-cards" in s: return s
    key = '''<a *ngIf="canSee('/admin/promotions')"'''
    assert key in s, "promotions menu link not found (run wire-cp8b.py first)"
    i = s.index(key); j = s.index("</a", i); j = s.index(">", j) + 1
    return s[:j] + '''
          <a *ngIf="canSee('/admin/gift-cards')" class="sidenav-link sidenav-sublink" routerLink="/admin/gift-cards" routerLinkActive="active"><span class="sidenav-icon">🎫</span> Gift cards</a>
          <a *ngIf="canSee('/admin/loyalty')" class="sidenav-link sidenav-sublink" routerLink="/admin/loyalty" routerLinkActive="active"><span class="sidenav-icon">⭐</span> Loyalty points</a>''' + s[j:]
def access(s):
    if "admin/gift-cards" in s: return s
    k = "  { prefix: 'admin/promotions', areas: ['SALES'] },"
    assert k in s, "promotions access rule not found (run wire-cp8b.py first)"
    return s.replace(k, k + "\n  { prefix: 'admin/gift-cards', areas: ['SALES'] },\n  { prefix: 'admin/loyalty', areas: ['SALES'] },", 1)
def sidenav(s):
    if "'/admin/gift-cards'" in s: return s
    k = "'/admin/promotions']"
    assert k in s, "sales sidenav prefixes not found (run wire-cp8b.py first)"
    return s.replace(k, "'/admin/promotions', '/admin/gift-cards', '/admin/loyalty']", 1)
edit("app.routes.ts", routes); edit("shared/shell.component.ts", shell); edit("core/access.ts", access); edit("core/sidenav-state.service.ts", sidenav)
