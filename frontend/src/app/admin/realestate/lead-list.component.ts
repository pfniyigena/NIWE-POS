import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReExportComponent } from './re-export.component';
import { ReImportComponent } from './re-import.component';
import { AuthService } from '../../core/auth.service';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { INTEREST_LABEL, LEAD_STATUS_LABEL, LeadRow, Option, RealEstateApiService, SOURCE_LABEL } from '../../core/api/realestate-api.service';

/** Real Estate > Leads: everyone who has asked about a property, with who looks after them and when the next call is due. */
@Component({
  selector: 'app-lead-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ReExportComponent, ReImportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Leads</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary imp-open" type="button" *ngIf="canManage" (click)="importOpen = !importOpen">Import</button>
          <a class="btn btn-primary" routerLink="/admin/realestate/leads/new">+ Add lead</a>
        </div>
      </div>
      <app-re-import kind="leads" *ngIf="importOpen" (imported)="load()" (closed)="importOpen = false"></app-re-import>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All stages</option><option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="in" [(ngModel)]="interest" (ngModelChange)="load()">
          <option value="">Buy or rent</option><option value="BUY">Buying</option><option value="RENT">Renting</option>
        </select>
        <select class="form-select" style="width:auto;" name="as" [(ngModel)]="assignedId" (ngModelChange)="load()">
          <option [ngValue]="null">Any agent</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option>
        </select>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="oo" [(ngModel)]="openOnly" (ngModelChange)="load()" /> Open only</label>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="do" [(ngModel)]="dueOnly" (ngModelChange)="load()" /> Follow-up overdue</label>
        <input class="form-control" style="width:15rem;" placeholder="Search name, phone, e-mail" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <app-re-export kind="leads" [filters]="{ q: q.trim(), status: status, interest: interest, assignedId: assignedId, openOnly: openOnly, dueOnly: dueOnly }"></app-re-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Name</th><th>Looking to</th><th>Budget</th><th>Area</th><th>Property</th><th>Stage</th><th>Agent</th><th>Next follow-up</th><th>Last contact</th><th class="text-end">Viewings</th></tr></thead>
        <tbody>
          <tr *ngFor="let l of list">
            <td data-label="Name"><a [routerLink]="['/admin/realestate/leads', l.id]"><strong>{{ l.name }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ l.phone || l.email || '—' }} · {{ source(l.source) }}</div></td>
            <td data-label="Looking to">{{ interestName(l.interest) }}</td>
            <td data-label="Budget">{{ budget(l) }}</td>
            <td data-label="Area">{{ l.preferredArea || '—' }}</td>
            <td data-label="Property"><a *ngIf="l.propertyId" [routerLink]="['/admin/realestate/properties', l.propertyId]">{{ l.propertyCode }}</a><span *ngIf="!l.propertyId">—</span></td>
            <td data-label="Stage"><span class="badge" [class.badge-success]="l.status === 'WON'" [class.badge-danger]="l.status === 'LOST'" [class.badge-warning]="l.status === 'NEGOTIATING' || l.status === 'VIEWING'" [class.badge-secondary]="l.status === 'NEW' || l.status === 'CONTACTED'">{{ statusName(l.status) }}</span>
              <span class="badge badge-warning" *ngIf="l.stale" style="margin-left:0.3rem;" title="Nothing has happened for a while">Quiet</span></td>
            <td data-label="Agent">{{ l.assignedName || '—' }}</td>
            <td [style.color]="l.followUpOverdue ? '#b91c1c' : ''" data-label="Next follow-up">{{ l.nextFollowUp ? (l.nextFollowUp | date: 'd MMM y') : '—' }}<span *ngIf="l.followUpOverdue"> (overdue)</span></td>
            <td data-label="Last contact">{{ l.lastContactAt ? (l.lastContactAt | date: 'd MMM') : '—' }}</td>
            <td class="text-end" data-label="Viewings">{{ l.viewings }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">No leads match.</p>
    </app-shell>
  `,
})
export class LeadListComponent implements OnInit {
  list: LeadRow[] = [];
  loaded = false;
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  agents: Option[] = [];
  statuses = (Object.keys(LEAD_STATUS_LABEL) as (keyof typeof LEAD_STATUS_LABEL)[]).map((k) => ({ value: k, label: LEAD_STATUS_LABEL[k] }));
  status = '';
  interest = '';
  assignedId: number | null = null;
  openOnly = true;
  dueOnly = false;
  q = '';
  error: string | null = null;

  canManage: boolean;
  importOpen = false;

  constructor(private api: RealEstateApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void {
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.load();
  }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.leadsPage({ q: this.q.trim(), status: this.status, interest: this.interest, assignedId: this.assignedId, openOnly: this.openOnly, dueOnly: this.dueOnly, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the leads'),
    });
  }

  statusName(s: string): string { return (LEAD_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  interestName(s: string): string { return (INTEREST_LABEL as Record<string, string>)[s] ?? s; }
  source(s: string): string { return (SOURCE_LABEL as Record<string, string>)[s] ?? s; }
  budget(l: LeadRow): string {
    const f = (n: number) => Math.round(n).toLocaleString();
    if (l.budgetMin != null && l.budgetMax != null) return `${f(l.budgetMin)} – ${f(l.budgetMax)}`;
    if (l.budgetMax != null) return `up to ${f(l.budgetMax)}`;
    if (l.budgetMin != null) return `from ${f(l.budgetMin)}`;
    return '—';
  }
}
