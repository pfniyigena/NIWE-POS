import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { HelpDeskCannedApiService, HelpDeskCategoryApiService } from '../core/api/helpdesk-api.service';
import { HelpDeskCanned, HelpDeskCategory } from '../core/models';

import { T } from '../core/i18n/t';
/** Help Desk > Saved replies — ready-made answers agents insert into a reply. {{requester}}, {{ticket}}, {{subject}} and {{agent}} are filled in when used. */
@Component({
  selector: 'app-helpdesk-canned-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Saved replies' | translate }}</h3>
        <a class="btn btn-link" routerLink="/admin/helpdesk/tickets">{{ 'Back to tickets' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>

      <div class="card mb-3 hd-canned-form" style="padding:0.9rem;">
        <h4 class="mb-2">{{ editId ? ('Edit saved reply' | translate) : ('New saved reply' | translate) }}</h4>
        <div class="form-group">
          <label class="form-label">{{ 'Title' | translate }}</label>
          <input class="form-control hd-canned-title" name="title" [(ngModel)]="title" maxlength="120" [placeholder]="'e.g. Please restart the device' | translate" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Reply text' | translate }}</label>
          <textarea class="form-control hd-canned-body" name="body" [(ngModel)]="body" rows="5" placeholder="Write the answer you give most often"></textarea>
          <div class="text-muted" style="font-size:0.8rem;">{{ 'You can use {p0}, {p1}, {p2} and {p3}; they are filled in with the ticket\\'s details when you insert the reply.' | translate: { p0: (tk.requester), p1: (tk.ticket), p2: (tk.subject), p3: (tk.agent) } }}</div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group" style="margin:0; min-width:220px;">
            <label class="form-label">{{ 'Category (optional)' | translate }}</label>
            <select class="form-select" name="categoryId" [(ngModel)]="categoryId">
              <option [ngValue]="null">{{ 'Any category' | translate }}</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select>
          </div>
          <label style="display:flex; gap:0.35rem; align-items:center; margin:0;"><input type="checkbox" name="active" [(ngModel)]="active" /> {{ 'In use' | translate }}</label>
          <button class="btn btn-primary hd-canned-save" type="button" [disabled]="busy || !title.trim() || !body.trim()" (click)="save()">{{ editId ? ('Save changes' | translate) : ('Add' | translate) }}</button>
          <button class="btn btn-link" type="button" *ngIf="editId" (click)="reset()">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <table class="table-sm hd-canned-table">
        <thead><tr><th>{{ 'Title' | translate }}</th><th>{{ 'Category' | translate }}</th><th>{{ 'Text' | translate }}</th><th>{{ 'In use' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let r of list">
            <td>{{ r.title }}</td>
            <td>{{ categoryName(r.categoryId) }}</td>
            <td style="max-width:360px; white-space:pre-wrap;">{{ r.body.length > 140 ? (r.body | slice: 0 : 140) + '…' : r.body }}</td>
            <td>{{ r.active ? ('Yes' | translate) : ('No' | translate) }}</td>
            <td style="white-space:nowrap;">
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="edit(r)">{{ 'Edit' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" (click)="remove(r)">{{ 'Delete' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="!list.length"><td colspan="5" class="text-muted">{{ 'No saved replies yet.' | translate }}</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class HelpDeskCannedListComponent implements OnInit {
  list: HelpDeskCanned[] = [];
  categories: HelpDeskCategory[] = [];
  error: string | null = null;
  notice: string | null = null;
  busy = false;

  readonly tk = { requester: '{{requester}}', ticket: '{{ticket}}', subject: '{{subject}}', agent: '{{agent}}' };
  editId: number | null = null;
  title = '';
  body = '';
  categoryId: number | null = null;
  active = true;

  constructor(private api: HelpDeskCannedApiService, private categoryApi: HelpDeskCategoryApiService) {}

  ngOnInit(): void {
    this.categoryApi.listAll().subscribe({ next: (c) => (this.categories = c), error: () => {} });
    this.load();
  }

  load(): void {
    this.api.list().subscribe({ next: (l) => (this.list = l), error: (err) => (this.error = err?.error?.message ?? T('Could not load the saved replies')) });
  }

  categoryName(id: number | null): string {
    if (id == null) return 'Any';
    return this.categories.find((c) => c.id === id)?.name ?? '—';
  }

  edit(r: HelpDeskCanned): void {
    this.editId = r.id;
    this.title = r.title;
    this.body = r.body;
    this.categoryId = r.categoryId;
    this.active = r.active;
    this.notice = null;
  }

  reset(): void {
    this.editId = null;
    this.title = '';
    this.body = '';
    this.categoryId = null;
    this.active = true;
  }

  save(): void {
    this.error = null;
    this.notice = null;
    this.busy = true;
    const payload = { title: this.title.trim(), body: this.body.trim(), categoryId: this.categoryId, active: this.active };
    const call = this.editId ? this.api.update(this.editId, payload) : this.api.create(payload);
    call.subscribe({
      next: () => {
        this.busy = false;
        this.notice = this.editId ? T('Saved.') : T('Saved reply added.');
        this.reset();
        this.load();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? T('Could not save the reply');
      },
    });
  }

  remove(r: HelpDeskCanned): void {
    this.error = null;
    this.notice = null;
    this.api.delete(r.id).subscribe({
      next: () => {
        this.notice = T('Deleted.');
        if (this.editId === r.id) this.reset();
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? T('Could not delete the reply')),
    });
  }
}
