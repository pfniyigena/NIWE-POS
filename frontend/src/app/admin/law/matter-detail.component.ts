import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { QuickCustomerComponent } from '../../shared/quick-customer.component';
import { MatterBillingComponent } from './matter-billing.component';
import { MatterTrustComponent } from './matter-trust.component';
import { MatterPapersComponent } from './matter-papers.component';
import { MatterDocumentsComponent } from './matter-documents.component';
import { MatterPortalComponent } from './matter-portal.component';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Customer } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import {
  BILLING_LABEL, ConflictResult, Contact, ContactType, DEADLINE_TYPE_LABEL, DeadlineType, LawApiService, Matter, MatterPayload, NOTE_KIND_LABEL, NoteKind, Option, PARTY_ROLE_LABEL,
  PartyRole, STATUS_LABEL, TEAM_ROLE_LABEL, TaskPriority, TeamRole,
} from '../../core/api/law-api.service';
import { today } from '../../core/time-util';

type Tab = 'deadlines' | 'tasks' | 'parties' | 'team' | 'diary' | 'documents' | 'billing' | 'trust' | 'papers' | 'portal' | 'conflicts';

/**
 * One matter: open a new one (/admin/law/matters/new) or work an existing one (/law/matters/:id, open to any login — the server decides
 * who may see or change it). Deadlines, tasks, parties, the team, the diary and the conflict check each have a tab.
 */
