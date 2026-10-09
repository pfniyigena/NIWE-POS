import { AfterViewInit, Component, ElementRef, OnInit, QueryList, ViewChildren } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import JsBarcode from 'jsbarcode';
import { CatalogApiService } from '../core/api/catalog-api.service';
import { StoreSettingsApiService } from '../core/api/store-settings-api.service';
import { Product, StoreSettings } from '../core/models';

/**
 * Fourier: "barcode/label generation for products that don't already carry a manufacturer
 * barcode" — `/admin/products/labels?ids=1,2,3`, reached from a "Generate"/"Label" action on the
 * product list or edit form. Deliberately light/printable like the sale receipt, return receipt
 * and purchase order print views (see those components' own doc comments for why) rather than a
 * server-rendered PDF — a sheet of labels is exactly the kind of thing someone wants to eyeball
 * and reprint from the browser's own print dialog, same as those.
 * <p>
 * Renders each barcode client-side with JsBarcode using CODE128 — not EAN-13/UPC — because a
 * product on this screen might carry either this app's own freshly-generated EAN-13 (see
 * CatalogService.generateBarcode) or an arbitrary pre-existing manufacturer value of any length,
 * and CODE128 can encode either one losslessly without caring which; any barcode scanner that can
 * already read this app's checkout barcode-scan flow reads CODE128 just as well. A product that
 * still has no barcode at all gets a "Generate barcode" button right on its own label card instead
 * of a barcode image, so this one screen covers both halves of the request — generating the value
 * and printing the label — without forcing a trip back to the product list first.
 */
@Component({
  selector: 'app-product-labels',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page">
      <div class="no-print toolbar">
        <button (click)="print()" [disabled]="products.length === 0">Print</button>
        <a routerLink="/admin/products">Back to products</a>
      </div>
      <div class="alert" *ngIf="error">{{ error }}</div>

      <div class="labels">
        <div class="label" *ngFor="let p of products">
          <div class="store" *ngIf="settings?.businessName">{{ settings?.businessName }}</div>
          <div class="name">{{ p.name }}</div>
          <div class="price">{{ p.unitPrice | number: '1.2-2' }}</div>
          <ng-container *ngIf="p.barcode">
            <svg #barcodeSvg></svg>
          </ng-container>
          <div class="no-barcode no-print" *ngIf="!p.barcode">
            <button [disabled]="generatingFor === p.id" (click)="generateBarcode(p)">
              {{ generatingFor === p.id ? 'Generating…' : 'Generate barcode' }}
            </button>
          </div>
        </div>
        <p class="text-muted" *ngIf="!loading && products.length === 0">No products to print — nothing matched the selection.</p>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        background: #fff;
        color: #111;
        min-height: 100vh;
        font-family: Arial, sans-serif;
      }
      .page {
        max-width: 900px;
        margin: 0 auto;
        padding: 16px;
      }
      .toolbar {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
      }
      .toolbar button,
      .toolbar a {
        padding: 8px 14px;
        cursor: pointer;
        border: 1px solid #ccc;
        border-radius: 4px;
        background: #f5f5f5;
        color: #111;
        text-decoration: none;
        font-size: 0.9rem;
      }
      .alert {
        background: #fde8ea;
        color: #9a1f33;
        padding: 8px 12px;
        border-radius: 4px;
        margin-bottom: 12px;
      }
      .labels {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
      }
      .label {
        border: 1px solid #999;
        border-radius: 4px;
        padding: 8px;
        text-align: center;
        page-break-inside: avoid;
        min-height: 1.3in;
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 2px;
      }
      .store {
        font-size: 0.7rem;
        color: #555;
      }
      .name {
        font-size: 0.85rem;
        font-weight: 700;
        overflow-wrap: break-word;
      }
      .price {
        font-size: 0.95rem;
        font-weight: 700;
      }
      .no-barcode button {
        padding: 6px 10px;
        cursor: pointer;
        border: 1px solid #ccc;
        border-radius: 4px;
        background: #f5f5f5;
        font-size: 0.8rem;
      }
      @media print {
        .no-print {
          display: none !important;
        }
        .labels {
          grid-template-columns: repeat(3, 1fr);
        }
      }
    `,
  ],
})
export class ProductLabelsComponent implements OnInit, AfterViewInit {
  @ViewChildren('barcodeSvg') barcodeSvgs!: QueryList<ElementRef<SVGElement>>;

  products: Product[] = [];
  settings: StoreSettings | null = null;
  error: string | null = null;
  loading = false;
  generatingFor: number | null = null;

  constructor(
    private route: ActivatedRoute,
    private catalogApi: CatalogApiService,
    private storeSettingsApi: StoreSettingsApiService
  ) {}

  ngOnInit(): void {
    this.storeSettingsApi.get().subscribe({
      next: (s) => (this.settings = s),
      error: () => (this.settings = null), // store name on the label is a nice-to-have only — never blocks printing
    });

    const idsParam = this.route.snapshot.queryParamMap.get('ids') ?? '';
    const ids = idsParam
      .split(',')
      .map((s) => Number(s.trim()))
      .filter((n) => Number.isFinite(n) && n > 0);
    if (ids.length === 0) {
      this.error = 'No products were selected to print.';
      return;
    }

    this.loading = true;
    forkJoin(ids.map((id) => this.catalogApi.getProduct(id))).subscribe({
      next: (products) => {
        this.products = products;
        this.loading = false;
        // Barcodes render once the *ngFor creates the <svg> elements — see ngAfterViewInit below.
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Failed to load the selected products';
        this.loading = false;
      },
    });
  }

  ngAfterViewInit(): void {
    this.barcodeSvgs.changes.subscribe(() => this.renderBarcodes());
    this.renderBarcodes();
  }

  private renderBarcodes(): void {
    // Only products with a barcode render an <svg> in the template above, so this list is already
    // exactly those products, in the same order — no index mismatch against `products` itself.
    const withBarcode = this.products.filter((p) => !!p.barcode);
    this.barcodeSvgs.forEach((ref, i) => {
      const product = withBarcode[i];
      if (!product?.barcode) return;
      JsBarcode(ref.nativeElement, product.barcode, {
        format: 'CODE128',
        displayValue: true,
        fontSize: 12,
        height: 40,
        margin: 2,
      });
    });
  }

  generateBarcode(p: Product): void {
    this.error = null;
    this.generatingFor = p.id;
    this.catalogApi.generateBarcode(p.id).subscribe({
      next: (updated) => {
        this.generatingFor = null;
        p.barcode = updated.barcode;
        // New <svg> appears for this card next render — QueryList.changes (above) catches it.
      },
      error: (err) => {
        this.generatingFor = null;
        this.error = err?.error?.message ?? 'Failed to generate a barcode';
      },
    });
  }

  print(): void {
    window.print();
  }
}
