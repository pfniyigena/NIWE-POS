import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ReturnsApiService } from '../core/api/returns-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption, SalesReturn } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

/** Returns with search (invoice, return number, customer, reason), refund method and day filters, pages, and Excel / PDF / CSV. */
@Component({
  selector: 'app-return-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Returns</h3>
        <a class="btn btn-primary" routerLink="/returns/new">+ Process a return</a>
      </div>
      <app-list-filter exportPath="/returns/export" [exportExtra]="extra" [extraActive]="method !== ''" placeholder="Search invoice, return no., customer, reason" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select rt-method" style="width:auto;" [(ngModel)]="method" (ngModelChange)="extraChanged()" name="method" title="Refund method">
          <option value="">Any refund method</option>
          <option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>Return date</th>
            <th>Return no.</th>
            <th>Invoice</th>
            <th>Customer</th>
            <th class="text-end">Refund total</th>
            <th>Method</th>
            <th>Reason</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of list.rows" class="rt-row">
            <td>{{ r.returnDate | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ r.returnNumber }}</td>
            <td>{{ r.invoiceNumber }}</td>
            <td>{{ r.customerName }}</td>
            <td class="text-end">{{ r.refundTotal | number: '1.2-2' }}</td>
            <td>{{ r.refundMethod }}</td>
            <td>{{ r.reason }}<span class="badge badge-secondary" style="margin-left:6px;" *ngIf="r.voidReturn">sale voided</span><span class="text-muted" *ngIf="r.approvedBy"> (approved by {{ r.approvedBy }})</span></td>
            <td>
              <!-- Task: "print out the return" — reprint later, no autoPrint here (manual, like the Receipts list). -->
              <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/returns', r.id, 'receipt']">Print</a>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || method ? 'No returns match these filters.' : 'No returns processed yet.' }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class ReturnListComponent implements OnInit {
  methods: PaymentMethodOption[] = [];
  method = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<SalesReturn>((f, p, sz) => this.returnsApi.page(f, p, sz, { method: this.method }), 'Failed to load returns');

  constructor(private returnsApi: ReturnsApiService, private methodApi: PaymentMethodApiService) {}

  ngOnInit(): void {
    this.list.load();
    this.methodApi.listAll().subscribe({ next: (m) => (this.methods = m), error: () => {} });
  }

  extraChanged(): void {
    this.extra = { method: this.method };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.method = '';
    this.extra = {};
  }
}
