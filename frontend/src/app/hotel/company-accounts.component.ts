import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { AuthService } from '../core/auth.service';
import { CompanyBill, CompanySummary, HotelApiService } from '../core/api/hotel-api.service';
import { PaymentMethodApiService } from '../core/api/payment-method-api.service';
import { PaymentMethodOption } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';
import { showPdf } from '../core/open-pdf';
import { errMsg, today } from './hotel-util';

import { T } from '../core/i18n/t';
/** Hotel > Company accounts: what companies owe for stays billed to them, and the money received against it. */
@Component({
  selector: 'app-company-accounts',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-2">{{ 'Company accounts' | translate }}</h3>
      <p class="text-muted" style="font-size:0.9rem;">{{ 'Stays billed to a company appear here at check-out. Record the money when the company pays.' | translate }}</p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>

      <div class="card summary-card" style="padding:1rem; margin-bottom:1rem;" *ngIf="summary.length">
        <table class="table-sm summary-table">
          <thead><tr><th>{{ 'Company' | translate }}</th><th class="text-end">{{ 'Billed' | translate }}</th><th class="text-end">{{ 'Received' | translate }}</th><th class="text-end">{{ 'Owing' | translate }}</th><th class="text-end">{{ 'Overdue' | translate }}</th><th class="text-end">{{ 'Open bills' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let c of summary">
              <td><strong>{{ c.companyName }}</strong></td>
              <td class="text-end">{{ c.billed | number: '1.2-2' }}</td>
              <td class="text-end">{{ c.paid | number: '1.2-2' }}</td>
              <td class="text-end"><strong>{{ c.outstanding | number: '1.2-2' }}</strong></td>
              <td class="text-end" [style.color]="c.overdue > 0 ? 'var(--danger-text)' : 'inherit'">{{ c.overdue | number: '1.2-2' }}</td>
              <td class="text-end">{{ c.openBills }}</td>
              <td><button class="btn btn-sm btn-outline-secondary only-company" type="button" (click)="onlyCompany(c)">{{ 'Show bills' | translate }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <app-list-filter exportPath="/hotel/company-bills/export" [exportExtra]="extra" [extraActive]="companyId !== null" [placeholder]="'Search invoice, company, guest, booking' | translate" [statuses]="statuses" statusAll="All bills" [dates]="false" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-2 align-center" *ngIf="companyId !== null">
        <span class="badge badge-info">{{ companyName }}</span>
        <button class="btn btn-sm btn-link clear-company" type="button" (click)="onlyCompany(null)">{{ 'Show all companies' | translate }}</button>
      </div>

      <table class="table-sm bills-list">
        <thead><tr><th>{{ 'Invoice' | translate }}</th><th>{{ 'Company' | translate }}</th><th>{{ 'Stay' | translate }}</th><th>{{ 'Issued' | translate }}</th><th>{{ 'Due' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th class="text-end">{{ 'Received' | translate }}</th><th class="text-end">{{ 'Owing' | translate }}</th><th></th></tr></thead>
        <tbody>
          <ng-container *ngFor="let b of list.rows">
            <tr>
              <td><strong>{{ b.billNumber }}</strong></td>
              <td>{{ b.companyName }}</td>
              <td><a [routerLink]="['/admin/hotel/bookings', b.bookingId]">{{ b.bookingNumber }}</a><div class="text-muted" style="font-size:0.8rem;">{{ b.guestName }}</div></td>
              <td>{{ b.issuedOn }}</td>
              <td>{{ b.dueDate || '—' }}<span class="badge badge-danger" style="margin-left:0.3rem;" *ngIf="b.overdue">{{ 'Overdue' | translate }}</span></td>
              <td class="text-end">{{ b.amount | number: '1.2-2' }}</td>
              <td class="text-end">{{ b.paid | number: '1.2-2' }}</td>
              <td class="text-end"><span class="badge badge-success" *ngIf="b.status === 'PAID'">{{ 'Paid' | translate }}</span><strong *ngIf="b.status !== 'PAID'">{{ b.outstanding | number: '1.2-2' }}</strong></td>
              <td class="flex gap-2" style="flex-wrap:wrap;">
                <button class="btn btn-sm btn-outline-secondary inv-pdf" type="button" (click)="pdf(b)">{{ 'Invoice' | translate }}</button>
                <button class="btn btn-sm btn-outline-primary receive" type="button" *ngIf="canManage && b.status !== 'PAID'" (click)="openReceive(b)">{{ 'Receive money' | translate }}</button>
                <button class="btn btn-sm btn-outline-secondary" type="button" (click)="toggle(b)">{{ openId === b.id ? ('Hide' | translate) : ('Receipts' | translate) }}</button>
              </td>
            </tr>
            <tr *ngIf="openId === b.id">
              <td colspan="9">
                <div style="padding:0.5rem 0.25rem;">
                  <div class="text-muted" style="font-size:0.85rem;">{{ b.description }}</div>
                  <table class="table-sm no-stack" style="max-width:520px; margin:0.4rem 0;" *ngIf="detail && detail.receipts.length">
                    <thead><tr><th>{{ 'Received' | translate }}</th><th>{{ 'Method' | translate }}</th><th>{{ 'Reference' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th></tr></thead>
                    <tbody><tr *ngFor="let r of detail.receipts"><td>{{ r.receivedOn }}</td><td>{{ r.method }}</td><td>{{ r.reference }}</td><td class="text-end">{{ r.amount | number: '1.2-2' }}</td></tr></tbody>
                  </table>
                  <p class="text-muted" *ngIf="detail && !detail.receipts.length">{{ 'Nothing received yet.' | translate }}</p>
                  <form *ngIf="receiveFor === b.id" class="receive-form" (ngSubmit)="receive(b)">
                    <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
                      <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" style="max-width:150px;" type="number" min="0.01" step="0.01" name="ra" [(ngModel)]="recAmount" required /></div>
                      <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Method' | translate }}</label>
                        <select class="form-control" name="rm" [(ngModel)]="recMethod"><option *ngFor="let m of methods" [value]="m.code">{{ m.name }}</option></select></div>
                      <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Reference' | translate }}</label><input class="form-control" name="rr" [(ngModel)]="recRef" [placeholder]="'Cheque or transfer no.' | translate" /></div>
                      <div class="form-group" style="margin:0;"><label class="form-label">{{ 'Date' | translate }}</label><input class="form-control" type="date" name="rd" [(ngModel)]="recDate" /></div>
                      <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Record' | translate }}</button>
                      <button class="btn btn-link" type="button" (click)="receiveFor = null">{{ 'Cancel' | translate }}</button>
                    </div>
                  </form>
                </div>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || companyId !== null ? ('Nothing matches these filters.' | translate) : ('No company has been billed yet. Open a stay, choose "Bill a company…" on a bill, and it lands here at check-out.' | translate) }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class CompanyAccountsComponent implements OnInit {
  summary: CompanySummary[] = [];
  methods: PaymentMethodOption[] = [];
  statuses = [{ value: 'OPEN', label: T('Open') }, { value: 'OVERDUE', label: T('Overdue') }, { value: 'PAID', label: T('Paid') }];
  companyId: number | null = null;
  companyName = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<CompanyBill>((f, p, sz) => this.api.page<CompanyBill>('company-bills', f, p, sz, this.companyId ? { companyId: this.companyId } : {}), 'Failed to load company bills');
  error: string | null = null;
  message: string | null = null;
  busy = false;
  openId: number | null = null;
  detail: CompanyBill | null = null;
  receiveFor: number | null = null;
  recAmount: number | null = null;
  recMethod = '';
  recRef = '';
  recDate = today();

  constructor(private api: HotelApiService, private pay: PaymentMethodApiService, public auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.pay.listActive().subscribe((m) => {
      this.methods = m.filter((x) => !x.credit && !x.roomCharge);
      this.recMethod = (this.methods.find((x) => x.cash) ?? this.methods[0])?.code ?? '';
    });
    this.reload();
  }

  reload(): void {
    this.api.companySummary().subscribe({ next: (s) => (this.summary = s), error: (e) => (this.error = errMsg(e, 'Failed to load the summary')) });
    this.list.load();
  }

  clearedExtra(): void {
    this.companyId = null;
    this.companyName = '';
    this.extra = {};
  }

  onlyCompany(c: CompanySummary | null): void {
    this.companyId = c ? c.companyId : null;
    this.companyName = c ? c.companyName : '';
    this.extra = c ? { companyId: c.companyId } : {};
    this.list.page = 0;
    this.list.load();
  }

  toggle(b: CompanyBill): void {
    if (this.openId === b.id) {
      this.openId = null;
      return;
    }
    this.openId = b.id;
    this.detail = null;
    this.api.companyBill(b.id).subscribe({ next: (d) => (this.detail = d), error: (e) => (this.error = errMsg(e, 'Failed to load the bill')) });
  }

  openReceive(b: CompanyBill): void {
    this.error = this.message = null;
    if (this.openId !== b.id) this.toggle(b);
    this.receiveFor = b.id;
    this.recAmount = b.outstanding;
    this.recRef = '';
    this.recDate = today();
  }

  receive(b: CompanyBill): void {
    if (!this.recAmount) return;
    this.busy = true;
    this.error = this.message = null;
    this.api.receiveFromCompany(b.id, { method: this.recMethod, amount: this.recAmount, reference: this.recRef || null, receivedOn: this.recDate || null }).subscribe({
      next: (d) => {
        this.busy = false;
        this.message = T('Recorded {p0} from {p1} against {p2}.', { p0: this.recAmount, p1: b.companyName, p2: b.billNumber });
        this.receiveFor = null;
        this.detail = d;
        this.reload();
      },
      error: (e) => {
        this.busy = false;
        this.error = errMsg(e, 'Could not record the money');
      },
    });
  }

  pdf(b: CompanyBill): void {
    showPdf(this.api.companyInvoicePdf(b.id), b.billNumber + '.pdf', (err) => { if (err) this.error = err; });
  }
}
