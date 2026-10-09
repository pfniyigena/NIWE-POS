import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

/**
 * Fourier: "Add forget password feature ... he/she can reset the password" — step two: the link
 * from the forgot-password email lands here with a `?token=` query param (see
 * PasswordResetService.requestReset's javadoc for how that link is built). The token itself is
 * single-use, 30-minutes-lived, and unguessable — see PasswordResetToken/PasswordResetService on
 * the backend; this screen just collects the new password and submits it alongside the token.
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center;">
      <div class="card" style="width:340px; padding:2rem;">
        <h3 class="mb-3" style="text-align:center;">Reset password</h3>

        <div *ngIf="!token">
          <div class="alert alert-danger">This reset link is missing its token — use the link from your email.</div>
        </div>

        <div *ngIf="token && !done">
          <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
          <form (ngSubmit)="submit()">
            <div class="form-group">
              <label class="form-label">New password</label>
              <input class="form-control" type="password" name="newPassword" [(ngModel)]="newPassword" autocomplete="new-password" required minlength="6" />
            </div>
            <div class="form-group">
              <label class="form-label">Confirm new password</label>
              <input class="form-control" type="password" name="confirmPassword" [(ngModel)]="confirmPassword" autocomplete="new-password" required minlength="6" />
            </div>
            <div class="alert alert-danger" *ngIf="mismatch">Passwords don't match.</div>
            <button class="btn btn-primary btn-block" type="submit" [disabled]="loading || !newPassword || !confirmPassword">
              {{ loading ? 'Resetting…' : 'Reset password' }}
            </button>
          </form>
        </div>

        <div *ngIf="done">
          <div class="alert alert-success">Your password has been reset. You can now sign in.</div>
        </div>

        <p style="margin-top:1rem; text-align:center;">
          <a routerLink="/login">Back to login</a>
        </p>
      </div>
    </div>
  `,
})
export class ResetPasswordComponent implements OnInit {
  token: string | null = null;
  newPassword = '';
  confirmPassword = '';
  loading = false;
  done = false;
  error: string | null = null;
  mismatch = false;

  constructor(private route: ActivatedRoute, private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
  }

  submit(): void {
    this.mismatch = this.newPassword !== this.confirmPassword;
    if (this.mismatch || !this.token) return;
    this.error = null;
    this.loading = true;
    this.auth.resetPassword(this.token, this.newPassword).subscribe({
      next: () => {
        this.loading = false;
        this.done = true;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to reset password';
      },
    });
  }
}
