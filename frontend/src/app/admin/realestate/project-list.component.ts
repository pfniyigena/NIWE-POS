import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReExportComponent } from './re-export.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { AuthService } from '../../core/auth.service';
import { PROJECT_STATUS_LABEL, ProjectRow, ProjectStatus, RealEstateApiService } from '../../core/api/realestate-api.service';

/** Real Estate > Projects: the developments we sell units in, with how many units are left. */
@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent, ReExportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Projects</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/realestate/unit-sales">Unit sales</a>
          <a class="btn btn-primary" *ngIf="canManage" routerLink="/admin/realestate/projects/new">+ New project</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">All projects</option><option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
        </select>
        <input class="form-control" style="width:15rem;" placeholder="Search project, area, developer" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <app-re-export kind="projects" [filters]="{ q: q.trim(), status: status }"></app-re-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Project</th><th>Area</th><th>Developer</th><th>Status</th><th class="text-end">Units</th><th class="text-end">Available</th><th class="text-end">Reserved</th><th class="text-end">Sold</th><th class="text-end">Stock value</th><th class="text-end">Sold value</th><th>Handover</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of list">
            <td data-label="Project"><a [routerLink]="['/admin/realestate/projects', p.id]"><strong>{{ p.name }}</strong></a><div class="text-muted" style="font-size:0.8rem;">{{ p.code }}</div></td>
            <td data-label="Area">{{ p.area || '—' }}</td>
            <td data-label="Developer">{{ p.developerName || '—' }}</td>
            <td data-label="Status"><span class="badge" [class.badge-success]="p.status === 'SELLING'" [class.badge-secondary]="p.status === 'PLANNING' || p.status === 'ARCHIVED'" [class.badge-warning]="p.status === 'COMPLETED'">{{ statusName(p.status) }}</span></td>
            <td class="text-end" data-label="Units">{{ p.totalUnits }}</td>
            <td class="text-end" data-label="Available">{{ p.available }}</td>
            <td class="text-end" data-label="Reserved">{{ p.reserved }}</td>
            <td class="text-end" data-label="Sold">{{ p.sold }}</td>
            <td class="text-end" data-label="Stock value">{{ p.stockValue | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Sold value">{{ p.soldValue | number: '1.0-0' }}</td>
            <td data-label="Handover">{{ p.handoverDate ? (p.handoverDate | date: 'MMM y') : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">No projects yet. A project is a development such as an estate of plots or an apartment block; you add its units and sell them one by one.</p>
    </app-shell>
  `,
})
export class ProjectListComponent implements OnInit {
  list: ProjectRow[] = [];
  loaded = false;
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  status = '';
  q = '';
  error: string | null = null;
  canManage: boolean;
  statuses = (Object.keys(PROJECT_STATUS_LABEL) as ProjectStatus[]).map((k) => ({ value: k, label: PROJECT_STATUS_LABEL[k] }));

  constructor(private api: RealEstateApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void { this.load(); }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.projectsPage({ q: this.q.trim(), status: this.status, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the projects'; },
    });
  }

  statusName(s: string): string { return (PROJECT_STATUS_LABEL as Record<string, string>)[s] ?? s; }
}
