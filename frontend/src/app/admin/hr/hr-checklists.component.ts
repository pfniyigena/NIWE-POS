import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { ChecklistKind, ChecklistTemplate, HrApiService, OpenTask } from '../../core/api/hr-api.service';

interface DraftItem { title: string; responsible: string; dueOffsetDays: number; }
interface Draft { id: number | null; name: string; kind: ChecklistKind; description: string; active: boolean; items: DraftItem[]; }

/** Employees > Checklists: tasks still to do for new starters and leavers, and the templates the checklists are started from. */
@Component({
  selector: 'app-hr-checklists',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Onboarding and offboarding</h3>
        <div class="flex gap-2">
          <button class="btn tab-open" type="button" [ngClass]="tab === 'open' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'open'">Open tasks</button>
          <button class="btn tab-templates" type="button" [ngClass]="tab === 'templates' ? 'btn-primary' : 'btn-outline-secondary'" (click)="tab = 'templates'">Templates</button>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <ng-container *ngIf="tab === 'open'">
        <form class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;" (ngSubmit)="loadTasks()">
          <input class="form-control task-who" name="who" [(ngModel)]="who" placeholder="Who is responsible (IT, Manager…)" style="max-width:18rem;" />
          <select class="form-select task-kind" style="width:auto;" name="kind" [(ngModel)]="kind" (ngModelChange)="loadTasks()">
            <option value="">All checklists</option><option value="ONBOARDING">Onboarding</option><option value="OFFBOARDING">Offboarding</option>
          </select>
          <label style="display:flex; gap:0.4rem; align-items:center;"><input class="task-overdue" type="checkbox" name="od" [(ngModel)]="overdueOnly" (ngModelChange)="loadTasks()" /> Overdue only</label>
          <button class="btn btn-outline-secondary" type="submit">Search</button>
        </form>
        <table class="stack table-sm task-table">
          <thead><tr><th>Employee</th><th>Checklist</th><th>Task</th><th>Who</th><th>Due</th><th></th></tr></thead>
          <tbody><tr *ngFor="let t of tasks; trackBy: byItem">
            <td data-label="Employee"><a [routerLink]="['/admin/hr/employees', t.employeeId]">{{ t.employeeName }}</a><div class="text-muted" style="font-size:0.8rem;">{{ t.employeeNumber }}</div></td>
            <td data-label="Checklist">{{ t.checklistName }}</td>
            <td data-label="Task">{{ t.title }}</td>
            <td data-label="Who">{{ t.responsible || '—' }}</td>
            <td data-label="Due">{{ t.dueDate || '—' }} <span class="badge badge-danger" *ngIf="t.overdue">Overdue</span></td>
            <td data-label=""><button class="btn btn-sm btn-outline-primary task-done" type="button" *ngIf="canManage" (click)="done(t)">Mark done</button></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="loaded && !tasks.length">Nothing is waiting. Start a checklist from an employee's page.</p>
      </ng-container>

      <ng-container *ngIf="tab === 'templates'">
        <div class="flex justify-between align-center mb-2">
          <span class="text-muted">A template is a ready list of tasks. Due dates count in days from the hire date (onboarding) or the last working day (offboarding).</span>
          <button class="btn btn-sm btn-primary tpl-new" type="button" *ngIf="canManage && !draft" (click)="newTemplate()">+ New template</button>
        </div>

        <form class="card mb-3 tpl-form" style="padding:1rem;" *ngIf="draft" (ngSubmit)="save()">
          <div class="flex gap-3" style="flex-wrap:wrap;">
            <div class="form-group" style="min-width:16rem;"><label class="form-label">Name</label><input class="form-control tpl-name" name="tn" [(ngModel)]="draft.name" /></div>
            <div class="form-group"><label class="form-label">For</label>
              <select class="form-control tpl-kind" name="tk" [(ngModel)]="draft.kind"><option value="ONBOARDING">New starters</option><option value="OFFBOARDING">Leavers</option></select>
            </div>
            <label style="display:flex; gap:0.4rem; align-items:center; margin-top:1.6rem;"><input class="tpl-active" type="checkbox" name="ta" [(ngModel)]="draft.active" /> In use</label>
          </div>
          <div class="form-group"><label class="form-label">Description (optional)</label><input class="form-control" name="td" [(ngModel)]="draft.description" /></div>
          <strong class="mb-1">Tasks</strong>
          <div class="flex gap-2 mb-1 align-center tpl-item" *ngFor="let it of draft.items; let i = index; trackBy: byIndex" style="flex-wrap:wrap;">
            <input class="form-control tpl-item-title" name="it{{ i }}" [(ngModel)]="it.title" placeholder="Task" style="min-width:16rem; flex:1;" />
            <input class="form-control tpl-item-who" name="iw{{ i }}" [(ngModel)]="it.responsible" placeholder="Who" style="width:9rem;" />
            <input class="form-control tpl-item-days" type="number" name="id{{ i }}" [(ngModel)]="it.dueOffsetDays" style="width:6rem;" title="Days after the start date" />
            <button class="btn btn-sm btn-outline-secondary" type="button" (click)="move(i, -1)" [disabled]="i === 0">↑</button>
            <button class="btn btn-sm btn-outline-secondary" type="button" (click)="move(i, 1)" [disabled]="i === draft.items.length - 1">↓</button>
            <button class="btn btn-sm btn-outline-danger tpl-item-del" type="button" (click)="draft.items.splice(i, 1)">Remove</button>
          </div>
          <div class="mb-2"><button class="btn btn-sm btn-outline-primary tpl-item-add" type="button" (click)="draft.items.push({ title: '', responsible: '', dueOffsetDays: 0 })">+ Add a task</button></div>
          <div class="flex gap-2">
            <button class="btn btn-primary tpl-save" type="submit">Save template</button>
            <button class="btn btn-outline-secondary" type="button" (click)="draft = null">Cancel</button>
          </div>
        </form>

        <table class="stack table-sm tpl-table" *ngIf="templates.length">
          <thead><tr><th>Name</th><th>For</th><th>Tasks</th><th>In use</th><th></th></tr></thead>
          <tbody><tr *ngFor="let t of templates; trackBy: byTpl">
            <td data-label="Name">{{ t.name }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="t.description">{{ t.description }}</div></td>
            <td data-label="For">{{ t.kind === 'ONBOARDING' ? 'New starters' : 'Leavers' }}</td>
            <td data-label="Tasks">{{ t.items.length }}</td>
            <td data-label="In use">{{ t.active ? 'Yes' : 'No' }}</td>
            <td data-label="" class="no-wrap" *ngIf="canManage">
              <button class="btn btn-sm btn-outline-secondary tpl-edit" type="button" (click)="edit(t)">Edit</button>
              <button class="btn btn-sm btn-outline-danger tpl-delete" type="button" (click)="remove(t)">Delete</button>
            </td>
            <td data-label="" *ngIf="!canManage"></td>
          </tr></tbody>
        </table>
        <p class="text-muted" *ngIf="loadedTemplates && !templates.length">No templates yet.</p>
      </ng-container>
    </app-shell>
  `,
})
export class HrChecklistsComponent implements OnInit, OnDestroy {
  private sub?: Subscription;
  tab: 'open' | 'templates' = 'open';
  tasks: OpenTask[] = [];
  templates: ChecklistTemplate[] = [];
  who = '';
  kind = '';
  overdueOnly = false;
  loaded = false;
  loadedTemplates = false;
  error: string | null = null;
  draft: Draft | null = null;
  canManage = false;

  constructor(private api: HrApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.canManage = this.auth.canAccessPath('/admin/hr/employees/new');
    this.loadTasks();
    this.loadTemplates();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  byItem(_: number, t: OpenTask): number { return t.itemId; }
  byTpl(_: number, t: ChecklistTemplate): number { return t.id; }
  byIndex(i: number): number { return i; }

  loadTasks(): void {
    this.sub?.unsubscribe();
    this.sub = this.api.openTasks({ responsible: this.who.trim(), kind: this.kind, overdueOnly: this.overdueOnly }).subscribe({
      next: (t) => { this.tasks = t; this.loaded = true; this.error = null; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the tasks'; },
    });
  }

  loadTemplates(): void {
    this.api.checklistTemplates().subscribe({
      next: (t) => { this.templates = t; this.loadedTemplates = true; },
      error: (e) => { this.loadedTemplates = true; this.error = e?.error?.message ?? 'Could not load the templates'; },
    });
  }

  done(t: OpenTask): void {
    this.api.setTaskDone(t.itemId, true, null).subscribe({
      next: () => this.loadTasks(),
      error: (e) => (this.error = e?.error?.message ?? 'Could not change the task'),
    });
  }

  newTemplate(): void {
    this.draft = { id: null, name: '', kind: 'ONBOARDING', description: '', active: true, items: [{ title: '', responsible: '', dueOffsetDays: 0 }] };
  }

  edit(t: ChecklistTemplate): void {
    this.draft = {
      id: t.id, name: t.name, kind: t.kind, description: t.description ?? '', active: t.active,
      items: t.items.map((i) => ({ title: i.title, responsible: i.responsible ?? '', dueOffsetDays: i.dueOffsetDays })),
    };
  }

  move(i: number, by: number): void {
    if (!this.draft) return;
    const items = this.draft.items;
    const j = i + by;
    if (j < 0 || j >= items.length) return;
    [items[i], items[j]] = [items[j], items[i]];
  }

  save(): void {
    const d = this.draft;
    if (!d) return;
    const payload = {
      name: d.name.trim(), kind: d.kind, description: d.description.trim() || null, active: d.active,
      items: d.items.filter((i) => i.title.trim()).map((i) => ({ title: i.title.trim(), responsible: i.responsible.trim(), dueOffsetDays: Number(i.dueOffsetDays) || 0 })),
    };
    const call = d.id ? this.api.updateChecklistTemplate(d.id, payload) : this.api.createChecklistTemplate(payload);
    call.subscribe({
      next: () => { this.draft = null; this.error = null; this.loadTemplates(); },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the template'),
    });
  }

  remove(t: ChecklistTemplate): void {
    if (!confirm(`Delete the template "${t.name}"? Checklists already started from it stay as they are.`)) return;
    this.api.deleteChecklistTemplate(t.id).subscribe({
      next: () => this.loadTemplates(),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the template'),
    });
  }
}
