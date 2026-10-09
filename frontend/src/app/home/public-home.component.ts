import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { PublicSettingsApiService } from '../core/api/public-settings-api.service';

/** One tile on the public home page's "What would you like to do?" grid — see PublicHomeComponent's own javadoc. */
interface PublicLink {
  icon: string;
  title: string;
  description: string;
  route: string;
  linkLabel: string;
}

/**
 * Fourier: "As our ERP grows, we are likely to have more public links (Menus), it is better to
 * have a home page with look nice like a website." Replaces the old approach of tucking one-off
 * links ("Reserve a table", "Help Desk") onto the plain login form — this is now the single front
 * door for every unauthenticated public feature, meant to just grow a new entry in {@link links}
 * below as each one ships (a loyalty signup page, a public menu, whatever comes next), rather than
 * cluttering the staff sign-in screen further.
 *
 * <p>This is the root route (`''` in app.routes.ts) now, not `/login` — a visitor who isn't staff
 * at all should land somewhere that looks like the business's own site, not a bare username/
 * password form. A user who's already signed in is sent straight on to their usual landing page
 * (same ADMIN → /registers, everyone else → /sessions split LoginComponent's own post-login
 * redirect uses) rather than seeing this marketing-style page at all.
 */
@Component({
  selector: 'app-public-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="public-home">
      <header class="public-home-hero">
        <div class="public-home-hero-inner">
          <div class="flex align-center gap-2 mb-2">
            <img *ngIf="logoDataUrl" [src]="logoDataUrl" alt="" style="height:48px; width:48px; object-fit:contain; border-radius:0.4rem; background:#fff;" />
            <h1 style="margin:0; font-size:1.8rem;">{{ businessName || 'Welcome' }}</h1>
          </div>
          <p class="public-home-tagline">Reservations, support, and more — all in one place.</p>
          <a class="btn btn-primary" routerLink="/login">Staff Login</a>
        </div>
      </header>

      <main class="public-home-main">
        <h2 class="mb-3" style="text-align:center;">What would you like to do?</h2>
        <div class="public-home-grid">
          <a *ngFor="let link of links" class="card public-home-card" [routerLink]="link.route">
            <div class="public-home-card-icon">{{ link.icon }}</div>
            <h3 class="mb-1">{{ link.title }}</h3>
            <p class="text-muted" style="margin:0 0 0.75rem;">{{ link.description }}</p>
            <span class="btn btn-outline-primary btn-sm">{{ link.linkLabel }}</span>
          </a>
        </div>
      </main>

      <footer class="public-home-footer">
        <span class="text-muted">&copy; {{ year }} {{ businessName || 'This business' }}</span>
      </footer>
    </div>
  `,
  styles: [`
    .public-home { min-height: 100vh; display: flex; flex-direction: column; }
    .public-home-hero {
      background: linear-gradient(135deg, var(--surface) 0%, var(--bg) 100%);
      border-bottom: 1px solid var(--border);
      padding: 3rem 1rem;
    }
    .public-home-hero-inner { max-width: 960px; margin: 0 auto; }
    .public-home-tagline { color: var(--muted); margin: 0 0 1.25rem; font-size: 1.05rem; }
    .public-home-main { flex: 1; max-width: 960px; margin: 0 auto; padding: 2.5rem 1rem; width: 100%; }
    .public-home-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1.25rem;
    }
    .public-home-card {
      padding: 1.5rem;
      text-align: center;
      text-decoration: none;
      color: inherit;
      transition: transform 0.15s ease, border-color 0.15s ease;
    }
    .public-home-card:hover { transform: translateY(-2px); border-color: var(--accent); }
    .public-home-card-icon { font-size: 2rem; margin-bottom: 0.5rem; }
    .public-home-footer {
      text-align: center;
      padding: 1.5rem 1rem;
      border-top: 1px solid var(--border);
    }
  `],
})
export class PublicHomeComponent implements OnInit {
  businessName: string | null = null;
  logoDataUrl: string | null = null;
  year = new Date().getFullYear();

  /** Grows by one entry per new public feature — nothing else on this page needs to change. */
  links: PublicLink[] = [
    { icon: '🍽️', title: 'Reserve a table', description: 'Book a table for an upcoming visit.', route: '/reserve', linkLabel: 'Reserve now' },
    { icon: '🎫', title: 'Help Desk', description: 'Submit a support request or check an existing ticket.', route: '/helpdesk', linkLabel: 'Open Help Desk' },
  ];

  constructor(private auth: AuthService, private router: Router, private publicSettingsApi: PublicSettingsApiService) {}

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) {
      const landing = this.auth.currentUser()?.role === 'ADMIN' ? '/registers' : '/sessions';
      this.router.navigate([landing]);
      return;
    }
    this.publicSettingsApi.branding().subscribe({
      next: (b) => {
        this.businessName = b.businessName;
        this.logoDataUrl = b.logoDataUrl;
      },
      // Branding is cosmetic only — if it fails to load, the page still works fine with generic text.
      error: () => {},
    });
  }
}
