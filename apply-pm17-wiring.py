#!/usr/bin/env python3
"""Phase PM-17 wiring that lives in files too big to ship whole:
  1. frontend/src/app/app.routes.ts             - the Charges page (utilities and service charges)
  2. frontend/src/app/shared/shell.component.ts - the Charges link in the Property Management menu
  3. frontend/src/app/core/report-defs.ts       - the new Property Management charges report
Usage (from the repo root):  python3 apply-pm17-wiring.py
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
if "PmChargeTypesComponent" in src:
    print("routes: already applied")
else:
    imp = "import { PmDepositListComponent } from './admin/propertymgmt/pm-deposit-list.component';\n"
    if imp not in src:
        sys.exit("routes: could not find the deposits import (apply apply-pm15-wiring.py first)")
    src = src.replace(imp, imp + "import { PmChargeTypesComponent } from './admin/propertymgmt/pm-charge-types.component';\n", 1)
    route = "  { path: 'admin/propertymgmt/deposits', component: PmDepositListComponent, canActivate: [authGuard, adminGuard] },\n"
    if route not in src:
        sys.exit("routes: could not find the deposits route to add after")
    src = src.replace(route, route + "  { path: 'admin/propertymgmt/charges', component: PmChargeTypesComponent, canActivate: [authGuard, adminGuard] },\n", 1)
    save(path, src)
    print("routes: added")

# 2. menu
path, src = rw("frontend/src/app/shared/shell.component.ts")
if "/admin/propertymgmt/charges" in src:
    print("menu: already applied")
else:
    link = '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/propertymgmt/deposits" routerLinkActive="active"><span class="sidenav-icon">🔐</span> Deposits</a>\n'
    if link not in src:
        sys.exit("menu: could not find the Deposits link to add after (apply apply-pm15-wiring.py first)")
    src = src.replace(link, link + '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/propertymgmt/charges" routerLinkActive="active"><span class="sidenav-icon">💧</span> Charges</a>\n', 1)
    save(path, src)
    print("menu: added")

# 3. report
path, src = rw("frontend/src/app/core/report-defs.ts")
if "key: 'pm-charges'" in src:
    print("report: already applied")
else:
    NEW = """  {
    key: 'pm-charges',
    title: 'Property Management utilities and service charges',
    section: 'Property Management',
    endpoint: 'pm-charges',
    categoryFilter: false,
    filterMode: 'dateOnly',
    columns: [
      { key: 'dueDate', label: 'Rent due', format: 'text' },
      { key: 'propertyName', label: 'Property', format: 'text' },
      { key: 'unitNo', label: 'Unit', format: 'text' },
      { key: 'tenantName', label: 'Tenant', format: 'text' },
      { key: 'leaseCode', label: 'Lease', format: 'text' },
      { key: 'name', label: 'Charge', format: 'text' },
      { key: 'source', label: 'Kind', format: 'text' },
      { key: 'description', label: 'Details', format: 'text' },
      { key: 'invoiceNumber', label: 'Invoice', format: 'text' },
      { key: 'amount', label: 'Amount', format: 'money' },
    ],
  },
"""
    anchor = "  {\n    key: 'pm-rent-increases',"
    if anchor not in src:
        sys.exit("report: could not find the pm-rent-increases report to add before")
    src = src.replace(anchor, NEW + anchor, 1)
    save(path, src)
    print("report: added")
