import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { Warehouse, WarehouseLocation } from '../core/models';

@Component({
  selector: 'app-register-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit register' : 'New register' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" />
        </div>
        <div class="form-group">
          <label class="form-label">Location</label>
          <input class="form-control" name="location" [(ngModel)]="location" />
        </div>
        <div class="form-group">
          <label class="form-label">Warehouse</label>
          <select class="form-select" name="warehouseId" [(ngModel)]="warehouseId" (ngModelChange)="onWarehouseChange()">
            <option [ngValue]="null">Not assigned (uses the primary warehouse)</option>
            <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Sales rung at this terminal deduct stock from this warehouse (its default location,
            unless a specific one is picked below); a return against one of its sales restocks the
            same spot. Leave unassigned to keep using the primary warehouse, same as before this
            setting existed.
          </div>
        </div>
        <div class="form-group" *ngIf="warehouseId">
          <label class="form-label">Location</label>
          <select class="form-select" name="defaultLocationId" [(ngModel)]="defaultLocationId">
            <option [ngValue]="null">Default location for this warehouse</option>
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
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/registers">Cancel</a>
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
  error: string | null = null;

  constructor(
    private registerApi: RegisterApiService,
    private warehouseApi: WarehouseApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.warehouseApi.listAll().subscribe({
      next: (w) => (this.warehouses = w.filter((x) => x.active)),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load warehouses'),
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
          this.loadLocations();
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load register'),
    });
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
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load locations'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = { name: this.name, location: this.location, warehouseId: this.warehouseId, defaultLocationId: this.defaultLocationId };
    if (this.id) {
      this.registerApi.updateRegister(this.id, payload).subscribe({
        next: () => this.router.navigate(['/registers']),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to save register'),
      });
    } else {
      this.registerApi.createRegister(payload).subscribe({
        next: () => this.router.navigate(['/registers']),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to create register'),
      });
    }
  }
}
