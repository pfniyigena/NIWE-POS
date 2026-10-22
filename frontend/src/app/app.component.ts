import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MaintenanceOverlayComponent } from './shared/maintenance-overlay.component';
import { ThemeService } from './core/theme.service';
import { AutoStackService } from './core/auto-stack.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MaintenanceOverlayComponent],
  template: `
    <router-outlet></router-outlet>
    <app-maintenance-overlay></app-maintenance-overlay>
  `,
})
export class AppComponent {
  // Injected only so the saved colour theme is applied the moment the app boots.
  constructor(_theme: ThemeService, autoStack: AutoStackService) {
    // Every plain data table becomes phone cards on a narrow screen — see AutoStackService.
    autoStack.start();
  }
}
