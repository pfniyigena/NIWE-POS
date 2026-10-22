import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
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
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-1">Modules</h3>
      <p class="text-muted mb-3">
        Your license decides which modules you may use; you then activate the ones your business needs. A module that is locked or not activated is hidden from the menu for everyone.
        <strong>Activation is permanent: once a module is activated it can't be deactivated.</strong>
        Sales, Purchases and Accounting are core: always active.
        Products and stock, customers, suppliers, staff and settings are shared by every module and are always available.
      </p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="alsoOn.length">
        Also activated, because it works together with the module you just activated: {{ names(keys(alsoOn)) }}.
      </div>
      <div class="alert alert-success" *ngIf="hotelJustEnabled">
        Hotel is active. Set it up in this order: first
        <a routerLink="/admin/hotel/room-types">room types</a> (name and nightly rate), then
        <a routerLink="/admin/hotel/rooms">rooms</a>, then check the
        <a routerLink="/admin/hotel/settings">hotel settings</a> (accounting mode, tax, times).
        The "Charge to room" payment method is now available in the POS and the Restaurant.
      </div>

      <div class="card mb-3" *ngIf="lic">
        <strong>License</strong>
        <div class="alert alert-danger mt-2" *ngIf="!lic.keyConfigured">
          This build of the application has no license key, so licenses cannot be installed. Ask NIWE for a build that includes it.
        </div>
        <div class="alert alert-warning mt-2" *ngIf="lic.devAllModules">
          The developer switch is on: every module counts as licensed. This must never be used in a deployment.
        </div>
        <div class="alert alert-danger mt-2" *ngIf="lic.problem">{{ lic.problem }}</div>
        <div class="alert alert-danger mt-2" *ngIf="lic.valid && lic.expired">
          The license expired on {{ lic.expiresAt | date: 'mediumDate' }}. The modules in it are locked until a new license is installed. Your data is untouched.
        </div>
        <div class="alert alert-warning mt-2" *ngIf="lic.expiringSoon">
          The license expires on {{ lic.expiresAt | date: 'mediumDate' }} ({{ lic.daysLeft }} days). Ask NIWE for a renewal in good time.
        </div>
        <div class="mt-2" *ngIf="lic.valid">
          Licensed to <strong>{{ lic.customer }}</strong> — license {{ lic.licenseId }}, number {{ lic.serial }},
          issued {{ lic.issuedAt | date: 'mediumDate' }},
          <span *ngIf="lic.expiresAt">expires {{ lic.expiresAt | date: 'mediumDate' }}</span>
          <span *ngIf="!lic.expiresAt">no expiry date</span>.
        </div>
        <div class="mt-2 text-muted" *ngIf="!lic.hasLicense">
          No license is installed. Only the core modules are available. To get one, send NIWE the installation id below and the modules you bought.
        </div>
        <div class="mt-2">
          Installation id: <code>{{ lic.installationId }}</code>
          <button type="button" class="btn btn-link" (click)="copy(lic.installationId)">{{ copied === lic.installationId ? 'Copied' : 'Copy' }}</button>
        </div>
        <div class="mt-2" *ngIf="lic.keyConfigured">
          <label class="btn btn-primary mb-0" [class.disabled]="installing">
            {{ installing ? 'Installing…' : 'Upload license file' }}
            <input type="file" accept=".niwelic,.json,.txt" style="display:none" (change)="onFile($event)" [disabled]="installing" />
          </label>
          <span class="text-success ml-2" *ngIf="installedOk">License installed.</span>
        </div>
        <details class="mt-2" *ngIf="lic.history.length">
          <summary class="text-muted">Installed licenses ({{ lic.history.length }})</summary>
          <div style="overflow-x:auto"><table class="table mt-2">
            <thead><tr><th>Number</th><th>License</th><th>Issued</th><th>Expires</th><th>Modules</th><th>Installed</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let h of lic.history">
                <td>{{ h.serial }}</td>
                <td>{{ h.licenseId }}</td>
                <td>{{ h.issuedAt | date: 'mediumDate' }}</td>
                <td>{{ h.expiresAt ? (h.expiresAt | date: 'mediumDate') : '—' }}</td>
                <td>{{ h.modules }}</td>
                <td>{{ h.installedAt | date: 'medium' }}<span *ngIf="h.installedBy"> by {{ h.installedBy }}</span></td>
                <td><span class="badge badge-success" *ngIf="h.active">In use</span></td>
              </tr>
            </tbody>
          </table></div>
        </details>
      </div>

      <div class="alert alert-success" *ngIf="lastRequest">
        {{ lastRequest.emailNote }}
        <div *ngIf="!lastRequest.emailSent" class="mt-2">
          <pre style="white-space:pre-wrap; margin:0;">{{ lastRequest.text }}</pre>
          <button type="button" class="btn btn-link" (click)="copy(lastRequest.text)">{{ copied === lastRequest.text ? 'Copied' : 'Copy this text' }}</button>
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
              Works together with {{ names(m.requires) }} — activating {{ m.name }} activates {{ names(m.requires) }} too.
            </div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="neededBy(m).length">
              Needed by {{ names(neededBy(m)) }} — activating that one activates this one too.
            </div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.state === 'LOCKED'">Not in your license.</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.state === 'EXPIRED'">In a license that has expired.</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.state === 'REQUESTED'">Requested on {{ m.requestedAt | date: 'mediumDate' }}. Waiting for the new license from NIWE.</div>
          </div>
          <span class="text-muted" style="white-space:nowrap;" *ngIf="m.state === 'CORE'">Core / Always on</span>
          <span class="text-muted" style="white-space:nowrap;" *ngIf="m.state === 'ACTIVE'">Active — can't be turned off</span>
          <button type="button" class="btn btn-primary" style="white-space:nowrap;" *ngIf="m.state === 'AVAILABLE'"
                  [disabled]="busyKey === m.key" (click)="activate(m)">Activate</button>
          <button type="button" class="btn btn-secondary" style="white-space:nowrap;" *ngIf="m.state === 'LOCKED' || m.state === 'EXPIRED'"
                  [disabled]="busyKey === m.key" (click)="openRequest(m)">{{ m.state === 'EXPIRED' ? 'Request renewal' : 'Request' }}</button>
          <button type="button" class="btn btn-link" style="white-space:nowrap;" *ngIf="m.state === 'REQUESTED'"
                  [disabled]="busyKey === m.key" (click)="withdraw(m)">Withdraw request</button>
        </div>
        <div class="mt-2" *ngIf="requesting === m.key">
          <label class="text-muted" style="font-size:0.9rem;">Note for NIWE (optional)</label>
          <textarea class="form-control" rows="2" maxlength="500" [(ngModel)]="note" placeholder="Why you need it, how many users, when"></textarea>
          <div class="mt-2">
            <button type="button" class="btn btn-primary" [disabled]="busyKey === m.key" (click)="sendRequest(m)">Send request</button>
            <button type="button" class="btn btn-link" (click)="requesting = null">Cancel</button>
          </div>
        </div>
      </div>
      <p class="text-muted" *ngIf="modules.length === 0 && !error">Loading…</p>
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
