import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { BranchApiService } from '../core/api/branch-api.service';
import { Branch, LocationType, WarehouseLocation } from '../core/models';

/** Admin > Inventory > Warehouses > New/Edit — see WarehouseListComponent's own javadoc. */
@Component({
  selector: 'app-warehouse-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit warehouse' : 'New warehouse' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Downtown Branch" required />
        </div>
        <div class="form-group">
          <label class="form-label">Address (optional)</label>
          <textarea class="form-control" name="address" [(ngModel)]="address" rows="2"></textarea>
        </div>
        <div class="form-group" *ngIf="branches.length > 1">
          <label class="form-label">Branch</label>
          <select class="form-control" style="max-width:260px;" name="branchId" [(ngModel)]="branchId">
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            The branch this warehouse belongs to. Sales from its registers and purchases received into it are tagged with this branch.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Low-stock threshold (optional)</label>
          <input class="form-control" style="max-width:160px;" type="number" min="0" step="1" name="lowStockThreshold" [(ngModel)]="lowStockThreshold" placeholder="Store default" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            A product is flagged low (dashboard, Stock On Hand report) and an alert email is sent when this
            warehouse's stock of it drops below this number. Leave blank to use the store-wide default.
          </div>
        </div>
        <div class="form-group" *ngIf="!primary">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active
          </label>
        </div>
        <div class="form-group" *ngIf="primary">
          <label class="form-label">
            <input type="checkbox" checked disabled /> Active
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            This is your primary warehouse — it can't be deactivated.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/warehouses">Cancel</a>
        </div>
      </form>

      <div *ngIf="id" class="mt-4">
        <h4 class="mb-2" style="font-size:1rem;">Locations</h4>
        <p class="text-muted mb-2" style="font-size:0.85rem;">
          Sub-areas within this warehouse — shelves, bins, a receiving area, and so on. Each one has a
          type that drives real behavior: every purchase/receipt lands on the warehouse's one RECEIVING
          location; a sale or return targets a SALES location (every warehouse always has at least one
          active); STORAGE is a plain holding area; DAMAGED stock is tracked but never counted as
          sellable. A register can be pinned to one specific SALES location here (Admin &gt; Registers),
          and Stock Transfers can move stock directly between any two locations, including two of these.
        </p>
        <div class="alert alert-danger" *ngIf="locationError">{{ locationError }}</div>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let loc of locations">
              <td>{{ loc.name }}</td>
              <td>
                <select class="form-control" style="display:inline-block; width:auto;" [ngModel]="loc.type" name="type-{{ loc.id }}"
                        (ngModelChange)="changeType(loc, $event)" [disabled]="loc.type === 'RECEIVING'">
                  <option value="RECEIVING" [disabled]="loc.type !== 'RECEIVING'">Receiving</option>
                  <option value="SALES">Sales</option>
                  <option value="STORAGE">Storage</option>
                  <option value="DAMAGED">Damaged</option>
                </select>
                <span class="text-muted" *ngIf="loc.type === 'RECEIVING'" style="font-size:0.8rem;">Can't be retyped</span>
              </td>
              <td>
                <span class="badge badge-success" *ngIf="loc.active">Active</span>
                <span class="badge badge-secondary" *ngIf="!loc.active">Inactive</span>
              </td>
              <td class="flex gap-2 align-center">
                <button class="btn btn-sm btn-outline-secondary" (click)="toggleActive(loc, false)" *ngIf="loc.active && loc.type !== 'RECEIVING'">Deactivate</button>
                <button class="btn btn-sm btn-outline-secondary" (click)="toggleActive(loc, true)" *ngIf="!loc.active">Activate</button>
                <span class="text-muted" style="font-size:0.8rem;" *ngIf="loc.active && loc.type === 'RECEIVING'">Can't be deactivated</span>
              </td>
            </tr>
            <tr *ngIf="locations.length === 0">
              <td colspan="4" class="text-muted">Loading...</td>
            </tr>
          </tbody>
        </table>
        <div class="flex gap-2 mt-2" style="align-items:flex-end;">
          <div class="form-group" style="margin-bottom:0; flex:1;">
            <label class="form-label">New location name</label>
            <input class="form-control" [(ngModel)]="newLocationName" name="newLocationName" placeholder="e.g. Shelf A" />
          </div>
          <div class="form-group" style="margin-bottom:0;">
            <label class="form-label">Type</label>
            <select class="form-control" [(ngModel)]="newLocationType" name="newLocationType">
              <option value="SALES">Sales</option>
              <option value="STORAGE">Storage</option>
              <option value="DAMAGED">Damaged</option>
            </select>
          </div>
          <button class="btn btn-outline-primary" type="button" (click)="addLocation()">+ Add location</button>
        </div>
      </div>
    </app-shell>
  `,
})
export class WarehouseFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  address: string | null = null;
  active = true;
  lowStockThreshold: number | null = null;
  /** Owning branch; the branch picker only shows when there is more than one active branch. */
  branchId: number | null = null;
  branches: Branch[] = [];
  /** Read-only here — see Warehouse model's own note on why this is never admin-settable. */
  primary = false;
  error: string | null = null;

  locations: WarehouseLocation[] = [];
  newLocationName = '';
  newLocationType: LocationType = 'SALES';
  locationError: string | null = null;

  constructor(private warehouseApi: WarehouseApiService, private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => {
      this.branches = b;
      if (this.branchId == null) {
        this.branchId = b.find((x) => x.headquarters)?.id ?? null;
      }
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.warehouseApi.listAll().subscribe({
      next: (warehouses) => {
        const w = warehouses.find((x) => x.id === this.id);
        if (!w) {
          this.error = 'Warehouse not found';
          return;
        }
        this.name = w.name;
        this.address = w.address;
        this.active = w.active;
        this.lowStockThreshold = w.lowStockThreshold ?? null;
        this.primary = w.primary;
        this.branchId = w.branchId ?? this.branchId;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load warehouse'),
    });
    this.loadLocations();
  }

  submit(): void {
    this.error = null;
    if (!this.name.trim()) {
      this.error = 'Name is required';
      return;
    }
    const payload = { name: this.name.trim(), address: this.address || null, active: this.primary ? true : this.active, lowStockThreshold: this.lowStockThreshold ?? null, branchId: this.branchId };
    const call = this.id ? this.warehouseApi.update(this.id, payload) : this.warehouseApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/warehouses']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save warehouse'),
    });
  }

  private loadLocations(): void {
    if (!this.id) return;
    this.warehouseApi.listLocations(this.id).subscribe({
      next: (locations) => (this.locations = locations),
      error: (err) => (this.locationError = err?.error?.message ?? 'Failed to load locations'),
    });
  }

  addLocation(): void {
    if (!this.id) return;
    const name = this.newLocationName.trim();
    if (!name) {
      this.locationError = 'Enter a location name';
      return;
    }
    this.locationError = null;
    this.warehouseApi.createLocation(this.id, { name, type: this.newLocationType }).subscribe({
      next: () => {
        this.newLocationName = '';
        this.loadLocations();
      },
      error: (err) => (this.locationError = err?.error?.message ?? 'Failed to add location'),
    });
  }

  toggleActive(loc: WarehouseLocation, active: boolean): void {
    this.locationError = null;
    this.warehouseApi.setLocationActive(loc.id, active).subscribe({
      next: () => this.loadLocations(),
      error: (err) => {
        this.locationError = err?.error?.message ?? 'Failed to update location';
        this.loadLocations();
      },
    });
  }

  /** Fourier: "Type drives behavior directly" — the backend enforces every invariant (can't retype away from RECEIVING at all, away from a last-active SALES location, or onto a second RECEIVING); this just surfaces whatever it says and reloads the real state either way. */
  changeType(loc: WarehouseLocation, newType: LocationType): void {
    this.locationError = null;
    this.warehouseApi.setLocationType(loc.id, newType).subscribe({
      next: () => this.loadLocations(),
      error: (err) => {
        this.locationError = err?.error?.message ?? 'Failed to change location type';
        this.loadLocations();
      },
    });
  }
}
