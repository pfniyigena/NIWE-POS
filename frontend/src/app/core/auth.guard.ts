import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { ModuleApiService, ModuleKey } from './api/module-api.service';
import { PublicSettingsApiService } from './api/public-settings-api.service';
import { ChartSetupApiService } from './api/chart-setup-api.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) {
    return true;
  }
  router.navigate(['/login']);
  return false;
};

/** Where adminGuard below sends an ADMIN whenever the chart of accounts still needs to be set up. */
const CHART_SETUP_PATH = '/setup/chart-country';

/**
 * Gates the admin-only screens (catalog/customer/staff management, dashboard, and — since
 * "Registers & Terminal" became ADMIN-only too, see app.routes.ts — registers itself). Assumes
 * authGuard already ran. Falls back to `/sessions`, not `/registers`: since `/registers` is now
 * itself behind this guard, falling back to it for a non-admin would bounce straight back here
 * and loop. `/sessions` is open to every logged-in user and always a safe landing page.
 *
 * <p>Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add
 * this on the screen then at first time a user select the country?" — since this guard already
 * sits in front of every admin route in app.routes.ts, folding the chart-setup check in here (
 * rather than adding it to each route individually) is what makes the wizard genuinely blocking:
 * an ADMIN can't reach any admin screen — not by the post-login redirect, not by typing a URL
 * directly, not after a page refresh — while {@code ChartSetupApiService} reports the chart still
 * needs setting up. `ChartSetupApiService.isConfirmedDone()` short-circuits this once the backend
 * has ever reported it's done, so a long-lived session isn't re-checking the server on every
 * single admin navigation forever.
 */
export const adminGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const chartSetup = inject(ChartSetupApiService);
  if (!auth.isAdmin()) {
    // Custom roles: a non-admin gets in when their role grants the area this path belongs to (core/access.ts).
    // Anything else falls back to /sessions, which is open to every logged-in user.
    if (!auth.canAccessPath(state.url)) {
      router.navigate(['/sessions']);
      return false;
    }
    // Only someone who can change accounting is held at the chart-of-accounts wizard.
    if (!auth.canManage('ACCOUNTING') || !state.url.split('?')[0].startsWith('/admin/accounting') || chartSetup.isConfirmedDone()) {
      return true;
    }
    return chartSetup.status().pipe(
      map((status) => {
        if (status.needsSelection) {
          router.navigate([CHART_SETUP_PATH]);
          return false;
        }
        return true;
      }),
      catchError(() => of(true))
    );
  }
  // The wizard route itself needs the admin-role check above, nothing more — otherwise it would
  // redirect to itself forever.
  if (state.url === CHART_SETUP_PATH) {
    return true;
  }
  if (chartSetup.isConfirmedDone()) {
    return true;
  }
  return chartSetup.status().pipe(
    map((status) => {
      if (status.needsSelection) {
        router.navigate([CHART_SETUP_PATH]);
        return false;
      }
      return true;
    }),
    // Fail-open — a transient network/API hiccup here must never lock an admin out of the whole
    // admin area; worst case the check is simply retried on the next navigation.
    catchError(() => of(true))
  );
};

/**
 * Settings > Modules: a screen of a module that is switched off is not reachable (typed URL, bookmark). Admins are sent
 * to Settings > Modules so they can switch it back on; everyone else to Settings > Appearance, open to every signed-in
 * user and not part of any module (so this can never bounce back and forth).
 * If the module state cannot be loaded the module counts as off.
 */
export function moduleGuard(key: ModuleKey): CanActivateFn {
  return () => {
    const modules = inject(ModuleApiService);
    const auth = inject(AuthService);
    const router = inject(Router);
    return modules.whenLoaded().pipe(
      map(() => {
        if (modules.on(key)) return true;
        router.navigate([auth.isAdmin() ? '/admin/settings/modules' : '/settings/appearance']);
        return false;
      })
    );
  };
}

/**
 * The public (no-login) pages /reserve and /helpdesk: reachable only while their module is switched on in Settings >
 * Modules; otherwise visitors land on the home page. If the state cannot be loaded the page counts as off.
 */
export function publicModuleGuard(key: 'RESTAURANT' | 'HELPDESK'): CanActivateFn {
  return () => {
    const publicSettings = inject(PublicSettingsApiService);
    const router = inject(Router);
    return publicSettings.modules().pipe(
      map((m) => (m[key] ? true : router.createUrlTree(['/']))),
      catchError(() => of(router.createUrlTree(['/'])))
    );
  };
}
