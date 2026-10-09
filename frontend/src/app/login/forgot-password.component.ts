import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

/**
 * Fourier: "Add forget password feature where user will input the email and he/she can reset
 * the password" — step one: the user enters their email and, if it's registered, gets a reset
 * link (see PasswordResetService on the backend). The confirmation message is always the same
 * regardless of whether the email is actually registered — see that service's javadoc for why
 * (account-enumeration protection): only a real system-level problem (SMTP not configured at all)
 * shows as an error here.
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center;">
      <div class="card" style="width:340px; padding:2rem;">
        <h3 class="mb-3" style="text-align:center;">Forgot password</h3>

        <div *ngIf="!sent">
          <p class="text-muted" style="font-size:0.9rem;">
            Enter your account email and we'll send you a link to reset your password.
          </p>
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
          <form (ngSubmit)="submit()">
            <div class="form-group">
              <label class="form-label">Email</label>
              <input class="form-control" type="email" name="email" [(ngModel)]="email" autocomplete="email" required />
            </div>
            <button class="btn btn-primary btn-block" type="submit" [disabled]="loading || !email">
              {{ loading ? 'Sending…' : 'Send reset link' }}
            </button>
          </form>
        </div>

        <div *ngIf="sent">
          <div class="alert alert-success">
            If that email is registered, a reset link is on its way. Check your inbox.
          </div>
        </div>

        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/login">Back to login</a>
        </p>
      </div>
    </div>
  `,
})
export class ForgotPasswordComponent {
  email = '';
  loading = false;
  sent = false;
  error: string | null = null;

  constructor(private auth: AuthService) {}

  submit(): void {
    this.error = null;
    this.loading = true;
    this.auth.forgotPassword(this.email.trim()).subscribe({
      next: () => {
        this.loading = false;
        this.sent = true;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to send reset link';
      },
    });
  }
}
