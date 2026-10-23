import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ManufacturingOrderApiService } from '../../core/api/manufacturing-order-api.service';
import { BomApiService } from '../../core/api/bom-api.service';
import { OverheadCostApiService } from '../../core/api/overhead-cost-api.service';
import { LocationChoice, LocationChoicesService } from '../../core/api/location-choices.service';
import { Bom, ManufacturingOrder, OverheadCost } from '../../core/models';

/** Manufacturing > Manufacturing orders > New — always creates a DRAFT; confirm/produce/cancel all happen on the detail screen. */
@Component({
  selector: 'app-manufacturing-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ editing ? 'Change ' + (existing?.orderNumber || 'manufacturing order') : 'New manufacturing order' }}</h3>
      <div class="alert alert-info" *ngIf="editing && existing && existing.status !== 'DRAFT'">This order is {{ existing.status.replace('_', ' ').toLowerCase() }}: only the planned quantity, the date and the note can change. The recipe, indirect costs and location are fixed once it is confirmed.</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Bill of materials</label>
          <select class="form-select" name="bomId" [(ngModel)]="bomId" required [disabled]="locked">
            <option [ngValue]="null" disabled>Select a bill of materials…</option>
            <option *ngFor="let b of boms" [ngValue]="b.id">{{ b.productName }} — {{ b.code }}{{ b.name ? ' (' + b.name + ')' : '' }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="boms.length === 0">
            No active bill of materials yet — create one under Manufacturing &gt; Bill of Materials first.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Planned quantity</label>
          <input class="form-control" style="max-width:160px;" type="number" min="0.0001" step="any" name="quantityPlanned" [(ngModel)]="quantityPlanned" required />
        </div>
        <div class="form-group">
          <label class="form-label">Scheduled date (optional)</label>
          <app-date-picker name="scheduledDate" [(ngModel)]="scheduledDate" ></app-date-picker>
        </div>
        <!-- Multi-warehouse phase 4 — shown only once there is a real choice (more than the default
             Receiving + Main Stock). Blank keeps the classic behaviour. -->
        <div class="form-group" *ngIf="locationChoices.length > 2">
          <label class="form-label">Produce at</label>
          <select class="form-select" name="locationId" [(ngModel)]="locationId" [disabled]="locked">
            <option [ngValue]="null">Default (main warehouse sales stock)</option>
            <option *ngFor="let l of locationChoices" [ngValue]="l.id">{{ l.label }}</option>
          </select>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Components are consumed from, and finished goods added to, this location.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Note</label>
          <textarea class="form-control" name="note" rows="2" [(ngModel)]="note"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">Indirect costs to apply (optional)</label>
          <div *ngFor="let o of overheadCosts">
            <label class="form-label">
              <input type="checkbox" [name]="'oc' + o.id" [checked]="selectedOverheadIds.has(o.id)" [disabled]="locked" (change)="toggleOverhead(o.id)" />
              {{ o.name }} ({{ methodLabel(o.allocationMethod) }} — {{ o.rate | number: '1.2-2' }})
            </label>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="overheadCosts.length === 0">
            No active indirect costs yet — set some up under Manufacturing &gt; Indirect Costs if you want overhead applied to this order.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ editing ? 'Save changes' : 'Create draft' }}</button>
          <a class="btn btn-link" [routerLink]="editing && existing ? ['/admin/manufacturing/orders', existing.id] : '/admin/manufacturing/orders'">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class ManufacturingOrderFormComponent implements OnInit {
  bomId: number | null = null;
  quantityPlanned = 1;
  scheduledDate: string | null = null;
  note = '';
  locationChoices: LocationChoice[] = [];
  locationId: number | null = null;
  boms: Bom[] = [];
  overheadCosts: OverheadCost[] = [];
  selectedOverheadIds = new Set<number>();
  error: string | null = null;
  editing = false;
  existing: ManufacturingOrder | null = null;

  get locked(): boolean {
    return this.editing && !!this.existing && this.existing.status !== 'DRAFT';
  }

  constructor(
    private manufacturingOrderApi: ManufacturingOrderApiService,
    private bomApi: BomApiService,
    private overheadCostApi: OverheadCostApiService,
    private locationChoicesService: LocationChoicesService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.locationChoicesService.load().subscribe((c) => (this.locationChoices = c));
    const idParam = this.route.snapshot.paramMap.get('id');
    this.bomApi.listAll().subscribe({
      next: (b) => {
        this.boms = b.filter((bom) => bom.active || bom.id === this.bomId);
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load bills of materials'),
    });
    if (idParam) {
      this.editing = true;
      this.manufacturingOrderApi.getById(Number(idParam)).subscribe({
        next: (o) => {
          this.existing = o;
          this.bomId = o.bomId;
          this.quantityPlanned = o.quantityPlanned;
          this.scheduledDate = o.scheduledDate;
          this.note = o.note ?? '';
          this.locationId = o.locationId;
          this.selectedOverheadIds = new Set(o.overheads.map((x) => x.overheadCostId).filter((x): x is number => x != null));
          // the recipe of an order may have been switched off since: keep it selectable
          this.bomApi.listAll().subscribe((b) => (this.boms = b.filter((bom) => bom.active || bom.id === this.bomId)));
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load the manufacturing order'),
      });
    }
    this.overheadCostApi.listActive().subscribe({
      next: (o) => (this.overheadCosts = o),
      error: () => {},
    });
  }

  toggleOverhead(id: number): void {
    if (this.selectedOverheadIds.has(id)) {
      this.selectedOverheadIds.delete(id);
    } else {
      this.selectedOverheadIds.add(id);
    }
  }

  methodLabel(method: string): string {
    switch (method) {
      case 'FLAT':
        return 'Flat';
      case 'PER_UNIT':
        return 'Per unit';
      case 'PERCENT_OF_MATERIAL_COST':
        return '% of material';
      default:
        return method;
    }
  }

  submit(): void {
    if (!this.bomId) {
      this.error = 'Select a bill of materials';
      return;
    }
    this.error = null;
    const payload = {
      bomId: this.bomId,
      quantityPlanned: this.quantityPlanned,
      scheduledDate: this.scheduledDate ? new Date(this.scheduledDate).toISOString().slice(0, 10) : null,
      note: this.note || null,
      locationId: this.locationChoices.length > 2 ? this.locationId : null,
      overheadCostIds: Array.from(this.selectedOverheadIds),
    };
    if (this.editing && this.existing) {
      const draft = this.existing.status === 'DRAFT';
      const update = {
        quantityPlanned: this.quantityPlanned,
        scheduledDate: payload.scheduledDate,
        note: payload.note,
        ...(draft ? { bomId: this.bomId, locationId: this.locationChoices.length > 2 ? this.locationId : null, overheadCostIds: payload.overheadCostIds } : {}),
      };
      this.manufacturingOrderApi.update(this.existing.id, update).subscribe({
        next: (mo) => this.router.navigate(['/admin/manufacturing/orders', mo.id]),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to save the manufacturing order'),
      });
      return;
    }
    this.manufacturingOrderApi.create(payload).subscribe({
      next: (mo) => this.router.navigate(['/admin/manufacturing/orders', mo.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to create manufacturing order'),
    });
  }
}
