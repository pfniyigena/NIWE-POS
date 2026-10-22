import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Customer } from '../../core/models';
import { BillingPreview, ProjectSummary, TimeApiService } from '../../core/api/time-api.service';

/** Time Tracking > Billing: approved billable time, per project, turned into a draft invoice. */
@Component({
  selector: 'app-time-billing',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Bill time</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="done">
        Draft invoice <a [routerLink]="['/admin/invoices', done.invoiceId]">{{ done.invoiceNumber }}</a> created from {{ done.entries }} entries
        ({{ done.totalAmount | number: '1.0-2' }}). Open it to review, then issue it.
      </div>

      <table class="table-sm mb-3">
        <thead><tr><th>Project</th><th>Customer</th><th class="text-end">Billable hours</th><th class="text-end">Ready to invoice</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let p of ready">
            <td>{{ p.name }}</td><td>{{ p.customerName || '—' }}</td>
            <td class="text-end">{{ p.billableHours | number: '1.0-2' }}</td>
            <td class="text-end">{{ p.unbilledAmount | number: '1.0-2' }}</td>
            <td><button class="btn btn-sm btn-primary" type="button" (click)="open(p)">Prepare invoice</button></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!ready.length">No approved billable time is waiting. Time appears here once it is approved.</p>

      <div class="card mb-3" style="padding:1rem; max-width:52rem;" *ngIf="current">
        <strong class="mb-2">{{ current.name }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" *ngIf="!current.customerId"><label class="form-label">Customer</label>
            <select class="form-select" name="cu" [(ngModel)]="customerId" (ngModelChange)="refresh()">
              <option [ngValue]="null">Choose…</option><option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">Time from</label><app-date-picker name="f" [(ngModel)]="from" (ngModelChange)="refresh()"></app-date-picker></div>
          <div class="form-group"><label class="form-label">to</label><app-date-picker name="t" [(ngModel)]="to" (ngModelChange)="refresh()"></app-date-picker></div>
          <div class="form-group"><label class="form-label">Invoice due</label><app-date-picker name="dd" [(ngModel)]="dueDate"></app-date-picker></div>
        </div>
        <table class="table-sm" *ngIf="preview?.lines?.length">
          <thead><tr><th>Invoice line</th><th class="text-end">Hours</th><th class="text-end">Rate</th><th class="text-end">Amount</th></tr></thead>
          <tbody>
            <tr *ngFor="let l of preview!.lines"><td>{{ l.description }}</td><td class="text-end">{{ l.hours | number: '1.0-2' }}</td>
              <td class="text-end">{{ l.hourlyRate | number: '1.0-2' }}</td><td class="text-end">{{ l.amount | number: '1.0-2' }}</td></tr>
          </tbody>
          <tfoot><tr><th>Total</th><th class="text-end">{{ preview!.totalHours | number: '1.0-2' }}</th><th></th><th class="text-end">{{ preview!.totalAmount | number: '1.0-2' }}</th></tr></tfoot>
        </table>
        <p class="text-muted" *ngIf="preview && !preview.lines.length">No billable approved time in this period.</p>
        <p class="text-muted" style="font-size:0.85rem;">One line per person and rate. The invoice is created as a draft, so you can add or change lines before issuing it. Tax follows the service product set in Time Tracking settings.</p>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="button" [disabled]="!preview?.lines?.length || busy" (click)="create()">Create draft invoice</button>
          <button class="btn btn-outline-secondary" type="button" (click)="current = null">Close</button>
        </div>
      </div>

      <div class="card" style="padding:1rem; max-width:52rem;" *ngIf="billed.length">
        <strong class="mb-2">Undo a time invoice</strong>
        <div class="text-muted mb-2" style="font-size:0.85rem;">If a draft invoice was deleted or an issued one was voided, release its time so it can be billed again.</div>
        <table class="table-sm">
          <tbody><tr *ngFor="let b of billed"><td>{{ b.number }}</td><td>{{ b.count }} entries</td>
            <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="release(b.id)">Release time</button></td></tr></tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class TimeBillingComponent implements OnInit {
  ready: ProjectSummary[] = [];
  customers: Customer[] = [];
  current: ProjectSummary | null = null;
  preview: BillingPreview | null = null;
  customerId: number | null = null;
  from: string | null = null;
  to: string | null = null;
  dueDate: string | null = null;
  done: { invoiceId: number; invoiceNumber: string; entries: number; totalAmount: number } | null = null;
  billed: { id: number; number: string; count: number }[] = [];
  error: string | null = null;
  busy = false;

  constructor(private api: TimeApiService, private customerApi: CustomerApiService) {}

  ngOnInit(): void {
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !(x as any).walkIn)), error: () => {} });
    this.load();
  }

  private load(): void {
    this.api.ready().subscribe({ next: (r) => (this.ready = r), error: (e) => (this.error = e?.error?.message ?? 'Could not load') });
    this.api.entries({ status: 'INVOICED', from: '2000-01-01', to: '2999-12-31' }).subscribe({
      next: (list) => {
        const map = new Map<number, { id: number; number: string; count: number }>();
        list.forEach((e) => {
          if (!e.invoiceId) return;
          const cur = map.get(e.invoiceId) ?? { id: e.invoiceId, number: e.invoiceNumber ?? '#' + e.invoiceId, count: 0 };
          cur.count++;
          map.set(e.invoiceId, cur);
        });
        this.billed = [...map.values()].sort((a, b) => b.id - a.id).slice(0, 10);
      },
      error: () => {},
    });
  }

  open(p: ProjectSummary): void {
    this.current = p;
    this.customerId = p.customerId;
    this.from = null;
    this.to = null;
    this.dueDate = null;
    this.done = null;
    this.refresh();
  }

  refresh(): void {
    if (!this.current) return;
    this.error = null;
    this.api.preview({ projectId: this.current.id, customerId: this.customerId, from: this.from || null, to: this.to || null }).subscribe({
      next: (p) => (this.preview = p),
      error: (e) => (this.error = e?.error?.message ?? 'Could not prepare the invoice'),
    });
  }

  create(): void {
    if (!this.current) return;
    this.busy = true;
    this.error = null;
    this.api.invoice({ projectId: this.current.id, customerId: this.customerId, from: this.from || null, to: this.to || null, dueDate: this.dueDate || null }).subscribe({
      next: (r) => {
        this.busy = false;
        this.done = r;
        this.current = null;
        this.preview = null;
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not create the invoice';
      },
    });
  }

  release(invoiceId: number): void {
    this.error = null;
    this.api.release(invoiceId).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? 'Could not release the time') });
  }
}
