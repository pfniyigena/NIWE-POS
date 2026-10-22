import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { ChecklistTemplate, EmployeeChecklist, HrApiService } from '../../core/api/hr-api.service';

/** Onboarding and offboarding task lists of one employee: start one from a template, tick tasks off, add your own. */
@Component({
  selector: 'app-employee-checklists',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePickerComponent],
  template: `
    <div class="card mb-3 emp-checklists" style="padding:1rem;">
      <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
        <strong>Onboarding and offboarding</strong>
        <button class="btn btn-sm btn-outline-primary cl-start-open" type="button" *ngIf="canManage" (click)="showStart = !showStart">{{ showStart ? 'Close' : '+ Start a checklist' }}</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form *ngIf="showStart" class="mb-3" (ngSubmit)="start()" style="border:1px solid var(--border, #ddd); border-radius:6px; padding:0.75rem;">
        <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:16rem;"><label class="form-label">Template</label>
            <select class="form-control cl-template" name="tpl" [(ngModel)]="templateId">
              <option [ngValue]="null">Choose…</option>
              <option *ngFor="let t of templates" [ngValue]="t.id">{{ t.name }} ({{ t.kind === 'ONBOARDING' ? 'onboarding' : 'offboarding' }}, {{ t.items.length }} tasks)</option>
            </select>
          </div>
          <div class="form-group"><label class="form-label">Counts from (optional)</label><app-date-picker name="sd" [(ngModel)]="startDate"></app-date-picker></div>
          <button class="btn btn-primary cl-start" type="submit" [disabled]="!templateId || busy">Start</button>
        </div>
        <div class="text-muted" style="font-size:0.8rem;">Due dates count from the hire date (onboarding) or the last working day (offboarding) unless you pick a date.</div>
        <div class="text-muted" *ngIf="!templates.length">There are no templates yet. Create one under HR &gt; Checklists.</div>
      </form>

      <div *ngFor="let c of lists; trackBy: byId" class="mb-3 cl-list" style="border:1px solid var(--border, #ddd); border-radius:6px; padding:0.75rem;">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <div><strong>{{ c.name }}</strong>
            <span class="badge" style="margin-left:0.4rem;" [ngClass]="c.kind === 'ONBOARDING' ? 'badge-success' : 'badge-warning'">{{ c.kind === 'ONBOARDING' ? 'Onboarding' : 'Offboarding' }}</span>
            <span class="badge badge-secondary cl-progress" style="margin-left:0.3rem;">{{ c.done }} of {{ c.total }} done</span>
            <span class="badge badge-success" style="margin-left:0.3rem;" *ngIf="c.complete">Complete</span>
          </div>
          <button class="btn btn-sm btn-outline-danger cl-delete" type="button" *ngIf="canManage" (click)="removeList(c)">Delete</button>
        </div>
        <table class="stack table-sm mt-2">
          <thead><tr><th style="width:2.5rem;">Done</th><th>Task</th><th>Who</th><th>Due</th><th></th></tr></thead>
          <tbody><tr *ngFor="let t of c.items; trackBy: byId" class="cl-task" [class.cl-done]="t.done">
            <td data-label="Done"><input type="checkbox" class="cl-tick" [checked]="t.done" [disabled]="!canManage" (change)="tick(t.id, $any($event.target).checked)" /></td>
            <td data-label="Task"><span [style.text-decoration]="t.done ? 'line-through' : 'none'">{{ t.title }}</span>
              <div class="text-muted" style="font-size:0.8rem;" *ngIf="t.done">Done {{ t.doneAt | date: 'yyyy-MM-dd' }}<span *ngIf="t.doneByName"> by {{ t.doneByName }}</span></div></td>
            <td data-label="Who">{{ t.responsible || '—' }}</td>
            <td data-label="Due">{{ t.dueDate || '—' }} <span class="badge badge-danger" *ngIf="t.overdue">Overdue</span></td>
            <td data-label=""><button class="btn btn-sm btn-outline-danger cl-task-del" type="button" *ngIf="canManage" (click)="removeTask(t.id)">Remove</button></td>
          </tr></tbody>
        </table>
        <form class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;" *ngIf="canManage" (ngSubmit)="addTask(c)">
          <input class="form-control cl-new-title" name="nt{{ c.id }}" [(ngModel)]="draft(c.id).title" placeholder="Another task" style="min-width:14rem; flex:1;" />
          <input class="form-control cl-new-who" name="nw{{ c.id }}" [(ngModel)]="draft(c.id).who" placeholder="Who" style="width:9rem;" />
          <app-date-picker name="nd{{ c.id }}" [(ngModel)]="draft(c.id).due"></app-date-picker>
          <button class="btn btn-sm btn-outline-primary cl-new-add" type="submit" [disabled]="!draft(c.id).title.trim()">Add task</button>
        </form>
      </div>
      <p class="text-muted" *ngIf="loaded && !lists.length">No checklists started for this employee.</p>
    </div>
  `,
})
export class EmployeeChecklistsComponent implements OnChanges {
  @Input() employeeId = 0;
  @Input() canManage = false;

  lists: EmployeeChecklist[] = [];
  templates: ChecklistTemplate[] = [];
  showStart = false;
  templateId: number | null = null;
  startDate = '';
  loaded = false;
  busy = false;
  error: string | null = null;
  private drafts: Record<number, { title: string; who: string; due: string }> = {};

  constructor(private api: HrApiService) {}

  ngOnChanges(): void {
    if (!this.employeeId) return;
    this.load();
    if (this.canManage) {
      this.api.checklistTemplates().subscribe({ next: (t) => (this.templates = t.filter((x) => x.active)), error: () => {} });
    }
  }

  byId(_: number, x: { id: number }): number { return x.id; }

  draft(id: number): { title: string; who: string; due: string } {
    return (this.drafts[id] ??= { title: '', who: '', due: '' });
  }

  load(): void {
    this.api.employeeChecklists(this.employeeId).subscribe({
      next: (l) => { this.lists = l; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the checklists'; },
    });
  }

  private replace(c: EmployeeChecklist): void {
    this.lists = this.lists.map((x) => (x.id === c.id ? c : x));
    this.error = null;
  }

  start(): void {
    if (!this.templateId) return;
    this.busy = true;
    this.api.startChecklist(this.employeeId, this.templateId, this.startDate || null).subscribe({
      next: () => { this.busy = false; this.showStart = false; this.templateId = null; this.startDate = ''; this.error = null; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not start the checklist'; },
    });
  }

  tick(taskId: number, done: boolean): void {
    this.api.setTaskDone(taskId, done, null).subscribe({
      next: (c) => this.replace(c),
      error: (e) => { this.error = e?.error?.message ?? 'Could not change the task'; this.load(); },
    });
  }

  addTask(c: EmployeeChecklist): void {
    const d = this.draft(c.id);
    this.api.addChecklistTask(c.id, d.title.trim(), d.who.trim() || null, d.due || null).subscribe({
      next: (r) => { this.drafts[c.id] = { title: '', who: '', due: '' }; this.replace(r); },
      error: (e) => (this.error = e?.error?.message ?? 'Could not add the task'),
    });
  }

  removeTask(taskId: number): void {
    this.api.deleteChecklistTask(taskId).subscribe({
      next: (c) => this.replace(c),
      error: (e) => (this.error = e?.error?.message ?? 'Could not remove the task'),
    });
  }

  removeList(c: EmployeeChecklist): void {
    if (!confirm(`Delete the checklist "${c.name}" with all its tasks?`)) return;
    this.api.deleteChecklist(c.id).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the checklist'),
    });
  }
}
