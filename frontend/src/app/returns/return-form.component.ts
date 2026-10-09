import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { OrderApiService } from '../core/api/order-api.service';
import { ReturnsApiService } from '../core/api/returns-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { Order, PaymentMethodOption } from '../core/models';

interface ReturnRow {
  lineId: number;
  description: string;
  soldQty: number;
  returnQty: number;
}

@Component({
  selector: 'app-return-form',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Process a return</h3>

      <div class="flex gap-2 mb-3">
        <input class="form-control" placeholder="Invoice number, e.g. POS-1234567890" [(ngModel)]="invoiceNumber" />
        <button class="btn btn-primary" (click)="find()">Find</button>
      </div>

      <div class="alert alert-warning" *ngIf="notFound">No invoice found matching '{{ notFound }}'</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div *ngIf="order as o">
        <p><strong>{{ o.invoiceNumber }}</strong> — {{ o.customerName }} — {{ o.status }}</p>

        <div class="flex gap-2 mb-2">
          <button type="button" class="btn btn-sm btn-outline-secondary" (click)="returnAll()">
            Return all items
          </button>
          <button type="button" class="btn btn-sm btn-outline-secondary" (click)="clearAll()">
            Clear
          </button>
        </div>

        <table class="table-sm mb-3">
          <thead>
            <tr>
              <th>Item</th>
              <th>Sold qty</th>
              <th>Return qty</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of rows">
              <td>{{ row.description }}</td>
              <td>{{ row.soldQty }}</td>
              <td>
                <input
                  type="number"
                  class="form-control form-control-sm"
                  style="width:90px;"
                  min="0"
                  [max]="row.soldQty"
                  [(ngModel)]="row.returnQty"
                />
              </td>
            </tr>
          </tbody>
        </table>

        <div class="flex gap-2 mb-3">
          <div class="grow">
            <label class="form-label">Refund method</label>
            <select class="form-select" [(ngModel)]="refundMethod">
              <option *ngFor="let m of paymentMethods" [value]="m.code">{{ m.name }}</option>
            </select>
          </div>
          <div class="grow">
            <label class="form-label">Reason</label>
            <input class="form-control" [(ngModel)]="reason" />
          </div>
        </div>
        <button class="btn btn-danger" (click)="submit()">Process return</button>
      </div>
    </app-shell>
  `,
})
export class ReturnFormComponent implements OnInit {
  invoiceNumber = '';
  notFound: string | null = null;
  error: string | null = null;

  order: Order | null = null;
  rows: ReturnRow[] = [];
  paymentMethods: PaymentMethodOption[] = [];
  refundMethod = 'CASH';
  reason = '';

  constructor(
    private orderApi: OrderApiService,
    private returnsApi: ReturnsApiService,
    private paymentMethodApi: PaymentMethodApiService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.paymentMethodApi.listActive().subscribe((methods) => {
      this.paymentMethods = methods;
      if (methods.length > 0) {
        this.refundMethod = methods[0].code;
      }
    });
  }

  find(): void {
    this.notFound = null;
    this.error = null;
    this.order = null;
    const query = this.invoiceNumber.trim();
    if (!query) return;
    this.orderApi.getByInvoiceNumber(query).subscribe({
      next: (o) => {
        this.order = o;
        this.rows = o.lines.map((l) => ({ lineId: l.id, description: l.description, soldQty: l.quantity, returnQty: 0 }));
      },
      error: () => (this.notFound = query),
    });
  }

  /**
   * Task: "add any option of returning all items on the order at once" — fills every row's
   * return qty up to what was originally sold, so a full-order return is one click instead of
   * typing each line by hand. Deliberately not "what's still returnable": the backend is the one
   * source of truth for that (`ReturnsService.processReturn` sums every prior return against each
   * line via `salesReturnLineRepository.sumReturnedQuantityForLine`, which this form doesn't have
   * a copy of) — on an order that already had a partial return, submitting this pre-fill as-is
   * gets rejected with a precise "only N remaining (of M sold, K already returned)" message
   * naming the exact line, same as it always has for a manually-typed over-return; the cashier
   * adjusts that row down and resubmits. For the common case — nothing returned yet — this fills
   * every row correctly on the first try.
   */
  returnAll(): void {
    this.rows.forEach((row) => (row.returnQty = row.soldQty));
  }

  clearAll(): void {
    this.rows.forEach((row) => (row.returnQty = 0));
  }

  submit(): void {
    if (!this.order) return;
    const lines = this.rows.filter((r) => r.returnQty > 0).map((r) => ({ orderLineId: r.lineId, quantity: r.returnQty }));
    if (lines.length === 0) {
      this.error = 'Enter a return quantity for at least one item';
      return;
    }
    this.error = null;
    this.returnsApi
      .process({ orderId: this.order.id, lines, refundMethod: this.refundMethod, reason: this.reason })
      .subscribe({
        // Task: "print out the return" — go straight to the printable receipt and auto-print it,
        // same convention as checkout.component.ts navigating to receipt.component.ts after a sale.
        next: (r) => this.router.navigate(['/returns', r.id, 'receipt'], { queryParams: { autoPrint: '1' } }),
        error: (err) => (this.error = err?.error?.message ?? 'Failed to process return'),
      });
  }
}
