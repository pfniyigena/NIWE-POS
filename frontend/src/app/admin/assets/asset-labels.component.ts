import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import * as QRCode from 'qrcode';
import { AssetApiService } from '../../core/api/asset-api.service';

import { T } from '../../core/i18n/t';
interface Tag { id: number; code: string; name: string; place: string; image: string }

/**
 * Assets > Print tags: a sheet of labels with the tag number and a QR code that opens the asset. Standalone (no side menu) so it prints
 * cleanly. Works for the assets ticked on the register (?ids=1,2,3), or for every asset still in use when no ids are given.
 */
@Component({
  selector: 'app-asset-labels',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  template: `
    <div class="al-page" style="padding:1rem;">
      <div class="al-bar flex gap-2 mb-3">
        <button class="btn btn-primary" (click)="print()" [disabled]="!tags.length">{{ '🖨 Print' | translate }}</button>
        <button class="btn btn-outline-secondary" (click)="back()">{{ '← Back' | translate }}</button>
        <span class="text-muted" style="align-self:center;">{{ '{p0} tag(s)' | translate: { p0: (tags.length) } }}</span>
      </div>
      <div class="alert alert-danger al-bar" *ngIf="error">{{ error }}</div>
      <div class="al-grid" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(240px, 1fr)); gap:12px;">
        <div class="al-tag" *ngFor="let t of tags" style="border:2px solid #111; border-radius:8px; padding:10px; display:flex; gap:10px; align-items:center; page-break-inside:avoid;">
          <img [src]="t.image" [alt]="'QR code' | translate" style="width:96px; height:96px;" />
          <div style="min-width:0;">
            <div style="font-size:1.25rem; font-weight:700;">{{ t.code }}</div>
            <div style="font-size:0.85rem; word-break:break-word;">{{ t.name }}</div>
            <div style="font-size:0.75rem;" *ngIf="t.place">{{ t.place }}</div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`@media print { .al-bar { display: none !important; } .al-page { padding: 0 !important; } }`],
})
export class AssetLabelsComponent implements OnInit {
  tags: Tag[] = [];
  error: string | null = null;

  constructor(private api: AssetApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const ids = (this.route.snapshot.queryParamMap.get('ids') ?? '')
      .split(',')
      .map((x) => Number(x))
      .filter((x) => x > 0);
    this.api.find({}).subscribe({
      next: (list) => {
        const origin = window.location.origin;
        const chosen = list.filter((a) => (ids.length ? ids.includes(a.id) : a.status !== 'DISPOSED'));
        Promise.all(
          chosen.map(async (a) => ({
            id: a.id,
            code: a.code,
            name: a.name,
            place: [a.branchName, a.location].filter(Boolean).join(' — '),
            image: await QRCode.toDataURL(origin + '/admin/assets/' + a.id, { margin: 1, width: 240 }),
          }))
        ).then((t) => (this.tags = t));
      },
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the assets')),
    });
  }

  print(): void {
    window.print();
  }

  back(): void {
    this.router.navigate(['/admin/assets']);
  }
}
