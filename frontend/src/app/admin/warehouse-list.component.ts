import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { Warehouse } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Admin > Inventory > Warehouses — Fourier: "multi-location/multi-warehouse tracking ..." Every
 * sale, stock adjustment, and purchase receipt implicitly targets whichever warehouse is flagged
 * "Primary" below unless a register says otherwise (Admin > Registers) or multi-warehouse is on
 * (Admin > Inventory > Settings). "+ New warehouse" is disabled while that setting is off — see
 * WarehouseService#create's own javadoc for why.
 */
@Component({
  selector: 'app-warehouse-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Warehouses' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/warehouses/new" *ngIf="multiWarehouseEnabled">{{ '+ New warehouse' | translate }}</a>
        <span class="text-muted" style="font-size:0.85rem;" *ngIf="!multiWarehouseEnabled">
          {{ 'Turn on "Multi-warehouse" in Inventory settings to add another warehouse.' | translate }}
        </span>
      </div>
      <p class="text-muted" style="font-size:0.85rem;">
        {{ 'Every sale, stock adjustment, and purchase receipt moves stock against whichever warehouse is marked "Primary" below, unless a register is assigned a different one.' | translate }}
      </p>
      <table>
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Address' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let w of warehouses">
            <td>{{ w.name }} <span class="text-muted" *ngIf="w.primary" style="font-size:0.8rem;">{{ '★ Primary' | translate }}</span></td>
            <td>{{ w.address || '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="w.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!w.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td class="flex gap-2 align-center">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/warehouses', w.id, 'edit']">{{ 'Edit' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary" (click)="deactivate(w)" *ngIf="w.active && !w.primary">{{ 'Deactivate' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="activate(w)" *ngIf="!w.active">{{ 'Activate' | translate }}</button>
              <span class="text-muted" style="font-size:0.8rem;" *ngIf="w.primary">{{ 'Can\\'t be deactivated' | translate }}</span>
            </td>
          </tr>
          <tr *ngIf="warehouses.length === 0">
            <td colspan="4" class="text-muted">{{ 'No warehouses yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class WarehouseListComponent implements OnInit {
  warehouses: Warehouse[] = [];
  multiWarehouseEnabled = false;
  error: string | null = null;

  constructor(private warehouseApi: WarehouseApiService, private settingsApi: StoreSettingsApiService) {}

  ngOnInit(): void {
    this.load();
    this.settingsApi.get().subscribe({
      next: (s) => (this.multiWarehouseEnabled = s.multiWarehouseEnabled),
      error: () => {
        /* Non-fatal — the "+ New warehouse" link just stays hidden, same as if it were off. */
      },
    });
  }

  private load(): void {
    this.warehouseApi.listAll().subscribe({
      next: (w) => (this.warehouses = w),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load warehouses')),
    });
  }

  activate(w: Warehouse): void {
    this.error = null;
    this.warehouseApi.setActive(w.id, true).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to activate warehouse')),
    });
  }

  deactivate(w: Warehouse): void {
    this.error = null;
    this.warehouseApi.setActive(w.id, false).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to deactivate warehouse')),
    });
  }
}
