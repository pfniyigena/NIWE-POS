import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PurchaseReturnApiService } from '../core/api/purchase-return-api.service';
import { PurchaseReturn } from '../core/models';

/**
 * Admin > Inventory > Purchase Returns — the store-wide list, mirroring
 * `app-sales-order-return-list` on the sales side. Processing a return itself happens from the
 * originating Purchase Order's own detail page (its "Process return" panel) — there is no
 * separate "new return" route here, just this read-only ledger of every return sent back to a
 * supplier so far.
 */
@Component({
  selector: 'app-purchase-return-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Purchase returns</h3>
      </div>
      <table>
        <thead>
          <tr>
            <th>Return date</th>
            <th>Return #</th>
            <th>Purchase order</th>
            <th>Supplier</th>
            <th class="text-end">Refund due</th>
            <th>Reason</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of returns">
            <td>{{ r.returnDate | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ r.returnNumber }}</td>
            <td>{{ r.purchaseOrderNumber }}</td>
            <td>{{ r.supplierName }}</td>
            <td class="text-end">{{ r.refundTotal | number: '1.2-2' }}</td>
            <td>{{ r.reason }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" *ngIf="r.purchaseOrderId" [routerLink]="['/admin/purchase-orders', r.purchaseOrderId]">View order</a>
            </td>
          </tr>
          <tr *ngIf="returns.length === 0">
            <td colspan="7" class="text-muted">No purchase returns processed yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class PurchaseReturnListComponent implements OnInit {
  returns: PurchaseReturn[] = [];

  constructor(private purchaseReturnApi: PurchaseReturnApiService) {}

  ngOnInit(): void {
    this.purchaseReturnApi.listAll().subscribe((r) => (this.returns = r));
  }
}
