#!/usr/bin/env python3
"""Idempotent: v1.10.0 wiring (footer, ...). Usage: wire-v110.py <frontend src/app dir>"""
import sys, os
base = sys.argv[1] if len(sys.argv) > 1 else 'src/app'
def patch(rel, pairs):
    p = os.path.join(base, rel); s = open(p, encoding='utf-8').read(); changed = False
    for marker, anchor, add, before in pairs:
        if marker in s: continue
        if anchor not in s: print('ANCHOR MISSING', rel, anchor[:70]); sys.exit(1)
        s = s.replace(anchor, (add + anchor) if before else (anchor + add), 1); changed = True
    if changed: open(p, 'w', encoding='utf-8').write(s); print('patched', rel)

# "© 2026 NIWE GROUP" under the content of every signed-in screen.
patch('shared/shell.component.ts', [
    ("NiweFooterComponent }", "@Component(", "import { NiweFooterComponent } from './niwe-footer.component';\n\n", True),
    ("imports: [NiweFooterComponent,", "imports: [", "NiweFooterComponent, ", False),
    ("<app-niwe-footer></app-niwe-footer>", "        <ng-content></ng-content>\n", "        <app-niwe-footer></app-niwe-footer>\n", False),
])
