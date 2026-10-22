import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { Arrears, PropertyMgmtApiService, PropertyRow } from '../../core/api/propertymgmt-api.service';

/** Property Management > Arrears: tenants with rent past its due date, how old it is (1 to 30, 31 to 60, 61 to 90, over 90 days), and the invoices behind it. */
@Component({
  selector: 'app-pm-arrears',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Arrears</h3>
        <div class="flex gap-2 align-center">
          <select class="form-select" style="width:auto;" name="pr" [(ngModel)]="propertyId" (ngModelChange)="load()">
            <option [ngValue]="null">Any property</option><option *ngFor="let p of properties" [ngValue]="p.id">{{ p.name }}</option>
          </select>
          <a class="btn btn-outline-secondary" routerLink="/admin/propertymgmt/rent">Rent</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!a && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="a">
        <div style="display:grid; grid-template-columns:repeat(auto-fill, minmax(10rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Owed past due</div><div class="kpi" [style.color]="a.total ? '#b91c1c' : ''">{{ a.total | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ a.tenants }} tenant{{ a.tenants === 1 ? '' : 's' }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">1 to 30 days</div><div class="kpi">{{ a.days30 | number: '1.0-0' }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">31 to 60 days</div><div class="kpi">{{ a.days60 | number: '1.0-0' }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">61 to 90 days</div><div class="kpi">{{ a.days90 | number: '1.0-0' }}</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Over 90 days</div><div class="kpi" [style.color]="a.days90Plus ? '#b91c1c' : ''">{{ a.days90Plus | number: '1.0-0' }}</div></div>
        </div>
        <table class="stack table-sm" *ngIf="a.rows.length">
          <thead><tr><th>Tenant</th><th>Phone</th><th class="text-end">Invoices</th><th class="text-end">1–30</th><th class="text-end">31–60</th><th class="text-end">61–90</th><th class="text-end">90+</th><th class="text-end">Owed</th><th>Oldest</th><th></th></tr></thead>
          <tbody>
            <ng-container *ngFor="let r of a.rows">
              <tr>
                <td data-label="Tenant"><a [routerLink]="['/admin/propertymgmt/tenants', r.tenantId]"><strong>{{ r.tenantName }}</strong></a></td>
                <td data-label="Phone">{{ r.phone || '—' }}</td>
                <td class="text-end" data-label="Invoices">{{ r.charges }}</td>
                <td class="text-end" data-label="1–30">{{ r.days30 | number: '1.0-0' }}</td><td class="text-end" data-label="31–60">{{ r.days60 | number: '1.0-0' }}</td>
                <td class="text-end" data-label="61–90">{{ r.days90 | number: '1.0-0' }}</td><td class="text-end" data-label="90+">{{ r.days90Plus | number: '1.0-0' }}</td>
                <td class="text-end" data-label="Owed"><strong>{{ r.outstanding | number: '1.0-0' }}</strong></td>
                <td style="white-space:nowrap;" data-label="Oldest">{{ r.oldestDue | date: 'd MMM y' }}<div class="text-muted" style="font-size:0.75rem;">{{ r.oldestDays }} days late</div></td>
                <td style="text-align:right;" data-label=""><button class="btn btn-sm btn-outline-secondary" type="button" (click)="toggle(r.tenantId)">{{ open === r.tenantId ? 'Hide' : 'Invoices' }}</button></td>
              </tr>
              <tr *ngIf="open === r.tenantId">
                <td colspan="10">
                  <table class="table-sm">
                    <thead><tr><th>Unit</th><th>Period</th><th>Due</th><th>Invoice</th><th class="text-end">Invoiced</th><th class="text-end">Paid</th><th class="text-end">Owed</th><th>Late</th><th></th></tr></thead>
                    <tbody><tr *ngFor="let c of r.items">
                      <td data-label="Tenant">{{ c.propertyName }} · {{ c.unitNo }}</td>
                      <td style="white-space:nowrap;" data-label="Phone">{{ c.periodStart | date: 'd MMM y' }} – {{ c.periodEnd | date: 'd MMM y' }}</td>
                      <td style="white-space:nowrap;" data-label="Invoices">{{ c.dueDate | date: 'd MMM y' }}</td>
                      <td data-label="1–30"><a *ngIf="c.invoiceId" [routerLink]="['/admin/invoices', c.invoiceId]">{{ c.invoiceNumber }}</a></td>
                      <td class="text-end" data-label="31–60">{{ c.billed | number: '1.0-0' }}</td><td class="text-end" data-label="61–90">{{ c.paid | number: '1.0-0' }}</td><td class="text-end" data-label="90+">{{ c.outstanding | number: '1.0-0' }}</td>
                      <td data-label="Owed">{{ c.daysOverdue }} days</td>
                      <td style="text-align:right;" data-label="Oldest"><a class="btn btn-sm btn-outline-primary" *ngIf="c.invoiceId" [routerLink]="['/admin/invoices', c.invoiceId, 'record-payment']">Record payment</a></td>
                    </tr></tbody>
                  </table>
                </td>
              </tr>
            </ng-container>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="!a.rows.length">No rent is past its due date.</p>
      </ng-container>
    </app-shell>
  `,
})
export class PmArrearsComponent implements OnInit {
  a: Arrears | null = null;
  properties: PropertyRow[] = [];
  propertyId: number | null = null;
  open: number | null = null;
  error: string | null = null;

  constructor(private api: PropertyMgmtApiService) {}

  ngOnInit(): void {
    this.api.properties({ activeOnly: true }).subscribe({ next: (p) => (this.properties = p), error: () => undefined });
    this.load();
  }

  load(): void {
    this.api.arrears({ propertyId: this.propertyId }).subscribe({ next: (a) => (this.a = a), error: (e) => (this.error = e?.error?.message ?? 'Could not load the arrears') });
  }

  toggle(id: number): void { this.open = this.open === id ? null : id; }
}
