import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../core/constants';
import { showPdf } from '../core/open-pdf';

/** A button that opens a printable A4 paper (delivery note, goods-received note, count sheet, transfer note) in a new tab. */
@Component({
  selector: 'app-paper-button',
  standalone: true,
  imports: [CommonModule],
  template: `<button class="btn paper-btn" [ngClass]="cls" type="button" [disabled]="busy" (click)="open()">{{ busy ? 'Opening…' : label }}</button><span class="paper-err" *ngIf="error" style="color:#b91c1c; font-size:0.8rem; margin-left:0.4rem;">{{ error }}</span>`,
})
export class PaperButtonComponent {
  /** Button text. */
  @Input() label = 'Print';
  /** API path after /api, e.g. /sales-orders/5/delivery-note. */
  @Input() path = '';
  @Input() cls = 'btn-outline-primary';
  @Input() file = 'document.pdf';
  busy = false;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  open(): void {
    this.busy = true;
    this.error = null;
    showPdf(this.http.get(API_BASE + this.path, { responseType: 'blob' }), this.file, (err) => {
      this.busy = false;
      this.error = err;
    });
  }
}
