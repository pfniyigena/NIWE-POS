import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { SupplierApiService } from '../core/api/supplier-api.service';

import { T } from '../core/i18n/t';
@Component({
  selector: 'app-supplier-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? ('Edit supplier' | translate) : ('New supplier' | translate) }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Name' | translate }}</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Contact person' | translate }}</label>
          <input class="form-control" name="contactName" [(ngModel)]="contactName" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Phone' | translate }}</label>
          <input class="form-control" name="phone" [(ngModel)]="phone" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Email' | translate }}</label>
          <input class="form-control" type="email" name="email" [(ngModel)]="email" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Tax ID' | translate }}</label>
          <input class="form-control" name="taxId" [(ngModel)]="taxId" [placeholder]="'VAT / tax registration number' | translate" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Address' | translate }}</label>
          <textarea class="form-control" name="address" rows="2" [(ngModel)]="address"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Notes' | translate }}</label>
          <textarea class="form-control" name="notes" rows="2" [(ngModel)]="notes"></textarea>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> {{ 'Active (selectable on a new purchase order)' | translate }}
          </label>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" routerLink="/admin/suppliers">{{ 'Cancel' | translate }}</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class SupplierFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  contactName = '';
  phone = '';
  email = '';
  taxId = '';
  address = '';
  notes = '';
  active = true;
  error: string | null = null;

  constructor(
    private supplierApi: SupplierApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.supplierApi.listAll().subscribe({
      next: (suppliers) => {
        const found = suppliers.find((s) => s.id === this.id);
        if (found) {
          this.name = found.name;
          this.contactName = found.contactName ?? '';
          this.phone = found.phone ?? '';
          this.email = found.email ?? '';
          this.taxId = found.taxId ?? '';
          this.address = found.address ?? '';
          this.notes = found.notes ?? '';
          this.active = found.active;
        }
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load supplier')),
    });
  }

  submit(): void {
    this.error = null;
    const payload = {
      name: this.name,
      contactName: this.contactName || null,
      phone: this.phone || null,
      email: this.email || null,
      taxId: this.taxId || null,
      address: this.address || null,
      notes: this.notes || null,
      active: this.active,
    };
    const call = this.id ? this.supplierApi.update(this.id, payload) : this.supplierApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/suppliers']),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to save supplier')),
    });
  }
}
