import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { InstallmentSummaryComponent } from './installment-summary.component';
import { InstallmentApiService, InstallmentPlanSummary } from '../../core/api/installment-api.service';

import { T } from '../../core/i18n/t';
/** Installments > Agreements: every instalment plan with its progress, plus the headline figures. */
@Component({
  selector: 'app-installment-plan-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, InstallmentSummaryComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Installments' | translate }}</h3>
        <div class="flex gap-2"><a class="btn btn-outline-primary" routerLink="/admin/installments/collections">{{ 'Collections' | translate }}</a><a class="btn btn-primary" routerLink="/admin/installments/new">{{ '+ New instalment plan' | translate }}</a></div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <app-installment-summary></app-installment-summary>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">{{ 'All agreements' | translate }}</option>
          <option value="ACTIVE">{{ 'Active' | translate }}</option><option value="DEFAULTED">{{ 'Defaulted' | translate }}</option><option value="COMPLETED">{{ 'Completed' | translate }}</option><option value="CANCELLED">{{ 'Cancelled' | translate }}</option><option value="WRITTEN_OFF">{{ 'Written off' | translate }}</option>
        </select>
        <input class="form-control" style="width:16rem;" [placeholder]="'Search customer, plan or invoice' | translate" name="q" [(ngModel)]="search" />
        <label style="display:flex; gap:0.3rem; align-items:center;"><input type="checkbox" name="od" [(ngModel)]="overdueOnly" /> {{ 'Overdue only' | translate }}</label>
      </div>

      <table class="table-sm">
        <thead><tr><th>{{ 'Plan' | translate }}</th><th>{{ 'Customer' | translate }}</th><th>{{ 'Invoice' | translate }}</th><th class="text-end">{{ 'Total payable' | translate }}</th><th class="text-end">{{ 'Paid' | translate }}</th><th class="text-end">{{ 'Outstanding' | translate }}</th><th>{{ 'Progress' | translate }}</th><th>{{ 'Next due' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of visible()">
            <td><a [routerLink]="['/admin/installments', p.id]">{{ p.planNumber }}</a></td>
            <td>{{ p.customerName || '—' }}</td>
            <td><a [routerLink]="['/admin/invoices', p.invoiceId]">{{ p.invoiceNumber }}</a></td>
            <td class="text-end">{{ p.totalPayable | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.paid | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.outstanding | number: '1.0-2' }}</td>
            <td>{{ p.paidInstallments }} / {{ p.numberOfInstallments }}
              <span class="badge badge-danger" *ngIf="p.overdueInstallments" style="margin-left:0.3rem;">{{ '{p0} overdue' | translate: { p0: (p.overdueInstallments) } }}</span></td>
            <td><span *ngIf="p.nextDueDate">{{ p.nextDueDate }} <span class="text-muted">({{ p.nextDueAmount | number: '1.0-2' }})</span></span><span *ngIf="!p.nextDueDate">—</span></td>
            <td><span class="badge" [class.badge-success]="p.status === 'COMPLETED'" [class.badge-warning]="p.status === 'ACTIVE'" [class.badge-danger]="p.status === 'DEFAULTED'" [class.badge-secondary]="p.status === 'CANCELLED' || p.status === 'WRITTEN_OFF'">{{ p.status === 'WRITTEN_OFF' ? ('WRITTEN OFF' | translate) : (p.status | translate) }}</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!visible().length">{{ 'No instalment plans found. Open an issued invoice and choose "Pay in instalments", or use the button above.' | translate }}</p>
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
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the plans')),
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
