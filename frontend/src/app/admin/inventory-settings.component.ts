import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { LocationChoice, LocationChoicesService } from '../core/api/location-choices.service';

/**
 * Admin > Inventory > Settings (Task: "Inventory Setting (Example: allow negative stock)").
 * Edits the same singleton settings row as BusinessDetailsComponent/SystemSettingsComponent —
 * see StoreSettingsApiService — but through its own independent request/endpoint
 * (updateInventorySettings), same "each screen only ever saves the fields it owns" convention as
 * those two.
 */
@Component({
  selector: 'app-inventory-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">Inventory settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Inventory settings saved.</div>
      <form (ngSubmit)="submit()" *ngIf="loaded">
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="allowNegativeStock" [(ngModel)]="allowNegativeStock" /> Allow negative stock
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            When checked (default), a sale can go through even if it takes a product's stock below
            zero, and a manual stock adjustment can too — today's behavior, unchanged. Uncheck this
            to block both: checkout will refuse to complete a sale that would oversell a product,
            and Stock adjustments will refuse a correction that would take one negative.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="productBreakdownEnabled" [(ngModel)]="productBreakdownEnabled" /> Product packaging &amp; breakdown
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Lets you break one product down into smaller sellable units, e.g. a bottle of whiskey
            into shots, or a case of beer into bottles — see Inventory &gt; Packaging. Unchecked
            (default) means the feature is fully off: packaging rules can still be configured, but
            running a breakdown is refused until this is turned on.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="multiWarehouseEnabled" [(ngModel)]="multiWarehouseEnabled" /> Multi-warehouse
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Unchecked (default): the store stays on one warehouse (its default "Main" one); you can
            still add further locations to it, and every register keeps selling from it. Checked:
            a second warehouse can be created, but no sale may complete against the Main warehouse
            any more — every register must be assigned a different warehouse (Admin &gt; Registers)
            before it can ring a sale. Purchases always land on the Main warehouse's default
            location either way, and Stock Transfers between locations are unaffected by this
            setting.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Costing method</label>
          <select class="form-select" name="costingMethod" [(ngModel)]="costingMethod">
            <option value="WEIGHTED_AVERAGE">Weighted average (recommended)</option>
            <option value="LAST_PURCHASE">Last purchase cost</option>
            <option value="MANUAL">Manual — never change cost automatically</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            How a product's cost price follows what you buy. Weighted average blends each delivery into the
            existing cost by quantity; last purchase cost simply takes the latest delivery's unit cost; manual
            leaves cost alone (edit it on the product). Landed cost on receiving needs weighted average or last purchase.
          </div>
        </div>
        <!-- Multi-warehouse phase 4 — which location Restaurant orders and wastage draw stock from.
             Shown only when there is a real choice (or one is already saved, so it can be changed back). -->
        <div class="form-group" *ngIf="locationChoices.length > 2 || restaurantLocationId !== null">
          <label class="form-label">Restaurant kitchen location</label>
          <select class="form-select" name="restaurantLocationId" [(ngModel)]="restaurantLocationId">
            <option [ngValue]="null">Default (main warehouse sales stock)</option>
            <option *ngFor="let l of locationChoices" [ngValue]="l.id">{{ l.label }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Restaurant orders (menu items and recipe ingredients) and wastage take their stock from
            this location. Leave on the default to keep using the main warehouse's sales stock.
          </div>
        </div>
        <button class="btn btn-primary" type="submit" [disabled]="saving">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
      </form>
    </app-shell>
  `,
})
export class InventorySettingsComponent implements OnInit {
  loaded = false;
  saving = false;
  error: string | null = null;
  saved = false;

  allowNegativeStock = true;
  productBreakdownEnabled = false;
  multiWarehouseEnabled = false;
  restaurantLocationId: number | null = null;
  costingMethod = 'WEIGHTED_AVERAGE';
  locationChoices: LocationChoice[] = [];

  constructor(private settingsApi: StoreSettingsApiService, private locationChoicesService: LocationChoicesService) {}

  ngOnInit(): void {
    this.locationChoicesService.load().subscribe((c) => (this.locationChoices = c));
    this.settingsApi.get().subscribe({
      next: (s) => {
        this.allowNegativeStock = s.allowNegativeStock;
        this.productBreakdownEnabled = s.productBreakdownEnabled;
        this.multiWarehouseEnabled = s.multiWarehouseEnabled;
        this.restaurantLocationId = s.restaurantLocationId ?? null;
        this.costingMethod = s.costingMethod ?? 'WEIGHTED_AVERAGE';
        this.loaded = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load inventory settings'),
    });
  }

  submit(): void {
    this.error = null;
    this.saved = false;
    this.saving = true;
    this.settingsApi
      .updateInventorySettings({
        allowNegativeStock: this.allowNegativeStock,
        productBreakdownEnabled: this.productBreakdownEnabled,
        multiWarehouseEnabled: this.multiWarehouseEnabled,
        restaurantLocationId: this.restaurantLocationId,
        costingMethod: this.costingMethod,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.saved = true;
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Failed to save inventory settings';
        },
      });
  }
}
