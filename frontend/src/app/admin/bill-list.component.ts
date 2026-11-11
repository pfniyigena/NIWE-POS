import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { BillApiService } from '../core/api/bill-api.service';
import { Branch, Bill } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';

import { T } from '../core/i18n/t';
/**
 * Admin > Purchases > Bills — Fourier: "The Purchase order should flow same workflow as Sale order
 * (with Bills and Refunds equivalent with Invoices and Credit note)." Store-wide ledger; one is
 * generated automatically once a purchase order is fully received, or manually from that order's
 * own detail page from the moment it's sent to the supplier — see the backend BillService's own
 * class javadoc. Deliberately no "+ New bill" here: unlike Invoice, there is no standalone bill
 * with no purchase order behind it in this app.
 */
@Component({
  selector: 'app-bill-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Bills' | translate }}</h3>
      </div>
      <app-list-filter exportKind="bills" [placeholder]="'Search bill no., supplier, purchase order…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="true" [branches]="branches" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Bill #' | translate }}</th>
            <th>{{ 'Purchase order' | translate }}</th>
            <th>{{ 'Supplier' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
            <th class="text-end">{{ 'Total' | translate }}</th>
            <th class="text-end">{{ 'Outstanding' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of list.rows">
            <td>{{ b.billNumber }}</td>
            <td>{{ b.purchaseOrderNumber || '—' }}</td>
            <td>{{ b.supplierName }}</td>
            <td>{{ b.billDate | date: 'medium' }}</td>
            <td><span class="badge" [ngClass]="statusClass(b.status)">{{ b.status }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(b.branchId) }}</td>
            <td class="text-end">{{ b.totalAmount | number: '1.2-2' }}</td>
            <td class="text-end">{{ b.status === 'ISSUED' ? (b.amountOutstanding | number: '1.2-2') : '—' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/bills', b.id]">{{ 'View' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="8" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ 'No bills yet — one is generated once a purchase order is fully received, or manually from that order\\'s own detail page.' | translate }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class BillListComponent implements OnInit {
  /** Branch column + choice — only shown with more than one active branch. */
  branches: Branch[] = [];


  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  error: string | null = null;

  list = new ListState<Bill>((f, p, sz) => this.billApi.page(f, p, sz), 'Failed to load bills');
  statuses = [{ value: 'DRAFT', label: T('Draft') }, { value: 'ISSUED', label: T('Issued') }, { value: 'VOID', label: T('Void') }];
  constructor(private billApi: BillApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.list.load();
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'ISSUED':
        return 'badge-success';
      case 'VOID':
        return 'badge-danger';
      default:
        return 'badge-secondary';
    }
  }
}
