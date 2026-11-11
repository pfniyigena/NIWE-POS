import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { Supplier } from '../core/models';

import { T } from '../core/i18n/t';
/** Admin > Inventory > Suppliers — Task: "Inventory depth ... purchase orders and supplier records." */
@Component({
  selector: 'app-supplier-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Suppliers' | translate }}</h3>
        <div class="flex gap-2">
          <!-- Task: "Add Supplier payment" — store-wide history, across every supplier. -->
          <a class="btn btn-outline-secondary" routerLink="/admin/supplier-payments">{{ 'Supplier payments' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/suppliers/new">{{ '+ New supplier' | translate }}</a>
        </div>
      </div>
      <app-list-filter exportKind="suppliers" statusParam="active" [placeholder]="'Search name, contact, phone, e-mail…' | translate" [statuses]="statuses" statusAll="All suppliers" [dates]="false" [branches]="[]" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Contact' | translate }}</th>
            <th>{{ 'Phone' | translate }}</th>
            <th>{{ 'Email' | translate }}</th>
            <th>{{ 'Tax ID' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let s of list.rows">
            <td>{{ s.name }}</td>
            <td>{{ s.contactName }}</td>
            <td>{{ s.phone }}</td>
            <td>{{ s.email }}</td>
            <td>{{ s.taxId || '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="s.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!s.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/suppliers', s.id, 'edit']">{{ 'Edit' | translate }}</a>
              <!-- Task: "Add Supplier payment" — every supplier can be paid, so this link (unlike Customer's) doesn't gate on any per-row flag. -->
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/suppliers', s.id, 'record-payment']">{{ 'Record payment' | translate }}</a>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(s)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="7" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ 'No suppliers yet.' | translate }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class SupplierListComponent implements OnInit {
  error: string | null = null;

  list = new ListState<Supplier>((f, p, sz) => this.supplierApi.page(f, p, sz, f.status === '' ? null : f.status === 'true'), 'Failed to load suppliers');
  statuses = [{ value: 'true', label: T('Active only') }, { value: 'false', label: T('Inactive only') }];
  constructor(private supplierApi: SupplierApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.list.load();
  }

  /** Backend either hard-deletes (no PO history) or falls back to deactivating (see SupplierService.delete) — either way this just reloads to show whichever happened. */
  remove(s: Supplier): void {
    if (!confirm(T('Remove supplier "{p0}"? If it\'s referenced by a purchase order it will be deactivated instead of deleted.', { p0: s.name }))) return;
    this.error = null;
    this.supplierApi.delete(s.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to remove supplier')),
    });
  }
}
