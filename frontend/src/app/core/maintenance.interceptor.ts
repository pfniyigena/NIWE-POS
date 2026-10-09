import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { MaintenanceService } from './maintenance.service';

/**
 * Fourier: "true maintenance-mode lockout" — recognizes the backend's maintenance response (a 503
 * with {"maintenance":true,...}, from MaintenanceModeFilter) on ANY request, from any screen, and
 * flags MaintenanceService so MaintenanceOverlayComponent can take over the whole page. The error
 * still propagates to whatever that screen's own error handling does (so an in-flight import/report
 * still shows its usual "failed" message underneath), but the overlay is what the person actually
 * sees on top of it.
 */
export const maintenanceInterceptor: HttpInterceptorFn = (req, next) => {
  const maintenance = inject(MaintenanceService);

  return next(req).pipe(
    catchError((error) => {
      if (error?.status === 503 && error?.error?.maintenance === true) {
        maintenance.activate(error.error.message);
      }
      return throwError(() => error);
    })
  );
};
