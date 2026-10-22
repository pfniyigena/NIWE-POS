#!/usr/bin/env python3
"""Version 1.5.0: bumps pom.xml, frontend/package.json and package-lock.json from 1.4.0 to 1.5.0, checks RELEASE-NOTES-v1.5.0.md and adds a README block.
Safe to run twice. Usage (from the repository folder): python3 tools/apply-v150.py"""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
root = os.getcwd()
OLD, NEW = "1.4.0", "1.5.0"
def rd(p): return open(p, encoding="utf-8").read()
def wr(p, s): open(p, "w", encoding="utf-8", newline="").write(s)

p = os.path.join(root, "backend", "pom.xml")
s = rd(p)
head, sep, tail = s.partition("<dependencies>")
if "<version>%s</version>" % NEW in head: print("pom already " + NEW)
else:
    assert "<version>%s</version>" % OLD in head, "pom: project version %s not found" % OLD
    wr(p, head.replace("<version>%s</version>" % OLD, "<version>%s</version>" % NEW, 1) + sep + tail); print("pom bumped")

p = os.path.join(root, "frontend", "package.json")
s = rd(p)
if '"version": "%s"' % NEW in s: print("package.json already " + NEW)
else:
    assert '"version": "%s"' % OLD in s, "package.json: %s not found" % OLD
    wr(p, s.replace('"version": "%s"' % OLD, '"version": "%s"' % NEW, 1)); print("package.json bumped")

p = os.path.join(root, "frontend", "package-lock.json")
if os.path.exists(p):
    s = rd(p)
    first = re.search(r'"version":\s*"([^"]+)"', s)
    if first and first.group(1) == NEW: print("package-lock already " + NEW)
    elif first and first.group(1) == OLD:
        s2, n = re.subn(r'("version": ")%s(")' % re.escape(OLD), r'\g<1>%s\g<2>' % NEW, s, count=2)
        wr(p, s2); print("package-lock bumped (%d)" % n)
    else: print("package-lock: nothing to change")

print("release notes in place:", os.path.exists(os.path.join(root, "RELEASE-NOTES-v%s.md" % NEW)))

p = os.path.join(root, "README.md")
block = rd(os.path.join(here, "README-v%s-block.md" % NEW)).rstrip("\n") + "\n\n"
s = rd(p)
if "Version %s" % NEW in s: print("README already has the %s block" % NEW)
else:
    m = re.search(r'^#+ .*Version 1\.4\.0.*$', s, re.M) or re.search(r'^#+ .*Version 1\.3\.0.*$', s, re.M)
    if m:
        wr(p, s[:m.start()] + block + s[m.start():]); print("README: %s block inserted before the previous block" % NEW)
    else:
        wr(p, s.rstrip("\n") + "\n\n" + block); print("README: no earlier heading found, block appended at the end - move it if you want")
