import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { ListFilterComponent } from '../shared/list-filter.component';
import { ListState } from '../core/api/list-state';
import { QuotationApiService } from '../core/api/quotation-api.service';
import { Branch, Quotation } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';

/** Admin > Sales > Quotations — Fourier: "Add Quotation ... without touching warehousing." */
@Component({
  selector: 'app-quotation-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ListFilterComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error || list.error">{{ error || list.error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Quotations' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/quotations/new">{{ '+ New quotation' | translate }}</a>
      </div>
      <app-list-filter exportKind="quotations" [placeholder]="'Search quote no., customer…' | translate" [statuses]="statuses" statusAll="All statuses" [dates]="true" [branches]="branches" (filterChange)="list.apply($event)"></app-list-filter>
      <table>
        <thead>
          <tr>
            <th>{{ 'Quote #' | translate }}</th>
            <th>{{ 'Customer' | translate }}</th>
            <th>{{ 'Date' | translate }}</th>
            <th>{{ 'Valid until' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th>
            <th class="text-end">{{ 'Lines' | translate }}</th>
            <th class="text-end">{{ 'Total' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let q of list.rows">
            <td>{{ q.quoteNumber }}</td>
            <td>{{ q.customerName }}</td>
            <td>{{ q.quoteDate | date: 'medium' }}</td>
            <td>{{ q.validUntil || '—' }}</td>
            <td><span class="badge" [ngClass]="statusClass(q.status)">{{ statusLabel(q.status) }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(q.branchId) }}</td>
            <td class="text-end">{{ q.lines.length }}</td>
            <td class="text-end">{{ q.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/quotations', q.id]">{{ 'View' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="list.loaded && list.rows.length === 0">
            <td colspan="8" class="text-muted"><ng-container *ngIf="list.filtered; else nothingYet">{{ 'Nothing matches these filters.' | translate }}</ng-container><ng-template #nothingYet>{{ 'No quotations yet.' | translate }}</ng-template></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class QuotationListComponent implements OnInit {
  /** Branch column + choice — only shown with more than one active branch. */
  branches: Branch[] = [];


  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  error: string | null = null;

  list = new ListState<Quotation>((f, p, sz) => this.quotationApi.page(f, p, sz), 'Failed to load quotations');
  statuses = [{ value: 'DRAFT', label: 'Draft' }, { value: 'SENT', label: 'Sent' }, { value: 'ACCEPTED', label: 'Accepted' }, { value: 'REJECTED', label: 'Rejected' }, { value: 'EXPIRED', label: 'Expired' }, { value: 'CONVERTED', label: 'Converted' }];
  constructor(private quotationApi: QuotationApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.list.load();
  }

  statusLabel(status: string): string {
    return status.replace('_', ' ');
  }

  statusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'badge-secondary';
      case 'SENT':
        return 'badge-secondary';
      case 'ACCEPTED':
        return 'badge-success';
      case 'REJECTED':
        return 'badge-danger';
      case 'EXPIRED':
        return 'badge-warning';
      case 'CONVERTED':
        return 'badge-success';
      default:
        return 'badge-secondary';
    }
  }
}
