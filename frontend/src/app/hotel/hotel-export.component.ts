import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE } from '../core/constants';
import { downloadBlob } from '../core/download-file';

/** Excel, PDF and CSV buttons for a Hotel screen that has no search bar (front desk lists, room types, the occupancy report). */
@Component({
  selector: 'app-hotel-export',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="hotel-export" style="display:inline-flex; gap:0.3rem; align-items:center; flex-wrap:wrap;">
      <span class="text-muted" style="font-size:0.85rem;">Export</span>
      <button *ngFor="let f of formats" class="btn btn-sm btn-outline-secondary" [ngClass]="'hx-' + f.value" type="button" (click)="go(f.value)" [disabled]="busy">{{ f.label }}</button>
      <span class="text-muted" style="font-size:0.8rem;" *ngIf="busy">Preparing…</span>
      <span style="color:#b91c1c; font-size:0.8rem;" *ngIf="error">{{ error }}</span>
    </span>
  `,
})
export class HotelExportComponent {
  /** The export path under /api, for example /hotel/room-types/export. */
  @Input() path = '';
  /** The file name without its extension. */
  @Input() name = 'hotel';
  @Input() filters: Record<string, string | number | boolean | null | undefined> = {};

  formats = [{ value: 'xlsx', label: 'Excel' }, { value: 'pdf', label: 'PDF' }, { value: 'csv', label: 'CSV' }];
  busy = false;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  go(format: string): void {
    this.busy = true;
    this.error = null;
    let params = new HttpParams().set('format', format);
    for (const [k, v] of Object.entries(this.filters ?? {})) {
      if (v === null || v === undefined || v === '' || v === false) continue;
      params = params.set(k, String(v));
    }
    this.http.get(`${API_BASE}${this.path}`, { params, responseType: 'blob' }).subscribe({
      next: (b) => { this.busy = false; downloadBlob(b, `${this.name}.${format}`); },
      error: () => { this.busy = false; this.error = 'Could not prepare the file'; },
    });
  }
}
