import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AdReport, OnlineStoreApiService, StoreAd, StoreAdForm, StoreAdvertiser, StoreAdvertiserForm } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Advertising: the businesses that pay for ads (advertisers), their ads (picture, link, spaces, dates, what they are charged) and a
 * report of how often each ad was shown and clicked, which can be printed for an advertiser. Nothing is invoiced here: the fee is a note for the shop.
 */
@Component({
  selector: 'app-store-ads',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Advertising' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/online-store/orders">{{ 'Online orders' | translate }}</a>
      </div>
      <p class="text-muted">{{ 'Sell space on your shop to other businesses. An ad is a picture that links to the advertiser. The shop labels every ad "Advertisement" and counts how often it is shown and clicked.' | translate }}</p>

      <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
        <button type="button" class="btn ad-tab-ads" [class.btn-primary]="tab === 'ads'" [class.btn-outline-secondary]="tab !== 'ads'" (click)="tab = 'ads'">{{ 'Ads' | translate }}</button>
        <button type="button" class="btn ad-tab-advertisers" [class.btn-primary]="tab === 'advertisers'" [class.btn-outline-secondary]="tab !== 'advertisers'" (click)="tab = 'advertisers'">{{ 'Advertisers' | translate }}</button>
        <button type="button" class="btn ad-tab-report" [class.btn-primary]="tab === 'report'" [class.btn-outline-secondary]="tab !== 'report'" (click)="openReport()">{{ 'Report' | translate }}</button>
      </div>

      <!-- ===== Ads ===== -->
      <ng-container *ngIf="tab === 'ads'">
        <div class="alert alert-warning" *ngIf="!advertisers.length">{{ 'Add an advertiser first (Advertisers tab), then you can add its ads.' | translate }}</div>
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveAd()" *ngIf="advertisers.length">
          <h4 class="mb-2">{{ editingAd ? ('Change the ad' | translate) : ('New ad' | translate) }}</h4>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Advertiser' | translate }}</label>
              <select class="form-select ad-advertiser" name="advertiser" [(ngModel)]="af.advertiserId">
                <option [ngValue]="null">—</option>
                <option *ngFor="let a of advertisers" [ngValue]="a.id">{{ a.name }}{{ a.active ? '' : (' (' + ('off' | translate) + ')') }}</option>
              </select></div>
            <div class="form-group" style="min-width:220px;"><label class="form-label">{{ 'Title (staff only)' | translate }}</label><input class="form-control ad-title" maxlength="120" name="title" [(ngModel)]="af.title" /></div>
            <div class="form-group" style="min-width:280px; flex:1;"><label class="form-label">{{ 'Link (where a click goes)' | translate }}</label><input class="form-control ad-link" maxlength="500" placeholder="https://" name="link" [(ngModel)]="af.linkUrl" /></div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ 'Where it is shown' | translate }}</label>
            <div class="flex gap-3" style="flex-wrap:wrap;">
              <label *ngFor="let s of spaces" class="flex gap-1" style="align-items:center;"><input type="checkbox" [attr.data-space]="s.code" [checked]="af.placements.includes(s.code)" (change)="toggleSpace(s.code, $any($event.target).checked)" /> {{ s.label | translate }}</label>
            </div>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group"><label class="form-label">{{ 'Shown from (optional)' | translate }}</label><input class="form-control" type="date" name="from" [(ngModel)]="af.startsOn" /></div>
            <div class="form-group"><label class="form-label">{{ 'Shown until (optional)' | translate }}</label><input class="form-control" type="date" name="until" [(ngModel)]="af.endsOn" /></div>
            <div class="form-group"><label class="form-label">{{ 'Fee charged (optional)' | translate }}</label><input class="form-control ad-fee" type="number" min="0" step="any" style="width:150px;" name="fee" [(ngModel)]="af.fee" /></div>
            <div class="form-group" style="min-width:200px;"><label class="form-label">{{ 'Fee note (optional)' | translate }}</label><input class="form-control" maxlength="160" placeholder="{{ 'e.g. one month' | translate }}" name="feeNote" [(ngModel)]="af.feeNote" /></div>
            <div class="form-group" style="min-width:200px;"><label class="form-label">{{ 'Text for screen readers (optional)' | translate }}</label><input class="form-control" maxlength="160" name="alt" [(ngModel)]="af.altText" /></div>
          </div>
          <div class="form-group">
            <label class="form-label">{{ editingAd ? ('Picture (choose a new one to replace it)' | translate) : ('Picture' | translate) }}</label>
            <div class="flex gap-3" style="align-items:center; flex-wrap:wrap;">
              <img *ngIf="editingAd" [src]="imageSrc(editingAd.imageUrl)" alt="" style="height:60px; max-width:200px; object-fit:contain; border-radius:6px; background:#f3f4f6;" />
              <input class="form-control ad-file" type="file" accept="image/jpeg,image/png,image/webp,image/gif" style="max-width:320px;" (change)="pick($event)" />
            </div>
            <div class="text-muted" style="font-size:0.8rem;">{{ 'A wide picture works best, about 1200 × 300 pixels. JPEG, PNG, WebP or GIF, at most 5 MB.' | translate }}</div>
          </div>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="active" [(ngModel)]="af.active" /> {{ 'Switched on' | translate }}</label>
            <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="paid" [(ngModel)]="af.feePaid" /> {{ 'The fee is paid' | translate }}</label>
          </div>
          <div class="flex gap-2 mt-2">
            <button class="btn btn-primary ad-save" type="submit" [disabled]="busy">{{ 'Save ad' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="editingAd" (click)="resetAd()">{{ 'Cancel' | translate }}</button>
          </div>
        </form>

        <div class="card" style="padding:1rem; overflow-x:auto;">
          <p class="text-muted" *ngIf="!loading && !ads.length">{{ 'No ads yet.' | translate }}</p>
          <table class="table-sm" *ngIf="ads.length">
            <thead><tr><th></th><th>{{ 'Ad' | translate }}</th><th>{{ 'Advertiser' | translate }}</th><th>{{ 'Where' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th><th class="text-end">{{ 'Shown' | translate }}</th><th class="text-end">{{ 'Clicks' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let a of ads" class="ad-row">
                <td><img [src]="imageSrc(a.imageUrl)" alt="" style="height:36px; width:90px; object-fit:contain; background:#f3f4f6; border-radius:4px;" /></td>
                <td><strong>{{ a.title }}</strong><div class="text-muted" style="font-size:0.75rem;">{{ dates(a) }}</div></td>
                <td>{{ a.advertiserName }}</td>
                <td>{{ spaceNames(a) }}</td>
                <td><span class="ad-status" [style.color]="a.showingNow ? '#15803d' : '#6b7280'">{{ status(a) }}</span></td>
                <td class="text-end">{{ a.fee === null ? '—' : (a.fee | number: '1.0-2') }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="a.fee !== null">{{ a.feePaid ? ('paid' | translate) : ('not paid' | translate) }}</div></td>
                <td class="text-end">{{ a.views | number }}</td>
                <td class="text-end">{{ a.clicks | number }}</td>
                <td class="text-end" style="white-space:nowrap;">
                  <button class="btn btn-sm btn-outline-secondary ad-edit" style="margin-right:0.3rem;" (click)="editAd(a)">{{ 'Edit' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger ad-del" (click)="removeAd(a)">{{ 'Delete' | translate }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </ng-container>

      <!-- ===== Advertisers ===== -->
      <ng-container *ngIf="tab === 'advertisers'">
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="saveAdvertiser()">
          <h4 class="mb-2">{{ editingAdvertiser ? ('Change the advertiser' | translate) : ('New advertiser' | translate) }}</h4>
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group" style="min-width:220px;"><label class="form-label">{{ 'Business name' | translate }}</label><input class="form-control adv-name" maxlength="120" name="name" [(ngModel)]="vf.name" /></div>
            <div class="form-group"><label class="form-label">{{ 'Contact person' | translate }}</label><input class="form-control" maxlength="120" name="contact" [(ngModel)]="vf.contactName" /></div>
            <div class="form-group"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" maxlength="160" name="email" [(ngModel)]="vf.email" /></div>
            <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" maxlength="40" name="phone" [(ngModel)]="vf.phone" /></div>
          </div>
          <div class="form-group"><label class="form-label">{{ 'Notes (optional)' | translate }}</label><input class="form-control" maxlength="500" name="notes" [(ngModel)]="vf.notes" /></div>
          <label class="flex gap-1" style="align-items:center;"><input type="checkbox" name="vactive" [(ngModel)]="vf.active" /> {{ 'Switched on (when off, none of its ads is shown)' | translate }}</label>
          <div class="flex gap-2 mt-2">
            <button class="btn btn-primary adv-save" type="submit" [disabled]="busy">{{ 'Save advertiser' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="editingAdvertiser" (click)="resetAdvertiser()">{{ 'Cancel' | translate }}</button>
          </div>
        </form>
        <div class="card" style="padding:1rem; overflow-x:auto;">
          <p class="text-muted" *ngIf="!advertisers.length">{{ 'No advertisers yet.' | translate }}</p>
          <table class="table-sm" *ngIf="advertisers.length">
            <thead><tr><th>{{ 'Business' | translate }}</th><th>{{ 'Contact' | translate }}</th><th class="text-end">{{ 'Ads' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let a of advertisers" class="adv-row">
                <td><strong>{{ a.name }}</strong></td>
                <td>{{ a.contactName }}<div class="text-muted" style="font-size:0.75rem;">{{ a.email }} {{ a.phone }}</div></td>
                <td class="text-end">{{ a.adCount }}</td>
                <td>{{ a.active ? ('On' | translate) : ('Off' | translate) }}</td>
                <td class="text-end" style="white-space:nowrap;">
                  <button class="btn btn-sm btn-outline-secondary adv-edit" style="margin-right:0.3rem;" (click)="editAdvertiser(a)">{{ 'Edit' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger adv-del" (click)="removeAdvertiser(a)">{{ 'Delete' | translate }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </ng-container>

      <!-- ===== Report ===== -->
      <ng-container *ngIf="tab === 'report'">
        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="loadReport()">
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'From' | translate }}</label><input class="form-control rp-from" type="date" name="rfrom" [(ngModel)]="rFrom" /></div>
            <div class="form-group"><label class="form-label">{{ 'Until' | translate }}</label><input class="form-control rp-to" type="date" name="rto" [(ngModel)]="rTo" /></div>
            <div class="form-group"><label class="form-label">{{ 'Advertiser' | translate }}</label>
              <select class="form-select rp-adv" name="radv" [(ngModel)]="rAdvertiser">
                <option [ngValue]="null">{{ 'All advertisers' | translate }}</option>
                <option *ngFor="let a of advertisers" [ngValue]="a.id">{{ a.name }}</option>
              </select></div>
            <div class="form-group"><button class="btn btn-primary rp-show" type="submit" [disabled]="busy">{{ 'Show' | translate }}</button></div>
            <div class="form-group flex" *ngIf="report" style="gap:0.5rem;"><button class="btn btn-outline-secondary rp-print" type="button" (click)="print()">{{ 'Print' | translate }}</button><button class="btn btn-outline-secondary rp-csv" type="button" (click)="csv()">{{ 'Download CSV' | translate }}</button></div>
          </div>
        </form>
        <div class="card" style="padding:1rem; overflow-x:auto;" *ngIf="report">
          <div class="flex mb-2" style="flex-wrap:wrap; gap:2rem;">
            <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Shown' | translate }}</div><strong class="rp-views" style="font-size:1.3rem;">{{ report.views | number }}</strong></div>
            <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Clicks' | translate }}</div><strong class="rp-clicks" style="font-size:1.3rem;">{{ report.clicks | number }}</strong></div>
            <div><div class="text-muted" style="font-size:0.8rem;">{{ 'Click rate' | translate }}</div><strong style="font-size:1.3rem;">{{ report.clickRate }}%</strong></div>
            <div class="text-muted" style="align-self:flex-end;">{{ report.from }} → {{ report.to }}</div>
          </div>
          <table class="table-sm">
            <thead><tr><th>{{ 'Advertiser' | translate }}</th><th>{{ 'Ad' | translate }}</th><th>{{ 'Where' | translate }}</th><th class="text-end">{{ 'Shown' | translate }}</th><th class="text-end">{{ 'Clicks' | translate }}</th><th class="text-end">{{ 'Click rate' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let r of report.rows" class="rp-row">
                <td>{{ r.advertiserName }}</td>
                <td>{{ r.title }}</td>
                <td><span *ngFor="let p of r.byPlacement; let last = last">{{ spaceLabel(p.placement) }} ({{ p.views }}/{{ p.clicks }}){{ last ? '' : ', ' }}</span></td>
                <td class="text-end">{{ r.views | number }}</td>
                <td class="text-end">{{ r.clicks | number }}</td>
                <td class="text-end">{{ r.clickRate }}%</td>
                <td class="text-end">{{ r.fee === null ? '—' : (r.fee | number: '1.0-2') }}<span class="text-muted" *ngIf="r.fee !== null"> ({{ r.feePaid ? ('paid' | translate) : ('not paid' | translate) }})</span></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" style="font-size:0.8rem;">{{ 'A visitor counts once per ad and place for half an hour, and an ad counts as shown only when it was on screen. In the Where column, the numbers are shown/clicks.' | translate }}</p>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class StoreAdsComponent implements OnInit {
  tab: 'ads' | 'advertisers' | 'report' = 'ads';
  ads: StoreAd[] = [];
  advertisers: StoreAdvertiser[] = [];
  loading = true;
  busy = false;
  error: string | null = null;
  message: string | null = null;

  readonly spaces = [
    { code: 'HOME_TOP', label: 'Top of the shop home page' },
    { code: 'PRODUCT', label: 'Above the product list' },
    { code: 'ORDER', label: 'Order tracking page (also after paying)' },
  ];

  editingAd: StoreAd | null = null;
  af: StoreAdForm = this.blankAd();
  file: File | null = null;

  editingAdvertiser: StoreAdvertiser | null = null;
  vf: StoreAdvertiserForm = this.blankAdvertiser();

  rFrom = '';
  rTo = '';
  rAdvertiser: number | null = null;
  report: AdReport | null = null;

  constructor(private api: OnlineStoreApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.advertisers().subscribe({ next: (l) => (this.advertisers = l), error: (e) => this.fail(e, 'Failed to load the ads') });
    this.api.ads().subscribe({ next: (l) => { this.ads = l; this.loading = false; }, error: (e) => { this.loading = false; this.fail(e, 'Failed to load the ads'); } });
  }

  private fail(e: any, fallback: string): void {
    this.message = null;
    this.error = e?.error?.message ?? T(fallback);
  }

  // ---- ads

  private blankAd(): StoreAdForm {
    return { advertiserId: null, title: '', placements: ['HOME_TOP'], linkUrl: '', altText: '', startsOn: '', endsOn: '', active: true, fee: '', feeNote: '', feePaid: false };
  }

  imageSrc(path: string): string {
    return this.api.imageUrl(path);
  }

  toggleSpace(code: string, on: boolean): void {
    const rest = this.af.placements.filter((c) => c !== code);
    this.af.placements = on ? [...rest, code] : rest;
  }

  pick(ev: Event): void {
    this.file = (ev.target as HTMLInputElement).files?.[0] ?? null;
  }

  spaceLabel(code: string): string {
    const s = this.spaces.find((x) => x.code === code);
    return s ? T(s.label) : code;
  }

  spaceNames(a: StoreAd): string {
    return a.placements.map((p) => this.spaceLabel(p)).join(', ');
  }

  dates(a: StoreAd): string {
    if (!a.startsOn && !a.endsOn) return T('No dates');
    if (a.startsOn && a.endsOn) return `${a.startsOn} → ${a.endsOn}`;
    return a.startsOn ? T('From {p0}', { p0: a.startsOn }) : T('Until {p0}', { p0: a.endsOn });
  }

  status(a: StoreAd): string {
    if (a.showingNow) return T('Showing');
    const advertiser = this.advertisers.find((x) => x.id === a.advertiserId);
    if (!a.active) return T('Off');
    if (advertiser && !advertiser.active) return T('Advertiser is off');
    const today = new Date().toISOString().slice(0, 10);
    if (a.startsOn && a.startsOn > today) return T('Starts {p0}', { p0: a.startsOn });
    return T('Ended');
  }

  editAd(a: StoreAd): void {
    this.editingAd = a;
    this.file = null;
    this.af = { advertiserId: a.advertiserId, title: a.title, placements: [...a.placements], linkUrl: a.linkUrl, altText: a.altText ?? '', startsOn: a.startsOn ?? '', endsOn: a.endsOn ?? '',
      active: a.active, fee: a.fee === null ? '' : String(a.fee), feeNote: a.feeNote ?? '', feePaid: a.feePaid };
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetAd(): void {
    this.editingAd = null;
    this.file = null;
    this.af = this.blankAd();
    document.querySelectorAll<HTMLInputElement>('.ad-file').forEach((i) => (i.value = ''));
  }

  saveAd(): void {
    this.error = null;
    this.message = null;
    this.busy = true;
    this.api.saveAd(this.editingAd?.id ?? null, { ...this.af, fee: this.af.fee === null ? '' : String(this.af.fee) }, this.file).subscribe({
      next: () => { this.busy = false; this.message = T('Ad saved'); this.resetAd(); this.load(); },
      error: (e) => { this.busy = false; this.fail(e, 'Could not save the ad'); },
    });
  }

  removeAd(a: StoreAd): void {
    if (!confirm(T('Delete the ad {p0}? Its counts are deleted too.', { p0: a.title }))) return;
    this.api.deleteAd(a.id).subscribe({ next: () => { this.message = T('Ad deleted'); this.load(); }, error: (e) => this.fail(e, 'Could not delete the ad') });
  }

  // ---- advertisers

  private blankAdvertiser(): StoreAdvertiserForm {
    return { name: '', contactName: '', email: '', phone: '', notes: '', active: true };
  }

  editAdvertiser(a: StoreAdvertiser): void {
    this.editingAdvertiser = a;
    this.vf = { name: a.name, contactName: a.contactName ?? '', email: a.email ?? '', phone: a.phone ?? '', notes: a.notes ?? '', active: a.active };
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetAdvertiser(): void {
    this.editingAdvertiser = null;
    this.vf = this.blankAdvertiser();
  }

  saveAdvertiser(): void {
    this.error = null;
    this.message = null;
    this.busy = true;
    this.api.saveAdvertiser(this.editingAdvertiser?.id ?? null, this.vf).subscribe({
      next: () => { this.busy = false; this.message = T('Advertiser saved'); this.resetAdvertiser(); this.load(); },
      error: (e) => { this.busy = false; this.fail(e, 'Could not save the advertiser'); },
    });
  }

  removeAdvertiser(a: StoreAdvertiser): void {
    if (!confirm(T('Delete the advertiser {p0}?', { p0: a.name }))) return;
    this.api.deleteAdvertiser(a.id).subscribe({ next: () => { this.message = T('Advertiser deleted'); this.load(); }, error: (e) => this.fail(e, 'Could not delete the advertiser') });
  }

  // ---- report

  openReport(): void {
    this.tab = 'report';
    if (!this.report) this.loadReport();
  }

  loadReport(): void {
    this.error = null;
    this.busy = true;
    this.api.adReport(this.rFrom, this.rTo, this.rAdvertiser).subscribe({
      next: (r) => { this.report = r; this.rFrom = r.from; this.rTo = r.to; this.busy = false; },
      error: (e) => { this.busy = false; this.fail(e, 'Could not load the report'); },
    });
  }

  private esc(s: string): string {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  private where(r: { byPlacement: { placement: string; views: number; clicks: number }[] }): string {
    return r.byPlacement.map((p) => `${this.spaceLabel(p.placement)} (${p.views}/${p.clicks})`).join(', ');
  }

  /** A clean page to print or save as PDF (the screen's menu and buttons stay out of it). */
  print(): void {
    const r = this.report;
    if (!r) return;
    const adv = this.rAdvertiser ? this.advertisers.find((a) => a.id === this.rAdvertiser)?.name ?? '' : T('All advertisers');
    const rows = r.rows.map((x) => `<tr><td>${this.esc(x.advertiserName)}</td><td>${this.esc(x.title)}</td><td>${this.esc(this.where(x))}</td><td class="n">${x.views}</td><td class="n">${x.clicks}</td><td class="n">${x.clickRate}%</td></tr>`).join('');
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>${this.esc(T('Advertising report'))}</title>
      <style>body{font-family:Arial,sans-serif;margin:24px;color:#111}h1{font-size:20px;margin:0 0 4px}p{margin:2px 0 12px;color:#555}table{border-collapse:collapse;width:100%;font-size:13px}
      th,td{border:1px solid #ccc;padding:6px 8px;text-align:left}th{background:#f3f4f6}.n{text-align:right}.tot td{font-weight:bold}small{color:#666}</style></head><body>
      <h1>${this.esc(T('Advertising report'))}</h1><p>${this.esc(adv)} · ${this.esc(r.from)} → ${this.esc(r.to)}</p>
      <table><thead><tr><th>${this.esc(T('Advertiser'))}</th><th>${this.esc(T('Ad'))}</th><th>${this.esc(T('Where'))} (${this.esc(T('shown'))}/${this.esc(T('clicks'))})</th><th class="n">${this.esc(T('Shown'))}</th><th class="n">${this.esc(T('Clicks'))}</th><th class="n">${this.esc(T('Click rate'))}</th></tr></thead>
      <tbody>${rows}<tr class="tot"><td colspan="3">${this.esc(T('Total'))}</td><td class="n">${r.views}</td><td class="n">${r.clicks}</td><td class="n">${r.clickRate}%</td></tr></tbody></table>
      <p><small>${this.esc(T('A visitor counts once per ad and place for half an hour, and an ad counts as shown only when it was on screen.'))}</small></p>
      <script>window.onload=function(){window.print();}</script></body></html>`;
    const w = window.open('', '_blank');
    if (!w) { this.error = T('Your browser blocked the print window. Allow pop-ups for this site and try again.'); return; }
    w.document.open();
    w.document.write(html);
    w.document.close();
  }

  csv(): void {
    const r = this.report;
    if (!r) return;
    const q = (s: string | number) => `"${String(s).replace(/"/g, '""')}"`;
    const lines = [[T('Advertiser'), T('Ad'), T('Where'), T('Shown'), T('Clicks'), T('Click rate'), T('Fee'), T('Paid')].map(q).join(',')];
    for (const x of r.rows) lines.push([x.advertiserName, x.title, this.where(x), x.views, x.clicks, x.clickRate, x.fee ?? '', x.fee === null ? '' : x.feePaid ? T('paid') : T('not paid')].map(q).join(','));
    const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `advertising-${r.from}-${r.to}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
