import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { HrApiService, OrgChart } from '../../core/api/hr-api.service';

/** Organizational Structure > Organization chart: departments, and who reports to whom. */
@Component({
  selector: 'app-org-chart',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>Organization chart</h3>
        <a class="btn btn-outline-primary" routerLink="/admin/hr/org/setup">Departments and positions</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-start;" *ngIf="chart">
        <div class="card" style="padding:1rem; flex:1; min-width:20rem;">
          <strong class="mb-2">Departments</strong>
          <ul class="org-tree">
            <ng-container *ngTemplateOutlet="deptTpl; context: { nodes: chart.departments }"></ng-container>
          </ul>
          <p class="text-muted" *ngIf="!chart.departments.length">No departments yet.</p>
        </div>
        <div class="card" style="padding:1rem; flex:1; min-width:20rem;">
          <strong class="mb-2">Reporting lines</strong>
          <ul class="org-tree">
            <ng-container *ngTemplateOutlet="personTpl; context: { nodes: chart.people }"></ng-container>
          </ul>
          <p class="text-muted" *ngIf="!chart.people.length">No employees yet.</p>
        </div>
      </div>

      <ng-template #deptTpl let-nodes="nodes">
        <li *ngFor="let n of nodes">
          <span class="org-node"><strong>{{ n.name }}</strong> <span class="text-muted">· {{ n.employeeCount }} {{ n.employeeCount === 1 ? 'person' : 'people' }}{{ n.managerName ? ' · managed by ' + n.managerName : '' }}</span></span>
          <ul *ngIf="n.children.length"><ng-container *ngTemplateOutlet="deptTpl; context: { nodes: n.children }"></ng-container></ul>
        </li>
      </ng-template>
      <ng-template #personTpl let-nodes="nodes">
        <li *ngFor="let n of nodes">
          <span class="org-node"><a [routerLink]="['/admin/hr/employees', n.id]">{{ n.name }}</a> <span class="text-muted">{{ n.jobTitle || '' }}{{ n.departmentName ? ' · ' + n.departmentName : '' }}</span></span>
          <ul *ngIf="n.reports.length"><ng-container *ngTemplateOutlet="personTpl; context: { nodes: n.reports }"></ng-container></ul>
        </li>
      </ng-template>
    </app-shell>
  `,
  styles: [
    `
      .org-tree, .org-tree ul { list-style: none; margin: 0; padding-left: 1.25rem; border-left: 1px solid var(--border-color, #d0d7de); }
      .org-tree { border-left: none; padding-left: 0; }
      .org-tree li { margin: 0.35rem 0; }
      .org-node { display: inline-block; padding: 0.15rem 0.5rem; }
    `,
  ],
})
export class OrgChartComponent implements OnInit {
  chart: OrgChart | null = null;
  error: string | null = null;

  constructor(private api: HrApiService) {}

  ngOnInit(): void {
    this.api.orgChart().subscribe({ next: (c) => (this.chart = c), error: (e) => (this.error = e?.error?.message ?? 'Failed to load the chart') });
  }
}

