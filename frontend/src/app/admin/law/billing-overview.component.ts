import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { LawExportComponent } from './law-export.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { BILLING_LABEL, BillingOverviewRow, LawApiService } from '../../core/api/law-api.service';

/** What each matter has earned and still has to bill: approved time, costs and fixed-fee stages waiting, what is billed, paid and owed. */
@Component({
  selector: 'app-law-billing-overview',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, LawExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Billing overview</h3>
        <app-law-export kind="billing" [filters]="{ openOnly: openOnly ? 'true' : 'false' }"></app-law-export>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" [(ngModel)]="openOnly" name="oo" (ngModelChange)="load()" /> Open matters only</label>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="rows.length">
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">Ready to bill</div><strong>{{ total('unbilledTime') + total('unbilledCosts') + total('stagesPlanned') | number: '1.0-2' }}</strong>
          <div class="text-muted" style="font-size:0.75rem;">time {{ total('unbilledTime') | number: '1.0-2' }} · costs {{ total('unbilledCosts') | number: '1.0-2' }} · stages {{ total('stagesPlanned') | number: '1.0-2' }}</div></div>
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">Billed (before tax)</div><strong>{{ total('billed') | number: '1.0-2' }}</strong></div>
        <div class="card" style="padding:0.6rem 0.9rem;"><div class="text-muted" style="font-size:0.8rem;">Still owed by clients</div><strong>{{ total('outstanding') | number: '1.0-2' }}</strong></div>
      </div>
      <div class="card" style="padding:1rem; overflow-x:auto;">
        <table class="stack table-sm">
          <thead><tr><th>Matter</th><th>Client</th><th>Billing</th><th class="text-end">Hours</th><th class="text-end">Time to bill</th><th class="text-end">Costs to bill</th><th class="text-end">Stages to bill</th><th class="text-end">Billed</th><th class="text-end">Paid</th><th class="text-end">Owed</th><th class="text-end">Retainer left</th></tr></thead>
          <tbody>
            <tr *ngFor="let r of shown">
              <td data-label="Matter"><a [routerLink]="['/law/matters', r.matterId]">{{ r.number }}</a><div class="text-muted" style="font-size:0.8rem;">{{ r.restricted ? '(restricted)' : r.title }}</div></td>
              <td data-label="Client">{{ r.restricted ? '' : r.clientName }}</td><td data-label="Billing">{{ label(r.billingType) }}</td>
              <td class="text-end" data-label="Hours">{{ r.hoursLogged | number: '1.0-2' }}</td>
              <td class="text-end" data-label="Time to bill"><strong>{{ r.unbilledTime | number: '1.0-2' }}</strong></td><td class="text-end" data-label="Costs to bill">{{ r.unbilledCosts | number: '1.0-2' }}</td><td class="text-end" data-label="Stages to bill">{{ r.stagesPlanned | number: '1.0-2' }}</td>
              <td class="text-end" data-label="Billed">{{ r.billed | number: '1.0-2' }}</td><td class="text-end" data-label="Paid">{{ r.paid | number: '1.0-2' }}</td><td class="text-end" data-label="Owed">{{ r.outstanding | number: '1.0-2' }}</td>
              <td class="text-end" data-label="Retainer left">{{ r.retainerBalance ? (r.retainerBalance | number: '1.0-2') : '—' }}</td>
            </tr>
            <tr *ngIf="!rows.length && !error"><td colspan="11" class="text-muted">No matters yet.</td></tr>
          </tbody>
        </table>
        <app-pager [page]="page" [size]="size" [total]="rows.length" (pageChange)="goPage($event)"></app-pager>
        <div class="text-muted mt-2" style="font-size:0.8rem;">Open a matter and use its Billing tab to approve time, add costs and create the invoice. The same figures are in the Law Firm reports.</div>
      </div>
    </app-shell>
  `,
})
export class LawBillingOverviewComponent implements OnInit {
  rows: BillingOverviewRow[] = [];
  shown: BillingOverviewRow[] = [];
  page = 0;
  readonly size = PAGE_SIZE;
  openOnly = true;
  error: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.billingOverview(this.openOnly).subscribe({
      next: (r) => { this.rows = r; this.page = 0; this.slice(); this.error = null; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the billing overview'),
    });
  }

  goPage(p: number): void { this.page = p; this.slice(); }
  private slice(): void { this.shown = this.rows.slice(this.page * this.size, (this.page + 1) * this.size); }

  label(t: string): string { return (BILLING_LABEL as Record<string, string>)[t] ?? t; }
  total(k: 'unbilledTime' | 'unbilledCosts' | 'stagesPlanned' | 'billed' | 'outstanding'): number { return this.rows.reduce((s, r) => s + (r[k] ?? 0), 0); }
}
