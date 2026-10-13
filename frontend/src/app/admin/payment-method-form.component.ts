import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';

@Component({
  selector: 'app-payment-method-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit payment method' : 'New payment method' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Code</label>
          <input class="form-control" name="code" [(ngModel)]="code" [disabled]="!!id" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
            The code can't be changed once created — it's stored on past sales and refunds.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Display name</label>
          <input class="form-control" name="name" [(ngModel)]="name" required />
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="cash" [(ngModel)]="cash" /> Cash-equivalent (counts toward till cash reconciliation)
          </label>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="credit" [(ngModel)]="credit" /> Store credit (selling with this method sells on the customer's tab)
          </label>
        </div>
        <div class="form-group">
          <label class="form-label">
            <input type="checkbox" name="roomCharge" [(ngModel)]="roomCharge" /> Charge to room (hotel — the amount goes on an in-house guest's room bill)
          </label>
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" /> Active (shown at checkout/returns)
          </label>
        </div>
        <div class="form-group">
          <label class="form-label">Display order</label>
          <input class="form-control" style="max-width:120px;" type="number" step="1" name="sortOrder" [(ngModel)]="sortOrder" />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            Controls the order methods appear on the checkout screen — lower numbers show first. Methods with the same number are ordered alphabetically by name.
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/payment-methods">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class PaymentMethodFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  cash = false;
  credit = false;
  roomCharge = false;
  active = true;
  sortOrder = 0;
  error: string | null = null;

  constructor(
    private paymentMethodApi: PaymentMethodApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.paymentMethodApi.listAll().subscribe({
      next: (methods) => {
        const found = methods.find((m) => m.id === this.id);
        if (found) {
          this.code = found.code;
          this.name = found.name;
          this.cash = found.cash;
          this.credit = found.credit;
          this.roomCharge = !!found.roomCharge;
          this.active = found.active;
          this.sortOrder = found.sortOrder;
        }
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load payment method'),
    });
  }

  submit(): void {
    this.error = null;
    const call = this.id
      ? this.paymentMethodApi.update(this.id, { name: this.name, cash: this.cash, credit: this.credit, roomCharge: this.roomCharge, active: this.active, sortOrder: this.sortOrder })
      : this.paymentMethodApi.create({ code: this.code, name: this.name, cash: this.cash, credit: this.credit, roomCharge: this.roomCharge, sortOrder: this.sortOrder });
    call.subscribe({
      next: () => this.router.navigate(['/admin/payment-methods']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save payment method'),
    });
  }
}
