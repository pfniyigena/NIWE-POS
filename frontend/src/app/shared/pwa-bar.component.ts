import { Component } from '@angular/core';
import { NgIf } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { PwaService } from '../core/pwa.service';

/**
 * Mounted once in AppComponent. Three small notices, all out of the way of the work:
 * a strip when the device is offline, a card offering "Install the app" (the browser's own install, or the Share > Add to Home Screen way on iPhone),
 * and a card when a new version of the app is ready to load.
 */
@Component({
  selector: 'app-pwa-bar',
  standalone: true,
  imports: [NgIf, TranslatePipe],
  template: `
    <div class="pwa-offline" *ngIf="!pwa.online" role="status">{{ 'You are offline. Sales and changes cannot be saved until the connection is back.' | translate }}</div>

    <div class="pwa-card pwa-update" *ngIf="pwa.updateReady" role="status">
      <span>{{ 'A new version of the app is ready.' | translate }}</span>
      <button type="button" class="btn btn-primary btn-sm pwa-reload" (click)="pwa.reload()">{{ 'Reload' | translate }}</button>
    </div>

    <div class="pwa-card pwa-install" *ngIf="!hidden && !pwa.updateReady && (pwa.canInstall || pwa.iosHint)" role="dialog" [attr.aria-label]="'Install the app' | translate">
      <img src="/icons/icon-192.png" alt="" width="40" height="40" />
      <div class="pwa-text">
        <strong>{{ 'Install the app' | translate }}</strong>
        <span *ngIf="pwa.canInstall">{{ 'Opens in its own window and starts faster.' | translate }}</span>
        <span *ngIf="!pwa.canInstall && pwa.iosHint">{{ 'Tap Share, then "Add to Home Screen".' | translate }}</span>
      </div>
      <button type="button" class="btn btn-primary btn-sm pwa-install-btn" *ngIf="pwa.canInstall" (click)="pwa.install()">{{ 'Install' | translate }}</button>
      <button type="button" class="btn btn-outline-secondary btn-sm pwa-later" (click)="pwa.dismiss()">{{ 'Not now' | translate }}</button>
    </div>
  `,
  styles: [`
    .pwa-offline { position: fixed; top: 0; left: 0; right: 0; z-index: 2000; background: #b45309; color: #fff; text-align: center; padding: 0.35rem 0.75rem; font-size: 0.85rem; }
    .pwa-card { position: fixed; z-index: 1900; left: 50%; transform: translateX(-50%); bottom: calc(0.75rem + env(safe-area-inset-bottom, 0px)); width: min(30rem, calc(100vw - 1.5rem));
                display: flex; align-items: center; gap: 0.75rem; padding: 0.65rem 0.8rem; border-radius: 12px; background: var(--bg-elevated, #1c2030); color: var(--text, #e5e7eb);
                border: 1px solid rgba(128,128,128,0.35); box-shadow: 0 6px 24px rgba(0,0,0,0.35); }
    .pwa-card img { border-radius: 10px; flex: none; }
    .pwa-text { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; font-size: 0.85rem; }
    .pwa-text span { opacity: 0.75; }
    .pwa-update span { flex: 1 1 auto; font-size: 0.9rem; }
    .pwa-card .btn { flex: none; }
  `],
})
export class PwaBarComponent {
  constructor(public pwa: PwaService) {}

  /** Not on the customer's second screen. */
  get hidden(): boolean {
    return location.pathname.startsWith('/customer-display');
  }
}
