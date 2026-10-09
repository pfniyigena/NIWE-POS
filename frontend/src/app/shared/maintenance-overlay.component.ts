import { Component } from '@angular/core';
import { NgIf } from '@angular/common';
import { MaintenanceService } from '../core/maintenance.service';

/**
 * Fourier: "true maintenance-mode lockout" — mounted once in AppComponent so it sits on top of
 * every screen. Shows when MaintenanceService.active() is true (set by maintenanceInterceptor the
 * moment any request comes back with the backend's "restore in progress" response), and covers the
 * whole page rather than just the one screen whose request happened to fail, since every other
 * screen is equally locked out right now too.
 * <p>
 * "Try again" doesn't retry anything itself — it just dismisses the overlay so the next normal
 * action (reload the list, resubmit the form) goes through as soon as the restore is actually done.
 */
@Component({
  selector: 'app-maintenance-overlay',
  standalone: true,
  imports: [NgIf],
  template: `
    <div class="maintenance-overlay" *ngIf="maintenance.active()">
      <div class="maintenance-card">
        <div class="maintenance-icon">⏳</div>
        <h3>System under maintenance</h3>
        <p>{{ maintenance.message() }}</p>
        <button class="btn btn-primary" (click)="maintenance.dismiss()">Try again</button>
      </div>
    </div>
  `,
  styles: [
    `
      .maintenance-overlay {
        position: fixed;
        inset: 0;
        z-index: 9999;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(10, 12, 18, 0.85);
        backdrop-filter: blur(2px);
      }
      .maintenance-card {
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius);
        padding: 2rem;
        max-width: 26rem;
        text-align: center;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
      }
      .maintenance-icon {
        font-size: 2rem;
        margin-bottom: 0.5rem;
      }
      .maintenance-card h3 {
        color: var(--text);
        margin: 0 0 0.75rem;
      }
      .maintenance-card p {
        color: var(--muted);
        margin: 0 0 1.5rem;
        font-size: 0.9rem;
        line-height: 1.4;
      }
    `,
  ],
})
export class MaintenanceOverlayComponent {
  constructor(public maintenance: MaintenanceService) {}
}
