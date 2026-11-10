import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PaperButtonComponent } from '../shared/paper-button.component';
import { ModuleApiService, ModuleInfo } from '../core/api/module-api.service';
import { LicenseApiService, LicenseStatus, ModuleRequestInfo, RequestResult } from '../core/api/license-api.service';

/**
 * Settings > Modules (admin only). A module is usable when it is in the license NIWE issued for this installation AND an admin has activated it.
 * Modules that are not in the license are shown locked with a Request button: the request goes to NIWE, who send a new license file that is
 * uploaded here. Activation is permanent — an active module can't be deactivated — and activating a module also activates the modules it
 * works together with. Hotel also adds the "Charge to room" payment method to the POS and Restaurant.
 */
@Component({
  selector: 'app-modules',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PaperButtonComponent],
  template: `
    <app-shell [narrow]="true">
      <div class="flex justify-between align-center gap-3">
        <h3 class="mb-1">{{ 'Modules' | translate }}</h3>
        <app-paper-button [label]="'Print PDF' | translate" path="/modules/list-pdf" file="niwe-erp-modules.pdf" cls="btn-outline-primary"></app-paper-button>
      </div>
      <p class="text-muted mb-3">
        {{ 'Your license decides which modules you may use; you then activate the ones your business needs. A module that is locked or not activated is hidden from the menu for everyone.' | translate }}
        <strong>{{ 'Activation is permanent: once a module is activated it can\\'t be deactivated.' | translate }}</strong>
        {{ 'Sales, Purchases and Accounting are core: always active. Products and stock, customers, suppliers, staff and settings are shared by every module and are always available.' | translate }}
      </p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="alsoOn.length">
        {{ 'Also activated, because it works together with the module you just activated: {p0}.' | translate: { p0: (names(keys(alsoOn))) } }}
      </div>
      <div class="alert alert-success" *ngIf="hotelJustEnabled">
        {{ 'Hotel is active. Set it up in this order: first' | translate }}
        <a routerLink="/admin/hotel/room-types">{{ 'room types' | translate }}</a> {{ '(name and nightly rate), then' | translate }}
        <a routerLink="/admin/hotel/rooms">{{ 'rooms' | translate }}</a>{{ ', then check the' | translate }}
        <a routerLink="/admin/hotel/settings">{{ 'hotel settings' | translate }}</a> {{ '(accounting mode, tax, times). The "Charge to room" payment method is now available in the POS and the Restaurant.' | translate }}
      </div>

      <div class="card mb-3" *ngIf="lic">
        <strong>{{ 'License' | translate }}</strong>
        <div class="alert alert-danger mt-2" *ngIf="!lic.keyConfigured">
          {{ 'This build of the application has no license key, so licenses cannot be installed. Ask NIWE for a build that includes it.' | translate }}
        </div>
        <div class="alert alert-warning mt-2" *ngIf="lic.devAllModules">
          {{ 'The developer switch is on: every module counts as licensed. This must never be used in a deployment.' | translate }}
        </div>
        <div class="alert alert-danger mt-2" *ngIf="lic.problem">{{ lic.problem }}</div>
        <div class="alert alert-danger mt-2" *ngIf="lic.valid && lic.expired">
          {{ 'The license expired on {p0}. The modules in it are locked until a new license is installed. Your data is untouched.' | translate: { p0: (lic.expiresAt | date: 'mediumDate') } }}
        </div>
        <div class="alert alert-warning mt-2" *ngIf="lic.expiringSoon">
          {{ 'The license expires on {p0} ({p1} days). Ask NIWE for a renewal in good time.' | translate: { p0: (lic.expiresAt | date: 'mediumDate'), p1: (lic.daysLeft) } }}
        </div>
        <div class="mt-2" *ngIf="lic.valid">
          {{ 'Licensed to' | translate }} <strong>{{ lic.customer }}</strong> {{ '— license {p0}, number {p1}, issued {p2},' | translate: { p0: (lic.licenseId), p1: (lic.serial), p2: (lic.issuedAt | date: 'mediumDate') } }}
          <span *ngIf="lic.expiresAt">{{ 'expires {p0}' | translate: { p0: (lic.expiresAt | date: 'mediumDate') } }}</span>
          <span *ngIf="!lic.expiresAt">{{ 'no expiry date' | translate }}</span>.
        </div>
        <div class="mt-2 text-muted" *ngIf="!lic.hasLicense">
          {{ 'No license is installed. Only the core modules are available. To get one, send NIWE the installation id below and the modules you bought.' | translate }}
        </div>
        <div class="mt-2">
          {{ 'Installation id:' | translate }} <code>{{ lic.installationId }}</code>
          <button type="button" class="btn btn-link" (click)="copy(lic.installationId)">{{ copied === lic.installationId ? ('Copied' | translate) : ('Copy' | translate) }}</button>
        </div>
        <div class="mt-2" *ngIf="lic.keyConfigured">
          <label class="btn btn-primary mb-0" [class.disabled]="installing">
            {{ installing ? ('Installing…' | translate) : ('Upload license file' | translate) }}
            <input type="file" accept=".niwelic,.json,.txt" style="display:none" (change)="onFile($event)" [disabled]="installing" />
          </label>
          <span class="text-success ml-2" *ngIf="installedOk">{{ 'License installed.' | translate }}</span>
        </div>
        <details class="mt-2" *ngIf="lic.history.length">
          <summary class="text-muted">{{ 'Installed licenses ({p0})' | translate: { p0: (lic.history.length) } }}</summary>
          <div style="overflow-x:auto"><table class="table mt-2">
            <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'License' | translate }}</th><th>{{ 'Issued' | translate }}</th><th>{{ 'Expires' | translate }}</th><th>{{ 'Modules' | translate }}</th><th>{{ 'Installed' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let h of lic.history">
                <td>{{ h.serial }}</td>
                <td>{{ h.licenseId }}</td>
                <td>{{ h.issuedAt | date: 'mediumDate' }}</td>
                <td>{{ h.expiresAt ? (h.expiresAt | date: 'mediumDate') : '—' }}</td>
                <td>{{ h.modules }}</td>
                <td>{{ h.installedAt | date: 'medium' }}<span *ngIf="h.installedBy"> {{ 'by {p0}' | translate: { p0: (h.installedBy) } }}</span></td>
                <td><span class="badge badge-success" *ngIf="h.active">{{ 'In use' | translate }}</span></td>
              </tr>
            </tbody>
          </table></div>
        </details>
      </div>

      <div class="alert alert-success" *ngIf="lastRequest">
        {{ lastRequest.emailNote }}
        <div *ngIf="!lastRequest.emailSent" class="mt-2">
          <pre style="white-space:pre-wrap; margin:0;">{{ lastRequest.text }}</pre>
          <button type="button" class="btn btn-link" (click)="copy(lastRequest.text)">{{ copied === lastRequest.text ? ('Copied' | translate) : ('Copy this text' | translate) }}</button>
        </div>
      </div>

      <div class="card mb-2" *ngFor="let m of modules">
        <div class="flex justify-between align-center gap-3">
          <div>
            <strong>{{ m.icon }} {{ m.name }}</strong>
            <span class="badge ml-2" [class.badge-success]="m.state === 'ACTIVE' || m.state === 'CORE'" [class.badge-secondary]="m.state === 'AVAILABLE' || m.state === 'LOCKED'"
                  [class.badge-warning]="m.state === 'REQUESTED' || m.state === 'EXPIRED'">{{ label(m) }}</span>
            <div class="text-muted" style="font-size:0.9rem; max-width:560px;">{{ m.description }}</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.requires.length">
              {{ 'Works together with {p0} — activating {p1} activates {p2} too.' | translate: { p0: (names(m.requires)), p1: (m.name), p2: (names(m.requires)) } }}
            </div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="neededBy(m).length">
              {{ 'Needed by {p0} — activating that one activates this one too.' | translate: { p0: (names(neededBy(m))) } }}
            </div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.state === 'LOCKED'">{{ 'Not in your license.' | translate }}</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.state === 'EXPIRED'">{{ 'In a license that has expired.' | translate }}</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.state === 'REQUESTED'">{{ 'Requested on {p0}. Waiting for the new license from NIWE.' | translate: { p0: (m.requestedAt | date: 'mediumDate') } }}</div>
          </div>
          <span class="text-muted" style="white-space:nowrap;" *ngIf="m.state === 'CORE'">{{ 'Core / Always on' | translate }}</span>
          <span class="text-muted" style="white-space:nowrap;" *ngIf="m.state === 'ACTIVE'">{{ 'Active — can\\'t be turned off' | translate }}</span>
          <button type="button" class="btn btn-primary" style="white-space:nowrap;" *ngIf="m.state === 'AVAILABLE'"
                  [disabled]="busyKey === m.key" (click)="activate(m)">{{ 'Activate' | translate }}</button>
          <button type="button" class="btn btn-secondary" style="white-space:nowrap;" *ngIf="m.state === 'LOCKED' || m.state === 'EXPIRED'"
                  [disabled]="busyKey === m.key" (click)="openRequest(m)">{{ m.state === 'EXPIRED' ? ('Request renewal' | translate) : ('Request' | translate) }}</button>
          <button type="button" class="btn btn-link" style="white-space:nowrap;" *ngIf="m.state === 'REQUESTED'"
                  [disabled]="busyKey === m.key" (click)="withdraw(m)">{{ 'Withdraw request' | translate }}</button>
        </div>
        <div class="mt-2" *ngIf="requesting === m.key">
          <label class="text-muted" style="font-size:0.9rem;">{{ 'Note for NIWE (optional)' | translate }}</label>
          <textarea class="form-control" rows="2" maxlength="500" [(ngModel)]="note" placeholder="Why you need it, how many users, when"></textarea>
          <div class="mt-2">
            <button type="button" class="btn btn-primary" [disabled]="busyKey === m.key" (click)="sendRequest(m)">{{ 'Send request' | translate }}</button>
            <button type="button" class="btn btn-link" (click)="requesting = null">{{ 'Cancel' | translate }}</button>
          </div>
        </div>
      </div>
      <p class="text-muted" *ngIf="modules.length === 0 && !error">{{ 'Loading…' | translate }}</p>
    </app-shell>
  `,
})
export class ModulesComponent implements OnInit {
  modules: ModuleInfo[] = [];
  lic: LicenseStatus | null = null;
  error: string | null = null;
  busyKey: string | null = null;
  hotelJustEnabled = false;
  /** Modules that were switched on together with the one just switched (integrated modules). */
  alsoOn: ModuleInfo[] = [];
  requesting: string | null = null;
  note = '';
  lastRequest: RequestResult | null = null;
  installing = false;
  installedOk = false;
  copied: string | null = null;

