import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { MeApiService } from '../core/api/me-api.service';
import { AuthService } from '../core/auth.service';

/**
 * Self-service "My Profile" (Task: "Add my profile, change my password") — open to any logged-in
 * user (cashier or admin), unlike Admin > Staff which is where an admin manages *other* accounts.
 * Two independent panels/forms on one screen: editing your own display name, and changing your
 * own password (which requires typing the current one — see ChangePasswordRequest's javadoc on
 * the backend). Username and role are shown but not editable here; changing either is an admin
 * action on the Staff screen, not something a user does to themselves.
 */
@Component({
  selector: 'app-my-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">My profile</h3>

      <div class="card mb-3" style="padding:1rem;">
        <div class="alert alert-danger" *ngIf="profileError">{{ profileError }}</div>
        <div class="alert alert-success" *ngIf="profileSaved">Profile updated.</div>
        <div class="form-group">
          <label class="form-label">Username</label>
          <input class="form-control" [value]="username" disabled />
        </div>
        <div class="form-group">
          <label class="form-label">Role</label>
          <input class="form-control" [value]="role" disabled />
        </div>
        <div class="form-group">
          <label class="form-label">Full name</label>
          <input class="form-control" name="fullName" [(ngModel)]="fullName" required />
        </div>
        <div class="form-group">
          <label class="form-label">Email</label>
          <input class="form-control" type="email" name="email" [(ngModel)]="email" autocomplete="email" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Optional — needed to use "Forgot password?" on the login screen.
          </div>
        </div>
        <button class="btn btn-primary" (click)="saveProfile()" [disabled]="savingProfile || !fullName.trim()">
          {{ savingProfile ? 'Saving…' : 'Save profile' }}
        </button>
      </div>

      <div class="card" style="padding:1rem;">
        <h4 class="mb-2" style="font-size:1rem;">Change password</h4>
        <div class="alert alert-danger" *ngIf="passwordError">{{ passwordError }}</div>
        <div class="alert alert-success" *ngIf="passwordSaved">Password changed.</div>
        <div class="form-group">
          <label class="form-label">Current password</label>
          <input class="form-control" type="password" name="currentPassword" [(ngModel)]="currentPassword" required />
        </div>
        <div class="form-group">
          <label class="form-label">New password</label>
          <input class="form-control" type="password" name="newPassword" [(ngModel)]="newPassword" required minlength="6" />
        </div>
        <div class="form-group">
          <label class="form-label">Confirm new password</label>
          <input class="form-control" type="password" name="confirmPassword" [(ngModel)]="confirmPassword" required minlength="6" />
        </div>
        <button class="btn btn-primary" (click)="changePassword()" [disabled]="changingPassword || !canChangePassword()">
          {{ changingPassword ? 'Changing…' : 'Change password' }}
        </button>
      </div>

      <!-- Task: "Add change PIN inside my profile" — the PIN used to identify yourself at the
           terminal (see CashierSessionService), separate from the login password above. -->
      <div class="card" style="padding:1rem; margin-top:1rem;">
        <h4 class="mb-2" style="font-size:1rem;">
          Change PIN
          <span class="badge badge-success" *ngIf="hasPin">Set</span>
          <span class="badge badge-secondary" *ngIf="!hasPin">Not set</span>
        </h4>
        <p class="text-muted" style="font-size:0.85rem;">Used to identify yourself at the terminal — 4 to 6 digits.</p>
        <div class="alert alert-danger" *ngIf="pinError">{{ pinError }}</div>
        <div class="alert alert-success" *ngIf="pinSaved">PIN updated.</div>
        <div class="form-group">
          <label class="form-label">New PIN</label>
          <input class="form-control" type="password" inputmode="numeric" name="newPin" [(ngModel)]="newPin" pattern="\\d{4,6}" maxlength="6" />
        </div>
        <div class="form-group">
          <label class="form-label">Confirm new PIN</label>
          <input class="form-control" type="password" inputmode="numeric" name="confirmPin" [(ngModel)]="confirmPin" pattern="\\d{4,6}" maxlength="6" />
        </div>
        <button class="btn btn-primary" (click)="changePin()" [disabled]="changingPin || !canChangePin()">
          {{ changingPin ? 'Changing…' : 'Change PIN' }}
        </button>
      </div>
    </app-shell>
  `,
})
export class MyProfileComponent implements OnInit {
  username = '';
  role = '';
  fullName = '';
  email = '';

  currentPassword = '';
  newPassword = '';
  confirmPassword = '';

  savingProfile = false;
  profileError: string | null = null;
  profileSaved = false;

  changingPassword = false;
  passwordError: string | null = null;
  passwordSaved = false;

  hasPin = false;
  newPin = '';
  confirmPin = '';
  changingPin = false;
  pinError: string | null = null;
  pinSaved = false;

  constructor(private meApi: MeApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.meApi.getProfile().subscribe({
      next: (u) => {
        this.username = u.username;
        this.role = u.role;
        this.fullName = u.fullName;
        this.email = u.email ?? '';
        this.hasPin = u.hasPin;
      },
      error: (err) => (this.profileError = err?.error?.message ?? 'Failed to load your profile'),
    });
  }

  saveProfile(): void {
    this.profileError = null;
    this.profileSaved = false;
    this.savingProfile = true;
    this.meApi.updateProfile(this.fullName.trim(), this.email.trim() || null).subscribe({
      next: (u) => {
        this.savingProfile = false;
        this.fullName = u.fullName;
        this.email = u.email ?? '';
        this.profileSaved = true;
        // Keep the topbar's displayed name in sync without requiring a re-login.
        this.auth.updateFullName(u.fullName);
      },
      error: (err) => {
        this.savingProfile = false;
        this.profileError = err?.error?.message ?? 'Failed to save your profile';
      },
    });
  }

  canChangePassword(): boolean {
    return (
      this.currentPassword.length > 0 &&
      this.newPassword.length >= 6 &&
      this.newPassword === this.confirmPassword
    );
  }

  changePassword(): void {
    this.passwordError = null;
    this.passwordSaved = false;
    if (this.newPassword !== this.confirmPassword) {
      this.passwordError = 'New password and confirmation do not match';
      return;
    }
    this.changingPassword = true;
    this.meApi.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.changingPassword = false;
        this.passwordSaved = true;
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
      },
      error: (err) => {
        this.changingPassword = false;
        this.passwordError = err?.error?.message ?? 'Failed to change password';
      },
    });
  }

  canChangePin(): boolean {
    return /^\d{4,6}$/.test(this.newPin) && this.newPin === this.confirmPin;
  }

  changePin(): void {
    this.pinError = null;
    this.pinSaved = false;
    if (!/^\d{4,6}$/.test(this.newPin)) {
      this.pinError = 'PIN must be 4 to 6 digits';
      return;
    }
    if (this.newPin !== this.confirmPin) {
      this.pinError = 'New PIN and confirmation do not match';
      return;
    }
    this.changingPin = true;
    this.meApi.changePin(this.newPin).subscribe({
      next: (u) => {
        this.changingPin = false;
        this.hasPin = u.hasPin;
        this.pinSaved = true;
        this.newPin = '';
        this.confirmPin = '';
      },
      error: (err) => {
        this.changingPin = false;
        this.pinError = err?.error?.message ?? 'Failed to change PIN';
      },
    });
  }
}
