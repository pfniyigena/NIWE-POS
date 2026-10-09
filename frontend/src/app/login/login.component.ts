import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center;">
      <div class="card" style="width:340px; padding:2rem;">
        <h3 class="mb-3" style="text-align:center;">POS Login</h3>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <form (ngSubmit)="submit()">
          <div class="form-group">
            <label class="form-label">Username</label>
            <input class="form-control" name="username" [(ngModel)]="username" autocomplete="username" />
          </div>
          <div class="form-group">
            <label class="form-label">Password</label>
            <input class="form-control" type="password" name="password" [(ngModel)]="password" autocomplete="current-password" />
          </div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="loading">
            {{ loading ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/forgot-password">Forgot password?</a>
        </p>
        <!-- Fourier: "it is better to have a home page with look nice like a website" — the public
             links that used to live here ("Reserve a table", "Help Desk") moved to the new public
             home page (app.routes.ts's '' route), which is now the app's actual front door; this
             is just the way back to it for anyone who lands on /login directly. -->
        <p style="text-align:center;">
          <a routerLink="/">&larr; Back to home</a>
        </p>
        <p class="text-muted mb-1" style="font-size:0.8rem; text-align:center;">
          Demo logins: admin / admin123 or cashier / cashier123
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  username = '';
  password = '';
  error: string | null = null;
  loading = false;

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = null;
    this.loading = true;
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.loading = false;
        // Registers & Terminal is ADMIN-only now (a cashier browsing it could open the terminal
        // for a shift nobody assigned them — see app.routes.ts); land a cashier on Sessions
        // instead, where their own assigned session (if any) is what they'd want to see first.
        const landing = this.auth.currentUser()?.role === 'ADMIN' ? '/registers' : '/sessions';
        this.router.navigate([landing]);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Login failed';
      },
    });
  }
}
