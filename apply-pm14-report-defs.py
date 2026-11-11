#!/usr/bin/env python3
"""Adds the Property Management documents report (phase PM-14) to frontend/src/app/core/report-defs.ts.
Usage (from the repo root):  python3 apply-pm14-report-defs.py [path/to/report-defs.ts]
Safe to run twice: it does nothing if the report is already there."""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "frontend/src/app/core/report-defs.ts"
src = open(path, encoding="utf-8").read()
if "key: 'pm-documents'" in src:
    print("Already applied:", path)
    sys.exit(0)

NEW = """  {
    key: 'pm-documents',
    title: 'Property Management documents',
    section: 'Property Management',
    endpoint: 'pm-documents',
    categoryFilter: false,
    filterMode: 'none',
    openOnlyToggle: 'Only documents that expire, soonest first (lapsed or lapsing ones on top)',
    columns: [
      { key: 'name', label: 'Document', format: 'text' },
      { key: 'subjectLabel', label: 'Attached to', format: 'text' },
      { key: 'category', label: 'Kind', format: 'text' },
      { key: 'fileName', label: 'File', format: 'text' },
      { key: 'expiryDate', label: 'Expires', format: 'text' },
      { key: 'expiryState', label: 'State', format: 'text' },
      { key: 'daysToExpiry', label: 'Days left', format: 'text' },
      { key: 'uploadedByName', label: 'Added by', format: 'text' },
    ],
  },
"""
end = src.rstrip().rfind("];")
if end < 0 or "REPORT_DEFS" not in src:
    sys.exit("Could not find the end of REPORT_DEFS in " + path)
open(path, "w", encoding="utf-8", newline="").write(src[:end] + NEW + src[end:])
print("Added the Property Management documents report to", path)
