import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ReorderApiService } from '../core/api/reorder-api.service';
import { ReorderSuggestion } from '../core/models';

interface Row extends ReorderSuggestion {
  selected: boolean;
  quantity: number;
}

/**
 * Admin > Inventory > Reorder — products at or below their reorder level (sellable stock plus open
 * purchase orders). Tick the ones to buy, adjust quantities, and create a draft purchase requisition.
 */
@Component({
  selector: 'app-reorder-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Reorder' | translate }}</h3>
        <button class="btn btn-primary" type="button" [disabled]="busy || selectedCount === 0" (click)="createRequisition()">
          {{ 'Create requisition ({p0})' | translate: { p0: (selectedCount) } }}
        </button>
      </div>
      <p class="text-muted mb-3" style="font-size:0.85rem;">
        {{ 'Products whose sellable stock plus open purchase orders is at or below their reorder level. Set the level on each product\\'s form.' | translate }}
      </p>
      <table>
        <thead>
          <tr>
            <th><input type="checkbox" [checked]="allSelected" (change)="toggleAll($any($event.target).checked)" /></th>
            <th>{{ 'SKU' | translate }}</th>
            <th>{{ 'Product' | translate }}</th>
            <th>{{ 'Category' | translate }}</th>
            <th class="text-end">{{ 'On hand' | translate }}</th>
            <th class="text-end">{{ 'On order' | translate }}</th>
            <th class="text-end">{{ 'Reorder level' | translate }}</th>
            <th class="text-end">{{ 'Max' | translate }}</th>
            <th class="text-end" style="width:120px;">{{ 'Order qty' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of rows">
            <td><input type="checkbox" [(ngModel)]="r.selected" [name]="'s' + r.productId" /></td>
            <td>{{ r.sku }}</td>
            <td>{{ r.productName }}</td>
            <td>{{ r.categoryName }}</td>
            <td class="text-end">{{ r.onHand }}</td>
            <td class="text-end">{{ r.onOrder }}</td>
            <td class="text-end">{{ r.reorderLevel }}</td>
            <td class="text-end">{{ r.maxStockLevel }}</td>
            <td class="text-end"><input class="form-control text-end" type="number" step="any" min="0.0001" [(ngModel)]="r.quantity" [name]="'q' + r.productId" /></td>
          </tr>
          <tr *ngIf="rows.length === 0 && loaded">
            <td colspan="9" class="text-muted">{{ 'Nothing needs reordering.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ReorderListComponent implements OnInit {
  rows: Row[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;

  constructor(private api: ReorderApiService, private router: Router) {}

  get selectedCount(): number {
    return this.rows.filter((r) => r.selected).length;
  }

  get allSelected(): boolean {
    return this.rows.length > 0 && this.rows.every((r) => r.selected);
  }

  ngOnInit(): void {
    this.api.suggestions().subscribe({
      next: (s) => {
        this.rows = s.map((x) => ({ ...x, selected: true, quantity: x.suggestedQuantity }));
        this.loaded = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load reorder suggestions'),
    });
  }

  toggleAll(checked: boolean): void {
    this.rows.forEach((r) => (r.selected = checked));
  }

  createRequisition(): void {
    const lines = this.rows.filter((r) => r.selected).map((r) => ({ productId: r.productId, quantity: Number(r.quantity) }));
    if (lines.some((l) => !(l.quantity > 0))) {
      this.error = 'Every selected product needs an order quantity above zero';
      return;
    }
    this.error = null;
    this.busy = true;
    this.api.createRequisition(lines).subscribe({
      next: (pr) => this.router.navigate(['/admin/purchase-requisitions', pr.id]),
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to create the requisition';
      },
    });
  }
}
