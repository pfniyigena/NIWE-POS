import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { RoutingApiService } from '../../core/api/routing-api.service';
import { WorkstationApiService } from '../../core/api/workstation-api.service';
import { Workstation } from '../../core/models';

interface DraftOperation {
  sequence: number;
  name: string;
  workstationId: number | null;
  estimatedMinutesPerUnit: number;
}

/**
 * Manufacturing > Settings > Routings > New/Edit. Nested operation-line editor, mirroring
 * PurchaseOrderFormComponent's add/remove-row line editor convention (but rows are typed in
 * directly rather than searched, since operations aren't a lookup of existing records).
 */
@Component({
  selector: 'app-routing-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ id ? 'Edit routing' : 'New routing' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <div class="form-group mb-3">
        <label class="form-label">Name</label>
        <input class="form-control" name="name" [(ngModel)]="name" required />
      </div>
      <div class="form-group mb-3">
        <label class="form-label">Description</label>
        <textarea class="form-control" name="description" rows="2" [(ngModel)]="description"></textarea>
      </div>
      <div class="form-group mb-3" *ngIf="id">
        <label class="form-label">
          <input type="checkbox" name="active" [(ngModel)]="active" /> Active
        </label>
      </div>

      <h4 class="mb-2">Operations</h4>
      <table class="table-sm mb-2">
        <thead>
          <tr>
            <th style="width:80px;">Seq</th>
            <th>Operation name</th>
            <th>Workstation</th>
            <th class="text-end">Est. minutes/unit</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let op of operations; let i = index">
            <td>
              <input type="number" class="form-control form-control-sm" style="width:70px;" min="1" [(ngModel)]="op.sequence" [name]="'seq' + i" />
            </td>
            <td>
              <input class="form-control form-control-sm" [(ngModel)]="op.name" [name]="'opname' + i" placeholder="e.g. Cut, Assemble, Paint, Pack" />
            </td>
            <td>
              <select class="form-select form-select-sm" [(ngModel)]="op.workstationId" [name]="'ws' + i">
                <option [ngValue]="null">—</option>
                <option *ngFor="let w of workstations" [ngValue]="w.id">{{ w.name }}</option>
              </select>
            </td>
            <td class="text-end">
              <input
                type="number"
                class="form-control form-control-sm"
                style="width:110px; margin-left:auto;"
                min="0"
                step="0.01"
                [(ngModel)]="op.estimatedMinutesPerUnit"
                [name]="'mins' + i"
              />
            </td>
            <td><button type="button" class="btn btn-sm btn-outline-danger" (click)="removeOperation(i)">Remove</button></td>
          </tr>
          <tr *ngIf="operations.length === 0">
            <td colspan="5" class="text-muted">No operations yet — add at least one below.</td>
          </tr>
        </tbody>
      </table>
      <button type="button" class="btn btn-outline-secondary btn-sm mb-3" (click)="addOperation()">+ Add operation</button>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="submit()">Save</button>
        <a class="btn btn-link" routerLink="/admin/manufacturing/routings">Cancel</a>
      </div>
    </app-shell>
  `,
})
export class RoutingFormComponent implements OnInit {
  id: number | null = null;
  name = '';
  description = '';
  active = true;
  operations: DraftOperation[] = [];
  workstations: Workstation[] = [];
  error: string | null = null;

  constructor(
    private routingApi: RoutingApiService,
    private workstationApi: WorkstationApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.workstationApi.listActive().subscribe({
      next: (w) => (this.workstations = w),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load workstations'),
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.addOperation();
      return;
    }
    this.id = Number(idParam);
    this.routingApi.getById(this.id).subscribe({
      next: (r) => {
        this.name = r.name;
        this.description = r.description ?? '';
        this.active = r.active;
        this.operations = r.operations.map((op) => ({
          sequence: op.sequence,
          name: op.name,
          workstationId: op.workstationId,
          estimatedMinutesPerUnit: op.estimatedMinutesPerUnit,
        }));
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load routing'),
    });
  }

  addOperation(): void {
    const nextSeq = this.operations.length > 0 ? Math.max(...this.operations.map((o) => o.sequence)) + 1 : 1;
    this.operations.push({ sequence: nextSeq, name: '', workstationId: null, estimatedMinutesPerUnit: 0 });
  }

  removeOperation(index: number): void {
    this.operations.splice(index, 1);
  }

  submit(): void {
    if (!this.name.trim()) {
      this.error = 'Name is required';
      return;
    }
    if (this.operations.length === 0 || this.operations.some((o) => !o.name.trim())) {
      this.error = 'Every operation needs a name — add at least one operation';
      return;
    }
    this.error = null;
    const payload = {
      name: this.name,
      description: this.description || null,
      active: this.active,
      operations: this.operations.map((o) => ({
        sequence: o.sequence,
        name: o.name,
        workstationId: o.workstationId,
        estimatedMinutesPerUnit: o.estimatedMinutesPerUnit,
      })),
    };
    const call = this.id ? this.routingApi.update(this.id, payload) : this.routingApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/manufacturing/routings']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save routing'),
    });
  }
}
