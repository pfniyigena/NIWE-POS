import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CustomerApiService } from '../core/api/customer-api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit customer' : 'New customer' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">Phone</label>
          <input class="form-control" name="phone" [(ngModel)]="phone" />
        </div>
        <div class="form-group">
          <label class="form-label">Email</label>
          <input class="form-control" type="email" name="email" [(ngModel)]="email" />
        </div>
        <div class="form-group">
          <label class="form-label">Tax ID</label>
          <input class="form-control" name="taxId" [(ngModel)]="taxId" />
        </div>
        <hr />
        <!-- Task: "Only Admin can enable Customer Credit" (earlier pass) stays true even though
             this whole form is now open to any logged-in user (Task: "Cashier can add or modify
             customer" / "Give Cashier Customer menu") — the store-credit fields are read-only for
             a non-admin, same visible-but-disabled pattern as the terminal's discount inputs, so a
             cashier can still see a customer's existing credit status while editing their basic
             details, just can't change it. The backend independently ignores these fields from a
             non-admin request regardless of what a disabled input sends — see CustomerController. -->
        <h4 class="mb-2">Store credit</h4>
        <div class="form-group">
          <label class="flex align-center gap-2">
            <input type="checkbox" name="creditEnabled" [(ngModel)]="creditEnabled" [disabled]="!isAdmin" />
            Allow this customer to buy on credit
          </label>
          <div class="text-muted" style="font-size:0.8rem;" *ngIf="!isAdmin">Only an admin can change this.</div>
        </div>
        <div class="form-group" *ngIf="creditEnabled">
          <label class="form-label">Credit limit</label>
          <input class="form-control" type="number" min="0" step="0.01" name="creditLimit" [(ngModel)]="creditLimit" [disabled]="!isAdmin" />
        </div>
        <div class="form-group" *ngIf="creditEnabled">
          <label class="form-label">Default payment term (days)</label>
          <input class="form-control" type="number" min="0" step="1" name="defaultCreditTermDays" [(ngModel)]="defaultCreditTermDays" placeholder="Optional — leave blank for no default due date" [disabled]="!isAdmin" />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/customers">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class CustomerFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  phone = '';
  email = '';
  taxId = '';
  creditEnabled = false;
  creditLimit: number | null = 0;
  defaultCreditTermDays: number | null = null;
  error: string | null = null;

  constructor(
    private customerApi: CustomerApiService,
    private route: ActivatedRoute,
    private router: Router,
    private auth: AuthService
  ) {}

  /** Task: "Only Admin can enable Customer Credit" — gates the Store credit section's inputs; see the template comment above it. */
  get isAdmin(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN';
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.customerApi.list().subscribe({
      next: (customers) => {
        const found = customers.find((c) => c.id === this.id);
        if (found) {
          this.name = found.name;
          this.phone = found.phone ?? '';
          this.email = found.email ?? '';
          this.taxId = found.taxId ?? '';
          this.creditEnabled = found.creditEnabled;
          this.creditLimit = found.creditLimit;
          this.defaultCreditTermDays = found.defaultCreditTermDays;
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load customer'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = {
      name: this.name,
      phone: this.phone || null,
      email: this.email || null,
      taxId: this.taxId || null,
      creditEnabled: this.creditEnabled,
      creditLimit: this.creditLimit ?? 0,
      defaultCreditTermDays: this.defaultCreditTermDays || null,
    };
    const call = this.id
      ? this.customerApi.update(this.id, payload)
      : this.customerApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/customers']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save customer'),
    });
  }
}
