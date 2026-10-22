import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PmStatementApiService, STATEMENT_STATUS_LABEL, StatementRow, statementBadge } from '../../core/api/pm-statement-api.service';

/** The owner statements of one owner, newest first, with a link to make another. */
@Component({
  selector: 'app-statements-panel',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;">
      <div class="flex justify-between align-center mb-2">
        <h4>Statements</h4>
        <div class="flex gap-2">
          <a class="btn btn-sm btn-outline-primary" *ngIf="canManage" routerLink="/admin/propertymgmt/statements" [queryParams]="{ ownerId: ownerId, make: 1 }">+ Statement</a>
          <a class="btn btn-sm btn-outline-secondary" routerLink="/admin/propertymgmt/statements" [queryParams]="{ ownerId: ownerId }">All</a>
        </div>
      </div>
      <table class="stack table-sm" *ngIf="rows.length; else none">
        <thead><tr><th>Statement</th><th>Period</th><th>Status</th><th class="text-end">Rent</th><th class="text-end">Net to owner</th></tr></thead>
        <tbody><tr *ngFor="let s of rows">
          <td data-label="Statement"><a [routerLink]="['/admin/propertymgmt/statements', s.id]"><strong>{{ s.code }}</strong></a></td>
          <td style="white-space:nowrap;" data-label="Period">{{ s.periodFrom | date: 'd MMM y' }} – {{ s.periodTo | date: 'd MMM y' }}</td>
          <td data-label="Status"><span class="badge" [ngClass]="badge(s.status)">{{ label(s.status) }}</span></td>
          <td class="text-end" data-label="Rent">{{ s.rentCollected | number: '1.0-0' }}</td>
          <td class="text-end" [style.color]="s.netPayable < 0 ? '#b91c1c' : ''" data-label="Net to owner"><strong>{{ s.netPayable | number: '1.0-0' }}</strong></td>
        </tr></tbody>
      </table>
      <ng-template #none><div class="text-muted">{{ loaded ? 'No statements yet.' : 'Loading…' }}</div></ng-template>
    </div>
  `,
})
export class StatementsPanelComponent implements OnChanges {
  @Input({ required: true }) ownerId!: number;
  @Input() canManage = false;
  rows: StatementRow[] = [];
  loaded = false;

  constructor(private api: PmStatementApiService) {}

  ngOnChanges(): void {
    this.api.list({ ownerId: this.ownerId }).subscribe({ next: (l) => { this.rows = l.slice(0, 12); this.loaded = true; }, error: () => { this.loaded = true; } });
  }

  badge(s: string): string { return statementBadge(s); }
  label(s: string): string { return (STATEMENT_STATUS_LABEL as Record<string, string>)[s] ?? s; }
}
