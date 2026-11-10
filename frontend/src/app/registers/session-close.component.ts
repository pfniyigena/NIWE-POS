import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { CashSummary, Denomination, PosSettings, RegisterSession, SessionReport } from '../core/models';

/**
 * Closing a shift: the drawer is counted, either as one typed total or note by note and coin by coin, and compared with what it should hold
 * (float + cash sales - cash refunds + paid in - paid out - safe drops). A difference needs a note when the rules in POS settings say so.
 */
@Component({
  selector: 'app-session-close',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <ng-container *ngIf="session">
        <h3 class="mb-1">{{ 'Close session — {p0}' | translate: { p0: (session.registerName) } }}</h3>
        <p class="text-muted">{{ 'Opened {p0} by {p1}' | translate: { p0: (session.openedAt | date: 'yyyy-MM-dd HH:mm'), p1: (session.cashierName) } }}</p>

        <table class="table-sm mb-3" *ngIf="report">
          <tbody>
            <tr>
              <td>{{ 'Opening float' | translate }}</td>
              <td class="text-end">{{ session.openingFloat | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>{{ 'Cash sales' | translate }}</td>
              <td class="text-end">{{ report.cashIn | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>{{ 'Cash refunds' | translate }}</td>
              <td class="text-end">−{{ report.cashRefundsOut | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="report.paidIn">
              <td>{{ 'Paid in' | translate }}</td>
              <td class="text-end">{{ report.paidIn | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="report.paidOut">
              <td>{{ 'Paid out' | translate }}</td>
              <td class="text-end">−{{ report.paidOut | number: '1.2-2' }}</td>
            </tr>
            <tr *ngIf="report.drops">
              <td>{{ 'Dropped into the safe' | translate }}</td>
              <td class="text-end">−{{ report.drops | number: '1.2-2' }}</td>
            </tr>
            <tr style="font-weight:700;">
              <td>{{ 'Expected cash in drawer' | translate }}</td>
              <td class="text-end sc-expected">{{ report.expectedCash | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>{{ 'Non-cash sales (card/mobile/bank)' | translate }}</td>
              <td class="text-end">{{ report.nonCashIn | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>{{ 'Total sales this session' | translate }}</td>
              <td class="text-end">{{ '{p0} sale(s), {p1}' | translate: { p0: (report.saleCount), p1: (report.grossSales | number: '1.2-2') } }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" style="font-size:0.85rem;"><a [routerLink]="['/cash-control', sessionId]">{{ 'Cash in and out of the drawer' | translate }}</a> {{ '— record money paid in or out, or drops into the safe, before counting.' | translate }}</p>

        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <form (ngSubmit)="submit()">
          <div class="form-group">
            <div class="flex justify-between align-center">
              <label class="form-label">{{ 'Count the drawer' | translate }}</label>
              <button type="button" class="btn btn-sm btn-outline-secondary sc-toggle" (click)="byNotes = !byNotes">{{ byNotes ? ('Type one total instead' | translate) : ('Count notes and coins' | translate) }}</button>
            </div>
            <div *ngIf="byNotes" class="sc-notes">
              <div class="flex gap-2 align-center mb-1" *ngFor="let d of denominations; let i = index">
                <input class="form-control sc-value" type="number" min="1" style="width:110px;" [(ngModel)]="d.value" [name]="'v' + i" [attr.aria-label]="'Note or coin value' | translate" />
                <span>×</span>
                <input class="form-control sc-count" type="number" min="0" style="width:90px;" [(ngModel)]="d.count" [name]="'c' + i" [attr.aria-label]="'How many' | translate" />
                <span class="text-muted grow text-end">{{ (d.value || 0) * (d.count || 0) | number: '1.0-2' }}</span>
                <button type="button" class="btn btn-sm btn-outline-secondary" (click)="denominations.splice(i, 1)" [title]="'Remove this row' | translate">×</button>
              </div>
              <button type="button" class="btn btn-sm btn-outline-secondary mb-2 sc-addrow" (click)="denominations.push({ value: 0, count: 0 })">{{ '+ Another value' | translate }}</button>
              <div class="flex justify-between" style="font-weight:700;"><span>{{ 'Counted' | translate }}</span><span class="sc-total">{{ notesTotal() | number: '1.2-2' }}</span></div>
            </div>
            <input *ngIf="!byNotes" class="form-control sc-counted" type="number" step="0.01" name="countedCash" [(ngModel)]="countedCash" />
          </div>
          <p *ngIf="report" class="sc-diff" [class.text-danger]="difference() < 0" [style.font-weight]="700">
            {{ difference() === 0 ? 'The count matches.' : (difference() > 0 ? 'Over by ' : 'Short by ') + (abs(difference()) | number: '1.2-2') }}
          </p>
          <div class="form-group" *ngIf="difference() !== 0">
            <label class="form-label">{{ 'Why is the count off?{p0}' | translate: { p0: (noteNeeded() ? ' (required)' : ' (optional)') } }}</label>
            <input class="form-control sc-note" maxlength="300" name="varianceNote" [(ngModel)]="varianceNote" [placeholder]="'For example: change given wrongly, to be checked' | translate" />
          </div>
          <button class="btn btn-danger sc-close" type="submit">{{ 'Close session' | translate }}</button>
        </form>
      </ng-container>
    </app-shell>
  `,
})
export class SessionCloseComponent implements OnInit {
  sessionId!: number;
  session: RegisterSession | null = null;
  report: SessionReport | null = null;
  settings: PosSettings | null = null;
  countedCash = 0;
  varianceNote = '';
  byNotes = false;
  denominations: Denomination[] = [5000, 2000, 1000, 500, 100, 50, 10, 5].map((value) => ({ value, count: 0 }));
  error: string | null = null;

  constructor(private route: ActivatedRoute, private router: Router, private registerApi: RegisterApiService) {}

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.registerApi.getSession(this.sessionId).subscribe((s) => (this.session = s));
    this.registerApi.getSessionReport(this.sessionId).subscribe((r) => (this.report = r));
    this.registerApi.posSettings().subscribe({
      next: (s) => {
        this.settings = s;
        if (s.denominationCountRequired) this.byNotes = true;
      },
      error: () => {},
    });
  }

  abs(n: number): number {
    return Math.abs(n);
  }

  notesTotal(): number {
    return this.denominations.reduce((sum, d) => sum + (Number(d.value) || 0) * (Number(d.count) || 0), 0);
  }

  counted(): number {
    return this.byNotes ? this.notesTotal() : Number(this.countedCash) || 0;
  }

  difference(): number {
    if (!this.report) return 0;
    return Math.round((this.counted() - this.report.expectedCash) * 100) / 100;
  }

  noteNeeded(): boolean {
    const s = this.settings;
    return !!s && s.varianceNoteRequired && Math.abs(this.difference()) > s.varianceTolerance;
  }

  submit(): void {
    this.error = null;
    const body: { countedCash?: number | null; denominations?: Denomination[]; varianceNote?: string } = { varianceNote: this.varianceNote };
    if (this.byNotes) {
      body.denominations = this.denominations.filter((d) => d.value > 0 && d.count > 0).map((d) => ({ value: Number(d.value), count: Number(d.count) }));
      if (body.denominations.length === 0) body.denominations = [{ value: 1, count: 0 }];
    } else {
      body.countedCash = Number(this.countedCash);
    }
    this.registerApi.closeSession(this.sessionId, body).subscribe({
      // Task: "Add print session report (Z-report) same we print receipt" — auto-opens the print
      // dialog on arrival, same ?autoPrint=1 convention checkout/return-form already use.
      next: () => this.router.navigate(['/sessions', this.sessionId, 'z-report'], { queryParams: { autoPrint: '1' } }),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to close session'),
    });
  }
}
