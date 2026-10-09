import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';
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
  if (auth.currentUser()?.role !== 'ADMIN') {
    router.navigate(['/sessions']);
    return false;
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
