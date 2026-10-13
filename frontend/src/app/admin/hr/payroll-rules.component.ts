import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { HrApiService, HrSettings, PayrollRule, PayrollRulePayload, TaxBand } from '../../core/api/hr-api.service';

/** Payroll > Rules: the income-tax bands and the statutory contributions (employee deductions and employer costs). */
@Component({
  selector: 'app-payroll-rules',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Payroll rules</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/payroll/runs">Payroll runs</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ saved }}</div>
      <div class="alert alert-warning">
        The ready-made {{ settings?.payrollCountry === 'CHAD' ? 'Chad' : 'Rwanda' }} figures are a starting point, not legal advice. Confirm every rate, ceiling and tax band with your accountant
        ({{ settings?.payrollCountry === 'CHAD' ? 'DGI and CNPS' : 'RRA and RSSB' }}) before running a real payroll — the law changes.
      </div>
      <div class="alert alert-warning" *ngIf="!bands.length">
        No income-tax bands are set, so income tax (PAYE) will be 0 for everyone. Enter the bands below{{ settings?.payrollCountry === 'CHAD' ? ' (the Chad income-tax bands are not pre-filled)' : '' }}.
      </div>

      <h4 class="mb-2">Income tax bands</h4>
      <div class="card mb-3" style="padding:1rem;">
        <div class="flex gap-3 align-center mb-2" style="flex-wrap:wrap;">
          <label>The amounts below are
            <select class="form-select" style="width:auto; display:inline-block;" name="mode" [ngModel]="settings?.taxBandsAnnual ? 'Y' : 'M'" (ngModelChange)="setMode($event === 'Y')">
              <option value="M">per month</option><option value="Y">per year</option>
            </select>
          </label>
          <span class="text-muted" style="font-size:0.85rem;">Each band taxes the part of the taxable pay that falls inside it.</span>
        </div>
        <table class="table-sm">
          <thead><tr><th>From</th><th>To (empty = no limit)</th><th>Rate %</th><th></th></tr></thead>
          <tbody><tr *ngFor="let b of bands; let i = index">
            <td><input class="form-control" type="number" min="0" style="width:10rem;" [name]="'bf' + i" [(ngModel)]="b.fromAmount" /></td>
            <td><input class="form-control" type="number" min="0" style="width:10rem;" [name]="'bt' + i" [(ngModel)]="b.toAmount" /></td>
            <td><input class="form-control" type="number" min="0" max="100" step="0.01" style="width:6rem;" [name]="'br' + i" [(ngModel)]="b.ratePercent" /></td>
            <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="bands.splice(i, 1)">Remove</button></td>
          </tr></tbody>
        </table>
        <div class="flex gap-2 mt-2">
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="addBand()">+ Add band</button>
          <button class="btn btn-sm btn-primary" type="button" (click)="saveBands()">Save bands</button>
        </div>
      </div>

      <div class="flex justify-between align-center mb-2">
        <h4>Contributions</h4>
        <button class="btn btn-sm btn-outline-secondary" type="button" (click)="loadDefaults()">Add missing ready-made rules</button>
      </div>
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveRule()">
        <strong class="mb-2">{{ ruleId ? 'Edit rule' : 'New rule' }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">Name</label><input class="form-control" name="rn" [(ngModel)]="rule.name" required /></div>
          <div class="form-group"><label class="form-label">Paid by</label>
            <select class="form-select" name="rk" [(ngModel)]="rule.kind" (ngModelChange)="onKind()">
              <option value="EMPLOYEE_DEDUCTION">Employee (deducted from pay)</option><option value="EMPLOYER_CONTRIBUTION">Employer (extra cost)</option>
            </select></div>
          <div class="form-group"><label class="form-label">Rate %</label><input class="form-control" type="number" min="0" max="100" step="0.01" style="width:6rem;" name="rr" [(ngModel)]="rule.ratePercent" required /></div>
          <div class="form-group"><label class="form-label">Of</label>
            <select class="form-select" name="rb" [(ngModel)]="rule.base">
              <option value="GROSS">Gross pay</option><option value="BASIC">Basic salary</option><option value="NET">Net pay</option>
            </select></div>
          <div class="form-group"><label class="form-label">Monthly cap</label><input class="form-control" type="number" min="0" style="width:9rem;" name="rc" [(ngModel)]="rule.capAmount" placeholder="None" /></div>
          <div class="form-group"><label class="form-label">Owed to</label>
            <select class="form-select" name="rt" [(ngModel)]="rule.target">
              <option value="SOCIAL_SECURITY">Social security</option><option value="OTHER">Health / other</option>
            </select></div>
          <div class="form-group" *ngIf="rule.kind === 'EMPLOYEE_DEDUCTION' && rule.base !== 'NET'"><label class="form-label"><input type="checkbox" name="rd" [(ngModel)]="rule.deductibleFromTax" /> Reduces taxable pay</label></div>
          <div class="form-group"><label class="form-label"><input type="checkbox" name="ra" [(ngModel)]="rule.active" /> Active</label></div>
          <div class="flex gap-2"><button class="btn btn-primary" type="submit">{{ ruleId ? 'Save' : 'Add' }}</button><button class="btn btn-link" type="button" *ngIf="ruleId" (click)="resetRule()">Cancel</button></div>
        </div>
      </form>
      <table class="table-sm">
        <thead><tr><th>Rule</th><th>Paid by</th><th>Rate</th><th>Of</th><th>Cap</th><th>Owed to</th><th>Reduces tax</th><th>Status</th><th></th></tr></thead>
        <tbody><tr *ngFor="let r of rules">
          <td>{{ r.name }}</td><td>{{ r.kind === 'EMPLOYEE_DEDUCTION' ? 'Employee' : 'Employer' }}</td><td>{{ r.ratePercent }}%</td>
          <td>{{ r.base === 'GROSS' ? 'Gross' : r.base === 'BASIC' ? 'Basic' : 'Net' }}</td><td>{{ r.capAmount ? (r.capAmount | number: '1.0-2') : '—' }}</td>
          <td>{{ r.target === 'SOCIAL_SECURITY' ? 'Social security' : 'Health / other' }}</td><td>{{ r.deductibleFromTax ? 'Yes' : 'No' }}</td>
          <td><span class="badge" [class.badge-success]="r.active" [class.badge-secondary]="!r.active">{{ r.active ? 'Active' : 'Off' }}</span></td>
          <td class="flex gap-1">
            <button class="btn btn-sm btn-outline-primary" type="button" (click)="editRule(r)">Edit</button>
            <button class="btn btn-sm btn-outline-danger" type="button" (click)="delRule(r)">Delete</button>
          </td>
        </tr></tbody>
      </table>
    </app-shell>
  `,
})
export class PayrollRulesComponent implements OnInit {
  settings: HrSettings | null = null;
  rules: PayrollRule[] = [];
  bands: TaxBand[] = [];
  ruleId: number | null = null;
  rule: PayrollRulePayload = PayrollRulesComponent.blank();
  error: string | null = null;
  saved: string | null = null;

  constructor(private api: HrApiService) {}

  private static blank(): PayrollRulePayload {
    return { code: null, name: '', kind: 'EMPLOYEE_DEDUCTION', base: 'GROSS', ratePercent: 0, capAmount: null, deductibleFromTax: false, target: 'SOCIAL_SECURITY', active: true, sortOrder: null };
  }

  ngOnInit(): void {
    this.api.payrollSettings().subscribe({ next: (s) => (this.settings = s), error: () => {} });
    this.loadRules();
    this.api.taxBands().subscribe({ next: (b) => (this.bands = b), error: (e) => (this.error = e?.error?.message ?? 'Failed to load the tax bands') });
  }

  private flash(msg: string): void {
    this.saved = msg;
    setTimeout(() => (this.saved = null), 3000);
  }

  loadRules(): void {
    this.api.payrollRules().subscribe({ next: (r) => (this.rules = r), error: (e) => (this.error = e?.error?.message ?? 'Failed to load the rules') });
  }

  setMode(annual: boolean): void {
    this.api.setTaxMode(annual).subscribe({
      next: (s) => {
        this.settings = s;
        this.flash(annual ? 'Tax bands are now yearly amounts.' : 'Tax bands are now monthly amounts.');
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not change the mode'),
    });
  }

  addBand(): void {
    const last = this.bands[this.bands.length - 1];
    this.bands.push({ fromAmount: last?.toAmount ?? 0, toAmount: null, ratePercent: 0 });
  }

  saveBands(): void {
    this.error = null;
    const clean = this.bands.map((b) => ({ fromAmount: Number(b.fromAmount), toAmount: b.toAmount === null || (b.toAmount as unknown) === '' ? null : Number(b.toAmount), ratePercent: Number(b.ratePercent) }));
    this.api.saveTaxBands(clean).subscribe({
      next: (b) => {
        this.bands = b;
        this.flash('Tax bands saved.');
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the tax bands'),
    });
  }

  onKind(): void {
    if (this.rule.kind === 'EMPLOYER_CONTRIBUTION') this.rule.deductibleFromTax = false;
  }

  editRule(r: PayrollRule): void {
    this.ruleId = r.id;
    this.rule = { code: r.code, name: r.name, kind: r.kind, base: r.base, ratePercent: r.ratePercent, capAmount: r.capAmount, deductibleFromTax: r.deductibleFromTax, target: r.target, active: r.active, sortOrder: r.sortOrder };
  }

  resetRule(): void {
    this.ruleId = null;
    this.rule = PayrollRulesComponent.blank();
  }

  saveRule(): void {
    this.error = null;
    const payload = { ...this.rule, capAmount: this.rule.capAmount || null };
    const call = this.ruleId ? this.api.updatePayrollRule(this.ruleId, payload) : this.api.createPayrollRule(payload);
    call.subscribe({
      next: () => {
        this.resetRule();
        this.loadRules();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the rule'),
    });
  }

  delRule(r: PayrollRule): void {
    if (!confirm(`Delete rule ${r.name}? (Turn it off instead to keep it.)`)) return;
    this.api.deletePayrollRule(r.id).subscribe({ next: () => this.loadRules(), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the rule') });
  }

  loadDefaults(): void {
    this.api.loadPayrollDefaults().subscribe({
      next: (r) => {
        this.rules = r;
        this.api.taxBands().subscribe((b) => (this.bands = b));
        this.flash('Ready-made rules added.');
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the ready-made rules'),
    });
  }
}
