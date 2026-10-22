import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { LawExportComponent } from './law-export.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { DEADLINE_TYPE_LABEL, Deadline, LawApiService, Option, Task } from '../../core/api/law-api.service';
import { addDays, today } from '../../core/time-util';

/** Law Firm > Deadlines & tasks: everyone's open deadlines and tasks across matters, with the overdue ones on top. */
@Component({
  selector: 'app-law-work-all',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, PagerComponent, LawExportComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Deadlines &amp; tasks</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <span>Deadlines from</span><app-date-picker name="f" [(ngModel)]="from" (ngModelChange)="load()"></app-date-picker>
        <span>to</span><app-date-picker name="t" [(ngModel)]="to" (ngModelChange)="load()"></app-date-picker>
        <select class="form-select" style="width:auto;" name="a" [(ngModel)]="assigneeId" (ngModelChange)="load()">
          <option [ngValue]="null">Everyone</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.name }}</option>
        </select>
        <label style="display:flex; gap:0.3rem; align-items:center;"><input type="checkbox" name="d" [(ngModel)]="includeDone" (ngModelChange)="load()" /> Show finished</label>
      </div>

      <div class="card mb-3" style="padding:1rem;">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.4rem;"><strong>Deadlines</strong>
          <app-law-export kind="deadlines" [filters]="{ assigneeId: assigneeId, from: from, to: to, includeDone: includeDone }"></app-law-export></div>
        <table class="stack table-sm mt-2" *ngIf="deadlines.length">
          <thead><tr><th>Date</th><th>What</th><th>Type</th><th>Matter</th><th>Assigned to</th><th>Status</th></tr></thead>
          <tbody>
            <tr *ngFor="let d of deadlines">
              <td [style.color]="d.overdue ? '#c0392b' : ''" data-label="Date"><strong>{{ d.dueDate }}</strong><span *ngIf="d.dueTime"> {{ d.dueTime }}</span><span *ngIf="d.overdue"> (overdue)</span></td>
              <td data-label="What">{{ d.title }}</td><td data-label="Type">{{ typeLabel(d.type) }}</td>
              <td data-label="Matter"><a [routerLink]="['/law/matters', d.matterId]">{{ d.matterNumber }}</a> <span class="text-muted">{{ d.matterTitle }}</span></td>
              <td data-label="Assigned to">{{ d.assignedToName || '—' }}</td>
              <td data-label="Status"><span class="badge" [class.badge-success]="d.status === 'DONE'" [class.badge-secondary]="d.status !== 'DONE'">{{ d.status.charAt(0) + d.status.slice(1).toLowerCase() }}</span></td>
            </tr>
          </tbody>
        </table>
        <app-pager [page]="deadlinePage" [size]="size" [total]="deadlineTotal" (pageChange)="goDeadlines($event)"></app-pager>
        <p class="text-muted mt-2" *ngIf="!deadlines.length">No deadlines in this period.</p>
      </div>

      <div class="card" style="padding:1rem;">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.4rem;"><strong>Tasks</strong>
          <app-law-export kind="tasks" [filters]="{ assigneeId: assigneeId, includeDone: includeDone }"></app-law-export></div>
        <table class="stack table-sm mt-2" *ngIf="tasks.length">
          <thead><tr><th>Due</th><th>Task</th><th>Priority</th><th>Matter</th><th>Assigned to</th><th>Status</th></tr></thead>
          <tbody>
            <tr *ngFor="let t of tasks">
              <td [style.color]="t.overdue ? '#c0392b' : ''" data-label="Due">{{ t.dueDate || '—' }}<span *ngIf="t.overdue"> (late)</span></td>
              <td data-label="Task">{{ t.title }}</td><td data-label="Priority">{{ t.priority.charAt(0) + t.priority.slice(1).toLowerCase() }}</td>
              <td data-label="Matter"><a [routerLink]="['/law/matters', t.matterId]">{{ t.matterNumber }}</a> <span class="text-muted">{{ t.matterTitle }}</span></td>
              <td data-label="Assigned to">{{ t.assignedToName || '—' }}</td><td data-label="Status">{{ t.status === 'IN_PROGRESS' ? 'In progress' : t.status === 'DONE' ? 'Done' : 'To do' }}</td>
            </tr>
          </tbody>
        </table>
        <app-pager [page]="taskPage" [size]="size" [total]="taskTotal" (pageChange)="goTasks($event)"></app-pager>
        <p class="text-muted mt-2" *ngIf="!tasks.length">No tasks.</p>
      </div>
    </app-shell>
  `,
})
export class LawWorkAllComponent implements OnInit {
  from = addDays(today(), -30);
  to = addDays(today(), 60);
  assigneeId: number | null = null;
  includeDone = false;
  staff: Option[] = [];
  deadlines: Deadline[] = [];
  tasks: Task[] = [];
  deadlinePage = 0;
  deadlineTotal = 0;
  taskPage = 0;
  taskTotal = 0;
  readonly size = PAGE_SIZE;
  error: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void {
    this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
    this.load();
  }

  typeLabel(t: string): string { return (DEADLINE_TYPE_LABEL as Record<string, string>)[t] ?? t; }

  goDeadlines(p: number): void { this.deadlinePage = p; this.loadDeadlines(); }
  goTasks(p: number): void { this.taskPage = p; this.loadTasks(); }

  load(): void {
    this.deadlinePage = 0;
    this.taskPage = 0;
    this.error = null;
    this.loadDeadlines();
    this.loadTasks();
  }

  private loadDeadlines(): void {
    this.api.deadlinesPage({ assigneeId: this.assigneeId, from: this.from, to: this.to, includeDone: this.includeDone, page: this.deadlinePage, size: this.size }).subscribe({
      next: (r) => { this.deadlines = r.items; this.deadlineTotal = r.total; }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the deadlines'),
    });
  }

  private loadTasks(): void {
    this.api.tasksPage({ assigneeId: this.assigneeId, includeDone: this.includeDone, page: this.taskPage, size: this.size }).subscribe({
      next: (r) => { this.tasks = r.items; this.taskTotal = r.total; }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the tasks'),
    });
  }
}
