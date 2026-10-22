import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ChecklistItem, HkStaff, HotelApiService, Productivity } from '../core/api/hotel-api.service';
import { addDays, errMsg, today } from './hotel-util';

/** Housekeeping > Setup: housekeepers, the inspection checklist and how the team is doing. */
@Component({
  selector: 'app-hk-setup',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
    <div class="alert alert-success" *ngIf="message">{{ message }}</div>

    <h5>Housekeepers</h5>
    <table class="table-sm staff-table">
      <thead><tr><th>Name</th><th>Active</th><th class="text-end">Rooms today</th><th></th></tr></thead>
      <tbody>
        <tr *ngFor="let s of staff" class="staff-row"><td>{{ s.name }}</td><td>{{ s.active ? 'Yes' : 'No' }}</td><td class="text-end">{{ s.tasks }}</td>
          <td class="flex gap-1" *ngIf="canManage"><button class="btn btn-sm btn-outline-secondary toggle-staff" type="button" (click)="toggleStaff(s)">{{ s.active ? 'Deactivate' : 'Activate' }}</button>
            <button class="btn btn-sm btn-outline-danger delete-staff" type="button" (click)="removeStaff(s)">Delete</button></td></tr>
      </tbody>
    </table>
    <p class="text-muted" *ngIf="!staff.length">No housekeepers yet.</p>
    <form class="flex gap-2 mb-4" *ngIf="canManage" (ngSubmit)="addStaff()">
      <input class="form-control staff-name" style="width:220px;" name="sname" [(ngModel)]="newStaff" placeholder="Name" required />
      <button class="btn btn-primary add-staff" type="submit">Add housekeeper</button>
    </form>

    <h5>Inspection checklist</h5>
    <div *ngFor="let c of checklist; let i = index" class="flex gap-2 align-center mb-1 checklist-line">
      <input class="form-control" style="max-width:360px;" name="cl{{ i }}" [(ngModel)]="c.label" [disabled]="!canManage" />
      <label><input type="checkbox" name="ca{{ i }}" [(ngModel)]="c.active" [disabled]="!canManage" /> Active</label>
      <button class="btn btn-sm btn-link remove-line" type="button" *ngIf="canManage" (click)="checklist.splice(i, 1)">Remove</button>
    </div>
    <div class="flex gap-2 mt-2 mb-4" *ngIf="canManage">
      <button class="btn btn-outline-secondary add-line" type="button" (click)="checklist.push({ id: null, label: '', sortOrder: checklist.length, active: true })">+ Add line</button>
      <button class="btn btn-primary save-checklist" type="button" (click)="saveChecklist()">Save checklist</button>
    </div>

    <h5>Team productivity</h5>
    <div class="flex gap-2 align-center mb-2">
      <input class="form-control prod-from" type="date" style="width:auto;" name="pf" [(ngModel)]="from" (ngModelChange)="loadProd()" />
      <span>to</span>
      <input class="form-control prod-to" type="date" style="width:auto;" name="pt" [(ngModel)]="to" (ngModelChange)="loadProd()" />
    </div>
    <table class="table-sm prod-table" *ngIf="prod.length">
      <thead><tr><th>Housekeeper</th><th class="text-end">Rooms</th><th class="text-end">Departure</th><th class="text-end">Stay-over</th><th class="text-end">Avg departure</th><th class="text-end">Avg stay-over</th><th class="text-end">Inspected</th><th class="text-end">Sent back</th></tr></thead>
      <tbody><tr *ngFor="let p of prod" class="prod-row"><td>{{ p.staffName }}</td><td class="text-end">{{ p.tasks }}</td><td class="text-end">{{ p.departures }}</td><td class="text-end">{{ p.stayovers }}</td>
        <td class="text-end">{{ p.avgDepartureMinutes === null ? '—' : p.avgDepartureMinutes + ' min' }}</td><td class="text-end">{{ p.avgStayoverMinutes === null ? '—' : p.avgStayoverMinutes + ' min' }}</td>
        <td class="text-end">{{ p.inspections }}</td><td class="text-end">{{ p.failedInspections }}</td></tr></tbody>
    </table>
    <p class="text-muted" *ngIf="!prod.length">No cleaning recorded in this period.</p>
  `,
})
export class HkSetupComponent implements OnInit {
  staff: HkStaff[] = [];
  checklist: ChecklistItem[] = [];
  prod: Productivity[] = [];
  newStaff = '';
  from = addDays(today(), -6);
  to = today();
  error: string | null = null;
  message: string | null = null;

  constructor(private api: HotelApiService, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('HOTEL');
  }

  ngOnInit(): void {
    this.loadStaff();
    this.api.hkChecklist(true).subscribe((c) => (this.checklist = c));
    this.loadProd();
  }

  loadStaff(): void {
    this.api.hkStaff().subscribe((s) => (this.staff = s));
  }

  loadProd(): void {
    if (this.from && this.to) this.api.hkProductivity(this.from, this.to).subscribe({ next: (p) => (this.prod = p), error: (e) => (this.error = errMsg(e, 'Failed to load productivity')) });
  }

  addStaff(): void {
    this.error = this.message = null;
    this.api.hkSaveStaff(null, this.newStaff, true).subscribe({ next: () => { this.newStaff = ''; this.loadStaff(); }, error: (e) => (this.error = errMsg(e, 'Could not add the housekeeper')) });
  }

  toggleStaff(s: HkStaff): void {
    this.api.hkSaveStaff(s.id, s.name, !s.active).subscribe({ next: () => this.loadStaff(), error: (e) => (this.error = errMsg(e, 'Could not update the housekeeper')) });
  }

  removeStaff(s: HkStaff): void {
    if (!confirm(`Delete ${s.name}?`)) return;
    this.error = null;
    this.api.hkDeleteStaff(s.id).subscribe({ next: () => this.loadStaff(), error: (e) => (this.error = errMsg(e, 'Could not delete the housekeeper')) });
  }

  saveChecklist(): void {
    this.error = this.message = null;
    this.api.hkSaveChecklist(this.checklist.filter((c) => c.label.trim()).map((c) => ({ label: c.label.trim(), active: c.active }))).subscribe({
      next: (c) => { this.checklist = c; this.message = 'Checklist saved.'; },
      error: (e) => (this.error = errMsg(e, 'Could not save the checklist')),
    });
  }
}
