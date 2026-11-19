import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MaintenanceOverlayComponent } from './shared/maintenance-overlay.component';
import { ThemeService } from './core/theme.service';
import { AutoStackService } from './core/auto-stack.service';
import { PwaService } from './core/pwa.service';
import { PwaBarComponent } from './shared/pwa-bar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MaintenanceOverlayComponent, PwaBarComponent],
  template: `
    <router-outlet></router-outlet>
    <app-maintenance-overlay></app-maintenance-overlay>
    <app-pwa-bar></app-pwa-bar>
  `,
})
export class AppComponent {
  // Injected only so the saved colour theme is applied the moment the app boots.
  constructor(_theme: ThemeService, autoStack: AutoStackService, _pwa: PwaService) {
    // Injected so the service worker is registered and the install offer is caught the moment the app boots.
    // Every plain data table becomes phone cards on a narrow screen — see AutoStackService.
    autoStack.start();
  }
}
