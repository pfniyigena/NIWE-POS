import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { Product } from '../core/models';

interface OptionRow {
  name: string;
  values: string;
}

/**
 * Admin > Products > one product > Variants. The product acts as the template: name one to three
 * options (Size, Colour…) with comma-separated values and one sellable product is generated per
 * combination, each with its own SKU, barcode, price and stock. The template itself is then kept inactive.
 */
@Component({
  selector: 'app-product-variants',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <ng-container *ngIf="template as t">
        <div class="flex justify-between align-center mb-3">
          <div>
            <h3 style="margin-bottom:0;">Variants of {{ t.name }}</h3>
            <div class="text-muted" style="font-size:0.85rem;" *ngIf="t.variantAttributes">{{ t.variantAttributes }}</div>
          </div>
          <a class="btn btn-link" [routerLink]="['/admin/products', t.id]">Back to product</a>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <strong>{{ variants.length ? 'Add more variants' : 'Generate variants' }}</strong>
          <p class="text-muted" style="font-size:0.85rem; margin:0.25rem 0 0.75rem;">
            One product is created for every combination, copying this product's price, cost, category, brand and tax. Existing
            combinations are skipped.<span *ngIf="!variants.length"> This product is kept as the template and made inactive, so it must hold no stock.</span>
          </p>
          <div class="flex gap-2 mb-2" *ngFor="let o of options; let i = index" style="align-items:flex-end;">
            <div style="width:180px;">
              <label class="form-label" *ngIf="i === 0">Option</label>
              <input class="form-control" placeholder="e.g. Size" [(ngModel)]="o.name" [name]="'on' + i" />
            </div>
            <div class="grow">
              <label class="form-label" *ngIf="i === 0">Values (comma separated)</label>
              <input class="form-control" placeholder="e.g. S, M, L" [(ngModel)]="o.values" [name]="'ov' + i" />
            </div>
            <button class="btn btn-link" type="button" *ngIf="options.length > 1" (click)="options.splice(i, 1)">Remove</button>
          </div>
          <div class="flex gap-2 align-center">
            <button class="btn btn-outline-secondary" type="button" *ngIf="options.length < 3" (click)="options.push({ name: '', values: '' })">+ Add option</button>
            <span class="text-muted" *ngIf="combinationCount > 0">{{ combinationCount }} combination{{ combinationCount === 1 ? '' : 's' }}</span>
            <span class="grow"></span>
            <button class="btn btn-primary" type="button" [disabled]="busy || combinationCount === 0" (click)="generate()">Generate</button>
          </div>
        </div>

        <table>
          <thead>
            <tr>
              <th>Variant</th>
              <th>SKU</th>
              <th>Barcode</th>
              <th class="text-end">Price</th>
              <th class="text-end">Stock</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let v of variants">
              <td>{{ v.variantLabel }}</td>
              <td>{{ v.sku }}</td>
              <td>{{ v.barcode || '—' }}</td>
              <td class="text-end">{{ v.unitPrice | number: '1.2-2' }}</td>
              <td class="text-end">{{ v.stockQuantity }}</td>
              <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/products', v.id, 'edit']">Edit</a></td>
            </tr>
            <tr *ngIf="variants.length === 0">
              <td colspan="6" class="text-muted">No variants yet.</td>
            </tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class ProductVariantsComponent implements OnInit {
  template: Product | null = null;
  variants: Product[] = [];
  options: OptionRow[] = [{ name: 'Size', values: '' }];
  error: string | null = null;
  message: string | null = null;
  busy = false;

  constructor(private route: ActivatedRoute, private catalogApi: CatalogApiService) {}

  private get id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    this.catalogApi.getProduct(this.id).subscribe({
      next: (p) => (this.template = p),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the product'),
    });
    this.loadVariants();
  }

  private loadVariants(): void {
    this.catalogApi.listVariants(this.id).subscribe({
      next: (v) => (this.variants = v),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load variants'),
    });
  }

  private parsed(): { name: string; values: string[] }[] {
    return this.options
      .map((o) => ({ name: o.name.trim(), values: Array.from(new Set(o.values.split(',').map((v) => v.trim()).filter((v) => v))) }))
      .filter((o) => o.name && o.values.length > 0);
  }

  get combinationCount(): number {
    const p = this.parsed();
    return p.length === 0 ? 0 : p.reduce((n, o) => n * o.values.length, 1);
  }

  generate(): void {
    const attributes = this.parsed();
    if (attributes.length === 0) return;
    this.error = null;
    this.message = null;
    this.busy = true;
    this.catalogApi.generateVariants(this.id, attributes).subscribe({
      next: (r) => {
        this.busy = false;
        this.variants = r.variants;
        this.message = `${r.created} variant${r.created === 1 ? '' : 's'} created${r.skipped ? `, ${r.skipped} already existed` : ''}.`;
        this.catalogApi.getProduct(this.id).subscribe((p) => (this.template = p));
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Failed to generate variants';
      },
    });
  }
}
