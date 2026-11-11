import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HotelExportComponent } from '../hotel/hotel-export.component';
import { ShellComponent } from '../shared/shell.component';
import { DiningTableApiService } from '../core/api/dining-table-api.service';
import { DiningTable } from '../core/models';

import { T } from '../core/i18n/t';
/** Admin > Restaurant > Tables. */
@Component({
  selector: 'app-table-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, HotelExportComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Tables' | translate }}</h3>
        <div class="flex gap-2 align-center">
          <app-hotel-export path="/restaurant/tables/export" name="restaurant-tables"></app-hotel-export>
          <button class="btn btn-primary" (click)="startNew()">{{ '+ New table' | translate }}</button>
        </div>
      </div>

      <div class="card mb-3" style="padding:1rem;" *ngIf="editing">
        <h4 class="mb-2" style="font-size:1rem;">{{ editId ? ('Edit table' | translate) : ('New table' | translate) }}</h4>
        <div class="flex gap-2 mb-2">
          <div class="form-group" style="flex:1;">
            <label class="form-label">{{ 'Name' | translate }}</label>
            <input class="form-control" [(ngModel)]="name" name="name" [placeholder]="'Table 1' | translate" />
          </div>
          <div class="form-group" style="flex:1;">
            <label class="form-label">{{ 'Section' | translate }}</label>
            <input class="form-control" [(ngModel)]="section" name="section" [placeholder]="'Main Hall' | translate" />
          </div>
          <div class="form-group" style="width:120px;">
            <label class="form-label">{{ 'Capacity' | translate }}</label>
            <input class="form-control" type="number" min="1" [(ngModel)]="capacity" name="capacity" />
          </div>
        </div>
        <label class="form-label mb-2">
          <input type="checkbox" [(ngModel)]="active" name="active" /> {{ 'Active' | translate }}
        </label>
        <div class="flex gap-2">
          <button class="btn btn-primary" (click)="save()">{{ 'Save' | translate }}</button>
          <button class="btn btn-link" (click)="cancelEdit()">{{ 'Cancel' | translate }}</button>
        </div>
      </div>

      <table>
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Section' | translate }}</th>
            <th>{{ 'Capacity' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let t of tables">
            <td>{{ t.name }}</td>
            <td>{{ t.section || '—' }}</td>
            <td>{{ t.capacity }}</td>
            <td>
              <span [class.text-muted]="!t.active">{{ t.status }}</span>
              <span class="text-muted" *ngIf="!t.active"> {{ '(inactive)' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <button class="btn btn-sm btn-outline-primary" (click)="startEdit(t)">{{ 'Edit' | translate }}</button>
              <button
                class="btn btn-sm btn-outline-secondary"
                *ngIf="t.status !== 'OUT_OF_SERVICE'"
                [disabled]="!!t.openOrderId"
                (click)="setStatus(t, 'OUT_OF_SERVICE')"
              >
                {{ 'Mark out of service' | translate }}
              </button>
              <button class="btn btn-sm btn-outline-secondary" *ngIf="t.status === 'OUT_OF_SERVICE'" (click)="setStatus(t, 'AVAILABLE')">
                {{ 'Mark available' | translate }}
              </button>
            </td>
          </tr>
          <tr *ngIf="tables.length === 0">
            <td colspan="5" class="text-muted">{{ 'No tables yet — add one above.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class TableListComponent implements OnInit {
  tables: DiningTable[] = [];
  error: string | null = null;

  editing = false;
  editId: number | null = null;
  name = '';
  section: string | null = null;
  capacity = 2;
  active = true;

  constructor(private tableApi: DiningTableApiService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.tableApi.listAll().subscribe({
      next: (t) => (this.tables = t),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load tables')),
    });
  }

  startNew(): void {
    this.editing = true;
    this.editId = null;
    this.name = '';
    this.section = null;
    this.capacity = 2;
    this.active = true;
  }

  startEdit(t: DiningTable): void {
    this.editing = true;
    this.editId = t.id;
    this.name = t.name;
    this.section = t.section;
    this.capacity = t.capacity;
    this.active = t.active;
  }

  cancelEdit(): void {
    this.editing = false;
  }

  save(): void {
    this.error = null;
    const payload = { name: this.name, section: this.section, capacity: this.capacity, active: this.active };
    const call = this.editId ? this.tableApi.update(this.editId, payload) : this.tableApi.create(payload);
    call.subscribe({
      next: () => {
        this.editing = false;
        this.load();
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to save table')),
    });
  }

  setStatus(t: DiningTable, status: string): void {
    this.error = null;
    this.tableApi.setStatus(t.id, status).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to update table status')),
    });
  }
}
