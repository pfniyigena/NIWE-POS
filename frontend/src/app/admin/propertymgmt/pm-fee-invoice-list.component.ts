import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { FEE_INVOICE_STATUS_LABEL, FeeInvoiceRow, FeeInvoiceTotals, PmFeeInvoiceApiService, feeInvoiceBadge } from '../../core/api/pm-fee-invoice-api.service';
import { OwnerRow, PropertyMgmtApiService } from '../../core/api/propertymgmt-api.service';

/** Property Management > Fee invoices: the invoices sent to owners for the management fee, with what they add up to (for the VAT return). */
@Component({
  selector: 'app-pm-fee-invoice-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Fee invoices</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/reports/pm-fee-invoices">Report</a>
      </div>
      <div class="text-muted mb-3" style="font-size:0.9rem;">One invoice is made for the owner each time a statement is agreed. It is a document only: the fee and the VAT are booked by the statement itself.</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-3 mb-3" style="flex-wrap:wrap;" *ngIf="totals">
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">Invoices in force</div><div class="kpi">{{ totals.count }}</div></div>
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">Fees</div><div class="kpi">{{ totals.net | number: '1.0-0' }}</div></div>
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">VAT charged</div><div class="kpi">{{ totals.vat | number: '1.0-0' }}</div></div>
        <div class="card" style="padding:0.7rem 1rem;"><div class="text-muted kpi-label">Total invoiced</div><div class="kpi">{{ totals.total | number: '1.0-0' }}</div></div>
      </div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="ow" [(ngModel)]="ownerId" (ngModelChange)="load()">
          <option [ngValue]="null">Any owner</option><option *ngFor="let o of owners" [ngValue]="o.id">{{ o.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">Any status</option><option value="ISSUED">Issued</option><option value="VOID">Void</option>
        </select>
        <label class="text-muted" style="display:flex; gap:0.3rem; align-items:center;">From <input class="form-control" type="date" name="fr" [(ngModel)]="from" (ngModelChange)="load()" style="width:auto;" /></label>
        <label class="text-muted" style="display:flex; gap:0.3rem; align-items:center;">To <input class="form-control" type="date" name="to" [(ngModel)]="to" (ngModelChange)="load()" style="width:auto;" /></label>
        <input class="form-control" style="width:15rem;" placeholder="Search number, owner, statement" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Invoice</th><th>Date</th><th>Owner</th><th>Statement</th><th class="text-end">Fee</th><th class="text-end">VAT</th><th class="text-end">Total</th><th>Status</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of list" [style.opacity]="r.status === 'VOID' ? 0.6 : 1">
            <td data-label="Invoice"><strong>{{ r.number }}</strong></td>
            <td data-label="Date" style="white-space:nowrap;">{{ r.issueDate | date: 'd MMM y' }}</td>
            <td data-label="Owner"><a [routerLink]="['/admin/propertymgmt/owners', r.ownerId]">{{ r.ownerName }}</a></td>
            <td data-label="Statement"><a [routerLink]="['/admin/propertymgmt/statements', r.statementId]">{{ r.statementCode }}</a><div class="text-muted" style="font-size:0.75rem;">{{ r.periodFrom | date: 'd MMM' }} to {{ r.periodTo | date: 'd MMM y' }}</div></td>
            <td class="text-end" data-label="Fee">{{ r.netAmount | number: '1.0-0' }}</td>
            <td class="text-end" data-label="VAT">{{ r.vatAmount ? (r.vatAmount | number: '1.0-0') : '—' }}</td>
            <td class="text-end" data-label="Total"><strong>{{ r.totalAmount | number: '1.0-0' }}</strong></td>
            <td data-label="Status"><span class="badge" [ngClass]="badge(r.status)">{{ statusName(r.status) }}</span></td>
            <td data-label="" style="text-align:right;"><button class="btn btn-sm btn-outline-secondary" type="button" (click)="download(r)" [disabled]="busy">PDF</button></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">No invoices match. They appear when a statement with a management fee is agreed.</p>
    </app-shell>
  `,
})
export class PmFeeInvoiceListComponent implements OnInit {
  list: FeeInvoiceRow[] = [];
  totals: FeeInvoiceTotals | null = null;
  owners: OwnerRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  loaded = false;
  busy = false;
  q = '';
  status = '';
  from = '';
  to = '';
  ownerId: number | null = null;
  error: string | null = null;

  constructor(private api: PmFeeInvoiceApiService, private pm: PropertyMgmtApiService) {}

  ngOnInit(): void {
    this.pm.owners({}).subscribe({ next: (l) => (this.owners = l), error: () => undefined });
    this.load();
  }

  badge(s: string): string { return feeInvoiceBadge(s); }
  statusName(s: string): string { return (FEE_INVOICE_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    const f = { q: this.q.trim(), status: this.status, ownerId: this.ownerId, from: this.from, to: this.to };
    this.api.totals(f).subscribe({ next: (t) => (this.totals = t), error: () => undefined });
    this.api.list({ ...f, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the invoices'; },
    });
  }

  download(r: FeeInvoiceRow): void {
    this.busy = true;
    this.api.pdf(r.id, r.number).subscribe({ next: () => (this.busy = false), error: () => { this.busy = false; this.error = 'Could not make the file'; } });
  }
}
