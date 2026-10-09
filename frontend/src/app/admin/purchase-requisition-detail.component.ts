import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { DatePickerComponent } from '../shared/date-picker.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PurchaseRequisitionApiService } from '../core/api/purchase-requisition-api.service';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { PurchaseRequisition, Supplier } from '../core/models';

interface ConvertRow {
  lineId: number;
  productId: number | null;
  productName: string;
  quantityRequested: number;
  unitCost: number | null;
}

/**
 * Admin > Inventory > Purchase Requisitions > detail — where a DRAFT is submitted, a
 * PENDING_APPROVAL requisition is approved/rejected, and where an APPROVED requisition is
 * converted into a real Purchase Order (Fourier: "Add ... the purchase-requisition approval gate
 * ... without touching warehousing"). The "Convert" panel is the one place a supplier and each
 * line's unit cost are ever supplied for this requisition — see the backend
 * PurchaseRequisition's class javadoc for why.
 *
 * <p>Fourier: "On requisition form add quick new supplier" — the same inline "+ New supplier"
 * quick-add panel as PurchaseOrderFormComponent's own (see that component's class javadoc), so
 * picking a supplier here that isn't set up yet doesn't mean losing this conversion to go fill
 * out the full Admin > Suppliers form and come back.
 *
 * <p>Fourier: "On create order from requisition form, the unit cost by default pick the product
 * unit cost" — each convert row now starts pre-filled from that product's own {@code
 * Product.costPrice} (same default PurchaseOrderFormComponent.addLine already uses), rather than
 * forcing the admin to type every cost in from scratch; it's still a plain editable input, so a
 * different negotiated cost for this particular order can still be typed over it.
 */
