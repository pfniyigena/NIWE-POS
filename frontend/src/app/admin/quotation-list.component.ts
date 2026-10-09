import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { QuotationApiService } from '../core/api/quotation-api.service';
import { Branch, Quotation } from '../core/models';
import { BranchApiService } from '../core/api/branch-api.service';
import { branchNameOf, effectiveBranchId } from '../core/branch-util';

/** Admin > Sales > Quotations — Fourier: "Add Quotation ... without touching warehousing." */
@Component({
  selector: 'app-quotation-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Quotations</h3>
        <a class="btn btn-primary" routerLink="/admin/quotations/new">+ New quotation</a>
      </div>
      <div class="flex gap-2 align-center mb-3" *ngIf="branches.length > 1">
        <select class="form-select" style="width:auto;" [(ngModel)]="branchFilter" title="Branch">
          <option [ngValue]="null">All branches</option>
          <option *ngFor="let br of branches" [ngValue]="br.id">{{ br.name }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>Quote #</th>
            <th>Customer</th>
            <th>Date</th>
            <th>Valid until</th>
            <th>Status</th>
            <th *ngIf="branches.length > 1">Branch</th>
            <th class="text-end">Lines</th>
            <th class="text-end">Total</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let q of shown">
            <td>{{ q.quoteNumber }}</td>
            <td>{{ q.customerName }}</td>
            <td>{{ q.quoteDate | date: 'medium' }}</td>
            <td>{{ q.validUntil || '—' }}</td>
            <td><span class="badge" [ngClass]="statusClass(q.status)">{{ statusLabel(q.status) }}</span></td>
            <td *ngIf="branches.length > 1">{{ branchName(q.branchId) }}</td>
            <td class="text-end">{{ q.lines.length }}</td>
            <td class="text-end">{{ q.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/quotations', q.id]">View</a>
            </td>
          </tr>
          <tr *ngIf="quotations.length === 0">
            <td colspan="8" class="text-muted">No quotations yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class QuotationListComponent implements OnInit {
  /** Branch filter + column — only shown with more than one active branch. */
  branches: Branch[] = [];
  branchFilter: number | null = null;

  get shown(): Quotation[] {
    return this.branchFilter == null ? this.quotations : this.quotations.filter((q) => effectiveBranchId(q.branchId, this.branches) === this.branchFilter);
  }

  branchName(id: number | null | undefined): string {
    return branchNameOf(id, this.branches);
  }

  quotations: Quotation[] = [];
  error: string | null = null;

  constructor(private quotationApi: QuotationApiService, private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.quotationApi.listAll().subscribe({
      next: (q) => (this.quotations = q),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load quotations'),
    });
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
