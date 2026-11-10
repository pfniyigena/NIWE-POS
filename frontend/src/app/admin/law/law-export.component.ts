import { Component, Input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE } from '../../core/constants';
import { downloadBlob } from '../../core/download-file';

/** Excel, PDF and CSV buttons for a list: downloads every row that matches the list filters, not just the page on screen. */
@Component({
  selector: 'app-law-export',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  template: `
    <span class="law-export" style="display:inline-flex; gap:0.3rem; align-items:center; flex-wrap:wrap;">
      <span class="text-muted" style="font-size:0.8rem;" *ngIf="label">{{ label }}</span>
      <button *ngFor="let f of formats" class="btn btn-sm btn-outline-secondary" type="button" (click)="go(f.value)" [disabled]="busy">{{ f.label }}</button>
      <span class="text-muted" style="font-size:0.8rem;" *ngIf="busy">{{ 'Preparing…' | translate }}</span>
      <span style="color:#b91c1c; font-size:0.8rem;" *ngIf="error">{{ error }}</span>
    </span>
  `,
})
export class LawExportComponent {
  /** matters, clients, contacts, deadlines, tasks, billing, trust-balances or trust-ledger */
  @Input() kind = 'matters';
  /** A short word shown before the buttons when a screen has more than one export. */
  @Input() label = '';
  /** The filters of the list as they are now; empty values are left out. */
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
    this.http.get(`${API_BASE}/law/export/${this.kind}`, { params, responseType: 'blob' }).subscribe({
      next: (b) => { this.busy = false; downloadBlob(b, `${this.kind}.${format}`); },
      error: () => { this.busy = false; this.error = 'Could not prepare the file'; },
    });
  }
}
