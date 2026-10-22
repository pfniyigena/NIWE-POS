import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReExportComponent } from './re-export.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { ProjectRow, RealEstateApiService, UNIT_SALE_STAGE_LABEL, UnitSaleRow, UnitSaleStage } from '../../core/api/realestate-api.service';

/** Real Estate > Unit sales: every reservation and sale of a project unit, with what the buyer has paid. */
@Component({
  selector: 'app-unit-sale-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ReExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Unit sales</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/realestate/projects">Projects</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="pj" [(ngModel)]="projectId" (ngModelChange)="load()">
          <option [ngValue]="null">All projects</option><option *ngFor="let p of projects" [ngValue]="p.id">{{ p.name }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="stage" (ngModelChange)="load()">
          <option value="">All stages</option><option *ngFor="let s of stages" [value]="s.value">{{ s.label }}</option>
        </select>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="oo" [(ngModel)]="openOnly" (ngModelChange)="load()" /> Open only</label>
        <input class="form-control" style="width:15rem;" placeholder="Search sale, buyer, unit" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <app-re-export kind="unit-sales" [filters]="{ q: q.trim(), stage: stage, projectId: projectId, openOnly: openOnly }"></app-re-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Sale</th><th>Unit</th><th>Buyer</th><th>Stage</th><th class="text-end">List price</th><th class="text-end">Agreed price</th><th class="text-end">Paid</th><th class="text-end">Still owed</th><th>Agent</th><th>Date</th></tr></thead>
        <tbody>
          <tr *ngFor="let s of list">
            <td data-label="Sale"><a [routerLink]="['/admin/realestate/unit-sales', s.id]"><strong>{{ s.code }}</strong></a></td>
            <td data-label="Unit"><a [routerLink]="['/admin/realestate/units', s.unitId]">{{ s.unitNo }}</a><div class="text-muted" style="font-size:0.8rem;">{{ s.projectName }}</div></td>
            <td data-label="Buyer">{{ s.buyerName }}</td>
            <td data-label="Stage"><span class="badge" [class.badge-success]="s.stage === 'COMPLETED'" [class.badge-danger]="s.stage === 'CANCELLED'" [class.badge-warning]="s.stage === 'RESERVED' || s.stage === 'SOLD'">{{ stageName(s.stage) }}</span>
              <span class="badge badge-danger" *ngIf="s.overdue" style="margin-left:0.3rem;">Late</span></td>
            <td class="text-end" data-label="List price">{{ s.listPrice | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Agreed price">{{ s.agreedPrice | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Paid">{{ s.stage === 'RESERVED' || s.stage === 'CANCELLED' ? '—' : (s.paid | number: '1.0-0') }}</td>
            <td class="text-end" data-label="Still owed">{{ s.stage === 'RESERVED' || s.stage === 'CANCELLED' ? '—' : (s.outstanding | number: '1.0-0') }}</td>
            <td data-label="Agent">{{ s.agentName || '—' }}</td>
            <td style="white-space:nowrap;" data-label="Date">{{ dateOf(s) }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">No unit sales match. Reserve a unit from its page in a project.</p>
    </app-shell>
  `,
})
export class UnitSaleListComponent implements OnInit {
  list: UnitSaleRow[] = [];
  projects: ProjectRow[] = [];
  loaded = false;
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  stage = '';
  projectId: number | null = null;
  openOnly = true;
  q = '';
  error: string | null = null;
  stages = (Object.keys(UNIT_SALE_STAGE_LABEL) as UnitSaleStage[]).map((k) => ({ value: k, label: UNIT_SALE_STAGE_LABEL[k] }));

  constructor(private api: RealEstateApiService) {}

  ngOnInit(): void {
    this.api.projects({}).subscribe({ next: (p) => (this.projects = p), error: () => {} });
    this.load();
  }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.unitSalesPage({ q: this.q.trim(), stage: this.stage, projectId: this.projectId, openOnly: this.openOnly, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the sales'; },
    });
  }

  stageName(s: string): string { return (UNIT_SALE_STAGE_LABEL as Record<string, string>)[s] ?? s; }

  dateOf(s: UnitSaleRow): string {
    const f = (x: string) => new Date(x + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
    if (s.stage === 'COMPLETED') return s.completedDate ? 'paid up ' + f(s.completedDate) : '—';
    if (s.stage === 'SOLD') return s.agreementDate ? 'signed ' + f(s.agreementDate) : '—';
    if (s.stage === 'RESERVED') return s.reservationExpiry ? 'held to ' + f(s.reservationExpiry) : s.reservedOn ? 'reserved ' + f(s.reservedOn) : '—';
    return '—';
  }
}
