import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { Collections, CollectionItem, FollowUpKind, InstallmentApiService } from '../../core/api/installment-api.service';

/** Installments > Collections: who to chase today — overdue plans, oldest first — with the last contact and any promise to pay, and a quick way to log a call. */
@Component({
  selector: 'app-installment-collections',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3 style="margin:0;">{{ 'Collections' | translate }}</h3>
        <a class="btn btn-link" routerLink="/admin/installments">{{ 'All agreements' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>

      <div class="stat-grid mb-3" *ngIf="data as c">
        <div class="stat-tile" [class.warn]="c.totalOverdue > 0"><div class="stat-label">{{ 'Overdue in this list' | translate }}</div><div class="stat-value">{{ c.totalOverdue | number: '1.0-2' }}</div>
          <div class="text-muted" style="font-size:0.75rem;">{{ '{p0} plan(s)' | translate: { p0: (c.plans) } }}</div></div>
        <div class="stat-tile" [class.warn]="c.brokenPromises > 0"><div class="stat-label">{{ 'Broken promises' | translate }}</div><div class="stat-value">{{ c.brokenPromises }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Promised for today' | translate }}</div><div class="stat-value">{{ c.promisesToday }}</div></div>
        <div class="stat-tile"><div class="stat-label">{{ 'Never contacted' | translate }}</div><div class="stat-value">{{ c.neverContacted }}</div></div>
      </div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <button *ngFor="let f of filters" type="button" class="btn" [class.btn-primary]="filter === f.key" [class.btn-outline-primary]="filter !== f.key" (click)="setFilter(f.key)">{{ f.label }}</button>
        <input class="form-control" style="width:16rem;" [placeholder]="'Search customer, phone, plan or invoice' | translate" name="q" [(ngModel)]="search" (ngModelChange)="searchChanged()" />
      </div>

      <table class="table-sm" *ngIf="data && data.items.length">
        <thead><tr><th>{{ 'Customer' | translate }}</th><th>{{ 'Plan' | translate }}</th><th class="text-end">{{ 'Overdue' | translate }}</th><th>{{ 'Age' | translate }}</th><th>{{ 'Next due' | translate }}</th><th>{{ 'Last contact' | translate }}</th><th>{{ 'Promise to pay' | translate }}</th><th></th></tr></thead>
        <tbody>
          <ng-container *ngFor="let i of data.items">
            <tr>
              <td><strong>{{ i.customerName || '—' }}</strong>
                <div class="text-muted" style="font-size:0.8rem;"><span *ngIf="i.customerPhone">{{ i.customerPhone }}</span><span *ngIf="i.customerPhone && i.customerEmail"> · </span><span *ngIf="i.customerEmail">{{ i.customerEmail }}</span></div></td>
              <td><a [routerLink]="['/admin/installments', i.planId]">{{ i.planNumber }}</a>
                <div class="text-muted" style="font-size:0.8rem;">{{ i.invoiceNumber }}</div></td>
              <td class="text-end">{{ i.overdueAmount | number: '1.0-2' }}
                <div class="text-muted" style="font-size:0.75rem;" *ngIf="i.overdueInstallments">{{ '{p0} instalment(s)' | translate: { p0: (i.overdueInstallments) } }}</div></td>
              <td><span class="badge" [class.badge-danger]="i.oldestOverdueDays > 60" [class.badge-warning]="i.oldestOverdueDays > 0 && i.oldestOverdueDays <= 60" [class.badge-secondary]="i.oldestOverdueDays === 0">
                  {{ i.oldestOverdueDays > 0 ? ('{p0} days' | translate: { p0: i.oldestOverdueDays }) : ('due soon' | translate) }}</span></td>
              <td><span *ngIf="i.nextDueDate">{{ i.nextDueDate }} <span class="text-muted">({{ i.nextDueAmount | number: '1.0-2' }})</span></span><span *ngIf="!i.nextDueDate">—</span></td>
              <td><span *ngIf="i.lastContactAt">{{ i.lastContactAt | date: 'd MMM' }} · {{ kindLabel(i.lastContactKind) }}</span><span *ngIf="!i.lastContactAt" class="text-muted">{{ 'never' | translate }}</span>
                <div class="text-muted" style="font-size:0.8rem; max-width:18rem;" *ngIf="i.lastContactNote">{{ i.lastContactNote }}</div></td>
              <td><span *ngIf="i.promisedOn" [style.color]="i.promiseBroken ? 'var(--danger-text, #b00020)' : ''">{{ i.promisedOn }}<span *ngIf="i.promisedAmount"> ({{ i.promisedAmount | number: '1.0-2' }})</span>
                  <span class="badge badge-danger" *ngIf="i.promiseBroken" style="margin-left:0.3rem;">{{ 'broken' | translate }}</span></span><span *ngIf="!i.promisedOn" class="text-muted">—</span></td>
              <td style="white-space:nowrap;"><button class="btn btn-sm btn-outline-primary" type="button" (click)="startLog(i)">{{ 'Log contact' | translate }}</button></td>
            </tr>
            <tr *ngIf="logging === i.planId"><td colspan="8">
              <form class="flex gap-2 align-center" style="flex-wrap:wrap;" (ngSubmit)="save(i)">
                <select class="form-select" style="width:auto;" name="k{{ i.planId }}" [(ngModel)]="kind"><option value="CALL">{{ 'Phone call' | translate }}</option><option value="VISIT">{{ 'Visit' | translate }}</option><option value="PROMISE">{{ 'Promise to pay' | translate }}</option><option value="NOTE">{{ 'Note' | translate }}</option></select>
                <ng-container *ngIf="kind === 'PROMISE'">
                  <app-date-picker name="d{{ i.planId }}" [(ngModel)]="date"></app-date-picker>
                  <input class="form-control" type="number" min="0" step="0.01" style="width:9rem;" [placeholder]="'Amount' | translate" name="a{{ i.planId }}" [(ngModel)]="amount" />
                </ng-container>
                <input class="form-control" style="flex:1; min-width:14rem;" [placeholder]="kind === 'PROMISE' ? 'Note (optional)' : 'What was said'" name="n{{ i.planId }}" [(ngModel)]="note" />
                <button class="btn btn-primary btn-sm" type="submit" [disabled]="busy || (kind === 'PROMISE' ? !date : !note.trim())">{{ 'Save' | translate }}</button>
                <button class="btn btn-link btn-sm" type="button" (click)="logging = null">{{ 'Cancel' | translate }}</button>
              </form>
            </td></tr>
          </ng-container>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="data && !data.items.length">{{ 'Nothing here. {p0}' | translate: { p0: (filter === 'overdue' ? 'No plan has an overdue instalment right now.' : 'No plan matches this filter.') } }}</p>
    </app-shell>
  `,
})
export class InstallmentCollectionsComponent implements OnInit {
  filters = [
    { key: 'overdue', label: 'Overdue' },
    { key: 'no-contact', label: 'Not contacted this week' },
    { key: 'promises', label: 'Promised' },
    { key: 'broken', label: 'Broken promises' },
    { key: 'due-soon', label: 'Due soon' },
    { key: 'defaulted', label: 'Defaulted' },
  ];
  filter = 'overdue';
  search = '';
  data: Collections | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  logging: number | null = null;
  kind: FollowUpKind = 'CALL';
  note = '';
  date = '';
  amount: number | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private api: InstallmentApiService) {}

  ngOnInit(): void {
    this.load();
  }

  setFilter(f: string): void {
    this.filter = f;
    this.load();
  }

  searchChanged(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.load(), 300);
  }

  load(): void {
    this.api.collections(this.filter, this.search).subscribe({
      next: (d) => {
        this.data = d;
        this.error = null;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the list'),
    });
  }

  kindLabel(k: FollowUpKind | null): string {
    return k ? { CALL: 'call', VISIT: 'visit', EMAIL: 'e-mail', PROMISE: 'promise', NOTE: 'note' }[k] : '';
  }

  startLog(i: CollectionItem): void {
    this.logging = i.planId;
    this.kind = 'CALL';
    this.note = '';
    this.date = '';
    this.amount = null;
  }

  save(i: CollectionItem): void {
    this.busy = true;
    this.error = null;
    this.api
      .addFollowUp(i.planId, { kind: this.kind, note: this.note.trim() || null, promisedOn: this.kind === 'PROMISE' ? this.date || null : null, promisedAmount: this.kind === 'PROMISE' ? this.amount : null })
      .subscribe({
        next: () => {
          this.busy = false;
          this.logging = null;
          this.info = 'Saved to the collections log.';
          this.load();
        },
        error: (e) => {
          this.busy = false;
          this.error = e?.error?.message ?? 'Could not save';
        },
      });
  }
}
