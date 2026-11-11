import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { TaxRateApiService } from '../core/api/tax-rate-api.service';
import { TaxRateOption } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Admin > Settings > Taxes (Task: "Add taxes menu under Settings Menu"). Replaces the old fixed
 * `app.pos.tax-rate` application property — each product now picks which rate applies to it (or
 * none, for tax-exempt goods) on the product's own edit screen, so any number of rates here can
 * be Active at once (mirrors Payment methods), not just one.
 */
@Component({
  selector: 'app-tax-rate-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Taxes' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/settings/taxes/new">{{ '+ New tax rate' | translate }}</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Rate' | translate }}</th>
            <th>{{ 'Pricing' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of taxRates">
            <td>{{ t.code }}</td>
            <td>{{ t.name }}</td>
            <td>{{ t.ratePercent | number: '1.0-2' }}%</td>
            <td>
              <span class="badge badge-secondary" *ngIf="t.inclusive">{{ 'Tax-inclusive' | translate }}</span>
              <span class="text-muted" *ngIf="!t.inclusive">{{ 'Tax added on top' | translate }}</span>
            </td>
            <td>
              <span class="badge badge-success" *ngIf="t.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!t.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/settings/taxes', t.id, 'edit']">{{ 'Edit' | translate }}</a></td>
          </tr>
          <tr *ngIf="taxRates.length === 0">
            <td colspan="6" class="text-muted">{{ 'No tax rates yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" style="margin-top:1rem;">
        {{ 'Each product picks its own tax rate (or none, for tax-exempt goods) from Admin > Inventory — any number of rates here can be Active at the same time.' | translate }}
      </p>
    </app-shell>
  `,
})
export class TaxRateListComponent implements OnInit {
  taxRates: TaxRateOption[] = [];
  error: string | null = null;

  constructor(private taxRateApi: TaxRateApiService) {}

  ngOnInit(): void {
    this.taxRateApi.listAll().subscribe({
      next: (t) => (this.taxRates = t),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load tax rates')),
    });
  }
}
