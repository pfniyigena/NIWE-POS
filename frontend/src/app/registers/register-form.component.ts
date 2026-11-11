import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, Warehouse, WarehouseLocation } from '../core/models';

import { T } from '../core/i18n/t';
@Component({
  selector: 'app-register-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? ('Edit register' | translate) : ('New register' | translate) }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Name' | translate }}</label>
          <input class="form-control" name="name" [(ngModel)]="name" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Location' | translate }}</label>
          <input class="form-control" name="location" [(ngModel)]="location" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Warehouse' | translate }}</label>
          <select class="form-select" name="warehouseId" [(ngModel)]="warehouseId" (ngModelChange)="onWarehouseChange()">
            <option [ngValue]="null">{{ 'Not assigned (uses the primary warehouse)' | translate }}</option>
            <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Sales rung at this terminal deduct stock from this warehouse (its default location, unless a specific one is picked below); a return against one of its sales restocks the same spot. Leave unassigned to keep using the primary warehouse, same as before this setting existed.' | translate }}
          </div>
        </div>
        <div class="form-group" *ngIf="warehouseId">
          <label class="form-label">{{ 'Location' | translate }}</label>
          <select class="form-select" name="defaultLocationId" [(ngModel)]="defaultLocationId">
            <option [ngValue]="null">{{ 'Default location for this warehouse' | translate }}</option>
            <option *ngFor="let loc of locations" [ngValue]="loc.id">{{ loc.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Fourier: "if multi-location then sale start from location" — narrows this terminal to one
            specific SALES location of the warehouse above (only a SALES location can ever back a
            sale — see Admin &gt; Warehouses for RECEIVING/STORAGE/DAMAGED locations), when it has
            more than one. Leave as "Default location" to keep targeting that warehouse's own SALES
            location, same as today.
          </div>
        </div>
        <h5 class="mb-2" style="margin-top:1.25rem;">{{ 'Till rules' | translate }}</h5>
        <div class="form-group">
          <label style="display:flex; gap:0.5rem; align-items:center;">
            <input type="checkbox" class="rg-active" name="active" [(ngModel)]="active" />
            <span>{{ 'In use' | translate }}</span>
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            {{ 'Switch a till off to stop new shifts from being opened on it (for example while it is being repaired). Its past sales and reports stay as they are. A till with an open shift has to be closed first.' | translate }}
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Usual opening float' | translate }}</label>
          <input class="form-control rg-default-float" type="number" step="0.01" min="0" name="defaultFloat" [(ngModel)]="defaultFloat" [placeholder]="'No suggestion' | translate" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">{{ 'Filled in automatically when a shift is opened here.' | translate }}</div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Highest opening float allowed' | translate }}</label>
          <input class="form-control rg-max-float" type="number" step="0.01" min="0" name="maxFloat" [(ngModel)]="maxFloat" [placeholder]="'No limit' | translate" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Who can work this till' | translate }}</label>
          <div class="text-muted" style="font-size:0.8rem; margin-bottom:0.35rem;">
            {{ 'Tick the people who may sell here. Leave all unticked to let anyone who can sell use it. Managers can always work any till.' | translate }}
          </div>
          <label *ngFor="let u of staff" style="display:flex; gap:0.5rem; align-items:center; margin-bottom:0.2rem;">
            <input type="checkbox" class="rg-cashier" [checked]="allowedIds.includes(u.id)" (change)="toggleCashier(u.id)" [name]="'cashier' + u.id" />
            <span>{{ u.fullName }}</span>
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" routerLink="/registers">{{ 'Cancel' | translate }}</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class RegisterFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  location = '';
  warehouseId: number | null = null;
  warehouses: Warehouse[] = [];
  defaultLocationId: number | null = null;
  locations: WarehouseLocation[] = [];
  active = true;
  defaultFloat: number | null = null;
  maxFloat: number | null = null;
  allowedIds: number[] = [];
  staff: AppUser[] = [];
  error: string | null = null;

  constructor(
    private registerApi: RegisterApiService,
    private warehouseApi: WarehouseApiService,
    private userApi: UserApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.warehouseApi.listAll().subscribe({
      next: (w) => (this.warehouses = w.filter((x) => x.active)),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load warehouses')),
    });
    this.userApi.list().subscribe({
      next: (u) => (this.staff = u.filter((x) => x.active)),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load staff list')),
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.registerApi.listRegisters().subscribe({
      next: (registers) => {
        const found = registers.find((r) => r.id === this.id);
        if (found) {
          this.name = found.name;
          this.location = found.location ?? '';
          this.warehouseId = found.warehouseId;
          this.defaultLocationId = found.defaultLocationId;
          this.active = found.active !== false;
          this.defaultFloat = found.defaultFloat ?? null;
          this.maxFloat = found.maxFloat ?? null;
          this.allowedIds = [...(found.allowedCashierIds ?? [])];
          this.loadLocations();
        }
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load register')),
    });
  }

  toggleCashier(id: number): void {
    this.allowedIds = this.allowedIds.includes(id) ? this.allowedIds.filter((x) => x !== id) : [...this.allowedIds, id];
  }

  /** Reloads this warehouse's own locations whenever the admin picks a different one — the previous selection almost never belongs to the new warehouse, so it's cleared rather than silently kept. */
  onWarehouseChange(): void {
    this.defaultLocationId = null;
    this.loadLocations();
  }

  private loadLocations(): void {
    if (!this.warehouseId) {
      this.locations = [];
      return;
    }
    // Fourier: "Type drives behavior directly" — a register can only ever be pinned to a SALES
    // location (the backend re-checks this too — see RegisterService#resolveDefaultLocation), so
    // RECEIVING/STORAGE/DAMAGED locations never even show up in this picker.
    this.warehouseApi.listLocations(this.warehouseId).subscribe({
      next: (locs) => (this.locations = locs.filter((l) => l.active && l.type === 'SALES')),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load locations')),
    });
  }

  submit(): void {
    this.error = null;
    const payload = {
      name: this.name,
      location: this.location,
      warehouseId: this.warehouseId,
      defaultLocationId: this.defaultLocationId,
      active: this.active,
      defaultFloat: this.defaultFloat === null || (this.defaultFloat as unknown) === '' ? null : Number(this.defaultFloat),
      maxFloat: this.maxFloat === null || (this.maxFloat as unknown) === '' ? null : Number(this.maxFloat),
      allowedCashierIds: this.allowedIds,
    };
    if (this.id) {
      this.registerApi.updateRegister(this.id, payload).subscribe({
        next: () => this.router.navigate(['/registers']),
        error: (err) => (this.error = err?.error?.message ?? T('Failed to save register')),
      });
    } else {
      this.registerApi.createRegister(payload).subscribe({
        next: () => this.router.navigate(['/registers']),
        error: (err) => (this.error = err?.error?.message ?? T('Failed to create register')),
      });
    }
  }
}
