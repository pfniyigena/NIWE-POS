import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SalesOrderApiService } from '../core/api/sales-order-api.service';
import { SalesOrderReturnApiService } from '../core/api/sales-order-return-api.service';
import { InvoiceApiService } from '../core/api/invoice-api.service';
import { openPdfForPrint } from '../core/download-file';
import { WarehouseApiService } from '../core/api/warehouse-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { Invoice, SalesOrder, SalesOrderReturn, Warehouse, WarehouseLocation } from '../core/models';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

interface LocationOption {
  id: number;
  label: string;
  type: string | null;
  primaryWarehouse: boolean;
}

interface FulfillRow {
  lineId: number;
  productName: string;
  remaining: number;
  quantityFulfilled: number;
  trackingMode: 'NONE' | 'BATCH' | 'SERIAL';
  serialText: string;
}

interface ReturnRow {
  lineId: number;
  productName: string;
  remaining: number;
  quantity: number;
}

/**
 * Admin > Sales > Sale Orders > detail — where a DRAFT is confirmed or cancelled, and where a
 * CONFIRMED/PARTIALLY_FULFILLED order is actually fulfilled against (mirrors
 * PurchaseOrderDetailComponent's send/cancel/receive split on the purchasing side). Confirming
 * enforces the customer's credit limit against this order's whole amount and is the moment it
 * starts showing up in the Record Payment screen's allocation table — see
 * SalesOrderService.confirm's backend javadoc.
 *
 * <p>Task 3: "Sale Order Return (Cancellation after Confirmation and Payment) Sale Module" — also
 * where a return is processed, once this order has any fulfilled quantity to return, and where its
 * own return history shows up. See SalesOrderReturn's class javadoc for why this is informational
 * only (no refund method/payout) — "refund due" is a figure to show, not a payment the system makes.
 *
 * <p>Fourier: "The record payment is still on Sale order view details => It should be to Invoice
 * view details" — there is deliberately no "Record payment" link anywhere on this screen anymore;
 * payment is only ever recorded from the Invoice's own detail screen now (see
 * InvoiceDetailComponent's javadoc), reached from here via "Generate invoice"/"View invoice"
 * below. Fourier: "add print (PDF with A4)" — {@link #print} opens the same A4 PDF {@code
 * DocumentPdfService.salesOrderPdf} builds in a new tab to print, same pattern as
 * InvoiceDetailComponent's own Print button.
 */
