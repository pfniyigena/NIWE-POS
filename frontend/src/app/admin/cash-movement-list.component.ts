import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { CashMovement, RegisterSummary } from '../core/models';
import { ListState } from '../core/api/list-state';
import { ListFilterComponent } from '../shared/list-filter.component';
import { PagerComponent } from '../shared/pager.component';

/** Every cash movement across shifts: money paid in, paid out, drops into the safe and counts, with search, filters, pages and Excel / PDF / CSV. */
@Component({
  selector: 'app-cash-movement-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ListFilterComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="list.error">{{ list.error }}</div>
      <h3 class="mb-3">Cash movements</h3>
      <app-list-filter exportPath="/cash-movements/export" [exportExtra]="extra" [extraActive]="registerId !== ''" placeholder="Search reason, note, who, register" [statuses]="kinds" statusAll="All movements" (filterChange)="list.apply($event)" (cleared)="clearedExtra()"></app-list-filter>
      <div class="flex gap-2 mb-3 align-center">
        <select class="form-select cm-register" style="width:auto;" [(ngModel)]="registerId" (ngModelChange)="extraChanged()" name="register" title="Register">
          <option value="">Any register</option>
          <option *ngFor="let r of registers" [value]="r.id">{{ r.name }}</option>
        </select>
      </div>
      <table>
        <thead>
          <tr><th>When</th><th>Register</th><th>What</th><th>Reason</th><th class="text-end">Amount</th><th>By</th><th></th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let m of list.rows" class="cm-row" [style.opacity]="m.voided ? 0.5 : 1">
            <td>{{ m.createdAt | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ m.registerName }}</td>
            <td>{{ label(m.kind) }}<span *ngIf="m.voided" class="badge badge-secondary" style="margin-left:6px;" [title]="m.voidReason || ''">voided</span></td>
            <td>{{ m.reason }}<span class="text-muted" *ngIf="m.note"> — {{ m.note }}</span>
              <span class="text-muted" *ngIf="m.kind === 'COUNT'"> (expected {{ m.expectedCash | number: '1.2-2' }})</span></td>
            <td class="text-end">{{ m.amount | number: '1.2-2' }}</td>
            <td>{{ m.byUser }}<span class="text-muted" *ngIf="m.approvedBy"> (approved by {{ m.approvedBy }})</span></td>
            <td><a [routerLink]="['/cash-control', m.sessionId]">Drawer</a></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="list.loaded && list.rows.length === 0">{{ list.filtered || registerId ? 'No cash movements match these filters.' : 'No cash has been moved by hand yet.' }}</p>
      <app-pager [page]="list.page" [size]="list.size" [total]="list.total" (pageChange)="list.goto($event)"></app-pager>
    </app-shell>
  `,
})
export class CashMovementListComponent implements OnInit {
  kinds = [
    { value: 'PAID_IN', label: 'Paid in' },
    { value: 'PAID_OUT', label: 'Paid out' },
    { value: 'DROP', label: 'Drops to safe' },
    { value: 'COUNT', label: 'Counts' },
    { value: 'VOIDED', label: 'Voided' },
  ];
  registers: RegisterSummary[] = [];
  registerId = '';
  extra: Record<string, string | number | null | undefined> = {};
  list = new ListState<CashMovement>((f, p, sz) => this.registerApi.cashMovementsPage(f, p, sz, { registerId: this.registerId }), 'Failed to load cash movements');

  constructor(private registerApi: RegisterApiService) {}

  ngOnInit(): void {
    this.list.load();
    this.registerApi.listRegisters().subscribe({ next: (r) => (this.registers = r), error: () => {} });
  }

  label(kind: string): string {
    return this.kinds.find((k) => k.value === kind)?.label.replace('Drops to safe', 'Drop to safe').replace('Counts', 'Count') ?? kind;
  }

  extraChanged(): void {
    this.extra = { registerId: this.registerId };
    this.list.page = 0;
    this.list.load();
  }

  clearedExtra(): void {
    this.registerId = '';
    this.extra = {};
  }
}
