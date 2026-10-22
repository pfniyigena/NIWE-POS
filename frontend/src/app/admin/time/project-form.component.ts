import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { CustomerApiService } from '../../core/api/customer-api.service';
import { Customer } from '../../core/models';
import { QuickCustomerComponent } from '../../shared/quick-customer.component';
import { ProjectDetail, ProjectPayload, ProjectStatus, StaffOption, TimeApiService } from '../../core/api/time-api.service';

/** Time Tracking > a project: its details, budget and rates, tasks and per-person rates. */
@Component({
  selector: 'app-time-project-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent, QuickCustomerComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? 'Project' : 'New project' }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/time/projects">Back to projects</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>

      <form class="card mb-3" style="padding:1rem; max-width:44rem;" (ngSubmit)="save()">
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">Name</label><input class="form-control" name="n" [(ngModel)]="f.name" required /></div>
          <div class="form-group" style="flex:1; min-width:8rem;"><label class="form-label">Code (optional)</label><input class="form-control" name="c" [(ngModel)]="f.code" /></div>
        </div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">Customer (who is billed)</label>
            <select class="form-select" name="cu" [(ngModel)]="f.customerId">
              <option [ngValue]="null">No customer</option>
              <option *ngFor="let c of customers" [ngValue]="c.id">{{ c.name }}</option>
            </select>
            <app-quick-customer (created)="onCustomerCreated($event)"></app-quick-customer></div>
          <div class="form-group" *ngIf="id"><label class="form-label">Status</label>
            <select class="form-select" name="s" [(ngModel)]="f.status">
              <option value="ACTIVE">Active — time can be logged</option>
              <option value="COMPLETED">Completed — no new time</option>
              <option value="ARCHIVED">Archived — hidden</option>
            </select></div>
        </div>
        <div class="form-group"><label class="form-label">Description</label><textarea class="form-control" rows="2" name="d" [(ngModel)]="f.description"></textarea></div>
        <div class="flex gap-3" style="flex-wrap:wrap;">
          <div class="form-group"><label class="form-label">Hourly rate</label><input class="form-control" type="number" min="0" step="0.01" name="r" [(ngModel)]="f.defaultRate" placeholder="Use the default" /></div>
          <div class="form-group"><label class="form-label">Budget (hours)</label><input class="form-control" type="number" min="0" step="0.5" name="b" [(ngModel)]="f.budgetHours" placeholder="No budget" /></div>
          <div class="form-group"><label class="form-label">Start</label><app-date-picker name="sd" [(ngModel)]="f.startDate"></app-date-picker></div>
          <div class="form-group"><label class="form-label">End</label><app-date-picker name="ed" [(ngModel)]="f.endDate"></app-date-picker></div>
        </div>
        <div class="form-group"><label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="bl" [(ngModel)]="f.billable" /> Time on this project is billable to the customer</label></div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit" [disabled]="busy">Save</button>
          <button class="btn btn-outline-danger" type="button" *ngIf="id" (click)="remove()">Delete</button>
        </div>
      </form>

      <ng-container *ngIf="detail">
        <div class="card mb-3" style="padding:1rem; max-width:44rem;">
          <strong class="mb-2">Hours</strong>
          <div>{{ detail.summary.hoursLogged | number: '1.0-2' }} h logged · {{ detail.summary.billableHours | number: '1.0-2' }} h billable ·
            {{ detail.summary.unbilledAmount | number: '1.0-2' }} ready to invoice
            <span *ngIf="detail.summary.budgetHours != null"> · budget {{ detail.summary.budgetHours | number: '1.0-2' }} h ({{ detail.summary.percentOfBudget | number: '1.0-0' }}% used)</span></div>
          <a *ngIf="detail.summary.unbilledAmount > 0" routerLink="/admin/time/billing" style="font-size:0.9rem;">Go to billing</a>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;">
          <strong class="mb-2">Tasks (optional)</strong>
          <table class="table-sm" *ngIf="detail.tasks.length">
            <thead><tr><th>Task</th><th class="text-end">Estimate (h)</th><th class="text-end">Logged (h)</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let t of detail.tasks">
                <td [style.opacity]="t.active ? 1 : 0.5">{{ t.name }}<span class="text-muted" *ngIf="!t.active"> (closed)</span></td>
                <td class="text-end">{{ t.estimatedHours != null ? (t.estimatedHours | number: '1.0-2') : '—' }}</td>
                <td class="text-end">{{ t.hoursLogged | number: '1.0-2' }}</td>
                <td style="white-space:nowrap;">
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="toggleTask(t)">{{ t.active ? 'Close' : 'Reopen' }}</button>
                  <button class="btn btn-sm btn-outline-danger" type="button" style="margin-left:0.3rem;" (click)="removeTask(t.id)">Remove</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
            <input class="form-control" style="width:14rem;" placeholder="New task" name="tn" [(ngModel)]="newTask" />
            <input class="form-control" style="width:8rem;" type="number" min="0" step="0.5" placeholder="Hours" name="te" [(ngModel)]="newTaskHours" />
            <button class="btn btn-outline-primary" type="button" [disabled]="!newTask.trim()" (click)="addTask()">Add task</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem; max-width:44rem;">
          <strong class="mb-2">Rates per person</strong>
          <div class="text-muted mb-2" style="font-size:0.85rem;">Anyone not listed is billed at the project rate{{ detail.summary.defaultRate != null ? ' (' + detail.summary.defaultRate + ')' : ' or the default rate in the settings' }}.
            A rate is fixed on an entry when it is submitted, so changing it later does not alter time already handed in.</div>
          <table class="table-sm" *ngIf="detail.rates.length">
            <tbody><tr *ngFor="let r of detail.rates"><td>{{ r.userName }}</td><td class="text-end">{{ r.hourlyRate | number: '1.0-2' }}</td>
              <td><button class="btn btn-sm btn-outline-danger" type="button" (click)="removeRate(r.userId)">Remove</button></td></tr></tbody>
          </table>
          <div class="flex gap-2 align-center mt-2" style="flex-wrap:wrap;">
            <select class="form-select" style="width:auto;" name="ru" [(ngModel)]="rateUser"><option [ngValue]="null">Person…</option><option *ngFor="let u of staff" [ngValue]="u.id">{{ u.fullName }}</option></select>
            <input class="form-control" style="width:8rem;" type="number" min="0" step="0.01" placeholder="Rate" name="rv" [(ngModel)]="rateValue" />
            <button class="btn btn-outline-primary" type="button" [disabled]="!rateUser || rateValue == null" (click)="addRate()">Set rate</button>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class TimeProjectFormComponent implements OnInit {
  id: number | null = null;
  detail: ProjectDetail | null = null;
  customers: Customer[] = [];
  staff: StaffOption[] = [];
  error: string | null = null;
  saved = false;
  busy = false;
  newTask = '';
  newTaskHours: number | null = null;
  rateUser: number | null = null;
  rateValue: number | null = null;
  f: ProjectPayload & { status: ProjectStatus; billable: boolean } = {
    name: '', code: '', customerId: null, description: '', status: 'ACTIVE', billable: true, defaultRate: null, budgetHours: null, startDate: null, endDate: null,
  };

  onCustomerCreated(c: Customer): void {
    this.customers = [...this.customers, c].sort((a, b) => a.name.localeCompare(b.name));
    this.f.customerId = c.id;
  }

  constructor(private api: TimeApiService, private customerApi: CustomerApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.customerApi.list().subscribe({ next: (c) => (this.customers = c.filter((x) => !(x as any).walkIn)), error: () => {} });
    this.api.staff().subscribe({ next: (s) => (this.staff = s), error: () => {} });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      this.id = Number(idParam);
      this.reload();
    }
  }

  private reload(): void {
    this.api.project(this.id!).subscribe({
      next: (d) => this.show(d),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the project'),
    });
  }

  private show(d: ProjectDetail): void {
    this.detail = d;
    const s = d.summary;
    this.f = {
      name: s.name, code: s.code ?? '', customerId: s.customerId, description: d.description ?? '', status: s.status, billable: s.billable,
      defaultRate: s.defaultRate, budgetHours: s.budgetHours, startDate: s.startDate, endDate: s.endDate, branchId: d.branchId,
    };
  }

  save(): void {
    this.error = null;
    this.saved = false;
    this.busy = true;
    const call = this.id ? this.api.updateProject(this.id, this.f) : this.api.createProject(this.f);
    call.subscribe({
      next: (d) => {
        this.busy = false;
        if (!this.id) {
          this.router.navigate(['/admin/time/projects', d.summary.id]);
          return;
        }
        this.show(d);
        this.saved = true;
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not save';
      },
    });
  }

  remove(): void {
    if (!confirm('Delete this project? Only possible while no time is logged on it.')) return;
    this.api.deleteProject(this.id!).subscribe({
      next: () => this.router.navigate(['/admin/time/projects']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete'),
    });
  }

  addTask(): void {
    this.api.addTask(this.id!, this.newTask.trim(), this.newTaskHours).subscribe({
      next: () => {
        this.newTask = '';
        this.newTaskHours = null;
        this.reload();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not add the task'),
    });
  }

  toggleTask(t: { id: number; name: string; estimatedHours: number | null; active: boolean }): void {
    this.api.updateTask(t.id, { name: t.name, estimatedHours: t.estimatedHours, active: !t.active }).subscribe({ next: () => this.reload(), error: (e) => (this.error = e?.error?.message ?? 'Could not change the task') });
  }

  removeTask(taskId: number): void {
    this.api.deleteTask(taskId).subscribe({ next: () => this.reload(), error: (e) => (this.error = e?.error?.message ?? 'Could not remove the task') });
  }

  addRate(): void {
    this.api.setRate(this.id!, this.rateUser!, this.rateValue!).subscribe({
      next: () => {
        this.rateUser = null;
        this.rateValue = null;
        this.reload();
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not set the rate'),
    });
  }

  removeRate(userId: number): void {
    this.api.removeRate(this.id!, userId).subscribe({ next: () => this.reload(), error: (e) => (this.error = e?.error?.message ?? 'Could not remove the rate') });
  }
}
