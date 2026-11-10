import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { PriceListPayload, PriceListRow, PricingApiService } from '../../core/api/pricing-api.service';

/** Sales > Price lists. A price list is a set of prices a customer group buys at; the screen to name one, set its dates and across-the-board adjustment, and open it to set the prices. */
@Component({
  selector: 'app-price-lists',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin:0;">{{ 'Price lists' | translate }}</h3>
        <button class="btn btn-primary pl-new" *ngIf="canManage && !editing" (click)="startNew()">{{ 'New price list' | translate }}</button>
      </div>
      <p class="text-muted">{{ 'A customer in a customer group pays that group\\'s price list instead of the normal price. A list can set a price for individual products (even a lower price for bigger quantities), and move every other product up or down by a percentage.' | translate }}</p>
      <div class="alert alert-danger pl-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success pl-done" *ngIf="done">{{ done }}</div>

      <div class="card mb-3 pl-form" *ngIf="editing" style="padding:1rem;">
        <h4>{{ editingId ? ('Edit price list' | translate) : ('New price list' | translate) }}</h4>
        <div class="grid-2">
          <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control pl-name" name="name" [(ngModel)]="form.name" /></div>
          <div class="form-group"><label class="form-label">{{ 'Description (optional)' | translate }}</label><input class="form-control pl-desc" name="description" [(ngModel)]="description" /></div>
          <div class="form-group"><label class="form-label">{{ 'Adjust all other products by (%)' | translate }}</label>
            <input class="form-control pl-adjust" type="number" step="0.01" name="adjust" [(ngModel)]="form.adjustPercent" />
            <div class="text-muted" style="font-size:0.8rem;">{{ 'For products with no price of their own on this list. -10 means 10% below the normal price; 0 means the normal price.' | translate }}</div></div>
          <div class="form-group"><label class="form-label">{{ 'Valid from (optional)' | translate }}</label><input class="form-control pl-from" type="date" name="from" [(ngModel)]="validFrom" /></div>
          <div class="form-group"><label class="form-label">{{ 'Valid to (optional)' | translate }}</label><input class="form-control pl-to" type="date" name="to" [(ngModel)]="validTo" /></div>
          <div class="form-group"><label class="flex align-center gap-2" style="margin-top:1.6rem;"><input type="checkbox" class="pl-active" name="active" [(ngModel)]="form.active" /> {{ 'Switched on' | translate }}</label></div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary pl-save" (click)="save()">{{ 'Save' | translate }}</button>
          <button class="btn btn-link pl-cancel" (click)="cancel()">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <table>
        <thead><tr><th>{{ 'Name' | translate }}</th><th class="text-end">{{ 'Other products' | translate }}</th><th>{{ 'Valid' | translate }}</th><th class="text-end">{{ 'Prices' | translate }}</th><th class="text-end">{{ 'Groups' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let l of rows" class="pl-row" [attr.data-name]="l.name">
            <td data-label="Name"><a [routerLink]="['/admin/pricing/price-lists', l.id]"><strong>{{ l.name }}</strong></a>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="l.description">{{ l.description }}</div></td>
            <td data-label="Other products" class="text-end">{{ l.adjustPercent === 0 ? 'Normal price' : (l.adjustPercent > 0 ? '+' : '') + (l.adjustPercent | number: '1.0-2') + '%' }}</td>
            <td data-label="Valid">{{ l.validFrom || l.validTo ? (l.validFrom || 'always') + ' to ' + (l.validTo || 'no end') : 'Always' }}</td>
            <td data-label="Prices" class="text-end">{{ l.entries }}</td>
            <td data-label="Groups" class="text-end">{{ l.groups }}</td>
            <td data-label="Status"><span [ngClass]="l.current ? 'text-success' : 'text-muted'">{{ l.current ? 'In use' : (l.active ? 'Outside its dates' : 'Switched off') }}</span></td>
            <td data-label="" class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/pricing/price-lists', l.id]">{{ 'Prices' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary pl-edit" *ngIf="canManage" (click)="startEdit(l)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger pl-delete" *ngIf="canManage" (click)="remove(l)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="rows.length === 0"><td colspan="7" class="text-muted">{{ 'No price lists yet.' | translate }}</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class PriceListsComponent implements OnInit {
  rows: PriceListRow[] = [];
  error: string | null = null;
  done: string | null = null;
  editing = false;
  editingId: number | null = null;
  form = { name: '', adjustPercent: 0, active: true };
  description = '';
  validFrom = '';
  validTo = '';

  constructor(private api: PricingApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.lists().subscribe({
      next: (r) => (this.rows = r),
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the price lists'),
    });
  }

  startNew(): void {
    this.error = null;
    this.done = null;
    this.editing = true;
    this.editingId = null;
    this.form = { name: '', adjustPercent: 0, active: true };
    this.description = '';
    this.validFrom = '';
    this.validTo = '';
  }

  startEdit(l: PriceListRow): void {
    this.error = null;
    this.done = null;
    this.editing = true;
    this.editingId = l.id;
    this.form = { name: l.name, adjustPercent: l.adjustPercent, active: l.active };
    this.description = l.description ?? '';
    this.validFrom = l.validFrom ?? '';
    this.validTo = l.validTo ?? '';
  }

  cancel(): void {
    this.editing = false;
  }

  save(): void {
    this.error = null;
    const payload: PriceListPayload = {
      name: this.form.name,
      description: this.description || null,
      adjustPercent: Number(this.form.adjustPercent) || 0,
      validFrom: this.validFrom || null,
      validTo: this.validTo || null,
      active: this.form.active,
    };
    const call = this.editingId ? this.api.updateList(this.editingId, payload) : this.api.createList(payload);
    call.subscribe({
      next: (l) => {
        this.done = 'Saved "' + l.name + '".';
        this.editing = false;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not save the price list'),
    });
  }

  remove(l: PriceListRow): void {
    this.error = null;
    this.done = null;
    if (!confirm('Delete the price list "' + l.name + '" and its ' + l.entries + ' price(s)?')) return;
    this.api.deleteList(l.id).subscribe({
      next: () => {
        this.done = 'Deleted "' + l.name + '".';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not delete the price list'),
    });
  }
}