@Component({
  selector: 'app-sales-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="so as o">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ o.orderNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(o.status)">{{ o.status.replace('_', ' ') }}</span>
          </div>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" *ngIf="o.status === 'DRAFT'" [routerLink]="['/admin/sales-orders', o.id, 'edit']">Edit</a>
            <button class="btn btn-primary" *ngIf="o.status === 'DRAFT'" (click)="confirm()">Confirm</button>
            <button class="btn btn-outline-danger" *ngIf="o.status === 'DRAFT' || o.status === 'CONFIRMED'" (click)="cancel()">Cancel order</button>
            <!-- Fourier: "add print (PDF with A4)". -->
            <button class="btn btn-outline-primary" [disabled]="printing" (click)="print()">{{ printing ? 'Opening…' : 'Print' }}</button>
            <!-- Fourier: "Add Invoice ... without touching warehousing" — only once the order is past DRAFT (a real commitment exists to invoice); never shown for CANCELLED. -->
            <button class="btn btn-outline-primary" *ngIf="!invoice && o.status !== 'DRAFT' && o.status !== 'CANCELLED'" (click)="generateInvoice()">Generate invoice</button>
            <!-- Fourier: "Instead of Record payment on Sale Order ... create invoice related to the Sale Order and record payment on invoice" — this screen never records a payment itself anymore; "View invoice" is the way to the screen that does. -->
            <a class="btn btn-outline-primary" *ngIf="invoice" [routerLink]="['/admin/invoices', invoice.id]">View invoice</a>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Customer</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.customerName }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Order date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.orderDate | date: 'medium' }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.fulfilledFrom?.length">
            <div class="stat-label">Fulfilled from</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.fulfilledFrom.join(', ') }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Expected</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.expectedDate ? (o.expectedDate | date: 'mediumDate') : '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Due date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.dueDate || '—' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Subtotal</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.subtotalAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Tax</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.taxAmount | number: '1.2-2' }}</div>
          </div>
          <!-- Fourier: "Same for discount (but discount should be activate in the setting or configuration) and it will be per line" — only shown once this document actually has one, same convention as the printed PDF's own addSubtotalBreakdown. -->
          <div class="stat-tile" *ngIf="o.discountAmount">
            <div class="stat-label">Discount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.discountAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Total amount</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.totalAmount | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Paid</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.amountPaid | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Outstanding</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.amountOutstanding | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.amountReturned > 0">
            <div class="stat-label">Returned</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.amountReturned | number: '1.2-2' }}</div>
          </div>
          <!-- Task 3: "credit/refund owed back" — only shown once a return has actually left the customer overpaid; informational, no payout button, see class javadoc. -->
          <div class="stat-tile" *ngIf="o.refundDue > 0">
            <div class="stat-label">Refund due to customer</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.refundDue | number: '1.2-2' }}</div>
          </div>
        </div>
        <p class="text-muted mb-3" *ngIf="o.notes">{{ o.notes }}</p>

        <h4 class="mb-2">Lines</h4>
        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Product</th>
              <th class="text-end">Ordered</th>
              <th class="text-end">Fulfilled</th>
              <th class="text-end">Unit price</th>
              <!-- Fourier: "Same for discount ... it will be per line" — only shown once this document actually has a discount on at least one line. -->
              <th class="text-end" *ngIf="o.discountAmount">Discount</th>
              <!-- Fourier: "Show also tax on line (Quotation, Invoice, Credit Note, Sale Order, Bill, Refund)" -->
              <th class="text-end">Tax</th>
              <th class="text-end">Line total</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of o.lines">
              <td>{{ l.productName }}</td>
              <td class="text-end">{{ l.quantityOrdered }}</td>
              <td class="text-end">{{ l.quantityFulfilled }}</td>
              <td class="text-end">{{ l.unitPrice | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="o.discountAmount">{{ l.discountAmount | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.taxTotal | number: '1.2-2' }}</td>
              <td class="text-end">{{ l.lineTotal | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>

        <ng-container *ngIf="o.status === 'CONFIRMED' || o.status === 'PARTIALLY_FULFILLED'">
          <h4 class="mb-2">Fulfill order</h4>
          <div class="alert alert-danger" *ngIf="fulfillError">{{ fulfillError }}</div>
          <!-- Multi-warehouse phase 3 — required once the store's multi-warehouse setting is on (no
               Main-warehouse sales, same as the POS); optional extra choice otherwise, shown only
               when there is actually somewhere else to fulfil from. -->
          <div class="form-group mb-3" style="max-width:400px;" *ngIf="multiWarehouseEnabled || fulfilOptions.length > 1">
            <label class="form-label">Fulfil from</label>
            <select class="form-select" name="fulfilLocationId" [(ngModel)]="fulfilLocationId">
              <option *ngIf="!multiWarehouseEnabled" [ngValue]="null">Default (main warehouse)</option>
              <option *ngIf="multiWarehouseEnabled" [ngValue]="null" disabled>Select a location…</option>
              <option *ngFor="let l of fulfilOptions" [ngValue]="l.id">{{ l.label }}</option>
            </select>
          </div>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Product</th>
                <th class="text-end">Remaining</th>
                <th class="text-end">Fulfilling now</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of fulfillRows">
                <td>
                  {{ row.productName }}
                  <div *ngIf="row.trackingMode === 'SERIAL'" style="margin-top:0.35rem;">
                    <textarea class="form-control form-control-sm" rows="2" style="max-width:320px;" placeholder="Serial numbers delivered (optional — otherwise the oldest in stock)" [(ngModel)]="row.serialText" [name]="'fser' + row.lineId"></textarea>
                    <div class="text-muted" style="font-size:0.75rem;">{{ parseSerials(row.serialText).length }} of {{ row.quantityFulfilled }} entered</div>
                  </div>
                </td>
                <td class="text-end">{{ row.remaining }}</td>
                <td class="text-end">
                  <input
                    type="number"
                    class="form-control form-control-sm"
                    style="width:100px; margin-left:auto;"
                    min="0"
                    [max]="row.remaining"
                    [(ngModel)]="row.quantityFulfilled"
                    [name]="'ful' + row.lineId"
                  />
                </td>
              </tr>
              <tr *ngIf="fulfillRows.length === 0">
                <td colspan="3" class="text-muted">Every line has already been fully fulfilled.</td>
              </tr>
            </tbody>
          </table>
          <button class="btn btn-primary" *ngIf="fulfillRows.length > 0" (click)="recordFulfillment()">Record fulfillment</button>
        </ng-container>

        <!-- Task 3: "Sale Order Return" — only once something has actually been fulfilled is there anything physical to bring back. -->
        <ng-container *ngIf="returnRows.length > 0">
          <h4 class="mb-2">Process a return</h4>
          <div class="alert alert-danger" *ngIf="returnError">{{ returnError }}</div>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Product</th>
                <th class="text-end">Available to return</th>
                <th class="text-end">Returning now</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of returnRows">
                <td>{{ row.productName }}</td>
                <td class="text-end">{{ row.remaining }}</td>
                <td class="text-end">
                  <input
                    type="number"
                    class="form-control form-control-sm"
                    style="width:100px; margin-left:auto;"
                    min="0"
                    [max]="row.remaining"
                    [(ngModel)]="row.quantity"
                    [name]="'ret' + row.lineId"
                  />
                </td>
              </tr>
            </tbody>
          </table>
          <div class="form-group mb-3" style="max-width:400px;" *ngIf="returnOptions.length > 2">
            <label class="form-label">Return stock to</label>
            <select class="form-select" name="returnLocationId" [(ngModel)]="returnLocationId">
              <option [ngValue]="null">Where it was fulfilled from</option>
              <option *ngFor="let l of returnOptions" [ngValue]="l.id">{{ l.label }}</option>
            </select>
          </div>
          <div class="form-group mb-3" style="max-width:400px;">
            <label>Reason (optional)</label>
            <input type="text" class="form-control" [(ngModel)]="returnReason" name="returnReason" />
          </div>
          <button class="btn btn-outline-danger" (click)="processReturn()">Process return</button>
        </ng-container>

        <ng-container *ngIf="returns.length > 0">
          <h4 class="mb-2 mt-3">Return history</h4>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Return date</th>
                <th>Return #</th>
                <th class="text-end">Refund due</th>
                <th>Stock returned to</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of returns">
                <td>{{ r.returnDate | date: 'medium' }}</td>
                <td>{{ r.returnNumber }}</td>
                <td class="text-end">{{ r.refundTotal | number: '1.2-2' }}</td>
                <td>{{ r.locationName ? (r.warehouseName ? r.warehouseName + ' / ' : '') + r.locationName : '—' }}</td>
                <td>{{ r.reason }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class SalesOrderDetailComponent implements OnInit {
  so: SalesOrder | null = null;
  error: string | null = null;
  fulfillError: string | null = null;
  fulfillRows: FulfillRow[] = [];
  returnRows: ReturnRow[] = [];
  returnReason = '';
  returnError: string | null = null;
  returns: SalesOrderReturn[] = [];
  /** Multi-warehouse phase 3 — see the fulfil/return pickers in the template. */
  multiWarehouseEnabled = false;
  fulfilOptions: LocationOption[] = [];
  returnOptions: LocationOption[] = [];
  fulfilLocationId: number | null = null;
  returnLocationId: number | null = null;
  /** Fourier: "Add Invoice ... without touching warehousing" — null means none has been generated for this order yet, see InvoiceApiService.getBySalesOrder's javadoc. */
  invoice: Invoice | null = null;
  printing = false;

  constructor(
    private salesOrderApi: SalesOrderApiService,
    private salesOrderReturnApi: SalesOrderReturnApiService,
    private invoiceApi: InvoiceApiService,
    private warehouseApi: WarehouseApiService,
    private storeSettingsApi: StoreSettingsApiService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.load();
    this.loadLocationOptions();
  }

  /** Never blocks the page: if settings or warehouses can't be read, the pickers just stay hidden and the old behaviour applies. */
  private loadLocationOptions(): void {
    this.storeSettingsApi
      .get()
      .pipe(catchError(() => of(null)))
      .subscribe((settings) => {
        this.multiWarehouseEnabled = !!settings?.multiWarehouseEnabled;
        this.warehouseApi
          .listAll()
          .pipe(catchError(() => of([] as Warehouse[])))
          .subscribe((all) => {
            const active = all.filter((w) => w.active);
            if (active.length === 0) return;
            forkJoin(
              active.map((w) => this.warehouseApi.listLocations(w.id).pipe(catchError(() => of([] as WarehouseLocation[]))))
            ).subscribe((perWarehouse) => {
              const options: LocationOption[] = [];
              active.forEach((w, i) => {
                for (const l of perWarehouse[i]) {
                  if (!l.active) continue;
                  options.push({
                    id: l.id,
                    label: `${w.name} / ${l.name}${l.type ? ' (' + l.type + ')' : ''}`,
                    type: l.type,
                    primaryWarehouse: w.primary,
                  });
                }
              });
              this.returnOptions = options;
              // Fulfilment only ever draws from SALES locations, and — with multi-warehouse on —
              // never from the Main warehouse.
              this.fulfilOptions = options.filter((o) => o.type === 'SALES' && !(this.multiWarehouseEnabled && o.primaryWarehouse));
            });
          });
      });
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.salesOrderApi.getById(id).subscribe({
      next: (so) => {
        this.so = so;
        this.fulfillRows = so.lines
          .filter((l) => l.quantityFulfilled < l.quantityOrdered)
          .map((l) => ({
            lineId: l.id,
            productName: l.productName,
            remaining: l.quantityOrdered - l.quantityFulfilled,
            quantityFulfilled: l.quantityOrdered - l.quantityFulfilled,
            trackingMode: l.trackingMode ?? 'NONE',
            serialText: '',
          }));
        // Task 3: "Process return" — only the fulfilled-but-not-yet-returned portion of each line
        // can be returned; needs the already-returned quantities to know how much is left.
        this.salesOrderReturnApi.returnedQuantitiesForOrder(id).subscribe((returnedQty) => {
          this.returnRows = so.lines
            .map((l) => {
              const remaining = l.quantityFulfilled - (returnedQty[l.id] ?? 0);
              return { lineId: l.id, productName: l.productName, remaining, quantity: 0 };
            })
            .filter((row) => row.remaining > 0);
        });
        this.salesOrderReturnApi.listForOrder(id).subscribe((returns) => (this.returns = returns));
        this.invoiceApi.getBySalesOrder(id).subscribe({
          next: (inv) => (this.invoice = inv),
          error: () => (this.invoice = null), // extra display only — never blocks the rest of the page
        });
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load sale order'),
    });
  }

  generateInvoice(): void {
    if (!this.so) return;
    this.error = null;
    this.invoiceApi.generate(this.so.id).subscribe({
      next: (inv) => (this.invoice = inv),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to generate invoice'),
    });
  }

  /** Fourier: "add print (PDF with A4)" — fetches the real A4 PDF and opens it in a new tab for the browser's own PDF viewer to print. */
  print(): void {
    if (!this.so) return;
    this.error = null;
    this.printing = true;
    this.salesOrderApi.pdf(this.so.id).subscribe({
      next: (blob) => {
        this.printing = false;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printing = false;
        this.error = err?.error?.message ?? 'Failed to generate the sale order PDF';
      },
    });
  }

  confirm(override = false): void {
    if (!this.so) return;
    this.error = null;
    this.salesOrderApi.confirm(this.so.id, override).subscribe({
      next: () => this.load(),
      error: (err) => {
        // 409 = not enough unreserved stock: warn, and let an admin confirm (reserve) anyway.
        if (err?.status === 409 && !override) {
          if (confirm(`${err?.error?.message ?? 'Not enough unreserved stock.'}\n\nConfirm and reserve anyway?`)) {
            this.confirm(true);
          }
          return;
        }
        this.error = err?.error?.message ?? 'Failed to confirm sale order';
      },
    });
  }

  cancel(): void {
    if (!this.so) return;
    if (!confirm(`Cancel sale order ${this.so.orderNumber}?`)) return;
    this.error = null;
    this.salesOrderApi.cancel(this.so.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel sale order'),
    });
  }

  parseSerials(text: string): string[] {
    return (text || '')
      .split(/[\n,;]+/)
      .map((x) => x.trim())
      .filter((x) => x.length > 0);
  }

  recordFulfillment(): void {
    if (!this.so) return;
    const lines = this.fulfillRows
      .filter((r) => r.quantityFulfilled > 0)
      .map((r) => ({
        lineId: r.lineId,
        quantityFulfilled: r.quantityFulfilled,
        serialNumbers: r.trackingMode === 'SERIAL' && this.parseSerials(r.serialText).length > 0 ? this.parseSerials(r.serialText) : undefined,
      }));
    const bad = this.fulfillRows.find((r) => r.quantityFulfilled > 0 && r.trackingMode === 'SERIAL' && this.parseSerials(r.serialText).length > 0 && this.parseSerials(r.serialText).length !== Number(r.quantityFulfilled));
    if (bad) {
      this.fulfillError = `${bad.productName}: enter ${bad.quantityFulfilled} serial number(s) or leave the box empty`;
      return;
    }
    if (lines.length === 0) {
      this.fulfillError = 'Enter a quantity for at least one line';
      return;
    }
    if (this.multiWarehouseEnabled && this.fulfilLocationId === null) {
      this.fulfillError = 'Choose the location this order is fulfilled from';
      return;
    }
    this.fulfillError = null;
    this.salesOrderApi.fulfill(this.so.id, { lines, locationId: this.fulfilLocationId }).subscribe({
      next: () => this.load(),
      error: (err) => (this.fulfillError = err?.error?.message ?? 'Failed to record fulfillment'),
    });
  }

  processReturn(): void {
    if (!this.so) return;
    const lines = this.returnRows.filter((r) => r.quantity > 0).map((r) => ({ lineId: r.lineId, quantity: r.quantity }));
    if (lines.length === 0) {
      this.returnError = 'Enter a quantity for at least one line';
      return;
    }
    this.returnError = null;
    this.salesOrderReturnApi.process({ salesOrderId: this.so.id, lines, reason: this.returnReason || null, locationId: this.returnLocationId }).subscribe({
      next: () => {
        this.returnReason = '';
        this.load();
      },
      error: (err) => (this.returnError = err?.error?.message ?? 'Failed to process return'),
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'CONFIRMED':
        return 'badge-secondary';
      case 'PARTIALLY_FULFILLED':
        return 'badge-warning';
      case 'FULFILLED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
