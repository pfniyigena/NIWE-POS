#!/usr/bin/env python3
"""Phase PM-16 wiring that lives in files too big to ship whole:
  1. frontend/src/app/app.routes.ts             - the Fee invoices page
  2. frontend/src/app/shared/shell.component.ts - the Fee invoices link in the Property Management menu
  3. frontend/src/app/core/report-defs.ts       - the new Property Management fee invoices report
Usage (from the repo root):  python3 apply-pm16-wiring.py
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
if "PmFeeInvoiceListComponent" in src:
    print("routes: already applied")
else:
    imp = "import { PmDepositListComponent } from './admin/propertymgmt/pm-deposit-list.component';\n"
    if imp not in src:
        sys.exit("routes: could not find the deposits import (apply apply-pm15-wiring.py first)")
    src = src.replace(imp, imp + "import { PmFeeInvoiceListComponent } from './admin/propertymgmt/pm-fee-invoice-list.component';\n", 1)
    route = "  { path: 'admin/propertymgmt/deposits', component: PmDepositListComponent, canActivate: [authGuard, adminGuard] },\n"
    if route not in src:
        sys.exit("routes: could not find the deposits route to add after")
    src = src.replace(route, route + "  { path: 'admin/propertymgmt/fee-invoices', component: PmFeeInvoiceListComponent, canActivate: [authGuard, adminGuard] },\n", 1)
    save(path, src)
    print("routes: added")

# 2. menu
path, src = rw("frontend/src/app/shared/shell.component.ts")
if "/admin/propertymgmt/fee-invoices" in src:
    print("menu: already applied")
else:
    link = '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/propertymgmt/statements" routerLinkActive="active"><span class="sidenav-icon">🧾</span> Owner statements</a>\n'
    if link not in src:
        sys.exit("menu: could not find the Owner statements link to add after")
    src = src.replace(link, link + '          <a class="sidenav-link sidenav-sublink" routerLink="/admin/propertymgmt/fee-invoices" routerLinkActive="active"><span class="sidenav-icon">📄</span> Fee invoices</a>\n', 1)
    save(path, src)
    print("menu: added")

# 3. report
path, src = rw("frontend/src/app/core/report-defs.ts")
if "key: 'pm-fee-invoices'" in src:
    print("report: already applied")
else:
    NEW = """  {
    key: 'pm-fee-invoices',
    title: 'Property Management fee invoices',
    section: 'Property Management',
    endpoint: 'pm-fee-invoices',
    categoryFilter: false,
    filterMode: 'dateOnly',
    openOnlyToggle: 'Also list voided invoices',
    columns: [
      { key: 'number', label: 'Invoice', format: 'text' },
      { key: 'issueDate', label: 'Date', format: 'text' },
      { key: 'ownerName', label: 'Owner', format: 'text' },
      { key: 'ownerTaxNumber', label: 'Owner tax ID', format: 'text' },
      { key: 'statementCode', label: 'Statement', format: 'text' },
      { key: 'periodFrom', label: 'From', format: 'text' },
      { key: 'periodTo', label: 'To', format: 'text' },
      { key: 'netAmount', label: 'Fee', format: 'money' },
      { key: 'vatPercent', label: 'VAT %', format: 'percentN' },
      { key: 'vatAmount', label: 'VAT', format: 'money' },
      { key: 'totalAmount', label: 'Total', format: 'money' },
      { key: 'status', label: 'Status', format: 'text' },
    ],
  },
"""
    anchor = "  {\n    key: 'pm-rent-increases',"
    if anchor not in src:
        sys.exit("report: could not find the pm-rent-increases report to add before")
    src = src.replace(anchor, NEW + anchor, 1)
    save(path, src)
    print("report: added")
