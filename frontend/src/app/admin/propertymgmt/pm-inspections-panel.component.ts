import { Component, Input, OnChanges } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { INSPECTION_KIND_LABEL, InspectionKind, InspectionRow, PmDepositApiService } from '../../core/api/pm-deposit-api.service';

import { T } from '../../core/i18n/t';
/** The inspections of one lease (move-in, move-out and visits), with the buttons that start them. */
@Component({
  selector: 'app-pm-inspections-panel',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="card" style="padding:1rem; margin-top:0.75rem;">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <h4>{{ 'Inspections' | translate }}<span class="text-muted" style="font-size:0.85rem;" *ngIf="rows.length"> ({{ rows.length }})</span></h4>
        <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="canManage">
          <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="!has('MOVE_IN') && !over" [disabled]="busy" (click)="start('MOVE_IN')">{{ 'Start move-in' | translate }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="!has('MOVE_OUT') && status !== 'DRAFT'" [disabled]="busy" (click)="start('MOVE_OUT')">{{ 'Start move-out' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="status === 'ACTIVE'" [disabled]="busy" (click)="start('PERIODIC')">{{ 'Record a visit' | translate }}</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <table class="stack table-sm" *ngIf="rows.length">
        <thead><tr><th>{{ 'Inspection' | translate }}</th><th>{{ 'Kind' | translate }}</th><th>{{ 'Date' | translate }}</th><th>{{ 'Looked at' | translate }}</th><th>{{ 'Problems' | translate }}</th><th class="text-end">{{ 'Repairs' | translate }}</th><th>{{ 'Status' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let r of rows">
            <td data-label="Inspection"><a [routerLink]="['/admin/propertymgmt/inspections', r.id]"><strong>{{ r.code }}</strong></a></td>
            <td data-label="Kind">{{ kindName(r.kind) }}</td>
            <td style="white-space:nowrap;" data-label="Date">{{ r.date | date: 'd MMM y' }}</td>
            <td data-label="Looked at">{{ '{p0} of {p1}' | translate: { p0: (r.assessed), p1: (r.items) } }}</td>
            <td data-label="Problems"><span [style.color]="r.problems ? '#b91c1c' : ''">{{ r.problems }}</span></td>
            <td class="text-end" data-label="Repairs">{{ r.repairCost ? (r.repairCost | number: '1.0-0') : '—' }}</td>
            <td data-label="Status"><span class="badge" [ngClass]="r.status === 'SIGNED_OFF' ? 'badge-success' : 'badge-warning'">{{ r.status === 'SIGNED_OFF' ? ('Signed off' | translate) : ('In progress' | translate) }}</span></td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted" *ngIf="!rows.length && loaded">{{ 'No inspection yet. Walk through the unit with the tenant when the keys change hands and record what you see.' | translate }}</p>
    </div>
  `,
})
export class PmInspectionsPanelComponent implements OnChanges {
  @Input() leaseId!: number;
  @Input() status = '';
  @Input() canManage = false;

  rows: InspectionRow[] = [];
  loaded = false;
  busy = false;
  error: string | null = null;

  constructor(private api: PmDepositApiService, private router: Router) {}

  get over(): boolean { return this.status === 'ENDED' || this.status === 'TERMINATED'; }

  ngOnChanges(): void { if (this.leaseId) this.load(); }

  load(): void {
    this.api.inspections({ leaseId: this.leaseId }).subscribe({ next: (r) => { this.rows = r.items; this.loaded = true; }, error: (e) => { this.loaded = true; this.error = e?.error?.message ?? T('Could not load the inspections'); } });
  }

  has(k: InspectionKind): boolean { return this.rows.some((r) => r.kind === k); }
  kindName(k: string): string { return (INSPECTION_KIND_LABEL as Record<string, string>)[k] ?? k; }

  start(kind: InspectionKind): void {
    this.busy = true;
    this.error = null;
    this.api.startInspection(this.leaseId, { kind }).subscribe({
      next: (i) => { this.busy = false; this.router.navigate(['/admin/propertymgmt/inspections', i.id]); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('Could not start the inspection'); },
    });
  }
}
