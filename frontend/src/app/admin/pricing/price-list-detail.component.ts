import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { downloadBlob } from '../../core/download-file';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { ImportResult } from '../../core/api/data-import-export-api.service';
import { PAGE_SIZE } from '../../core/api/paged';
import { PriceEntry, PriceListRow, PricingApiService } from '../../core/api/pricing-api.service';
import { Product } from '../../core/models';

/** One price list: its prices (a product can have several quantity steps), adding and removing them, and loading or saving the whole list as a CSV file. */
@Component({
  selector: 'app-price-list-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="mb-2"><a routerLink="/admin/pricing/price-lists">&larr; All price lists</a></div>
      <ng-container *ngIf="list">
        <h3 class="pl-title">{{ list.name }}</h3>
        <p class="text-muted">
          {{ 'Products below use their own price here. Every other product is {p0}.' | translate: { p0: (list.adjustPercent === 0 ? 'sold at the normal price' : 'moved ' + (list.adjustPercent > 0 ? 'up' : 'down') + ' by ' + (list.adjustPercent < 0 ? -list.adjustPercent : list.adjustPercent) + '%') } }}
          <span *ngIf="!list.current"> <strong>{{ 'This list is not in use right now' | translate }}</strong> ({{ list.active ? 'outside its dates' : 'switched off' }}).</span>
        </p>
      </ng-container>
      <div class="alert alert-danger pl-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success pl-done" *ngIf="done">{{ done }}</div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="canManage">
        <h4>{{ 'Add a price' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="margin:0; min-width:16rem; position:relative;">
            <label class="form-label">{{ 'Product' | translate }}</label>
            <input class="form-control pl-search-product" [placeholder]="'Type a name or SKU' | translate" [(ngModel)]="productSearch" name="productSearch" (input)="searchProducts()" autocomplete="off" />
            <div class="pl-picks" *ngIf="picks.length > 0" style="position:absolute; z-index:5; background:var(--surface); border:1px solid var(--border); width:100%; max-height:14rem; overflow:auto;">
              <button type="button" class="btn btn-link pl-pick" *ngFor="let p of picks" style="display:block; width:100%; text-align:left;" (click)="choose(p)">
                {{ p.name }} <span class="text-muted">{{ p.sku }} - {{ p.unitPrice | number: '1.2-2' }}</span>
              </button>
            </div>
          </div>
          <div class="form-group" style="margin:0;"><label class="form-label">{{ 'From quantity' | translate }}</label><input class="form-control pl-min" type="number" min="0.0001" step="any" style="width:6rem;" [(ngModel)]="minQuantity" name="minQuantity" /></div>
          <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Price' | translate }}</label><input class="form-control pl-price" type="number" min="0" step="0.01" style="width:8rem;" [(ngModel)]="price" name="price" /></div>
          <button class="btn btn-primary pl-add" [disabled]="!chosen || price === null" (click)="add()">{{ 'Save price' | translate }}</button>
        </div>
        <div class="text-muted pl-chosen" *ngIf="chosen" style="margin-top:0.4rem; font-size:0.85rem;">{{ 'Selected: {p0} (normal price {p1}). Saving a price for a quantity already on the list changes it.' | translate: { p0: (chosen.name), p1: (chosen.unitPrice | number: '1.2-2') } }}</div>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <input class="form-control pl-filter" style="max-width:18rem;" [placeholder]="'Search this list' | translate" [(ngModel)]="filter" name="filter" (input)="reload(true)" />
        <button class="btn btn-outline-secondary btn-sm pl-export" (click)="exportList()">{{ 'Download as CSV' | translate }}</button>
      </div>
      <table>
        <thead><tr><th>{{ 'Product' | translate }}</th><th>{{ 'SKU' | translate }}</th><th class="text-end">{{ 'Normal price' | translate }}</th><th class="text-end">{{ 'From qty' | translate }}</th><th class="text-end">{{ 'Price' | translate }}</th><th class="text-end">{{ 'Difference' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let e of entries" class="pl-entry" [attr.data-sku]="e.sku">
            <td data-label="Product">{{ e.productName }}</td>
            <td data-label="SKU">{{ e.sku }}</td>
            <td data-label="Normal price" class="text-end">{{ e.basePrice | number: '1.2-2' }}</td>
            <td data-label="From qty" class="text-end">{{ e.minQuantity }}</td>
            <td data-label="Price" class="text-end"><strong>{{ e.price | number: '1.2-2' }}</strong></td>
            <td data-label="Difference" class="text-end">{{ diff(e) }}</td>
            <td data-label=""><button class="btn btn-sm btn-outline-danger pl-remove" *ngIf="canManage" (click)="remove(e)">{{ 'Remove' | translate }}</button></td>
          </tr>
          <tr *ngIf="entries.length === 0"><td colspan="7" class="text-muted">{{ 'No prices on this list{p0}.' | translate: { p0: (filter ? ' match your search' : ' yet') } }}</td></tr>
        </tbody>
      </table>
      <div class="flex gap-2 align-center" style="margin-top:0.5rem;">
        <button class="btn btn-sm btn-outline-secondary pl-prev" [disabled]="page === 0" (click)="go(page - 1)">{{ 'Previous' | translate }}</button>
        <span class="text-muted pl-pageinfo">{{ '{p0}-{p1} of {p2}' | translate: { p0: (total === 0 ? 0 : page * pageSize + 1), p1: (min(total, (page + 1) * pageSize)), p2: (total) } }}</span>
        <button class="btn btn-sm btn-outline-secondary pl-next" [disabled]="(page + 1) * pageSize >= total" (click)="go(page + 1)">{{ 'Next' | translate }}</button>
      </div>

      <div class="card" style="padding:1rem; margin-top:1.5rem;" *ngIf="canManage">
        <h4>{{ 'Load prices from a file' | translate }}</h4>
        <p class="text-muted" style="font-size:0.85rem;">{{ 'A CSV with the columns sku, minQuantity (optional, 1 if left out) and price. A price already on the list for that product and quantity is changed; nothing is removed. Check the file first: nothing is saved until you confirm.' | translate }}</p>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <button class="btn btn-sm btn-outline-secondary pl-template" (click)="downloadTemplate()">{{ 'Download template' | translate }}</button>
          <input type="file" accept=".csv" class="pl-file" id="pl-file" (change)="onFile($event)" />
          <button class="btn btn-outline-primary pl-check" [disabled]="!file || working" (click)="check()">{{ working ? ('Working…' | translate) : ('Check file' | translate) }}</button>
        </div>
        <div class="alert alert-danger pl-import-error" *ngIf="importError" style="margin-top:0.5rem;">{{ importError }}</div>
        <div *ngIf="result as res" class="pl-result" style="margin-top:0.5rem;">
          <div class="alert alert-info" *ngIf="res.dryRun"><strong>{{ 'Nothing saved yet.' | translate }}</strong> {{ '{p0} row(s) read: {p1} can be loaded, {p2} will be skipped.' | translate: { p0: (res.total), p1: (res.imported), p2: (res.skipped) } }}</div>
          <div class="alert alert-success" *ngIf="!res.dryRun">{{ 'Loaded {p0}, skipped {p1}.' | translate: { p0: (res.imported), p1: (res.skipped) } }}</div>
          <div *ngFor="let n of res.notes" class="text-muted" style="font-size:0.85rem;">{{ n }}</div>
          <table *ngIf="res.skippedRows.length > 0">
            <thead><tr><th>{{ 'Row' | translate }}</th><th>{{ 'Reason' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let r of res.skippedRows"><td data-label="Row">{{ r.rowNumber }}</td><td data-label="Reason">{{ r.reason }}</td></tr></tbody>
          </table>
          <div class="flex gap-2" style="margin-top:0.5rem;" *ngIf="res.dryRun">
            <button class="btn btn-primary pl-confirm" [disabled]="res.imported === 0 || working" (click)="confirm()">{{ 'Load {p0} price(s)' | translate: { p0: (res.imported) } }}</button>
            <button class="btn btn-outline-secondary pl-import-cancel" (click)="clear()">{{ 'Cancel' | translate }}</button>
          </div>
        </div>
      </div>
    </app-shell>
  `,
})
export class PriceListDetailComponent implements OnInit {
  id = 0;
  list: PriceListRow | null = null;
  entries: PriceEntry[] = [];
  total = 0;
  page = 0;
  pageSize = PAGE_SIZE;
  filter = '';
  error: string | null = null;
  done: string | null = null;

  productSearch = '';
  picks: Product[] = [];
  chosen: Product | null = null;
  minQuantity = 1;
  price: number | null = null;

  file: File | null = null;
  working = false;
  importError: string | null = null;
  result: ImportResult | null = null;

  constructor(private route: ActivatedRoute, private api: PricingApiService, private catalog: CatalogApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.list(this.id).subscribe({ next: (l) => (this.list = l), error: (err) => (this.error = err?.error?.message ?? 'Could not load the price list') });
    this.reload(false);
  }

  min(a: number, b: number): number {
    return Math.min(a, b);
  }

  diff(e: PriceEntry): string {
    if (!e.basePrice) return '';
    const pct = ((e.price - e.basePrice) / e.basePrice) * 100;
    if (Math.abs(pct) < 0.005) return 'same';
    return (pct > 0 ? '+' : '') + pct.toFixed(1) + '%';
  }

  reload(resetPage: boolean): void {
    if (resetPage) this.page = 0;
    this.api.entries(this.id, this.filter.trim(), this.page, this.pageSize).subscribe({
      next: (r) => {
        this.entries = r.items;
        this.total = r.total;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the prices'),
    });
  }

  go(p: number): void {
    this.page = Math.max(0, p);
    this.reload(false);
  }

  searchProducts(): void {
    this.chosen = null;
    const q = this.productSearch.trim();
    if (q.length < 2) {
      this.picks = [];
      return;
    }
    this.catalog.listAllProducts(q, 0, 8).subscribe({ next: (r) => (this.picks = r.content), error: () => (this.picks = []) });
  }

  choose(p: Product): void {
    this.chosen = p;
    this.productSearch = p.name;
    this.picks = [];
    if (this.price === null) this.price = p.unitPrice;
  }

  add(): void {
    this.error = null;
    this.done = null;
    if (!this.chosen || this.price === null) return;
    const name = this.chosen.name;
    this.api.saveEntry(this.id, this.chosen.id, Number(this.minQuantity) || 1, Number(this.price)).subscribe({
      next: () => {
        this.done = 'Saved a price for ' + name + '.';
        this.chosen = null;
        this.productSearch = '';
        this.price = null;
        this.minQuantity = 1;
        this.reload(false);
        this.api.list(this.id).subscribe((l) => (this.list = l));
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not save the price'),
    });
  }

  remove(e: PriceEntry): void {
    this.error = null;
    this.done = null;
    this.api.deleteEntry(this.id, e.id).subscribe({
      next: () => {
        this.done = 'Removed the price for ' + e.productName + '.';
        this.reload(false);
        this.api.list(this.id).subscribe((l) => (this.list = l));
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not remove the price'),
    });
  }

  exportList(): void {
    this.api.exportList(this.id).subscribe((b) => downloadBlob(b, 'price-list-' + (this.list?.name ?? this.id) + '.csv'));
  }

  downloadTemplate(): void {
    this.api.template().subscribe((b) => downloadBlob(b, 'price-list-import-template.csv'));
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    this.file = input.files && input.files.length > 0 ? input.files[0] : null;
    this.result = null;
    this.importError = null;
  }

  check(): void {
    if (!this.file) return;
    this.run(true);
  }

  confirm(): void {
    if (!this.file) return;
    this.run(false);
  }

  private run(dry: boolean): void {
    this.working = true;
    this.importError = null;
    this.api.importEntries(this.id, this.file as File, dry).subscribe({
      next: (r) => {
        this.working = false;
        this.result = r;
        if (!dry) {
          this.reload(true);
          this.api.list(this.id).subscribe((l) => (this.list = l));
        }
      },
      error: (err) => {
        this.working = false;
        this.importError = err?.error?.message ?? 'Could not read the file';
      },
    });
  }

  clear(): void {
    this.result = null;
    this.file = null;
    const el = document.getElementById('pl-file') as HTMLInputElement | null;
    if (el) el.value = '';
  }
}
