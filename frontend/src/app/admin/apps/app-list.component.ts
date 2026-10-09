import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AppsApiService } from '../../core/api/apps-api.service';
import { AppSummary } from '../../core/models';

/**
 * Admin > Settings > Apps (Task: "This will work as a plugin (or apps) to be installed and
 * configured"). A fixed, code-defined catalog (see the backend's AppCatalog — currently just
 * EFRIS Rwanda e-Invoicing) rather than a real marketplace: "Install" just creates this store's
 * own on/off + settings record for a built-in integration that already ships with this codebase.
 *
 * Deliberately a two-step lifecycle, not one: **Install** (start setting an app up) always leaves
 * it disabled; a separate **Enable** action is what actually turns it on, so an app with no
 * settings filled in yet never silently starts acting on real sales the moment it's installed.
 */
@Component({
  selector: 'app-app-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <h3 class="mb-1">Apps</h3>
      <p class="text-muted mb-3">
        Optional integrations you can install and configure for this store. Installing an app
        leaves it disabled until its settings are filled in and you turn it on.
      </p>
      <table>
        <thead>
          <tr>
            <th>App</th>
            <th>Category</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let a of apps">
            <td>
              <strong>{{ a.name }}</strong>
              <div class="text-muted" style="font-size:0.85rem; max-width:520px;">{{ a.description }}</div>
            </td>
            <td>{{ a.category }}</td>
            <td>
              <span class="badge badge-secondary" *ngIf="!a.installed">Not installed</span>
              <span class="badge badge-warning" *ngIf="a.installed && !a.enabled">Installed — disabled</span>
              <span class="badge badge-success" *ngIf="a.installed && a.enabled">Enabled</span>
            </td>
            <td>
              <div class="flex gap-2">
                <button *ngIf="!a.installed" class="btn btn-sm btn-primary" (click)="install(a)" [disabled]="busyKey === a.key">
                  Install
                </button>
                <a *ngIf="a.installed" class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/apps', a.key]">Configure</a>
                <button
                  *ngIf="a.installed && !a.enabled"
                  class="btn btn-sm btn-outline-secondary"
                  (click)="enable(a)"
                  [disabled]="busyKey === a.key"
                >
                  Enable
                </button>
                <button
                  *ngIf="a.installed && a.enabled"
                  class="btn btn-sm btn-outline-secondary"
                  (click)="disable(a)"
                  [disabled]="busyKey === a.key"
                >
                  Disable
                </button>
                <button *ngIf="a.installed" class="btn btn-sm btn-outline-danger" (click)="uninstall(a)" [disabled]="busyKey === a.key">
                  Uninstall
                </button>
              </div>
            </td>
          </tr>
          <tr *ngIf="apps.length === 0">
            <td colspan="4" class="text-muted">No apps available.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class AppListComponent implements OnInit {
  apps: AppSummary[] = [];
  error: string | null = null;
  /** Disables every action button for the one row currently mid-request, so a double-click can't fire two overlapping install/enable/uninstall calls. */
  busyKey: string | null = null;

  constructor(private appsApi: AppsApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.appsApi.list().subscribe({
      next: (apps) => (this.apps = apps),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load apps'),
    });
  }

  install(app: AppSummary): void {
    this.busyKey = app.key;
    this.appsApi.install(app.key).subscribe({
      next: () => {
        this.busyKey = null;
        this.load();
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Failed to install app';
      },
    });
  }

  enable(app: AppSummary): void {
    this.busyKey = app.key;
    this.appsApi.enable(app.key).subscribe({
      next: () => {
        this.busyKey = null;
        this.load();
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Failed to enable app';
      },
    });
  }

  disable(app: AppSummary): void {
    this.busyKey = app.key;
    this.appsApi.disable(app.key).subscribe({
      next: () => {
        this.busyKey = null;
        this.load();
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Failed to disable app';
      },
    });
  }

  uninstall(app: AppSummary): void {
    if (!confirm(`Uninstall ${app.name}? This removes all of its saved configuration and cannot be undone.`)) return;
    this.busyKey = app.key;
    this.appsApi.uninstall(app.key).subscribe({
      next: () => {
        this.busyKey = null;
        this.load();
      },
      error: (err) => {
        this.busyKey = null;
        this.error = err?.error?.message ?? 'Failed to uninstall app';
      },
    });
  }
}
