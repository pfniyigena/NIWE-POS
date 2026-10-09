import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SupplierApiService } from '../core/api/supplier-api.service';
import { Supplier } from '../core/models';

/** Admin > Inventory > Suppliers — Task: "Inventory depth ... purchase orders and supplier records." */
@Component({
  selector: 'app-supplier-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Suppliers</h3>
        <div class="flex gap-2">
          <!-- Task: "Add Supplier payment" — store-wide history, across every supplier. -->
          <a class="btn btn-outline-secondary" routerLink="/admin/supplier-payments">Supplier payments</a>
          <a class="btn btn-primary" routerLink="/admin/suppliers/new">+ New supplier</a>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Contact</th>
            <th>Phone</th>
            <th>Email</th>
            <th>Tax ID</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let s of suppliers">
            <td>{{ s.name }}</td>
            <td>{{ s.contactName }}</td>
            <td>{{ s.phone }}</td>
            <td>{{ s.email }}</td>
            <td>{{ s.taxId || '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="s.active">Active</span>
              <span class="badge badge-secondary" *ngIf="!s.active">Inactive</span>
            </td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/suppliers', s.id, 'edit']">Edit</a>
              <!-- Task: "Add Supplier payment" — every supplier can be paid, so this link (unlike Customer's) doesn't gate on any per-row flag. -->
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/suppliers', s.id, 'record-payment']">Record payment</a>
              <button class="btn btn-sm btn-outline-danger" (click)="remove(s)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="suppliers.length === 0">
            <td colspan="7" class="text-muted">No suppliers yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class SupplierListComponent implements OnInit {
  suppliers: Supplier[] = [];
  error: string | null = null;

  constructor(private supplierApi: SupplierApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.supplierApi.listAll().subscribe({
      next: (s) => (this.suppliers = s),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load suppliers'),
    });
  }

  /** Backend either hard-deletes (no PO history) or falls back to deactivating (see SupplierService.delete) — either way this just reloads to show whichever happened. */
  remove(s: Supplier): void {
    if (!confirm(`Remove supplier "${s.name}"? If it's referenced by a purchase order it will be deactivated instead of deleted.`)) return;
    this.error = null;
    this.supplierApi.delete(s.id).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to remove supplier'),
    });
  }
}
