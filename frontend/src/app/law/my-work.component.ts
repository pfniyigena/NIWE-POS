import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { DEADLINE_TYPE_LABEL, LawApiService, Mine } from '../core/api/law-api.service';

import { T } from '../core/i18n/t';
/** Law Firm > My work: my deadlines (overdue and the next 30 days), my tasks and my open matters. Open to any login; the server shows only the person's own. */
@Component({
  selector: 'app-law-my-work',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'My work' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="m">
        <div class="card mb-3" style="padding:1rem;">
          <strong>{{ 'Deadlines' | translate }}</strong> <span class="text-muted" style="font-size:0.85rem;">{{ 'overdue and the next 30 days' | translate }}</span>
          <table class="stack table-sm mt-2" *ngIf="m.deadlines.length">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'Matter' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let d of m.deadlines">
                <td [style.color]="d.overdue ? '#c0392b' : ''" data-label="Date"><strong>{{ d.dueDate }}</strong><span *ngIf="d.dueTime"> {{ d.dueTime }}</span>
                  <span class="badge badge-danger" *ngIf="d.overdue" style="margin-left:0.3rem;">{{ 'Overdue' | translate }}</span>
                  <span class="text-muted" *ngIf="!d.overdue" style="font-size:0.8rem;"> · {{ d.daysLeft === 0 ? 'today' : d.daysLeft + ' d' }}</span></td>
                <td data-label="What">{{ d.title }}</td><td data-label="Type">{{ typeLabel(d.type) }}</td>
                <td data-label="Matter"><a [routerLink]="['/law/matters', d.matterId]">{{ d.matterNumber }}</a> <span class="text-muted">{{ d.matterTitle }}</span></td>
                <td><button class="btn btn-sm btn-outline-success" (click)="done(d.id)">{{ 'Done' | translate }}</button></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" *ngIf="!m.deadlines.length">{{ 'Nothing due.' | translate }}</p>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <strong>{{ 'My tasks' | translate }}</strong>
          <table class="stack table-sm mt-2" *ngIf="m.tasks.length">
            <thead><tr><th style="width:2rem;"></th><th>{{ 'Task' | translate }}</th><th>{{ 'Due' | translate }}</th><th>{{ 'Priority' | translate }}</th><th>{{ 'Matter' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let t of m.tasks">
                <td><input type="checkbox" (change)="finish(t.id)" [title]="'Mark done' | translate" /></td>
                <td data-label="Task">{{ t.title }}<span class="badge badge-secondary" *ngIf="t.status === 'IN_PROGRESS'" style="margin-left:0.3rem;">{{ 'In progress' | translate }}</span></td>
                <td [style.color]="t.overdue ? '#c0392b' : ''" data-label="Due">{{ t.dueDate || '—' }}<span *ngIf="t.overdue"> {{ '(late)' | translate }}</span></td>
                <td data-label="Priority"><span class="badge" [class.badge-danger]="t.priority === 'URGENT'" [class.badge-warning]="t.priority === 'HIGH'" [class.badge-secondary]="t.priority === 'NORMAL' || t.priority === 'LOW'">{{ t.priority.charAt(0) + t.priority.slice(1).toLowerCase() }}</span></td>
                <td data-label="Matter"><a [routerLink]="['/law/matters', t.matterId]">{{ t.matterNumber }}</a> <span class="text-muted">{{ t.matterTitle }}</span></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" *ngIf="!m.tasks.length">{{ 'No open tasks.' | translate }}</p>
        </div>

        <div class="card" style="padding:1rem;">
          <strong>{{ 'My matters' | translate }}</strong>
          <table class="stack table-sm mt-2" *ngIf="m.matters.length">
            <thead><tr><th>{{ 'Number' | translate }}</th><th>{{ 'Matter' | translate }}</th><th>{{ 'Client' | translate }}</th><th>{{ 'Practice area' | translate }}</th><th>{{ 'Lead' | translate }}</th><th>{{ 'Next deadline' | translate }}</th><th>{{ 'Open tasks' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let x of m.matters">
                <td data-label="Number"><a [routerLink]="['/law/matters', x.id]">{{ x.number }}</a></td>
                <td data-label="Matter">{{ x.title }}<span class="badge badge-warning" *ngIf="x.status === 'ON_HOLD'" style="margin-left:0.3rem;">{{ 'On hold' | translate }}</span>
                  <span class="badge badge-danger" *ngIf="!x.conflictCleared" style="margin-left:0.3rem;">{{ 'Conflicts not cleared' | translate }}</span></td>
                <td data-label="Client">{{ x.clientName }}</td><td data-label="Practice area">{{ x.practiceArea || '—' }}</td><td data-label="Lead">{{ x.leadName || '—' }}</td>
                <td [style.color]="x.overdueDeadlines ? '#c0392b' : ''" data-label="Next deadline">{{ x.nextDeadline || '—' }}<span *ngIf="x.overdueDeadlines"> {{ '({p0} overdue)' | translate: { p0: (x.overdueDeadlines) } }}</span></td>
                <td data-label="Open tasks">{{ x.openTasks }}</td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted mt-2" *ngIf="!m.matters.length">{{ 'You are not on any open matter.' | translate }}</p>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class LawMyWorkComponent implements OnInit {
  m: Mine | null = null;
  error: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.mine().subscribe({ next: (m) => (this.m = m), error: (e) => (this.error = e?.error?.message ?? T('Could not load your work')) });
  }

  typeLabel(t: string): string { return (DEADLINE_TYPE_LABEL as Record<string, string>)[t] ?? t; }

  done(id: number): void {
    this.api.deadlineDone(id).subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? T('Could not update the deadline')) });
  }

  finish(id: number): void {
    this.api.taskStatus(id, 'DONE').subscribe({ next: () => this.load(), error: (e) => (this.error = e?.error?.message ?? T('Could not update the task')) });
  }
}
