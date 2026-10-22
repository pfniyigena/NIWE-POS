import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { PmSettings, PmSettingsPayload, PropertyMgmtApiService } from '../../core/api/propertymgmt-api.service';
import { AutoRunResult, PmStatementApiService } from '../../core/api/pm-statement-api.service';
import { PmReminderSettingsComponent } from './pm-reminder-settings.component';

/** Property Management > Settings (admin): areas, when a lease counts as ending soon, the usual rent due day, and the usual management fee. */
@Component({
  selector: 'app-pm-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, PmReminderSettingsComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Property Management settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <form class="card" style="padding:1rem; max-width:40rem;" (ngSubmit)="save()">
        <div class="form-group mb-2"><label class="form-label">Areas you work in <span class="text-muted">(one per line)</span></label>
          <textarea class="form-control" rows="6" name="areas" [(ngModel)]="areas" placeholder="Kololo&#10;Nakasero&#10;Ntinda"></textarea>
          <div class="text-muted" style="font-size:0.85rem;">They appear as suggestions when you add a property.</div></div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">A lease is "ending soon" for the last (days)</label><input class="form-control" style="max-width:9rem;" type="number" min="1" max="365" name="ew" [(ngModel)]="expiryWarningDays" /></div>
          <div class="form-group mb-2"><label class="form-label">Rent usually falls due on day</label><input class="form-control" style="max-width:9rem;" type="number" min="1" max="28" name="dd" [(ngModel)]="defaultDueDay" /></div>
          <div class="form-group mb-2"><label class="form-label">Usual management fee (%)</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="100" step="0.01" name="df" [(ngModel)]="defaultFee" placeholder="none" />
            <div class="text-muted" style="font-size:0.85rem;">Used when neither the property nor its owner sets one.</div></div>
          <div class="form-group mb-2"><label class="form-label">VAT on the management fee (%)</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="100" step="0.01" name="fv" [(ngModel)]="feeVat" placeholder="none" />
            <div class="text-muted" style="font-size:0.85rem;">Charged on top of the fee and taken from the owner. Leave empty if you do not charge VAT. A draft statement shows a warning when this changes, and rebuilding it applies the new rate.</div></div>
        </div>
        <h4 class="mb-2 mt-2">Rent invoicing</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">Invoice rent this many days before it falls due</label><input class="form-control" style="max-width:9rem;" type="number" min="0" max="60" name="ia" [(ngModel)]="invoiceAheadDays" /></div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="ai" [(ngModel)]="autoInvoice" /> Invoice rent automatically every morning</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">When this is off, press <em>Invoice the rent that is ready</em> on the Rent page. Either way each invoice goes to the tenant as a customer and is paid in Invoices as usual.</div>
        <h4 class="mb-2 mt-3">Owner statements every month</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="as" [(ngModel)]="autoStatements" /> Make last month's statements automatically</label>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">From this day of the month</label><input class="form-control" style="max-width:9rem;" type="number" min="1" max="28" name="ad" [(ngModel)]="autoStatementsDay" /></div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="asd" [(ngModel)]="autoSendStatements" [disabled]="!autoStatements" /> Also agree them and e-mail the owners</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">Every morning the system checks whether it is time. It makes a draft statement for every owner who had rent or repairs last month. With the second box on, a statement is agreed (and booked to the accounts) and e-mailed only when it has no warning and the owner would be paid something; every other statement stays a draft for you to look at. {{ through ? 'Last made for the month ending ' + through + '.' : 'Not made yet.' }}</div>
        <div class="mb-3">
          <button class="btn btn-outline-secondary" type="button" (click)="runNow()" [disabled]="running || saving">{{ running ? 'Working…' : 'Make the statements for last month now' }}</button>
          <span class="text-muted" style="font-size:0.85rem;"> Uses the switches above as saved, so save first.</span>
        </div>
        <div class="alert alert-success" *ngIf="ranResult">{{ ranResult.message }}<div *ngFor="let n of ranResult.notes" style="font-size:0.9rem;">{{ n }}</div></div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : 'Save settings' }}</button></div>
      </form>
      <app-pm-reminder-settings></app-pm-reminder-settings>
    </app-shell>
  `,
})
export class PmSettingsComponent implements OnInit {
  areas = '';
  expiryWarningDays = 60;
  defaultDueDay = 1;
  defaultFee: number | null = null;
  feeVat: number | null = null;
  autoStatements = false;
  autoStatementsDay = 5;
  autoSendStatements = false;
  through: string | null = null;
  running = false;
  ranResult: AutoRunResult | null = null;
  hadFeeVat = false;
  invoiceAheadDays = 7;
  autoInvoice = false;
  hadFee = false;
  saving = false;
  saved = false;
  error: string | null = null;

  constructor(private api: PropertyMgmtApiService, private statementApi: PmStatementApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => this.fill(s), error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
  }

  private fill(s: PmSettings): void {
    this.areas = s.areas.join('\n');
    this.expiryWarningDays = s.expiryWarningDays;
    this.defaultDueDay = s.defaultDueDay;
    this.defaultFee = s.defaultFeePercent;
    this.feeVat = s.feeVatPercent;
    this.autoStatements = s.autoStatements;
    this.autoStatementsDay = s.autoStatementsDay;
    this.autoSendStatements = s.autoSendStatements;
    this.through = s.autoStatementsThrough;
    this.hadFeeVat = s.feeVatPercent != null;
    this.invoiceAheadDays = s.invoiceAheadDays;
    this.autoInvoice = s.autoInvoice;
    this.hadFee = s.defaultFeePercent != null;
  }

  runNow(): void {
    this.running = true;
    this.error = null;
    this.ranResult = null;
    this.statementApi.autoRun(true).subscribe({
      next: (r) => { this.running = false; this.ranResult = r; this.through = r.periodTo; },
      error: (e) => { this.running = false; this.error = e?.error?.message ?? 'Could not make the statements'; },
    });
  }

  save(): void {
    this.saving = true;
    this.saved = false;
    this.error = null;
    const hasFee = this.defaultFee != null && String(this.defaultFee) !== '';
    const p: PmSettingsPayload = { areas: this.areas.split('\n').map((a) => a.trim()).filter((a) => a).join(', '), expiryWarningDays: this.expiryWarningDays, defaultDueDay: this.defaultDueDay, invoiceAheadDays: Number(this.invoiceAheadDays), autoInvoice: this.autoInvoice };
    p.autoStatements = this.autoStatements;
    p.autoStatementsDay = Number(this.autoStatementsDay);
    p.autoSendStatements = this.autoStatements && this.autoSendStatements;
    if (hasFee) p.defaultFeePercent = Number(this.defaultFee);
    else if (this.hadFee) p.clearFee = true;
    if (this.feeVat != null && String(this.feeVat) !== '') p.feeVatPercent = Number(this.feeVat);
    else if (this.hadFeeVat) p.clearFeeVat = true;
    this.api.saveSettings(p).subscribe({
      next: (s) => { this.saving = false; this.saved = true; this.fill(s); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the settings'; },
    });
  }
}
