import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ModuleApiService } from '../core/api/module-api.service';
import { ChartSetupApiService, ChartCountryOption } from '../core/api/chart-setup-api.service';

/**
 * Fourier: "The Config app.accounting.chart-country can be forgotten easily, what if we add this
 * on the screen then at first time a user select the country?" — the first-run, full-page setup
 * wizard an ADMIN is redirected to (see auth.guard.ts's adminGuard) the first time they log in on
 * a store with no chart of accounts seeded yet. No app-shell chrome, same convention as
 * LoginComponent/PurchaseOrderPrintComponent — this screen sits outside the normal admin
 * navigation entirely, by design: it's the one thing that has to happen before any of it.
 *
 * <p>This is a one-time choice (see AccountService.seedDefaultChartIfNeeded(ChartCountry)'s own
 * javadoc) — once submitted, the backend locks it into StoreSettings.chartCountry and rejects any
 * later attempt to call this same endpoint again, so there's no "change it later" control here.
 */
@Component({
  selector: 'app-chart-country-setup',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex-col align-center" style="min-height:100vh; justify-content:center; padding:1rem;">
      <div class="card" style="width:480px; padding:2rem;">
        <h3 class="mb-2" style="text-align:center;">Set up your chart of accounts</h3>
        <p class="text-muted mb-3" style="text-align:center;">
          Choose the country this store operates in. This sets up the starter chart of accounts
          your business will use — it's a one-time choice that can't be changed from this screen
          afterward, so pick the right one before continuing.
        </p>
        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <div class="flex-col gap-2 mb-3">
          <div
            class="card"
            style="cursor:pointer; padding:1rem; border-width:2px; border-style:solid;"
            [style.border-color]="selected === 'RWANDA' ? 'var(--accent)' : 'transparent'"
            (click)="selected = 'RWANDA'"
          >
            <strong>Rwanda</strong>
            <div class="text-muted" style="font-size:0.85rem; margin-top:0.25rem;">
              This app's own account numbering, extended with Rwanda's statutory accounts — VAT
              Payable (RRA), PAYE, RSSB contributions, CBHIS.
            </div>
          </div>
          <div
            class="card"
            style="cursor:pointer; padding:1rem; border-width:2px; border-style:solid;"
            [style.border-color]="selected === 'CHAD' ? 'var(--accent)' : 'transparent'"
            (click)="selected = 'CHAD'"
          >
            <strong>Chad</strong>
            <div class="text-muted" style="font-size:0.85rem; margin-top:0.25rem;">
              Real SYSCOHADA (OHADA) account codes — TVA facturée, IRPP, CNPS contributions.
            </div>
          </div>
        </div>
        <button class="btn btn-primary btn-block" [disabled]="!selected || saving" (click)="confirm()">
          {{ saving ? 'Setting up…' : 'Confirm and continue' }}
        </button>
      </div>
    </div>
  `,
})
export class ChartCountrySetupComponent {
  selected: ChartCountryOption | null = null;
  saving = false;
  error: string | null = null;

  constructor(private chartSetup: ChartSetupApiService, private router: Router, private auth: AuthService, private modules: ModuleApiService) {
    // Pre-select whatever app.accounting.chart-country already suggests — a sensible default to
    // land on, not a blank form, same courtesy as every other settings screen in this app. The
    // admin can still pick the other one before confirming.
    this.chartSetup.status().subscribe({
      next: (status) => {
        if (!status.needsSelection) {
          // Already set up by the time this screen loaded (e.g. a sale forced the lazy fallback
          // seed, or another tab already finished the wizard) — nothing left to do here.
          this.goHome();
          return;
        }
        this.selected = status.suggestedCountry;
      },
    });
  }

  confirm(): void {
    if (!this.selected) {
      return;
    }
    this.error = null;
    this.saving = true;
    this.chartSetup.select(this.selected).subscribe({
      next: () => {
        this.saving = false;
        this.goHome();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? 'Could not set up the chart of accounts';
      },
    });
  }

  /** Admin landing page: the POS registers when the POS module is on, otherwise the dashboard. */
  private goHome(): void {
    this.modules.whenLoaded().subscribe(() => this.router.navigate([this.auth.homePath(this.modules.on('POS'))]));
  }
}
