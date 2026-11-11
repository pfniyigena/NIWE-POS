import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ManufacturingOrderApiService } from '../../core/api/manufacturing-order-api.service';
import { downloadBlob } from '../../core/download-file';
import { ComponentAvailability, ManufacturingOrder } from '../../core/models';

import { q4 } from '../../core/qty';
import { T } from '../../core/i18n/t';
/**
 * Manufacturing > Manufacturing orders > detail — where a DRAFT is confirmed or cancelled, and
 * where a CONFIRMED/PARTIALLY_PRODUCED order is actually produced against. Mirrors
 * PurchaseOrderDetailComponent's "process against this order" + history-table layout. Each
 * "Record production" click can cover any quantity up to what's still remaining, so a partial run
 * today and the rest later — same partial-run shape as a purchase order's receipt or a sales
 * order's fulfillment.
 */
@Component({
  selector: 'app-manufacturing-order-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="mo as o">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ o.orderNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(o.status)">{{ o.status.replace('_', ' ') }}</span>
          </div>
          <div class="flex gap-2">
            <a class="btn btn-outline-secondary btn-edit" *ngIf="o.status !== 'PRODUCED' && o.status !== 'CANCELLED'" [routerLink]="['/admin/manufacturing/orders', o.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-outline-secondary btn-jobcard" (click)="printJobCard()">{{ 'Print job card' | translate }}</button>
            <button class="btn btn-outline-secondary btn-copy" (click)="copy()">{{ 'Copy as new draft' | translate }}</button>
            <button class="btn btn-primary" *ngIf="o.status === 'DRAFT'" (click)="confirm()">{{ 'Confirm' | translate }}</button>
            <button class="btn btn-outline-danger" *ngIf="o.status === 'DRAFT' || o.status === 'CONFIRMED'" (click)="cancel()">{{ 'Cancel order' | translate }}</button>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">{{ 'Product' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.productName }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Bill of materials' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.bomCode || '—' }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.locationName">
            <div class="stat-label">{{ 'Location' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.warehouseName ? o.warehouseName + ' / ' : '' }}{{ o.locationName }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Routing' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.routingName || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Planned' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.quantityPlanned }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Produced' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.quantityProduced }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Scheduled' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.scheduledDate ? (o.scheduledDate | date: 'mediumDate') : '—' }}</div>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">{{ 'Total material cost' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalMaterialCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Total overhead cost' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalOverheadCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Total labour cost' | translate }}</div>
            <div class="stat-value tl-labour" style="font-size:1.1rem;">{{ o.totalLabourCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Total machine cost' | translate }}</div>
            <div class="stat-value tl-machine" style="font-size:1.1rem;">{{ o.totalMachineCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Total cost' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalCost | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.totalScrapped > 0">
            <div class="stat-label">{{ 'Scrapped' | translate }}</div>
            <div class="stat-value tl-scrap" style="font-size:1.1rem;">{{ o.totalScrapped }} ({{ o.totalScrapCost | number: '1.2-2' }})</div>
          </div>
          <div class="stat-tile" *ngIf="o.yieldPercent != null">
            <div class="stat-label">{{ 'Yield' | translate }}</div>
            <div class="stat-value tl-yield" style="font-size:1.1rem;">{{ o.yieldPercent | number: '1.0-1' }}%</div>
          </div>
          <div class="stat-tile" *ngIf="o.totalByProductCredit > 0">
            <div class="stat-label">{{ 'By-product credit' | translate }}</div>
            <div class="stat-value tl-credit" style="font-size:1.1rem;">{{ o.totalByProductCredit | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">{{ 'Unit cost' | translate }}</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.unitCost != null ? (o.unitCost | number: '1.2-2') : '—' }}</div>
          </div>
        </div>

        <ng-container *ngIf="o.status !== 'DRAFT' && o.status !== 'CANCELLED'">
          <h4 class="mb-2">{{ 'Planned cost' | translate }}</h4>
          <table class="table-sm mb-3 planned-cost">
            <thead><tr><th></th><th class="text-end">{{ 'Material' | translate }}</th><th class="text-end">{{ 'Labour' | translate }}</th><th class="text-end">{{ 'Machine' | translate }}</th><th class="text-end">{{ 'Overhead' | translate }}</th><th class="text-end">{{ 'Total' | translate }}</th></tr></thead>
            <tbody>
              <tr>
                <td>{{ 'Planned for {p0}' | translate: { p0: (o.quantityPlanned) } }}</td>
                <td class="text-end">{{ o.plannedMaterialCost | number: '1.2-2' }}</td>
                <td class="text-end">{{ o.plannedLabourCost | number: '1.2-2' }}</td>
                <td class="text-end">{{ o.plannedMachineCost | number: '1.2-2' }}</td>
                <td class="text-end">{{ o.plannedOverheadCost | number: '1.2-2' }}</td>
                <td class="text-end">{{ o.plannedTotalCost | number: '1.2-2' }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <ng-container *ngIf="o.operations.length > 0">
          <h4 class="mb-2">{{ 'Operations' | translate }}</h4>
          <table class="table-sm mb-3 op-table">
            <thead><tr><th>{{ 'Step' | translate }}</th><th>{{ 'Operation' | translate }}</th><th>{{ 'Workstation' | translate }}</th><th class="text-end">{{ 'Min / unit' | translate }}</th><th class="text-end">{{ 'Machine rate / h' | translate }}</th><th class="text-end">{{ 'Labour rate / h' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let op of o.operations" class="op-row">
                <td>{{ op.sequence }}</td>
                <td>{{ op.name }}</td>
                <td>{{ op.workstationName || '—' }}</td>
                <td class="text-end">{{ op.minutesPerUnit | number: '1.0-2' }}</td>
                <td class="text-end">{{ op.machineRate | number: '1.2-2' }}</td>
                <td class="text-end">{{ op.labourRate | number: '1.2-2' }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <p class="text-muted mb-3" *ngIf="o.note">{{ o.note }}</p>

        <ng-container *ngIf="avail && avail.quantityRemaining > 0">
          <h4 class="mb-2">{{ 'Components for the {p0} still to make' | translate: { p0: (avail.quantityRemaining) } }}</h4>
          <div class="alert alert-warning avail-warning" *ngIf="!avail.canProduceAll">
            {{ 'Stock is short for this order. {p0}' | translate: { p0: (avail.maxProducible > 0 ? 'You can make ' + avail.maxProducible + ' of the ' + avail.quantityRemaining + ' with what is on hand.' : 'Nothing can be made until the missing components arrive.') } }}
            <span *ngIf="avail.allowNegativeStock"> {{ '(Negative stock is allowed, so production will still go through.)' | translate }}</span>
          </div>
          <div class="alert alert-success avail-ok" *ngIf="avail.canProduceAll">{{ 'All components are in stock for the rest of this order.' | translate }}</div>
          <table class="table-sm mb-3 avail-table">
            <thead><tr><th>{{ 'Component' | translate }}</th><th class="text-end">{{ 'Per unit' | translate }}</th><th class="text-end">{{ 'Needed' | translate }}</th><th class="text-end">{{ 'On hand' | translate }}</th><th class="text-end">{{ 'Short by' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of avail.lines" class="avail-row" [class.avail-short]="l.shortBy > 0">
                <td>{{ l.componentName }}</td>
                <td class="text-end">{{ l.perUnit }}</td>
                <td class="text-end">{{ l.required }}</td>
                <td class="text-end">{{ l.available != null ? l.available : ('not tracked' | translate) }}</td>
                <td class="text-end"><span *ngIf="l.shortBy > 0" class="text-danger">{{ l.shortBy }}</span><span *ngIf="l.shortBy === 0">—</span></td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <ng-container *ngIf="o.overheads.length > 0">
          <h4 class="mb-2">{{ 'Indirect costs applied' | translate }}</h4>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>{{ 'Name' | translate }}</th>
                <th>{{ 'Method' | translate }}</th>
                <th class="text-end">{{ 'Rate' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let oh of o.overheads">
                <td>{{ oh.name }}</td>
                <td>{{ methodLabel(oh.method) }}</td>
                <td class="text-end">{{ oh.rate | number: '1.2-2' }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <ng-container *ngIf="o.status === 'CONFIRMED' || o.status === 'PARTIALLY_PRODUCED'">
          <h4 class="mb-2">{{ 'Record production' | translate }}</h4>
          <div class="alert alert-danger" *ngIf="produceError">{{ produceError }}</div>
          <div class="flex gap-2 align-center mb-3">
            <div>
              <label class="form-label">{{ 'Quantity to produce now (remaining: {p0})' | translate: { p0: (remaining()) } }}</label>
              <input type="number" step="any" class="form-control" style="width:140px;" min="0.0001" [max]="remaining()" [(ngModel)]="produceQuantity" name="produceQuantity" />
            </div>
            <button class="btn btn-primary" style="margin-top:1.5rem;" (click)="recordProduction()">{{ 'Record production' | translate }}</button>
            <button class="btn btn-outline-secondary btn-max" style="margin-top:1.5rem;" *ngIf="avail && !avail.canProduceAll && avail.maxProducible > 0" (click)="produceQuantity = avail.maxProducible">{{ 'Use what stock allows ({p0})' | translate: { p0: (avail.maxProducible) } }}</button>
          </div>
          <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
            <div>
              <label class="form-label">{{ 'Scrapped / rejected units (optional)' | translate }}</label>
              <input type="number" step="any" class="form-control scrap-qty" style="width:140px;" min="0" [(ngModel)]="scrapQuantity" name="scrapQuantity" />
            </div>
            <div class="grow" style="min-width:220px;">
              <label class="form-label">{{ 'Reason' | translate }}</label>
              <input class="form-control scrap-reason" [(ngModel)]="scrapReason" name="scrapReason" maxlength="255" [placeholder]="'e.g. burnt, wrong size, failed check' | translate" />
            </div>
          </div>
          <div class="mb-3" *ngIf="avail && avail.lines.length > 0">
            <label class="form-label">{{ 'Actual quantity used (optional — leave blank for the recipe\\'s quantity for the units started)' | translate }}</label>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div *ngFor="let l of avail.lines" style="min-width:150px;">
                <small class="text-muted">{{ l.componentName }}</small>
                <input type="number" step="any" class="form-control form-control-sm actual-use" min="0" [(ngModel)]="actualUse[l.componentId]" [name]="'au' + l.componentId" [placeholder]="'' + (l.perUnit * ((produceQuantity || 0) + (scrapQuantity || 0)))" />
              </div>
            </div>
          </div>
          <div class="mb-3" *ngIf="o.operations.length > 0">
            <label class="form-label">{{ 'Actual minutes taken (optional — leave blank to use the planned minutes)' | translate }}</label>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <div *ngFor="let op of o.operations" style="min-width:150px;">
                <small class="text-muted">{{ op.sequence }}. {{ op.name }}</small>
                <input type="number" class="form-control form-control-sm actual-min" min="0" step="0.01" [(ngModel)]="actualMinutes[op.sequence]" [name]="'am' + op.sequence" [placeholder]="(op.minutesPerUnit * (produceQuantity || 0)) | number: '1.0-2'" />
              </div>
            </div>
          </div>
        </ng-container>

        <ng-container *ngIf="o.runs.length > 0">
          <h4 class="mb-2">{{ 'Production run history' | translate }}</h4>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>{{ 'Date' | translate }}</th>
                <th class="text-end">{{ 'Quantity produced' | translate }}</th>
                <th class="text-end">{{ 'Material cost' | translate }}</th>
                <th class="text-end">{{ 'Overhead cost' | translate }}</th>
                <th class="text-end">{{ 'Labour cost' | translate }}</th>
                <th class="text-end">{{ 'Machine cost' | translate }}</th>
                <th class="text-end">{{ 'Total cost' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let run of o.runs">
                <tr>
                  <td>{{ run.producedAt | date: 'medium' }}</td>
                  <td class="text-end">{{ run.quantityProduced }}</td>
                  <td class="text-end">{{ run.materialCost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ run.overheadCost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ run.labourCost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ run.machineCost | number: '1.2-2' }}</td>
                  <td class="text-end">{{ run.totalCost | number: '1.2-2' }}</td>
                </tr>
                <tr>
                  <td colspan="7" style="padding-left:2rem;">
                    <span class="text-muted" style="font-size:0.85rem;">{{ 'Consumed:' | translate }} </span>
                    <span *ngFor="let c of run.consumptions; let last = last" class="run-use" style="font-size:0.85rem;">
                      {{ c.componentName }} × {{ c.quantityConsumed }}<span *ngIf="c.varianceQuantity !== 0" class="run-variance" [class.text-danger]="c.varianceQuantity > 0"> {{ '(recipe {p0}, {p1}{p2} = {p3})' | translate: { p0: (c.plannedQuantity), p1: (c.varianceQuantity > 0 ? '+' : ''), p2: (c.varianceQuantity), p3: (c.varianceCost | number: '1.2-2') } }}</span>{{ !last ? ', ' : '' }}
                    </span>
                    <div *ngIf="run.quantityScrapped > 0" class="run-scrap" style="font-size:0.85rem;">
                      {{ 'Scrapped {p0}{p1} · cost {p2} · yield {p3}%' | translate: { p0: (run.quantityScrapped), p1: (run.scrapReason ? ' — ' + run.scrapReason : ''), p2: (run.scrapCost | number: '1.2-2'), p3: (run.yieldPercent | number: '1.0-1') } }}
                    </div>
                    <div *ngFor="let bp of run.byProducts" class="run-byproduct" style="font-size:0.85rem;">
                      {{ 'By-product: {p0} × {p1}{p2}' | translate: { p0: (bp.productName), p1: (bp.quantity), p2: (bp.costValue > 0 ? ' (takes ' + (bp.costValue | number: '1.2-2') + ' of the cost)' : '') } }}
                    </div>
                    <div *ngFor="let ro of run.operations" class="run-op" style="font-size:0.85rem;">
                      {{ '{p0}. {p1}{p2}: {p3} min (planned {p4}) — labour {p5}, machine {p6}' | translate: { p0: (ro.sequence), p1: (ro.name), p2: (ro.workstationName ? ' @ ' + ro.workstationName : ''), p3: (ro.actualMinutes | number: '1.0-2'), p4: (ro.plannedMinutes | number: '1.0-2'), p5: (ro.labourCost | number: '1.2-2'), p6: (ro.machineCost | number: '1.2-2') } }}
                    </div>
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class ManufacturingOrderDetailComponent implements OnInit {
  mo: ManufacturingOrder | null = null;
  error: string | null = null;
  produceError: string | null = null;
  produceQuantity = 1;
  avail: ComponentAvailability | null = null;
  actualMinutes: Record<number, number | null> = {};
  scrapQuantity: number | null = null;
  scrapReason = '';
  actualUse: Record<number, number | null> = {};

  constructor(
    private manufacturingOrderApi: ManufacturingOrderApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.manufacturingOrderApi.getById(id).subscribe({
      next: (mo) => {
        this.mo = mo;
        this.produceQuantity = this.remaining();
        this.manufacturingOrderApi.availability(id).subscribe({ next: (a) => (this.avail = a), error: () => (this.avail = null) });
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load manufacturing order')),
    });
  }

  remaining(): number {
    return this.mo ? q4(this.mo.quantityPlanned - this.mo.quantityProduced) : 0;
  }

  confirm(): void {
    if (!this.mo) return;
    this.error = null;
    this.manufacturingOrderApi.confirm(this.mo.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to confirm manufacturing order')),
    });
  }

  printJobCard(): void {
    if (!this.mo) return;
    this.manufacturingOrderApi.jobCard(this.mo.id).subscribe({
      next: (blob) => {
        const w = window.open(URL.createObjectURL(blob), '_blank');
        if (!w) downloadBlob(blob, `job-card-${this.mo?.orderNumber}.pdf`);
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to build the job card')),
    });
  }

  copy(): void {
    if (!this.mo) return;
    this.manufacturingOrderApi.copy(this.mo.id).subscribe({
      next: (n) => this.router.navigate(['/admin/manufacturing/orders', n.id]),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to copy the manufacturing order')),
    });
  }

  cancel(): void {
    if (!this.mo) return;
    if (!confirm(T('Cancel manufacturing order {p0}?', { p0: this.mo.orderNumber }))) return;
    this.error = null;
    this.manufacturingOrderApi.cancel(this.mo.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to cancel manufacturing order')),
    });
  }

  recordProduction(): void {
    if (!this.mo) return;
    if (!this.produceQuantity || this.produceQuantity <= 0) {
      this.produceError = 'Enter a quantity greater than zero';
      return;
    }
    this.produceError = null;
    const operationMinutes = Object.entries(this.actualMinutes)
      .filter(([, v]) => v !== null && v !== undefined && (v as unknown) !== '')
      .map(([seq, v]) => ({ sequence: Number(seq), minutes: Number(v) }));
    const componentQuantities = Object.entries(this.actualUse)
      .filter(([, v]) => v !== null && v !== undefined && (v as unknown) !== '')
      .map(([id, v]) => ({ componentId: Number(id), quantity: Number(v) }));
    this.manufacturingOrderApi.produce(this.mo.id, {
      quantity: this.produceQuantity,
      operationMinutes,
      quantityScrapped: this.scrapQuantity || 0,
      scrapReason: this.scrapReason.trim() || null,
      componentQuantities,
    }).subscribe({
      next: () => {
        this.actualMinutes = {};
        this.actualUse = {};
        this.scrapQuantity = null;
        this.scrapReason = '';
        this.load();
      },
      error: (err) => (this.produceError = err?.error?.message ?? 'Failed to record production'),
    });
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

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'CONFIRMED':
        return 'badge-secondary';
      case 'PARTIALLY_PRODUCED':
        return 'badge-warning';
      case 'PRODUCED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
