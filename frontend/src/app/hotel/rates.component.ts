import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { HotelApiService, RateCalendar, RatePlan, RateRule, RateRulePayload, RoomType } from '../core/api/hotel-api.service';
import { errMsg } from './hotel-util';

const DAYS = [
  { code: 'MON', label: 'Mon' }, { code: 'TUE', label: 'Tue' }, { code: 'WED', label: 'Wed' }, { code: 'THU', label: 'Thu' },
  { code: 'FRI', label: 'Fri' }, { code: 'SAT', label: 'Sat' }, { code: 'SUN', label: 'Sun' },
];

/** Hotel > Rates: seasonal rules (dates, weekdays, room type, min stay), rate plans (discounts) and a price calendar preview. */
@Component({
  selector: 'app-hotel-rates',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-2">Rates</h3>
      <p class="text-muted" style="font-size:0.9rem;">A room type's own rate is the standard price. Seasonal rules change it for a date range (and optionally certain weekdays); the rule with the highest priority wins. Rate plans give a discount on top. Existing bookings keep their price.</p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="flex justify-between align-center mb-2">
        <h4>Seasonal rules</h4>
        <button class="btn btn-primary" *ngIf="auth.canManage('HOTEL')" (click)="newRule()">+ Add rule</button>
      </div>
      <form *ngIf="rule" class="card rule-form" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="saveRule()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Name</label><input class="form-control" name="rn" [(ngModel)]="rule.name" required placeholder="High season" /></div>
          <div class="form-group"><label class="form-label">Room type</label>
            <select class="form-control" name="rt" [(ngModel)]="rule.roomTypeId"><option [ngValue]="null">All room types</option><option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}</option></select></div>
          <div class="form-group"><label class="form-label">From</label><input class="form-control" type="date" name="rs" [(ngModel)]="rule.startDate" required /></div>
          <div class="form-group"><label class="form-label">Until (last night)</label><input class="form-control" type="date" name="re" [(ngModel)]="rule.endDate" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">Price is</label>
            <select class="form-control" name="rm" [(ngModel)]="rule.mode">
              <option value="FIXED">A fixed nightly price</option><option value="PERCENT">A % change</option><option value="AMOUNT">An amount added or taken off</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ rule.mode === 'FIXED' ? 'Price' : rule.mode === 'PERCENT' ? 'Percent (+/-)' : 'Amount (+/-)' }}</label>
            <input class="form-control" style="max-width:140px;" type="number" step="0.01" name="rv" [(ngModel)]="rule.value" required /></div>
          <div class="form-group"><label class="form-label">Min nights</label><input class="form-control" style="max-width:100px;" type="number" min="1" name="rmn" [(ngModel)]="rule.minNights" /></div>
          <div class="form-group"><label class="form-label">Priority</label><input class="form-control" style="max-width:100px;" type="number" name="rp" [(ngModel)]="rule.priority" /></div>
          <label class="mb-2"><input type="checkbox" name="ra" [(ngModel)]="rule.active" /> Active</label>
        </div>
        <div class="mb-2"><span class="form-label" style="margin-right:0.5rem;">Only on</span>
          <label *ngFor="let d of days" style="margin-right:0.6rem;"><input type="checkbox" [checked]="hasDay(d.code)" (change)="toggleDay(d.code)" [attr.data-day]="d.code" /> {{ d.label }}</label>
          <span class="text-muted" style="font-size:0.8rem;">(none ticked = every day)</span></div>
        <button class="btn btn-primary" type="submit" [disabled]="busy">Save rule</button>
        <button class="btn btn-link" type="button" (click)="rule = null">Cancel</button>
      </form>
      <table class="table-sm rules-table">
        <thead><tr><th>Rule</th><th>Room type</th><th>Dates</th><th>Price</th><th>Priority</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of rules" [class.text-muted]="!r.active">
            <td><strong>{{ r.name }}</strong><div class="badge badge-warning" *ngIf="!r.active">Off</div><div style="font-size:0.8rem;" *ngIf="r.daysOfWeek">{{ r.daysOfWeek }}</div><div style="font-size:0.8rem;" *ngIf="r.minNights > 1">min {{ r.minNights }} nights</div></td>
            <td>{{ r.roomTypeName || 'All' }}</td>
            <td>{{ r.startDate }} → {{ r.endDate || 'open' }}</td>
            <td>{{ describe(r) }}</td>
            <td>{{ r.priority }}</td>
            <td class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
              <button class="btn btn-sm btn-outline-primary" (click)="editRule(r)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" (click)="removeRule(r)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="loaded && rules.length === 0">No seasonal rules yet. Every night uses the room type's own rate.</p>

      <div class="flex justify-between align-center mb-2" style="margin-top:2rem;">
        <h4>Rate plans</h4>
        <button class="btn btn-primary" *ngIf="auth.canManage('HOTEL')" (click)="newPlan()">+ Add plan</button>
      </div>
      <form *ngIf="plan" class="card plan-form" style="padding:1rem; margin-bottom:1rem;" (ngSubmit)="savePlan()">
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">Plan name</label><input class="form-control" name="pn" [(ngModel)]="plan.name" required placeholder="Corporate" /></div>
          <div class="form-group"><label class="form-label">Percent (+/-)</label><input class="form-control" style="max-width:120px;" type="number" step="0.01" name="pp" [(ngModel)]="plan.adjustPercent" /></div>
          <div class="form-group"><label class="form-label">Amount (+/-)</label><input class="form-control" style="max-width:120px;" type="number" step="0.01" name="pa" [(ngModel)]="plan.adjustAmount" /></div>
          <div class="form-group"><label class="form-label">Min nights</label><input class="form-control" style="max-width:100px;" type="number" min="1" name="pm" [(ngModel)]="plan.minNights" /></div>
          <label class="mb-2"><input type="checkbox" name="pac" [(ngModel)]="plan.active" /> Active</label>
        </div>
        <div class="form-group"><label class="form-label">Description</label><input class="form-control" name="pd" [(ngModel)]="plan.description" /></div>
        <button class="btn btn-primary" type="submit" [disabled]="busy">Save plan</button>
        <button class="btn btn-link" type="button" (click)="plan = null">Cancel</button>
      </form>
      <table class="table-sm plans-table">
        <thead><tr><th>Plan</th><th>Adjustment</th><th>Min nights</th><th>Bookings</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let p of plans" [class.text-muted]="!p.active">
            <td><strong>{{ p.name }}</strong><div class="badge badge-warning" *ngIf="!p.active">Off</div><div style="font-size:0.8rem;" *ngIf="p.description">{{ p.description }}</div></td>
            <td>{{ p.adjustPercent }}% <span *ngIf="p.adjustAmount">/ {{ p.adjustAmount | number: '1.2-2' }}</span></td>
            <td>{{ p.minNights }}</td>
            <td>{{ p.bookings }}</td>
            <td class="flex gap-2" *ngIf="auth.canManage('HOTEL')">
              <button class="btn btn-sm btn-outline-primary" (click)="editPlan(p)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" (click)="removePlan(p)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="loaded && plans.length === 0">No rate plans yet.</p>

      <h4 style="margin-top:2rem;" class="mb-2">Price calendar</h4>
      <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
        <div class="form-group"><label class="form-label">Room type</label>
          <select class="form-control cal-type" name="ct" [(ngModel)]="calType"><option *ngFor="let t of types" [ngValue]="t.id">{{ t.name }}</option></select></div>
        <div class="form-group"><label class="form-label">From</label><input class="form-control cal-from" type="date" name="cf" [(ngModel)]="calFrom" /></div>
        <div class="form-group"><label class="form-label">Days</label><select class="form-control cal-days" name="cd" [(ngModel)]="calDays"><option [ngValue]="14">14</option><option [ngValue]="31">31</option><option [ngValue]="62">62</option></select></div>
        <div class="form-group"><label class="form-label">Plan</label>
          <select class="form-control cal-plan" name="cp" [(ngModel)]="calPlan"><option [ngValue]="null">Standard</option><option *ngFor="let p of plans" [ngValue]="p.id">{{ p.name }}</option></select></div>
        <button class="btn btn-outline-primary cal-go" type="button" (click)="loadCalendar()">Show</button>
      </div>
      <div *ngIf="cal">
        <p class="text-muted" style="font-size:0.85rem;">{{ cal.roomTypeName }} — standard rate {{ cal.baseRate | number: '1.2-2' }} (one-night price per day)</p>
        <div class="cal-grid">
          <div *ngFor="let d of cal.days" class="cal-cell" [class.cal-diff]="d.rate !== cal.baseRate" [attr.title]="d.ruleName || 'Standard'">
            <div style="font-size:0.7rem;" class="text-muted">{{ d.date | date: 'EEE d MMM' }}</div>
            <strong>{{ d.rate | number: '1.0-2' }}</strong>
            <div style="font-size:0.7rem;">{{ d.ruleName || '' }}</div>
          </div>
        </div>
      </div>
    </app-shell>
  `,
  styles: [`
    .cal-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap:0.4rem; }
    .cal-cell { border:1px solid var(--border-color, #ddd); border-radius:6px; padding:0.4rem; min-height:4rem; }
    .cal-diff { background: rgba(255, 193, 7, 0.15); }
  `],
})
export class HotelRatesComponent implements OnInit {
  days = DAYS;
  types: RoomType[] = [];
  rules: RateRule[] = [];
  plans: RatePlan[] = [];
  loaded = false;
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  rule: (RateRulePayload & { id?: number }) | null = null;
  plan: any = null;
  cal: RateCalendar | null = null;
  calType: number | null = null;
  calFrom = new Date().toISOString().slice(0, 10);
  calDays = 14;
  calPlan: number | null = null;

  constructor(private api: HotelApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.api.roomTypes().subscribe({ next: (t) => { this.types = t; if (t.length) this.calType = t[0].id; }, error: () => {} });
    this.reload();
  }

  reload(): void {
    this.api.rateRules().subscribe({ next: (r) => { this.rules = r; this.loaded = true; }, error: (e) => (this.error = errMsg(e, 'Failed to load rules')) });
    this.api.ratePlans().subscribe({ next: (p) => (this.plans = p), error: (e) => (this.error = errMsg(e, 'Failed to load plans')) });
  }

  describe(r: RateRule): string {
    return r.mode === 'FIXED' ? `${r.value.toFixed(2)} per night` : r.mode === 'PERCENT' ? `${r.value > 0 ? '+' : ''}${r.value}%` : `${r.value > 0 ? '+' : ''}${r.value.toFixed(2)} per night`;
  }

  // ---- rules
  newRule(): void {
    this.rule = { name: '', roomTypeId: null, startDate: '', endDate: null, daysOfWeek: null, mode: 'PERCENT', value: 0, minNights: 1, priority: 0, active: true };
  }
  editRule(r: RateRule): void {
    this.rule = { id: r.id, name: r.name, roomTypeId: r.roomTypeId, startDate: r.startDate, endDate: r.endDate, daysOfWeek: r.daysOfWeek, mode: r.mode, value: r.value, minNights: r.minNights, priority: r.priority, active: r.active };
  }
  hasDay(c: string): boolean {
    return (this.rule?.daysOfWeek ?? '').split(',').includes(c);
  }
  toggleDay(c: string): void {
    if (!this.rule) return;
    const set = new Set((this.rule.daysOfWeek ?? '').split(',').filter((x) => x));
    set.has(c) ? set.delete(c) : set.add(c);
    this.rule.daysOfWeek = DAYS.map((d) => d.code).filter((x) => set.has(x)).join(',') || null;
  }
  saveRule(): void {
    if (!this.rule) return;
    const r = this.rule;
    this.busy = true; this.error = null; this.notice = null;
    const payload: RateRulePayload = { name: r.name, roomTypeId: r.roomTypeId, startDate: r.startDate, endDate: r.endDate || null, daysOfWeek: r.daysOfWeek, mode: r.mode, value: Number(r.value), minNights: Number(r.minNights) || 1, priority: Number(r.priority) || 0, active: r.active };
    this.api.saveRateRule(r.id ?? null, payload).subscribe({
      next: () => { this.busy = false; this.rule = null; this.notice = 'Rule saved. Existing bookings keep their price.'; this.reload(); },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Failed to save the rule'); },
    });
  }
  removeRule(r: RateRule): void {
    if (!confirm(`Delete the rule "${r.name}"?`)) return;
    this.api.deleteRateRule(r.id).subscribe({ next: () => { this.notice = 'Rule deleted.'; this.reload(); }, error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }

  // ---- plans
  newPlan(): void {
    this.plan = { name: '', description: '', adjustPercent: 0, adjustAmount: 0, minNights: 1, active: true };
  }
  editPlan(p: RatePlan): void {
    this.plan = { ...p };
  }
  savePlan(): void {
    const p = this.plan;
    this.busy = true; this.error = null; this.notice = null;
    const payload = { name: p.name, description: p.description || null, adjustPercent: Number(p.adjustPercent) || 0, adjustAmount: Number(p.adjustAmount) || 0, minNights: Number(p.minNights) || 1, active: p.active };
    this.api.saveRatePlan(p.id ?? null, payload).subscribe({
      next: () => { this.busy = false; this.plan = null; this.notice = 'Plan saved.'; this.reload(); },
      error: (e) => { this.busy = false; this.error = errMsg(e, 'Failed to save the plan'); },
    });
  }
  removePlan(p: RatePlan): void {
    if (!confirm(`Delete the plan "${p.name}"?`)) return;
    this.api.deleteRatePlan(p.id).subscribe({ next: () => { this.notice = 'Plan deleted.'; this.reload(); }, error: (e) => (this.error = errMsg(e, 'Failed to delete')) });
  }

  // ---- calendar
  loadCalendar(): void {
    if (!this.calType || !this.calFrom) return;
    this.error = null;
    const to = new Date(this.calFrom + 'T00:00:00Z');
    to.setUTCDate(to.getUTCDate() + this.calDays - 1);
    this.api.rateCalendar(this.calType, this.calFrom, to.toISOString().slice(0, 10), this.calPlan).subscribe({
      next: (c) => (this.cal = c),
      error: (e) => (this.error = errMsg(e, 'Failed to load the calendar')),
    });
  }
}
