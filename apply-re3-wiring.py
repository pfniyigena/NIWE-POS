#!/usr/bin/env python3
"""Phase RE-3 wiring that lives in files too big to ship whole:
  1. frontend/src/app/app.routes.ts             - the My follow-ups page
  2. frontend/src/app/shared/shell.component.ts - the My follow-ups link in the Real Estate menu
Usage (from the repo root):  python3 apply-re3-wiring.py
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


# 1. routes
path, src = rw("frontend/src/app/app.routes.ts")
if "ReFollowUpsComponent" in src:
    print("routes: already applied")
else:
    imp = "import { LeadListComponent } from './admin/realestate/lead-list.component';\n"
    if imp not in src:
        sys.exit("routes: could not find the lead list import")
    src = src.replace(imp, imp + "import { ReFollowUpsComponent } from './admin/realestate/re-follow-ups.component';\n", 1)
    route = "  { path: 'admin/realestate/leads', component: LeadListComponent, canActivate: [authGuard, adminGuard] },\n"
    if route not in src:
        sys.exit("routes: could not find the leads route to add after")
    src = src.replace(route, route + "  { path: 'admin/realestate/follow-ups', component: ReFollowUpsComponent, canActivate: [authGuard, adminGuard] },\n", 1)
    save(path, src)
    print("routes: added")

# 2. menu
path, src = rw("frontend/src/app/shared/shell.component.ts")
if "/admin/realestate/follow-ups" in src:
    print("menu: already applied")
else:
    link = '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/realestate/leads" routerLinkActive="active"><span class="sidenav-icon">🙋</span> Leads</a>\n'
    if link not in src:
        sys.exit("menu: could not find the Leads link to add after")
    src = src.replace(link, link + '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/realestate/follow-ups" routerLinkActive="active"><span class="sidenav-icon">📞</span> My follow-ups</a>\n', 1)
    save(path, src)
    print("menu: added")
