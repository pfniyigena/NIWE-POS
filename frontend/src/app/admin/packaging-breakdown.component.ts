import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PackagingApiService } from '../core/api/packaging-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { LocationChoice, LocationChoicesService } from '../core/api/location-choices.service';
import { PackagingBreakdown, PackagingRule } from '../core/models';

/**
 * Admin > Inventory > Breakdown stock — Fourier: "I want to add in the POS: Product Packaging &
 * Breakdown / Unit Conversion ... If not activated ignore packaging." Pick an active rule, say how
 * many parent units to break down, and it runs immediately (no draft/confirm step — see the
 * backend PackagingService#breakdown's javadoc for why). The run button is disabled with an
 * explanatory banner when StoreSettings.productBreakdownEnabled is off — same "form-level gating,
 * not menu hiding" treatment DiscountMode already gets elsewhere in this app, rather than hiding
 * this screen's sidenav link outright.
 *
 * <p>Arriving with `?ruleId=` (from the rule list's "Break down" link) preselects that rule and
 * narrows the history table below to it; with no ruleId, the history table shows every rule's
 * breakdowns.
 */
@Component({
  selector: 'app-packaging-breakdown',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">Break down stock</h3>
        <a class="btn btn-link" routerLink="/admin/packaging/rules">Packaging rules</a>
      </div>

      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="success">{{ success }}</div>
      <div class="alert alert-warning" *ngIf="loaded && !productBreakdownEnabled">
        Product packaging &amp; breakdown is turned off — enable "Product packaging &amp; breakdown" in
        <a routerLink="/admin/inventory/settings">Inventory settings</a> before running a breakdown.
        You can still browse and configure rules.
      </div>

      <div class="card mb-4" style="padding:1rem; max-width:560px;" *ngIf="loaded">
        <div class="form-group mb-3">
          <label class="form-label">Packaging rule</label>
          <select class="form-select" name="ruleId" [(ngModel)]="selectedRuleId" (ngModelChange)="onRuleChange()">
            <option [ngValue]="null">— Select a rule —</option>
            <option *ngFor="let r of activeRules" [ngValue]="r.id">
              {{ r.parentProductName }} &rarr; {{ r.childProductName }} ({{ r.unitsPerParent }} per unit)
            </option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="activeRules.length === 0">
            No active packaging rules — create one on the Packaging rules screen first.
          </div>
        </div>
        <div class="form-group mb-3" *ngIf="selectedRule">
          <label class="form-label">Quantity of {{ selectedRule.parentProductName }} to break down</label>
          <input type="number" class="form-control" style="max-width:160px;" min="1" name="parentQuantity" [(ngModel)]="parentQuantity" />
          <div class="text-muted" style="font-size:0.85rem; margin-top:0.25rem;" *ngIf="parentQuantity > 0">
            Produces <strong>{{ parentQuantity * selectedRule.unitsPerParent }}</strong> x {{ selectedRule.childProductName }}.
          </div>
        </div>
        <!-- Multi-warehouse phase 4 — only when there is a real choice; blank = classic behaviour. -->
        <div class="form-group mb-3" *ngIf="selectedRule && locationChoices.length > 2">
          <label class="form-label">At location</label>
          <select class="form-select" name="locationId" [(ngModel)]="locationId">
            <option [ngValue]="null">Default (main warehouse sales stock)</option>
            <option *ngFor="let l of locationChoices" [ngValue]="l.id">{{ l.label }}</option>
          </select>
        </div>
        <div class="form-group mb-3" *ngIf="selectedRule">
          <label class="form-label">Note (optional)</label>
          <input class="form-control" name="note" [(ngModel)]="note" placeholder="e.g. Opened for Friday night shift" />
        </div>
        <button class="btn btn-primary" type="button" (click)="submit()" [disabled]="!selectedRule || !productBreakdownEnabled || busy">
          {{ busy ? 'Breaking down…' : 'Break down now' }}
        </button>
      </div>

      <h4 class="mb-2">{{ filterRuleId ? 'History for this rule' : 'Breakdown history' }}</h4>
      <table class="table-sm">
        <thead>
          <tr>
            <th>When</th>
            <th>Parent</th>
            <th class="text-end">Qty</th>
            <th></th>
            <th>Child</th>
            <th class="text-end">Qty produced</th>
            <th class="text-end">Child cost/unit</th>
            <th>Location</th>
            <th>Note</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of history">
            <td>{{ b.createdAt | date: 'medium' }}</td>
            <td>{{ b.parentProductName }}</td>
            <td class="text-end">{{ b.parentQuantity }}</td>
            <td class="text-muted">&rarr;</td>
            <td>{{ b.childProductName }}</td>
            <td class="text-end">{{ b.childQuantityProduced }}</td>
            <td class="text-end">{{ b.childUnitCost != null ? (b.childUnitCost | number: '1.2-2') : '—' }}</td>
            <td class="text-muted">{{ b.locationName ? (b.warehouseName ? b.warehouseName + ' / ' : '') + b.locationName : '—' }}</td>
            <td class="text-muted">{{ b.note }}</td>
          </tr>
          <tr *ngIf="history.length === 0">
            <td colspan="9" class="text-muted">No breakdowns recorded yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class PackagingBreakdownComponent implements OnInit {
  loaded = false;
  busy = false;
  error: string | null = null;
  success: string | null = null;
  productBreakdownEnabled = false;

  activeRules: PackagingRule[] = [];
  selectedRuleId: number | null = null;
  selectedRule: PackagingRule | null = null;
  parentQuantity = 1;
  note = '';
  locationChoices: LocationChoice[] = [];
  locationId: number | null = null;

  filterRuleId: number | null = null;
  history: PackagingBreakdown[] = [];

  constructor(
    private packagingApi: PackagingApiService,
    private settingsApi: StoreSettingsApiService,
    private locationChoicesService: LocationChoicesService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.locationChoicesService.load().subscribe((c) => (this.locationChoices = c));
    const ruleIdParam = this.route.snapshot.queryParamMap.get('ruleId');
    this.filterRuleId = ruleIdParam ? Number(ruleIdParam) : null;

    this.settingsApi.get().subscribe({
      next: (s) => (this.productBreakdownEnabled = s.productBreakdownEnabled),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load inventory settings'),
    });

    this.packagingApi.listRules(true).subscribe({
      next: (rules) => {
        this.activeRules = rules;
        if (this.filterRuleId && rules.some((r) => r.id === this.filterRuleId)) {
          this.selectedRuleId = this.filterRuleId;
          this.onRuleChange();
        }
        this.loaded = true;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load packaging rules';
        this.loaded = true;
      },
    });

    this.loadHistory();
  }

  private loadHistory(): void {
    this.packagingApi.listBreakdowns(this.filterRuleId ?? undefined).subscribe({
      next: (h) => (this.history = h),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load breakdown history'),
    });
  }

  onRuleChange(): void {
    this.selectedRule = this.activeRules.find((r) => r.id === this.selectedRuleId) ?? null;
    this.parentQuantity = 1;
  }

  submit(): void {
    if (!this.selectedRule) return;
    if (!this.parentQuantity || this.parentQuantity < 1) {
      this.error = 'Quantity must be a positive number';
      return;
    }
    this.error = null;
    this.success = null;
    this.busy = true;
    this.packagingApi
      .runBreakdown({ ruleId: this.selectedRule.id, parentQuantity: this.parentQuantity, note: this.note || null, locationId: this.locationChoices.length > 2 ? this.locationId : null })
      .subscribe({
        next: (b) => {
          this.busy = false;
          this.success = `Broke down ${b.parentQuantity} x ${b.parentProductName} into ${b.childQuantityProduced} x ${b.childProductName}.`;
          this.note = '';
          this.loadHistory();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Failed to run breakdown';
        },
      });
  }
}
