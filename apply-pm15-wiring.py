#!/usr/bin/env python3
"""Phase PM-15 wiring that lives in files too big to ship whole:
  1. frontend/src/app/app.routes.ts          - the Deposits page and the Inspection page
  2. frontend/src/app/shared/shell.component.ts - the Deposits link in the Property Management menu
  3. frontend/src/app/core/report-defs.ts    - the Property Management deposits report now shows what is held, kept and refunded
Usage (from the repo root):  python3 apply-pm15-wiring.py
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


# 1. routes
path, src = rw("frontend/src/app/app.routes.ts")
if "PmDepositListComponent" in src:
    print("routes: already applied")
else:
    imp = "import { PmArrearsComponent } from './admin/propertymgmt/arrears.component';\n"
    if imp not in src:
        sys.exit("routes: could not find the arrears import to add after")
    src = src.replace(imp, imp + "import { PmDepositListComponent } from './admin/propertymgmt/pm-deposit-list.component';\nimport { PmInspectionDetailComponent } from './admin/propertymgmt/pm-inspection-detail.component';\n", 1)
    route = "  { path: 'admin/propertymgmt/arrears', component: PmArrearsComponent, canActivate: [authGuard, adminGuard] },\n"
    if route not in src:
        sys.exit("routes: could not find the arrears route to add after")
    src = src.replace(route, route + "  { path: 'admin/propertymgmt/deposits', component: PmDepositListComponent, canActivate: [authGuard, adminGuard] },\n"
                      "  { path: 'admin/propertymgmt/inspections/:id', component: PmInspectionDetailComponent, canActivate: [authGuard, adminGuard] },\n", 1)
    save(path, src)
    print("routes: added")

# 2. menu
path, src = rw("frontend/src/app/shared/shell.component.ts")
if "/admin/propertymgmt/deposits" in src:
    print("menu: already applied")
else:
    link = '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/propertymgmt/arrears" routerLinkActive="active"><span class="sidenav-icon">⏰</span> Arrears</a>\n'
    if link not in src:
        sys.exit("menu: could not find the Arrears link to add after")
    src = src.replace(link, link + '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/propertymgmt/deposits" routerLinkActive="active"><span class="sidenav-icon">🔐</span> Deposits</a>\n', 1)
    save(path, src)
    print("menu: added")

# 3. report
path, src = rw("frontend/src/app/core/report-defs.ts")
_block = re.search(r"  \{\n    key: 'pm-deposits',.*?\n  \},\n", src, re.S)
if _block and "key: 'held'" in _block.group(0):
    print("report: already applied")
else:
    NEW = """  {
    key: 'pm-deposits',
    title: 'Property Management security deposits',
    section: 'Property Management',
    endpoint: 'pm-deposits',
    categoryFilter: false,
    filterMode: 'none',
    openOnlyToggle: 'Only deposits that need action: to collect, or to settle now the lease is over',
    columns: [
      { key: 'propertyName', label: 'Property', format: 'text' },
      { key: 'unitNo', label: 'Unit', format: 'text' },
      { key: 'ownerName', label: 'Owner', format: 'text' },
      { key: 'tenantName', label: 'Tenant', format: 'text' },
      { key: 'leaseCode', label: 'Lease', format: 'text' },
      { key: 'endDate', label: 'End', format: 'text' },
      { key: 'daysToEnd', label: 'Days left', format: 'text' },
      { key: 'agreed', label: 'Agreed', format: 'money' },
      { key: 'paidIn', label: 'Paid in', format: 'money' },
      { key: 'deducted', label: 'Kept', format: 'money' },
      { key: 'refunded', label: 'Refunded', format: 'money' },
      { key: 'held', label: 'Held', format: 'money' },
      { key: 'state', label: 'State', format: 'text' },
    ],
  },
"""
    m = re.search(r"  \{\n    key: 'pm-deposits',.*?\n  \},\n", src, re.S)
    if not m:
        sys.exit("report: could not find the pm-deposits report")
    src = src[:m.start()] + NEW + src[m.end():]
    save(path, src)
    print("report: replaced")
