import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PortalApiService } from '../core/api/portal-api.service';
import { PortalLayoutComponent } from './portal-layout.component';

/** Client portal sign-in. The same answer is given for a wrong e-mail, a wrong password and a locked account. */
@Component({
  selector: 'app-portal-login',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, PortalLayoutComponent],
  template: `
    <app-portal-layout [signedIn]="false">
      <div class="card" style="max-width:24rem; margin:2rem auto; padding:1.5rem;">
        <h3 class="mb-2">{{ 'Sign in' | translate }}</h3>
        <div class="text-muted mb-3" style="font-size:0.9rem;">{{ 'Follow your matter, read the documents your lawyers shared with you, and send them messages.' | translate }}</div>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <form (ngSubmit)="submit()">
          <div class="form-group"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="email" [(ngModel)]="email" autocomplete="username" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Password' | translate }}</label><input class="form-control" type="password" name="password" [(ngModel)]="password" autocomplete="current-password" required /></div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="busy || !email || !password">{{ busy ? ('Signing in…' | translate) : ('Sign in' | translate) }}</button>
        </form>
        <div class="text-muted mt-3" style="font-size:0.85rem;">{{ 'First time? Use the link in the invitation the firm sent you. Forgot your password? Ask the firm for a new link.' | translate }}</div>
      </div>
    </app-portal-layout>
  `,
})
export class PortalLoginComponent {
  email = '';
  password = '';
  busy = false;
  error: string | null = null;

  constructor(private portal: PortalApiService, private router: Router) {}

  submit(): void {
    this.busy = true;
    this.error = null;
    this.portal.login(this.email, this.password).subscribe({
      next: () => { this.busy = false; this.router.navigate(['/portal']); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not sign in'; },
    });
  }
}
