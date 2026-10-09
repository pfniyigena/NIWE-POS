import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PurchaseRequisitionApiService } from '../core/api/purchase-requisition-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product } from '../core/models';

interface DraftLine {
  productId: number;
  productName: string;
  quantityRequested: number;
  note: string;
}

/**
 * Admin > Inventory > Purchase Requisitions > New/Edit — Fourier: "Add ... the
 * purchase-requisition approval gate ... without touching warehousing." Only ever creates/edits a
 * DRAFT (see the backend PurchaseRequisitionStatus's javadoc); submitting, approving, rejecting,
 * cancelling and converting all happen on the detail screen instead. Deliberately no supplier or
 * cost fields here at all — see the backend PurchaseRequisition's class javadoc for why those
 * only get attached at conversion time.
 */
@Component({
  selector: 'app-purchase-requisition-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ id ? 'Edit purchase requisition' : 'New purchase requisition' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3">
        <label class="form-label">Notes</label>
        <textarea class="form-control" name="notes" rows="2" [(ngModel)]="notes"></textarea>
      </div>

      <h4 class="mb-2">Line items</h4>
      <div class="flex gap-2 mb-2" style="position:relative;">
        <input
          class="form-control"
          placeholder="Search products by name, SKU or code to add a line"
          [(ngModel)]="productSearch"
          (input)="onProductSearch()"
        />
        <div class="dropdown-panel" *ngIf="productResults.length > 0">
          <div class="dropdown-item" *ngFor="let p of productResults" (click)="addLine(p)">
            {{ p.name }} <span class="text-muted">— stock {{ p.stockQuantity }}</span>
          </div>
        </div>
      </div>

      <table class="table-sm mb-3">
        <thead>
          <tr>
            <th>Product</th>
            <th class="text-end">Quantity requested</th>
            <th>Note</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let line of lines; let i = index">
            <td>{{ line.productName }}</td>
            <td class="text-end">
              <input type="number" class="form-control form-control-sm" style="width:90px; margin-left:auto;" min="1" [(ngModel)]="line.quantityRequested" [name]="'qty' + i" />
            </td>
            <td>
              <input type="text" class="form-control form-control-sm" placeholder="Optional" [(ngModel)]="line.note" [name]="'note' + i" />
            </td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" (click)="removeLine(i)">Remove</button></td>
          </tr>
          <tr *ngIf="lines.length === 0">
            <td colspan="4" class="text-muted">No lines yet — search for a product above to add one.</td>
          </tr>
        </tbody>
      </table>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save draft</button>
        <a class="btn btn-link" routerLink="/admin/purchase-requisitions">Cancel</a>
      </div>
    </app-shell>
  `,
  styles: [
    `
      .dropdown-panel {
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        z-index: 20;
        background: var(--surface, #fff);
        border: 1px solid var(--border, #ddd);
        border-radius: 0.375rem;
        max-height: 260px;
        overflow-y: auto;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
      }
      .dropdown-item {
        padding: 0.5rem 0.75rem;
        cursor: pointer;
      }
      .dropdown-item:hover {
        background: var(--hover, rgba(0, 0, 0, 0.05));
      }
    `,
  ],
})
export class PurchaseRequisitionFormComponent implements OnInit {
  id: number | null = null;
  notes = '';
  lines: DraftLine[] = [];
  error: string | null = null;

  productSearch = '';
  productResults: Product[] = [];

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private requisitionApi: PurchaseRequisitionApiService,
    private catalogApi: CatalogApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.requisitionApi.getById(this.id).subscribe({
        next: (r) => {
          if (r.status !== 'DRAFT') {
            this.error = `${r.requisitionNumber} is ${r.status.replace('_', ' ').toLowerCase()} — only a draft can be edited.`;
            return;
          }
          this.notes = r.notes ?? '';
          this.lines = r.lines.map((l) => ({
            productId: l.productId as number,
            productName: l.productName,
            quantityRequested: l.quantityRequested,
            note: l.note ?? '',
          }));
        },
        error: (err) => (this.error = err?.error?.message ?? 'Failed to load purchase requisition'),
      });
    }
  }

  onProductSearch(): void {
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    const term = this.productSearch.trim();
    if (!term) {
      this.productResults = [];
      return;
    }
    this.searchDebounce = setTimeout(() => {
      this.catalogApi.searchProducts(null, term).subscribe({
        next: (results) => (this.productResults = results.slice(0, 20)),
        error: () => (this.productResults = []),
      });
    }, 250);
  }

  addLine(p: Product): void {
    const existing = this.lines.find((l) => l.productId === p.id);
    if (existing) {
      existing.quantityRequested += 1;
    } else {
      this.lines.push({ productId: p.id, productName: p.name, quantityRequested: 1, note: '' });
    }
    this.productSearch = '';
    this.productResults = [];
  }

  removeLine(index: number): void {
    this.lines.splice(index, 1);
  }

  submit(): void {
    if (this.lines.length === 0) {
      this.error = 'Add at least one line item';
      return;
    }
    this.error = null;
    const payload = {
      notes: this.notes || null,
      lines: this.lines.map((l) => ({ productId: l.productId, quantityRequested: l.quantityRequested, note: l.note || null })),
    };
    const call = this.id ? this.requisitionApi.update(this.id, payload) : this.requisitionApi.create(payload);
    call.subscribe({
      next: (r) => this.router.navigate(['/admin/purchase-requisitions', r.id]),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save purchase requisition'),
    });
  }
}
