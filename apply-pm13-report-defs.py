#!/usr/bin/env python3
"""Adds the four Property Management reports of phase PM-13 to frontend/src/app/core/report-defs.ts.
Usage (from the repo root):  python3 apply-pm13-report-defs.py [path/to/report-defs.ts]
Safe to run twice: it does nothing if the reports are already there."""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app/core/report-defs.ts"
src = open(path, encoding="utf-8").read()
if "key: 'pm-deposits'" in src:
    print("Already applied:", path)
    sys.exit(0)

NEW = """  {
    key: 'pm-deposits',
    title: 'Property Management deposits held',
    section: 'Property Management',
    endpoint: 'pm-deposits',
    categoryFilter: false,
    filterMode: 'none',
    openOnlyToggle: 'Only leases ending within 60 days or past their end (deposits to settle soon)',
    columns: [
      { key: 'propertyName', label: 'Property', format: 'text' },
      { key: 'unitNo', label: 'Unit', format: 'text' },
      { key: 'ownerName', label: 'Owner', format: 'text' },
      { key: 'tenantName', label: 'Tenant', format: 'text' },
      { key: 'code', label: 'Lease', format: 'text' },
      { key: 'startDate', label: 'Start', format: 'text' },
      { key: 'endDate', label: 'End', format: 'text' },
      { key: 'daysToEnd', label: 'Days left', format: 'text' },
      { key: 'deposit', label: 'Deposit', format: 'money' },
      { key: 'rent', label: 'Rent', format: 'money' },
      { key: 'frequency', label: 'Frequency', format: 'text' },
      { key: 'state', label: 'State', format: 'text' },
    ],
  },
  {
    key: 'pm-rent-increases',
    title: 'Property Management rent increases due',
    section: 'Property Management',
    endpoint: 'pm-rent-increases',
    categoryFilter: false,
    filterMode: 'none',
    openOnlyToggle: 'Only leases ending within 120 days or past their end',
    columns: [
      { key: 'code', label: 'Lease', format: 'text' },
      { key: 'propertyName', label: 'Property', format: 'text' },
      { key: 'unitNo', label: 'Unit', format: 'text' },
      { key: 'ownerName', label: 'Owner', format: 'text' },
      { key: 'tenantName', label: 'Tenant', format: 'text' },
      { key: 'endDate', label: 'Ends', format: 'text' },
      { key: 'daysToEnd', label: 'Days left', format: 'text' },
      { key: 'rent', label: 'Rent now', format: 'money' },
      { key: 'escalationPercent', label: 'Increase %', format: 'percent' },
      { key: 'newRent', label: 'Rent after', format: 'money' },
      { key: 'increase', label: 'Extra', format: 'money' },
      { key: 'frequency', label: 'Frequency', format: 'text' },
      { key: 'state', label: 'State', format: 'text' },
      { key: 'renewalCode', label: 'Renewal', format: 'text' },
    ],
  },
  {
    key: 'pm-trend',
    title: 'Property Management monthly trend',
    section: 'Property Management',
    endpoint: 'pm-trend',
    categoryFilter: false,
    filterMode: 'none',
    openOnlyToggle: 'Only months with rent still owed or units empty',
    columns: [
      { key: 'month', label: 'Month', format: 'text' },
      { key: 'units', label: 'Units', format: 'int' },
      { key: 'occupied', label: 'Let', format: 'int' },
      { key: 'occupancyPercent', label: 'Occupancy %', format: 'percent' },
      { key: 'charges', label: 'Charges', format: 'int' },
      { key: 'invoiced', label: 'Invoiced', format: 'money' },
      { key: 'collected', label: 'Collected', format: 'money' },
      { key: 'outstanding', label: 'Still owed', format: 'money' },
      { key: 'collectedPercent', label: 'Collected %', format: 'percent' },
    ],
  },
  {
    key: 'pm-contractors',
    title: 'Property Management repairs by contractor',
    section: 'Property Management',
    endpoint: 'pm-contractors',
    categoryFilter: false,
    filterMode: 'none',
    openOnlyToggle: 'Only contractors who have been paid something',
    columns: [
      { key: 'contractorName', label: 'Contractor', format: 'text' },
      { key: 'jobs', label: 'Jobs', format: 'int' },
      { key: 'completed', label: 'Done', format: 'int' },
      { key: 'open', label: 'Open', format: 'int' },
      { key: 'late', label: 'Late', format: 'int' },
      { key: 'totalCost', label: 'Total cost', format: 'money' },
      { key: 'averageCost', label: 'Average cost', format: 'moneyN' },
      { key: 'averageDays', label: 'Average days', format: 'text' },
    ],
  },
"""
end = src.rstrip().rfind("];")
if end < 0 or "REPORT_DEFS" not in src:
    sys.exit("Could not find the end of REPORT_DEFS in " + path)
out = src[:end] + NEW + src[end:]
open(path, "w", encoding="utf-8", newline="").write(out)
print("Added 4 Property Management reports to", path)
