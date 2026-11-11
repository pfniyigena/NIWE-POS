import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { StaffOption, TimeApiService } from '../../core/api/time-api.service';
import { WorkOrderApiService, WorkOrderStatus, WorkOrderSummary } from '../../core/api/work-order-api.service';
import { WorkOrderTabsComponent } from './work-order-tabs.component';

import { T } from '../../core/i18n/t';
interface Column { status: WorkOrderStatus; title: string; items: WorkOrderSummary[]; }

/** Work Orders > Board: jobs in columns by status. Drag a card to start it or put it on hold; open it to complete it. */
@Component({
  selector: 'app-work-order-board',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, WorkOrderTabsComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-2">
        <h3>{{ 'Work orders' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/work-orders/new">{{ '+ New work order' | translate }}</a>
      </div>
      <app-work-order-tabs></app-work-order-tabs>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>

      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="as" [(ngModel)]="assignee" (ngModelChange)="rebuild()">
          <option [ngValue]="null">{{ 'Everyone' | translate }}</option>
          <option [ngValue]="0">{{ 'Unassigned' | translate }}</option>
          <option *ngFor="let s of staff" [ngValue]="s.id">{{ s.fullName }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ty" [(ngModel)]="type" (ngModelChange)="rebuild()">
          <option value="">{{ 'Customer and internal' | translate }}</option>
          <option value="CUSTOMER">{{ 'Customer jobs' | translate }}</option><option value="INTERNAL">{{ 'Internal maintenance' | translate }}</option>
        </select>
        <input class="form-control" style="width:14rem;" [placeholder]="'Search' | translate" name="q" [(ngModel)]="q" (ngModelChange)="rebuild()" />
        <span class="text-muted" style="font-size:0.8rem;">{{ 'Drag a card into' | translate }} <em>{{ 'In progress' | translate }}</em> {{ 'to start it, or into' | translate }} <em>{{ 'On hold' | translate }}</em>.</span>
      </div>

      <div class="board" style="display:flex; gap:0.75rem; align-items:flex-start; overflow-x:auto; padding-bottom:0.5rem;">
        <div *ngFor="let c of columns" class="board-col" [attr.data-status]="c.status"
             style="flex:1; min-width:15rem; background:var(--surface-alt); border-radius:6px; padding:0.6rem; min-height:12rem;"
             [style.outline]="over === c.status ? '2px dashed #3b82f6' : 'none'"
             (dragover)="allow($event, c)" (dragleave)="over = null" (drop)="drop($event, c)">
          <div class="flex justify-between" style="margin-bottom:0.5rem;"><strong>{{ c.title }}</strong><span class="badge badge-secondary">{{ c.items.length }}</span></div>
          <div *ngFor="let w of c.items" class="board-card" [attr.data-id]="w.id" [attr.draggable]="w.status !== 'COMPLETED'" (dragstart)="dragStart($event, w)" (dragend)="over = null"
               style="background:var(--surface); border:1px solid var(--border); border-radius:5px; padding:0.5rem; margin-bottom:0.5rem; font-size:0.85rem;"
               [style.borderLeft]="w.priority === 'URGENT' ? '4px solid #c0392b' : w.priority === 'HIGH' ? '4px solid #e0a100' : '1px solid var(--border)'">
            <div class="flex justify-between"><a [routerLink]="['/admin/work-orders', w.id]"><strong>{{ w.number }}</strong></a>
              <span class="text-muted" style="font-size:0.75rem;">{{ w.type === 'CUSTOMER' ? ('Customer' | translate) : ('Internal' | translate) }}</span></div>
            <div>{{ w.title }}</div>
            <div class="text-muted" style="font-size:0.78rem;">{{ w.type === 'CUSTOMER' ? (w.customerName || '—') : (w.assetName || ('Internal' | translate)) }}</div>
            <div style="font-size:0.78rem;">{{ w.assignedToName || ('Unassigned' | translate) }}</div>
            <div style="font-size:0.78rem;" [style.color]="w.overdue ? '#c0392b' : ''" *ngIf="w.dueDate">{{ 'Due {p0}' | translate: { p0: (w.dueDate) } }}<span *ngIf="w.overdue"> {{ '(late)' | translate }}</span></div>
            <div style="margin-top:0.3rem;">
              <span class="badge badge-success" *ngIf="w.approvalRequired && w.approvalStatus === 'APPROVED'">{{ 'Approved' | translate }}</span>
              <span class="badge badge-warning" *ngIf="w.approvalRequired && (w.approvalStatus === 'NONE' || w.approvalStatus === 'REQUESTED')">{{ w.approvalStatus === 'REQUESTED' ? ('Waiting for customer' | translate) : ('Needs estimate' | translate) }}</span>
              <span class="badge badge-danger" *ngIf="w.approvalRequired && w.approvalStatus === 'REJECTED'">{{ 'Declined' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="w.scheduleId">{{ 'Planned' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="w.checklistTotal">{{ w.checklistDone }}/{{ w.checklistTotal }}</span>
            </div>
            <div class="flex gap-1" style="margin-top:0.35rem;" *ngIf="w.status !== 'COMPLETED'">
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="w.status === 'OPEN' || w.status === 'ON_HOLD'" (click)="move(w, 'IN_PROGRESS')">{{ w.status === 'ON_HOLD' ? ('Resume' | translate) : ('Start' | translate) }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="w.status === 'OPEN' || w.status === 'IN_PROGRESS'" (click)="move(w, 'ON_HOLD')">{{ 'Hold' | translate }}</button>
            </div>
          </div>
          <p class="text-muted" style="font-size:0.8rem;" *ngIf="!c.items.length">{{ 'Nothing here.' | translate }}</p>
        </div>
      </div>
    </app-shell>
  `,
})
export class WorkOrderBoardComponent implements OnInit {
  all: WorkOrderSummary[] = [];
  columns: Column[] = [];
  staff: StaffOption[] = [];
  assignee: number | null = null;
  type = '';
  q = '';
  error: string | null = null;
  notice: string | null = null;
  over: string | null = null;
  private dragging: WorkOrderSummary | null = null;

  constructor(private api: WorkOrderApiService, private timeApi: TimeApiService) {}

  ngOnInit(): void {
    this.timeApi.staff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    this.load();
  }

  load(): void {
    this.api.list({}).subscribe({
      next: (l) => { this.all = l.filter((w) => w.status !== 'CANCELLED'); this.rebuild(); },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load the work orders')),
    });
  }

  rebuild(): void {
    const q = this.q.trim().toLowerCase();
    const keep = (w: WorkOrderSummary) =>
      (this.assignee === null || (this.assignee === 0 ? w.assignedToUserId == null : w.assignedToUserId === this.assignee)) &&
      (!this.type || w.type === this.type) &&
      (!q || (w.number + ' ' + w.title + ' ' + (w.customerName ?? '') + ' ' + (w.assetName ?? '')).toLowerCase().includes(q));
    const rows = this.all.filter(keep);
    const mk = (status: WorkOrderStatus, title: string, max = 1000): Column => ({
      status, title, items: rows.filter((w) => w.status === status).slice(0, max),
    });
    this.columns = [mk('OPEN', 'Open'), mk('IN_PROGRESS', 'In progress'), mk('ON_HOLD', 'On hold'), mk('COMPLETED', 'Completed', 15)];
  }

  dragStart(ev: DragEvent, w: WorkOrderSummary): void {
    if (w.status === 'COMPLETED') { ev.preventDefault(); return; }
    this.dragging = w;
    ev.dataTransfer?.setData('text/plain', String(w.id));
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move';
  }

  allow(ev: DragEvent, c: Column): void {
    if (!this.dragging) return;
    ev.preventDefault();
    this.over = c.status;
  }

  drop(ev: DragEvent, c: Column): void {
    ev.preventDefault();
    this.over = null;
    const w = this.dragging;
    this.dragging = null;
    if (!w || w.status === c.status) return;
    this.move(w, c.status);
  }

  move(w: WorkOrderSummary, to: WorkOrderStatus): void {
    this.error = null;
    this.notice = null;
    if (to === 'OPEN') { this.notice = T('A job cannot go back to Open from here.'); return; }
    if (to === 'COMPLETED') { this.notice = T('Open {p0} to complete it — the checklist, parts and a note are checked there.', { p0: w.number }); return; }
    const call = to === 'IN_PROGRESS' ? this.api.start(w.id) : this.api.hold(w.id);
    call.subscribe({
      next: (d) => {
        const i = this.all.findIndex((x) => x.id === w.id);
        if (i >= 0) this.all[i] = { ...this.all[i], status: d.status, overdue: d.overdue, approvalStatus: d.approvalStatus, approvalRequired: d.approvalRequired };
        this.rebuild();
      },
      error: (e) => (this.error = w.number + ': ' + (e?.error?.message ?? 'could not change the status')),
    });
  }
}
