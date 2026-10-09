import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PurchaseOrderApiService } from '../core/api/purchase-order-api.service';
import { PurchaseReturnApiService } from '../core/api/purchase-return-api.service';
import { BillApiService } from '../core/api/bill-api.service';
import { Bill, PurchaseOrder, PurchaseReturn } from '../core/models';

interface ReceiveRow {
  lineId: number;
  productName: string;
  remaining: number;
  quantityReceived: number;
  trackingMode: 'NONE' | 'BATCH' | 'SERIAL';
  batchNumber: string;
  expiryDate: string;
  serialText: string;
}

interface ReturnRow {
  lineId: number;
  productName: string;
  remaining: number;
  quantity: number;
}

/**
 * Admin > Inventory > Purchase Orders > detail — where a DRAFT is sent or cancelled, and where a
 * SENT/PARTIALLY_RECEIVED order is actually received against (Task: "a stock-receiving flow that
 * increments quantity with a paper trail"). Each "Record receipt" click can cover any subset of
 * lines/quantities, so a delivery that only has part of the order can be recorded today and the
 * rest later — {@link PurchaseOrderApiService.receive} accepts exactly that.
 *
 * Task 4: "Supplier Purchase Return (Cancellation after Confirmation and Payment) Inventory
 * Module" — also where a return is sent back to the supplier, once this order has any received
 * quantity to return, and where its own return history shows up. Informational only — see
 * PurchaseReturn's class javadoc for why there's no payout/refund-received button here.
 *
 * <p>Fourier: "The Purchase order should flow same workflow as Sale order (with Bills and Refunds
 * equivalent with Invoices and Credit note)" — there is deliberately no "Record payment" link
 * anywhere on this screen anymore, same as SalesOrderDetailComponent's own javadoc states for the
 * Sale Order side: payment is only ever recorded from the Bill's own detail screen now, reached
 * from here via "Generate bill"/"View bill" below (mirroring "Generate invoice"/"View invoice").
 */
