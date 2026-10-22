import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CustomerApiService } from '../core/api/customer-api.service';
import { Customer } from '../core/models';

/**
 * "+ New customer" link that opens a small inline panel (name, phone, email, tax ID), creates the customer and emits it so the
 * form it sits in can add it to its picker and select it. Same fields as the sale-order form's own quick-add. Its inputs are
 * standalone, so they never become part of (or submit) the surrounding form.
 */
@Component({
  selector: 'app-quick-customer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="margin-top:0.3rem;">
      <button type="button" class="btn btn-link btn-sm" style="padding:0;" *ngIf="!open" (click)="open = true">+ New customer</button>
      <div class="card" style="padding:0.9rem; margin-top:0.4rem;" *ngIf="open">
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <div style="flex:2; min-width:12rem;"><label class="form-label">Name</label>
            <input class="form-control" [ngModelOptions]="{ standalone: true }" [(ngModel)]="name" placeholder="Customer name" (keydown.enter)="$event.preventDefault(); save()" /></div>
          <div style="flex:1; min-width:9rem;"><label class="form-label">Phone</label>
            <input class="form-control" [ngModelOptions]="{ standalone: true }" [(ngModel)]="phone" (keydown.enter)="$event.preventDefault(); save()" /></div>
          <div style="flex:1; min-width:9rem;"><label class="form-label">Email</label>
            <input class="form-control" type="email" [ngModelOptions]="{ standalone: true }" [(ngModel)]="email" (keydown.enter)="$event.preventDefault(); save()" /></div>
          <div style="flex:1; min-width:8rem;"><label class="form-label">Tax ID</label>
            <input class="form-control" [ngModelOptions]="{ standalone: true }" [(ngModel)]="taxId" (keydown.enter)="$event.preventDefault(); save()" /></div>
        </div>
        <div class="flex gap-2">
          <button type="button" class="btn btn-primary btn-sm" (click)="save()" [disabled]="saving">{{ saving ? 'Saving…' : 'Save customer' }}</button>
          <button type="button" class="btn btn-link btn-sm" (click)="cancel()">Cancel</button>
        </div>
      </div>
    </div>
  `,
})
export class QuickCustomerComponent {
  @Output() created = new EventEmitter<Customer>();
  open = false;
  name = '';
  phone = '';
  email = '';
  taxId = '';
  saving = false;
  error: string | null = null;

  constructor(private api: CustomerApiService) {}

  cancel(): void {
    this.open = false;
    this.name = this.phone = this.email = this.taxId = '';
    this.error = null;
  }

  save(): void {
    if (!this.name.trim()) {
      this.error = 'Name is required';
      return;
    }
    this.saving = true;
    this.error = null;
    this.api.create({ name: this.name.trim(), phone: this.phone.trim() || null, email: this.email.trim() || null, taxId: this.taxId.trim() || null }).subscribe({
      next: (c) => { this.saving = false; this.created.emit(c); this.cancel(); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Failed to save customer'; },
    });
  }
}
