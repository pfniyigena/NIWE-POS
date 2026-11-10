import { Component, Input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { PortalApiService } from '../core/api/portal-api.service';
import { NiweFooterComponent } from '../shared/niwe-footer.component';

/** The frame around every client-portal page: the firm's name on top, the client's name and a Sign out button. It has nothing of the staff menu. */
@Component({
  selector: 'app-portal-layout',
  standalone: true,
  imports: [TranslatePipe, NiweFooterComponent, CommonModule, RouterLink],
  template: `
    <div style="min-height:100vh;">
      <header style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:0.5rem; padding:0.75rem 1.25rem; border-bottom:1px solid var(--border, #ddd);">
        <a routerLink="/portal" style="text-decoration:none; color:inherit;"><strong style="font-size:1.1rem;">{{ firm || 'Client portal' }}</strong><span class="text-muted" *ngIf="firm"> {{ '· Client portal' | translate }}</span></a>
        <div *ngIf="signedIn" style="display:flex; gap:0.75rem; align-items:center;">
          <span class="text-muted" style="font-size:0.9rem;">{{ name }}</span>
          <button class="btn btn-sm btn-outline-secondary" (click)="signOut()">{{ 'Sign out' | translate }}</button>
        </div>
      </header>
      <main style="max-width:60rem; margin:0 auto; padding:1.25rem;">
        <ng-content></ng-content>
      </main>
    </div>
    <app-niwe-footer [fixed]="true"></app-niwe-footer>
  `,
})
export class PortalLayoutComponent {
  @Input() firm: string | null = null;
  @Input() name: string | null = null;
  @Input() signedIn = true;

  constructor(private portal: PortalApiService, private router: Router) {}

  signOut(): void {
    this.portal.signOut();
    this.router.navigate(['/portal/login']);
  }
}