@Component({
  selector: 'app-purchase-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="po as o">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0.25rem;">{{ o.orderNumber }}</h3>
            <span class="badge" [ngClass]="statusClass(o.status)">{{ o.status.replace('_', ' ') }}</span>
          </div>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" *ngIf="o.status === 'DRAFT'" [routerLink]="['/admin/purchase-orders', o.id, 'edit']">Edit</a>
            <button class="btn btn-primary" *ngIf="o.status === 'DRAFT'" (click)="send()">Send to supplier</button>
            <button class="btn btn-outline-danger" *ngIf="o.status === 'DRAFT' || o.status === 'SENT'" (click)="cancel()">Cancel order</button>
            <!-- Fourier: "same workflow as Sale order" — only once the order is past DRAFT/CANCELLED (a real commitment exists to bill); mirrors Sale Order's own "Generate invoice". -->
            <button class="btn btn-outline-primary" *ngIf="!bill && o.status !== 'DRAFT' && o.status !== 'CANCELLED'" [disabled]="generatingBill" (click)="generateBill()">{{ generatingBill ? 'Generating…' : 'Generate bill' }}</button>
            <a class="btn btn-outline-primary" *ngIf="bill" [routerLink]="['/admin/bills', bill.id]">View bill</a>
            <!-- Task 3: "Print out purchase order" -->
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/purchase-orders', o.id, 'print']" target="_blank">🖨 Print</a>
          </div>
        </div>

        <div class="stat-grid mb-3">
          <div class="stat-tile">
            <div class="stat-label">Supplier</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.supplierName }}</div>
          </div>
          <!-- Multi-warehouse phase 2: where received stock lands (its Receiving location). -->
          <div class="stat-tile">
            <div class="stat-label">Receives into</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.receivingWarehouseName }}</div>
          </div>
          <div class="stat-tile" *ngIf="o.landedCostTotal">
            <div class="stat-label">Landed cost</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.landedCostTotal | number: '1.2-2' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Order date</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.orderDate | date: 'medium' }}</div>
          </div>
          <div class="stat-tile">
            <div class="stat-label">Expected</div>
            <div class="stat-value" style="font-size:1.1rem;">{{ o.expectedDate ? (o.expectedDate | date: 'mediumDate') : '—' }}</div>
          </div>
          <!-- Fourier: "for purchases, sales, invoices, bills the tax are not being captured" — PurchaseOrder had no total concept at all before this; mirrors the backend's PurchaseOrderDto (SupplierPaymentService.totalCost/netCost/taxCost). -->
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
          <!-- Task 4: "credit/refund owed back" — only shown once a return has actually left the store overpaid relative to what's now owed; informational, no collect button, see class javadoc. -->
          <div class="stat-tile" *ngIf="o.refundDue > 0">
            <div class="stat-label">Refund due from supplier</div>
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
              <th class="text-end">Received</th>
              <th class="text-end">Unit cost</th>
              <!-- Fourier: "Same for discount ... it will be per line" — only shown once this document actually has a discount on at least one line. -->
              <th class="text-end" *ngIf="o.discountAmount">Discount</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of o.lines">
              <td>{{ l.productName }}</td>
              <td class="text-end">{{ l.quantityOrdered }}</td>
              <td class="text-end">{{ l.quantityReceived }}</td>
              <td class="text-end">{{ l.unitCost | number: '1.2-2' }}</td>
              <td class="text-end" *ngIf="o.discountAmount">{{ l.discountAmount | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>

        <ng-container *ngIf="o.status === 'SENT' || o.status === 'PARTIALLY_RECEIVED'">
          <h4 class="mb-2">Record a receipt</h4>
          <div class="alert alert-danger" *ngIf="receiveError">{{ receiveError }}</div>
          <table class="table-sm mb-3">
            <thead>
              <tr>
                <th>Product</th>
                <th class="text-end">Remaining</th>
                <th class="text-end">Receiving now</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of receiveRows">
                <td>
                  {{ row.productName }}
                  <div *ngIf="row.trackingMode === 'BATCH'" class="flex gap-2" style="margin-top:0.35rem;">
                    <input class="form-control form-control-sm" style="max-width:160px;" placeholder="Batch / lot no." [(ngModel)]="row.batchNumber" [name]="'batch' + row.lineId" />
                    <input class="form-control form-control-sm" style="max-width:160px;" type="date" title="Expiry date" [(ngModel)]="row.expiryDate" [name]="'exp' + row.lineId" />
                  </div>
                  <div *ngIf="row.trackingMode === 'SERIAL'" style="margin-top:0.35rem;">
                    <textarea class="form-control form-control-sm" rows="2" style="max-width:320px;" placeholder="Serial numbers — one per line or comma-separated" [(ngModel)]="row.serialText" [name]="'ser' + row.lineId"></textarea>
                    <div class="text-muted" style="font-size:0.75rem;">{{ serialCount(row) }} of {{ row.quantityReceived }} entered</div>
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
                    [(ngModel)]="row.quantityReceived"
                    [name]="'recv' + row.lineId"
                  />
                </td>
              </tr>
              <tr *ngIf="receiveRows.length === 0">
                <td colspan="3" class="text-muted">Every line has already been fully received.</td>
              </tr>
            </tbody>
          </table>
          <div class="mb-3" *ngIf="receiveRows.length > 0" style="max-width:520px;">
            <label class="form-label">Landed cost for this delivery (optional)</label>
            <div class="flex gap-2">
              <input class="form-control" type="number" min="0" step="0.01" placeholder="Freight, duty, clearing…" [(ngModel)]="landedCost" name="landedCost" />
              <input class="form-control" placeholder="Note" [(ngModel)]="landedCostNote" name="landedCostNote" />
            </div>
            <label class="form-label" style="margin-top:0.35rem;" *ngIf="landedCost">
              <input type="checkbox" [(ngModel)]="landedCostPaid" name="landedCostPaid" /> Already paid (otherwise recorded as owed)
            </label>
            <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
              Spread over the delivered products by value and added to their cost and to Inventory in the books. Record freight here
              rather than as a separate expense, or it would be counted twice.
            </div>
          </div>
          <button class="btn btn-primary" *ngIf="receiveRows.length > 0" (click)="recordReceipt()">Record receipt</button>
        </ng-container>

        <!-- Task 4: "Supplier Purchase Return" — only once something has actually been received is there anything to send back. -->
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
                    [name]="'poret' + row.lineId"
                  />
                </td>
              </tr>
            </tbody>
          </table>
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
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of returns">
                <td>{{ r.returnDate | date: 'medium' }}</td>
                <td>{{ r.returnNumber }}</td>
                <td class="text-end">{{ r.refundTotal | number: '1.2-2' }}</td>
                <td>{{ r.reason }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>
      </ng-container>
    </app-shell>
  `,
})
export class PurchaseOrderDetailComponent implements OnInit {
  po: PurchaseOrder | null = null;
  error: string | null = null;
  receiveError: string | null = null;
  receiveRows: ReceiveRow[] = [];
  landedCost: number | null = null;
  landedCostNote = '';
  landedCostPaid = false;
  returnRows: ReturnRow[] = [];
  returnReason = '';
  returnError: string | null = null;
  returns: PurchaseReturn[] = [];
  /** Fourier: "same workflow as Sale order" — null means none has been generated for this order yet, see BillApiService.getByPurchaseOrder's javadoc. */
  bill: Bill | null = null;
  generatingBill = false;

  constructor(
    private purchaseOrderApi: PurchaseOrderApiService,
    private purchaseReturnApi: PurchaseReturnApiService,
    private billApi: BillApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.purchaseOrderApi.getById(id).subscribe({
      next: (po) => {
        this.po = po;
        this.receiveRows = po.lines
          .filter((l) => l.quantityReceived < l.quantityOrdered)
          .map((l) => ({
            lineId: l.id,
            productName: l.productName,
            remaining: l.quantityOrdered - l.quantityReceived,
            quantityReceived: l.quantityOrdered - l.quantityReceived,
            trackingMode: l.trackingMode ?? 'NONE',
            batchNumber: '',
            expiryDate: '',
            serialText: '',
          }));
        // Task 4: "Process return" — only the received-but-not-yet-returned portion of each line
        // can be sent back; needs the already-returned quantities to know how much is left.
        this.purchaseReturnApi.returnedQuantitiesForOrder(id).subscribe((returnedQty) => {
          this.returnRows = po.lines
            .map((l) => {
              const remaining = l.quantityReceived - (returnedQty[l.id] ?? 0);
              return { lineId: l.id, productName: l.productName, remaining, quantity: 0 };
            })
            .filter((row) => row.remaining > 0);
        });
        this.purchaseReturnApi.listForOrder(id).subscribe((returns) => (this.returns = returns));
        this.billApi.getByPurchaseOrder(id).subscribe({
          next: (bill) => (this.bill = bill),
          error: () => (this.bill = null), // extra display only — never blocks the rest of the page
        });
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load purchase order'),
    });
  }

  /** Fourier: "same workflow as Sale order" — mirrors SalesOrderDetailComponent's own generateInvoice(). */
  generateBill(): void {
    if (!this.po) return;
    this.error = null;
    this.generatingBill = true;
    this.billApi.generate(this.po.id).subscribe({
      next: (bill) => {
        this.generatingBill = false;
        this.bill = bill;
      },
      error: (err) => {
        this.generatingBill = false;
        this.error = err?.error?.message ?? 'Failed to generate bill';
      },
    });
  }

  send(): void {
    if (!this.po) return;
    this.error = null;
    this.purchaseOrderApi.send(this.po.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to send purchase order'),
    });
  }

  cancel(): void {
    if (!this.po) return;
    if (!confirm(`Cancel purchase order ${this.po.orderNumber}?`)) return;
    this.error = null;
    this.purchaseOrderApi.cancel(this.po.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to cancel purchase order'),
    });
  }

  parseSerials(text: string): string[] {
    return (text || '')
      .split(/[\n,;]+/)
      .map((x) => x.trim())
      .filter((x) => x.length > 0);
  }

  serialCount(row: ReceiveRow): number {
    return this.parseSerials(row.serialText).length;
  }

  recordReceipt(): void {
    if (!this.po) return;
    const lines = this.receiveRows
      .filter((r) => r.quantityReceived > 0)
      .map((r) => ({
        lineId: r.lineId,
        quantityReceived: r.quantityReceived,
        batchNumber: r.trackingMode === 'BATCH' && r.batchNumber.trim() ? r.batchNumber.trim() : null,
        expiryDate: r.trackingMode === 'BATCH' && r.expiryDate ? r.expiryDate : null,
        serialNumbers: r.trackingMode === 'SERIAL' ? this.parseSerials(r.serialText) : undefined,
      }));
    const serialProblem = this.receiveRows.find((r) => r.quantityReceived > 0 && r.trackingMode === 'SERIAL' && this.serialCount(r) !== Number(r.quantityReceived));
    if (serialProblem) {
      this.receiveError = `${serialProblem.productName}: enter exactly ${serialProblem.quantityReceived} serial number(s) (${this.serialCount(serialProblem)} entered)`;
      return;
    }
    if (lines.length === 0) {
      this.receiveError = 'Enter a quantity for at least one line';
      return;
    }
    this.receiveError = null;
    const landed = this.landedCost && Number(this.landedCost) > 0 ? Number(this.landedCost) : null;
    this.purchaseOrderApi
      .receive(this.po.id, { lines, landedCost: landed, landedCostPaid: landed ? this.landedCostPaid : false, landedCostNote: landed ? this.landedCostNote.trim() || null : null })
      .subscribe({
      next: () => {
        this.landedCost = null;
        this.landedCostNote = '';
        this.landedCostPaid = false;
        this.load();
      },
      error: (err) => (this.receiveError = err?.error?.message ?? 'Failed to record receipt'),
    });
  }

  processReturn(): void {
    if (!this.po) return;
    const lines = this.returnRows.filter((r) => r.quantity > 0).map((r) => ({ lineId: r.lineId, quantity: r.quantity }));
    if (lines.length === 0) {
      this.returnError = 'Enter a quantity for at least one line';
      return;
    }
    this.returnError = null;
    this.purchaseReturnApi.process({ purchaseOrderId: this.po.id, lines, reason: this.returnReason || null }).subscribe({
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
      case 'SENT':
        return 'badge-secondary';
      case 'PARTIALLY_RECEIVED':
        return 'badge-warning';
      case 'RECEIVED':
        return 'badge-success';
      case 'CANCELLED':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
