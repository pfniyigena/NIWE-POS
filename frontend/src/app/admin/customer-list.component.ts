import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { CustomerApiService } from '../core/api/customer-api.service';
import { Customer } from '../core/models';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Customers' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/customers/new">{{ '+ New customer' | translate }}</a>
      </div>
      <app-list-filter exportKind="customers" [placeholder]="'Search name, phone, e-mail, tax no.…' | translate" [statuses]="[]" statusAll="All statuses" [dates]="false" [branches]="[]" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Phone' | translate }}</th>
            <th>{{ 'Email' | translate }}</th>
            <th>{{ 'Tax ID' | translate }}</th>
            <th>{{ 'Credit' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of list.rows">
            <td>{{ c.name }}</td>
            <td>{{ c.phone }}</td>
            <td>{{ c.email }}</td>
            <td>{{ c.taxId || '—' }}</td>
            <td>
              <span *ngIf="c.creditEnabled" class="badge badge-success">{{ 'Enabled · limit {p0}' | translate: { p0: (c.creditLimit | number: '1.2-2') } }}</span>
              <span *ngIf="!c.creditEnabled" class="text-muted">{{ 'Off' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <!-- Fourier: "Better add one button (View) on customer then put all others buttons
                   on view form" — Edit/Invoices/Payments/Statement/Record payment all now live on
                   CustomerDetailComponent, reached from here. -->
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/customers', c.id]">{{ 'View' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="6" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ 'No customers yet.' | translate }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class CustomerListComponent implements OnInit {
  error: string | null = null;

  list = new ListState<Customer>((f, p, sz) => this.customerApi.page(f, p, sz), 'Failed to load customers');

  constructor(private customerApi: CustomerApiService) {}

  ngOnInit(): void {
    this.list.load();
  }
}
