import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { BillApiService } from '../core/api/bill-api.service';
import { Bill } from '../core/models';
import { openPdfForPrint } from '../core/download-file';

/**
 * Admin > Purchases > Refunds — Fourier: "Refunds equivalent with Credit note." Every voided bill,
 * store-wide, newest-voided first. A refund is not a separate document type in this app: it's the
 * same Bill entity once it reaches VOID, carrying its own "REF-" number (see the backend
 * Bill.refundNumber's javadoc) stamped at void time — the accounts-payable mirror of
 * CreditNoteListComponent. "Print" opens that refund's own PDF in a new tab.
 */
@Component({
  selector: 'app-refund-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">Refunds</h3>
      </div>
      <app-list-filter exportKind="refunds" placeholder="Search refund no., bill, supplier…" [statuses]="[]" statusAll="All statuses" [dates]="true" [branches]="[]" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>Refund #</th>
            <th>Bill #</th>
            <th>Supplier</th>
            <th>Voided</th>
            <th>Reason</th>
            <th class="text-end">Amount</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of list.rows">
            <td>{{ b.refundNumber }}</td>
            <td>{{ b.billNumber }}</td>
            <td>{{ b.supplierName }}</td>
            <td>{{ b.voidedAt | date: 'medium' }}</td>
            <td>{{ b.voidReason || '—' }}</td>
            <td class="text-end">{{ b.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/bills', b.id]">View bill</a>
              <button class="btn btn-sm btn-outline-secondary" (click)="print(b)" [disabled]="printingId === b.id">Print</button>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="7" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">Nothing matches these filters.</ng-container><ng-template #nothingYet>No refunds yet — voiding a bill issues one automatically.</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class RefundListComponent implements OnInit {
  error: string | null = null;
  printingId: number | null = null;

  list = new ListState<Bill>((f, p, sz) => this.billApi.refundPage(f, p, sz), 'Failed to load refunds');

  constructor(private billApi: BillApiService) {}

  ngOnInit(): void {
    this.list.load();
  }

  print(bill: Bill): void {
    this.printingId = bill.id;
    this.billApi.refundPdf(bill.id).subscribe({
      next: (blob) => {
        this.printingId = null;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printingId = null;
        this.error = err?.error?.message ?? 'Failed to open refund PDF';
      },
    });
  }
}
