#!/usr/bin/env python3
"""Phase RE-6 wiring that lives in files too big to ship whole:
  1. frontend/src/app/app.routes.ts             - the Collections page
  2. frontend/src/app/shared/shell.component.ts - the Collections link in the Real Estate menu
Usage (from the repo root):  python3 apply-re6-wiring.py
Safe to run twice: each step does nothing when it is already done."""
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."


def rw(rel):
    path = os.path.join(ROOT, rel)
    with open(path, encoding="utf-8") as f:
        return path, f.read()


def save(path, text):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


path, src = rw("frontend/src/app/app.routes.ts")
if "ReCollectionsComponent" in src:
    print("routes: already applied")
else:
    imp = "import { UnitSaleListComponent } from './admin/realestate/unit-sale-list.component';\n"
    if imp not in src:
        sys.exit("routes: could not find the unit sale list import")
    src = src.replace(imp, imp + "import { ReCollectionsComponent } from './admin/realestate/re-collections.component';\n", 1)
    route = "  { path: 'admin/realestate/unit-sales', component: UnitSaleListComponent, canActivate: [authGuard, adminGuard] },\n"
    if route not in src:
        sys.exit("routes: could not find the unit sales route to add after")
    src = src.replace(route, route + "  { path: 'admin/realestate/collections', component: ReCollectionsComponent, canActivate: [authGuard, adminGuard] },\n", 1)
    save(path, src)
    print("routes: added")

path, src = rw("frontend/src/app/shared/shell.component.ts")
if "/admin/realestate/collections" in src:
    print("menu: already applied")
else:
    import re
    m = re.search(r'^( *)<a class="sidenav-link sidenav-sublink" routerLink="/admin/realestate/unit-sales"[^\n]*\n', src, re.M)
    if not m:
        sys.exit("menu: could not find the Unit sales link to add after")
    ind = m.group(1)
    add = ind + '<a class="sidenav-link sidenav-sublink" routerLink="/admin/realestate/collections" routerLinkActive="active"><span class="sidenav-icon">💰</span> Collections</a>\n'
    src = src[:m.end()] + add + src[m.end():]
    save(path, src)
    print("menu: added")
