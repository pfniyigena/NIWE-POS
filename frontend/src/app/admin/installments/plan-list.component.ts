import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { InstallmentSummaryComponent } from './installment-summary.component';
import { InstallmentApiService, InstallmentPlanSummary } from '../../core/api/installment-api.service';

/** Installments > Agreements: every instalment plan with its progress, plus the headline figures. */
@Component({
  selector: 'app-installment-plan-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, InstallmentSummaryComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Installments</h3>
        <a class="btn btn-primary" routerLink="/admin/installments/new">+ New instalment plan</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <app-installment-summary></app-installment-summary>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All agreements</option>
          <option value="ACTIVE">Active</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option>
        </select>
        <input class="form-control" style="width:16rem;" placeholder="Search customer, plan or invoice" name="q" [(ngModel)]="search" />
        <label style="display:flex; gap:0.3rem; align-items:center;"><input type="checkbox" name="od" [(ngModel)]="overdueOnly" /> Overdue only</label>
      </div>

      <table class="table-sm">
        <thead><tr><th>Plan</th><th>Customer</th><th>Invoice</th><th class="text-end">Total payable</th><th class="text-end">Paid</th><th class="text-end">Outstanding</th><th>Progress</th><th>Next due</th><th>Status</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of visible()">
            <td><a [routerLink]="['/admin/installments', p.id]">{{ p.planNumber }}</a></td>
            <td>{{ p.customerName || '—' }}</td>
            <td><a [routerLink]="['/admin/invoices', p.invoiceId]">{{ p.invoiceNumber }}</a></td>
            <td class="text-end">{{ p.totalPayable | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.paid | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.outstanding | number: '1.0-2' }}</td>
            <td>{{ p.paidInstallments }} / {{ p.numberOfInstallments }}
              <span class="badge badge-danger" *ngIf="p.overdueInstallments" style="margin-left:0.3rem;">{{ p.overdueInstallments }} overdue</span></td>
            <td><span *ngIf="p.nextDueDate">{{ p.nextDueDate }} <span class="text-muted">({{ p.nextDueAmount | number: '1.0-2' }})</span></span><span *ngIf="!p.nextDueDate">—</span></td>
            <td><span class="badge" [class.badge-success]="p.status === 'COMPLETED'" [class.badge-warning]="p.status === 'ACTIVE'" [class.badge-secondary]="p.status === 'CANCELLED'">{{ p.status }}</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!visible().length">No instalment plans found. Open an issued invoice and choose "Pay in instalments", or use the button above.</p>
    </app-shell>
  `,
})
export class InstallmentPlanListComponent implements OnInit {
  plans: InstallmentPlanSummary[] = [];
  status = '';
  search = '';
  overdueOnly = false;
  error: string | null = null;

  constructor(private api: InstallmentApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.plans({ status: this.status }).subscribe({
      next: (p) => {
        this.plans = p;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the plans'),
    });
  }

  visible(): InstallmentPlanSummary[] {
    const q = this.search.trim().toLowerCase();
    return this.plans.filter(
      (p) =>
        (!this.overdueOnly || p.overdueInstallments > 0) &&
        (!q || [p.planNumber, p.customerName, p.invoiceNumber].some((v) => (v ?? '').toLowerCase().includes(q)))
    );
  }
}
