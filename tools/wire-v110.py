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

# v1.11.0: Promotions, Gift cards and Loyalty points show in the menu only while they are switched on in Sales > Settings.
_p = os.path.join(base, 'shared/shell.component.ts')
_s = open(_p, encoding='utf-8').read()
_c = False
for _path, _key in (('/admin/promotions', 'promotions'), ('/admin/gift-cards', 'giftCards'), ('/admin/loyalty', 'loyalty')):
    _old = "*ngIf=\"canSee('%s')\"" % _path
    _new = "*ngIf=\"canSee('%s') && modules.feature('%s')\"" % (_path, _key)
    if _new in _s: continue
    if _old not in _s: print('ANCHOR MISSING shell', _path); sys.exit(1)
    _s = _s.replace(_old, _new, 1); _c = True
if _c: open(_p, 'w', encoding='utf-8').write(_s); print('patched shared/shell.component.ts (sales features)')
