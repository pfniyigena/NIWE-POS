import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { GroupRow, PriceListRow, PricingApiService } from '../../core/api/pricing-api.service';
import { Customer } from '../../core/models';

/** Sales > Customer groups. A group (Wholesale, Staff, VIP) shares one price list; customers are put in a group here, or on the customer's own form. */
@Component({
  selector: 'app-customer-groups',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin:0;">{{ 'Customer groups' | translate }}</h3>
        <button class="btn btn-primary cg-new" *ngIf="canManage && !editing" (click)="startNew()">{{ 'New group' | translate }}</button>
      </div>
      <p class="text-muted">{{ 'Put customers in a group and give the group a' | translate }} <a routerLink="/admin/pricing/price-lists">{{ 'price list' | translate }}</a>{{ '. At the till, and on invoices, quotations and sales orders, those customers then get the list\\'s prices.' | translate }}</p>
      <div class="alert alert-danger cg-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success cg-done" *ngIf="done">{{ done }}</div>

      <div class="card mb-3 cg-form" *ngIf="editing" style="padding:1rem;">
        <h4>{{ editingId ? ('Edit group' | translate) : ('New group' | translate) }}</h4>
        <div class="grid-2">
          <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control cg-name" name="name" [(ngModel)]="name" /></div>
          <div class="form-group"><label class="form-label">{{ 'Description (optional)' | translate }}</label><input class="form-control cg-desc" name="description" [(ngModel)]="description" /></div>
          <div class="form-group"><label class="form-label">{{ 'Price list' | translate }}</label>
            <select class="form-select cg-list" name="priceListId" [(ngModel)]="priceListId">
              <option [ngValue]="null">{{ 'None (normal prices)' | translate }}</option>
              <option *ngFor="let l of lists" [ngValue]="l.id">{{ l.name }}{{ l.current ? '' : ' (not in use)' }}</option>
            </select></div>
          <div class="form-group"><label class="flex align-center gap-2" style="margin-top:1.6rem;"><input type="checkbox" class="cg-active" name="active" [(ngModel)]="active" /> {{ 'Switched on' | translate }}</label></div>
        </div>
        <div class="flex gap-2"><button class="btn btn-primary cg-save" (click)="save()">{{ 'Save' | translate }}</button><button class="btn btn-link cg-cancel" (click)="editing = false">{{ 'Cancel' | translate }}</button></div>
      </div>

      <table>
        <thead><tr><th>{{ 'Group' | translate }}</th><th>{{ 'Price list' | translate }}</th><th class="text-end">{{ 'Customers' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let g of rows" class="cg-row" [attr.data-name]="g.name">
            <td data-label="Group"><strong>{{ g.name }}</strong><div class="text-muted" style="font-size:0.8rem;" *ngIf="g.description">{{ g.description }}</div></td>
            <td data-label="Price list">{{ g.priceListName || 'Normal prices' }}</td>
            <td data-label="Customers" class="text-end">{{ g.customers }}</td>
            <td data-label="Status">{{ g.active ? ('On' | translate) : ('Off' | translate) }}</td>
            <td data-label="" class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary cg-members" *ngIf="canManage" (click)="openMembers(g)">{{ 'Add customers' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary cg-edit" *ngIf="canManage" (click)="startEdit(g)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger cg-delete" *ngIf="canManage" (click)="remove(g)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="rows.length === 0"><td colspan="5" class="text-muted">{{ 'No customer groups yet.' | translate }}</td></tr>
        </tbody>
      </table>

      <div class="card cg-assign" *ngIf="assignGroup" style="padding:1rem; margin-top:1rem;">
        <h4>{{ 'Add customers to {p0}' | translate: { p0: (assignGroup.name) } }}</h4>
        <input class="form-control cg-assign-search" style="max-width:20rem;" [placeholder]="'Search customers' | translate" [(ngModel)]="customerSearch" name="customerSearch" (input)="filterCustomers()" />
        <div style="max-height:16rem; overflow:auto; margin-top:0.5rem;">
          <label class="flex align-center gap-2 cg-assign-row" *ngFor="let c of shownCustomers" style="padding:0.2rem 0;">
            <input type="checkbox" class="cg-assign-tick" [checked]="isTicked(c.id)" (change)="toggle(c.id)" />
            {{ c.name }} <span class="text-muted">{{ c.phone }}</span>
            <span class="text-muted" *ngIf="c.customerGroupId && c.customerGroupId !== assignGroup.id" style="font-size:0.8rem;">{{ '(now in {p0})' | translate: { p0: (groupName(c.customerGroupId)) } }}</span>
          </label>
          <div class="text-muted" *ngIf="shownCustomers.length === 0">{{ 'No customers match.' | translate }}</div>
        </div>
        <div class="flex gap-2" style="margin-top:0.5rem;">
          <button class="btn btn-primary cg-assign-save" [disabled]="ticked.length === 0" (click)="assign()">{{ 'Put {p0} customer(s) in this group' | translate: { p0: (ticked.length) } }}</button>
          <button class="btn btn-link cg-assign-cancel" (click)="assignGroup = null">{{ 'Close' | translate }}</button>
        </div>
      </div>
    </app-shell>
  `,
})
export class CustomerGroupsComponent implements OnInit {
  rows: GroupRow[] = [];
  lists: PriceListRow[] = [];
  error: string | null = null;
  done: string | null = null;
  editing = false;
  editingId: number | null = null;
  name = '';
  description = '';
  priceListId: number | null = null;
  active = true;

  assignGroup: GroupRow | null = null;
  customers: Customer[] = [];
  shownCustomers: Customer[] = [];
  customerSearch = '';
  ticked: number[] = [];

  constructor(private api: PricingApiService, private customerApi: CustomerApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    this.load();
    this.api.lists().subscribe({ next: (l) => (this.lists = l), error: () => (this.lists = []) });
  }

  load(): void {
    this.api.groups().subscribe({ next: (g) => (this.rows = g), error: (err) => (this.error = err?.error?.message ?? 'Could not load the groups') });
  }

  groupName(id: number): string {
    return this.rows.find((g) => g.id === id)?.name ?? 'another group';
  }

  startNew(): void {
    this.error = null;
    this.done = null;
    this.editing = true;
    this.editingId = null;
    this.name = '';
    this.description = '';
    this.priceListId = null;
    this.active = true;
  }

  startEdit(g: GroupRow): void {
    this.error = null;
    this.done = null;
    this.editing = true;
    this.editingId = g.id;
    this.name = g.name;
    this.description = g.description ?? '';
    this.priceListId = g.priceListId;
    this.active = g.active;
  }

  save(): void {
    this.error = null;
    const payload = { name: this.name, description: this.description || null, priceListId: this.priceListId, active: this.active };
    const call = this.editingId ? this.api.updateGroup(this.editingId, payload) : this.api.createGroup(payload);
    call.subscribe({
      next: (g) => {
        this.done = 'Saved "' + g.name + '".';
        this.editing = false;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not save the group'),
    });
  }

  remove(g: GroupRow): void {
    this.error = null;
    this.done = null;
    if (!confirm('Delete the group "' + g.name + '"?')) return;
    this.api.deleteGroup(g.id).subscribe({
      next: () => {
        this.done = 'Deleted "' + g.name + '".';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not delete the group'),
    });
  }

  openMembers(g: GroupRow): void {
    this.assignGroup = g;
    this.ticked = [];
    this.customerSearch = '';
    this.customerApi.list().subscribe({
      next: (c) => {
        this.customers = c.filter((x) => !x.walkIn);
        this.filterCustomers();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the customers'),
    });
  }

  filterCustomers(): void {
    const q = this.customerSearch.trim().toLowerCase();
    this.shownCustomers = (q ? this.customers.filter((c) => c.name.toLowerCase().includes(q) || (c.phone ?? '').includes(q)) : this.customers).slice(0, 200);
  }

  isTicked(id: number): boolean {
    return this.ticked.includes(id);
  }

  toggle(id: number): void {
    this.ticked = this.ticked.includes(id) ? this.ticked.filter((x) => x !== id) : [...this.ticked, id];
  }

  assign(): void {
    if (!this.assignGroup) return;
    const g = this.assignGroup;
    this.api.assign(this.ticked, g.id).subscribe({
      next: (r) => {
        this.done = r.changed + ' customer(s) put in "' + g.name + '".';
        this.assignGroup = null;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not change the customers'),
    });
  }
}
