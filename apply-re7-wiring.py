#!/usr/bin/env python3
"""Phase RE-7 wiring that lives in files too big to ship whole:
  1. frontend/src/app/app.routes.ts             - the Commissions page
  2. frontend/src/app/shared/shell.component.ts - the Commissions link in the Real Estate menu
Usage (from the repo root):  python3 apply-re7-wiring.py
Safe to run twice: each step does nothing when it is already done."""
import os
import re
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
if "ReCommissionsComponent" in src:
    print("routes: already applied")
else:
    imp = "import { ReCollectionsComponent } from './admin/realestate/re-collections.component';\n"
    if imp not in src:
        sys.exit("routes: could not find the collections import (apply apply-re6-wiring.py first)")
    src = src.replace(imp, imp + "import { ReCommissionsComponent } from './admin/realestate/re-commissions.component';\n", 1)
    route = "  { path: 'admin/realestate/collections', component: ReCollectionsComponent, canActivate: [authGuard, adminGuard] },\n"
    if route not in src:
        sys.exit("routes: could not find the collections route to add after")
    src = src.replace(route, route + "  { path: 'admin/realestate/commissions', component: ReCommissionsComponent, canActivate: [authGuard, adminGuard] },\n", 1)
    save(path, src)
    print("routes: added")

path, src = rw("frontend/src/app/shared/shell.component.ts")
if "/admin/realestate/commissions" in src:
    print("menu: already applied")
else:
    m = re.search(r'^( *)<a class="sidenav-link sidenav-sublink" routerLink="/admin/realestate/collections"[^\n]*\n', src, re.M)
    if not m:
        sys.exit("menu: could not find the Collections link to add after (apply apply-re6-wiring.py first)")
    ind = m.group(1)
    add = ind + '<a class="sidenav-link sidenav-sublink" routerLink="/admin/realestate/commissions" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Commissions</a>\n'
    src = src[:m.end()] + add + src[m.end():]
    save(path, src)
    print("menu: added")
