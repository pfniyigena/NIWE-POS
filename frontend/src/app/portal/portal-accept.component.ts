import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PortalApiService, PortalInviteInfo } from '../core/api/portal-api.service';
import { PortalLayoutComponent } from './portal-layout.component';

import { T } from '../core/i18n/t';
/** The page an invitation (or a reset) link opens: the person chooses their password, once, and is signed in. */
@Component({
  selector: 'app-portal-accept',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, PortalLayoutComponent],
  template: `
    <app-portal-layout [signedIn]="false" [firm]="info?.firmName ?? null">
      <div class="card" style="max-width:26rem; margin:2rem auto; padding:1.5rem;">
        <h3 class="mb-2">{{ 'Choose your password' | translate }}</h3>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <div *ngIf="!token" class="alert alert-danger">{{ 'This link is missing its code. Use the link from your invitation.' | translate }}</div>
        <div *ngIf="token && !info && !error" class="text-muted">{{ 'Checking the link…' | translate }}</div>
        <form *ngIf="info" (ngSubmit)="submit()">
          <div class="text-muted mb-3">{{ 'Hello {p0}. You will sign in with' | translate: { p0: (info.name) } }} <strong>{{ info.email }}</strong>.</div>
          <div class="form-group"><label class="form-label">{{ 'New password' | translate }}</label><input class="form-control" type="password" name="p1" [(ngModel)]="p1" autocomplete="new-password" required /></div>
          <div class="form-group"><label class="form-label">{{ 'Repeat the password' | translate }}</label><input class="form-control" type="password" name="p2" [(ngModel)]="p2" autocomplete="new-password" required /></div>
          <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'At least 10 characters, with letters and numbers, and not your e-mail address.' | translate }}</div>
          <div class="alert alert-danger" *ngIf="p1 && p2 && p1 !== p2">{{ 'The two passwords are different.' | translate }}</div>
          <button class="btn btn-primary btn-block" type="submit" [disabled]="busy || !p1 || p1 !== p2">{{ busy ? ('Saving…' | translate) : ('Save and continue' | translate) }}</button>
        </form>
      </div>
    </app-portal-layout>
  `,
})
export class PortalAcceptComponent implements OnInit {
  token: string | null = null;
  info: PortalInviteInfo | null = null;
  p1 = '';
  p2 = '';
  busy = false;
  error: string | null = null;

  constructor(private portal: PortalApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) return;
    this.portal.inviteInfo(this.token).subscribe({ next: (i) => (this.info = i), error: (e) => (this.error = e?.error?.message ?? T('This link is not valid')) });
  }

  submit(): void {
    if (!this.token) return;
    this.busy = true;
    this.error = null;
    this.portal.accept(this.token, this.p1).subscribe({
      next: () => { this.busy = false; this.router.navigate(['/portal']); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
