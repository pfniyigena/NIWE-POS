import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PortalApiService, PortalMe } from '../core/api/portal-api.service';
import { STATUS_LABEL } from '../core/api/law-api.service';
import { PortalLayoutComponent } from './portal-layout.component';

/** The client's matters (only those the firm opened to the portal), and a place to change their own password. */
@Component({
  selector: 'app-portal-home',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, PortalLayoutComponent],
  template: `
    <app-portal-layout [firm]="me?.firmName ?? null" [name]="me?.name ?? null">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!me && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="me">
        <h3 class="mb-1">{{ 'Your matters' | translate }}</h3>
        <div class="text-muted mb-3">{{ me.clientName }}</div>
        <div class="card mb-3" style="padding:1rem;" *ngFor="let m of me.matters">
          <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
            <div><a [routerLink]="['/portal/matters', m.id]"><strong>{{ m.number }} · {{ m.title }}</strong></a>
              <div class="text-muted" style="font-size:0.85rem;">{{ m.practiceArea }}<span *ngIf="m.leadName"> {{ '· Your lawyer: {p0}' | translate: { p0: (m.leadName) } }}</span></div></div>
            <div style="text-align:right;">
              <span class="badge">{{ status(m.status) }}</span>
              <div style="font-size:0.85rem;" *ngIf="m.nextDeadline">{{ 'Next date:' | translate }} <strong>{{ m.nextDeadline | date: 'd MMM y' }}</strong></div>
              <div style="font-size:0.85rem; color:#b45309;" *ngIf="m.unreadMessages">{{ '{p0} new message{p1}' | translate: { p0: (m.unreadMessages), p1: (m.unreadMessages === 1 ? '' : 's') } }}</div>
            </div>
          </div>
        </div>
        <div class="card" style="padding:1rem;" *ngIf="!me.matters.length">{{ 'The firm has not opened any matter to the portal for you yet.' | translate }}<span *ngIf="me.firmEmail || me.firmPhone"> {{ 'Contact them{p0}{p1}.' | translate: { p0: (me.firmPhone ? ' on ' + me.firmPhone : ''), p1: (me.firmEmail ? ' or ' + me.firmEmail : '') } }}</span></div>

        <div class="mt-4">
          <button class="btn btn-sm btn-outline-secondary" (click)="pw.open = !pw.open">{{ 'Change my password' | translate }}</button>
          <form class="card mt-2" style="padding:1rem; max-width:26rem;" *ngIf="pw.open" (ngSubmit)="changePassword()">
            <div class="alert alert-danger" *ngIf="pw.error">{{ pw.error }}</div>
            <div class="form-group"><label class="form-label">{{ 'Current password' | translate }}</label><input class="form-control" type="password" name="c" [(ngModel)]="pw.current" autocomplete="current-password" /></div>
            <div class="form-group"><label class="form-label">{{ 'New password' | translate }}</label><input class="form-control" type="password" name="n" [(ngModel)]="pw.next" autocomplete="new-password" /></div>
            <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'At least 10 characters, with letters and numbers. You will have to sign in again.' | translate }}</div>
            <button class="btn btn-primary" type="submit" [disabled]="pw.busy || !pw.current || !pw.next">{{ 'Change the password' | translate }}</button>
          </form>
        </div>
      </ng-container>
    </app-portal-layout>
  `,
})
export class PortalHomeComponent implements OnInit {
  me: PortalMe | null = null;
  error: string | null = null;
  pw = { open: false, current: '', next: '', busy: false, error: null as string | null };

  constructor(private portal: PortalApiService, private router: Router) {}

  ngOnInit(): void {
    if (!this.portal.signedIn()) { this.router.navigate(['/portal/login']); return; }
    this.portal.me().subscribe({
      next: (m) => (this.me = m),
      error: (e) => {
        if (e?.status === 401 || e?.status === 403) { this.portal.signOut(); this.router.navigate(['/portal/login']); return; }
        this.error = e?.error?.message ?? 'Could not load your matters';
      },
    });
  }

  status(s: string): string { return (STATUS_LABEL as Record<string, string>)[s] ?? s; }

  changePassword(): void {
    this.pw.busy = true;
    this.pw.error = null;
    this.portal.changePassword(this.pw.current, this.pw.next).subscribe({
      next: () => { this.portal.signOut(); this.router.navigate(['/portal/login']); },
      error: (e) => { this.pw.busy = false; this.pw.error = e?.error?.message ?? 'That did not work'; },
    });
  }
}
