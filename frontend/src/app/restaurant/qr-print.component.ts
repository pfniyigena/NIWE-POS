import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import * as QRCode from 'qrcode';
import { RestaurantOnlineApiService } from '../core/api/restaurant-online-api.service';

import { T } from '../core/i18n/t';
interface Card { title: string; sub: string; url: string; image: string }

/** A sheet of QR cards for printing: one per table, or one poster for the takeaway and delivery page. Standalone (no side menu) so it prints cleanly. */
@Component({
  selector: 'app-qr-print',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  template: `
    <div class="qp-page" style="padding:1rem;">
      <div class="qp-bar flex gap-2 mb-3">
        <button class="btn btn-primary qp-print" (click)="print()">{{ '🖨 Print' | translate }}</button>
        <button class="btn btn-outline-secondary" (click)="back()">{{ '← Back' | translate }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="qp-grid" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(260px, 1fr)); gap:16px;">
        <div class="qp-card" *ngFor="let c of cards" style="border:2px solid #111; border-radius:12px; padding:16px; text-align:center; page-break-inside:avoid;">
          <div style="font-size:1.1rem;">{{ c.sub }}</div>
          <div class="qp-title" style="font-size:2rem; font-weight:700; margin-bottom:6px;">{{ c.title }}</div>
          <img [src]="c.image" [alt]="'QR code' | translate" style="width:200px; height:200px;" />
          <div style="font-size:0.8rem; word-break:break-all; margin-top:6px;">{{ c.url }}</div>
        </div>
      </div>
    </div>
  `,
  styles: [`@media print { .qp-bar { display: none !important; } .qp-page { padding: 0 !important; } }`],
})
export class QrPrintComponent implements OnInit {
  cards: Card[] = [];
  error: string | null = null;

  constructor(private api: RestaurantOnlineApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const origin = window.location.origin;
    if (this.route.snapshot.queryParamMap.get('web')) {
      this.make([{ title: 'Order online', sub: 'Takeaway & delivery', url: origin + '/order' }]);
      return;
    }
    this.api.qr().subscribe({
      next: (list) => this.make(list.filter((t) => t.active).map((t) => ({ title: 'Table ' + t.name, sub: 'Scan to see the menu and order', url: origin + t.path }))),
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the QR codes')),
    });
  }

  private make(items: { title: string; sub: string; url: string }[]): void {
    Promise.all(items.map(async (i) => ({ ...i, image: await QRCode.toDataURL(i.url, { margin: 1, width: 400 }) }))).then((c) => (this.cards = c));
  }

  print(): void {
    window.print();
  }

  back(): void {
    window.history.back();
  }
}
