import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { InvoiceApiService } from '../core/api/invoice-api.service';
import { Branch, Invoice } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';

import { T } from '../core/i18n/t';
/**
 * Admin > Sales > Invoices — Fourier: "Add Invoice ... without touching warehousing"; later:
 * "someone can create invoice (with invoice lines) without creating sales order." Store-wide
 * ledger; one is still generated from a Sales Order's own detail page once confirmed, or created
 * standalone right here via "+ New invoice".
 *
 * <p>Fourier: "on the customer ... view button where we can have button to view the list of
 * invoices, payments etc" — arriving from a customer's own row via `?customerId=&customerName=`
 * (see CustomerListComponent's "Invoices" link) narrows this to just that customer's invoices,
 * same `?productId=&productName=` convention ReportViewerComponent's own Stock Movement History
 * link already uses.
 */
@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <div>
          <h3 style="margin-bottom:0;">{{ customerName ? ('Invoices for {p0}' | translate: { p0: customerName }) : ('Invoices' | translate) }}</h3>
          <a *ngIf="customerName" class="text-muted" routerLink="/admin/invoices" style="font-size:0.85rem;">{{ 'Show every customer\\'s invoices' | translate }}</a>
        </div>
        <a class="btn btn-primary" routerLink="/admin/invoices/new">{{ '+ New invoice' | translate }}</a>
      </div>
      <app-list-filter exportKind="invoices" [exportExtra]="{ customerId: customerId }" [placeholder]="'Search invoice no., customer, sale order…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="true" [branches]="branches" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Invoice #' | translate }}</th>
            <th>{{ 'Sale order' | translate }}</th>
            <th>{{ 'Customer' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Due date' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
            <th class="text-end">{{ 'Total' | translate }}</th>
            <th class="text-end">{{ 'Outstanding' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let inv of list.rows">
            <td>{{ inv.invoiceNumber }}</td>
            <td>{{ inv.salesOrderNumber || '—' }}</td>
            <td>{{ inv.customerName }}</td>
            <td>{{ inv.invoiceDate | date: 'medium' }}</td>
            <td>{{ inv.dueDate || '—' }}</td>
            <td><span class="badge" [ngClass]="statusClass(inv.status)">{{ inv.status }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(inv.branchId) }}</td>
            <td class="text-end">{{ inv.totalAmount | number: '1.2-2' }}</td>
            <td class="text-end">{{ inv.status === 'ISSUED' ? (inv.amountOutstanding | number: '1.2-2') : '—' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/invoices', inv.id]">{{ 'View' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="9" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ customerName ? ('No invoices for {p0} yet.' | translate: { p0: customerName }) : ('No invoices yet — generate one from a sale order’s detail page once it’s confirmed, or create one directly with "+ New invoice".' | translate) }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class InvoiceListComponent implements OnInit {
  /** Branch column + choice — only shown with more than one active branch. */
  branches: Branch[] = [];


  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  error: string | null = null;
  /** Fourier: "on the customer ... view button" — set from `?customerId=&customerName=`, null for the store-wide list. */
  customerId: number | null = null;
  customerName: string | null = null;

  list = new ListState<Invoice>((f, p, sz) => this.invoiceApi.page(f, p, sz, { customerId: this.customerId }), 'Failed to load invoices');
  statuses = [{ value: 'DRAFT', label: T('Draft') }, { value: 'ISSUED', label: T('Issued') }, { value: 'VOID', label: T('Void') }];
  constructor(private invoiceApi: InvoiceApiService, private route: ActivatedRoute, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    const customerIdParam = this.route.snapshot.queryParamMap.get('customerId');
    if (customerIdParam) {
      this.customerId = Number(customerIdParam);
      this.customerName = this.route.snapshot.queryParamMap.get('customerName') ?? `#${customerIdParam}`;
    }
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
