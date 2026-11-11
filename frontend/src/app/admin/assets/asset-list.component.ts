import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { PagerComponent } from '../../shared/pager.component';
import { AuthService } from '../../core/auth.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { downloadBlob } from '../../core/download-file';
import { Branch } from '../../core/models';
import {
  AssetApiService, AssetBulkRequest, AssetCategory, AssetFilter, AssetStatus, AssetSummary, Custodian, DepreciationRunResult, FixedAsset,
} from '../../core/api/asset-api.service';

import { T } from '../../core/i18n/t';
/**
 * Assets > Asset register: every fixed asset with search, filters and quick views (held, nobody holding, warranty or insurance ending,
 * ending soon), several at once (move, hand out, take back, print tags), a download of what is on screen, and the "Run depreciation"
 * action (the same thing the daily scheduled job does for months that have ended).
 */
@Component({
  selector: 'app-asset-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin-bottom:0;">{{ 'Asset register' | translate }}</h3>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <a class="btn btn-outline-primary" routerLink="/admin/assets/overview">{{ 'Overview' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/assets/verifications">{{ 'Verification' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/assets/categories">{{ 'Categories' | translate }}</a>
          <a class="btn btn-outline-primary" *ngIf="canManage" routerLink="/admin/assets/from-purchase">{{ 'From a purchase' | translate }}</a>
          <a class="btn btn-primary" *ngIf="canManage" routerLink="/admin/assets/new">{{ '+ New asset' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="runMessage">{{ runMessage }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="flex gap-3 mb-3" style="flex-wrap:wrap;" *ngIf="summary">
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Assets' | translate }}</div><strong>{{ summary.assetCount }}</strong></div>
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Total cost' | translate }}</div><strong>{{ summary.totalCost | number: '1.2-2' }}</strong></div>
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Accumulated depreciation' | translate }}</div><strong>{{ summary.totalAccumulatedDepreciation | number: '1.2-2' }}</strong></div>
        <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Book value' | translate }}</div><strong>{{ summary.totalBookValue | number: '1.2-2' }}</strong></div>
      </div>

      <div class="card mb-3" style="padding:0.75rem 1rem;" *ngIf="canManage">
        <div class="flex align-center gap-3" style="flex-wrap:wrap;">
          <strong>{{ 'Run depreciation' | translate }}</strong>
          <label class="flex align-center gap-2">{{ 'Up to' | translate }}
            <app-date-picker name="upTo" [(ngModel)]="upTo"></app-date-picker>
          </label>
          <button class="btn btn-outline-primary" type="button" (click)="run()" [disabled]="running">{{ running ? ('Running…' | translate) : ('Run now' | translate) }}</button>
          <span class="text-muted" style="font-size:0.85rem;">
            {{ 'Charges every full month up to that date that has not been charged yet, and posts it to Accounting. This also runs by itself every day for months that have ended.' | translate }}
          </span>
        </div>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
        <button type="button" class="btn btn-sm" *ngFor="let v of views" [ngClass]="view === v.key ? 'btn-primary' : 'btn-outline-primary'" (click)="setView(v.key)">{{ v.label }}</button>
      </div>
      <div class="flex gap-2 mb-3 as-filters" style="flex-wrap:wrap; align-items:flex-end;">
        <input class="form-control" style="width:15rem;" [placeholder]="'Search tag, name, serial, holder, place' | translate" [(ngModel)]="q" (keyup.enter)="reload()" name="q" />
        <select class="form-select" style="width:auto; max-width:14rem;" [(ngModel)]="categoryId" (ngModelChange)="reload()" name="categoryId" [title]="'Category' | translate">
          <option [ngValue]="null">{{ 'Any category' | translate }}</option>
          <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" [(ngModel)]="status" (ngModelChange)="reload()" name="status" [title]="'Status' | translate">
          <option value="">{{ 'All assets' | translate }}</option>
          <option value="ACTIVE">{{ 'Active' | translate }}</option>
          <option value="FULLY_DEPRECIATED">{{ 'Fully depreciated' | translate }}</option>
          <option value="DISPOSED">{{ 'Disposed' | translate }}</option>
        </select>
        <select class="form-select" style="width:auto;" *ngIf="branches.length > 1" [(ngModel)]="branchId" (ngModelChange)="reload()" name="branch" [title]="'Branch' | translate">
          <option [ngValue]="null">{{ 'All branches' | translate }}</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
        <input class="form-control" style="width:10rem;" [placeholder]="'Place' | translate" [(ngModel)]="location" (keyup.enter)="reload()" name="location" />
        <input class="form-control" style="width:10rem;" [placeholder]="'Held by' | translate" [(ngModel)]="custodian" (keyup.enter)="reload()" name="custodian" />
        <button type="button" class="btn btn-outline-secondary" (click)="reload()">{{ 'Search' | translate }}</button>
        <button type="button" class="btn btn-link" (click)="clear()">{{ 'Clear' | translate }}</button>
        <span style="flex:1;"></span>
        <button type="button" class="btn btn-outline-primary" (click)="download('xlsx')" [disabled]="busy">{{ 'Excel' | translate }}</button>
        <button type="button" class="btn btn-outline-primary" (click)="download('pdf')" [disabled]="busy">{{ 'PDF' | translate }}</button>
      </div>

      <div class="card mb-3" style="padding:0.75rem 1rem;" *ngIf="canManage && selectedCount > 0">
        <div class="flex gap-2 align-center" style="flex-wrap:wrap;">
          <strong>{{ '{p0} selected' | translate: { p0: (selectedCount) } }}</strong>
          <button type="button" class="btn btn-sm btn-outline-primary" (click)="mode = mode === 'move' ? '' : 'move'">{{ 'Move…' | translate }}</button>
          <button type="button" class="btn btn-sm btn-outline-primary" (click)="mode = mode === 'out' ? '' : 'out'">{{ 'Hand out…' | translate }}</button>
          <button type="button" class="btn btn-sm btn-outline-primary" (click)="bulkIn()" [disabled]="busy">{{ 'Take back' | translate }}</button>
          <button type="button" class="btn btn-sm btn-outline-primary" (click)="printTags()">{{ 'Print tags' | translate }}</button>
          <button type="button" class="btn btn-sm btn-link" (click)="selected = {}; mode = ''">{{ 'Clear selection' | translate }}</button>
        </div>
        <div class="flex gap-3 mt-2" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="mode === 'move'">
          <div class="form-group" *ngIf="branches.length > 1"><label class="form-label">{{ 'To branch' | translate }}</label>
            <select class="form-select" name="bBranch" [(ngModel)]="bulkBranch">
              <option [ngValue]="null">{{ 'Keep the branch' | translate }}</option>
              <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'New place' | translate }}</label><input class="form-control" name="bLoc" [(ngModel)]="bulkLocation" /></div>
          <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="bDate" [(ngModel)]="bulkDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="bReason" [(ngModel)]="bulkReason" /></div>
          <button type="button" class="btn btn-primary" (click)="bulkMove()" [disabled]="busy">{{ 'Move {p0}' | translate: { p0: (selectedCount) } }}</button>
        </div>
        <div class="flex gap-3 mt-2" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="mode === 'out'">
          <div class="form-group"><label class="form-label">{{ 'A user' | translate }}</label>
            <select class="form-select" name="bUser" [(ngModel)]="bulkUser">
              <option [ngValue]="null">{{ '— or type a name —' | translate }}</option>
              <option *ngFor="let u of custodians" [ngValue]="u.id">{{ u.name }}</option>
            </select></div>
          <div class="form-group" *ngIf="!bulkUser"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="bName" [(ngModel)]="bulkName" /></div>
          <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="bDate2" [(ngModel)]="bulkDate"></app-date-picker></div>
          <button type="button" class="btn btn-primary" (click)="bulkOut()" [disabled]="busy">{{ 'Hand out {p0}' | translate: { p0: (selectedCount) } }}</button>
        </div>
      </div>
      <div class="alert alert-warning" *ngIf="skipped.length">
        <strong>{{ 'Some were left as they were:' | translate }}</strong>
        <div *ngFor="let s of skipped">{{ s.code || ('#' + s.id) }} — {{ s.reason }}</div>
      </div>

      <table class="table-sm">
        <thead>
          <tr>
            <th style="width:2rem;"><input *ngIf="canManage" type="checkbox" [checked]="allPageSelected" (change)="toggleAll($event)" [title]="'Select this page' | translate" /></th>
            <th>{{ 'Tag' | translate }}</th><th>{{ 'Asset' | translate }}</th><th>{{ 'Category' | translate }}</th><th>{{ 'Purchased' | translate }}</th>
            <th class="text-end">{{ 'Cost' | translate }}</th><th class="text-end">{{ 'Accumulated' | translate }}</th><th class="text-end">{{ 'Book value' | translate }}</th>
            <th *ngIf="branches.length > 1">{{ 'Branch' | translate }}</th><th>{{ 'Place' | translate }}</th><th>{{ 'Held by' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let a of pageRows">
            <td><input *ngIf="canManage" type="checkbox" [checked]="!!selected[a.id]" (change)="toggle(a.id, $event)" /></td>
            <td><a [routerLink]="['/admin/assets', a.id]">{{ a.code }}</a> <span [title]="'Files attached' | translate" *ngIf="a.fileCount">📎{{ a.fileCount }}</span></td>
            <td>{{ a.name }}
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="a.serialNumber">{{ 'S/N {p0}' | translate: { p0: (a.serialNumber) } }}</div></td>
            <td>{{ a.categoryName }}</td>
            <td>{{ a.purchaseDate }}</td>
            <td class="text-end">{{ a.cost | number: '1.2-2' }}</td>
            <td class="text-end">{{ a.accumulatedDepreciation | number: '1.2-2' }}</td>
            <td class="text-end">{{ a.bookValue | number: '1.2-2' }}</td>
            <td *ngIf="branches.length > 1">{{ a.branchName || '—' }}</td>
            <td>{{ a.location || '—' }}</td>
            <td>{{ a.custodianName || '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="a.status === 'ACTIVE'">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="a.status === 'FULLY_DEPRECIATED'">{{ 'Fully depreciated' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="a.status === 'DISPOSED'">{{ 'Disposed' | translate }}</span>
              <span class="badge badge-warning" *ngIf="a.warrantyState === 'EXPIRING'" title="Warranty ends {{ a.warrantyExpiresOn }}">{{ 'Warranty ending' | translate }}</span>
              <span class="badge badge-danger" *ngIf="a.warrantyState === 'EXPIRED'" title="Warranty ended {{ a.warrantyExpiresOn }}">{{ 'Warranty ended' | translate }}</span>
              <span class="badge badge-warning" *ngIf="a.insuranceState === 'EXPIRING'" title="Insurance ends {{ a.insuranceExpiresOn }}">{{ 'Insurance ending' | translate }}</span>
              <span class="badge badge-danger" *ngIf="a.insuranceState === 'EXPIRED'" title="Insurance ended {{ a.insuranceExpiresOn }}">{{ 'Insurance ended' | translate }}</span>
            </td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/assets', a.id]">{{ 'Open' | translate }}</a></td>
          </tr>
          <tr *ngIf="!assets.length && !loading"><td [attr.colspan]="branches.length > 1 ? 13 : 12" class="text-muted">
            {{ filtered ? ('Nothing matches these filters.' | translate) : ('No assets yet — add the asset categories first (with their depreciation terms), then register your equipment, vehicles and furniture.' | translate) }}
          </td></tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="assets.length" (pageChange)="page = $event"></app-pager>
    </app-shell>
  `,
})
export class AssetListComponent implements OnInit {
  assets: FixedAsset[] = [];
  summary: AssetSummary | null = null;
  categories: AssetCategory[] = [];
  custodians: Custodian[] = [];
  branches: Branch[] = [];
  error: string | null = null;
  notice: string | null = null;
  runMessage: string | null = null;
  running = false;
  busy = false;
  loading = false;
  upTo = AssetListComponent.endOfLastMonth();
  skipped: { id: number; code: string | null; reason: string }[] = [];

  readonly views = [
    { key: '', label: T('All') },
    { key: 'held', label: T('Held by someone') },
    { key: 'no-custodian', label: T('Nobody holding') },
    { key: 'warranty-expiring', label: T('Warranty ending') },
    { key: 'insurance-expiring', label: T('Insurance ending') },
    { key: 'expired', label: T('Warranty or insurance ended') },
    { key: 'soon', label: T('Fully depreciated soon') },
  ];
  view = '';
  q = '';
  status: AssetStatus | '' = '';
  categoryId: number | null = null;
  branchId: number | null = null;
  location = '';
  custodian = '';
  page = 0;
  size = 25;
  selected: Record<number, boolean> = {};
  mode: '' | 'move' | 'out' = '';
  bulkBranch: number | null = null;
  bulkLocation = '';
  bulkReason = '';
  bulkUser: number | null = null;
  bulkName = '';
  bulkDate = new Date().toISOString().slice(0, 10);

  constructor(private api: AssetApiService, private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('ACCOUNTING');
  }

  get filtered(): boolean {
    return !!(this.view || this.q || this.status || this.categoryId || this.branchId || this.location || this.custodian);
  }

  get pageRows(): FixedAsset[] {
    return this.assets.slice(this.page * this.size, (this.page + 1) * this.size);
  }

  get selectedCount(): number {
    return this.selectedIds().length;
  }

  get allPageSelected(): boolean {
    return this.pageRows.length > 0 && this.pageRows.every((a) => this.selected[a.id]);
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.categories().subscribe({ next: (c) => (this.categories = c), error: () => {} });
    this.api.custodians().subscribe({ next: (c) => (this.custodians = c), error: () => {} });
    const v = this.route.snapshot.queryParamMap.get('view');
    if (v) this.view = v;
    this.reload();
  }

  private filter(): AssetFilter {
    return { view: this.view, q: this.q.trim(), status: this.status, categoryId: this.categoryId, branchId: this.branchId, location: this.location.trim(), custodian: this.custodian.trim() };
  }

  setView(v: string): void {
    this.view = v;
    this.reload();
  }

  clear(): void {
    this.view = '';
    this.q = '';
    this.status = '';
    this.categoryId = null;
    this.branchId = null;
    this.location = '';
    this.custodian = '';
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = null;
    this.selected = {};
    this.page = 0;
    this.api.find(this.filter()).subscribe({
      next: (a) => {
        this.assets = a;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.error = e?.error?.message ?? T('Failed to load assets');
      },
    });
    this.api.summary(this.branchId).subscribe({ next: (s) => (this.summary = s), error: () => {} });
  }

  private selectedIds(): number[] {
    return Object.keys(this.selected).filter((k) => this.selected[+k]).map(Number);
  }

  toggle(id: number, ev: Event): void {
    this.selected = { ...this.selected, [id]: (ev.target as HTMLInputElement).checked };
  }

  toggleAll(ev: Event): void {
    const on = (ev.target as HTMLInputElement).checked;
    const next = { ...this.selected };
    this.pageRows.forEach((a) => (next[a.id] = on));
    this.selected = next;
  }

  private send(req: AssetBulkRequest, done: string): void {
    this.busy = true;
    this.notice = null;
    this.skipped = [];
    this.api.bulk(req).subscribe({
      next: (r) => {
        this.busy = false;
        this.notice = r.done + ' asset(s) ' + done + '.';
        this.skipped = r.skipped;
        this.mode = '';
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not complete that');
      },
    });
  }

  bulkMove(): void {
    if (!this.bulkLocation.trim() && !this.bulkBranch) {
      this.error = T('Choose a branch or type the new place.');
      return;
    }
    this.send({ action: 'TRANSFER', ids: this.selectedIds(), toBranchId: this.bulkBranch, toLocation: this.bulkLocation.trim() || null, date: this.bulkDate, reason: this.bulkReason.trim() || null }, 'moved');
  }

  bulkOut(): void {
    if (!this.bulkUser && !this.bulkName.trim()) {
      this.error = T('Choose a user or type a name.');
      return;
    }
    this.send({ action: 'CHECK_OUT', ids: this.selectedIds(), custodianUserId: this.bulkUser, custodianName: this.bulkUser ? null : this.bulkName.trim(), date: this.bulkDate }, 'handed out');
  }

  bulkIn(): void {
    if (!confirm(T('Take back {p0} asset(s) from whoever holds them?', { p0: this.selectedCount }))) return;
    this.send({ action: 'CHECK_IN', ids: this.selectedIds(), date: this.bulkDate }, 'taken back');
  }

  printTags(): void {
    this.router.navigate(['/admin/assets/labels'], { queryParams: { ids: this.selectedIds().join(',') } });
  }

  download(format: 'xlsx' | 'pdf'): void {
    this.busy = true;
    this.api.registerExport(format, this.filter()).subscribe({
      next: (b) => {
        this.busy = false;
        downloadBlob(b, `asset-register.${format}`);
      },
      error: () => {
        this.busy = false;
        this.error = T('Could not download the register');
      },
    });
  }

  run(): void {
    this.error = this.runMessage = null;
    this.running = true;
    this.api.runDepreciation(this.upTo || null).subscribe({
      next: (r: DepreciationRunResult) => {
        this.running = false;
        this.runMessage = r.entriesCreated
          ? `Depreciation posted up to ${r.upTo}: ${r.entriesCreated} charge(s) over ${r.monthsPosted} month(s), total ${r.totalAmount}.`
          : `Nothing to charge up to ${r.upTo} — depreciation is already up to date.`;
        this.reload();
      },
      error: (e) => {
        this.running = false;
        this.error = e?.error?.message ?? T('Could not run depreciation');
      },
    });
  }

  private static endOfLastMonth(): string {
    const d = new Date();
    const last = new Date(d.getFullYear(), d.getMonth(), 0);
    const mm = String(last.getMonth() + 1).padStart(2, '0');
    const dd = String(last.getDate()).padStart(2, '0');
    return `${last.getFullYear()}-${mm}-${dd}`;
  }
}