@Component({
  selector: 'app-law-matter-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, QuickCustomerComponent, MatterBillingComponent, MatterTrustComponent, MatterPapersComponent, MatterDocumentsComponent, MatterPortalComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ isNew ? ('Open a matter' | translate) : matter?.number || ('Matter' | translate) }}<span *ngIf="matter && !isNew" style="font-weight:400;"> · {{ matter.title }}</span></h3>
        <a class="btn btn-outline-secondary" [routerLink]="canSeeList ? '/admin/law/matters' : '/law/mine'">{{ 'Back' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>

      <!-- open / edit form -->
      <form class="card mb-3" style="padding:1rem; max-width:52rem;" *ngIf="isNew || editing" (ngSubmit)="saveMatter()">
        <div class="form-group mb-2"><label class="form-label">{{ 'Title' | translate }}</label><input class="form-control" name="ti" [(ngModel)]="f.title" required [placeholder]="'e.g. Mukamana v. Habimana — land dispute' | translate" /></div>
        <div class="form-group mb-2" *ngIf="isNew">
          <label class="form-label">{{ 'Client' | translate }}</label>
          <select class="form-select" name="cl" [(ngModel)]="f.clientCustomerId" required>
            <option [ngValue]="null">{{ 'Choose a client…' | translate }}</option>
            <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}<span *ngIf="c.phone"> · {{ c.phone }}</span></option>
          </select>
          <app-quick-customer (created)="onCustomerCreated($event)"></app-quick-customer>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'Run the conflict check on the next screen before you start work.' | translate }}</div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Lead lawyer' | translate }}</label>
            <select class="form-select" name="ld" [(ngModel)]="f.leadUserId"><option [ngValue]="null">{{ 'Me' | translate }}</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.name }}</option></select></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Practice area' | translate }}</label>
            <select class="form-select" name="pa" [(ngModel)]="f.practiceArea"><option [ngValue]="null">—</option><option *ngFor="let p of areas" [ngValue]="p">{{ p }}</option></select></div>
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Jurisdiction' | translate }}</label>
            <select class="form-select" name="ju" [(ngModel)]="f.jurisdiction"><option [ngValue]="null">—</option><option *ngFor="let p of jurisdictions" [ngValue]="p">{{ p }}</option></select></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Court or forum' | translate }}</label><input class="form-control" name="co" [(ngModel)]="courtOrForum" /></div>
          <div class="form-group mb-2" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Court case number' | translate }}</label><input class="form-control" name="cn" [(ngModel)]="courtCaseNumber" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'Opened' | translate }}</label><app-date-picker name="od" [(ngModel)]="openedDate"></app-date-picker></div>
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'How it is billed' | translate }}</label>
            <select class="form-select" name="bt" [(ngModel)]="f.billingType">
              <option *ngFor="let b of billingTypes" [value]="b.key">{{ b.label }}</option>
            </select></div>
          <div class="form-group mb-2" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Fee agreement' | translate }} <span class="text-muted">{{ '(rate, fixed fee, retainer, percentage)' | translate }}</span></label>
            <input class="form-control" name="bn" [(ngModel)]="billingNote" /></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Description' | translate }}</label><textarea class="form-control" rows="3" name="de" [(ngModel)]="description"></textarea></div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-3"><input type="checkbox" name="rs" [(ngModel)]="f.restricted" /> {{ 'Restricted — only the team and admins can open this matter' | translate }}</label>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : isNew ? ('Open matter' | translate) : ('Save' | translate) }}</button>
          <button class="btn btn-outline-secondary" type="button" *ngIf="editing" (click)="editing = false">{{ 'Cancel' | translate }}</button>
        </div>
      </form>

      <ng-container *ngIf="matter && !isNew">
        <!-- overview -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="!editing">
          <div class="flex justify-between" style="flex-wrap:wrap; gap:1rem;">
            <div>
              <div>
                <span class="badge" [class.badge-success]="matter.status === 'OPEN'" [class.badge-warning]="matter.status === 'ON_HOLD'" [class.badge-secondary]="matter.status === 'CLOSED'">{{ statusLabel(matter.status) }}</span>
                <span class="badge badge-secondary" *ngIf="matter.restricted" style="margin-left:0.3rem;">{{ 'Restricted' | translate }}</span>
                <span class="badge" style="margin-left:0.3rem;" [class.badge-success]="matter.conflictCleared" [class.badge-danger]="!matter.conflictCleared">{{ matter.conflictCleared ? ('Conflicts cleared' | translate) : ('Conflicts not cleared' | translate) }}</span>
              </div>
              <div class="mt-2">{{ 'Client:' | translate }} <a [routerLink]="['/admin/customers', matter.clientId]" *ngIf="canSeeList">{{ matter.clientName }}</a><strong *ngIf="!canSeeList">{{ matter.clientName }}</strong>
                <span class="text-muted" *ngIf="matter.clientPhone"> · {{ matter.clientPhone }}</span><span class="text-muted" *ngIf="matter.clientEmail"> · {{ matter.clientEmail }}</span></div>
              <div>{{ 'Lead lawyer:' | translate }} <strong>{{ matter.leadName || '—' }}</strong></div>
              <div *ngIf="matter.practiceArea">{{ 'Practice area: {p0}' | translate: { p0: (matter.practiceArea) } }}<span *ngIf="matter.jurisdiction"> · {{ matter.jurisdiction }}</span></div>
              <div *ngIf="matter.courtOrForum || matter.courtCaseNumber">{{ 'Court: {p0}' | translate: { p0: (matter.courtOrForum || '—') } }}<span *ngIf="matter.courtCaseNumber"> {{ '· case no. {p0}' | translate: { p0: (matter.courtCaseNumber) } }}</span></div>
              <div>{{ 'Billing: {p0}' | translate: { p0: (billing(matter.billingType)) } }}<span class="text-muted" *ngIf="matter.billingNote"> — {{ matter.billingNote }}</span></div>
              <div class="text-muted" style="font-size:0.85rem;">{{ 'Opened {p0}' | translate: { p0: (matter.openedDate) } }}<span *ngIf="matter.createdByName"> {{ 'by {p0}' | translate: { p0: (matter.createdByName) } }}</span><span *ngIf="matter.closedDate"> {{ '· closed {p0}' | translate: { p0: (matter.closedDate) } }}</span></div>
              <div *ngIf="matter.outcome" class="mt-1"><strong>{{ 'Outcome:' | translate }}</strong> {{ matter.outcome }}</div>
              <p class="mt-2" style="white-space:pre-line;" *ngIf="matter.description">{{ matter.description }}</p>
            </div>
            <div class="flex-col gap-2" style="min-width:12rem;" *ngIf="matter.canLead">
              <button class="btn btn-outline-secondary" (click)="startEdit()">{{ 'Edit details' | translate }}</button>
              <button class="btn btn-outline-secondary" *ngIf="matter.status === 'OPEN'" (click)="setStatus('ON_HOLD')" [disabled]="busy">{{ 'Put on hold' | translate }}</button>
              <button class="btn btn-outline-secondary" *ngIf="matter.status === 'ON_HOLD'" (click)="setStatus('OPEN')" [disabled]="busy">{{ 'Resume' | translate }}</button>
              <button class="btn btn-outline-secondary" *ngIf="matter.status === 'CLOSED'" (click)="setStatus('OPEN')" [disabled]="busy">{{ 'Reopen' | translate }}</button>
              <button class="btn btn-outline-danger" *ngIf="matter.status !== 'CLOSED'" (click)="closeOpen = !closeOpen">{{ 'Close matter' | translate }}</button>
              <button class="btn btn-sm btn-outline-danger" *ngIf="canSeeList && !matter.deadlines.length && !matter.tasks.length && !matter.notes.length" (click)="remove()">{{ 'Delete' | translate }}</button>
            </div>
          </div>
          <div class="mt-3" *ngIf="closeOpen">
            <label class="form-label">{{ 'Outcome' | translate }} <span class="text-muted">{{ '(how it ended)' | translate }}</span></label>
            <textarea class="form-control" rows="2" name="oc" [(ngModel)]="outcome"></textarea>
            <div class="mt-2 flex gap-2">
              <button class="btn btn-danger" (click)="close(false)" [disabled]="busy">{{ 'Close matter' | translate }}</button>
              <button class="btn btn-danger" *ngIf="needForce" (click)="close(true)" [disabled]="busy">{{ 'Close it anyway' | translate }}</button>
              <button class="btn btn-outline-secondary" (click)="closeOpen = false">{{ 'Cancel' | translate }}</button>
            </div>
          </div>
        </div>

        <!-- tabs -->
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;">
          <button *ngFor="let t of tabs" class="btn" [class.btn-primary]="tab === t.key" [class.btn-outline-secondary]="tab !== t.key" (click)="tab = t.key">{{ t.label }}<span *ngIf="count(t.key)"> ({{ count(t.key) }})</span></button>
        </div>

        <!-- deadlines -->
        <div class="card" style="padding:1rem;" *ngIf="tab === 'deadlines'">
          <table class="stack table-sm" *ngIf="matter.deadlines.length">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'Assigned to' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let d of matter.deadlines" [style.opacity]="d.status === 'OPEN' ? 1 : 0.6">
                <td [style.color]="d.overdue ? '#c0392b' : ''" data-label="Date"><strong>{{ d.dueDate }}</strong><span *ngIf="d.dueTime"> {{ d.dueTime }}</span>
                  <span *ngIf="d.overdue"> {{ '(overdue)' | translate }}</span><span class="text-muted" *ngIf="d.status === 'OPEN' && !d.overdue" style="font-size:0.8rem;"> · {{ d.daysLeft === 0 ? 'today' : d.daysLeft + ' d' }}</span></td>
                <td data-label="What">{{ d.title }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="d.notes">{{ d.notes }}</div></td>
                <td data-label="Type">{{ deadlineType(d.type) }}</td><td data-label="Assigned to">{{ d.assignedToName || '—' }}</td>
                <td data-label="Status"><span class="badge" [class.badge-success]="d.status === 'DONE'" [class.badge-secondary]="d.status !== 'DONE'">{{ d.status.charAt(0) + d.status.slice(1).toLowerCase() }}</span>
                  <span class="text-muted" style="font-size:0.75rem;" *ngIf="d.completedByName"> {{ d.completedByName }}</span></td>
                <td *ngIf="matter.canWork">
                  <button class="btn btn-sm btn-outline-success" *ngIf="d.status === 'OPEN'" (click)="deadlineDone(d.id)">{{ 'Done' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="d.status !== 'OPEN'" (click)="deadlineReopen(d.id)">{{ 'Reopen' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" style="margin-left:0.3rem;" (click)="editDeadline(d)">{{ 'Edit' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger" style="margin-left:0.3rem;" (click)="deleteDeadline(d.id)">{{ 'Delete' | translate }}</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!matter.deadlines.length">{{ 'No deadlines yet. Add court dates, filing dates and limitation dates so nothing is missed.' | translate }}</p>
          <form *ngIf="matter.canWork" class="mt-3" (ngSubmit)="saveDeadline()" style="border-top:1px solid var(--border, #d0d7de); padding-top:0.8rem;">
            <strong>{{ dlEditing ? ('Edit deadline' | translate) : ('Add a deadline' | translate) }}</strong>
            <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:end;">
              <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'What' | translate }}</label><input class="form-control" name="dt" [(ngModel)]="dl.title" required /></div>
              <div class="form-group"><label class="form-label">{{ 'Type' | translate }}</label>
                <select class="form-select" name="dty" [(ngModel)]="dl.type"><option *ngFor="let t of deadlineTypes" [value]="t.key">{{ t.label }}</option></select></div>
              <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="dd" [(ngModel)]="dl.dueDate" required></app-date-picker></div>
              <div class="form-group"><label class="form-label">{{ 'Time' | translate }}</label><input class="form-control" type="time" name="dtm" [(ngModel)]="dl.dueTime" /></div>
              <div class="form-group"><label class="form-label">{{ 'Assigned to' | translate }}</label>
                <select class="form-select" name="da" [(ngModel)]="dl.assignedToUserId"><option [ngValue]="null">{{ 'Lead lawyer' | translate }}</option><option *ngFor="let t of matter.team" [ngValue]="t.userId">{{ t.userName }}</option></select></div>
              <div class="form-group" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Notes' | translate }}</label><input class="form-control" name="dn" [(ngModel)]="dl.notes" /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ dlEditing ? ('Save' | translate) : ('Add' | translate) }}</button>
              <button class="btn btn-outline-secondary" type="button" *ngIf="dlEditing" (click)="resetDeadline()">{{ 'Cancel' | translate }}</button>
            </div>
          </form>
        </div>

        <!-- tasks -->
        <div class="card" style="padding:1rem;" *ngIf="tab === 'tasks'">
          <table class="stack table-sm" *ngIf="matter.tasks.length">
            <thead><tr><th style="width:2rem;"></th><th>{{ 'Task' | translate }}</th><th>{{ 'Due' | translate }}</th><th>{{ 'Assigned to' | translate }}</th><th>{{ 'Priority' | translate }}</th><th>{{ 'Status' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let t of matter.tasks" [style.opacity]="t.status === 'DONE' ? 0.6 : 1">
                <td><input type="checkbox" [checked]="t.status === 'DONE'" [disabled]="!matter.canWork" (change)="toggleTask(t.id, t.status)" /></td>
                <td [style.textDecoration]="t.status === 'DONE' ? 'line-through' : ''" data-label="Task">{{ t.title }}<div class="text-muted" style="font-size:0.8rem;" *ngIf="t.description">{{ t.description }}</div></td>
                <td [style.color]="t.overdue ? '#c0392b' : ''" data-label="Due">{{ t.dueDate || '—' }}<span *ngIf="t.overdue"> {{ '(late)' | translate }}</span></td>
                <td data-label="Assigned to">{{ t.assignedToName || '—' }}</td><td data-label="Priority">{{ t.priority.charAt(0) + t.priority.slice(1).toLowerCase() }}</td>
                <td data-label="Status">{{ t.status === 'IN_PROGRESS' ? 'In progress' : t.status === 'DONE' ? ('Done' | translate) : ('To do' | translate) }}</td>
                <td *ngIf="matter.canWork">
                  <button class="btn btn-sm btn-outline-secondary" *ngIf="t.status === 'TODO'" (click)="startTask(t.id)">{{ 'Start' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" style="margin-left:0.3rem;" (click)="editTask(t)">{{ 'Edit' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger" style="margin-left:0.3rem;" (click)="deleteTask(t.id)">{{ 'Delete' | translate }}</button>
                </td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!matter.tasks.length">{{ 'No tasks yet.' | translate }}</p>
          <form *ngIf="matter.canWork" class="mt-3" (ngSubmit)="saveTask()" style="border-top:1px solid var(--border, #d0d7de); padding-top:0.8rem;">
            <strong>{{ tkEditing ? ('Edit task' | translate) : ('Add a task' | translate) }}</strong>
            <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:end;">
              <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Task' | translate }}</label><input class="form-control" name="tt" [(ngModel)]="tk.title" required /></div>
              <div class="form-group"><label class="form-label">{{ 'Due' | translate }}</label><app-date-picker name="td" [(ngModel)]="tk.dueDate"></app-date-picker></div>
              <div class="form-group"><label class="form-label">{{ 'Assigned to' | translate }}</label>
                <select class="form-select" name="ta" [(ngModel)]="tk.assignedToUserId"><option [ngValue]="null">{{ 'Nobody yet' | translate }}</option><option *ngFor="let t of matter.team" [ngValue]="t.userId">{{ t.userName }}</option></select></div>
              <div class="form-group"><label class="form-label">{{ 'Priority' | translate }}</label>
                <select class="form-select" name="tp" [(ngModel)]="tk.priority"><option value="LOW">{{ 'Low' | translate }}</option><option value="NORMAL">{{ 'Normal' | translate }}</option><option value="HIGH">{{ 'High' | translate }}</option><option value="URGENT">{{ 'Urgent' | translate }}</option></select></div>
              <div class="form-group" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Details' | translate }}</label><input class="form-control" name="tn" [(ngModel)]="tk.description" /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ tkEditing ? ('Save' | translate) : ('Add' | translate) }}</button>
              <button class="btn btn-outline-secondary" type="button" *ngIf="tkEditing" (click)="resetTask()">{{ 'Cancel' | translate }}</button>
            </div>
          </form>
        </div>

        <!-- parties -->
        <div class="card" style="padding:1rem;" *ngIf="tab === 'parties'">
          <table class="stack table-sm" *ngIf="matter.parties.length">
            <thead><tr><th>{{ 'Name' | translate }}</th><th>{{ 'Role' | translate }}</th><th>{{ 'Phone' | translate }}</th><th>{{ 'E-mail' | translate }}</th><th>{{ 'ID' | translate }}</th><th>{{ 'Note' | translate }}</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let p of matter.parties">
                <td data-label="Name">{{ p.name }}</td><td data-label="Role">{{ partyRole(p.role) }}</td><td data-label="Phone">{{ p.phone || '—' }}</td><td data-label="E-mail">{{ p.email || '—' }}</td><td data-label="ID">{{ p.idNumber || '—' }}</td><td data-label="Note">{{ p.note || '' }}</td>
                <td *ngIf="matter.canWork"><button class="btn btn-sm btn-outline-danger" (click)="removeParty(p.id)">{{ 'Remove' | translate }}</button></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!matter.parties.length">{{ 'No opposing parties, lawyers or witnesses recorded. Add them so the conflict check can see them.' | translate }}</p>
          <div *ngIf="matter.canWork" class="mt-3" style="border-top:1px solid var(--border, #d0d7de); padding-top:0.8rem;">
            <strong>{{ 'Add a party' | translate }}</strong>
            <form class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:end;" (ngSubmit)="addParty()">
              <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Person or organisation' | translate }}</label>
                <select class="form-select" name="pc" [(ngModel)]="pty.contactId" required>
                  <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
                  <option *ngFor="let c of contacts" [ngValue]="c.id">{{ c.name }}<span *ngIf="c.phone"> · {{ c.phone }}</span></option>
                </select></div>
              <div class="form-group"><label class="form-label">{{ 'Role' | translate }}</label>
                <select class="form-select" name="pr" [(ngModel)]="pty.role"><option *ngFor="let r of partyRoles" [value]="r.key">{{ r.label }}</option></select></div>
              <div class="form-group" style="flex:1; min-width:10rem;"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="pn" [(ngModel)]="pty.note" /></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Add' | translate }}</button>
              <button class="btn btn-outline-secondary" type="button" (click)="newContactOpen = !newContactOpen">{{ '+ New contact' | translate }}</button>
            </form>
            <form class="card mt-2" style="padding:0.8rem;" *ngIf="newContactOpen" (ngSubmit)="createContact()">
              <div class="flex gap-2" style="flex-wrap:wrap; align-items:end;">
                <div class="form-group" style="flex:2; min-width:12rem;"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="cn1" [(ngModel)]="nc.name" required /></div>
                <div class="form-group"><label class="form-label">{{ 'Kind' | translate }}</label>
                  <select class="form-select" name="ct1" [(ngModel)]="nc.type"><option value="PERSON">{{ 'Person' | translate }}</option><option value="COMPANY">{{ 'Company' | translate }}</option><option value="GOVERNMENT">{{ 'Government / court' | translate }}</option></select></div>
                <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="cp1" [(ngModel)]="nc.phone" /></div>
                <div class="form-group"><label class="form-label">{{ 'ID / registration no.' | translate }}</label><input class="form-control" name="ci1" [(ngModel)]="nc.idNumber" /></div>
                <button class="btn btn-primary" type="submit">{{ 'Save contact' | translate }}</button>
              </div>
            </form>
          </div>
        </div>

        <!-- team -->
        <div class="card" style="padding:1rem;" *ngIf="tab === 'team'">
          <table class="stack table-sm" *ngIf="!teamEditing">
            <thead><tr><th>{{ 'Person' | translate }}</th><th>{{ 'Role' | translate }}</th></tr></thead>
            <tbody><tr *ngFor="let t of matter.team"><td data-label="Person">{{ t.userName }}</td><td data-label="Role">{{ teamRole(t.role) }}</td></tr></tbody>
          </table>
          <button class="btn btn-outline-secondary mt-2" *ngIf="matter.canLead && !teamEditing" (click)="startTeamEdit()">{{ 'Change team' | translate }}</button>
          <ng-container *ngIf="teamEditing">
            <div class="flex gap-2 mb-2" *ngFor="let m of teamEdit; let i = index" style="align-items:center;">
              <select class="form-select" style="width:16rem;" [name]="'tu' + i" [(ngModel)]="m.userId"><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.name }}</option></select>
              <select class="form-select" style="width:12rem;" [name]="'tr' + i" [(ngModel)]="m.role"><option *ngFor="let r of teamRoles" [value]="r.key">{{ r.label }}</option></select>
              <button class="btn btn-sm btn-outline-danger" type="button" (click)="teamEdit.splice(i, 1)">{{ 'Remove' | translate }}</button>
            </div>
            <div class="flex gap-2">
              <button class="btn btn-outline-secondary" type="button" (click)="addMember()">{{ '+ Add person' | translate }}</button>
              <button class="btn btn-primary" type="button" (click)="saveTeam()" [disabled]="busy">{{ 'Save team' | translate }}</button>
              <button class="btn btn-outline-secondary" type="button" (click)="teamEditing = false">{{ 'Cancel' | translate }}</button>
            </div>
          </ng-container>
        </div>

        <!-- diary -->
        <div class="card" style="padding:1rem;" *ngIf="tab === 'diary'">
          <form *ngIf="matter.canWork" (ngSubmit)="addNote()" class="mb-3">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:end;">
              <div class="form-group"><label class="form-label">{{ 'Kind' | translate }}</label>
                <select class="form-select" name="nk" [(ngModel)]="nt.kind"><option *ngFor="let k of noteKinds" [value]="k.key">{{ k.label }}</option></select></div>
              <div class="form-group" style="flex:1; min-width:16rem;"><label class="form-label">{{ 'Entry' | translate }}</label><textarea class="form-control" rows="2" name="nx" [(ngModel)]="nt.text" required></textarea></div>
              <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Add to diary' | translate }}</button>
            </div>
          </form>
          <div *ngFor="let n of matter.notes" class="mb-2" style="border-left:3px solid var(--border, #d0d7de); padding-left:0.7rem;">
            <div class="text-muted" style="font-size:0.8rem;"><span class="badge badge-secondary">{{ noteKind(n.kind) }}</span> {{ n.createdAt | date: 'd MMM y, HH:mm' }} · {{ n.createdByName }}
              <a href="javascript:void(0)" *ngIf="n.canDelete" (click)="deleteNote(n.id)" style="margin-left:0.5rem; color:#c0392b;">{{ 'delete' | translate }}</a></div>
            <div style="white-space:pre-line;">{{ n.text }}</div>
          </div>
          <p class="text-muted" *ngIf="!matter.notes.length">{{ 'The diary is empty.' | translate }}</p>
        </div>

        <!-- conflicts -->
        <div *ngIf="tab === 'billing'">
          <app-law-matter-billing [matterId]="matter.id"></app-law-matter-billing>
        </div>

        <div *ngIf="tab === 'documents'">
          <app-law-matter-documents [matterId]="matter.id"></app-law-matter-documents>
        </div>

        <div *ngIf="tab === 'portal'">
          <app-law-matter-portal [matterId]="matter.id"></app-law-matter-portal>
        </div>

        <div *ngIf="tab === 'papers'">
          <app-law-matter-papers [matterId]="matter.id"></app-law-matter-papers>
        </div>

        <div *ngIf="tab === 'trust'">
          <app-law-matter-trust [matterId]="matter.id"></app-law-matter-trust>
        </div>

        <div class="card" style="padding:1rem;" *ngIf="tab === 'conflicts'">
          <div *ngIf="matter.conflictCleared" class="alert alert-success">{{ 'Cleared by {p0} on {p1}.' | translate: { p0: (matter.conflictClearedBy), p1: (matter.conflictClearedAt | date: 'd MMM y, HH:mm') } }}
            <span *ngIf="matter.conflictNote"><br>{{ matter.conflictNote }}</span></div>
          <div *ngIf="!matter.conflictCleared" class="alert alert-warning">{{ 'Conflicts have not been cleared on this matter. Run the check, then clear it. Adding a party clears it again until re-checked.' | translate }}</div>
          <button class="btn btn-primary" (click)="runCheck()" [disabled]="busy || !matter.canWork">{{ checking ? ('Checking…' | translate) : ('Run conflict check' | translate) }}</button>

          <ng-container *ngIf="check">
            <p class="mt-3" *ngIf="!check.hits"><strong>{{ 'No overlaps found' | translate }}</strong> {{ 'for the client or the {p0} recorded part{p1}.' | translate: { p0: (matter.parties.length), p1: (matter.parties.length === 1 ? 'y' : 'ies') } }}</p>
            <ng-container *ngFor="let s of check.subjects">
              <div *ngFor="let h of s.hits" class="card mt-2" style="padding:0.9rem;" [style.borderLeft]="h.severity === 'HIGH' ? '4px solid #c0392b' : '4px solid #e6a700'">
                <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
                  <div><strong>{{ s.label }}</strong> <span class="text-muted">({{ subjectRole(s.role) }})</span> {{ 'matches' | translate }} <strong>{{ h.name }}</strong>
                    <span class="text-muted" style="font-size:0.85rem;"> {{ 'on {p0}' | translate: { p0: (h.reasons.join(', ')) } }}</span></div>
                  <span class="badge" [class.badge-danger]="h.severity === 'HIGH'" [class.badge-warning]="h.severity !== 'HIGH'">{{ h.severity === 'HIGH' ? ('Opposite sides' | translate) : ('Review' | translate) }}</span>
                </div>
                <ul style="margin:0.4rem 0 0 1rem;">
                  <li *ngFor="let r of h.matters">
                    <span *ngIf="!r.hidden"><a [routerLink]="['/law/matters', r.id]">{{ r.number }}</a> {{ r.title }}</span><span *ngIf="r.hidden" class="text-muted">{{ 'A restricted matter' | translate }}</span>
                    — {{ r.role === 'CLIENT' ? 'Client' : partyRole(r.role) }} <span class="text-muted">({{ r.status.toLowerCase().replace('_', ' ') }})</span>
                  </li>
                </ul>
              </div>
            </ng-container>
          </ng-container>

          <div class="mt-3" *ngIf="matter.canLead && !matter.conflictCleared">
            <label class="form-label">{{ 'Note' | translate }} <span class="text-muted">{{ '(required when overlaps were found: why they do not stop the firm acting)' | translate }}</span></label>
            <textarea class="form-control" rows="2" name="cc" [(ngModel)]="clearNote"></textarea>
            <button class="btn btn-success mt-2" (click)="clear()" [disabled]="busy">{{ 'Clear conflicts' | translate }}</button>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class LawMatterDetailComponent implements OnInit {
  isNew = false;
  canSeeList = true;
  matter: Matter | null = null;
  staff: Option[] = [];
  customers: Customer[] = [];
  contacts: Contact[] = [];
  areas: string[] = [];
  jurisdictions: string[] = [];
  editing = false;
  saving = false;
  busy = false;
  checking = false;
  error: string | null = null;
  info: string | null = null;
  tab: Tab = 'deadlines';

  f: MatterPayload = { title: '', clientCustomerId: null, billingType: 'HOURLY', restricted: false, leadUserId: null, practiceArea: null, jurisdiction: null };
  description = '';
  courtOrForum = '';
  courtCaseNumber = '';
  billingNote = '';
  openedDate = today();

  closeOpen = false;
  needForce = false;
  outcome = '';

  dl: { title: string; type: DeadlineType; dueDate: string; dueTime: string; assignedToUserId: number | null; notes: string } = this.blankDeadline();
  dlEditing: number | null = null;
  tk: { title: string; description: string; dueDate: string | null; assignedToUserId: number | null; priority: TaskPriority } = this.blankTask();
  tkEditing: number | null = null;
  pty: { contactId: number | null; role: PartyRole; note: string } = { contactId: null, role: 'OPPOSING_PARTY', note: '' };
  newContactOpen = false;
  nc: { name: string; type: ContactType; phone: string; idNumber: string } = { name: '', type: 'PERSON', phone: '', idNumber: '' };
  teamEditing = false;
  teamEdit: { userId: number; role: TeamRole }[] = [];
  nt: { kind: NoteKind; text: string } = { kind: 'NOTE', text: '' };
  check: ConflictResult | null = null;
  clearNote = '';

  tabs: { key: Tab; label: string }[] = [
    { key: 'deadlines', label: 'Deadlines' }, { key: 'tasks', label: 'Tasks' }, { key: 'parties', label: 'Parties' },
    { key: 'team', label: 'Team' }, { key: 'diary', label: 'Diary' }, { key: 'documents', label: 'Documents' }, { key: 'billing', label: 'Billing' }, { key: 'trust', label: 'Client money' }, { key: 'papers', label: 'Papers' }, { key: 'portal', label: 'Client portal' }, { key: 'conflicts', label: 'Conflict check' },
  ];
  billingTypes = Object.entries(BILLING_LABEL).map(([key, label]) => ({ key, label }));
  deadlineTypes = Object.entries(DEADLINE_TYPE_LABEL).map(([key, label]) => ({ key, label }));
  partyRoles = Object.entries(PARTY_ROLE_LABEL).map(([key, label]) => ({ key, label }));
  teamRoles = Object.entries(TEAM_ROLE_LABEL).map(([key, label]) => ({ key, label }));
  noteKinds = Object.entries(NOTE_KIND_LABEL).map(([key, label]) => ({ key, label }));

  constructor(private auth: AuthService, private api: LawApiService, private customerApi: CustomerApiService, private route: ActivatedRoute, private router: Router) {}

  private blankDeadline() { return { title: '', type: 'OTHER' as DeadlineType, dueDate: '', dueTime: '', assignedToUserId: null as number | null, notes: '' }; }
  private blankTask() { return { title: '', description: '', dueDate: null as string | null, assignedToUserId: null as number | null, priority: 'NORMAL' as TaskPriority }; }

  ngOnInit(): void {
    this.isNew = this.router.url.includes('/matters/new');
    this.canSeeList = this.auth.canAccessPath('/admin/law/matters');
    this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
    this.api.settings().subscribe({
      next: (s) => {
        this.areas = s.practiceAreas;
        this.jurisdictions = s.jurisdictions;
        if (this.isNew) { this.f.billingType = s.defaultBillingType; this.f.restricted = s.restrictedByDefault; }
      },
      error: () => {},
    });
    if (this.isNew) {
      this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !x.walkIn)), error: () => {} });
      const c = this.route.snapshot.queryParamMap.get('clientId');
      if (c) this.f.clientCustomerId = Number(c);
    } else {
      const wanted = this.route.snapshot.queryParamMap.get('tab');
      if (wanted && this.tabs.some((t) => t.key === wanted)) this.tab = wanted as Tab;
      this.load();
      this.api.contacts().subscribe({ next: (l) => (this.contacts = l), error: () => {} });
    }
  }

  load(): void {
    this.api.matter(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (m) => { this.matter = m; if (!m.conflictCleared && m.canWork && this.tab === 'deadlines' && !m.deadlines.length) this.tab = 'conflicts'; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the matter'),
    });
  }

  // ---- helpers
  statusLabel(s: string): string { return (STATUS_LABEL as Record<string, string>)[s] ?? s; }
  billing(b: string): string { return (BILLING_LABEL as Record<string, string>)[b] ?? b; }
  deadlineType(t: string): string { return (DEADLINE_TYPE_LABEL as Record<string, string>)[t] ?? t; }
  partyRole(r: string): string { return (PARTY_ROLE_LABEL as Record<string, string>)[r] ?? r; }
  teamRole(r: string): string { return (TEAM_ROLE_LABEL as Record<string, string>)[r] ?? r; }
  noteKind(k: string): string { return (NOTE_KIND_LABEL as Record<string, string>)[k] ?? k; }
  subjectRole(r: string | null): string { return r === 'CLIENT' ? 'client' : r ? this.partyRole(r).toLowerCase() : 'search'; }

  count(t: Tab): number {
    if (!this.matter) return 0;
    switch (t) {
      case 'deadlines': return this.matter.deadlines.filter((d) => d.status === 'OPEN').length;
      case 'tasks': return this.matter.tasks.filter((x) => x.status !== 'DONE').length;
      case 'parties': return this.matter.parties.length;
      case 'team': return this.matter.team.length;
      case 'diary': return this.matter.notes.length;
      default: return 0;
    }
  }

  /** Runs a matter-returning call, shows the result, and reports a failure in the banner. */
  private run(call: { subscribe: (o: { next: (m: Matter) => void; error: (e: any) => void }) => unknown }, message?: string, after?: () => void): void {
    this.busy = true;
    this.error = null;
    this.info = null;
    call.subscribe({
      next: (m) => { this.matter = m; this.busy = false; if (message) this.info = message; if (after) after(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; if (e?.status === 409 && this.closeOpen) this.needForce = true; },
    });
  }

  // ---- open / edit
  onCustomerCreated(c: Customer): void {
    this.customers = [...this.customers, c].sort((a, b) => a.name.localeCompare(b.name));
    this.f.clientCustomerId = c.id;
  }

  startEdit(): void {
    const m = this.matter!;
    this.f = { title: m.title, clientCustomerId: m.clientId, billingType: m.billingType, restricted: m.restricted, leadUserId: m.leadUserId, practiceArea: m.practiceArea, jurisdiction: m.jurisdiction };
    this.description = m.description ?? '';
    this.courtOrForum = m.courtOrForum ?? '';
    this.courtCaseNumber = m.courtCaseNumber ?? '';
    this.billingNote = m.billingNote ?? '';
    this.openedDate = m.openedDate ?? today();
    this.editing = true;
  }

  saveMatter(): void {
    this.saving = true;
    this.error = null;
    const p: MatterPayload = { ...this.f, title: this.f.title.trim(), description: this.description.trim() || null, courtOrForum: this.courtOrForum.trim() || null,
      courtCaseNumber: this.courtCaseNumber.trim() || null, billingNote: this.billingNote.trim() || null, openedDate: this.openedDate };
    if (this.isNew) {
      this.api.open(p).subscribe({
        next: (m) => { this.saving = false; this.router.navigate(['/law/matters', m.id]); },
        error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not open the matter'; },
      });
    } else {
      this.api.update(this.matter!.id, p).subscribe({
        next: (m) => { this.saving = false; this.matter = m; this.editing = false; this.info = 'Saved.'; },
        error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the matter'; },
      });
    }
  }

  setStatus(s: 'OPEN' | 'ON_HOLD'): void { this.run(this.api.setStatus(this.matter!.id, s, null, false), s === 'ON_HOLD' ? 'Put on hold.' : 'Open again.'); }

  close(force: boolean): void {
    this.run(this.api.setStatus(this.matter!.id, 'CLOSED', this.outcome.trim() || null, force), 'Matter closed.', () => { this.closeOpen = false; this.needForce = false; });
  }

  remove(): void {
    if (!confirm('Delete this matter? This cannot be undone.')) return;
    this.api.deleteMatter(this.matter!.id).subscribe({ next: () => this.router.navigate(['/admin/law/matters']), error: (e) => (this.error = e?.error?.message ?? 'Could not delete the matter') });
  }

  // ---- deadlines
  resetDeadline(): void { this.dl = this.blankDeadline(); this.dlEditing = null; }

  editDeadline(d: Matter['deadlines'][number]): void {
    this.dlEditing = d.id;
    this.dl = { title: d.title, type: d.type, dueDate: d.dueDate, dueTime: d.dueTime ? d.dueTime.slice(0, 5) : '', assignedToUserId: d.assignedToUserId, notes: d.notes ?? '' };
  }

  saveDeadline(): void {
    if (!this.dl.dueDate) { this.error = 'Choose the date.'; return; }
    const p = { title: this.dl.title.trim(), type: this.dl.type, dueDate: this.dl.dueDate, dueTime: this.dl.dueTime || null, assignedToUserId: this.dl.assignedToUserId, notes: this.dl.notes.trim() || null };
    const call = this.dlEditing ? this.api.updateDeadline(this.dlEditing, p) : this.api.addDeadline(this.matter!.id, p);
    this.run(call, this.dlEditing ? 'Deadline saved.' : 'Deadline added.', () => this.resetDeadline());
  }

  deadlineDone(id: number): void { this.run(this.api.deadlineDone(id)); }
  deadlineReopen(id: number): void { this.run(this.api.deadlineReopen(id)); }
  deleteDeadline(id: number): void { if (confirm('Delete this deadline?')) this.run(this.api.deleteDeadline(id)); }

  // ---- tasks
  resetTask(): void { this.tk = this.blankTask(); this.tkEditing = null; }

  editTask(t: Matter['tasks'][number]): void {
    this.tkEditing = t.id;
    this.tk = { title: t.title, description: t.description ?? '', dueDate: t.dueDate, assignedToUserId: t.assignedToUserId, priority: t.priority };
  }

  saveTask(): void {
    const p = { title: this.tk.title.trim(), description: this.tk.description.trim() || null, dueDate: this.tk.dueDate || null, assignedToUserId: this.tk.assignedToUserId, priority: this.tk.priority };
    const call = this.tkEditing ? this.api.updateTask(this.tkEditing, p) : this.api.addTask(this.matter!.id, p);
    this.run(call, this.tkEditing ? 'Task saved.' : 'Task added.', () => this.resetTask());
  }

  toggleTask(id: number, status: string): void { this.run(this.api.taskStatus(id, status === 'DONE' ? 'TODO' : 'DONE')); }
  startTask(id: number): void { this.run(this.api.taskStatus(id, 'IN_PROGRESS')); }
  deleteTask(id: number): void { if (confirm('Delete this task?')) this.run(this.api.deleteTask(id)); }

  // ---- parties
  addParty(): void {
    if (!this.pty.contactId) return;
    this.run(this.api.addParty(this.matter!.id, this.pty.contactId, this.pty.role, this.pty.note.trim() || null), 'Party added. Run the conflict check again.', () => {
      this.pty = { contactId: null, role: 'OPPOSING_PARTY', note: '' };
      this.check = null;
    });
  }

  removeParty(id: number): void { this.run(this.api.removeParty(this.matter!.id, id)); }

  createContact(): void {
    this.api.createContact({ name: this.nc.name.trim(), type: this.nc.type, phone: this.nc.phone.trim() || null, idNumber: this.nc.idNumber.trim() || null }).subscribe({
      next: (c) => {
        this.contacts = [...this.contacts, c].sort((a, b) => a.name.localeCompare(b.name));
        this.pty.contactId = c.id;
        this.newContactOpen = false;
        this.nc = { name: '', type: 'PERSON', phone: '', idNumber: '' };
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not save the contact'),
    });
  }

  // ---- team
  startTeamEdit(): void {
    this.teamEdit = this.matter!.team.map((t) => ({ userId: t.userId, role: t.role }));
    this.teamEditing = true;
  }

  addMember(): void {
    const first = this.staff.find((u) => !this.teamEdit.some((m) => m.userId === u.id)) ?? this.staff[0];
    if (first) this.teamEdit.push({ userId: first.id, role: 'ASSOCIATE' });
  }

  saveTeam(): void { this.run(this.api.setTeam(this.matter!.id, this.teamEdit), 'Team saved.', () => (this.teamEditing = false)); }

  // ---- diary
  addNote(): void {
    this.run(this.api.addNote(this.matter!.id, this.nt.kind, this.nt.text.trim()), undefined, () => (this.nt = { kind: 'NOTE', text: '' }));
  }

  deleteNote(id: number): void { if (confirm('Delete this diary entry?')) this.run(this.api.deleteNote(this.matter!.id, id)); }

  // ---- conflicts
  runCheck(): void {
    this.checking = true;
    this.error = null;
    this.api.conflictCheck(this.matter!.id).subscribe({
      next: (r) => { this.check = r; this.checking = false; },
      error: (e) => { this.checking = false; this.error = e?.error?.message ?? 'Could not run the check'; },
    });
  }

  clear(): void {
    this.run(this.api.conflictClear(this.matter!.id, this.clearNote.trim() || null), 'Conflicts cleared.', () => (this.clearNote = ''));
  }
}
