import { RouterLink } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { AuthService } from '../../core/auth.service';
import { PAGE_SIZE } from '../../core/api/paged';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Customer } from '../../core/models';
import { LoyaltyApiService, LoyaltyCustomerDetail, LoyaltyCustomerRow, LoyaltySettings, LoyaltySummary } from '../../core/api/loyalty-api.service';

/** Sales > Loyalty points. Customers earn points on what they spend and spend them at the till. Set the rules here, see who has points, and correct a balance by hand. */
@Component({
  selector: 'app-loyalty',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <h3>Loyalty points</h3>
      <p class="text-muted">A named customer earns points on every sale (the walk-in customer does not). Points can then pay part of a later sale at the till. Points are a cost to the shop only when they are spent.</p>
      <div class="alert alert-danger lp-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success lp-done" *ngIf="done">{{ done }}</div>

      <div class="card mb-3 lp-settings" *ngIf="s" style="padding:1rem;">
        <h4>The rules</h4>
        <div class="lp-switch-note mb-2" style="font-size:0.9rem;">
          Loyalty points are <strong>{{ s.enabled ? 'switched on' : 'switched off' }}</strong>. Switch them on or off in <a routerLink="/admin/sales/settings">Sales &gt; Settings</a>.
        </div>
        <div class="grid-2">
          <div class="form-group"><label class="form-label">A customer earns points for every (amount spent)</label><input class="form-control lp-spend" type="number" min="0" step="0.01" name="spend" [(ngModel)]="s.spendAmount" [disabled]="!canManage" /></div>
          <div class="form-group"><label class="form-label">…and gets this many points</label><input class="form-control lp-earned" type="number" min="1" step="1" name="earned" [(ngModel)]="s.pointsEarned" [disabled]="!canManage" /></div>
          <div class="form-group"><label class="form-label">One point is worth (when spent)</label><input class="form-control lp-value" type="number" min="0" step="0.01" name="value" [(ngModel)]="s.pointValue" [disabled]="!canManage" /></div>
          <div class="form-group"><label class="form-label">Fewest points that can be spent at once</label><input class="form-control lp-min" type="number" min="0" step="1" name="min" [(ngModel)]="s.minRedeemPoints" [disabled]="!canManage" /></div>
          <div class="form-group"><label class="form-label">Points can pay at most this % of one sale</label><input class="form-control lp-max" type="number" min="0" max="100" step="1" name="max" [(ngModel)]="s.maxRedeemPercent" [disabled]="!canManage" /></div>
        </div>
        <p class="text-muted" style="font-size:0.85rem;">Example: a customer who spends {{ s.spendAmount | number: '1.0-2' }} gets {{ s.pointsEarned }} point(s); 100 points are worth {{ 100 * s.pointValue | number: '1.0-2' }}.</p>
        <button class="btn btn-primary lp-save" *ngIf="canManage" (click)="save()">Save the rules</button>
      </div>

      <div class="grid-2 mb-3 lp-summary" *ngIf="summary">
        <div class="card" style="padding:0.75rem;"><div class="text-muted">Points customers hold</div>
          <div style="font-size:1.4rem; font-weight:700;" class="lp-outstanding">{{ summary.outstandingPoints | number }}</div>
          <div class="text-muted" style="font-size:0.85rem;">worth {{ summary.outstandingValue | number: '1.2-2' }} if all were spent · {{ summary.customers }} customer(s)</div></div>
      </div>

      <div class="card mb-3 lp-detail" *ngIf="detail" style="padding:1rem;">
        <div class="flex justify-between align-center">
          <h4 style="margin:0;">{{ detail.name }} — {{ detail.points }} points (worth {{ detail.value | number: '1.2-2' }})</h4>
          <button class="btn btn-link lp-detail-close" (click)="detail = null">Close</button>
        </div>
        <div class="flex gap-2 mt-2 mb-2" style="flex-wrap:wrap; align-items:end;" *ngIf="canManage">
          <div class="form-group" style="margin:0;"><label class="form-label">Add or take away points (use a minus to take away)</label><input class="form-control lp-adjust-points" type="number" step="1" name="adjPoints" [(ngModel)]="adjPoints" style="max-width:200px;" /></div>
          <div class="form-group" style="margin:0;"><label class="form-label">Reason</label><input class="form-control lp-adjust-note" name="adjNote" [(ngModel)]="adjNote" style="min-width:260px;" /></div>
          <button class="btn btn-outline-primary lp-adjust" (click)="adjust()">Change balance</button>
        </div>
        <table>
          <thead><tr><th>When</th><th>What</th><th class="text-end">Points</th><th class="text-end">Balance after</th><th>Sale</th><th>Note</th></tr></thead>
          <tbody>
            <tr *ngFor="let t of detail.history" class="lp-txn">
              <td data-label="When">{{ t.at | date: 'medium' }}</td>
              <td data-label="What">{{ typeLabel(t.type) }}</td>
              <td data-label="Points" class="text-end">{{ t.points > 0 ? '+' : '' }}{{ t.points }}</td>
              <td data-label="Balance after" class="text-end">{{ t.balanceAfter }}</td>
              <td data-label="Sale">{{ t.invoiceNumber || '' }}</td>
              <td data-label="Note">{{ t.note || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex gap-2 mb-2" style="flex-wrap:wrap; align-items:center;">
        <select class="form-control lp-pick" style="max-width:320px;" name="lpPick" [(ngModel)]="pickId" (ngModelChange)="openPicked()" *ngIf="canManage">
          <option [ngValue]="null">Open any customer (to give points)…</option>
          <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
        </select>
        <input class="form-control lp-search" style="max-width:260px;" placeholder="Search name or phone" name="lpSearch" [(ngModel)]="q" (ngModelChange)="reload()" />
      </div>
      <table>
        <thead><tr><th>Customer</th><th>Phone</th><th class="text-end">Points</th><th class="text-end">Worth</th><th>Last activity</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of rows" class="lp-row" [attr.data-name]="c.name">
            <td data-label="Customer"><strong>{{ c.name }}</strong></td>
            <td data-label="Phone">{{ c.phone || '' }}</td>
            <td data-label="Points" class="text-end lp-points">{{ c.points }}</td>
            <td data-label="Worth" class="text-end">{{ c.value | number: '1.2-2' }}</td>
            <td data-label="Last activity">{{ c.lastActivity | date: 'medium' }}</td>
            <td data-label=""><button class="btn btn-sm btn-outline-primary lp-open" (click)="open(c.customerId)">History</button></td>
          </tr>
          <tr *ngIf="rows.length === 0"><td colspan="6" class="text-muted">No customer has points yet.</td></tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goTo($event)"></app-pager>
    </app-shell>
  `,
})
export class LoyaltyComponent implements OnInit {
  s: LoyaltySettings | null = null;
  summary: LoyaltySummary | null = null;
  rows: LoyaltyCustomerRow[] = [];
  detail: LoyaltyCustomerDetail | null = null;
  error: string | null = null;
  done: string | null = null;
  q = '';
  page = 0;
  size = PAGE_SIZE;
  total = 0;
  adjPoints: number | null = null;
  adjNote = '';
  customers: Customer[] = [];
  pickId: number | null = null;

  constructor(private api: LoyaltyApiService, private auth: AuthService, private customerApi: CustomerApiService) {}

  get canManage(): boolean {
    return this.auth.canManage('SALES');
  }

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (err) => (this.error = err?.error?.message ?? 'Could not load the rules') });
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !x.walkIn)), error: () => (this.customers = []) });
    this.reload();
  }

  openPicked(): void {
    if (this.pickId != null) this.open(this.pickId);
  }

  reload(): void {
    this.page = 0;
    this.load();
  }

  goTo(p: number): void {
    this.page = p;
    this.load();
  }

  load(): void {
    this.api.customers(this.q, this.page, this.size).subscribe({
      next: (r) => {
        this.rows = r.items;
        this.total = r.total;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not load the customers'),
    });
    this.api.summary().subscribe({ next: (x) => (this.summary = x), error: () => (this.summary = null) });
  }

  typeLabel(t: string): string {
    return (
      ({ EARNED: 'Earned on a sale', REDEEMED: 'Spent on a sale', EARN_REVERSED: 'Taken back (return)', REDEEM_REFUNDED: 'Given back (return)', ADJUSTED: 'Changed by hand' } as Record<string, string>)[t] ?? t
    );
  }

  save(): void {
    if (!this.s) return;
    this.error = null;
    this.done = null;
    const s = this.s;
    this.api
      .saveSettings({
        enabled: s.enabled,
        spendAmount: Number(s.spendAmount) || 0,
        pointsEarned: Math.floor(Number(s.pointsEarned) || 0),
        pointValue: Number(s.pointValue) || 0,
        minRedeemPoints: Math.floor(Number(s.minRedeemPoints) || 0),
        maxRedeemPercent: Number(s.maxRedeemPercent) || 0,
      })
      .subscribe({
        next: (r) => {
          this.s = r;
          this.done = r.enabled ? 'Saved. Loyalty points are on.' : 'Saved. Loyalty points are off.';
          this.load();
        },
        error: (err) => (this.error = err?.error?.message ?? 'Could not save the rules'),
      });
  }

  open(customerId: number): void {
    this.error = null;
    this.api.detail(customerId).subscribe({ next: (d) => (this.detail = d), error: (err) => (this.error = err?.error?.message ?? 'Could not load the history') });
  }

  adjust(): void {
    if (!this.detail) return;
    this.error = null;
    this.done = null;
    this.api.adjust(this.detail.customerId, Math.trunc(Number(this.adjPoints) || 0), this.adjNote).subscribe({
      next: (d) => {
        this.detail = d;
        this.done = 'Balance changed. ' + d.name + ' now has ' + d.points + ' points.';
        this.adjPoints = null;
        this.adjNote = '';
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not change the balance'),
    });
  }
}
