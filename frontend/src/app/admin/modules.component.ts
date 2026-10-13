import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ModuleApiService, ModuleInfo } from '../core/api/module-api.service';

/**
 * Settings > Modules (admin only): activate the optional modules your business uses. A module that is not activated is
 * hidden everywhere. Activation is permanent — an active module can't be deactivated — and activating a module also
 * activates the modules it works together with. Hotel also adds the "Charge to room" payment method to the POS and Restaurant.
 */
@Component({
  selector: 'app-modules',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-1">Modules</h3>
      <p class="text-muted mb-3">
        Activate the modules your business uses. A module that is not activated is hidden from the menu for everyone.
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
      <div class="card mb-2" *ngFor="let m of modules">
        <div class="flex justify-between align-center gap-3">
          <div>
            <strong>{{ m.icon }} {{ m.name }}</strong>
            <span class="badge ml-2" [class.badge-success]="m.enabled" [class.badge-secondary]="!m.enabled">{{ m.core ? 'Core' : m.enabled ? 'Active' : 'Not activated' }}</span>
            <div class="text-muted" style="font-size:0.9rem; max-width:560px;">{{ m.description }}</div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="m.requires.length">
              Works together with {{ names(m.requires) }} — activating {{ m.name }} activates {{ names(m.requires) }} too.
            </div>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="neededBy(m).length">
              Needed by {{ names(neededBy(m)) }} — activating that one activates this one too.
            </div>
          </div>
          <span class="text-muted" style="white-space:nowrap;" *ngIf="m.core">Core / Always on</span>
          <span class="text-muted" style="white-space:nowrap;" *ngIf="!m.core && m.enabled">Active — can't be turned off</span>
          <button type="button" class="btn btn-primary" style="white-space:nowrap;" *ngIf="!m.core && !m.enabled"
                  [disabled]="busyKey === m.key" (click)="activate(m)">Activate</button>
        </div>
      </div>
      <p class="text-muted" *ngIf="modules.length === 0 && !error">Loading…</p>
    </app-shell>
  `,
})
export class ModulesComponent implements OnInit {
  modules: ModuleInfo[] = [];
  error: string | null = null;
  busyKey: string | null = null;
  hotelJustEnabled = false;
  /** Modules that were switched on together with the one just switched (integrated modules). */
  alsoOn: ModuleInfo[] = [];

  constructor(private modulesApi: ModuleApiService) {}

  ngOnInit(): void {
    this.modulesApi.list().subscribe({
      next: (m) => (this.modules = m),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load modules'),
    });
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
