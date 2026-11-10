import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SalesOrderReturnApiService } from '../core/api/sales-order-return-api.service';
import { SalesOrderReturn } from '../core/models';

/**
 * Admin > Sales > Sale Order Returns — the store-wide list, mirroring the POS Returns list
 * (`app-return-list`). Processing a return itself happens from the originating Sale Order's own
 * detail page (its "Process return" panel), the same way fulfilling an order does — there is no
 * separate "new return" route here, just this read-only ledger of every return processed so far.
 */
@Component({
  selector: 'app-sales-order-return-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Sale order returns' | translate }}</h3>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Return date' | translate }}</th>
            <th>{{ 'Return #' | translate }}</th>
            <th>{{ 'Sale order' | translate }}</th>
            <th>{{ 'Customer' | translate }}</th>
            <th class="text-end">{{ 'Refund due' | translate }}</th>
            <th>{{ 'Reason' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of returns">
            <td>{{ r.returnDate | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ r.returnNumber }}</td>
            <td>{{ r.salesOrderNumber }}</td>
            <td>{{ r.customerName }}</td>
            <td class="text-end">{{ r.refundTotal | number: '1.2-2' }}</td>
            <td>{{ r.reason }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" *ngIf="r.salesOrderId" [routerLink]="['/admin/sales-orders', r.salesOrderId]">{{ 'View order' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="returns.length === 0">
            <td colspan="7" class="text-muted">{{ 'No sale order returns processed yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SalesOrderReturnListComponent implements OnInit {
  returns: SalesOrderReturn[] = [];

  constructor(private salesOrderReturnApi: SalesOrderReturnApiService) {}

  ngOnInit(): void {
    this.salesOrderReturnApi.listAll().subscribe((r) => (this.returns = r));
  }
}