@Component({
  selector: 'app-purchase-requisition-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="r as req">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ req.requisitionNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(req.status)">{{ req.status.replace('_', ' ') }}</span>
          </div>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" *ngIf="req.status === 'DRAFT'" [routerLink]="['/admin/purchase-requisitions', req.id, 'edit']">Edit</a>
            <button class="btn btn-primary" *ngIf="req.status === 'DRAFT'" (click)="submit()">Submit for approval</button>
            <button class="btn btn-primary" *ngIf="req.status === 'PENDING_APPROVAL'" (click)="approve()">Approve</button>
            <button class="btn btn-outline-danger" *ngIf="req.status === 'PENDING_APPROVAL'" (click)="reject()">Reject</button>
            <button class="btn btn-outline-danger" *ngIf="req.status === 'DRAFT' || req.status === 'PENDING_APPROVAL'" (click)="cancel()">Cancel</button>
            <a class="btn btn-outline-primary" *ngIf="req.convertedPurchaseOrderId" [routerLink]="['/admin/purchase-orders', req.convertedPurchaseOrderId]">View purchase order</a>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Requested by</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ req.requestedByFullName || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Request date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ req.requestDate | date: 'medium' }}</div>
          </div>
          <div class="stat-tile" *ngIf="req.approvedByFullName">
            <div class="stat-label">Approved by</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ req.approvedByFullName }}</div>
          </div>
        </div>
        <p class="text-muted mb-3" *ngIf="req.notes">{{ req.notes }}</p>
        <p class="text-muted mb-3" *ngIf="req.rejectionReason">Rejected — {{ req.rejectionReason }}</p>
        <p class="text-muted mb-3" *ngIf="req.convertedPurchaseOrderNumber">Converted into purchase order {{ req.convertedPurchaseOrderNumber }}.</p>

        <h4 class="mb-2">Lines</h4>
        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Product</th>
              <th class="text-end">Quantity requested</th>
              <th>Note</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of req.lines">
              <td>{{ l.productName }}</td>
              <td class="text-end">{{ l.quantityRequested }}</td>
              <td>{{ l.note || '—' }}</td>
            </tr>
          </tbody>
        </table>

        <ng-container *ngIf="req.status === 'APPROVED'">
          <h4 class="mb-2">Convert to purchase order</h4>
          <div class="alert alert-danger" *ngIf="convertError">{{ convertError }}</div>
          <div class="flex gap-2 mb-2">
            <div class="grow">
              <label class="form-label">Supplier</label>
              <div class="flex gap-2">
                <select class="form-select" name="supplierId" [(ngModel)]="supplierId" [disabled]="showNewSupplier">
                  <option [ngValue]="null" disabled>Select a supplier…</option>
                  <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.name }}</option>
                </select>
                <button type="button" class="btn btn-outline-secondary" style="white-space:nowrap;" *ngIf="!showNewSupplier" (click)="openNewSupplier()">
                  + New supplier
                </button>
              </div>
            </div>
            <div class="grow">
              <label class="form-label">Expected date (optional)</label>
              <app-date-picker name="expectedDate" [(ngModel)]="expectedDate" ></app-date-picker>
            </div>
          </div>

          <div class="card mb-3" style="padding:0.9rem; border:1px solid var(--border, #ddd); border-radius:0.375rem;" *ngIf="showNewSupplier">
            <div class="alert alert-danger" *ngIf="newSupplierError">{{ newSupplierError }}</div>
            <div class="flex gap-2 mb-2">
              <div class="grow">
                <label class="form-label">Name</label>
                <input class="form-control" name="newSupplierName" [(ngModel)]="newSupplierName" placeholder="Supplier name" />
              </div>
              <div class="grow">
                <label class="form-label">Contact person</label>
                <input class="form-control" name="newSupplierContact" [(ngModel)]="newSupplierContact" />
              </div>
            </div>
            <div class="flex gap-2 mb-2">
              <div class="grow">
                <label class="form-label">Phone</label>
                <input class="form-control" name="newSupplierPhone" [(ngModel)]="newSupplierPhone" />
              </div>
              <div class="grow">
                <label class="form-label">Email</label>
                <input class="form-control" type="email" name="newSupplierEmail" [(ngModel)]="newSupplierEmail" />
              </div>
            </div>
            <div class="flex gap-2 mb-2">
              <div class="grow">
                <label class="form-label">Tax ID</label>
                <input class="form-control" name="newSupplierTaxId" [(ngModel)]="newSupplierTaxId" />
              </div>
              <div class="grow"></div>
            </div>
            <div class="flex gap-2">
              <button type="button" class="btn btn-primary btn-sm" (click)="saveNewSupplier()">Save supplier</button>
              <button type="button" class="btn btn-link btn-sm" (click)="cancelNewSupplier()">Cancel</button>
            </div>
          </div>

          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Product</th>
                <th class="text-end">Quantity</th>
                <th class="text-end">Unit cost</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of convertRows">
                <td>{{ row.productName }}</td>
                <td class="text-end">{{ row.quantityRequested }}</td>
                <td class="text-end">
                  <input
                    type="number"
                    class="form-control form-control-sm"
                    style="width:100px; margin-left:auto;"
                    min="0"
                    step="0.01"
                    [(ngModel)]="row.unitCost"
                    [name]="'cost' + row.lineId"
                  />
                </td>
              </tr>
            </tbody>
          </table>
          <button class="btn btn-primary" (click)="convert()">Create purchase order</button>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class PurchaseRequisitionDetailComponent implements OnInit {
  r: PurchaseRequisition | null = null;
  error: string | null = null;
  suppliers: Supplier[] = [];
  supplierId: number | null = null;
  expectedDate: string | null = null;
  convertRows: ConvertRow[] = [];
  convertError: string | null = null;

  // Fourier: "On requisition form add quick new supplier" — inline quick-add panel state, same
  // shape as PurchaseOrderFormComponent's own.
  showNewSupplier = false;
  newSupplierName = '';
  newSupplierContact = '';
  newSupplierPhone = '';
  newSupplierEmail = '';
  newSupplierTaxId = '';
  newSupplierError: string | null = null;

  constructor(
    private requisitionApi: PurchaseRequisitionApiService,
    private supplierApi: SupplierApiService,
    private catalogApi: CatalogApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
    this.supplierApi.listAll().subscribe((s) => (this.suppliers = s));
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.requisitionApi.getById(id).subscribe({
      next: (r) => {
        this.r = r;
        this.convertRows = r.lines.map((l) => ({
          lineId: l.id,
          productId: l.productId,
          productName: l.productName,
          quantityRequested: l.quantityRequested,
          unitCost: null,
        }));
        this.prefillUnitCosts();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load purchase requisition'),
    });
  }

  /** Fourier: "the unit cost by default pick the product unit cost" — pre-fills each row from its product's current Product.costPrice; left null (so the admin must enter it, same as before) for any row whose line has no productId or whose product lookup fails. */
  private prefillUnitCosts(): void {
    const rowsWithProduct = this.convertRows.filter((row) => row.productId != null);
    if (rowsWithProduct.length === 0) return;
    forkJoin(rowsWithProduct.map((row) => this.catalogApi.getProduct(row.productId as number))).subscribe({
      next: (products) => products.forEach((p, i) => (rowsWithProduct[i].unitCost = p.costPrice)),
      error: () => {
        /* leave unitCost null — the admin enters it manually, same as before this default existed */
      },
    });
  }

  /** Fourier: "On requisition form add quick new supplier" — opens the inline quick-add panel; the picker itself is disabled while it's open so there's only one obvious place to be setting the supplier. */
  openNewSupplier(): void {
    this.showNewSupplier = true;
    this.newSupplierError = null;
  }

  cancelNewSupplier(): void {
    this.showNewSupplier = false;
    this.newSupplierName = '';
    this.newSupplierContact = '';
    this.newSupplierPhone = '';
    this.newSupplierEmail = '';
    this.newSupplierTaxId = '';
    this.newSupplierError = null;
  }

  /** Creates the supplier, then selects it on this conversion right away — same convenience as PurchaseOrderFormComponent.saveNewSupplier. */
  saveNewSupplier(): void {
    if (!this.newSupplierName.trim()) {
      this.newSupplierError = 'Name is required';
      return;
    }
    this.newSupplierError = null;
    this.supplierApi
      .create({
        name: this.newSupplierName.trim(),
        contactName: this.newSupplierContact || null,
        phone: this.newSupplierPhone || null,
        email: this.newSupplierEmail || null,
        taxId: this.newSupplierTaxId || null,
        active: true,
      })
      .subscribe({
        next: (supplier) => {
          this.suppliers = [...this.suppliers, supplier].sort((a, b) => a.name.localeCompare(b.name));
          this.supplierId = supplier.id;
          this.cancelNewSupplier();
        },
        error: (err) => (this.newSupplierError = err?.error?.message ?? 'Failed to save supplier'),
      });
  }

  submit(): void {
    if (!this.r) return;
    this.error = null;
    this.requisitionApi.submit(this.r.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to submit purchase requisition'),
    });
  }

  approve(): void {
    if (!this.r) return;
    this.error = null;
    this.requisitionApi.approve(this.r.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to approve purchase requisition'),
    });
  }

  reject(): void {
    if (!this.r) return;
    const reason = window.prompt('Reason for rejecting this requisition (optional):') ?? '';
    this.error = null;
    this.requisitionApi.reject(this.r.id, reason || null).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to reject purchase requisition'),
    });
  }

  cancel(): void {
    if (!this.r) return;
    if (!confirm(`Cancel purchase requisition ${this.r.requisitionNumber}?`)) return;
    this.error = null;
    this.requisitionApi.cancel(this.r.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel purchase requisition'),
    });
  }

  convert(): void {
    if (!this.r) return;
    if (!this.supplierId) {
      this.convertError = 'Select a supplier';
      return;
    }
    const missing = this.convertRows.find((row) => row.unitCost == null);
    if (missing) {
      this.convertError = `Enter a unit cost for '${missing.productName}'`;
      return;
    }
    this.convertError = null;
    this.requisitionApi
      .convert(this.r.id, {
        supplierId: this.supplierId,
        expectedDate: this.expectedDate ? new Date(this.expectedDate).toISOString() : null,
        lines: this.convertRows.map((row) => ({ lineId: row.lineId, unitCost: row.unitCost as number })),
      })
      .subscribe({
        next: (po) => this.router.navigate(['/admin/purchase-orders', po.id]),
        error: (err) => (this.convertError = err?.error?.message ?? 'Failed to convert purchase requisition'),
      });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'PENDING_APPROVAL':
        return 'badge-warning';
      case 'APPROVED':
        return 'badge-success';
      case 'REJECTED':
        return 'badge-danger';
      case 'CONVERTED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
