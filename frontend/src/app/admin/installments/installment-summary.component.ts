import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InstallmentApiService, InstallmentSummary } from '../../core/api/installment-api.service';

/** The four instalment figures (active agreements, outstanding balance, overdue, due this month) — on the Installments page and the dashboard. */
@Component({
  selector: 'app-installment-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ng-container *ngIf="s">
      <div class="stat-grid mb-3">
        <div class="stat-tile"><div class="stat-label">Active agreements</div><div class="stat-value">{{ s.activeAgreements }}</div></div>
        <div class="stat-tile"><div class="stat-label">Outstanding balance</div><div class="stat-value">{{ s.outstandingBalance | number: '1.0-2' }}</div></div>
        <div class="stat-tile" [class.warn]="s.overdueAmount > 0">
          <div class="stat-label">Overdue amount</div><div class="stat-value">{{ s.overdueAmount | number: '1.0-2' }}</div>
          <div class="text-muted" style="font-size:0.75rem;" *ngIf="s.overdueInstallments">{{ s.overdueInstallments }} instalment(s)</div>
        </div>
        <div class="stat-tile"><div class="stat-label">Due this month</div><div class="stat-value">{{ s.dueThisMonth | number: '1.0-2' }}</div>
          <div class="text-muted" style="font-size:0.75rem;">Collected this month: {{ s.collectedThisMonth | number: '1.0-2' }}</div></div>
      </div>
    </ng-container>
  `,
})
export class InstallmentSummaryComponent implements OnInit {
  s: InstallmentSummary | null = null;

  constructor(private api: InstallmentApiService) {}

  ngOnInit(): void {
    this.api.summary().subscribe({ next: (s) => (this.s = s), error: () => (this.s = null) });
  }
}
