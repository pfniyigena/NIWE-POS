import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { LawApiService, TrustReconciliationView } from '../../core/api/law-api.service';
import { showPdf } from '../../core/open-pdf';

/**
 * Law Firm > Client money > Reconcile: the three figures that must agree - the bank statement (after deposits in transit and payments not yet
 * cleared), the client ledgers added together, and what the general ledger says (trust bank account and client money held).
 */
@Component({
  selector: 'app-law-trust-reconcile',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Reconcile the trust account' | translate }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/law/trust">{{ 'Back to client money' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!v && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

      <ng-container *ngIf="v">
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.6rem 0.9rem; min-width:12rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Client ledgers (all clients)' | translate }}</div><strong>{{ v.ledgerTotal | number: '1.0-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;" *ngIf="v.pendingOut">{{ '{p0} waiting to be paid out' | translate: { p0: (v.pendingOut | number: '1.0-2') } }}</div></div>
          <div class="card" style="padding:0.6rem 0.9rem; min-width:12rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Trust bank account (accounts)' | translate }}</div><strong>{{ v.glBank | number: '1.0-2' }}</strong></div>
          <div class="card" style="padding:0.6rem 0.9rem; min-width:12rem;"><div class="text-muted" style="font-size:0.8rem;">{{ 'Client money held (accounts)' | translate }}</div><strong>{{ v.glLiability | number: '1.0-2' }}</strong></div>
        </div>
        <div class="alert alert-warning" *ngIf="v.glBank !== v.ledgerTotal || v.glLiability !== v.ledgerTotal">{{ 'The accounts and the client ledgers do not agree. Ask your accountant to look at the journal entries for the trust account.' | translate }}</div>

        <form class="card mb-3" style="padding:1rem;" (ngSubmit)="save()">
          <h4 class="mb-2">{{ 'New reconciliation' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div class="form-group" style="min-width:9rem;"><label class="form-label">{{ 'Statement date' | translate }}</label><app-date-picker name="ad" [(ngModel)]="f.asOfDate"></app-date-picker></div>
            <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Closing balance on the bank statement' | translate }}</label><input class="form-control" type="number" step="0.01" name="sb" [(ngModel)]="f.statementBalance" /></div>
            <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Deposits not yet on the statement' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="dt" [(ngModel)]="f.depositsInTransit" /></div>
            <div class="form-group" style="min-width:12rem;"><label class="form-label">{{ 'Payments not yet cleared' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="op" [(ngModel)]="f.outstandingPayments" /></div>
          </div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="nt" [(ngModel)]="f.note" /></div>
          <div class="mb-2" *ngIf="f.statementBalance != null">{{ 'Adjusted bank balance' | translate }} <strong>{{ adjusted() | number: '1.0-2' }}</strong> {{ 'against client ledgers' | translate }} <strong>{{ v.ledgerTotal | number: '1.0-2' }}</strong>:
            <strong [style.color]="diff() === 0 ? '#27ae60' : '#c0392b'">{{ diff() === 0 ? 'they agree' : 'difference ' + (diff() | number: '1.0-2') }}</strong></div>
          <div><button class="btn btn-primary" type="submit" [disabled]="busy || f.statementBalance == null">{{ 'Save the reconciliation' | translate }}</button></div>
        </form>

        <div class="card" style="padding:1rem; overflow-x:auto;">
          <h4 class="mb-2">{{ 'Earlier reconciliations' | translate }}</h4>
          <table class="stack table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th class="text-end">{{ 'Statement' | translate }}</th><th class="text-end">{{ 'In transit' | translate }}</th><th class="text-end">{{ 'Not cleared' | translate }}</th><th class="text-end">{{ 'Adjusted' | translate }}</th><th class="text-end">{{ 'Ledgers' | translate }}</th><th class="text-end">{{ 'Accounts (bank / held)' | translate }}</th><th class="text-end">{{ 'Difference' | translate }}</th><th>{{ 'Result' | translate }}</th><th>{{ 'By' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let h of v.history">
                <td data-label="Date">{{ h.asOfDate | date: 'd MMM y' }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="h.note">{{ h.note }}</div></td>
                <td class="text-end" data-label="Statement">{{ h.statementBalance | number: '1.0-2' }}</td><td class="text-end" data-label="In transit">{{ h.depositsInTransit | number: '1.0-2' }}</td><td class="text-end" data-label="Not cleared">{{ h.outstandingPayments | number: '1.0-2' }}</td>
                <td class="text-end" data-label="Adjusted">{{ h.adjustedBank | number: '1.0-2' }}</td><td class="text-end" data-label="Ledgers">{{ h.ledgerTotal | number: '1.0-2' }}</td>
                <td class="text-end" data-label="Accounts (bank / held)">{{ h.glBank | number: '1.0-2' }} / {{ h.glLiability | number: '1.0-2' }}</td>
                <td class="text-end" data-label="Difference">{{ h.difference | number: '1.0-2' }}</td>
                <td data-label="Result"><span class="badge" [style.background]="h.balanced ? '#27ae60' : '#c0392b'" style="color:#fff;">{{ h.balanced ? ('balanced' | translate) : ('not balanced' | translate) }}</span></td>
                <td data-label="By">{{ h.preparedByName }}</td>
                <td><button class="btn btn-sm btn-outline-secondary rc-print" type="button" (click)="print(h.id)">{{ 'Print' | translate }}</button></td>
              </tr>
              <tr *ngIf="!v.history.length"><td colspan="11" class="text-muted">{{ 'No reconciliation has been saved yet.' | translate }}</td></tr>
            </tbody>
          </table>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class LawTrustReconcileComponent implements OnInit {
  v: TrustReconciliationView | null = null;
  error: string | null = null;
  busy = false;
  f: { asOfDate: string | null; statementBalance: number | null; depositsInTransit: number | null; outstandingPayments: number | null; note: string } =
    { asOfDate: null, statementBalance: null, depositsInTransit: null, outstandingPayments: null, note: '' };

  constructor(private api: LawApiService) {}

  print(id: number): void {
    this.error = null;
    showPdf(this.api.reconciliationPdf(id), 'trust-reconciliation.pdf', (e) => (this.error = e));
  }

  ngOnInit(): void {
    this.api.trustReconciliation().subscribe({ next: (v) => (this.v = v), error: (e) => (this.error = e?.error?.message ?? 'Could not load the reconciliation') });
  }

  adjusted(): number { return (this.f.statementBalance ?? 0) + (this.f.depositsInTransit ?? 0) - (this.f.outstandingPayments ?? 0); }
  diff(): number { return Math.round((this.adjusted() - (this.v?.ledgerTotal ?? 0)) * 100) / 100; }

  save(): void {
    this.busy = true;
    this.error = null;
    this.api.trustReconcile({ asOfDate: this.f.asOfDate, statementBalance: this.f.statementBalance, depositsInTransit: this.f.depositsInTransit, outstandingPayments: this.f.outstandingPayments,
      note: this.f.note.trim() || null }).subscribe({
      next: (v) => { this.busy = false; this.v = v; this.f = { asOfDate: null, statementBalance: null, depositsInTransit: null, outstandingPayments: null, note: '' }; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not save the reconciliation'; },
    });
  }
}
