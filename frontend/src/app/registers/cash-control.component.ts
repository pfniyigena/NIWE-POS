import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { CashMovement, CashSummary, Denomination } from '../core/models';
import { AuthService } from '../core/auth.service';

import { T } from '../core/i18n/t';
/**
 * The cash drawer of one shift: where it stands (float, cash sales, refunds, paid in, paid out, drops, expected cash), a form to record money
 * paid in or out, a drop into the safe, or a count made during the shift, and the list of what has been recorded. A manager can void a mistake.
 */
@Component({
  selector: 'app-cash-control',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Cash drawer' | translate }}<span *ngIf="sum"> — {{ sum.registerName }}</span></h3>
        <span class="flex gap-2">
          <a *ngIf="sum?.status === 'OPEN'" class="btn btn-sm btn-outline-secondary" [routerLink]="['/pos', sessionId]">{{ 'Back to the till' | translate }}</a>
          <a *ngIf="sum?.status === 'OPEN' && isManager" class="btn btn-sm btn-outline-danger" [routerLink]="['/sessions', sessionId, 'close']">{{ 'Close the shift' | translate }}</a>
          <a *ngIf="sum?.status === 'OPEN'" class="btn btn-sm btn-outline-secondary cc-xreport" [routerLink]="['/sessions', sessionId, 'x-report']">{{ 'X report' | translate }}</a>
          <a *ngIf="sum?.status === 'CLOSED'" class="btn btn-sm btn-outline-secondary" [routerLink]="['/sessions', sessionId, 'z-report']">{{ 'Z report' | translate }}</a>
        </span>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="done">{{ done }}</div>
      <ng-container *ngIf="sum">
        <div class="alert alert-warning cc-alert" *ngIf="sum.dropAlert">{{ 'The drawer holds more than {p0}. Drop some cash into the safe.' | translate: { p0: (sum.dropAlertAmount | number: '1.0-0') } }}</div>
        <div class="card mb-3" style="padding:1rem; max-width:560px;">
          <table class="table-sm">
            <tbody>
              <tr><td>{{ 'Opening float' | translate }}</td><td class="text-end">{{ sum.openingFloat | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Cash sales' | translate }}</td><td class="text-end">{{ sum.cashSales | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Cash refunds' | translate }}</td><td class="text-end">−{{ sum.cashRefunds | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Paid in' | translate }}</td><td class="text-end cc-in">{{ sum.paidIn | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Paid out' | translate }}</td><td class="text-end cc-out">−{{ sum.paidOut | number: '1.2-2' }}</td></tr>
              <tr><td>{{ 'Dropped into the safe' | translate }}</td><td class="text-end cc-drops">−{{ sum.drops | number: '1.2-2' }}</td></tr>
              <tr style="font-weight:700;"><td>{{ 'Cash that should be in the drawer' | translate }}</td><td class="text-end cc-expected">{{ sum.expectedCash | number: '1.2-2' }}</td></tr>
            </tbody>
          </table>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:560px;" *ngIf="sum.status === 'OPEN'">
          <h4 style="font-size:1rem;" class="mb-2">{{ 'Record' | translate }}</h4>
          <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
            <button *ngFor="let k of kinds" type="button" class="btn btn-sm cc-kind" [class.btn-primary]="kind === k.value" [class.btn-outline-secondary]="kind !== k.value" (click)="pick(k.value)">{{ k.label }}</button>
          </div>
          <ng-container *ngIf="kind !== 'COUNT'">
            <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
              <select class="form-select cc-reason" style="width:auto;" [(ngModel)]="reasonCode" name="reason">
                <option *ngFor="let r of reasonList()" [value]="r.code">{{ r.label }}</option>
              </select>
              <input class="form-control cc-amount" type="number" min="0" step="0.01" [placeholder]="'Amount' | translate" style="width:140px;" [(ngModel)]="amount" name="amount" />
            </div>
            <input class="form-control cc-note mb-2" maxlength="200" [placeholder]="'What was it for?' | translate" [(ngModel)]="note" name="note" />
            <input *ngIf="needsPin()" class="form-control cc-pin mb-2" type="password" inputmode="numeric" maxlength="6" style="width:200px;" [placeholder]="'Manager\\'s PIN' | translate" [(ngModel)]="pin" name="pin" />
            <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="needsPin()">{{ 'A paid-out above {p0} needs a manager\\'s PIN.' | translate: { p0: (sum.paidOutApprovalOver | number: '1.0-0') } }}</div>
          </ng-container>
          <ng-container *ngIf="kind === 'COUNT'">
            <p class="text-muted" style="font-size:0.85rem;">{{ 'Count the drawer without closing the shift. Nothing moves; the difference is kept on record.' | translate }}</p>
            <div class="flex gap-2 align-center mb-1" *ngFor="let d of denominations; let i = index">
              <input class="form-control" type="number" min="1" style="width:100px;" [(ngModel)]="d.value" [name]="'v' + i" [attr.aria-label]="'Value' | translate" />
              <span>×</span>
              <input class="form-control cc-dcount" type="number" min="0" style="width:90px;" [(ngModel)]="d.count" [name]="'c' + i" [attr.aria-label]="'How many' | translate" />
              <span class="text-muted grow text-end">{{ (d.value || 0) * (d.count || 0) | number: '1.0-2' }}</span>
            </div>
            <div class="flex justify-between mb-2" style="font-weight:700;"><span>{{ 'Counted' | translate }}</span><span class="cc-counted">{{ countTotal() | number: '1.2-2' }}</span></div>
            <input class="form-control cc-note mb-2" maxlength="200" [placeholder]="'Note (optional)' | translate" [(ngModel)]="note" name="note2" />
          </ng-container>
          <button class="btn btn-primary cc-save" type="button" [disabled]="saving" (click)="save()">{{ kind === 'COUNT' ? ('Record the count' | translate) : ('Save' | translate) }}</button>
        </div>

        <h4 style="font-size:1rem;" class="mb-2">{{ 'Recorded this shift' | translate }}</h4>
        <table class="mb-3">
          <thead>
            <tr><th>{{ 'Time' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Reason' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'By' | translate }}</th><th></th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of sum.movements" class="cc-row" [style.opacity]="m.voided ? 0.5 : 1">
              <td>{{ m.createdAt | date: 'HH:mm' }}</td>
              <td>{{ label(m.kind) }}<span *ngIf="m.voided" class="badge badge-secondary" style="margin-left:6px;" [title]="m.voidReason || ''">{{ 'voided' | translate }}</span></td>
              <td>{{ m.reason }}<span class="text-muted" *ngIf="m.note"> — {{ m.note }}</span>
                <span class="text-muted" *ngIf="m.kind === 'COUNT'"> {{ '(expected {p0}, {p1})' | translate: { p0: (m.expectedCash | number: '1.2-2'), p1: ((m.variance || 0) === 0 ? 'matches' : ((m.variance || 0) > 0 ? 'over ' : 'short ') + (abs(m.variance || 0) | number: '1.2-2')) } }}</span>
              </td>
              <td class="text-end">{{ m.kind === 'PAID_IN' || m.kind === 'COUNT' ? '' : '−' }}{{ m.amount | number: '1.2-2' }}</td>
              <td>{{ m.byUser }}<span class="text-muted" *ngIf="m.approvedBy"> {{ '(approved by {p0})' | translate: { p0: (m.approvedBy) } }}</span></td>
              <td><button *ngIf="isManager && sum.status === 'OPEN' && !m.voided && m.kind !== 'COUNT'" class="btn btn-sm btn-outline-danger cc-void" (click)="voidIt(m)">{{ 'Void' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="sum.movements.length === 0">{{ 'Nothing recorded yet on this shift.' | translate }}</p>
      </ng-container>
    </app-shell>
  `,
})
export class CashControlComponent implements OnInit {
  sessionId!: number;
  sum: CashSummary | null = null;
  reasons: Record<string, Record<string, string>> = {};
  kinds = [
    { value: 'PAID_IN', label: 'Paid in' },
    { value: 'PAID_OUT', label: 'Paid out' },
    { value: 'DROP', label: 'Drop to safe' },
    { value: 'COUNT', label: 'Count the drawer' },
  ];
  kind = 'PAID_OUT';
  reasonCode = '';
  amount: number | null = null;
  note = '';
  pin = '';
  denominations: Denomination[] = [5000, 2000, 1000, 500, 100, 50, 10, 5].map((value) => ({ value, count: 0 }));
  error: string | null = null;
  done: string | null = null;
  saving = false;
  isManager = false;

  constructor(private route: ActivatedRoute, private registerApi: RegisterApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.isManager = this.auth.isAdmin() || this.auth.canManage('SALES') || this.auth.hasPermission('APPROVE_POS_CASH') || this.auth.hasPermission('OVERRIDE_DISCOUNT_CAP');
    this.registerApi.cashReasons().subscribe((r) => {
      this.reasons = r;
      this.pick(this.kind);
    });
    this.reload();
  }

  reload(): void {
    this.registerApi.cashSummary(this.sessionId).subscribe({
      next: (s) => (this.sum = s),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load the drawer')),
    });
  }

  abs(n: number): number {
    return Math.abs(n);
  }

  label(kind: string): string {
    return this.kinds.find((k) => k.value === kind)?.label ?? kind;
  }

  reasonList(): { code: string; label: string }[] {
    return Object.entries(this.reasons[this.kind] ?? {}).map(([code, label]) => ({ code, label }));
  }

  pick(kind: string): void {
    this.kind = kind;
    this.reasonCode = this.reasonList()[0]?.code ?? '';
    this.error = null;
    this.done = null;
  }

  needsPin(): boolean {
    return !!this.sum && this.kind === 'PAID_OUT' && !this.isManager && this.sum.paidOutApprovalOver > 0 && (this.amount ?? 0) > this.sum.paidOutApprovalOver;
  }

  countTotal(): number {
    return this.denominations.reduce((sum, d) => sum + (Number(d.value) || 0) * (Number(d.count) || 0), 0);
  }

  save(): void {
    this.error = null;
    this.done = null;
    this.saving = true;
    const body: { kind: string; reasonCode?: string; amount?: number | null; note?: string; approverPin?: string; denominations?: Denomination[] } = { kind: this.kind, note: this.note };
    if (this.kind === 'COUNT') {
      body.denominations = this.denominations.filter((d) => d.value > 0 && d.count > 0).map((d) => ({ value: Number(d.value), count: Number(d.count) }));
      if (body.denominations.length === 0) body.denominations = [{ value: 1, count: 0 }];
    } else {
      body.reasonCode = this.reasonCode;
      body.amount = this.amount;
      if (this.pin) body.approverPin = this.pin;
    }
    this.registerApi.recordCash(this.sessionId, body).subscribe({
      next: (m: CashMovement) => {
        this.saving = false;
        this.done = this.kind === 'COUNT' ? 'Count recorded.' : this.label(m.kind) + ' recorded.';
        this.amount = null;
        this.note = '';
        this.pin = '';
        this.denominations.forEach((d) => (d.count = 0));
        this.reload();
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message ?? T('Could not record that');
      },
    });
  }

  voidIt(m: CashMovement): void {
    const reason = prompt('Why is this being voided?');
    if (!reason) return;
    this.error = null;
    this.registerApi.voidCash(m.id, reason).subscribe({
      next: () => this.reload(),
      error: (err) => (this.error = err?.error?.message ?? T('Could not void that')),
    });
  }
}
