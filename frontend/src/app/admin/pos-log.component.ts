import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { PosException, RegisterSummary } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

import { T } from '../core/i18n/t';
/** Voids and returns: sales voided after payment, returns (with who approved them) and carts dropped before payment, with search, filters, pages and Excel / PDF / CSV. */
@Component({
  selector: 'app-pos-log',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <h3 class="mb-3">Voids &amp; returns</h3>
      <app-list-filter exportPath="/pos/exceptions/export" [exportExtra]="extra" [extraActive]="registerId !== ''" [placeholder]="'Search reference, customer, who, reason' | translate" [statuses]="types" statusAll="Everything" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select pl-register" style="width:auto;" [(ngModel)]="registerId" (ngModelChange)="extraChanged()" name="register" [title]="'Register' | translate">
          <option value="">{{ 'Any register' | translate }}</option>
          <option *ngFor="let r of registers" [value]="r.id">{{ r.name }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr><th>{{ 'When' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Reference' | translate }}</th><th>{{ 'Customer' | translate }}</th><th>{{ 'Register' | translate }}</th><th>{{ 'By' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Reason' | translate }}</th><th>{{ 'Approved by' | translate }}</th><th></th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let e of list.rows" class="pl-row">
            <td>{{ e.at | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td><span class="badge" [class.badge-secondary]="e.type !== 'RETURN'">{{ label(e.type) }}</span></td>
            <td>{{ e.ref }}</td>
            <td>{{ e.customer }}</td>
            <td>{{ e.registerName }}</td>
            <td>{{ e.by }}</td>
            <td class="text-end">{{ e.amount | number: '1.2-2' }}</td>
            <td>{{ e.reason }}</td>
            <td>{{ e.approvedBy }}</td>
            <td><a *ngIf="e.type !== 'DROPPED_CART' && e.orderId" [routerLink]="['/receipt', e.orderId]">{{ 'Receipt' | translate }}</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || registerId ? ('Nothing matches these filters.' | translate) : ('No sale has been voided or returned, and no cart dropped, yet.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class PosLogComponent implements OnInit {
  types = [
    { value: 'VOID', label: T('Voided sales') },
    { value: 'RETURN', label: T('Returns') },
    { value: 'DROPPED_CART', label: T('Dropped carts') },
    { value: 'DISCOUNT', label: T('Discounts') },
    { value: 'PRICE_OVERRIDE', label: T('Price changes') },
    { value: 'REPRINT', label: T('Receipt reprints') },
  ];
  registers: RegisterSummary[] = [];
  registerId = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<PosException>((f, p, sz) => this.registerApi.exceptionsPage(f, p, sz, { registerId: this.registerId }), 'Failed to load the log');

  constructor(private registerApi: RegisterApiService) {}

  ngOnInit(): void {
    this.list.load();
    this.registerApi.listRegisters().subscribe({ next: (r) => (this.registers = r), error: () => {} });
  }

  label(type: string): string {
    return this.types.find((t) => t.value === type)?.label.replace('Voided sales', 'Voided sale').replace('Returns', 'Return').replace('Dropped carts', 'Dropped cart').replace('Discounts', 'Discount').replace('Price changes', 'Price changed').replace('Receipt reprints', 'Receipt reprint') ?? type;
  }

  extraChanged(): void {
    this.extra = { registerId: this.registerId };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.registerId = '';
    this.extra = {};
  }
}