  constructor(private modulesApi: ModuleApiService, private licenseApi: LicenseApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.modulesApi.list().subscribe({
      next: (m) => (this.modules = m),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load modules'),
    });
    this.licenseApi.status().subscribe({
      next: (s) => (this.lic = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the license'),
    });
  }

  label(m: ModuleInfo): string {
    switch (m.state) {
      case 'CORE': return 'Core';
      case 'ACTIVE': return 'Active';
      case 'AVAILABLE': return 'In your license';
      case 'REQUESTED': return 'Requested';
      case 'EXPIRED': return 'License expired';
      default: return 'Locked';
    }
  }

  activate(m: ModuleInfo): void {
    const together = m.requires.filter((k) => !this.modules.find((x) => x.key === k)?.enabled);
    const extra = together.length ? ` This also activates ${this.names(together)}.` : '';
    if (!confirm(`Activate ${m.name}? Activation is permanent — once activated, a module can't be deactivated.${extra}`)) {
      return;
    }
    this.error = null;
    this.hotelJustEnabled = false;
    this.alsoOn = [];
    this.busyKey = m.key;
    this.modulesApi.set(m.key, true).subscribe({
      next: (all) => {
        this.busyKey = null;
        const wasOn = new Set(this.modules.filter((x) => x.enabled).map((x) => x.key));
        this.modules = all;
        this.alsoOn = all.filter((x) => x.enabled && !wasOn.has(x.key) && x.key !== m.key);
        this.hotelJustEnabled = m.key === 'HOTEL';
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Could not activate the module';
      },
    });
  }

  openRequest(m: ModuleInfo): void {
    this.error = null;
    this.lastRequest = null;
    this.note = '';
    this.requesting = m.key;
  }

  sendRequest(m: ModuleInfo): void {
    this.error = null;
    this.busyKey = m.key;
    this.licenseApi.request(m.key, this.note.trim() || null).subscribe({
      next: (r) => {
        this.busyKey = null;
        this.requesting = null;
        this.lastRequest = r;
        this.reload();
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Could not send the request';
      },
    });
  }

  withdraw(m: ModuleInfo): void {
    if (!confirm(`Withdraw the request for ${m.name}?`)) return;
    this.error = null;
    this.lastRequest = null;
    this.busyKey = m.key;
    this.licenseApi.requests().subscribe({
      next: (list) => {
        const pending = list.find((r: ModuleRequestInfo) => r.module === m.key && r.status === 'PENDING');
        if (!pending) {
          this.busyKey = null;
          this.reload();
          return;
        }
        this.licenseApi.withdraw(pending.id).subscribe({
          next: () => {
            this.busyKey = null;
            this.reload();
          },
          error: (err) => {
            this.busyKey = null;
            this.error = err?.error?.message ?? 'Could not withdraw the request';
          },
        });
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Could not load the requests';
      },
    });
  }

  async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.error = null;
    this.installedOk = false;
    this.installing = true;
    try {
      const text = await file.text();
      this.licenseApi.install(text).subscribe({
        next: (s) => {
          this.installing = false;
          this.installedOk = true;
          this.lic = s;
          this.modulesApi.list().subscribe((m) => (this.modules = m));
        },
        error: (err) => {
          this.installing = false;
          this.error = err?.error?.message ?? 'The license could not be installed';
        },
      });
    } catch {
      this.installing = false;
      this.error = 'The file could not be read';
    }
  }

  copy(text: string): void {
    try {
      navigator.clipboard.writeText(text).then(() => {
        this.copied = text;
        setTimeout(() => (this.copied = null), 2000);
      });
    } catch {
      // clipboard not available: the text is on the screen to select by hand
    }
  }

  names(keys: string[]): string {
    return keys.map((k) => this.modules.find((m) => m.key === k)?.name ?? k).join(', ');
  }

  keys(list: ModuleInfo[]): string[] {
    return list.map((m) => m.key);
  }

  /** Modules that need this one. */
  neededBy(m: ModuleInfo): string[] {
    return this.modules.filter((o) => o.requires.includes(m.key)).map((o) => o.key);
  }
}
