import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../../core/constants';
import { downloadBlob } from '../../core/download-file';

interface PrintItem { label: string; path: string; file: string; enabled: boolean; hint: string; }
export interface PaymentRow { id: number; paidAt: string; amount: number; method: string | null; receiptNumber: string | null; what: string; }

/** Opens a PDF from the print service in a new tab (or saves it when the browser blocks the new tab). Shared by the print buttons and the receipt buttons. */
function openPdf(http: HttpClient, path: string, file: string, done: (error: string | null) => void): void {
  http.get(`${API_BASE}/realestate/print/${path}`, { responseType: 'blob' }).subscribe({
    next: (b) => {
      const url = URL.createObjectURL(b);
      const w = window.open(url, '_blank');
      if (!w) downloadBlob(b, file);
      done(null);
    },
    error: (e) => {
      const blob = e?.error;
      if (blob instanceof Blob) {
        blob.text().then((t) => { try { done(JSON.parse(t).message ?? 'Could not prepare the document'); } catch { done('Could not prepare the document'); } });
      } else done('Could not prepare the document');
    },
  });
}

/** The print buttons of a record: offer letter, reservation letter and sale agreement (deals and unit sales), the price list (projects) or the flyer (properties). A button is greyed out, with a hint, until the record has reached that step. */
@Component({
  selector: 'app-re-print',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="re-print mb-2" style="display:flex; gap:0.4rem; flex-wrap:wrap; align-items:center;" *ngIf="items.length">
      <span class="text-muted" style="font-size:0.85rem;">Print:</span>
      <button *ngFor="let i of items; trackBy: track" class="btn btn-sm btn-outline-secondary" type="button" [disabled]="busy || !i.enabled" [title]="i.enabled ? '' : i.hint" (click)="open(i)">{{ i.label }}</button>
      <span class="text-muted" style="font-size:0.8rem;" *ngIf="busy">Preparing…</span>
      <span style="color:#b91c1c; font-size:0.85rem;" *ngIf="error">{{ error }}</span>
    </div>
  `,
})
export class RePrintComponent implements OnChanges {
  /** deal, sale, project or property */
  @Input() kind = 'deal';
  @Input() id: number | null = null;
  /** The stage of the deal or sale (decides which papers can be printed). */
  @Input() stage: string | null = null;

  items: PrintItem[] = [];
  busy = false;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  track(_: number, i: PrintItem): string { return i.path; }

  ngOnChanges(): void {
    const s = this.stage ?? '';
    const base = this.kind === 'deal' ? `deals/${this.id}` : this.kind === 'sale' ? `unit-sales/${this.id}` : this.kind === 'project' ? `projects/${this.id}` : `properties/${this.id}`;
    if (this.id == null || s === 'CANCELLED') { this.items = []; return; }
    if (this.kind === 'deal') {
      this.items = [
        { label: 'Offer letter', path: `${base}/offer`, file: 'offer.pdf', enabled: true, hint: '' },
        { label: 'Reservation letter', path: `${base}/reservation`, file: 'reservation.pdf', enabled: s !== 'OFFER', hint: 'Reserve the property first' },
        { label: 'Sale agreement', path: `${base}/agreement`, file: 'sale-agreement.pdf', enabled: s === 'AGREEMENT' || s === 'COMPLETED', hint: 'Available once the agreement is recorded on the deal' },
      ];
    } else if (this.kind === 'sale') {
      this.items = [
        { label: 'Reservation letter', path: `${base}/reservation`, file: 'reservation.pdf', enabled: true, hint: '' },
        { label: 'Sale agreement', path: `${base}/agreement`, file: 'sale-agreement.pdf', enabled: s === 'SOLD' || s === 'COMPLETED', hint: 'Available once the agreement is signed and the unit is sold' },
        { label: 'Buyer statement', path: `${base}/statement`, file: 'statement.pdf', enabled: s === 'SOLD' || s === 'COMPLETED', hint: 'Available once the unit is sold' },
      ];
    } else if (this.kind === 'project') {
      this.items = [
        { label: 'Price list (available units)', path: `${base}/price-list`, file: 'price-list.pdf', enabled: true, hint: '' },
        { label: 'Price list (all units)', path: `${base}/price-list?all=true`, file: 'price-list-all.pdf', enabled: true, hint: '' },
      ];
    } else {
      this.items = [{ label: 'Flyer', path: `${base}/flyer`, file: 'flyer.pdf', enabled: true, hint: '' }];
    }
  }

  open(i: PrintItem): void {
    this.busy = true;
    this.error = null;
    openPdf(this.http, i.path, i.file, (e) => { this.busy = false; this.error = e; });
  }
}

/** The payments received on a deal or a unit sale, each with a button for its receipt. */
@Component({
  selector: 'app-re-payments',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h4 class="mb-2">Payments received</h4>
    <div style="color:#b91c1c; font-size:0.85rem;" class="mb-1" *ngIf="error">{{ error }}</div>
    <p class="text-muted" *ngIf="loaded && !rows.length">No payment has been received yet.</p>
    <table class="stack table-sm" *ngIf="rows.length">
      <thead><tr><th>Date</th><th>For</th><th>Method</th><th>Receipt no.</th><th class="text-end">Amount</th><th></th></tr></thead>
      <tbody><tr *ngFor="let r of rows; trackBy: track">
        <td data-label="Date">{{ r.paidAt | date: 'd MMM y' }}</td><td data-label="For">{{ r.what }}</td><td data-label="Method">{{ r.method || '—' }}</td>
        <td data-label="Receipt no.">{{ r.receiptNumber || '—' }}</td><td class="text-end" data-label="Amount">{{ r.amount | number: '1.0-0' }}</td>
        <td data-label="" style="text-align:right;"><button class="btn btn-sm btn-outline-secondary receipt-btn" type="button" (click)="receipt(r)" [disabled]="busy">Receipt</button></td>
      </tr></tbody>
    </table>
  `,
})
export class RePaymentsComponent implements OnInit, OnChanges {
  /** deals or unit-sales */
  @Input() kind = 'deals';
  @Input() id: number | null = null;
  /** Change this to load the list again (for example after a payment was recorded). */
  @Input() refresh: unknown = null;

  rows: PaymentRow[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  track(_: number, r: PaymentRow): number { return r.id; }

  ngOnInit(): void { this.load(); }
  ngOnChanges(): void { if (this.loaded) this.load(); }

  load(): void {
    if (this.id == null) return;
    this.http.get<PaymentRow[]>(`${API_BASE}/realestate/print/${this.kind}/${this.id}/payments`).subscribe({
      next: (r) => { this.rows = r; this.loaded = true; },
      error: () => { this.rows = []; this.loaded = true; },
    });
  }

  receipt(r: PaymentRow): void {
    this.busy = true;
    this.error = null;
    openPdf(this.http, `${this.kind}/${this.id}/payments/${r.id}/receipt`, `receipt-${r.receiptNumber ?? r.id}.pdf`, (e) => { this.busy = false; this.error = e; });
  }
}
