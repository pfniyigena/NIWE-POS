import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MaintenanceOverlayComponent } from './shared/maintenance-overlay.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MaintenanceOverlayComponent],
  template: `
    <router-outlet></router-outlet>
    <app-maintenance-overlay></app-maintenance-overlay>
  `,
})
export class AppComponent {}
