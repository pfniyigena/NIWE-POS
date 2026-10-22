#!/usr/bin/env python3
"""Version 1.4.0: bumps pom.xml, frontend/package.json and package-lock.json from 1.3.0 to 1.4.0, adds RELEASE-NOTES-v1.4.0.md and a README block.
Safe to run twice. Usage (from the repository folder): python3 tools/apply-v140.py"""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__))
root = os.getcwd()
def rd(p): return open(p, encoding="utf-8").read()
def wr(p, s): open(p, "w", encoding="utf-8", newline="").write(s)

p = os.path.join(root, "backend", "pom.xml")
s = rd(p)
if "<version>1.4.0</version>" in s.split("<dependencies>")[0]: print("pom already 1.4.0")
else:
    head, sep, tail = s.partition("<dependencies>")
    assert "<version>1.3.0</version>" in head, "pom: project version 1.3.0 not found"
    head = head.replace("<version>1.3.0</version>", "<version>1.4.0</version>", 1)
    wr(p, head + sep + tail); print("pom bumped")

p = os.path.join(root, "frontend", "package.json")
s = rd(p)
if '"version": "1.4.0"' in s: print("package.json already 1.4.0")
else:
    assert '"version": "1.3.0"' in s, "package.json: 1.3.0 not found"
    wr(p, s.replace('"version": "1.3.0"', '"version": "1.4.0"', 1)); print("package.json bumped")

p = os.path.join(root, "frontend", "package-lock.json")
if os.path.exists(p):
    s = rd(p)
    n = 0
    def sub(m):
        global n
        n += 1
        return m.group(1) + "1.4.0" + m.group(2)
    # the root version, and the root package entry ("" key)
    first = re.search(r'"version":\s*"([^"]+)"', s)
    if first and first.group(1) == "1.4.0": print("package-lock already 1.4.0"); s = ""
    s2 = re.sub(r'("version": ")1\.3\.0(")', sub, s, count=2) if s and first and first.group(1) == "1.3.0" else s
    if n: wr(p, s2); print("package-lock bumped (%d)" % n)
    else: print("package-lock: nothing to change")

print("release notes in place:", os.path.exists(os.path.join(root, "RELEASE-NOTES-v1.4.0.md")))

p = os.path.join(root, "README.md")
block = rd(os.path.join(here, "README-v1.4.0-block.md")).rstrip("\n") + "\n\n"
s = rd(p)
if "Version 1.4.0" in s: print("README already has the 1.4.0 block")
else:
    m = re.search(r'^#+ .*Version 1\.3\.0.*$', s, re.M)
    if m:
        wr(p, s[:m.start()] + block + s[m.start():]); print("README: 1.4.0 block inserted before the 1.3.0 block")
    else:
        wr(p, s.rstrip("\n") + "\n\n" + block); print("README: no 1.3.0 heading found, block appended at the end - move it if you want")
