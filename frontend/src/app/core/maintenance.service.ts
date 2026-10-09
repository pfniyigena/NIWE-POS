import { Injectable, signal } from '@angular/core';

/**
 * Fourier: "true maintenance-mode lockout" — set by maintenanceInterceptor the moment any request
 * comes back with the backend's {"maintenance":true} response (see MaintenanceModeFilter on the
 * backend), read by MaintenanceOverlayComponent to show a full-screen notice over whatever screen
 * the person happens to be on. Cleared by the overlay's own "Try again" button — there's nothing
 * to poll, since the next request someone makes will simply succeed once the restore is done.
 */
@Injectable({ providedIn: 'root' })
export class MaintenanceService {
  readonly active = signal(false);
  readonly message = signal<string>('The system is being restored from a backup. Please wait a moment and try again.');

  activate(message?: string): void {
    if (message) this.message.set(message);
    this.active.set(true);
  }

  dismiss(): void {
    this.active.set(false);
  }
}
