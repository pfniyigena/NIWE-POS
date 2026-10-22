#!/usr/bin/env python3
"""Idempotent: Hotel > Online booking (staff screen) + the public /stay page + the home-page tile. Usage: wire-ho8.py <frontend src/app dir>"""
import sys, os
base = sys.argv[1] if len(sys.argv) > 1 else 'src/app'
def patch(rel, pairs):
    p = os.path.join(base, rel); s = open(p, encoding='utf-8').read(); changed = False
    for marker, anchor, add, before in pairs:
        if marker in s: continue
        if anchor not in s: print('ANCHOR MISSING', rel, anchor[:70]); sys.exit(1)
        s = s.replace(anchor, (add + anchor) if before else (anchor + add), 1); changed = True
    if changed: open(p, 'w', encoding='utf-8').write(s); print('patched', rel)
    else: print('already', rel)
patch('app.routes.ts', [
    ("OnlineSettingsComponent }", "import { HotelReportComponent } from './hotel/hotel-report.component';\n",
     "import { OnlineSettingsComponent } from './hotel/online-settings.component';\nimport { PublicHotelComponent } from './hotel/public-hotel.component';\n", False),
    ("path: 'admin/hotel/online'", "  { path: 'admin/hotel/reports', component: HotelReportComponent, canActivate: [authGuard, adminGuard] },\n",
     "  { path: 'admin/hotel/online', component: OnlineSettingsComponent, canActivate: [authGuard, adminGuard] },\n", False),
    ("path: 'stay'", "  { path: 'book', component: PublicBookingComponent, canActivate: [publicModuleGuard('BOOKING')] },\n",
     "  // The hotel's public booking page: open to anyone while the Hotel module and Hotel > Online booking are on.\n  { path: 'stay', component: PublicHotelComponent, canActivate: [publicModuleGuard('HOTEL')] },\n", False),
])
s = open(os.path.join(base, 'core/auth.guard.ts'), encoding='utf-8').read()
if "key: 'RESTAURANT' | 'HELPDESK' | 'BOOKING' | 'REAL_ESTATE'): CanActivateFn" in s:
    open(os.path.join(base, 'core/auth.guard.ts'), 'w', encoding='utf-8').write(s.replace("key: 'RESTAURANT' | 'HELPDESK' | 'BOOKING' | 'REAL_ESTATE'): CanActivateFn", "key: 'RESTAURANT' | 'HELPDESK' | 'BOOKING' | 'REAL_ESTATE' | 'HOTEL'): CanActivateFn"))
    print('patched core/auth.guard.ts')
s = open(os.path.join(base, 'home/public-home.component.ts'), encoding='utf-8').read()
if "module: 'RESTAURANT' | 'HELPDESK' | 'BOOKING' | 'REAL_ESTATE';" in s:
    s = s.replace("module: 'RESTAURANT' | 'HELPDESK' | 'BOOKING' | 'REAL_ESTATE';", "module: 'RESTAURANT' | 'HELPDESK' | 'BOOKING' | 'REAL_ESTATE' | 'HOTEL';")
    open(os.path.join(base, 'home/public-home.component.ts'), 'w', encoding='utf-8').write(s); print('patched home type')
patch('home/public-home.component.ts', [
    ("route: '/stay'", "    { icon: '🏠', title: 'Find a property',",
     "    { icon: '🛏️', title: 'Book a room', description: 'See what is free and book your stay.', route: '/stay', linkLabel: 'Book a room', module: 'HOTEL' },\n", True),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/hotel/online"',
     """              <a *ngIf="canSee('/admin/hotel/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/reports" routerLinkActive="active">""",
     """              <a *ngIf="canSee('/admin/hotel/online')" class="sidenav-link sidenav-sublink" routerLink="/admin/hotel/online" routerLinkActive="active"><span class="sidenav-icon">🌐</span> Online booking</a>
""", True),
])
