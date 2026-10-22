#!/usr/bin/env python3
"""Idempotent: Restaurant screens added by RS-2.. (routes + side menu). Usage: wire-rs.py <frontend src/app dir>"""
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

IMPORT_ANCHOR = "import { RestaurantWastageFormComponent } from './restaurant/restaurant-wastage-form.component';\n"
ROUTE_ANCHOR = "  { path: 'admin/restaurant/wastage/new', component: RestaurantWastageFormComponent, canActivate: [authGuard] },\n"
patch('app.routes.ts', [
    ("RestaurantSettingsComponent }", IMPORT_ANCHOR, "import { RestaurantSettingsComponent } from './restaurant/restaurant-settings.component';\n", False),
    ("path: 'admin/restaurant/settings'", ROUTE_ANCHOR, "  { path: 'admin/restaurant/settings', component: RestaurantSettingsComponent, canActivate: [authGuard, adminGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/restaurant/settings"',
     """          <a *ngIf="canSee('/admin/restaurant/menu')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/menu" routerLinkActive="active"><span class="sidenav-icon">📋</span> Menu</a>
""",
     """          <a *ngIf="canSee('/admin/restaurant/settings')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/settings" routerLinkActive="active"><span class="sidenav-icon">⚙️</span> Settings</a>
""", False),
])

# RS-4: voids and comps log
patch('app.routes.ts', [
    ("RestaurantVoidsComponent }", IMPORT_ANCHOR, "import { RestaurantVoidsComponent } from './restaurant/restaurant-voids.component';\n", False),
    ("path: 'admin/restaurant/voids'", ROUTE_ANCHOR, "  { path: 'admin/restaurant/voids', component: RestaurantVoidsComponent, canActivate: [authGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/restaurant/voids"',
     """          <a *ngIf="canSee('/admin/restaurant/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
""",
     """          <a *ngIf="canSee('/admin/restaurant/voids')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/voids" routerLinkActive="active"><span class="sidenav-icon">🚫</span> Voids &amp; comps</a>
""", True),
])

# RS-5: waiting list
patch('app.routes.ts', [
    ("WaitlistComponent }", IMPORT_ANCHOR, "import { WaitlistComponent } from './restaurant/waitlist.component';\n", False),
    ("path: 'admin/restaurant/waitlist'", ROUTE_ANCHOR, "  { path: 'admin/restaurant/waitlist', component: WaitlistComponent, canActivate: [authGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/restaurant/waitlist"',
     """          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reservations" routerLinkActive="active"><span class="sidenav-icon">📅</span> Reservations</a>
""",
     """          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/waitlist" routerLinkActive="active"><span class="sidenav-icon">⏳</span> Waiting list</a>
""", False),
])

# RS-6: expo screen
patch('app.routes.ts', [
    ("KitchenExpoComponent }", IMPORT_ANCHOR, "import { KitchenExpoComponent } from './restaurant/kitchen-expo.component';\n", False),
    ("path: 'admin/restaurant/kitchen/expo'", ROUTE_ANCHOR, "  { path: 'admin/restaurant/kitchen/expo', component: KitchenExpoComponent, canActivate: [authGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/restaurant/kitchen/expo"',
     """          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/kitchen" routerLinkActive="active"><span class="sidenav-icon">🍳</span> Kitchen display</a>
""",
     """          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/kitchen/expo" routerLinkActive="active"><span class="sidenav-icon">🛎️</span> Expo</a>
""", False),
])

# RS-7: sales analysis, end of day, printable Z-report
patch('app.routes.ts', [
    ("SalesAnalysisComponent }", IMPORT_ANCHOR, "import { SalesAnalysisComponent } from './restaurant/sales-analysis.component';\nimport { EndOfDayComponent } from './restaurant/end-of-day.component';\nimport { EndOfDayPrintComponent } from './restaurant/end-of-day-print.component';\n", False),
    ("path: 'admin/restaurant/analysis'", ROUTE_ANCHOR, "  { path: 'admin/restaurant/analysis', component: SalesAnalysisComponent, canActivate: [authGuard] },\n  { path: 'admin/restaurant/end-of-day', component: EndOfDayComponent, canActivate: [authGuard] },\n  { path: 'admin/restaurant/end-of-day/print/:date', component: EndOfDayPrintComponent, canActivate: [authGuard] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/restaurant/analysis"',
     """          <a *ngIf="canSee('/admin/restaurant/reports')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reports" routerLinkActive="active"><span class="sidenav-icon">📊</span> Reports</a>
""",
     """          <a *ngIf="canSee('/admin/restaurant/analysis')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/analysis" routerLinkActive="active"><span class="sidenav-icon">📈</span> Sales analysis</a>
          <a *ngIf="canSee('/admin/restaurant/end-of-day')" class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/end-of-day" routerLinkActive="active"><span class="sidenav-icon">🧾</span> End of day</a>
""", True),
])

# RS-8: online orders (staff queue, setup, QR print) and the guest pages
patch('app.routes.ts', [
    ("OnlineOrdersComponent }", IMPORT_ANCHOR, "import { OnlineOrdersComponent } from './restaurant/online-orders.component';\nimport { OnlineSetupComponent } from './restaurant/online-setup.component';\nimport { QrPrintComponent } from './restaurant/qr-print.component';\nimport { PublicOrderComponent } from './restaurant/public-order.component';\nimport { OrderTrackComponent } from './restaurant/order-track.component';\n", False),
    ("path: 'admin/restaurant/online-orders'", ROUTE_ANCHOR, "  { path: 'admin/restaurant/online-orders', component: OnlineOrdersComponent, canActivate: [authGuard] },\n  { path: 'admin/restaurant/online-setup', component: OnlineSetupComponent, canActivate: [authGuard] },\n  { path: 'admin/restaurant/qr-print', component: QrPrintComponent, canActivate: [authGuard] },\n  { path: 'menu/:token', component: PublicOrderComponent, canActivate: [publicModuleGuard('RESTAURANT')] },\n  { path: 'order', component: PublicOrderComponent, canActivate: [publicModuleGuard('RESTAURANT')] },\n  { path: 'order/track/:code', component: OrderTrackComponent, canActivate: [publicModuleGuard('RESTAURANT')] },\n", False),
])
patch('shared/shell.component.ts', [
    ('routerLink="/admin/restaurant/online-orders"',
     """          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/reservations" routerLinkActive="active"><span class="sidenav-icon">📅</span> Reservations</a>
""",
     """          <a class="sidenav-link sidenav-sublink" routerLink="/admin/restaurant/online-orders" routerLinkActive="active"><span class="sidenav-icon">📲</span> Online orders</a>
""", False),
])
